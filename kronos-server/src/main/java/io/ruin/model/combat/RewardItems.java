package io.ruin.model.combat;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.actions.ItemNPCAction;
import io.ruin.model.item.actions.ItemObjectAction;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.attributes.AttributeTypes;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.magic.spells.TargetSpell;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;
import io.ruin.model.stat.StatType;

import java.util.*;

/**
 * The PvM overhaul rewards (2026-11-13), per the OSRS wiki:
 * <ul>
 * <li>Dual macuahuitl (28997): two splats a tick apart, the max hit split floor/ceil; the second only rolls if the
 * first hit. Blood Moon set (29028/29022/29025): Bloodrager, a successful hit has 33% (55% if both splats hit) to
 * make the next attack a tick early.</li>
 * <li>Blue Moon spear (28988) + Blue Moon set (29019/29013/29016): Frostweaver, a successful Bind/Snare/Entangle or
 * Ancient ice spell has 20% to strike with an instant melee hit.</li>
 * <li>Eclipse atlatl (29000): fires atlatl darts but hits with the melee Strength max hit (PlayerCombat). Eclipse
 * Moon set (29010/29004/29007): 20% per successful hit to Burn the target, 10 damage over 40 ticks (1 per 4 ticks),
 * up to 5 burns at once.</li>
 * <li>Dragon hunter wand (30070): +75% accuracy, +40% damage with magic vs draconic npcs.</li>
 * <li>Tome of earth (30064, empty 30066): +10% damage on standard earth spells vs npcs (1 charge a cast), an
 * infinite earth rune source; soiled pages (30068) add 20 charges each, max 20,000.</li>
 * <li>Twinflame staff (30634): +10% accuracy and damage on standard spells; bolt/blast/wave spells fire a second
 * spell a tick later for 40% of the first hit (no XP); infinite fire and water runes. Made from a battlestaff + both
 * element staff crowns (30631, 30628).</li>
 * <li>Giantsoul amulet (30638, uncharged 30637): teleports to Bryophyta, Obor and the Royal Titans; big bones + law
 * rune per charge, max 16,000.</li>
 * <li>Cowbell amulet (33104, empty 33103): 1 air rune per charge (max 1,000); Teleport to the cow field; Ring makes a
 * dead Brutus return 13 ticks later.</li>
 * <li>Cow slippers (33093/33096/33097/33098): Swap cycles the styles.</li>
 * <li>Desiccated pages (30640) at the Book of Eternal Flame / Earthen Might / Storms (55990/55991/55992): burnt /
 * soiled / soaked pages, 50 Runecraft required, 50 XP each.</li>
 * <li>Hueycoatl hide (30085) + needle + thread: coif 76 (2 hides, 190 XP), vambraces 76 (1, 95), chaps 77 (2, 190),
 * body 78 (3, 285).</li>
 * <li>Bottomless milk bucket (33091 empty / 33089): fill it at a dairy cow, up to 10,000 milk.</li>
 * </ul>
 */
public final class RewardItems {

	private RewardItems() {
	}

	public static final int DUAL_MACUAHUITL = 28997, BLUE_MOON_SPEAR = 28988, ECLIPSE_ATLATL = 29000, ATLATL_DART = 28991;
	public static final int[] BLOOD_SET = { 29028, 29022, 29025 }, BLUE_SET = { 29019, 29013, 29016 }, ECLIPSE_SET = { 29010, 29004, 29007 };
	public static final int DH_WAND = 30070, TWINFLAME = 30634, TOME_OF_EARTH = 30064, TOME_OF_EARTH_EMPTY = 30066, SOILED_PAGE = 30068;
	private static final int FIRE_CROWN = 30631, ICE_CROWN = 42771, BATTLESTAFF = 1391; // ice crown: cache stage 43
	private static final int GIANTSOUL = 30638, GIANTSOUL_UNCHARGED = 30637, BIG_BONES = 532, LAW_RUNE = 563;
	private static final int COWBELL = 33104, COWBELL_EMPTY = 33103, AIR_RUNE = 556;
	private static final int[] SLIPPERS = { 33093, 33096, 33097, 33098 };
	private static final int DESICCATED_PAGE = 30640, BURNT_PAGE = 20718, SOAKED_PAGE = 25578;
	private static final int HUEY_HIDE = 30085, NEEDLE = 1733, THREAD = 1734;
	private static final int BUCKET_EMPTY = 33091, BUCKET_FULL = 33089;

