package io.ruin.model.activities.bosses.sylvaroth;

import io.ruin.api.utils.Random;
import io.ruin.cache.NPCType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

// Sylvaroth -- a bigger, recolored (multi-hue) reskin of the classic Ent (npc 6594), wired up
// entirely through data/npcs/combat/sylvaroth.json's "handler" field (no manual
// NPCType.registerCombat() call needed -- npc_combat.java's fromJson() does that automatically
// for any combat json carrying a "handler" class name).
//
// Pure ranged/magic attacker (no melee) -- every attack is a real, visible Projectile with real
// travel time, so players see it coming and have a chance to react/pray, matching the exact
// technique Zulrah uses for its own ranged/magic split. Permanently immune to melee (same
// preDefend-block pattern Dusk uses for its own ranged/magic immunity, just flipped to melee).
// Can spread its attacks across multiple nearby players (not just its current target), and on
// death unleashes a multi-wave "Splinter Cascade" that hits every nearby player a few times in a
// row -- the "many moving flows" death special, same World/npc-event staged-wave pattern Dusk
// uses for its own end-of-fight AOE.
@Slf4j
public class Sylvaroth extends NPCCombat {

	public static final int SYLVAROTH = 30557;

	// Same projectile ids/travel params Zulrah uses for its own ranged/magic attacks -- proven
	// visible in-game already, not a guess.
	private static final Projectile RANGED_PROJECTILE = new Projectile(1044, 65, 20, 20, 15, 12, 15, 10);
	private static final Projectile MAGIC_PROJECTILE = new Projectile(1046, 65, 20, 20, 15, 12, 15, 10);

	// Minimum projectile flight time (in the same 20ms client-cycle units Projectile.send()
	// returns), regardless of distance. Projectile.send()'s duration scales with distance
	// (durationStart + distance*durationIncrement) -- a pure ranged/magic attacker that closes to
	// melee range gets a near-zero flight time and the hit lands almost the instant the
	// projectile appears, giving no time to react/switch prayer. Fixed two ways: stay at range
	// instead of closing in (follow(5) below), AND floor the duration so even a point-blank shot
	// still gives a fair reaction window.
	private static final int MIN_PROJECTILE_DURATION = 90; // ~1.8s

	// Chance each attack also strikes a second random nearby player (not the primary target) with
	// the same style -- lets the boss meaningfully engage multiple attackers instead of only ever
	// hitting whoever it's currently locked onto.
	private static final double MULTI_TARGET_CHANCE = 0.35;

	private static final int DEATH_SPECIAL_WAVES = 3;

	// Splinter Wave -- a line of moving hazards sweeps across the arena floor and players must
	// step out of its path or take heavy damage. Same spawn-a-line-of-NPCs-and-step-them-forward
	// technique Galvek's tsunami special uses (see Galvek.java#waveAttack), reused rather than
	// reinvented -- WAVE_NPC_ID 8099 is the same stock "Tsunami" NPC that attack already relies
	// on. Everything below is computed relative to Sylvaroth's own live position rather than
	// hardcoded region coordinates (unlike Galvek's arena-specific version), since the safe/clear
	// radius around her spawn is only confirmed out to ARENA_OBJECT_SCAN_RADIUS (15 tiles, see
	// clearArenaClutter()) -- both the sweep width and travel distance below stay comfortably
	// inside that.
	private static final int WAVE_NPC_ID = 8099;
	private static final int WAVE_HALF_WIDTH = 6;
	private static final int WAVE_SWEEP_RANGE = 10;
	private static final int WAVE_ATTACK_INTERVAL = 6;
	private final List<NPC> waves = new ArrayList<>();
	private int attacksSinceWave = 0;

	// Group loot: everyone who dealt at least this share of the kill's total damage gets their
	// own independent handleNewDrop() roll (same call every solo kill already uses), instead of
	// only whichever single player NPCCombat's generic getKiller() would otherwise pick. Reuses
	// the existing Combat.killers damage bookkeeping (inherited from NPCCombat/Combat) rather
	// than tracking damage a second time. More damage share means more roll attempts, so heavier
	// contributors get meaningfully better odds, not just a participation drop.
	private static final double DAMAGE_SHARE_THRESHOLD = 0.10;

	public static void register() {
		NPCType.registerCombat(Sylvaroth.class, SYLVAROTH);
	}

	// Object 5582 ("Logs") sits somewhere in the reused terrain around her arena, cluttering the
	// fight space -- removed once, the first time she's spawned (there's only ever one Sylvaroth
	// in the world, so init() running once at boot is sufficient). Scans a generous box around her
	// spawn rather than a single hardcoded tile, since the exact tile wasn't hand-measured; finding
	// none is a harmless no-op, not an error.
	private static final int OBJ_LOGS = 5582;
	private static final int ARENA_OBJECT_SCAN_RADIUS = 15;
	private static boolean arenaObjectsCleared = false;

