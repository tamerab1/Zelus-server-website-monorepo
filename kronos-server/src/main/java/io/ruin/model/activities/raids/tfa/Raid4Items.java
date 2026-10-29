package io.ruin.model.activities.raids.tfa;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Color;
import io.ruin.cache.ItemID;
import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.skills.magic.spells.ancient.AncientTeleport;
import io.ruin.model.stat.StatType;

/**
 * The Fractured Archive's rewards, used as the OSRS wiki describes them (2026-10-28):
 * <ul>
 * <li>Obligator: charged with 1 ascension shard + 20 nature + 20 death runes for 10 charges (20,000 max), one an attack;
 * charged, every hit takes the best of two damage rolls (one more each for the target's stab and slash defence above its
 * crush) and +20% / +40% against 2x2 / 3x3+ targets. Uncharging returns the shards and runes.</li>
 * <li>Zorya's tome: a powered staff (its spell in PlayerCombat), 3 astral + 2 nature runes a charge, 20,000 max.</li>
 * <li>Archivist's folio: 5 teleports per ascension shard, Read to the Archive lobby.</li>
 * <li>Ascension shards: chisel into 10 ascension bolts (86 Fletching, 140 xp); diamond / onyx bolt tips on them.</li>
 * <li>The four elemental fragments: Break for a permanent +2 to that element's spell max hits.</li>
 * </ul>
 */
public final class Raid4Items {

	private Raid4Items() {
	}

	public static final int OBLIGATOR = 34715, OBLIGATOR_UNCHARGED = 34717, TOME = 34725, TOME_UNCHARGED = 34727,
			FOLIO = 34723, FOLIO_INERT = 34722, SHARDS = 34669, BOLTS = 34684, DIAMOND_BOLTS = 34690, ONYX_BOLTS = 34702,
			HORN_OF_BRAN = 34679, ARCHIVE_KEY = 34658, ALLIANCE_MESSAGE = 34657;
	/** Earth, water, wind, fire: bit 0..3 of {@code Player.tfaFragments}. */
	public static final int[] FRAGMENTS = { 34671, 34673, 34675, 34677 };
	static final String[] ELEMENTS = { "earth", "water", "wind", "fire" };
	public static final int MAX_CHARGES = 20_000;
	public static final int SEQ_TOME_ATTACK = 15139, SPOT_TOME_CAST = 4433, SPOT_TOME_PROJ = 4427, SPOT_TOME_IMPACT = 4429,
			SPOT_OBLIGATOR_THRUST = 4423, SEQ_ADD_TIPS = 15158;

