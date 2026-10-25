package io.ruin.model.entity.npc.combatpet;

import io.ruin.model.map.Projectile;

import java.util.HashMap;
import java.util.Map;

/**
 * Tier-tinted copies of the spotanims tiered pets use (cache: "gfx" lines in
 * .dev/cache-restore-tool/tier_pets.txt -- keep in sync). T1 = bronze recolour, T2 = electric texture 187 +
 * cyan tint, T3 = cosmic texture 186 + violet tint.
 */
public final class TierEffects {

	private static final Map<Integer, int[]> TINTED = new HashMap<>();

	static {
		tint(380, 6646, 6647, 6648);   // shadow blitz projectile
		tint(381, 6649, 6650, 6651);   // shadow blitz impact
		tint(383, 6652, 6653, 6654);   // shadow barrage burst
		tint(2010, 6655, 6656, 6657);  // maledictus spectral projectile
		tint(2002, 6658, 6659, 6660);  // maledictus blood projectile
		tint(2005, 6661, 6662, 6663);  // maledictus impact
		tint(1028, 6664, 6665, 6666);  // earthquake tremor
		tint(1377, 6667, 6668, 6669);  // jal-xil spear
		tint(1907, 6670, 6671, 6672);  // xamphur orb
		tint(1908, 6673, 6674, 6675);  // xamphur orb impact
		tint(1914, 6676, 6677, 6678);  // xamphur claws
		tint(1919, 6679, 6680, 6681);  // xamphur summon burst
	}

	private TierEffects() {
	}

	private static void tint(int base, int t1, int t2, int t3) {
		TINTED.put(base, new int[]{t1, t2, t3});
	}

	/** The tier's copy of {@code baseGfx}, or the base id when it has no tinted copies. */
	public static int of(int baseGfx, PetTier tier) {
		int[] ids = TINTED.get(baseGfx);
		return ids == null ? baseGfx : ids[tier.ordinal()];
	}

	public static Projectile of(Projectile base, PetTier tier) {
		return new Projectile(of(base.getGfxId(), tier), base.getStartHeight(), base.getEndHeight(), base.getDelay(),
				base.getDurationStart(), base.getDurationIncrement(), base.getCurve(), base.getIdk());
	}

}