	/* ------------------------------------------------------------------ set checks */

	private static boolean wearing(Player p, int weapon, int[] set) {
		Equipment e = p.getEquipment();
		if (e.getId(Equipment.SLOT_WEAPON) != weapon)
			return false;
		for (int id : set)
			if (!MoonArmour.wears(p, id))
				return false;
		return true;
	}

	public static boolean bloodMoonSet(Player p) {
		return wearing(p, DUAL_MACUAHUITL, BLOOD_SET);
	}

	public static boolean blueMoonSet(Player p) {
		return wearing(p, BLUE_MOON_SPEAR, BLUE_SET);
	}

	public static boolean eclipseMoonSet(Player p) {
		return wearing(p, ECLIPSE_ATLATL, ECLIPSE_SET);
	}

	/* ------------------------------------------------------------------ combat hooks */

	/** Bloodrager: true when the next attack should come a tick early. */
	public static boolean bloodrager(Player p, boolean firstHit, boolean secondHit) {
		if (!bloodMoonSet(p) || !firstHit)
			return false;
		int roll = Random.get(0, 99);
		if (secondHit)
			roll = Math.min(roll, Random.get(0, 99));
		return roll <= 32;
	}

	/** Hit modifiers, from PlayerCombat.preTargetDefend. */
	public static void preTargetDefend(Player p, Hit hit, Entity target) {
		if (hit.attackStyle == null || target == null)
			return;
		int weapon = p.getEquipment().getId(Equipment.SLOT_WEAPON);
		if (weapon == DH_WAND && hit.attackStyle.isMagic() && target.isNpc() && target.npc.getDef().dragon)
			hit.boostAttack(0.75).boostDamage(0.40);
		if (weapon == TWINFLAME && hit.attackSpell != null && standard(hit.attackSpell) && !hit.soulEcho)
			hit.boostAttack(0.10).boostDamage(0.10);
	}

	private static boolean standard(TargetSpell spell) {
		return spell.getClass().getPackageName().endsWith(".modern");
	}

