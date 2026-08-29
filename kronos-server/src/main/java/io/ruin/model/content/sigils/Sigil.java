package io.ruin.model.content.sigils;

/**
 * Deadman Mode combat/permanent sigils. Numbers below match the "Deadman: Annihilation"
 * event's effect text (the most recent/canonical revision per the OSRS wiki) for the four
 * sigils this server has a confirmed real effect for. Plain data only -- no functional/effect
 * fields, matching this codebase's existing idiom for enums like Perks/LoyaltyTitle: the
 * actual combat-hook logic lives at each call site (see SigilManager.isAttuned/isUnlocked
 * usages in PlayerCombat.java and TargetSpell.java), not inside the enum itself.
 *
 * ~36 other real "Sigil of X" items exist in the cache (ids 25990-26148) with no known/ported
 * effect yet -- adding one later is just another entry here plus a call-site `if` block, no
 * framework changes needed.
 */
public enum Sigil {

	RESILIENCE(25990, 25991, 25992, "Sigil of resilience", Category.PERMANENT,
		"All attacks from monsters do 25% less damage."),
	// NOT SAFELY OBTAINABLE: the real OSRS ids for this item (26002-26004) were already
	// repurposed on this server for UpgradeManager's Ring of the Undead/Beasts/Arachnids
	// (confirmed live 2026-08-29). ObjType.setCustomFields() deliberately does NOT patch
	// an "Attune" option onto these ids, so this entry can never actually be attuned in
	// its current state -- it's kept here (rather than deleted) only so the rune-save
	// hook in TargetSpell.java has something to reference; isAttuned() for it is always
	// false until a genuinely free item id is found for this sigil.
	METICULOUS_MAGE(26002, 26003, 26004, "Sigil of the meticulous mage", Category.COMBAT,
		"Your magic accuracy is increased by +40 and you have a 50% chance to save runes or staff charges when casting spells."),
	RIGOROUS_RANGER(25999, 26000, 26001, "Sigil of the rigorous ranger", Category.COMBAT,
		"Your ranged accuracy is increased by +20, and you have a 50% chance to save ammunition when using ranged weapons, except when throwing chinchompas."),
	MENACING_MAGE(26077, 26078, 26079, "Sigil of the menacing mage", Category.COMBAT,
		"Upon dealing magic damage, you have a 15% chance to curse the target. The curse deals 18 damage over the next 6 seconds and also heals you for the same amount. A target can only be under the effect of one curse at a time."),

	;

	public enum Category {
		COMBAT,    // max MAX_ATTUNED_COMBAT_SIGILS active at once, see SigilManager
		PERMANENT, // uncapped, active immediately once unlocked
	}

	public final int attunedId;
	public final int unattunedId;
	public final int notedId;
	public final String displayName;
	public final Category category;
	public final String description;

	Sigil(int attunedId, int unattunedId, int notedId, String displayName, Category category, String description) {
		this.attunedId = attunedId;
		this.unattunedId = unattunedId;
		this.notedId = notedId;
		this.displayName = displayName;
		this.category = category;
		this.description = description;
	}

	public static final Sigil[] VALUES = values();

}
