package io.ruin.model.activities.moonsofperil.bosses;

import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.World;

import java.util.*;

/**
 * Blue Moon (npc 13017, 500 HP, flat armour 5).
 * <ul>
 * <li>Curse of the Moon (cold): each landed combo hit adds 1/2/5 stacks, decaying 2 every 5 ticks. At 3+ stacks the
 * player's attacks slow by a tick. Relighting both braziers clears it.</li>
 * <li>Weapon Freeze: the player's weapon is forced into one of three blocks of ice; punch the one with Eyatlalli's
 * glyph under it (70 HP, 10 per hit) to free it. Icy spikes telegraph around the blocks and erupt 3 ticks later.
 * Failing to break it in time costs up to 16; starting unarmed costs 3-15. The Moon is gone for the phase.</li>
 * <li>Frost Storm: both braziers are shrouded and tornadoes sweep the arena north/south (8 damage on contact). The Moon
 * heals 10 per unlit brazier every 10 ticks, up to five times. Relighting both clears the cold.</li>
 * </ul>
 */
public class BlueMoon extends MoonBoss {

	public static final int ID = 13017; // pmoon_boss_blue_moon (multivar -> 13013 vis)
	public static final int SPAWN_X = 1438, SPAWN_Y = 9678, ENTRY_X = 1441, ENTRY_Y = 9684;

	private static final int[][] SIGILS = { { 1441, 9683 }, { 1443, 9681 }, { 1443, 9678 }, { 1438, 9676 }, { 1436, 9678 },
		{ 1436, 9681 } };

	private static final int COMBO_ANIM = 11014, MAGIC_ANIM = 11007, RETURN_ANIM = 11011;
	private static final int MAGIC_GFX = 2783, FREEZE_GFX = 2754, STUN_GFX = 245, TORNADO_IMPACT_GFX = 502;
	private static final int ICE_SPAWN_GFX = 2769, ICE_IDLE_GFX = 2770, ICE_ATTACK_GFX = 2771;
	private static final int TORNADO = 13027, TORNADO_ANIM = 10970, BLUE_FIRE = 51053;
	public static final int BRAZIER_LEFT = 52992, BRAZIER_RIGHT = 52993;
	public static final int ICICLE = 13025, CRACKED_ICICLE = 13026;
	private static final int ICICLE_GLOW_ANIM = 11031;
	private static final int[][] ICICLES = { { 1434, 9678, 52981 }, { 1443, 9676, 52983 }, { 1441, 9685, 52985 } };

	private static final int ARENA_MIN_X = 1433, ARENA_MAX_X = 1452, ARENA_MIN_Y = 9672, ARENA_MAX_Y = 9688;

	private static final int[][] ICE_CROSS = { { 0, -3 }, { 1, -3 }, { 0, -2 }, { 1, -2 }, { 0, -1 }, { 1, -1 }, { 0, 2 }, { 1, 2 },
		{ 0, 3 }, { 1, 3 }, { 0, 4 }, { 1, 4 }, { -3, 0 }, { -2, 0 }, { -1, 0 }, { 2, 0 }, { 3, 0 }, { 4, 0 }, { -3, 1 }, { -2, 1 },
		{ -1, 1 }, { 2, 1 }, { 3, 1 }, { 4, 1 }, { -1, -1 }, { 2, -1 }, { -1, 2 }, { 2, 2 } };
	private static final int[][] ICE_BIG = { { -3, -3 }, { -2, -3 }, { -1, -3 }, { 2, -3 }, { 3, -3 }, { 4, -3 }, { -3, -2 }, { -2, -2 },
		{ -1, -2 }, { 2, -2 }, { 3, -2 }, { 4, -2 }, { -3, -1 }, { -2, -1 }, { -1, -1 }, { 0, -1 }, { 1, -1 }, { 2, -1 }, { 3, -1 },
		{ 4, -1 }, { -1, 0 }, { 2, 0 }, { -1, 1 }, { 2, 1 }, { -3, 2 }, { -2, 2 }, { -1, 2 }, { 0, 2 }, { 1, 2 }, { 2, 2 }, { 3, 2 },
		{ 4, 2 }, { -3, 3 }, { -2, 3 }, { -1, 3 }, { 2, 3 }, { 3, 3 }, { 4, 3 }, { -3, 4 }, { -2, 4 }, { -1, 4 }, { 2, 4 }, { 3, 4 },
		{ 4, 4 } };