	/**
	 * Spell hooks, from TargetSpell.cast just before the hit lands: the tome of earth, the Twinflame's second spell and
	 * Frostweaver.
	 */
	public static void onSpellCast(Player p, TargetSpell spell, Entity target, Hit hit, boolean earthSpell, int delayTicks) {
		if (p == null || target == null)
			return;
		String name = spell.getClass().getSimpleName();
		// tome of earth: +10% on standard earth spells vs npcs, one charge a cast
		Item shield = p.getEquipment().get(Equipment.SLOT_SHIELD);
		if (earthSpell && target.isNpc() && shield != null && shield.getId() == TOME_OF_EARTH && standard(spell)) {
			int charges = AttributeExtensions.getCharges(shield);
			if (charges > 0) {
				hit.boostDamage(0.10);
				shield.putAttribute(AttributeTypes.CHARGES, charges - 1);
				if (charges - 1 <= 0) {
					shield.setId(TOME_OF_EARTH_EMPTY);
					p.sendMessage("Your tome of earth has run out of charges.");
				}
			}
		}
		int weapon = p.getEquipment().getId(Equipment.SLOT_WEAPON);
		// Twinflame: bolt/blast/wave fire a second spell a tick later for 40% of the first hit
		if (weapon == TWINFLAME && standard(spell) && (name.endsWith("Bolt") || name.endsWith("Blast") || name.endsWith("Wave"))) {
			hit.postDamage(t -> {
				if (hit.isBlocked() || hit.damage <= 0)
					return;
				int twin = (int) Math.floor(hit.damage * 0.4);
				if (twin > 0)
					target.hit(new Hit(p, AttackStyle.MAGIC, AttackType.ACCURATE).fixedDamage(twin).ignoreDefence().delay(1));
			});
		}
		// Frostweaver: a successful bind / ice spell, 20% for an instant melee strike
		boolean grasp = spell instanceof io.ruin.model.skills.magic.spells.arceuus.ArceuusCombatSpell
			&& ((io.ruin.model.skills.magic.spells.arceuus.ArceuusCombatSpell) spell).isGrasp();
		boolean binding = grasp || name.equals("Bind") || name.equals("Snare") || name.equals("Entangle") || name.startsWith("Ice");
		if (binding && blueMoonSet(p)) {
			int chance = grasp ? 50 : 20; // wiki: 50% after a Grasp, 20% after a Bind / Ice spell
			hit.postDamage(t -> {
				if (hit.isBlocked() || Random.get(0, 99) >= chance)
					return;
				int max = CombatUtils.getMaxMeleeDamage(p, AttackType.ACCURATE);
				p.graphics(2791, 0, 0); // special_spell_spear_spotanim
				target.hit(new Hit(p, AttackStyle.STAB, AttackType.ACCURATE).randDamage(max));
				p.sendFilteredMessage("<col=4f9fd6>Frostweaver strikes your frozen foe!</col>");
			});
		}
	}

	/* ------------------------------------------------------------------ Eclipse burn */

	private static final Map<Entity, List<int[]>> BURNS = new WeakHashMap<>(); // {damageLeft}

	/** Eclipse Moon set: 20% per successful atlatl hit to Burn (10 over 40 ticks, max 5 stacks). */
	public static void eclipseBurn(Player p, Entity target, Hit hit) {
		if (hit.isBlocked() || hit.damage <= 0 || !eclipseMoonSet(p) || Random.get(0, 99) >= 20)
			return;
		List<int[]> stacks = BURNS.computeIfAbsent(target, k -> new ArrayList<>());
		if (stacks.size() >= 5)
			return;
		int[] burn = { 10 };
		stacks.add(burn);
		World.startEvent(e -> {
			while (burn[0] > 0) {
				e.delay(4);
				if ((target.isNpc() && target.npc.isRemoved()) || (target.isPlayer() && !target.player.isOnline()) || target.getCombat() == null || target.getCombat().isDead()) {
					burn[0] = 0;
					break;
				}
				burn[0]--;
				target.hit(new Hit(p).fixedDamage(1).ignoreDefence().ignorePrayer());
			}
			stacks.remove(burn);
		});
	}

	/** Eclipse special: consumes every burn on the target and returns the damage left in them. */
	public static int consumeBurns(Entity target) {
		List<int[]> stacks = BURNS.remove(target);
		if (stacks == null)
			return 0;
		int left = 0;
		for (int[] b : stacks) {
			left += b[0];
			b[0] = 0;
		}
		return left;
	}

	/* ------------------------------------------------------------------ item options */