	public static void register() {
		// Obligator
		for (int id : new int[] { OBLIGATOR, OBLIGATOR_UNCHARGED }) {
			ItemAction.registerInventory(id, "charge", (p, item) -> chargeObligator(p, item));
			for (int with : new int[] { SHARDS, ItemID.NATURE_RUNE, ItemID.DEATH_RUNE })
				ItemItemAction.register(id, with, (p, item, other) -> chargeObligator(p, item));
		}
		ItemAction.registerInventory(OBLIGATOR, "check", Raid4Items::check);
		ItemAction.registerEquipment(OBLIGATOR, "check", Raid4Items::check);
		ItemAction.registerInventory(OBLIGATOR, "uncharge", Raid4Items::unchargeObligator);
		ObjType.get(OBLIGATOR).addPreTargetDefendListener(Raid4Items::obligatorHit);

		// Zorya's tome
		for (int id : new int[] { TOME, TOME_UNCHARGED }) {
			ItemAction.registerInventory(id, "charge", (p, item) -> chargeTome(p, item));
			for (int with : new int[] { ItemID.ASTRAL_RUNE, ItemID.NATURE_RUNE })
				ItemItemAction.register(id, with, (p, item, other) -> chargeTome(p, item));
		}
		ItemAction.registerInventory(TOME, "check", Raid4Items::check);
		ItemAction.registerEquipment(TOME, "check", Raid4Items::check);
		ItemAction.registerInventory(TOME, "uncharge", Raid4Items::unchargeTome);
		ObjType.get(TOME).addPreTargetDefendListener((p, item, hit, target) -> spendCharge(p, item, TOME_UNCHARGED));

		// Archivist's folio
		for (int id : new int[] { FOLIO, FOLIO_INERT }) {
			ItemAction.registerInventory(id, "charge", (p, item) -> chargeFolio(p, item));
			ItemItemAction.register(id, SHARDS, (p, item, other) -> chargeFolio(p, item));
			ItemAction.registerInventory(id, "read", Raid4Items::readFolio);
		}
		ItemAction.registerInventory(FOLIO, "check", (p, item) -> p.sendMessage("Your archivist's folio has "
				+ NumberUtils.formatNumber(AttributeExtensions.getCharges(item)) + " teleport charges."));

		// shards, bolts and tips
		ItemItemAction.register(ItemID.CHISEL, SHARDS, (p, chisel, shards) -> makeBolts(p));
		ItemItemAction.register(BOLTS, ItemID.DIAMOND_BOLT_TIPS, (p, a, b) -> tipBolts(p, ItemID.DIAMOND_BOLT_TIPS, DIAMOND_BOLTS, 65, 7));
		ItemItemAction.register(BOLTS, ItemID.ONYX_BOLT_TIPS, (p, a, b) -> tipBolts(p, ItemID.ONYX_BOLT_TIPS, ONYX_BOLTS, 73, 9.4));

		// the elemental fragments
		for (int i = 0; i < 4; i++) {
			int idx = i;
			ItemAction.registerInventory(FRAGMENTS[i], "break", (p, item) -> breakFragment(p, item, idx));
		}

		ItemAction.registerInventory(HORN_OF_BRAN, "inspect", (p, item) -> p.sendMessage(
				"The horn is inscribed with the name Bran. King Arthur of Camelot would surely want to hear how you came by it."));
		ItemAction.registerInventory(ARCHIVE_KEY, "inspect", (p, item) -> p.sendMessage(
				"A key of stone and vines. It must open something within the Fractured Archive."));
		ItemAction.registerInventory(ALLIANCE_MESSAGE, "read", (p, item) -> p.sendMessage(
				"'Help us defend the Archive. Guthix's knowledge must not fall to Ocellus.' ~ The Alliance"));
	}

	/* ------------------------------------------------------------------ charges */

	private static void check(Player player, Item item) {
		player.sendMessage("Your " + item.getDef().name + " has " + NumberUtils.formatNumber(AttributeExtensions.getCharges(item))
				+ " charges remaining.");
	}

	/** One charge an attack; at none it reverts to its uncharged form. */
	private static void spendCharge(Player player, Item item, int uncharged) {
		if (AttributeExtensions.getCharges(item) > 0) {
			AttributeExtensions.deincrementCharges(item, 1);
			int left = AttributeExtensions.getCharges(item);
			if (left > 0 && left % 1000 == 0)
				check(player, item);
			if (left > 0)
				return;
		}
		player.sendMessage(Color.RED.wrap("Your " + item.getDef().name + " has run out of charges!"));
		item.setId(uncharged);
		player.getCombat().updateWeapon(false);
	}

	private static void chargeObligator(Player player, Item item) {
		int have = AttributeExtensions.getCharges(item);
		int sets = Math.min(player.getInventory().getAmount(SHARDS), Math.min(player.getInventory().getAmount(ItemID.NATURE_RUNE),
				player.getInventory().getAmount(ItemID.DEATH_RUNE)) / 20);
		sets = Math.min(sets, (MAX_CHARGES - have) / 10);
		if (sets <= 0) {
			player.sendMessage(have >= MAX_CHARGES ? "Your Obligator can't hold any more charges."
					: "Each 10 charges needs 1 ascension shard, 20 nature runes and 20 death runes.");
			return;
		}
		player.getInventory().remove(SHARDS, sets);
		player.getInventory().remove(ItemID.NATURE_RUNE, sets * 20);
		player.getInventory().remove(ItemID.DEATH_RUNE, sets * 20);
		AttributeExtensions.addCharges(item, sets * 10);
		item.setId(OBLIGATOR);
		check(player, item);
	}