	private boolean headIconSet = false;

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDefend(this::blockMelee);
		if (!arenaObjectsCleared) {
			arenaObjectsCleared = true;
			clearArenaClutter();
		}
	}

	private void clearArenaClutter() {
		try {
			int centerX = npc.getPosition().getX();
			int centerY = npc.getPosition().getY();
			int z = npc.getPosition().getZ();
			int removed = 0;
			for (int dx = -ARENA_OBJECT_SCAN_RADIUS; dx <= ARENA_OBJECT_SCAN_RADIUS; dx++) {
				for (int dy = -ARENA_OBJECT_SCAN_RADIUS; dy <= ARENA_OBJECT_SCAN_RADIUS; dy++) {
					Tile tile = Tile.get(centerX + dx, centerY + dy, z, true);
					if (tile == null || tile.gameObjects == null) {
						continue;
					}
					for (GameObject obj : new ArrayList<>(tile.gameObjects)) {
						if (obj != null && obj.getId() == OBJ_LOGS) {
							obj.remove();
							removed++;
						}
					}
				}
			}
			if (removed > 0) {
				log.info("Sylvaroth: removed {} instance(s) of object {} from the arena", removed, OBJ_LOGS);
			}
		} catch (Exception e) {
			log.error("Sylvaroth: failed to clear arena clutter (non-fatal, fight is unaffected)", e);
		}
	}

	private void blockMelee(Hit hit) {
		if (hit.attackStyle != null && hit.attackStyle.isMelee()) {
			hit.block();
		}
	}

	@Override
	public void follow() {
		follow(5);
	}

	@Override
	public boolean attack() {
		if (!target.getPosition().isWithinDistance(npc.getPosition(), 10)) {
			return false;
		}

		if (++attacksSinceWave >= WAVE_ATTACK_INTERVAL) {
			attacksSinceWave = 0;
			splinterWaveAttack();
			return true;
		}

		boolean magic = Math.random() < 0.5;
		fireAt(target, magic);

		if (Math.random() < MULTI_TARGET_CHANCE) {
			List<Player> nearby = new ArrayList<>(npc.localPlayers());
			nearby.remove(target);
			nearby.removeIf(p -> p.dead() || !p.getPosition().isWithinDistance(npc.getPosition(), 10));
			if (!nearby.isEmpty()) {
				fireAt(Random.get(nearby), Math.random() < 0.5);
			}
		}
		return true;
	}

	@Override
	public void process() {
		// setHeadIcon() called from init() silently no-ops (the npc's avatar isn't assigned yet
		// at that point) -- must be deferred to process(), same gotcha documented for
		// NPCCombat's headIcon usage elsewhere in this project.
		if (!headIconSet) {
			npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromMelee);
			headIconSet = true;
		}
	}

	private void fireAt(Entity victim, boolean magic) {
		npc.face(victim);
		npc.animate(info.attack_animation);
		Projectile projectile = magic ? MAGIC_PROJECTILE : RANGED_PROJECTILE;
		AttackStyle style = magic ? AttackStyle.MAGIC : AttackStyle.RANGED;
		int duration = Math.max(projectile.send(npc, victim), MIN_PROJECTILE_DURATION);
		victim.hit(new Hit(npc, style).randDamage(info.max_damage).clientDelay(duration));
	}

	private void splinterWaveAttack() {
		npc.animate(info.attack_animation);
		int centerX = npc.getPosition().getX();
		int centerY = npc.getPosition().getY();
		int z = npc.getPosition().getZ();

		for (Player p : npc.localPlayers()) {
			if (!p.dead() && p.getPosition().isWithinDistance(npc.getPosition(), 15)) {
				p.sendMessage("Sylvaroth unleashes a wave of splintering roots -- move out of its path!");
			}
		}

		int startX = centerX - WAVE_SWEEP_RANGE;
		int endX = centerX + WAVE_SWEEP_RANGE;
		for (int dy = -WAVE_HALF_WIDTH; dy <= WAVE_HALF_WIDTH; dy++) {
			NPC wave = new NPC(WAVE_NPC_ID).spawn(new Position(startX, centerY + dy, z));
			waves.add(wave);
			wave.startEvent(event -> {
				int ticks = 0;
				while (ticks++ < WAVE_SWEEP_RANGE * 2 && wave.getPosition().getX() < endX) {
					event.delay(1);
					wave.step(1, 0, StepType.WALK);
					for (Player p : npc.localPlayers()) {
						if (!p.dead() && p.isAt(wave.getPosition())) {
							p.hit(new Hit(npc).fixedDamage(p.getHp()));
						}
					}
				}
				wave.remove();
				waves.remove(wave);
			});
		}
	}

	@Override
	public void startDeath(Hit killHit) {
		setDead(true);
		if (target != null) {
			reset();
		}
		List<Player> witnesses = new ArrayList<>(npc.localPlayers());
		Player primaryKiller = getKiller() != null ? getKiller().player : null;
		npc.addEvent(event -> {
			for (int wave = 0; wave < DEATH_SPECIAL_WAVES; wave++) {
				for (Player p : witnesses) {
					if (p.dead() || !p.getPosition().isWithinDistance(npc.getPosition(), 15)) {
						continue;
					}
					fireAt(p, wave % 2 == 0);
				}
				event.delay(1);
			}
			distributeGroupDrops(primaryKiller);
			super.startDeath(killHit);
		});
	}

	private void distributeGroupDrops(Player primaryKiller) {
		if (killers == null || killers.isEmpty()) {
			return;
		}
		int totalDamage = 0;
		for (Killer k : killers.values()) {
			totalDamage += k.damage;
		}
		if (totalDamage <= 0) {
			return;
		}
		for (Killer k : killers.values()) {
			Player player = k.player;
			if (player == null) {
				continue;
			}
			double share = k.damage / (double) totalDamage;
			if (share < DAMAGE_SHARE_THRESHOLD) {
				continue;
			}
			int rolls = share >= 0.50 ? 3 : share >= 0.25 ? 2 : 1;
			// primaryKiller already gets one roll from the normal single-killer path in
			// super.startDeath() (called right after this) -- only give them the extra rolls
			// their damage share earns on top of that, so their total matches everyone else's.
			int extraRolls = player == primaryKiller ? rolls - 1 : rolls;
			for (int i = 0; i < extraRolls; i++) {
				handleNewDrop(player, npc.getId(), player.getPosition());
			}
		}
	}

}
