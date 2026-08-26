package io.ruin.services;

import com.alibaba.fastjson2.JSON;
import io.ruin.Server;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.entity.npc.actions.edgeville.StarterGuide;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One-off official-launch data reset: preserves exactly two accounts (server owners), wipes
 * every other player's saves, highscores, and beta-era audit logs. Built as backup -> dry run ->
 * execute, in that order, with execute() never run automatically -- see ::launchbackup,
 * ::launchwipedryrun, ::launchwipeexecute in CommandHandlerAdmin.
 */
@Slf4j
public final class LaunchWipe {

	// Server owners -- permanently preserved. Matched via normalize() so "Mr Boolt", "mr boolt",
	// "mr_boolt" (gear_loadouts' sanitized key scheme) etc. all match the same target.
	private static final Set<String> PRESERVE_NORMALIZED = Set.of("mrboolt", "peaks");

	private static final String[] LOG_TABLE_PREFIX_LIKE = {"logs\\_%"};

	static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String noExt = raw.endsWith(".json") ? raw.substring(0, raw.length() - 5) : raw;
		return noExt.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	static boolean isPreserved(String filenameOrName) {
		return PRESERVE_NORMALIZED.contains(normalize(filenameOrName));
	}

	private static Path savesRoot() {
		return Paths.get(ServerWrapper.dataFolder.getAbsolutePath(), "runtime", "saves");
	}

	private static Path backupRoot(String label) {
		return Paths.get(ServerWrapper.dataFolder.getAbsolutePath(), "backups", label);
	}

