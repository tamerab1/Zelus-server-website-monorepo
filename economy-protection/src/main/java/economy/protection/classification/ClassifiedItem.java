package economy.protection.classification;

import java.util.EnumSet;
import java.util.Set;

/** Immutable classification record for a single item id, loaded from JSON config. */
public final class ClassifiedItem {

	public final int id;
	public final Set<ItemFlag> flags;
	/** PvP value used to convert a spawnable item into PKP when it would have died with the victim. */
	public final long pvpValue;
	/** Max amount of this item a single player may spawn per in-game day. 0 = unlimited. */
	public final int dailySpawnCap;

	public ClassifiedItem(int id, Set<ItemFlag> flags, long pvpValue, int dailySpawnCap) {
		this.id = id;
		this.flags = flags == null || flags.isEmpty() ? EnumSet.noneOf(ItemFlag.class) : EnumSet.copyOf(flags);
		this.pvpValue = pvpValue;
		this.dailySpawnCap = dailySpawnCap;
	}

	public boolean has(ItemFlag flag) {
		return flags.contains(flag);
	}
}
