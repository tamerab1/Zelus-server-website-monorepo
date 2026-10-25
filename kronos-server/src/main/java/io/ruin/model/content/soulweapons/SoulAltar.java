package io.ruin.model.content.soulweapons;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.ItemDialogue;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.object.actions.ObjectAction;

/**
 * The Reliquary of Souls, object 62445 (2026-10-18): the Soulbound weapons' altar at home (.dev/cache-restore-tool
 * BuildSoulAltar: an obsidian dais whose floating soul core, archetype emblems and wisps turn on one loc sequence,
 * BuildLocSpinAnims). Spawned at 3123,3490 (3x3, centred on 3124,3491) in the Soulbound Sanctuary: the old fenced yard
 * there (map l48_54) was removed with RemoveMapLocs and floored with arcane flagstones, a soul brazier on each corner.
 * <ul>
 * <li>Commune: with a weapon bound, the player kneels in prayer, a soul burst rises and the Soul Tree opens; without
 * one, the altar says so and offers the selector.</li>
 * <li>Choose-Weapon: the selector for players without a weapon; a bound player is told which weapon holds their soul
 * (and gets a lost weapon back, progress intact, through SoulWeapons.claim).</li>
 * <li>Guide: four pages on Soul XP, tiers and evolutions, the Soul Tree and auras, and resets / PvM only.</li>
 * </ul>
 * Examine is answered by ObjectActionHandler with {@link #EXAMINE}.
 */
public final class SoulAltar {
	public static final int OBJECT = 65525;
	public static final String EXAMINE = "A conduit of ancient souls, resonating with living weapons.";
	private static final int ANIM_PRAY = 645, GFX_SOUL_BURST = 6827;

	private SoulAltar() {
	}

	static void register() {
		ObjectAction.register(OBJECT, 1, (player, obj) -> commune(player));
		ObjectAction.register(OBJECT, 2, (player, obj) -> chooseWeapon(player));
		ObjectAction.register(OBJECT, 3, (player, obj) -> guide(player));
	}

	private static void commune(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null) {
			player.dialogue(new MessageDialogue("The altar hums silently...<br>You must first bond with a living weapon."),
					new OptionsDialogue("Bond with a living weapon now?",
							new Option("Yes, show me the weapons.", () -> SoulStarterInterface.open(player)),
							new Option("Not yet.", player::closeDialogue)));
			return;
		}
		player.animate(ANIM_PRAY);
		player.graphics(GFX_SOUL_BURST, 0, 0);
		player.sendMessage(SoulWeapons.TAG + "Your " + d.weaponType.name + " resonates with the Reliquary.");
		SoulTreeInterface.open(player);
	}

	private static void chooseWeapon(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null) {
			SoulStarterInterface.open(player);
			return;
		}
		if (!SoulWeapons.owns(player, d.weaponType)) {
			SoulWeapons.claim(player); // lost or destroyed: it returns, souls intact
			return;
		}
		player.dialogue(new ItemDialogue().one(d.weaponType.itemFor(d.tier), "Your soul is already bound to the "
				+ d.weaponType.name + ".<br>Only one weapon may be bonded per journey."));
	}

	private static void guide(Player player) {
		int icon = player.soulWeapon != null ? player.soulWeapon.weaponType.itemFor(player.soulWeapon.tier)
				: SoulWeaponType.MELEE.itemFor(1);
		player.dialogue(
				new ItemDialogue().one(icon, "<col=7d1fd1>Living Weapons</col> (1/4)<br>A Soulbound weapon grows with you. Slay "
						+ "monsters while wielding it to harvest Soul XP: a quarter of their combat level, and bosses add 50 to 250."),
				new ItemDialogue().one(SoulWeaponType.MELEE.itemFor(8), "<col=7d1fd1>Tiers & Evolutions</col> (2/4)<br>Soul XP "
						+ "raises your weapon from Tier 1 to Tier 10, stronger at every tier. It evolves into a new form at "
						+ "Tier 4 and Tier 8; the Ascended form (Tier 8) unlocks a special attack."),
				new ItemDialogue().one(SoulWeaponType.MAGIC.itemFor(8), "<col=7d1fd1>Soul Tree & Auras</col> (3/4)<br>Every new "
						+ "tier grants a Soul Point. Spend them on three talents in the Soul Tree (5 ranks each). Tiers 5, 8 "
						+ "and 10 awaken cosmetic auras that glow around you."),
				new ItemDialogue().one(SoulWeaponType.RANGED.itemFor(8), "<col=7d1fd1>Resets & Safety</col> (4/4)<br>Talents work "
						+ "only against monsters and never in Rivals. Your first Soul Tree reset is completely free. A lost "
						+ "weapon returns from this altar with its souls intact."),
				new OptionsDialogue("The Reliquary awaits.",
						new Option(player.soulWeapon != null ? "Commune (open my Soul Tree)" : "Bond with a living weapon",
								() -> commune(player)),
						new Option("Close.", player::closeDialogue)));
	}
}
