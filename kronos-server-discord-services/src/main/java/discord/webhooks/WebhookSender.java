package discord.webhooks;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import discord.comp.impl.Message;

public class WebhookSender {

	public static record Result(Status status, int statusCode, String body, long retryMs) {
		public static enum Status {
			Ok, Retry, Error
		}
	};

	static HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	/** {@code attachment://item_<id>.png} thumbnails ({@link ItemIcons}): their PNGs go up with the message. */
	private static final Pattern ITEM_ATTACHMENT = Pattern.compile(Pattern.quote(ItemIcons.PREFIX) + "(" + ItemIcons.FILE_PREFIX + "\\d+\\.png)");

	public static CompletableFuture<Result> send(String target, Message message) {
		var json = message.toJson().toString();
		var files = new LinkedHashMap<String, byte[]>();
		var matcher = ITEM_ATTACHMENT.matcher(json);
		while (matcher.find()) {
			var bytes = ItemIcons.read(matcher.group(1));
			if (bytes != null) {
				files.putIfAbsent(matcher.group(1), bytes);
			}
		}
		return send(files.isEmpty() ? buildRequest(target, json) : buildMultipartRequest(target, json, files));
	}

	private static CompletableFuture<Result> send(HttpRequest request) {
		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenCompose(response -> {
					var status = response.statusCode();
					var body = response.body();
					if (status >= 200 && status < 300) {
						return CompletableFuture.completedFuture(new Result(Result.Status.Ok, status, body, 0));
					} else if (status == 429) {
						// rate limited: Retry re-queues it (it was Ok, which silently dropped the message)
						var delayMs = computeRetryDelayMs(response);
						return CompletableFuture.completedFuture(new Result(Result.Status.Retry, status, body, delayMs));
					}
					return CompletableFuture.completedFuture(new Result(Result.Status.Error, status, body, 0));
				});
	}

	private static long computeRetryDelayMs(HttpResponse<String> response) {
		Optional<String> resetAfter = firstHeader(response, "X-RateLimit-Reset-After");
		if (resetAfter.isPresent()) {
			double seconds = Double.parseDouble(resetAfter.get());
			return (long) Math.ceil(seconds * 1000.0);
		}

		// Discord/HTTP: Retry-After could be seconds or HTTP-date
		Optional<String> retryAfter = firstHeader(response, "Retry-After");
		if (retryAfter.isPresent()) {
			String value = retryAfter.get().trim();
			return (long) Math.ceil(Double.parseDouble(value) * 1000.0);
		}

		return 10_000;
	}

	private static Optional<String> firstHeader(HttpResponse<?> response, String name) {
		return response.headers().firstValue(name);
	}

	private static HttpRequest buildRequest(String url, String json) {
		return HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
	}

	/** multipart/form-data: the JSON as payload_json, then each file as files[n] (Discord's upload format). */
	private static HttpRequest buildMultipartRequest(String url, String json, Map<String, byte[]> files) {
		var boundary = "zelus" + UUID.randomUUID().toString().replace("-", "");
		var body = new ByteArrayOutputStream();
		var crlf = "\r\n";
		body.writeBytes(("--" + boundary + crlf
				+ "Content-Disposition: form-data; name=\"payload_json\"" + crlf
				+ "Content-Type: application/json" + crlf + crlf
				+ json + crlf).getBytes(StandardCharsets.UTF_8));
		int n = 0;
		for (var file : files.entrySet()) {
			body.writeBytes(("--" + boundary + crlf
					+ "Content-Disposition: form-data; name=\"files[" + n++ + "]\"; filename=\"" + file.getKey() + "\"" + crlf
					+ "Content-Type: image/png" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
			body.writeBytes(file.getValue());
			body.writeBytes(crlf.getBytes(StandardCharsets.UTF_8));
		}
		body.writeBytes(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
		return HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build();
	}
}
