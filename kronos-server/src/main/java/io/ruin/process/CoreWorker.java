package io.ruin.process;

import io.ruin.HooksV2;
import io.ruin.Server;
import io.ruin.api.utils.Random;
import io.ruin.db.PlayerDatabase;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.activities.pkbots.ZelusBotPlayer;
import io.ruin.model.entity.player.Player;
import io.ruin.model.object.owned.OwnedObject;
import lombok.extern.slf4j.Slf4j;
import org.rsmod.util.RsmodGlobal;

@Slf4j
public final class CoreWorker extends World {

	public interface Hook {
		record PostUpdate(int tick) implements Hook {
		}
	}

	public static final HooksV2<Hook> hooks = new HooksV2<>(Hook.class);

	private static volatile Stage processStage = Stage.INDEX;
	private static int tick = 0;

	public enum Stage {

		INDEX(CoreWorker::index), LOGIC(CoreWorker::logic), UPDATE(CoreWorker::update);

		private final Runnable runnable;

		Stage(Runnable runnable) {
			this.runnable = runnable;
		}

		public static final Stage[] values = values();

	}

	public static void process() {
		tick += 1;
		for (final Stage stage : Stage.values) {
			processStage = stage;
			processStage.runnable.run();
		}

		savePlayers();
	}

	// Every online player used to be queued for a save on the exact same tick (tick % 50 == 0,
	// i.e. once every 30s) -- with each save serializing a player's full state (~140-150KB of
	// JSON observed in production) across only 4 save-worker threads (World.db_threads), that put
	// a synchronized multi-MB allocation/IO burst on every 30s boundary. That's the prime suspect
	// for a periodic, several-second stall affecting every player's packet flush at once (ground
	// items, inventory, and equipped-appearance updates all going invisible until the burst
	// cleared and the backlog flushed through) -- reported as "random" because it only visibly
	// affected whoever's update happened to land inside that window.
	//
	// Fix: spread saves across all 50 ticks by player index instead of bursting everyone on the
	// same tick. Each player still saves roughly once every 30s, just not all in the same instant.
	private static void savePlayers() {
		int pendingCount = PlayerDatabase.db().pendingSaves();
		int queuedCount = 0;

		for (final Player player : players()) {
			// Bots are in-memory only — never save them to the database
			if (player instanceof ZelusBotPlayer) {
				continue;
			}

			if (player.logoutProcessStage != Player.LogoutStage.NoLogout) {
				continue;
			}

			if (player.getIndex() % 50 != tick % 50) {
				continue;
			}

			if (PlayerDatabase.insertQueue(player)) {
				queuedCount++;
			}
		}

		if (queuedCount > 0) {
			log.trace("Queued saves for " + queuedCount + " players, pending: " + pendingCount);
		}
	}

	public static boolean isPast(final Stage stage) {
		return processStage.ordinal() > stage.ordinal();
	}

	/**
	 * Index - Used for indexing players & npcs.
	 */

	private static int scrambleTicks;

	private static void index() {
		CoreWorker.index0();
	}

	private static void index0() {
		/*
		 * Scrambling
		 */
		if (--scrambleTicks <= 0) {
			scrambleTicks = World.isPVP()
					? Random.get(24, 36)
					: Random.get(100, 150);
			World.playersScramble();
		}
	}

	private static void logic() {
		CoreWorker.logic0();
	}

	/**
	 * Logic - Things like packet handling, combat, movement, etc.
	 */
	private static void logic0() {
		var tick = true;
		for (final Player player : World.players()) {
			try {
				player.checkLogout();
			} catch (final Throwable t) {
				Server.logError(player.captureState(), t);
			}
		}
		RsmodGlobal.getGameCycle().preTick();
		RsmodGlobal.buildAreaTick();
		RsmodGlobal.getGameCycle().getMapClock().tick();
		if (tick) {
			for (final Object o : npcsSlots()) {
				if (o == null) {
					continue;
				}

				final NPC npc = (NPC) o;

				try {
					npc.processed = true;
					npc.process();
				} catch (final Throwable t) {
					Server.logError(npc.captureState(), t);
				}
			}
			for (final Player player : World.players()) {
				try {
					if (player.isOnline()) {
						player.processed = true;
						player.process();
					}
				} catch (final Throwable t) {
					Server.logError(player.captureState(), t);
				}
			}
			for (final OwnedObject object : ownedObjects.values()) {
				try {
					object.tick();
				} catch (final Throwable t) {
					Server.logError("", t);
				}
			}
		}
	}

	private static void update() {
		CoreWorker.update0();
	}

	/**
	 * Update - Things like updating local players, local npcs, writing packets to the channel, etc.
	 */

	private static void update0() {
		for (Player player : World.players()) {
			try {
				if (!player.isOnline()) {
					continue;
				}
				player.getNpcUpdater().process();
				player.getUpdater().process();
				player.sendVarps();
			} catch (Throwable t) {
				Server.logError("", t);
			}
		}
		hooks.handle(new Hook.PostUpdate(tick));
		RsmodGlobal.getGameCycle().postTick();
	}

}
