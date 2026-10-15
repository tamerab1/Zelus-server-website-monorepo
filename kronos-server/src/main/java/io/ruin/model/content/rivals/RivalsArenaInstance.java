package io.ruin.model.content.rivals;

import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerAction;
import io.ruin.model.entity.shared.listeners.LogoutListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.utility.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * One ranked Rivals match in its own copy of a PvP Arena fight arena (region 13362: walled, bright sand and stone),
 * so it is isolated from the world and from every other match.
 * <ol>
 * <li>Both kits are equipped (RivalsSnapshot) and the players placed 10 tiles apart on open floor.</li>
 * <li>5-second countdown: both locked, "5 .. 1, FIGHT!".</li>
 * <li>Fight: only each other can be attacked, teleports are refused, logging out is a technical loss, and after 10
 * minutes the player with more hitpoints wins.</li>
 * <li>Every death in here is caught (deathEndListener) and revived: nothing is ever dropped, also when both players die
 * on the same tick. The match ends on the first death.</li>
 * <li>Results (RivalsProfile), then both players get their real loadout back, return to where they queued, and the
 * instance is destroyed.</li>
 * </ol>
 */
final class RivalsArenaInstance {

	/**
	 * The north-west fight arena of the PvP Arena (region 13362): walled, sunlit sand and stone, good contrast for
	 * players and spell graphics (2026-10-15: replaced the TzHaar Fight Pit, too dark). Interior ~3334-3353 x 3246-3257.
	 */
	private static final int ARENA_REGION = 13362;
	/** Floor spots 10 tiles apart (searched outward for a free tile in the copy). */
	private static final Position SPAWN_A = new Position(3338, 3251, 0), SPAWN_B = new Position(3348, 3251, 0);
	private static final int TIME_LIMIT_TICKS = 1000, WARNING_TICKS = 900;
	/** Sounds (ids as specified for Rivals): countdown 5..2, the final "1 / FIGHT!", victory, defeat. */
	private static final int SOUND_TICK = 2277, SOUND_FIGHT = 2276, SOUND_VICTORY = 101, SOUND_DEFEAT = 90;
	/** The red skull overhead during the fight (deaths stay safe; it's for the high-stakes look). */
	private static final int RED_SKULL = 1;
	/** Where staff watch from: the north side of the arena (moved to a free tile). */
	private static final Position WATCH_SPOT = new Position(3343, 3256, 0);

	private final Player p1, p2;
	private final RivalsMode mode;
	private DynamicMap map;
	private boolean started, finished;
	/** Started by the developer ::rivalsforce: skips the 30-minute rematch rule (testing). */
	boolean forced;
	private final int[] skullBefore = new int[2];
	/** Staff watching (::rivalswatch) and where to send them back to. */
	private final List<Player> spectators = new ArrayList<>();
	private final List<Position> spectatorReturns = new ArrayList<>();

	RivalsArenaInstance(Player p1, Player p2, RivalsMode mode) {
		this.p1 = p1;
		this.p2 = p2;
		this.mode = mode;
	}

	boolean involves(Player p) {
		return p == p1 || p == p2;
	}

	/** Builds the arena and starts the countdown. False (and nothing changed) if no instance could be made. */
	boolean start() {
		try {
			map = new DynamicMap().build(ARENA_REGION, 0);
		} catch (Exception e) {
			ServerWrapper.logError("Rivals: unable to build the arena instance", e);
			return false;
		}
		skullBefore[0] = p1.getAppearance().getSkullIcon();
		skullBefore[1] = p2.getAppearance().getSkullIcon();
		for (Player p : new Player[]{p1, p2}) {
			p.closeInterfaces();
			p.resetActions(true, true, true);
			RivalsSnapshot.takeAndEquip(p, mode);
			// after the snapshot (so the real levels are saved as they were): no poison / venom / freeze carried in
			p.getCombat().restore();
			p.getCombat().resetKillers();
		}
		attach(p1, p2);
		attach(p2, p1);
		Position a = freeTile(SPAWN_A), b = freeTile(SPAWN_B);
		p1.getMovement().teleport(a);
		p2.getMovement().teleport(b);
		p1.face(b);
		p2.face(a);
		p1.lock();
		p2.lock();
		for (Player p : new Player[]{p1, p2})
			p.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> " + mode.label + " match vs <col=ffffff>" + (p == p1 ? p2 : p1).getName()
				+ "</col> (" + (p == p1 ? p2 : p1).rivalsRating + " SR). Get ready!");