	private static void unchargeObligator(Player player, Item item) {
		int charges = AttributeExtensions.getCharges(item);
		int sets = (charges + 9) / 10;
		player.dialogue(new OptionsDialogue(Color.DARK_RED.wrap("Uncharge your Obligator?"),
				new Option("Yes, take back " + sets + " shards, " + sets * 20 + " nature and death runes.", () -> {
					player.getInventory().addOrDrop(SHARDS, sets);
					player.getInventory().addOrDrop(ItemID.NATURE_RUNE, sets * 20);
					player.getInventory().addOrDrop(ItemID.DEATH_RUNE, sets * 20);
					AttributeExtensions.setCharges(item, 0);
					item.setId(OBLIGATOR_UNCHARGED);
					player.sendMessage("You uncharge your Obligator.");
				}), new Option("No.", Player::closeDialogue)));
	}

	/**
	 * The charged Obligator's passive on a melee hit: the best of 2..4 damage rolls, and +20% / +40% damage on a
	 * 2x2 / 3x3-or-bigger target. Then a charge.
	 */
	private static void obligatorHit(Player player, Item item, Hit hit, Entity target) {
		if (hit.attackStyle == null || !hit.attackStyle.isMelee())
			return;
		int rolls = 2;
		if (target != null && target.isNpc()) {
			var c = target.npc.getCombat();
			double crush = c.getBonus(EquipmentStats.CRUSH_DEFENCE);
			if (c.getBonus(EquipmentStats.STAB_DEFENCE) > crush)
				rolls++;
			if (c.getBonus(EquipmentStats.SLASH_DEFENCE) > crush)
				rolls++;
		}
		hit.damageRolls = Math.max(hit.damageRolls, rolls);
		int size = target == null ? 1 : target.getSize();
		if (size >= 3)
			hit.boostDamage(0.4);
		else if (size == 2)
			hit.boostDamage(0.2);
		player.graphics(SPOT_OBLIGATOR_THRUST, 0, 0);
		spendCharge(player, item, OBLIGATOR_UNCHARGED);
	}

	private static void chargeTome(Player player, Item item) {
		int have = AttributeExtensions.getCharges(item);
		int can = Math.min(player.getInventory().getAmount(ItemID.ASTRAL_RUNE) / 3, player.getInventory().getAmount(ItemID.NATURE_RUNE) / 2);
		can = Math.min(can, MAX_CHARGES - have);
		if (can <= 0) {
			player.sendMessage(have >= MAX_CHARGES ? "Your tome can't hold any more charges."
					: "Each charge needs 3 astral runes and 2 nature runes.");
			return;
		}
		int max = can;
		player.integerInput("How many charges? (up to " + NumberUtils.formatNumber(max) + ")", n -> {
			if (n < 1)
				return;
			int add = Math.min(n, max);
			player.getInventory().remove(ItemID.ASTRAL_RUNE, add * 3);
			player.getInventory().remove(ItemID.NATURE_RUNE, add * 2);
			AttributeExtensions.addCharges(item, add);
			item.setId(TOME);
			check(player, item);
		});
	}