	public static final String FROZEN_KEY = "MOON_WEAPON_FROZEN";
	private static final String COLD_KEY = "MOON_COLD";

	/* curse */
	private int cold = 0;

	/* weapon freeze */
	private boolean freeze;
	private int freezeTicks;
	private final List<NPC> icicles = new ArrayList<>();
	private NPC target;
	private int iceDamage;
	private boolean broken;
	private Item frozenWeapon;
	private int iceWaveIn, iceWave;
	private final Map<Position, Integer> iceErupts = new HashMap<>();

	/* frost storm */
	private boolean storm;
	private int stormTicks, healIn, heals;
	private boolean leftShrouded, rightShrouded;
	private final Map<NPC, int[]> tornadoes = new HashMap<>(); // npc -> {ticksLeft, dy}

	@Override
	protected int[][] sigils() {
		return SIGILS;
	}

	@Override
	protected int comboAnim() {
		return COMBO_ANIM;
	}

	/* client state: pmoon_boss_blue_wearprevent / brazier_lit_left / brazier_lit_right */
	private static final io.ruin.model.var.VarPlayerRepository V_WEARPREVENT = io.ruin.model.var.VarPlayerRepository.varpbit(9854, false),
		V_LIT_LEFT = io.ruin.model.var.VarPlayerRepository.varpbit(9855, false),
		V_LIT_RIGHT = io.ruin.model.var.VarPlayerRepository.varpbit(9856, false);
	private static final int TORNADO_DAMAGE = 8; // RSProx log: ~8-9 frost damage for sharing a tornado's tile

	@Override
	protected int condition() {
		return cold;
	}

	private void syncBraziers() {
		V_LIT_LEFT.set(player, leftShrouded ? 0 : 1);
		V_LIT_RIGHT.set(player, rightShrouded ? 0 : 1);
	}

	@Override
	protected int flatArmour() {
		return -5; // wiki: flat armour -5, i.e. every hit lands 5 higher
	}

	/* ------------------------------------------------------------------ curse of the moon */

	@Override
	protected void onComboHit(int index, int damage) {
		if (damage <= 0)
			return;
		addCold(index == 0 ? 1 : index == 1 ? 2 : 5);
	}

	/** wiki Curse of the Moon: messages at 3 and 9 stacks, capped at 127 */
	private void addCold(int stacks) {
		int before = cold;
		cold = Math.min(127, cold + stacks);
		if (before < 3 && cold >= 3)
			player.sendMessage("The Blue Moon pierces your defence. The cold is starting to get in...");
		else if (before < 9 && cold >= 9)
			player.sendMessage("The Blue Moon pierces your defence. Your joints are starting to lock up...");
		player.set(COLD_KEY, cold);
	}

	@Override
	protected void offGlyphCurse() {
		addCold(1);
	}

	@Override
	protected void standardTick() {
		if (cold > 0 && io.ruin.Server.currentTick() % 5 == 0) {
			cold = Math.max(0, cold - 2);
			player.set(COLD_KEY, cold);
		}
	}

	@Override
	protected void modifyPlayerHit(Hit hit) {
		// at 18 stacks the next attack is cancelled and 18 stacks are removed
		if (cold >= 18 && hit.attackStyle != null) {
			hit.block();
			cold -= 18;
			player.set(COLD_KEY, cold);
			player.sendMessage("Your joints lock up as you go to attack. The cold is too much to bear!");
		}
	}

	/* ------------------------------------------------------------------ specials */

	@Override
	protected void startSpecial(boolean a) {
		if (a)
			startStorm();
		else
			startFreeze();
	}

	@Override
	protected boolean specialTick() {
		if (storm)
			return stormTick();
		if (freeze)
			return freezeTick();
		return false;
	}

	/* ---------------- frost storm */

	private void startStorm() {
		storm = true;
		stormTicks = 50;
		healIn = 10;
		heals = 0;
		npc.face(pos(1440, 9672));
		npc.animate(MAGIC_ANIM);
		npc.graphics(MAGIC_GFX, 255, 30);
		for (int x = ARENA_MIN_X; x <= 1447; x++) {
			spawnObject(BLUE_FIRE, x, ARENA_MIN_Y, 10, 0);
			spawnObject(BLUE_FIRE, x, ARENA_MAX_Y, 10, 0);
		}
		leftShrouded = rightShrouded = true;
		syncBraziers();
		player.getMovement().teleport(mx(1440), my(9677), 0);
		player.graphics(STUN_GFX, 124, 0);
		player.stun(2, true);
	}