	public static void register() {
		registerAtlatlFletching();
		// tome of earth
		for (int tome : new int[] { TOME_OF_EARTH, TOME_OF_EARTH_EMPTY }) {
			ItemItemAction.register(tome, SOILED_PAGE, (p, primary, secondary) -> addPages(p, primary));
			ItemAction.registerInventory(tome, "check", (p, item) -> checkTome(p, item));
			ItemAction.registerEquipment(tome, "check", (p, item) -> checkTome(p, item));
		}
		ItemAction.registerInventory(TOME_OF_EARTH, "pages", (p, item) -> removePages(p, item));
		ItemAction.registerInventory(TOME_OF_EARTH_EMPTY, "pages", (p, item) -> addPages(p, item));

		// Twinflame staff: a battlestaff and both crowns
		for (int[] pair : new int[][] { { FIRE_CROWN, BATTLESTAFF }, { ICE_CROWN, BATTLESTAFF }, { FIRE_CROWN, ICE_CROWN } })
			ItemItemAction.register(pair[0], pair[1], (p, a, b) -> makeTwinflame(p));

		// Giantsoul amulet
		for (int id : new int[] { GIANTSOUL, GIANTSOUL_UNCHARGED }) {
			ItemItemAction.register(id, BIG_BONES, (p, a, b) -> chargeGiantsoul(p, a.getId() == BIG_BONES ? b : a));
			ItemItemAction.register(id, LAW_RUNE, (p, a, b) -> chargeGiantsoul(p, a.getId() == LAW_RUNE ? b : a));
		}
		ItemAction.registerInventory(GIANTSOUL, "rub", (p, item) -> rubGiantsoul(p, item));
		ItemAction.registerEquipment(GIANTSOUL, "Bryophyta", (p, item) -> giantsoulTeleport(p, item, 0));
		ItemAction.registerEquipment(GIANTSOUL, "Obor", (p, item) -> giantsoulTeleport(p, item, 1));
		ItemAction.registerEquipment(GIANTSOUL, "Branda and Eldric", (p, item) -> giantsoulTeleport(p, item, 2));
		ItemAction.registerEquipment(GIANTSOUL, "Check", (p, item) -> p.sendMessage("Your giantsoul amulet has "
				+ AttributeExtensions.getCharges(item) + " charges."));

		// Cowbell amulet
		for (int id : new int[] { COWBELL, COWBELL_EMPTY })
			ItemItemAction.register(id, AIR_RUNE, (p, a, b) -> chargeCowbell(p, a.getId() == AIR_RUNE ? b : a, a.getId() == AIR_RUNE ? a : b));
		for (int id : new int[] { COWBELL, COWBELL_EMPTY }) {
			ItemAction.registerInventory(id, "ring", (p, item) -> io.ruin.model.activities.bosses.brutus.Brutus.ring(p));
			ItemAction.registerEquipment(id, "Ring", (p, item) -> io.ruin.model.activities.bosses.brutus.Brutus.ring(p));
		}
		ItemAction.registerEquipment(COWBELL, "Teleport", (p, item) -> cowbellTeleport(p, item));
		ItemAction.registerEquipment(COWBELL, "Check", (p, item) -> p.sendMessage("Your cowbell amulet has "
				+ AttributeExtensions.getCharges(item) + " charges."));
		ItemAction.registerInventory(COWBELL, "check", (p, item) -> p.sendMessage("Your cowbell amulet has "
				+ AttributeExtensions.getCharges(item) + " charges."));
		ItemAction.registerInventory(COWBELL, "uncharge", (p, item) -> unchargeCowbell(p, item));

		ItemAction.registerInventory(COWBELL, "teleport", (p, item) -> cowbellTeleport(p, item));
		ItemAction.registerInventory(COWBELL_EMPTY, "charge", (p, item) -> p.sendMessage("Use air runes on the amulet to charge it (1 rune per charge, up to 1,000)."));
		ItemAction.registerInventory(GIANTSOUL, "check", (p, item) -> p.sendMessage("Your giantsoul amulet has "
				+ AttributeExtensions.getCharges(item) + " charges."));
		ItemAction.registerInventory(GIANTSOUL_UNCHARGED, "charge", (p, item) -> p.sendMessage("Use big bones and law runes on the amulet to charge it (one of each per charge)."));
		ItemAction.registerInventory(TWINFLAME, "inspect", (p, item) -> p.sendMessage("Bolt, blast and wave spells cast with this staff are followed by a second spell for 40% of the damage."));
		for (int id : new int[] { BUCKET_EMPTY, BUCKET_FULL }) {
			ItemAction.registerInventory(id, "fill", (p, item) -> p.sendMessage("Use the bucket on a dairy cow to fill it."));
			ItemAction.registerInventory(id, "check", (p, item) -> p.sendMessage("Your bottomless milk bucket holds "
					+ AttributeExtensions.getCharges(item) + " buckets of milk."));
		}

		// Cow slippers
		for (int id : SLIPPERS)
			ItemAction.registerInventory(id, "swap", (p, item) -> {
				int idx = 0;
				for (int i = 0; i < SLIPPERS.length; i++)
					if (SLIPPERS[i] == item.getId())
						idx = i;
				item.setId(SLIPPERS[(idx + 1) % SLIPPERS.length]);
				p.sendMessage("You swap the style of your cow slippers.");
			});
		for (int id : SLIPPERS)
			ItemAction.registerEquipment(id, "swap", (p, item) -> {
				int idx = 0;
				for (int i = 0; i < SLIPPERS.length; i++)
					if (SLIPPERS[i] == item.getId())
						idx = i;
				item.setId(SLIPPERS[(idx + 1) % SLIPPERS.length]);
				p.getEquipment().sendUpdates();
				p.getAppearance().update();
			});

		// desiccated page plinths
		int[][] plinths = { { 55990, BURNT_PAGE }, { 55991, SOILED_PAGE }, { 55992, SOAKED_PAGE } };
		for (int[] pl : plinths) {
			int product = pl[1];
			ObjectAction.register(pl[0], 1, (p, obj) -> convertPages(p, product));
			ItemObjectAction.register(DESICCATED_PAGE, pl[0], (p, item, obj) -> convertPages(p, product));
		}

		// Hueycoatl hide armour
		ItemItemAction.register(NEEDLE, HUEY_HIDE, (p, a, b) -> craftHide(p));

		// bottomless milk bucket
		for (int id : new int[] { BUCKET_EMPTY, BUCKET_FULL })
			ItemNPCAction.register(id, "Dairy cow", (p, item, npc) -> fillBucket(p, item, npc));
		ItemAction.registerInventory(BUCKET_FULL, "empty", (p, item) -> {
			item.putAttribute(AttributeTypes.CHARGES, 0);
			item.setId(BUCKET_EMPTY);
			p.sendMessage("You empty the bottomless milk bucket.");
		});
	}