	private static void unchargeTome(Player player, Item item) {
		int charges = AttributeExtensions.getCharges(item);
		player.dialogue(new OptionsDialogue(Color.DARK_RED.wrap("Uncharge Zorya's tome?"),
				new Option("Yes, take back " + charges * 3 + " astral and " + charges * 2 + " nature runes.", () -> {
					player.getInventory().addOrDrop(ItemID.ASTRAL_RUNE, charges * 3);
					player.getInventory().addOrDrop(ItemID.NATURE_RUNE, charges * 2);
					AttributeExtensions.setCharges(item, 0);
					item.setId(TOME_UNCHARGED);
					player.sendMessage("You uncharge Zorya's tome.");
				}), new Option("No.", Player::closeDialogue)));
	}

	/** Zorya's tome's spell: base max hit 18, raised by magic damage %. */
	public static int tomeMaxHit(Player player) {
		return (int) (18 * (1 + player.getEquipment().bonuses[EquipmentStats.MAGIC_DAMAGE] / 100.0));
	}

	/* ------------------------------------------------------------------ the folio */

	private static void chargeFolio(Player player, Item item) {
		int shards = player.getInventory().getAmount(SHARDS);
		if (shards <= 0) {
			player.sendMessage("The folio is charged with ascension shards, 5 teleports a shard.");
			return;
		}
		player.getInventory().remove(SHARDS, shards);
		AttributeExtensions.addCharges(item, shards * 5);
		item.setId(FOLIO);
		player.sendMessage("Your archivist's folio now holds " + NumberUtils.formatNumber(AttributeExtensions.getCharges(item))
				+ " teleport charges.");
	}

	private static void readFolio(Player player, Item item) {
		if (AttributeExtensions.getCharges(item) <= 0) {
			player.sendMessage("The folio's pages are inert. Charge it with ascension shards.");
			return;
		}
		if (AncientTeleport.teleport(player, Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0)) {
			AttributeExtensions.deincrementCharges(item, 1);
			if (AttributeExtensions.getCharges(item) <= 0)
				item.setId(FOLIO_INERT);
		}
	}

	/* ------------------------------------------------------------------ fletching */

	private static void makeBolts(Player player) {
		if (!player.getStats().check(StatType.Fletching, 86, "chisel ascension shards into bolts"))
			return;
		int shards = player.getInventory().getAmount(SHARDS);
		if (shards <= 0)
			return;
		player.getInventory().remove(SHARDS, shards);
		player.getInventory().add(BOLTS, shards * 10);
		player.getStats().addXp(StatType.Fletching, 140.0 * shards, true);
		player.sendMessage("You chisel " + shards + " ascension shards into " + shards * 10 + " ascension bolts.");
	}

	private static void tipBolts(Player player, int tips, int result, int level, double xpEach) {
		if (!player.getStats().check(StatType.Fletching, level, "tip ascension bolts"))
			return;
		int n = Math.min(10, Math.min(player.getInventory().getAmount(BOLTS), player.getInventory().getAmount(tips)));
		if (n <= 0)
			return;
		player.animate(SEQ_ADD_TIPS);
		player.getInventory().remove(BOLTS, n);
		player.getInventory().remove(tips, n);
		player.getInventory().add(result, n);
		player.getStats().addXp(StatType.Fletching, xpEach * n, true);
	}

	/* ------------------------------------------------------------------ fragments */

	private static void breakFragment(Player player, Item item, int idx) {
		if ((player.tfaFragments & (1 << idx)) != 0) {
			player.sendMessage("You have already absorbed the power of living " + ELEMENTS[idx] + ".");
			return;
		}
		item.remove();
		player.tfaFragments |= 1 << idx;
		player.sendMessage(Color.DARK_GREEN.wrap("You break the fragment. Your " + ELEMENTS[idx]
				+ " spells now hit up to 2 harder."));
	}

	/** +2 to an elemental spell's max hit if the caster broke that element's fragment. */
	public static int fragmentBonus(Player player, int element) {
		return player != null && (player.tfaFragments & (1 << element)) != 0 ? 2 : 0;
	}

	/** For the combat log's style check. */
	static boolean melee(AttackStyle style) {
		return style != null && style.isMelee();
	}
}