	private boolean stormTick() {
		if (stormTicks > 18) {
			for (int i = 0; i < 2; i++) {
				boolean north = rand(0, 1) == 0;
				int x = rand(ARENA_MIN_X, ARENA_MAX_X);
				NPC t = spawnAdd(TORNADO, x, north ? ARENA_MAX_Y : ARENA_MIN_Y);
				t.animate(TORNADO_ANIM);
				tornadoes.put(t, new int[] { 18, north ? -1 : 1 });
			}
		}
		for (Iterator<Map.Entry<NPC, int[]>> it = tornadoes.entrySet().iterator(); it.hasNext();) {
			Map.Entry<NPC, int[]> e = it.next();
			NPC t = e.getKey();
			int[] s = e.getValue();
			if (--s[0] <= 0) {
				removeAdd(t);
				it.remove();
				continue;
			}
			t.stepAbs(t.getAbsX(), t.getAbsY() + s[1], StepType.WALK);
			if (player.getAbsX() == t.getAbsX() && player.getAbsY() == t.getAbsY()) {
				// wiki: the storms deal damage and force a 2-tick walk; carried two tiles along its path
				player.graphics(TORNADO_IMPACT_GFX, 60, 0);
				damagePlayer(TORNADO_DAMAGE - 1, TORNADO_DAMAGE + 1);
				player.resetSteps();
				player.getMovement().force(0, s[1] * 2, 0, 0, 0, 60, s[1] > 0 ? io.ruin.model.map.Direction.SOUTH : io.ruin.model.map.Direction.NORTH);
			}
		}
		if (--healIn <= 0) {
			healIn = 10;
			int unlit = (leftShrouded ? 1 : 0) + (rightShrouded ? 1 : 0);
			if (unlit > 0 && heals < 5) {
				heals++;
				healMoon(10 * unlit);
			}
		}
		if (--stormTicks <= 0) {
			endStorm();
			return false;
		}
		return true;
	}

	private void endStorm() {
		storm = false;
		for (NPC t : new ArrayList<>(tornadoes.keySet()))
			removeAdd(t);
		tornadoes.clear();
		leftShrouded = rightShrouded = false;
		if (player != null)
			syncBraziers();
	}

	/** "Relight" on a brazier during the Frost Storm. */
	public static void relight(Player player, boolean left) {
		NPC moon = player.get("MOON_NPC");
		if (moon == null || !(moon.getCombat() instanceof BlueMoon b) || !b.storm) {
			player.sendMessage("The brazier is burning steadily.");
			return;
		}
		if (left ? !b.leftShrouded : !b.rightShrouded) {
			player.sendMessage("This brazier is already lit.");
			return;
		}
		player.animate(811);
		if (left)
			b.leftShrouded = false;
		else
			b.rightShrouded = false;
		b.syncBraziers();
		player.sendMessage("You dispel the blue moon's shroud over the brazier, causing it to re-ignite!");
		if (!b.leftShrouded && !b.rightShrouded) {
			b.cold = 0;
			player.set(COLD_KEY, 0);
			player.sendMessage("Lighting the final brazier fills you with warmth, removing all of your cold buildup.");
		}
	}

	/* ---------------- weapon freeze */

	private void startFreeze() {
		freeze = true;
		freezeTicks = 53;
		broken = false;
		iceDamage = 0;
		iceWave = 0;
		iceWaveIn = 2;
		npc.face(pos(1440, 9672));
		npc.animate(MAGIC_ANIM);
		int glow = rand(0, ICICLES.length - 1);
		for (int i = 0; i < ICICLES.length; i++) {
			NPC block = spawnAdd(ICICLE, ICICLES[i][0], ICICLES[i][1]);
			block.hitListener = new HitListener().preDamage(h -> iceHit(block, h));
			icicles.add(block);
			if (i == glow) {
				target = block;
				block.animate(ICICLE_GLOW_ANIM);
				spawnObject(ICICLES[i][2], ICICLES[i][0], ICICLES[i][1], 10, 3);
			}
		}
		player.set(FROZEN_KEY, true);
		V_WEARPREVENT.set(player, 1);
		Item weapon = player.getEquipment().get(Equipment.SLOT_WEAPON);
		player.animate(3170);
		player.graphics(FREEZE_GFX);
		if (weapon != null) {
			frozenWeapon = weapon;
			player.getEquipment().set(Equipment.SLOT_WEAPON, null);
			player.getEquipment().sendUpdates();
		} else {
			player.sendMessage("<col=00ffff>With no weapon in hand, your hands begin to freeze over!</col>");
			damagePlayer(3, 15);
		}
		npc.addEvent(e -> {
			e.delay(2);
			if (freeze)
				hideMoon(true);
		});
	}

