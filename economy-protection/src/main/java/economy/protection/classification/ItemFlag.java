package economy.protection.classification;

/**
 * Economy classification flags for an item id. Not mutually exclusive —
 * an item can carry any combination via {@link ClassifiedItem#flags}.
 */
public enum ItemFlag {
	/** Spawnable via ::spawn. Forces shop price/alch value to 0, blocks trading, and vanishes on drop/death. */
	SPAWNABLE,
	/** Explicitly allowed to be traded even if other rules would otherwise restrict it. */
	TRADABLE,
	/** Full market-value PvP loot — drops physically, subject to the configurable economy sink roll. */
	HIGH_VALUE,
	/** Vanishes when it would land on the ground, without the other SPAWNABLE side effects (price/trade). */
	DISSOLVABLE
}
