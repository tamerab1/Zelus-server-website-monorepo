package io.ruin.model.activities.colosseum;

import io.ruin.model.World;
import io.ruin.model.activities.bosses.custom.CustomBossCombat;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.dynamic.DynamicMap;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * One Colosseum of Trials fight (2026-11-07): a private copy of the Colosseum (region {@link Colosseum#REGION}) for a solo
 * player or a duo, its bosses in waves, and the score.
 * <ul>
 * <li>duo: the arena is multi-combat so both players can fight the boss, every boss has +60% hitpoints (max hit
 * unchanged) and its normal attacks hit both players (CustomBossCombat.afterAttack);</li>
 * <li>deaths are safe (nothing is lost): the dead player is sent back to the lobby; when nobody is left in the arena the
 * bosses are removed and the instance released;</li>
 * <li>a clear pays {@link Trial#points} x the speed multiplier, split in a duo by each player's share of the damage
 * (under {@link #MIN_SHARE} earns nothing); drops are rolled per player by each boss's own table.</li>
 * </ul>
 */
@Slf4j
public final class TrialSession {

	static final double DUO_HP = 1.6, MIN_SHARE = 0.10;
	static final int NEXT_WAVE_TICKS = 4, RETURN_TICKS = 10;

	final Trial trial;
	final boolean duo;
	final List<Player> members;
	private final DynamicMap map;
	/** damage dealt by each member (user id) over every wave */
	private final Map<Integer, Integer> damage = new HashMap<>();
	/** members still inside the arena */
	private final Set<Player> inside = new HashSet<>();
	private int wave = -1, startTick;
	private NPC boss;
	private boolean over;

	private TrialSession(Trial trial, List<Player> members, DynamicMap map) {
		this.trial = trial;
		this.duo = members.size() > 1;
		this.members = members;
		this.map = map;
	}

	/** Builds the arena and brings the players in; null (with a message) if it can't be built. */
	static TrialSession start(Trial trial, List<Player> members) {
		DynamicMap map;
		try {
			map = new DynamicMap().build(Colosseum.REGION, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("colosseum: couldn't build an arena", e);
			members.forEach(p -> p.sendMessage("The arena can't be opened right now. Try again in a moment."));
			return null;
		}
		if (members.size() > 1)
			map.makeDynamicMapMultiCombat();
		TrialSession s = new TrialSession(trial, members, map);
		for (Player p : members)
			s.bringIn(p);
		World.startEvent(e -> {
			e.delay(3);
			s.startTick = (int) io.ruin.Server.currentTick();
			s.nextWave();
		});
		return s;
	}

	/** Raid-style: the screen fades to black, the player is moved in, then it fades back (no teleport animation). */
	private void bringIn(Player player) {
		player.lock();
		Colosseum.fadeOut(player);
		player.addEvent(event -> {
			event.delay(2);
			player.isInstanced = true;
			player.currentDynamicMap = map;
			player.inDynamicMap = true;
			inside.add(player);
			player.getMovement().teleport(map.convertX(Colosseum.ARENA_ENTRY.getX()), map.convertY(Colosseum.ARENA_ENTRY.getY()), 0);
			player.face(Colosseum.ARENA_ENTRY_FACING);
			player.sendMessage("<col=7f0000>" + trial.title + "</col>" + (duo ? " (duo)" : "") + " - " + trial.waves.length
					+ (trial.waves.length == 1 ? " boss." : " waves.") + " Teleport out to forfeit.");
			player.deathEndListener = (entity, killer, killHit) -> died(player);
			map.assignListener(player).onExit((p, logout) -> left(p, logout));
			event.delay(1);
			Colosseum.fadeIn(player);
			player.unlock();
		});
	}

	private void nextWave() {
		if (over || inside.isEmpty())
			return;
		wave++;
		int id = trial.waves[wave];
		NPC npc = new NPC(id).spawn(map.convertX(Colosseum.ARENA_BOSS.getX()), map.convertY(Colosseum.ARENA_BOSS.getY()), 0,
				Colosseum.ARENA_BOSS_FACING, 6);
		npc.getCombat().setAllowRespawn(false);
		npc.noKillPoints = true; // Trial Marks only: no Zelus / PVM points
		map.addNpc(npc);
		if (duo) {
			int hp = (int) Math.round(npc.getMaxHp() * DUO_HP);
			npc.setMaxHp(hp);
			npc.setHp(hp);
		}
		if (npc.getCombat() instanceof CustomBossCombat c)
			c.arena = this::playersInside;
		npc.graphics(1039);
		boss = npc;
		if (trial.waves.length > 1)
			forInside(p -> p.sendMessage("<col=7f0000>Wave " + (wave + 1) + "/" + trial.waves.length + ":</col> " + npc.getDef().name));
		npc.deathStartListener = (entity, killer, killHit) -> countDamage(npc);
		npc.deathEndListener = (entity, killer, killHit) -> {
			boss = null;
			if (wave + 1 < trial.waves.length)
				World.startEvent(e -> {
					e.delay(NEXT_WAVE_TICKS);
					nextWave();
				});
			else
				victory();
		};
	}

	private void countDamage(NPC npc) {
		HashMap<Integer, Killer> killers = npc.getCombat().killers;
		if (killers == null)
			return;
		for (var e : killers.entrySet())
			damage.merge(e.getKey(), e.getValue().damage, Integer::sum);
	}

	private void victory() {
		over = true;
		int ticks = (int) io.ruin.Server.currentTick() - startTick;
		double mult = trial.speedMultiplier(ticks);
		int total = (int) Math.round(trial.points * mult);
		int sum = damage.values().stream().mapToInt(Integer::intValue).sum();
		String time = Colosseum.formatTicks(ticks);
		for (Player p : members) {
			if (!inside.contains(p))
				continue;
			int earned;
			if (!duo)
				earned = total;
			else {
				double share = sum == 0 ? 0 : damage.getOrDefault(p.getUserId(), 0) / (double) sum;
				earned = share < MIN_SHARE ? 0 : (int) Math.round(total * share);
				p.sendMessage("Your share of the damage: " + Math.round(share * 100) + "%"
						+ (share < MIN_SHARE ? " - under 10%, so no Trial Marks this time." : "."));
			}
			earned = EggEffects.trialMarks(p, earned);
			p.trialMarks += earned;
			p.sendMessage("<col=7f0000>" + trial.title + " cleared in " + time + "</col>"
					+ (mult > 1 ? " (speed bonus x" + mult + ")" : "") + ". You earn <col=005f00>" + earned
					+ "</col> Trial Marks (total " + p.trialMarks + ").");
			Colosseum.recordBest(p, trial, duo, ticks);
			io.ruin.model.content.codex.ZelusTask.TRIAL_CONTENDER.increment(p, 1);
			if (trial == Trial.BALAROK)
				io.ruin.model.content.codex.ZelusTask.BANE_OF_THE_BEHEMOTH.increment(p, 1);
			p.sendMessage("You'll be returned to the Colosseum in a few seconds.");
		}
		World.startEvent(e -> {
			e.delay(RETURN_TICKS);
			List<Player> back = new ArrayList<>(inside);
			back.forEach(Colosseum::fadeOut);
			e.delay(2);
			for (Player p : back)
				Colosseum.walkOut(p);
		});
	}

	/** Safe death: the player is moved out (which runs {@link #left}). */
	private void died(Player player) {
		player.sendMessage("You have been defeated. Nothing is lost in the Colosseum.");
		forInside(p -> {
			if (p != player)
				p.sendMessage(player.getName() + " has fallen!");
		});
		player.getMovement().teleport(Colosseum.LOBBY);
		player.face(io.ruin.model.map.Direction.SOUTH);
	}

	private void left(Player player, boolean logout) {
		if (logout)
			player.getMovement().teleport(Colosseum.LOBBY);
		player.currentDynamicMap = null;
		player.inDynamicMap = false;
		player.isInstanced = false;
		player.deathEndListener = null;
		inside.remove(player);
		if (!inside.isEmpty()) {
			if (!over)
				forInside(p -> p.sendMessage(player.getName() + " has left the arena."));
			return;
		}
		// nobody left: remove the bosses and release the instance
		over = true;
		if (boss != null && !boss.isRemoved())
			boss.remove();
		boss = null;
		map.destroy();
		Colosseum.ended(this);
	}

	List<Player> playersInside() {
		List<Player> l = new ArrayList<>();
		for (Player p : inside)
			if (p.isOnline() && !p.getCombat().isDead())
				l.add(p);
		return l;
	}

	private void forInside(java.util.function.Consumer<Player> action) {
		new ArrayList<>(inside).forEach(action);
	}
}
