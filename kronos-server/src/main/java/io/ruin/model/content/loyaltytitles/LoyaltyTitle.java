package io.ruin.model.content.loyaltytitles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ported from nr-wicked's "Loyalty Titles" cache struct data (ids 30000-30174).
 * Two source data anomalies were fixed during transcription:
 * - id 30167 ("King Pin"): source cost field was a corrupted overflowed int; since its
 *   requirement isn't a "Purchasable..." one, cost is unused for it anyway - set to 0.
 * - id 30174 ("the Magnificent"): source stored its (suffix-shaped) text under the prefix
 *   param keys; stored here correctly as a suffix.
 */
public class LoyaltyTitle {

	public enum Category {
		GENERAL, PVM, PVP, MASTERY, SKILLING, DONATOR
	}

	public enum Currency {
		COINS("coins"), DONATOR_POINTS("donator points"), PVM_POINTS("PVM points"), PK_POINTS("PK points");

		public final String label;

		Currency(String label) {
			this.label = label;
		}
	}

	public static final int CURRENCY_ITEM = 995; // coins - every title in the source data uses this

	public final int id;
	public final Category category;
	public final boolean prefix; // true = prefix-style ("Text Name"), false = suffix-style ("Name text")
	public final String text; // includes color tags and the separating space, exactly as authored
	public final String requirement;
	public final int cost;
	public final Currency currency;

	private LoyaltyTitle(int id, Category category, boolean prefix, String text, String requirement, int cost, Currency currency) {
		this.id = id;
		this.category = category;
		this.prefix = prefix;
		this.text = text;
		this.requirement = requirement;
		this.cost = cost;
		this.currency = currency;
	}

	public boolean isPurchasable() {
		return requirement != null && requirement.startsWith("Purchasable");
	}

	// matches the single <col=RRGGBB>...</col> span every title's text is authored with
	private static final Pattern COLOR_OPEN = Pattern.compile("<col=([0-9a-fA-F]{6})>");

	/**
	 * Nests an explicit <shad=000000> inside the color tag, matching the same
	 * <col=X><shad=Y>text</shad></col> convention used by the older staff/donator
	 * Title.java titles, so loyalty titles render with the same shaded look.
	 */
	private String shadedText() {
		return COLOR_OPEN.matcher(text).replaceFirst("$0<shad=000000>")
				.replaceFirst("</col>", "</shad></col>");
	}

	public String preview(String playerName) {
		String shaded = shadedText();
		return prefix ? (shaded + playerName) : (playerName + shaded);
	}

	public String priceRequirementText() {
		return requirement.replace("%price%", String.valueOf(cost));
	}

	private static final List<LoyaltyTitle> REGISTRY = new ArrayList<>();
	private static final Map<Integer, LoyaltyTitle> BY_ID = new HashMap<>();

