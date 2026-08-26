package logging.sentry;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryLongDate;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;

public class SentrySpans {

	// Was ScopedValue<ISpan>: a JDK preview feature. Preview-feature class
	// files are tied to the exact major JDK version that compiled them and
	// are rejected outright by any other version, even a newer one -- CI
	// compiles this module on JDK 21 while the game_server image runs on
	// JDK 24, so every call here threw UnsupportedClassVersionError. Because
	// this wraps the entire tick body in Server.java (core.logic.tick,
	// core.continuations.tick, core.rsprot.tick), that meant the core game
	// tick -- including RSProtService.tick(), which finishes the login
	// handshake -- never ran even once since server boot. A plain
	// ThreadLocal gives the same dynamic-scoping/nesting behavior for this
	// single-threaded-per-worker use case without any JDK-version coupling.
	private static final ThreadLocal<ISpan> CURRENT_SPAN = new ThreadLocal<>();

	public static void customFinished(String name, long elapsednano, Map<String, Object> data) {
		var root = CURRENT_SPAN.get();
		var opt = new SpanOptions();
		var endNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
		var startNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - elapsednano;
		opt.setStartTimestamp(new SentryLongDate(startNano));
		var span = root.startChild(name, name, opt);
		for (var entry : data.entrySet()) {
			span.setData(entry.getKey(), entry.getValue());
		}
		span.finish(SpanStatus.OK, new SentryLongDate(endNano));
	}

	public static void start(String name, Runnable runnable) {
		var root = CURRENT_SPAN.get();
		if (root != null) {
			startChild(root, name, runnable);
			return;
		}
		var tx = Sentry.startTransaction(name, name);
		runWithSpan(tx, () -> {
			try {
				runnable.run();
			} catch (Exception e) {
				tx.setStatus(SpanStatus.INTERNAL_ERROR);
				tx.setThrowable(e);
				throw e;
			} finally {
				tx.finish();
			}
		});
	}

	private static void startChild(ISpan span, String name, Runnable runnable) {
		var tx = span.startChild(name, name);
		runWithSpan(tx, () -> {
			try {
				runnable.run();
			} catch (Exception e) {
				tx.setStatus(SpanStatus.INTERNAL_ERROR);
				tx.setThrowable(e);
				throw e;
			} finally {
				tx.finish();
			}
		});
	}

	private static void runWithSpan(ISpan span, Runnable runnable) {
		var previous = CURRENT_SPAN.get();
		CURRENT_SPAN.set(span);
		try {
			runnable.run();
		} finally {
			CURRENT_SPAN.set(previous);
		}
	}
}