	/* tome of earth */

	private static void addPages(Player p, Item tome) {
		int charges = AttributeExtensions.getCharges(tome), room = 20000 - charges;
		Item pages = p.getInventory().findItem(SOILED_PAGE);
		if (pages == null) {
			p.sendMessage("You have no soiled pages to add.");
			return;
		}
		if (room < 20) {
			p.sendMessage("Your tome of earth can't hold any more pages.");
			return;
		}
		int add = Math.min(room / 20, pages.getAmount());
		pages.incrementAmount(-add);
		tome.putAttribute(AttributeTypes.CHARGES, charges + add * 20);
		tome.setId(TOME_OF_EARTH);
		checkTome(p, tome);
	}

	private static void removePages(Player p, Item tome) {
		int charges = AttributeExtensions.getCharges(tome);
		int pages = charges / 20; // a partly used page is consumed
		if (pages <= 0) {
			p.sendMessage("There are no whole pages left in the tome.");
			return;
		}
		if (!p.getInventory().hasRoomFor(SOILED_PAGE)) {
			p.sendMessage("You need a free inventory space to remove the pages.");
			return;
		}
		tome.putAttribute(AttributeTypes.CHARGES, 0);
		tome.setId(TOME_OF_EARTH_EMPTY);
		p.getInventory().add(SOILED_PAGE, pages);
		p.sendMessage("You remove " + pages + " soiled pages from the tome.");
	}