	private static void p(int id, Category cat, String text, String req, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, true, text, req, cost, Currency.COINS));
	}

	private static void s(int id, Category cat, String text, String req, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, false, text, req, cost, Currency.COINS));
	}

	// donator-point-priced variants, used for the DONATOR category's purchasable titles
	private static void pDp(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, true, text, PURCHASE_DP, cost, Currency.DONATOR_POINTS));
	}

	private static void sDp(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, false, text, PURCHASE_DP, cost, Currency.DONATOR_POINTS));
	}

	// PVM-point-priced variants, used for PVM category purchasable titles
	private static void pPvm(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, true, text, PURCHASE_PVM, cost, Currency.PVM_POINTS));
	}

	private static void sPvm(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, false, text, PURCHASE_PVM, cost, Currency.PVM_POINTS));
	}

	// PK-point-priced variants, used for PVP category purchasable titles
	private static void pPk(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, true, text, PURCHASE_PK, cost, Currency.PK_POINTS));
	}

	private static void sPk(int id, Category cat, String text, int cost) {
		REGISTRY.add(new LoyaltyTitle(id, cat, false, text, PURCHASE_PK, cost, Currency.PK_POINTS));
	}

	public static void register() {
		for (LoyaltyTitle t : REGISTRY)
			BY_ID.put(t.id, t);
	}

	public static LoyaltyTitle get(int id) {
		return BY_ID.get(id);
	}

	public static List<LoyaltyTitle> all() {
		return Collections.unmodifiableList(REGISTRY);
	}

	private static final String PURCHASE = "Purchasable from the Title Store for %price% coins";
	private static final String PURCHASE_DP = "Purchasable from the Title Store for %price% donator points";
	private static final String PURCHASE_PVM = "Purchasable from the Title Store for %price% PVM points";
	private static final String PURCHASE_PK = "Purchasable from the Title Store for %price% PK points";

	static {
		s(30001, Category.GENERAL, "<col=C86402> the Fallen</col>", PURCHASE, 400000000);
		p(30002, Category.GENERAL, "<col=C86402>Lionheart </col>", PURCHASE, 250000000);
		p(30003, Category.GENERAL, "<col=C86402>Master </col>", PURCHASE, 700000000);
		s(30004, Category.GENERAL, "<col=C86402> the Undefeated</col>", PURCHASE, 1200000000);
		s(30005, Category.PVM, "<col=4169E1>, the Regal Ravager</col>", "Kill each Dagannoth King 250 times", 50000000);
		p(30006, Category.GENERAL, "<col=1369AD>Day One </col>", "Login day 1 of launch", 25000000);
		s(30007, Category.GENERAL, "<col=047F06> the Devoted</col>", "7 days playtime", 50000000);
		p(30008, Category.PVM, "<col=474341>Bukalla's Heir, </col>", "Complete Dagannoth Kings Collection Log", 50000000);
		p(30009, Category.PVP, "<col=C86400>Assassin </col>", "250 Kills", 25000000);
		p(30010, Category.GENERAL, "<col=C86402>King </col>", PURCHASE, 500000000);
		p(30011, Category.PVM, "<col=FAB402>Nexpert </col>", "Complete the Nex Collection Log", 100000000);
		s(30012, Category.PVM, "<col=C120C1> the Nihil</col>", "Kill Nex 250 times", 50000000);
		s(30013, Category.PVM, "<col=784F23> the Stoutslayer</col>", "Complete 250 Slayer Tasks", 100000000);
		s(30014, Category.PVM, "<col=FAB402> the Ultimate Slayer</col>", "Kill all Slayer Bosses 100 times", 100000000);
		s(30015, Category.PVP, "<col=FC3838> the Beast</col>", "750 Player Kills", 50000);
		p(30016, Category.SKILLING, "<col=74C165>Log Lord </col>", "Obtain 99 Woodcutting", 50000000);
		s(30017, Category.SKILLING, "<col=047F06> the Master Farmer</col>", "Obtain 200m experience in farming", 100000000);
		p(30018, Category.GENERAL, "<col=C86402>Emperor </col>", PURCHASE, 300000000);
		s(30019, Category.PVM, "<col=6060A0>, Armadylean</col>", "250 Kree'Arra Kills", 25000000);
		s(30020, Category.PVP, "<col=8904B1> the Annihilator</col>", "1000 Player Kills", 75000);
		s(30021, Category.PVM, "<col=801A21>, Brawn of a Tsutsaroth</col>", "Complete T'sutsaroth Collection Log", 50000000);
		s(30022, Category.MASTERY, "<col=eda011> the True Completionist</col>", "Tier 3 Completionist Cape", 500000000);
		p(30023, Category.PVM, "<col=525252>Grotesque </col>", "Kill the Grotesuqe Guardians 100 times", 50000000);
		s(30024, Category.PVM, "<col=C8CADC>, Swiftness of the Aviansie</col>", "Complete the Kree'Arra Collection Log", 100000000);
		p(30025, Category.GENERAL, "<col=C86402>Crusader </col>", PURCHASE, 300000000);
		p(30026, Category.SKILLING, "<col=8b0000>Duradel's Disciple </col>", "Obtain 200m Slayer Experience", 100000000);
		p(30027, Category.PVM, "<col=936F84>cHaOs </col>", "Complete the Chaos Elemental Collection Log", 50000000);
		p(30028, Category.SKILLING, "<col=567b40>Quiver Quipper </col>", "Obtain 99 Fletching", 50000000);
		p(30029, Category.GENERAL, "<col=C86402>Count </col>", PURCHASE, 150000000);
		s(30030, Category.PVM, "<col=06A010>, the Twisted</col>", "Complete 50 Chamber of Xeric Raids", 50000000);
		p(30031, Category.GENERAL, "<col=dbe048>Stakemaster </col>", "Win 100 Stakes", 100000000);
		p(30032, Category.MASTERY, "<col=C12006>Maxed </col>", "Max all skills", 250000000);
		p(30033, Category.PVM, "<col=7d00ff>Chthonic </col>", "Kill Skotizo 10 times", 50000000);
		p(30034, Category.SKILLING, "<col=8b0000>Vannaka's Vanguard </col>", "Obtain 99 Slayer", 50000000);
		s(30035, Category.GENERAL, "<col=C86402> Junior</col>", PURCHASE, 100000000);
		p(30036, Category.SKILLING, "<col=5d0909>Smith's Sidekick </col>", "Kill Zalcano 25 times", 50000000);
		s(30037, Category.SKILLING, "<col=3bceff> the Codfather</col>", "Obtain 200m fishing experience", 100000000);
		s(30038, Category.MASTERY, "<col=E8EFF7> the Completionist</col>", "Tier 1 / Tier 2 Completionist Cape", 250000000);
		p(30039, Category.SKILLING, "<col=d46200>Pyro </col>", "Obtain 99 Firemaking", 50000000);
		s(30040, Category.MASTERY, "<col=ba061f> the Hardcore Ironman Btw</col>", "Max on a Hardcore Ironman", 25000000);
		p(30041, Category.GENERAL, "<col=C86402>Duchess </col>", PURCHASE, 200000000);
		p(30042, Category.GENERAL, "<col=C86402>Wunderkind </col>", PURCHASE, 1000000000);
		s(30043, Category.PVM, "<col=C86400> of V</col>", "Kill 500 Basilisk Knights", 50000000);
		p(30044, Category.PVM, "<col=27BDC2>Thunderstruck </col>", "Kill Vet'ion 250 times", 50000000);
		p(30045, Category.PVM, "<col=C12006>Sire </col>", "Kill the Abyssal Sire 250 times", 50000000);
		p(30046, Category.PVM, "<col=7C5025>Giantslayer </col>", "Kill Obor & Bryophyta 50 times", 25000000);
		s(30047, Category.PVM, "<col=7dfc53>, the Snake</col>", "Kill Zulrah 500 times", 50000000);
		p(30048, Category.SKILLING, "<col=8904B1>Thief </col>", "99 Thieving", 10000000);
		p(30049, Category.PVM, "<col=453934>TzKal-</col>", "Kill TzKal-Zuk", 100000000);
		s(30050, Category.GENERAL, "<col=006400> the Clue Chaser</col>", "Complete 100 Clue Scrolls", 25000000);
		s(30051, Category.GENERAL, "<col=C86400> the Adorable</col>", PURCHASE, 100000000);
		p(30052, Category.GENERAL, "<col=369EDE>Clueless </col>", "Complete 250 Clue Scrolls", 50000000);
		p(30053, Category.GENERAL, "<col=C86402>Grumpy </col>", PURCHASE, 100000000);
		p(30054, Category.PVM, "<col=e80404>Godslayer </col>", "Complete all God War boss collection logs", 100000000);
		p(30055, Category.GENERAL, "<col=C86400>Archon </col>", PURCHASE, 500000000);
		s(30056, Category.PVM, "<col=AC4C22> the Queenslayer</col>", "Complete the Kalphite Queen Collection Log", 50000000);
		p(30057, Category.SKILLING, "<col=d46200>Kindled </col>", "Obtain 200m Firemaking Experience", 100000000);
		p(30058, Category.SKILLING, "<col=7dfc53>Mixologist </col>", "Obtain 200m Herblore Experience", 100000000);
		p(30059, Category.SKILLING, "<col=ef9ae6>Abyss Diver </col>", "Entered 250 Abyss Rifts", 25000000);
		s(30060, Category.PVM, "<col=eaeaea>, the Revenant</col>", "Kill 2,500 Revenants", 50000000);
		p(30061, Category.GENERAL, "<col=C86402>Duderino </col>", PURCHASE, 150000000);
		p(30062, Category.PVM, "<col=572714>Castellan </col>", "Kill 1000 Demonic Creatures", 50000000);
		p(30063, Category.PVM, "<col=41394C>Dark Core </col>", "Kill the Corporeal Beast 250 times", 50000000);
		p(30064, Category.SKILLING, "<col=ca8d42>Apprentice Baiter </col>", "99 Hunter", 10000000);
		p(30065, Category.GENERAL, "<col=C86402>Overlord </col>", PURCHASE, 1000000000);
		p(30066, Category.PVM, "<col=385d3b>Hydra Hugger </col>", "Kill the Alchemical Hydra 250 times", 50000000);
		s(30067, Category.GENERAL, "<col=FFD700> the Gold Digger</col>", "Complete 1,000 Clue Scrolls", 250000000);
		s(30068, Category.SKILLING, "<col=0F5C0F> the Gardener</col>", "Achieve 99 Farming", 25000000);
		p(30069, Category.PVM, "<col=8904b1>Nucleur </col>", "Kill the Thermonucleur Smoke Devil 250 times", 50000000);
		s(30070, Category.PVM, "<col=D8432B> the Abyssal</col>", "Kill 1000 Abyssal Creatures", 25000000);
		p(30071, Category.PVM, "<col=ace6d3>Arachnaphobe </col>", "Kill Venenatis 250 times", 50000000);
		p(30072, Category.PVM, "<col=FAB402>Tentacle Tickler </col>", "Kill the Kraken 250 times", 50000000);
		s(30073, Category.GENERAL, "<col=C86402> the Handsome</col>", PURCHASE, 250000000);
		s(30074, Category.PVP, "<col=99a3a4>... you fail</col>", "500 Player Kills", 10000);
		p(30075, Category.PVM, "<col=FAB402>Slayer Master </col>", "Complete 100 Slayer Tasks", 50000000);
		s(30076, Category.PVM, "<col=DF01D7> the Elfborne</col>", "100 Corrupted Gauntlet Completions", 100000000);
		p(30077, Category.PVM, "<col=800080>Mythical Muzzler </col>", "Complete Cerberus's Collection Log", 50000000);
		s(30078, Category.PVM, "<col=00FFFF> of the Elves</col>", "100 Gauntlet Completions", 50000000);
		p(30079, Category.GENERAL, "<col=C86402>The </col>", PURCHASE, 100000000);
		p(30080, Category.PVM, "<col=c538e0>Usine Usurper </col>", "Kill Callisto 250 times", 50000000);
		p(30081, Category.PVM, "<col=57c44b>Sprout Specialist </col>", "Kill Hespori 25 times", 50000000);
		s(30082, Category.GENERAL, "<col=C86402> the Strange</col>", PURCHASE, 250000000);
		s(30083, Category.PVP, "<col=D7F388> the Victor</col>", "Win 10 tournaments", 10000);
		p(30084, Category.PVP, "<col=99a3a4>Cutey-pie </col>", "500 Player Kills", 10000);
		p(30085, Category.SKILLING, "<col=edca53>Gourmet Guru </col>", "Obtain 200m Cooking Experience", 100000000);
		s(30086, Category.PVM, "<col=C120C1> the Muspah</col>", "Kill the Phantom Muspah 250 times", 50000000);
		p(30087, Category.SKILLING, "<col=8904B1>Safecracker </col>", "Obtain 200m Thieving Experience", 100000000);
		s(30088, Category.PVP, "<col=99a3a4> ate dirt</col>", "500 Player Kills", 10000);
		s(30089, Category.SKILLING, "<col=c86400> the Master Fisherman</col>", "Obtain 99 fishing", 100000000);
		s(30090, Category.PVM, "<col=8B4513>, the Burrow Breaker</col>", "Kill the Giant Mole 250 times", 50000000);
		p(30091, Category.PVM, "<col=e699c9>Illusionaist </col>", "Kill the Mimic 25 times", 50000000);
		s(30092, Category.GENERAL, "<col=7DFC53> the Enigmatologist</col>", "Complete 500 Clue Scrolls", 100000000);
		p(30093, Category.GENERAL, "<col=C86402>Duke </col>", PURCHASE, 50000000);
		s(30094, Category.PVM, "<col=87CEEB>, the Azure Slayer</col>", "Complete Vorkath's Collection Log", 50000000);
		p(30095, Category.GENERAL, "<col=C86402>Hellraiser </col>", PURCHASE, 250000000);
		p(30096, Category.SKILLING, "<col=ffa64b>Chisel Chieftan </col>", "Obtain 200m Crafting Experience", 100000000);
		p(30097, Category.SKILLING, "<col=567b40>Bolt Boss </col>", "Obtain 200m Fletching Experience", 100000000);
		p(30098, Category.GENERAL, "<col=C86402>Flyboy </col>", PURCHASE, 300000000);
		s(30099, Category.PVM, "<col=1E90FF>, the Lucid Conqueror</col>", "Kill the Nightmare of Ashihama 250 times", 50000000);
		p(30100, Category.SKILLING, "<col=3bceff>Rune Sage </col>", "Obtain 200m Runecrafting Experience", 100000000);
		s(30101, Category.GENERAL, "<col=C86401> the Awesome</col>", PURCHASE, 50000000);
		p(30102, Category.PVM, "<col=8B0000>Dragonkin </col>", "Kill the King Black Dragon 250 times", 50000000);
		p(30103, Category.GENERAL, "<col=C86402>Baron </col>", PURCHASE, 100000000);
		p(30104, Category.PVM, "<col=BA061F>Final Boss </col>", "5,000 Boss Kills / 100 of all bosses", 250000000);
		s(30105, Category.PVM, "<col=800000>, T'sutsaroth's Scrouge</col>", "Kill T'Sutsaroth 250 times", 50000000);
		p(30106, Category.SKILLING, "<col=57C44B>Paul Bunyan </col>", "Obtain 200m Woodcutting Experience", 100000000);
		p(30107, Category.SKILLING, "<col=ff7600>Alloy Alchemist </col>", "Obtain 200m Smithing Experience", 100000000);
		s(30108, Category.PVM, "<col=C0C0C0>, the Echo's Silencer</col>", "Kill Phosani's Nightmare 250 times", 50000000);
		s(30109, Category.PVM, "<col=C12006> of Vampyrium</col>", "Kill Vanstrom Klause 250 times", 50000000);
		p(30110, Category.PVM, "<col=FF4500>Infernal Tamer </col>", "Kill Cerberus 250 times", 50000000);
		s(30111, Category.PVP, "<col=FC3838> the Aggressive</col>", "100 Player Kills", 15000);
		p(30112, Category.SKILLING, "<col=ff7600>Iron Innovator </col>", "Obtain 99 smithing", 50000000);
		s(30113, Category.PVP, "<col=99a3a4> the Idiot</col>", "500 Player Kills", 10000);
		s(30114, Category.PVP, "<col=99a3a4> the Flamboyant</col>", "500 Player Kills", 50000000);
		p(30115, Category.PVM, "<col=FAB402>Arachnofoe </col>", "250 Sarachnis Kills", 25000000);
		p(30116, Category.GENERAL, "<col=C86400>Athlete </col>", PURCHASE, 400000000);
		p(30117, Category.PVM, "<col=06A010>Moss Master </col>", "Complete Bryophyta's Collection Log", 50000000);
		p(30118, Category.GENERAL, "<col=C86402>Bandito </col>", PURCHASE, 150000000);
		p(30119, Category.GENERAL, "<col=C86401>Cheerful </col>", PURCHASE, 120000000);
		s(30120, Category.GENERAL, "<col=C81414> the Gamebreaker</col>", "Awarded to members who have helped the game in immeasurable ways", 0);
		s(30121, Category.MASTERY, "<col=6D6D75> the Ironman Btw</col>", "Max on a Ironman", 25000000);
		p(30122, Category.PVM, "<col=c49ac6>Phantom </col>", "Complete the Phantom Muspah Collection Log", 50000000);
		s(30123, Category.PVM, "<col=C2A155>, Finesse of the Icyene</col>", "Complete Commander Zilyana Collection Log", 100000000);
		p(30124, Category.GENERAL, "<col=047F06>Veteran </col>", "3/1/2026", 500000000);
		s(30125, Category.PVM, "<col=7C5025> of the Underground</col>", "Complete the Giant Mole Collection Log", 100000000);
		p(30126, Category.SKILLING, "<col=0700ff>Rooftop Racer </col>", "Obtain 99m Agility Experience", 100000000);
		s(30127, Category.GENERAL, "<col=C86402> the Intimidating</col>", PURCHASE, 500000000);
		s(30128, Category.PVM, "<col=3C3C3C> the Last Rider</col>", "Complete the King Black Dragon Collection Log", 50000000);
		p(30129, Category.PVM, "<col=8d6b93>Verzik's Valet </col>", "Complete 100 Theatre of Blood", 100000000);
		s(30130, Category.PVP, "<col=99a3a4>? Who?</col>", "500 Player Kills", 10000);
		s(30131, Category.PVM, "<col=27bdc2> the Ascended</col>", "Complete the Slayer Collection Log", 100000000);
		p(30132, Category.SKILLING, "<col=b3fff2>Blizzard's BFF </col>", "Subdue the Wintertodt 25 times", 50000000);
		s(30133, Category.PVP, "<col=99a3a4> the Fail Magnet</col>", "500 Player Kills", 50000000);
		p(30134, Category.SKILLING, "<col=ca8d42>Master Baiter </col>", "200M Hunter Experience", 100000000);
		s(30135, Category.GENERAL, "<col=C86402> the Divine</col>", PURCHASE, 400000000);
		p(30136, Category.SKILLING, "<col=0700ff>Balance Baron </col>", "Obtain 99 Agility", 50000000);
		s(30137, Category.SKILLING, "<col=ffa64b>, the Journmeyman</col>", "Obtain 99 Crafting", 50000000);
		p(30138, Category.PVP, "<col=99a3a4>Everyone attack </col>", "500 Player Kills", 50000000);
		p(30139, Category.PVP, "<col=99a3a4>Delusional </col>", "500 Player Kills", 10000);
		s(30140, Category.GENERAL, "<col=B432C9> the Distracted</col>", "Complete the Minigames Collection Log", 100000000);
		p(30141, Category.MASTERY, "<col=E6591A>Kal-Haar-Xil </col>", "Complete the Inferno and Fight Caves Collection Log", 250000000);
		s(30142, Category.PVM, "<col=6D6D75> Zilyana's Bane</col>", "Kill Commander Zilyana 250 times", 100000000);
		s(30143, Category.PVM, "<col=1369AD> the Dragonrider</col>", "250 Vorkath Kills", 50000000);
		p(30144, Category.GENERAL, "<col=C86402>Witch King </col>", PURCHASE, 250000000);
		p(30145, Category.SKILLING, "<col=cfaa80>Rockhard </col>", "Obtain 200m Mining Experience", 100000000);
		s(30146, Category.PVM, "<col=74c165>, the Xeric's Vanguard</col>", "Complete 50 Challenge Mode CoX", 100000000);
		p(30147, Category.GENERAL, "<col=6D6D75>Ironman </col>", "Ironman Status", 5000000);
		p(30148, Category.PVM, "<col=C12006>TzHaar-</col>", "Gamble a Fire Cape", 50000000);
		p(30149, Category.GENERAL, "<col=7DFC53>Globetrotter </col>", "Complete 750 Clue Scrolls", 100000000);
		s(30150, Category.PVM, "<col=6B7732>, Strength of the Ourgs</col>", "Complete the General Graardor's Collection Log", 100000000);
		s(30151, Category.GENERAL, "<col=C86402> the Mysterious</col>", PURCHASE, 500000000);
		s(30152, Category.GENERAL, "<col=a9d8fe> the Adventurer</col>", "14 days play time", 25000000);
		p(30153, Category.GENERAL, "<col=FAB402>Double Agent </col>", "Complete 50 Clue Scrolls", 25000000);
		p(30154, Category.PVM, "<col=c10000>Bloody </col>", "Complete 25 Theatre of Blood", 50000000);
		p(30155, Category.PVM, "<col=DED82F>Deacon </col>", "Kill 2500 Demonic Creatures", 25000000);
		p(30156, Category.GENERAL, "<col=C86402>Desperado </col>", PURCHASE, 250000000);
		p(30157, Category.GENERAL, "<col=ba061f>Hardcore Ironman </col>", "Hardcore Ironman Status", 10000000);
		p(30158, Category.SKILLING, "<col=7dfc53>Brewmaster </col>", "Obtain 99 Herblore", 50000000);
		p(30159, Category.GENERAL, "<col=C86402>Big Cheese </col>", "Kill 1000 Rats", 50000000);
		p(30160, Category.GENERAL, "<col=C86402>Bigwig </col>", PURCHASE, 350000000);
		p(30161, Category.SKILLING, "<col=FAB402>Chef </col>", "Obtain 99 Cooking", 50000000);
		p(30162, Category.SKILLING, "<col=3bceff>Mystic Misfit </col>", "Obtain 99 Runecrafting", 50000000);
		p(30163, Category.GENERAL, "<col=FAB402>Master of Clues </col>", "Complete 1500 Clue Scrolls", 100000000);
		p(30164, Category.SKILLING, "<col=c4b48f>Mine Over Matter </col>", "Obtain 99 Mining", 50000000);
		p(30165, Category.PVM, "<col=fff2cc>Goliath's Bane </col>", "Complete Obor's Collection Log", 50000000);
		p(30166, Category.PVP, "<col=99a3a4>Cowardly </col>", "500 Player Kills", 10000);
		p(30167, Category.GENERAL, "<col=FFD700>King Pin </col>", "Win 1000 stakes", 0);
		p(30168, Category.PVM, "<col=595730>Graverobber </col>", "Complete the Barrows Collection Log", 25000000);
		s(30169, Category.PVM, "<col=06A010>, Bandosian</col>", "250 General Graardor Kills", 25000000);
		s(30170, Category.GENERAL, "<col=C86402> the Hot</col>", PURCHASE, 300000000);
		s(30171, Category.MASTERY, "<col=B02600> the Infernal</col>", "Obtain an Infernal Cape", 100000000);
		s(30172, Category.MASTERY, "<col=EDA011>, Jack of All Trades</col>", "Obtain all pets", 500000000);
		p(30173, Category.GENERAL, "<col=C86402>Esquire </col>", PURCHASE, 250000000);
		s(30174, Category.GENERAL, "<col=C86402> the Magnificent</col>", PURCHASE, 750000000);
		p(30175, Category.GENERAL, "<col=00008B>Beta Tester </col>", "Awarded to players who took part in the beta test", 0);
		p(30200, Category.DONATOR, "<col=0f52ba>Donor </col>", "Donator rank", 0);
		p(30201, Category.DONATOR, "<col=3cb3dd>Super </col>", "Super Donator rank", 0);
		p(30202, Category.DONATOR, "<col=9b111e>Elite </col>", "Elite Donator rank", 0);
		p(30203, Category.DONATOR, "<col=B9F2FF>Noble </col>", "Noble Donator rank", 0);
		p(30204, Category.DONATOR, "<col=FFD700>The Gilded </col>", "Gold Donator rank", 0);
		p(30205, Category.DONATOR, "<col=0f0f0f>Platinum </col>", "Platinum Donator rank", 0);
		p(30206, Category.DONATOR, "<col=bb7c00>Legend </col>", "Legendary Donator rank", 0);
		p(30207, Category.DONATOR, "<col=FC7306>Supreme </col>", "Supreme Donator rank", 0);
		pDp(30208, Category.DONATOR, "<col=00FFFF>ZLUS </col>", 500);
		pDp(30209, Category.DONATOR, "<col=C0392B>VIP </col>", 2000);
		sDp(30210, Category.DONATOR, "<col=FF8C00> the Wallet Warrior</col>", 1000);
		sDp(30211, Category.DONATOR, "<col=2ECC71> the Big Spender</col>", 5000);
		pPvm(30212, Category.PVM, "<col=CD7F32>Grindlord </col>", 1000);
		sPvm(30213, Category.PVM, "<col=4FC3F7> the Untouchable</col>", 650);
		pPk(30214, Category.PVP, "<col=DC143C>Bloodthirsty </col>", 1000);
		pPk(30215, Category.PVP, "<col=000000>KILLER </col>", 5000);
		sPk(30216, Category.PVP, "<col=800000> the Feared</col>", 2500);
		pPk(30217, Category.PVP, "<col=A9A9A9>Edgevillian </col>", 1500);
		// Staff-only, non-purchasable -- granted manually via ::givetitle. Cyan to
		// match the ZLUS donator title's color (30208).
		p(30220, Category.GENERAL, "<col=00FFFF>CM </col>", "Awarded to the Zelus Community Manager", 0);
	}
}
