package io.ruin.model.inter.handlers;

import io.ruin.model.activities.bosses.Malakar;
import io.ruin.model.activities.bosses.SolHeredit;
import io.ruin.model.activities.bosses.araxxor.Araxxor;
import io.ruin.model.activities.bosses.nightmare.NightmareCombat;
import io.ruin.model.activities.moonsofperil.MoonsOfPerilRewards;
import io.ruin.model.activities.tempevents.summerevent.SummerBoss;
import io.ruin.model.activities.tempevents.summerevent.SummerEventHandler;
import io.ruin.model.item.actions.impl.boxes.mystery.CrystalChestBox;
import io.ruin.model.item.actions.impl.boxes.mystery.LimitedMysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.MysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.MysteryBoxCrate;
import io.ruin.model.item.actions.impl.boxes.mystery.PVPArmourMysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.Promopackage;
import io.ruin.model.item.actions.impl.boxes.mystery.SlayerCasket;
import io.ruin.model.item.actions.impl.boxes.mystery.SlayerChest;
import io.ruin.model.item.actions.impl.boxes.mystery.SuperMysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.ThirdAgeMysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.UltraMysteryBox;
import io.ruin.model.item.actions.impl.boxes.mystery.VoteMysteryBox;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.map.object.actions.impl.BrimstoneChest;
import io.ruin.model.map.object.actions.impl.LarransChest;

public enum LootsTables {
	REGULAR_MYSTERY_BOX("Regular Mystery Box", MysteryBox.MYSTERY_BOX_TABLE),
	ARAXXOR("Araxxor", Araxxor.table),
	ADVANCED_MYSTERY_BOX("Advanced Mystery Box", MysteryBox.ADVANCED_MYSTERY_BOX_COMMON_TABLE),
	BRIMSTONE_CHEST("Brimstone Chest", BrimstoneChest.BRIMSTONE_TABLE),
	DRACO_CHEST("Draco Chest", io.ruin.model.map.object.actions.impl.DracoChest.DRACO_CHEST_TABLE),
	LARRANS_CHEST("Larran's Chest", LarransChest.COMMON),
	SLAYER_MYSTERY_BOX("Slayer Mystery Box", MysteryBox.SLAYER_MYSTERY_BOX_TABLE),
	MALAKAR("Malakar Group Boss", Malakar.table),
	SOL_HEREDIT("Sol Heredit", SolHeredit.table),
	SUMMER_FROG("Summer Frog", SummerEventHandler.summerFrogLootTable),
	SUMMER_IMP("Summer Imp", SummerEventHandler.summerImpLootTable),
	SUMMER_BOSS("Summer Boss", SummerBoss.table),
	MOONS_OF_PERIL("Moons of Peril", MoonsOfPerilRewards.table),
	NIGHTMARE("Nightmare", NightmareCombat.regularTable),
	HIGH_MYSTERY_BOX("High Tier Mystery Box", MysteryBox.HIGH_TIER_MYSTERY_BOX_TABLE),
	LOW_MYSTERY_BOX("Low Tier Mystery Box", MysteryBox.LOW_TIER_MYSTERY_BOX_TABLE),
	COX_MYSTERY_BOX("CoX Mystery Box", MysteryBox.COX_MYSTERY_BOX_TABLE),
	NIGHTMARE_MYSTERY_BOX("Nightmare Mystery Box", MysteryBox.NIGHTMARE_MYSTERY_BOX_TABLE),
	ARGENTAVIS_MYSTERY_BOX("Argentavis Mystery Box", MysteryBox.ARGENTAVIS_MYSTERY_BOX_TABLE),
	NEX_MYSTERY_BOX("Nex Mystery Box", MysteryBox.NEX_MYSTERY_BOX_TABLE),
	FORGOTTEN_LOCKBOX("Forgotten lockbox", MysteryBox.FORGOTTEN_LOCKBOX),
	DONATOR_WEAPON_BOX("Donator Weapon Box", MysteryBox.DONATOR_WEAPON_BOX),
	HOLIDAY_MYSTERY_BOX("Spring Mystery Box", MysteryBox.HOLIDAY_MYSTERY_BOX),
	SUMMER_MYSTERY_BOX("Summer Mystery Box", MysteryBox.SUMMER_MYSTERY_BOX),
	DONATOR_ARMOUR_BOX("Donator Armour Box", MysteryBox.DONATOR_ARMOUR_BOX),
	VOTE_BOX("Vote Buff Streak Box", MysteryBox.VOTE_BOX),
	WILDERNESS_MYSTERY_BOX("Wilderness Mystery Box", MysteryBox.WILDERNESS_MYSTERY_BOX_TABLE),
	TOB_MYSTERY_BOX("ToB Mystery Box", MysteryBox.TOB_MYSTERY_BOX_TABLE),
	TOB_REFUND_BOX("ToB Refund Box", MysteryBox.TOB_REFUND_TABLE),
	GAUNTLET_MYSTERY_BOX("Gauntlet Mystery Box", MysteryBox.GAUNTLET_MYSTERY_BOX_TABLE),
	GWD_MYSTERY_BOX("GWD Mystery Box", MysteryBox.GWD_MYSTERY_BOX_TABLE),
	GALVEK_MYSTERY_BOX("Galvek Mystery Box", MysteryBox.GALVEK_MYSTERY_BOX_TABLE),
	ENHANCED_SUPERIOR_SLAYER_BOX("Enhanced Superior Box", MysteryBox.ENHANCED_SUPERIOR_SLAYER_BOX),
	REASON_ULTRA_POINT_BOX("Ultra Mystery Box", MysteryBox.ULTRA_MYSTERY_BOX),

