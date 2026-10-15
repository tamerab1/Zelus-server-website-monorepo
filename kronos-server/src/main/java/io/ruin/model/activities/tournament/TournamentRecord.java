package io.ruin.model.activities.tournament;

import java.util.ArrayList;
import java.util.List;

/** One finished Pit Championship, kept in the Hall of Fame (saved to runtime/saves/pit_championships.json). */
public class TournamentRecord {

	public long finishedAt;
	public String format;
	public int entrants;
	public String champion;
	public String runnerUp;
	public List<String> semifinalists = new ArrayList<>();
	/** Results per round, e.g. "Zezima def. Woox" or "Zezima (bye)". */
	public List<List<String>> rounds = new ArrayList<>();
}
