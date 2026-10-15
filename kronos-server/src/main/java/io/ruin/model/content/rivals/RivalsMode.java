package io.ruin.model.content.rivals;

import io.ruin.model.inter.questtab.presets.Preset;

/**
 * The ranked Rivals brackets (2026-10-15: four meta kits, RivalsKits), handed out as phantom items for the match only
 * (RivalsSnapshot), so a player's own gear never enters the arena. One Skill Rating covers every bracket.
 */
public enum RivalsMode {
	ENDGAME_PRISMATIC("Endgame Prismatic", "Torva / Ancestral / Masori / ZCB", "endgame", new RivalsKits.EndgamePrismatic(), false),
	DHAROK_RISK("Dharok Risk-Fight", "Dharok's / Vengeance / No overheads", "dharok", new RivalsKits.DharokRisk(), true),
	WILDERNESS_OUTLAW("Wilderness Outlaw", "Barrows / Mystics / Claws / Ballista", "outlaw", new RivalsKits.WildernessOutlaw(), false),
	MAXED_PURE("Maxed Pure", "75 Att / 1 Def / Elder / AGS / Surge", "pure", new RivalsKits.MaxedPure(), false);

	public final String label;
	/** The card's second line in the matchmaking window. */
	public final String tagline;
	/** ::rivalsforce <name> [key]. */
	public final String key;
	public final Preset kit;
	/** Overhead prayers (protection, retribution, redemption, smite) are refused for the whole match. */
	public final boolean noOverheads;

	RivalsMode(String label, String tagline, String key, Preset kit, boolean noOverheads) {
		this.label = label;
		this.tagline = tagline;
		this.key = key;
		this.kit = kit;
		this.noOverheads = noOverheads;
	}

	static RivalsMode byKey(String key) {
		for (RivalsMode m : values())
			if (m.key.equalsIgnoreCase(key))
				return m;
		return null;
	}
}
