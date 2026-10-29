package io.ruin.model.content.soulweapons;

import io.ruin.model.World;
import io.ruin.model.content.rivals.RivalsFairPlay;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;

/**
 * Soulbound auras (2026-10-18), cosmetic only: unlocked by tier, one active at a time (SoulTreeInterface), saved as
 * {@link SoulWeaponData#activeCosmeticAura}. While the player's own Soulbound weapon is wielded and they're out of
 * combat, the aura pulses around them every {@link #PULSE_TICKS} ticks; everyone nearby sees it (a plain spot-anim).
 * Out of combat only, so it never replaces a special attack's or a spell's effect. The spot-anims are recoloured copies
 * built by .dev/cache-restore-tool soulbound_aura_gfx.txt.
 */
public enum SoulAura {
	SOUL_WISPS("Soul Wisps", 5, 6827),
	ASCENDANT_RADIANCE("Ascendant Radiance", 8, 6828),
	// 2026-10-29 round 3: in the Wisps / Radiance family but its own (cache stage 6, PatchRaid4Spotanims): 6839, a larger
	// halo of the sparkle rings alternating violet and cyan, crowned by 6840, a small white-cyan ring at head height;
	// both on a silent sequence. Was the purple sparkle burst 6829, then 78 / 2125 / the Tumeken flare copy.
	ETERNAL_SOULFIRE("Eternal Soulfire", 10, 6839);

	/** Eternal Soulfire's crown ring: its own spot slot, head height, a beat after the halo. */
	private static final int CROWN_GFX = 6840, CROWN_SLOT = 3, CROWN_HEIGHT = 110, CROWN_DELAY = 15;

	private static final int PULSE_TICKS = 8, CALM_TICKS = 10;

	public final String name;
	public final int tier, gfx;

	SoulAura(String name, int tier, int gfx) {
		this.name = name;
		this.tier = tier;
		this.gfx = gfx;
	}

	public boolean unlocked(SoulWeaponData d) {
		return d != null && d.tier >= tier;
	}

	/** The player's active aura, or null (also null if the saved name is unknown or no longer unlocked). */
	static SoulAura active(SoulWeaponData d) {
		if (d == null || d.activeCosmeticAura == null)
			return null;
		for (SoulAura a : values())
			if (a.name().equals(d.activeCosmeticAura))
				return a.unlocked(d) ? a : null;
		return null;
	}

	/** The aura the player just unlocked at this tier, if any (announced by SoulWeapons.addXp). */
	static SoulAura unlockedAt(int tier) {
		for (SoulAura a : values())
			if (a.tier == tier)
				return a;
		return null;
	}

	static void register() {
		World.startEvent(e -> {
			while (true) {
				e.delay(PULSE_TICKS);
				for (Player player : World.players()) {
					try {
						pulse(player);
					} catch (Exception ex) {
						ex.printStackTrace(); // one bad player must not stop every aura
					}
				}
			}
		});
	}

	private static void pulse(Player player) {
		SoulWeaponData d = player.soulWeapon;
		SoulAura aura = active(d);
		if (aura == null || player.dead() || player.isLocked() || RivalsFairPlay.active(player))
			return;
		Item weapon = player.getEquipment().get(Equipment.SLOT_WEAPON);
		if (weapon == null || !d.weaponType.isLook(weapon.getId()))
			return;
		if (player.getCombat().isAttacking(CALM_TICKS) || player.getCombat().isDefending(CALM_TICKS))
			return;
		play(player, aura);
	}

	/** One pulse of an aura on the player (also the taste shown when it is switched on). */
	static void play(Player player, SoulAura aura) {
		player.graphics(aura.gfx, 0, 0);
		if (aura == ETERNAL_SOULFIRE && player.avatarExtended() != null)
			player.avatarExtended().setSpotAnim(CROWN_SLOT, CROWN_GFX, CROWN_DELAY, CROWN_HEIGHT, false);
	}
}
