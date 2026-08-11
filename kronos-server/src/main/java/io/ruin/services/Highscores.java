package io.ruin.services;

import io.ruin.Server;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;

import static core.task.api.API.*;

/**
 * Writes each player's stats to the `reason`.`hs_users` table, which the
 * website's /hiscores endpoint reads via game_database.py's HiscoreUser ORM.
 *
 * `difficulty`/`mode` are plain int(11) columns on the live table (not
 * strings), encoded as the enum's ordinal:
 *   difficulty: Difficulty.values() order -- EASY, INTERMEDIATE, HARD, EXTREME, OSRS
 *   mode:       GameMode.values() order -- STANDARD, IRONMAN, ULTIMATE_IRONMAN,
 *               HARDCORE_IRONMAN, GROUP_IRONMAN, HARDCORE_GROUP_IRONMAN
 * The website API must decode using this same ordinal order.
 *
 * Skill xp columns follow StatType's declaration order (Attack..Construction,
 * 23 skills), which is also the ordinal order StatList indexes stats by.
 */
@Slf4j
public final class Highscores {

	private static final int SKILL_COUNT = StatType.values().length;

	public static void register() {
		if (World.isDev()) {
			return;
		}
		queue(() -> {
			while (true) {
				sleep(300000 / 600); // 5 minutes -- keeps long-session players' rows fresh
				for (Player player : World.players()) {
					if (player != null) {
						submit(player);
					}
				}
			}
		});
	}

	public static void submit(Player player) {
		if (player == null || player.getName() == null || player.getStats() == null) {
			return;
		}

		String username = player.getName();
		int userId = player.getUserId();
		int difficultyOrdinal = player.getDifficulty() == null ? 0 : player.getDifficulty().ordinal();
		int modeOrdinal = player.getGameMode() == null ? 0 : player.getGameMode().ordinal();
		int totalLevel = player.getStats().totalLevel;
		long totalXp = player.getStats().totalXp;
		int[] skillXp = new int[SKILL_COUNT];
		for (StatType type : StatType.values()) {
			skillXp[type.ordinal()] = (int) player.getStats().get(type).experience;
		}

		// Single connection/lambda so the delete+insert can't be reordered by the
		// database executor's thread pool (each Server.gameDb.execute() call is an
		// independent task with no ordering guarantee across separate calls).
		Server.gameDb.execute(con -> {
			try (PreparedStatement delete = con.prepareStatement("DELETE FROM hs_users WHERE username = ?")) {
				delete.setString(1, username);
				delete.executeUpdate();
			}

			try (PreparedStatement insert = con.prepareStatement(
					"INSERT INTO hs_users (username, user_id, difficulty, mode, totalLevel, totalXp, " +
					"attackXp, defenceXp, strengthXp, hitpointsXp, rangedXp, prayerXp, magicXp, cookingXp, " +
					"woodcuttingXp, fletchingXp, fishingXp, firemakingXp, craftingXp, smithingXp, miningXp, " +
					"herbloreXp, agilityXp, thievingXp, slayerXp, farmingXp, runecraftingXp, hunterXp, " +
					"constructionXp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				insert.setString(1, username);
				insert.setInt(2, userId);
				insert.setInt(3, difficultyOrdinal);
				insert.setInt(4, modeOrdinal);
				insert.setInt(5, totalLevel);
				insert.setLong(6, totalXp);
				for (int i = 0; i < SKILL_COUNT; i++) {
					insert.setInt(7 + i, skillXp[i]);
				}
				insert.executeUpdate();
			}
		});
	}
}
