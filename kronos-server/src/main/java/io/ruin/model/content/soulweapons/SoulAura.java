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
	ETERNAL_SOULFIRE("Eternal Soulfire", 10, 6829);

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
		player.graphics(aura.gfx, 0, 0);
	}
}
