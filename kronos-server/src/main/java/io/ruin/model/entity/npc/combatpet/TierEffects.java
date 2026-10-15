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
		tint(380, 4239, 4240, 4241);   // shadow blitz projectile
		tint(381, 4242, 4243, 4244);   // shadow blitz impact
		tint(383, 4245, 4246, 4247);   // shadow barrage burst
		tint(2010, 4248, 4249, 4250);  // maledictus spectral projectile
		tint(2002, 4251, 4252, 4253);  // maledictus blood projectile
		tint(2005, 4254, 4255, 4256);  // maledictus impact
		tint(1028, 4257, 4258, 4259);  // earthquake tremor
		tint(1377, 4260, 4261, 4262);  // jal-xil spear
		tint(1907, 4263, 4264, 4265);  // xamphur orb
		tint(1908, 4266, 4267, 4268);  // xamphur orb impact
		tint(1914, 4269, 4270, 4271);  // xamphur claws
		tint(1919, 4272, 4273, 4274);  // xamphur summon burst
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