	private static void checkTome(Player p, Item tome) {
		p.sendMessage("Your tome of earth has " + AttributeExtensions.getCharges(tome) + " charges.");
	}

	/* Twinflame */

	private static void makeTwinflame(Player p) {
		if (!p.getInventory().contains(FIRE_CROWN, 1) || !p.getInventory().contains(ICE_CROWN, 1) || !p.getInventory().contains(BATTLESTAFF, 1)) {
			p.sendMessage("You need a battlestaff and both element staff crowns to make a twinflame staff.");
			return;
		}
		p.startEvent(e -> {
			p.lock();
			p.animate(1248);
			e.delay(4);
			if (p.getInventory().contains(FIRE_CROWN, 1) && p.getInventory().contains(ICE_CROWN, 1) && p.getInventory().contains(BATTLESTAFF, 1)) {
				p.getInventory().remove(FIRE_CROWN, 1);
				p.getInventory().remove(ICE_CROWN, 1);
				p.getInventory().remove(BATTLESTAFF, 1);
				p.getInventory().add(TWINFLAME, 1);
				p.sendMessage("You fix both crowns to the battlestaff, creating a twinflame staff.");
			}
			p.unlock();
		});
	}

	/* Giantsoul amulet */

	private static void chargeGiantsoul(Player p, Item amulet) {
		int bones = p.getInventory().count(BIG_BONES), laws = p.getInventory().count(LAW_RUNE);
		int charges = AttributeExtensions.getCharges(amulet);
		int add = Math.min(Math.min(bones, laws), 16000 - charges);
		if (add <= 0) {
			p.sendMessage(charges >= 16000 ? "Your amulet can't hold any more charges." : "You need big bones and law runes to charge the amulet.");
			return;
		}
		p.getInventory().remove(BIG_BONES, add);
		p.getInventory().remove(LAW_RUNE, add);
		amulet.putAttribute(AttributeTypes.CHARGES, charges + add);
		amulet.setId(GIANTSOUL);
		p.sendMessage("You add " + add + " charges. Your giantsoul amulet now has " + (charges + add) + " charges.");
	}

	private static void rubGiantsoul(Player p, Item amulet) {
		p.dialogue(new OptionsDialogue("Where would you like to teleport to?",
				new Option("Bryophyta's lair", () -> giantsoulTeleport(p, amulet, 0)),
				new Option("Obor's lair", () -> giantsoulTeleport(p, amulet, 1)),
				new Option("Branda and Eldric's lair", () -> giantsoulTeleport(p, amulet, 2)),
				new Option("Nowhere")));
	}

	private static void giantsoulTeleport(Player p, Item amulet, int dest) {
		int charges = AttributeExtensions.getCharges(amulet);
		if (charges <= 0) {
			p.sendMessage("Your giantsoul amulet has no charges.");
			return;
		}
		int[][] spots = { { 3174, 9900, 0 }, { 3096, 9832, 0 }, { 2950, 9574, 0 } };
		if (ModernTeleport.teleport(p, spots[dest][0], spots[dest][1], spots[dest][2]))
			amulet.putAttribute(AttributeTypes.CHARGES, charges - 1);
	}

	/* Cowbell amulet */

	private static void chargeCowbell(Player p, Item amulet, Item runes) {
		int charges = AttributeExtensions.getCharges(amulet);
		int add = Math.min(runes.getAmount(), 1000 - charges);
		if (add <= 0) {
			p.sendMessage("Your cowbell amulet can't hold any more charges.");
			return;
		}
		runes.incrementAmount(-add);
		amulet.putAttribute(AttributeTypes.CHARGES, charges + add);
		amulet.setId(COWBELL);
		p.sendMessage("Your cowbell amulet now has " + (charges + add) + " charges.");
	}