	CRYSTAL_CHEST("Crystal Chest", CrystalChestBox.CRYSTAL_TABLE),
	PVP_VOTING_BOX("PvP Voting Box", VoteMysteryBox.PVP_VOTING_BOX_TABLE),
	ECO_VOTING_BOX("Economy Voting Box", VoteMysteryBox.ECO_VOTING_BOX_TABLE),
	LIMITED_MYSTERY_BOX("Limited Mystery Box", LimitedMysteryBox.ECO_MYSTERY_BOX_TABLE),
	SLAYER_CASKET("Slayer Casket", SlayerCasket.SLAYER_CASKET_LOOT),
	MYSTERY_BOX_CRATE("Mystery Box Crate", MysteryBoxCrate.MYSTERY_BOX_TABLE),
	PROMO_PACKAGE("Promo Package", Promopackage.MYSTERY_BOX_TABLE),
	PROMO_SUPER_MYSTERY_BOX("Promo Super Mystery Box", Promopackage.SUPERMBOX_TABLE),
	SUPER_MYSTERY_BOX("Super Mystery Box", SuperMysteryBox.ECO_MYSTERY_BOX_TABLE),
	ULTRA_MYSTERY_BOX_ECO("Ultra Mystery Box", UltraMysteryBox.ECO_MYSTERY_BOX_TABLE),
	THIRD_AGE_MYSTERY_BOX("Third Age Mystery Box", ThirdAgeMysteryBox.THIRD_AGE_BOX_TABLE),
	PVP_ARMOUR_MYSTERY_BOX("PvP Armour Mystery Box", PVPArmourMysteryBox.PVP_ARMOUR_BOX_TABLE),
	SLAYER_CHEST_BOSS_LOOT("Slayer Chest (Boss Loot)", SlayerChest.BOSS_LOOT),
	SLAYER_CHEST_VERY_RARE("Slayer Chest (Very Rare)", SlayerChest.VERY_RARE_LOOT),
	BOXING_MYSTERY_CHEST("Tournament Chest", io.ruin.model.activities.tournament.BoxingMysteryChest.TABLE),
	RIVALS_MYSTERY_BOX("Rivals Mystery Box", io.ruin.model.content.rivals.RivalsItems.BOX),

	;

	String tableName;
	LootTable table;

	LootsTables(String name, LootTable table) {
		this.tableName = name;
		this.table = table;
	}

	public static final LootsTables[] VALUES = values();

	/** Box item id -> the table its "open" action actually rolls (examining the box opens it in the loot viewer). */
	private static final java.util.Map<Integer, LootsTables> BOX_LOOT = new java.util.HashMap<>();

	private static void box(LootsTables table, int... ids) {
		for (int id : ids)
			BOX_LOOT.put(id, table);
	}

	static {
		box(REGULAR_MYSTERY_BOX, 6199, 31618, 4810, 30446);
		box(ADVANCED_MYSTERY_BOX, 30461, 30444, 30462, 30447, 30448, 30449);
		box(TOB_MYSTERY_BOX, 30529);
		box(COX_MYSTERY_BOX, 30530);
		box(WILDERNESS_MYSTERY_BOX, 30578);
		box(ENHANCED_SUPERIOR_SLAYER_BOX, 30542);
		box(GAUNTLET_MYSTERY_BOX, 30528);
		box(REASON_ULTRA_POINT_BOX, 30430);
		box(GWD_MYSTERY_BOX, 30582);
		box(NIGHTMARE_MYSTERY_BOX, 30526);
		box(GALVEK_MYSTERY_BOX, 30527);
		box(ARGENTAVIS_MYSTERY_BOX, 30525);
		box(NEX_MYSTERY_BOX, 30524);
		box(FORGOTTEN_LOCKBOX, 30763);
		box(DONATOR_WEAPON_BOX, 59524, 30628);
		box(DONATOR_ARMOUR_BOX, 59525, 30627);
		box(HOLIDAY_MYSTERY_BOX, 32000);
		box(SUMMER_MYSTERY_BOX, io.ruin.cache.ItemID.SUMMER_MYSTERY_BOX);
		box(VOTE_BOX, 30596);
		box(SLAYER_MYSTERY_BOX, 30452);
		box(LOW_MYSTERY_BOX, 30450);
		box(HIGH_MYSTERY_BOX, 30451);
		box(LIMITED_MYSTERY_BOX, 30426);
		box(MYSTERY_BOX_CRATE, 30456);
		box(PVP_ARMOUR_MYSTERY_BOX, 22330);
		box(SLAYER_CASKET, 23071);
		box(SUPER_MYSTERY_BOX, 290);
		box(THIRD_AGE_MYSTERY_BOX, 6831);
		box(ECO_VOTING_BOX, 6829);
		box(BOXING_MYSTERY_CHEST, io.ruin.model.activities.tournament.BoxingMysteryChest.ID);
		box(RIVALS_MYSTERY_BOX, io.ruin.model.content.rivals.RivalsShop.MYSTERY_BOX);
	}

	/** @return the loot table a box item opens into, or null if the item isn't a box with a viewable table. */
	public static LootsTables forBox(int itemId) {
		return BOX_LOOT.get(itemId);
	}

}