		World.startEvent(e -> {
			e.delay(1); // the region-exit handlers of where they came from have run by now
			guard();
			while (!finished) {
				guard();
				e.delay(2);
			}
		});
		World.startEvent(e -> {
			for (int n = 5; n >= 1; n--) {
				if (finished)
					return;
				shout(String.valueOf(n));
				sound(n >= 2 ? SOUND_TICK : SOUND_FIGHT);
				e.delay(2);
			}
			if (finished)
				return;
			shout("FIGHT!");
			sound(SOUND_FIGHT);
			started = true;
			for (Player p : new Player[]{p1, p2}) {
				p.unlock();
				p.setAction(1, PlayerAction.ATTACK); // the right-click / left-click Attack option on players
				p.setAction(3, null);                 // no "Trade with" in the arena
				p.getAppearance().setSkullIcon(RED_SKULL);
			}
			e.delay(WARNING_TICKS);
			if (finished)
				return;
			p1.sendMessage("<col=ff0000>One minute left! If time runs out, the player with more hitpoints wins.");
			p2.sendMessage("<col=ff0000>One minute left! If time runs out, the player with more hitpoints wins.");
			e.delay(TIME_LIMIT_TICKS - WARNING_TICKS);
			if (finished)
				return;
			int hp1 = p1.getHp(), hp2 = p2.getHp();
			Player loser = hp1 == hp2 ? (Random.rollPercent(50) ? p1 : p2) : hp1 < hp2 ? p1 : p2;
			for (Player p : new Player[]{p1, p2})
				p.sendMessage("Time's up! " + other(loser).getName() + " wins on hitpoints.");
			finish(loser, false);
		});
		return true;
	}

	private void sound(int id) {
		p1.privateSound(id);
		p2.privateSound(id);
	}

	private void shout(String text) {
		p1.forceText(text);
		p2.forceText(text);
	}

	private Player other(Player p) {
		return p == p1 ? p2 : p1;
	}

	/** The copy of an arena spot, moved to the nearest free floor tile (spiral search, up to 6 tiles out). */
	private Position freeTile(Position spot) {
		int x = map.convertX(spot.getX()), y = map.convertY(spot.getY());
		for (int r = 0; r <= 6; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dy = -r; dy <= r; dy++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
						continue;
					Tile t = Tile.get(x + dx, y + dy, 0);
					if (t != null && t.isTileFree())
						return new Position(x + dx, y + dy, 0);
				}
		return new Position(x, y, 0);
	}

	/**
	 * The match's own listener objects per player (index 0 = p1). The Rivals lobby lies inside the Duel Arena's bounds,
	 * and DuelArena's region-exit handler NULLS a player's attack / death / teleport listeners when they leave it -- which
	 * the teleport into the instance does (2026-10-15: "You can't attack players from where you're standing"). So they
	 * are attached after arrival and re-asserted every 2 ticks until the match ends ({@link #guard}).
	 */
	private final io.ruin.model.entity.shared.listeners.AttackPlayerListener[] attackListeners =
		new io.ruin.model.entity.shared.listeners.AttackPlayerListener[2];
	private final io.ruin.model.entity.shared.listeners.DeathListener[] deathListeners =
		new io.ruin.model.entity.shared.listeners.DeathListener[2];
	private final io.ruin.model.entity.shared.listeners.TeleportListener[] teleportListeners =
		new io.ruin.model.entity.shared.listeners.TeleportListener[2];
	private final LogoutListener[] logoutListeners = new LogoutListener[2];
	/** Brackets without overhead prayers (Dharok Risk-Fight); null otherwise. */
	private final io.ruin.model.entity.shared.listeners.ActivatePrayerListener[] prayerRules =
		new io.ruin.model.entity.shared.listeners.ActivatePrayerListener[2];
	private static final java.util.Set<io.ruin.model.skills.prayer.Prayer> OVERHEADS = java.util.EnumSet.of(
		io.ruin.model.skills.prayer.Prayer.PROTECT_FROM_MAGIC, io.ruin.model.skills.prayer.Prayer.PROTECT_FROM_MISSILES,
		io.ruin.model.skills.prayer.Prayer.PROTECT_FROM_MELEE, io.ruin.model.skills.prayer.Prayer.RETRIBUTION,
		io.ruin.model.skills.prayer.Prayer.REDEMPTION, io.ruin.model.skills.prayer.Prayer.SMITE);

	/** Puts the match listeners back on a player if anything replaced them (both players, every 2 ticks). */
	private void guard() {
		for (int i = 0; i < 2; i++) {
			Player p = i == 0 ? p1 : p2;
			if (attackListeners[i] == null || !p.isOnline())
				continue;
			if (p.attackPlayerListener != attackListeners[i] || p.deathEndListener != deathListeners[i]
				|| p.teleportListener != teleportListeners[i] || p.logoutListener != logoutListeners[i]
				|| p.activatePrayerListener != prayerRules[i]) {
				p.attackPlayerListener = attackListeners[i];
				p.deathEndListener = deathListeners[i];
				p.teleportListener = teleportListeners[i];
				p.logoutListener = logoutListeners[i];
				p.activatePrayerListener = prayerRules[i];
				p.deathStartListener = null;    // no duel-arena death handling in a Rivals match
				p.allowPrayerListener = null;   // and no duel-arena prayer rules
			}
		}
	}

	private void attach(Player player, Player opponent) {
		int i = player == p1 ? 0 : 1;
		player.attackPlayerListener = (attacker, target, message) -> {
			if (!started || finished) {
				if (message)
					attacker.sendMessage("The match hasn't started yet.");
				return false;
			}
			if (target != opponent) {
				if (message)
					attacker.sendMessage("You can only attack your Rivals opponent.");
				return false;
			}
			return true;
		};
		player.deathEndListener = (entity, killer, killHit) -> {
			Player dead = entity.player;
			revive(dead);
			if (!finished)
				finish(dead, false);
		};
		player.teleportListener = p -> {
			p.sendMessage("You can't teleport out of a Rivals match.");
			return false;
		};
		player.logoutListener = new LogoutListener().onLogout(this::onLogout);
		if (mode.noOverheads) {
			for (io.ruin.model.skills.prayer.Prayer prayer : OVERHEADS)
				if (player.getPrayer().isActive(prayer))
					player.getPrayer().deactivate(prayer);
			player.activatePrayerListener = (pl, prayer) -> {
				if (!OVERHEADS.contains(prayer))
					return true;
				pl.sendMessage("Overhead prayers are disabled in " + mode.label + ".");
				return false;
			};
		} else {
			player.activatePrayerListener = null;
		}
		prayerRules[i] = player.activatePrayerListener;
		player.deathStartListener = null;
		player.allowPrayerListener = null;
		attackListeners[i] = player.attackPlayerListener;
		deathListeners[i] = player.deathEndListener;
		teleportListeners[i] = player.teleportListener;
		logoutListeners[i] = player.logoutListener;
	}

	private static void revive(Player p) {
		if (p.getCombat().isDead()) {
			p.getCombat().setDead(false);
			p.animate(-1, 0);
		}
		p.getCombat().resetKillers();
		p.getCombat().restore();
	}

	/** Logging out mid-match is a technical loss; the leaver's real loadout is restored before their save. */
	private void onLogout(Player leaver) {
		if (!finished)
			finish(leaver, true);
		release(leaver);
	}

	private void finish(Player loser, boolean technical) {
		if (finished)
			return;
		finished = true;
		Player winner = other(loser);
		String[] lines = RivalsProfile.applyResult(winner, loser, technical, forced);
		winner.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> <col=40ff40>Victory</col> vs " + loser.getName() + ": " + lines[0]);
		loser.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> <col=ff4040>Defeat</col> vs " + winner.getName() + ": " + lines[1]);
		winner.forceText("Victory!");
		winner.privateSound(SOUND_VICTORY);
		loser.privateSound(SOUND_DEFEAT);
		RivalsQueueManager.onMatchEnd(this);
		// a short pause on the result, then everyone out; a player still mid-death is revived by the listener first
		World.startEvent(e -> {
			e.delay(3);
			for (int i = 0; i < 8 && (p1.getCombat().isDead() || p2.getCombat().isDead()); i++)
				e.delay(1);
			release(p1);
			release(p2);
			for (int i = 0; i < spectators.size(); i++) {
				Player s = spectators.get(i);
				if (s.isOnline()) {
					s.getMovement().teleport(spectatorReturns.get(i));
					s.logoutListener = null;
					s.sendMessage("The Rivals match you were watching has ended.");
				}
			}
			spectators.clear();
			spectatorReturns.clear();
			if (map != null) {
				map.destroy();
				map = null;
			}
		});
	}

	String description() {
		return mode.label + ": " + p1.getName() + " vs " + p2.getName() + (started ? "" : " (countdown)");
	}

	/** Staff: watch this match from the north end of the pit; sent back when it ends. */
	boolean spectate(Player staff) {
		if (map == null || finished)
			return false;
		Position back = staff.getPosition().copy();
		spectators.add(staff);
		spectatorReturns.add(back);
		staff.getMovement().teleport(freeTile(WATCH_SPOT));
		// logging out while watching must not leave them inside an instance that is about to be destroyed
		staff.logoutListener = new LogoutListener().onLogout(p -> p.getMovement().teleport(back));
		return true;
	}

	/** Detaches the match from the player, restores their loadout and sends them back (once per player). */
	private void release(Player p) {
		if (!RivalsSnapshot.active(p) && p.attackPlayerListener == null)
			return;
		p.attackPlayerListener = null;
		p.deathEndListener = null;
		p.teleportListener = null;
		p.logoutListener = null;
		p.activatePrayerListener = null;
		p.setAction(1, null);                    // back to the normal player menu
		p.setAction(3, PlayerAction.TRADE);
		p.getAppearance().setSkullIcon(skullBefore[p == p1 ? 0 : 1]);
		p.unlock();
		revive(p);
		p.getPrayer().deactivateAll(); // match prayers must not keep draining the real Prayer level
		RivalsSnapshot.restore(p);
		p.getMovement().teleport(RivalsSnapshot.returnPosition(p));
		p.rivalsReturnPosition = null;
		DivisionRivalsInterfaceManager.refreshIfOpen(p);
	}
}