	private static void unchargeCowbell(Player p, Item amulet) {
		int charges = AttributeExtensions.getCharges(amulet);
		if (charges <= 0)
			return;
		if (!p.getInventory().hasRoomFor(AIR_RUNE)) {
			p.sendMessage("You need a free inventory space.");
			return;
		}
		amulet.putAttribute(AttributeTypes.CHARGES, 0);
		amulet.setId(COWBELL_EMPTY);
		p.getInventory().add(AIR_RUNE, charges);
		p.sendMessage("You remove " + charges + " air runes from the cowbell amulet.");
	}

	private static void cowbellTeleport(Player p, Item amulet) {
		int charges = AttributeExtensions.getCharges(amulet);
		if (charges <= 0) {
			p.sendMessage("Your cowbell amulet has no charges.");
			return;
		}
		if (p.wildernessLevel > 20) {
			p.sendMessage("You can't teleport above level 20 Wilderness.");
			return;
		}
		if (ModernTeleport.teleport(p, 3262, 3297, 0))
			amulet.putAttribute(AttributeTypes.CHARGES, charges - 1);
	}

	/* desiccated pages */

	private static void convertPages(Player p, int product) {
		int pages = p.getInventory().count(DESICCATED_PAGE);
		if (pages <= 0) {
			p.sendMessage("You need desiccated pages to use here.");
			return;
		}
		if (p.getStats().get(StatType.Runecrafting).fixedLevel < 50) {
			p.dialogue(new MessageDialogue("You need a Runecraft level of 50 to restore these pages."));
			return;
		}
		p.animate(645);
		p.getInventory().remove(DESICCATED_PAGE, pages);
		p.getInventory().add(product, pages);
		p.getStats().addXp(StatType.Runecrafting, 50.0 * pages, true);
		p.sendMessage("You restore " + pages + " desiccated page" + (pages == 1 ? "" : "s") + ".");
	}

	/* Hueycoatl hide */

	private static void craftHide(Player p) {
		p.dialogue(new OptionsDialogue("What would you like to make?",
				new Option("Hueycoatl hide coif (2 hides, level 76)", () -> craft(p, 30073, 2, 76, 190)),
				new Option("Hueycoatl hide vambraces (1 hide, level 76)", () -> craft(p, 30082, 1, 76, 95)),
				new Option("Hueycoatl hide chaps (2 hides, level 77)", () -> craft(p, 30079, 2, 77, 190)),
				new Option("Hueycoatl hide body (3 hides, level 78)", () -> craft(p, 30076, 3, 78, 285))));
	}

	private static void craft(Player p, int product, int hides, int level, double xp) {
		if (p.getStats().get(StatType.Crafting).currentLevel < level) {
			p.dialogue(new MessageDialogue("You need a Crafting level of " + level + " to make that."));
			return;
		}
		if (p.getInventory().count(HUEY_HIDE) < hides || !p.getInventory().contains(THREAD, 1)) {
			p.sendMessage("You need " + hides + " Hueycoatl hide" + (hides == 1 ? "" : "s") + " and some thread.");
			return;
		}
		p.animate(1249);
		p.getInventory().remove(HUEY_HIDE, hides);
		p.getInventory().remove(THREAD, 1);
		p.getInventory().add(product, 1);
		p.getStats().addXp(StatType.Crafting, xp, true);
		p.sendMessage("You make " + io.ruin.cache.ObjType.get(product).descriptiveName + ".");
	}

	/* bottomless milk bucket */

	private static void fillBucket(Player p, Item bucket, NPC cow) {
		p.startEvent(e -> {
			p.sendMessage("You start filling the bottomless milk bucket.");
			while (true) {
				Item b = p.getInventory().findItem(BUCKET_FULL);
				if (b == null)
					b = p.getInventory().findItem(BUCKET_EMPTY);
				if (b == null)
					return;
				int charges = AttributeExtensions.getCharges(b);
				if (charges >= 10000) {
					p.sendMessage("Your bottomless milk bucket is full.");
					return;
				}
				p.face(cow);
				p.animate(2305);
				e.delay(p.getEquipment().hasId(COWBELL) ? 6 : 7);
				if (!p.getPosition().isWithinDistance(cow.getPosition(), 2))
					return;
				b.putAttribute(AttributeTypes.CHARGES, charges + 1);
				b.setId(BUCKET_FULL);
			}
		});
	}