	private void iceHit(NPC block, Hit hit) {
		if (!freeze || hit.attacker == null || hit.attacker.player == null) {
			hit.block();
			return;
		}
		if (block != target) {
			hit.block();
			hit.attacker.player.sendMessage("Your weapon isn't in this block of ice.");
			return;
		}
		hit.damage = Math.min(10, Math.max(0, block.getHp() - 1));
		iceDamage += hit.damage;
		if (iceDamage >= 70)
			crack(block);
	}

	private void crack(NPC block) {
		block.transform(CRACKED_ICICLE);
		target = null;
		if (!broken) {
			broken = true;
			returnWeapon();
			player.sendMessage("You shatter the ice and your weapon is freed.");
		}
	}

	private boolean freezeTick() {
		for (NPC block : icicles) // never let the blocks actually die
			if (block.getHp() < 2)
				block.incrementHp(1);
		if (--iceWaveIn <= 0) {
			iceWaveIn = 8;
			int erupt = freezeTicks - 3;
			for (NPC block : icicles) {
				int[][] pattern = iceWave >= 1 && rand(1, 6) <= iceWave ? ICE_BIG : ICE_CROSS;
				for (int[] d : pattern) {
					Position p = new Position(block.getAbsX() + d[0], block.getAbsY() + d[1], 0);
					if (!iceErupts.containsKey(p)) {
						World.sendGraphics(ICE_SPAWN_GFX, 0, 0, p);
						iceErupts.put(p, erupt);
					}
				}
			}
			iceWave++;
		}
		for (Iterator<Map.Entry<Position, Integer>> it = iceErupts.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Position, Integer> e = it.next();
			if (freezeTicks <= e.getValue()) {
				World.sendGraphics(ICE_ATTACK_GFX, 0, 0, e.getKey());
				if (player.getAbsX() == e.getKey().getX() && player.getAbsY() == e.getKey().getY())
					damagePlayer(0, 12);
				it.remove();
			} else if (freezeTicks < e.getValue() + 3) {
				World.sendGraphics(ICE_IDLE_GFX, 0, 0, e.getKey());
			}
		}
		if (--freezeTicks <= 0) {
			endFreeze();
			return false;
		}
		return true;
	}

	private void endFreeze() {
		if (!broken) {
			damagePlayer(0, 28); // wiki: failing to break the block deals up to 28
		}
		returnWeapon();
		freeze = false;
		for (NPC block : new ArrayList<>(icicles))
			removeAdd(block);
		icicles.clear();
		iceErupts.clear();
		target = null;
		hideMoon(false);
		npc.animate(RETURN_ANIM);
	}

	private void returnWeapon() {
		if (player == null)
			return;
		player.remove(FROZEN_KEY);
		V_WEARPREVENT.set(player, 0);
		if (frozenWeapon == null)
			return;
		Item w = frozenWeapon;
		frozenWeapon = null;
		if (player.getEquipment().get(Equipment.SLOT_WEAPON) == null) {
			player.getEquipment().set(Equipment.SLOT_WEAPON, w);
			player.getEquipment().sendUpdates();
		} else {
			player.getInventory().addOrDrop(w); // the item itself: keeps its charges / attributes
		}
	}

	@Override
	protected void cleanupSpecial() {
		if (storm)
			endStorm();
		returnWeapon();
		if (freeze) {
			freeze = false;
			hideMoon(false);
		}
		icicles.clear();
		iceErupts.clear();
		cold = 0;
		if (player != null)
			player.remove(COLD_KEY);
	}

	public static void register() {
		ObjectAction.register(BRAZIER_LEFT, 1, (p, obj) -> relight(p, true));
		ObjectAction.register(BRAZIER_RIGHT, 1, (p, obj) -> relight(p, false));
	}
}