	private static List<String> discoverTargetTables() {
		List<String> tables = new ArrayList<>();
		tables.add("hs_users");
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement("SHOW TABLES LIKE ?")) {
				stmt.setString(1, LOG_TABLE_PREFIX_LIKE[0]);
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						tables.add(rs.getString(1));
					}
				}
			} catch (Exception e) {
				log.error("Failed discovering logs_* tables", e);
			}
		});
		return tables;
	}

	// ============================== BACKUP ==============================

	public static String backup() {
		String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		Path dest = backupRoot("launch_" + ts);
		StringBuilder sb = new StringBuilder();
		try {
			Files.createDirectories(dest);

			Path savesDest = dest.resolve("saves");
			int fileCount = copyTree(savesRoot(), savesDest);
			sb.append("Copied ").append(fileCount).append(" save file(s) -> ").append(savesDest).append("\n");

			Path sqlDir = dest.resolve("sql");
			Files.createDirectories(sqlDir);
			for (String table : discoverTargetTables()) {
				int rows = dumpTableToJson(table, sqlDir.resolve(table + ".json"));
				sb.append("Dumped ").append(rows).append(" row(s) from `").append(table).append("` -> ")
						.append(sqlDir.resolve(table + ".json")).append("\n");
			}

			Path mongoFile = dest.resolve("mongo_players.json");
			MongoPlayerMirror mirror = MongoPlayerMirrorRegistry.get();
			int mongoCount = mirror == null ? -1 : mirror.dumpAll(mongoFile);
			sb.append(mirror == null
					? "Mongo mirror not available (player-mongo module not loaded) -- skipped\n"
					: "Dumped " + mongoCount + " mongo player document(s) -> " + mongoFile + "\n");

			sb.append("\nBackup complete: ").append(dest);
		} catch (Exception e) {
			log.error("Launch backup failed", e);
			sb.append("\nBACKUP FAILED: ").append(e);
		}
		return sb.toString();
	}

	private static int copyTree(Path src, Path dest) throws IOException {
		if (!Files.exists(src)) {
			return 0;
		}
		int[] count = {0};
		try (Stream<Path> walk = Files.walk(src)) {
			walk.forEach(p -> {
				try {
					Path rel = src.relativize(p);
					Path target = dest.resolve(rel.toString());
					if (Files.isDirectory(p)) {
						Files.createDirectories(target);
					} else {
						Files.createDirectories(target.getParent());
						Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
						count[0]++;
					}
				} catch (Exception e) {
					log.error("Failed copying " + p, e);
				}
			});
		}
		return count[0];
	}

	private static int dumpTableToJson(String table, Path dest) {
		List<Map<String, Object>> rows = new ArrayList<>();
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement("SELECT * FROM `" + table + "`");
				 ResultSet rs = stmt.executeQuery()) {
				ResultSetMetaData meta = rs.getMetaData();
				int cols = meta.getColumnCount();
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 1; i <= cols; i++) {
						row.put(meta.getColumnLabel(i), rs.getObject(i));
					}
					rows.add(row);
				}
			} catch (Exception e) {
				log.error("Failed dumping table " + table, e);
			}
		});
		try {
			Files.writeString(dest, JSON.toJSONString(rows));
		} catch (Exception e) {
			log.error("Failed writing dump for " + table, e);
		}
		return rows.size();
	}

	// ============================== DRY RUN ==============================

	public static String dryRun() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== LAUNCH WIPE DRY RUN (nothing written) ===\n\n");

		sb.append("-- File-based saves (").append(savesRoot()).append(") --\n");
		Map<String, int[]> perDir = new LinkedHashMap<>(); // dir -> [total, preserved]
		if (Files.exists(savesRoot())) {
			try (Stream<Path> walk = Files.walk(savesRoot())) {
				walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
					String dirKey = savesRoot().relativize(p).getParent() == null
							? "." : savesRoot().relativize(p).getParent().toString();
					int[] counts = perDir.computeIfAbsent(dirKey, k -> new int[2]);
					counts[0]++;
					if (isPreserved(p.getFileName().toString())) {
						counts[1]++;
					}
				});
			} catch (Exception e) {
				sb.append("ERROR walking saves tree: ").append(e).append("\n");
			}
		} else {
			sb.append("(saves directory does not exist)\n");
		}
		int totalFiles = 0, totalPreserved = 0;
		for (var entry : perDir.entrySet()) {
			totalFiles += entry.getValue()[0];
			totalPreserved += entry.getValue()[1];
			sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()[0])
					.append(" file(s), ").append(entry.getValue()[1]).append(" preserved, ")
					.append(entry.getValue()[0] - entry.getValue()[1]).append(" would be deleted\n");
		}
		sb.append("  TOTAL: ").append(totalFiles).append(" file(s), ").append(totalPreserved)
				.append(" preserved, ").append(totalFiles - totalPreserved).append(" would be deleted\n\n");

		sb.append("-- MariaDB (`reason`) --\n");
		for (String table : discoverTargetTables()) {
			int count = countRows(table);
			sb.append("  `").append(table).append("`: ").append(count).append(" row(s) would be deleted (full wipe)\n");
		}
		sb.append("\n");

		sb.append("-- MongoDB `players` collection --\n");
		MongoPlayerMirror mirror = MongoPlayerMirrorRegistry.get();
		List<String> mongoNames = mirror == null ? List.of() : mirror.allNames();
		long mongoPreserved = mongoNames.stream().filter(LaunchWipe::isPreserved).count();
		if (mirror == null) {
			sb.append("  mongo mirror not available (player-mongo module not loaded)\n\n");
		} else {
			sb.append("  ").append(mongoNames.size()).append(" document(s), ").append(mongoPreserved)
					.append(" preserved, ").append(mongoNames.size() - mongoPreserved).append(" would be deleted\n\n");
		}

		sb.append("-- Starter pack per-IP claim tracker (StarterGuide.ipClaimCounts) --\n");
		sb.append("  ").append(StarterGuide.ipClaimCounts.size())
				.append(" IP(s) tracked, all would be cleared -- every IP starts at 0 claims post-wipe\n\n");

		sb.append("-- Preservation check --\n");
		for (String target : PRESERVE_NORMALIZED) {
			sb.append("  '").append(target).append("': ")
					.append(totalPreserved > 0 || mongoPreserved > 0 ? "found matching file(s)/doc(s) above" : "NOT FOUND ANYWHERE -- verify this account exists before executing")
					.append("\n");
		}

		return sb.toString();
	}

	private static int countRows(String table) {
		int[] result = {0};
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement("SELECT COUNT(*) FROM `" + table + "`");
				 ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					result[0] = rs.getInt(1);
				}
			} catch (Exception e) {
				log.error("Failed counting rows in " + table, e);
			}
		});
		return result[0];
	}

	// ============================== EXECUTE ==============================

	public static String execute() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== LAUNCH WIPE EXECUTING ===\n\n");

		int deletedFiles = 0;
		if (Files.exists(savesRoot())) {
			try (Stream<Path> walk = Files.walk(savesRoot())) {
				List<Path> toDelete = walk
						.filter(p -> p.toString().endsWith(".json"))
						.filter(p -> !isPreserved(p.getFileName().toString()))
						.collect(Collectors.toList());
				for (Path p : toDelete) {
					try {
						Files.delete(p);
						deletedFiles++;
					} catch (Exception e) {
						log.error("Failed deleting " + p, e);
					}
				}
			} catch (Exception e) {
				sb.append("ERROR walking saves tree: ").append(e).append("\n");
			}
		}
		sb.append("Deleted ").append(deletedFiles).append(" save file(s) from ").append(savesRoot()).append("\n");

		for (String table : discoverTargetTables()) {
			int deleted = wipeTable(table);
			sb.append("Deleted ").append(deleted).append(" row(s) from `").append(table).append("`\n");
		}

		MongoPlayerMirror mirror = MongoPlayerMirrorRegistry.get();
		int mongoDeleted = mirror == null ? -1 : mirror.deleteAllExcept(PRESERVE_NORMALIZED, LaunchWipe::normalize);
		sb.append(mirror == null
				? "Mongo mirror not available (player-mongo module not loaded) -- skipped\n"
				: "Deleted " + mongoDeleted + " document(s) from mongo `players` collection\n");

		int ipCount = StarterGuide.ipClaimCounts.size();
		StarterGuide.ipClaimCounts.clear();
		StarterGuide.saveIps();
		sb.append("Cleared ").append(ipCount).append(" starter-pack IP claim record(s) -- every IP starts fresh\n");

		sb.append("\n=== LAUNCH WIPE COMPLETE ===");
		return sb.toString();
	}

	private static int wipeTable(String table) {
		int[] result = {0};
		Server.gameDb.execute(con -> {
			try (PreparedStatement stmt = con.prepareStatement("DELETE FROM `" + table + "`")) {
				result[0] = stmt.executeUpdate();
			} catch (Exception e) {
				log.error("Failed wiping table " + table, e);
			}
		});
		return result[0];
	}

}