	/** Infinite rune sources: the tome of earth (earth) and the Twinflame staff (fire, water). */
	public static boolean freeRune(Player p, io.ruin.model.skills.magic.rune.Rune rune) {
		if (rune == io.ruin.model.skills.magic.rune.Rune.EARTH && (p.getEquipment().hasId(TOME_OF_EARTH) || p.getEquipment().hasId(TOME_OF_EARTH_EMPTY)))
			return true;
		return p.getEquipment().getId(Equipment.SLOT_WEAPON) == TWINFLAME
				&& (rune == io.ruin.model.skills.magic.rune.Rune.FIRE || rune == io.ruin.model.skills.magic.rune.Rune.WATER);
	}

	/* ------------------------------------------------------------------ Atlatl dart fletching (OSRS wiki, 74 Fletching) */

	private static final int BROKEN_ANTLER = 31086, CHISEL = 1755, DART_TIPS = 30998, HEADLESS_DART = 31010, DART_SHAFT = 31004,
		FEATHER = 314;

	private static void registerAtlatlFletching() {
		// chisel + broken antler -> 100 tips (10 XP, 4 ticks)
		ItemItemAction.register(CHISEL, BROKEN_ANTLER, (p, a, b) -> fletchLoop(p, BROKEN_ANTLER, 1, 0, 0, DART_TIPS, 100, 10.0, 4, "You chisel the antler into dart tips."));
		// 20 shafts + 20 feathers -> 20 headless darts (20 XP, 2 ticks)
		ItemItemAction.register(DART_SHAFT, FEATHER, (p, a, b) -> fletchLoop(p, DART_SHAFT, 20, FEATHER, 20, HEADLESS_DART, 20, 20.0, 2, "You attach feathers to the dart shafts."));
		// 20 headless + 20 tips -> 20 atlatl darts (30 XP, 2 ticks)
		ItemItemAction.register(HEADLESS_DART, DART_TIPS, (p, a, b) -> fletchLoop(p, HEADLESS_DART, 20, DART_TIPS, 20, ATLATL_DART, 20, 30.0, 2, "You attach tips to the atlatl darts."));
	}

	private static void fletchLoop(Player p, int a, int aAmt, int b, int bAmt, int out, int outAmt, double xp, int ticks, String msg) {
		if (!p.getStats().check(StatType.Fletching, 74, out, "make that"))
			return;
		p.startEvent(e -> {
			while (true) {
				Item ia = p.getInventory().findItem(a);
				Item ib = b == 0 ? null : p.getInventory().findItem(b);
				if (ia == null || (b != 0 && ib == null))
					return;
				int sets = Math.min(1, Math.min(ia.getAmount() / aAmt, b == 0 ? 1 : ib.getAmount() / bAmt));
				int partA = aAmt, partB = bAmt, made = outAmt;
				if (sets == 0) { // a short last batch of shafts / headless darts
					if (aAmt == 1)
						return;
					int n = Math.min(ia.getAmount(), ib.getAmount());
					if (n <= 0)
						return;
					partA = partB = made = n;
				}
				if (!p.getInventory().hasRoomFor(out, made)) {
					p.sendMessage("You don't have enough inventory space.");
					return;
				}
				p.animate(8480);
				p.getInventory().remove(a, partA);
				if (b != 0)
					p.getInventory().remove(b, partB);
				p.getInventory().add(out, made);
				p.getStats().addXp(StatType.Fletching, xp * made / outAmt, true);
				p.sendFilteredMessage(msg);
				e.delay(ticks);
			}
		});
	}
}
