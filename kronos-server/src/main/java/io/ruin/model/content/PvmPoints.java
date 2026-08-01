package io.ruin.model.content;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/*
 * @project Kronos
 * @author Patrity - https://github.com/Patrity
 * Created on - 6/7/2020
 */
public class PvmPoints {

    private static final Map<String, Integer> BOSS_POINTS = new HashMap<>();

    static {
        // Tier 1 - Easy bosses (2 pts)
        for (String name : new String[]{
            "giant mole", "obor", "scorpia", "chaos fanatic", "crazy archaeologist",
            "deranged archaeologist", "dagannoth rex", "dagannoth prime", "dagannoth supreme",
            "king black dragon", "kalphite queen", "chaos elemental",
            "vote boss", "blood reaper", "stone poltegeist",
            "sarachnis", "lizardman shaman", "elder chaos druid",
            "tainted unicorn", "ophidia"
        }) BOSS_POINTS.put(name, 2);

        // Tier 2 - Medium bosses (5 pts)
        for (String name : new String[]{
            "cerberus", "zulrah", "thermonuclear smoke devil", "kraken",
            "abyssal sire", "commander zilyana", "general graardor", "kree'arra",
            "k'ril tsutsaroth", "callisto", "venenatis", "vet'ion", "corporeal beast",
            "dusk", "skotizo", "grotesque guardians",
            "catalyst brute", "catalyst ranger", "catalyst mager",
            "scurrius", "phantom muspah"
        }) BOSS_POINTS.put(name, 5);

        // Tier 3 - Hard bosses (10 pts)
        for (String name : new String[]{
            "vorkath", "alchemical hydra", "nightmare", "the nightmare", "nex",
            "vardorvis", "duke sucellus", "leviathan", "the whisperer",
            "araxxor", "tormented demon", "yama", "malakar", "argentavis",
            "sol heredit", "galvek", "balance elemental",
            "revenantmaledictus", "revenant maledictus"
        }) BOSS_POINTS.put(name, 10);

        // Tier 4 - Elite bosses & raid final bosses (20 pts)
        for (String name : new String[]{
            "tztok-jad", "tzkal-zuk",
            "great olm", "verzik vitur",
            "tumeken's warden", "elidinis' warden",
            "ba-ba", "akkha", "kephri", "zebak"
        }) BOSS_POINTS.put(name, 20);
    }

    public static void addPoints(Player player, NPC npc) {
        if (player == null || npc == null || npc.getDef() == null) return;
        if (!player.isPvmMode()) return; // PVP Mode accounts never earn PVM Points
        String name = npc.getDef().name.toLowerCase();
        Integer points = BOSS_POINTS.get(name);
        if (points != null && points > 0) {
            player.PvmPoints += points;
        }
    }

    /** True if {@code npc} has a dedicated entry in the named-boss point table above. */
    public static boolean hasNamedPoints(NPC npc) {
        if (npc == null || npc.getDef() == null) return false;
        return BOSS_POINTS.containsKey(npc.getDef().name.toLowerCase());
    }

    /** Award points directly for raid completions (CoX, ToB, ToA). */
    public static void addRaidPoints(Player player, int points) {
        if (player == null || !player.isPvmMode()) return; // PVP Mode accounts never earn PVM Points
        player.PvmPoints += points;
    }
}
