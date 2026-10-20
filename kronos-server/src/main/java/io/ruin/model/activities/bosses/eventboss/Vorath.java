package io.ruin.model.activities.bosses.eventboss;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.activities.VorathHandler;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.route.routes.ProjectileRoute;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.utility.Broadcast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pandora, the Enigma Keeper (npc 17033, was "Vorath") -- the Mystery Box boss (2026-10-20). Its model is a living
 * gift box skinned on the PLAYER skeleton (FindBossRig: skeleton 0, score 0.889; not the Mimic's), so it uses player
 * animations, measured on the mesh: stand 808 / walk 819 (cache), punch 422 / kick 423 up close, a toss 7617 for its
 * crystals, the two-handed slam 2068 for its specials, block 424 and death 836 (npcs/combat/Vorath.json).
 * <p>
 * The fight:
 * <ul>
 *   <li><b>Everyone who fights it gets fought back.</b> Pandora remembers who hit it in the last
 *   {@link #ATTACKER_MEMORY} ticks; with more than one attacker every attack goes at all of them: adjacent ones get the
 *   punch / kick, the rest a crystal each.</li>
 *   <li><b>Enigma Crystals</b> -- its magic: spinning violet prism cubes (spotanim {@link #CRYSTAL}) that shatter in a
 *   purple burst ({@link #CRYSTAL_BURST}). Protect from magic halves them until it enrages.</li>
 *   <li><b>What's in the box?!</b> (every {@link #SPECIAL_EVERY}th attack, alternating) -- either the Shadow Slam
 *   (a marked blast under everyone within 7 tiles, 2 ticks to step off) or <b>Prize Rain</b>: gift-crystals fall on
 *   three tiles around each player; standing on one when it lands hurts.</li>
 *   <li><b>Chronos the Timekeeper</b> (16201, the custom boss from 2026-10-19) answers its call at 75 / 50 / 25% HP --
 *   at most {@link #MAX_KEEPERS} at once. Each hunts a random attacker, and every few seconds a keeper <b>stops
 *   time</b> on its victim (a 3-second freeze). They vanish when Pandora dies and drop nothing.</li>
 *   <li>It chases whoever it's fighting, but only up to {@link #LEASH} tiles from its spawn, and drifts home when idle.</li>
 *   <li><b>Always prays protect from melee</b>: melee hits are blocked, so it's a ranged / magic fight.</li>
 *   <li>Under 40% HP it bursts open (enrage): crystals pierce protect from magic and specials hit harder.</li>
 * </ul>
 * Rewards (every player who hit it): a Mystery box and 15-30K coins guaranteed, then one roll on {@link #TABLE}
 * (very rare: Arcane mystery box; uncommon: Super / Limited mystery box)
 * (shown by examine in the loot viewer, LootsTables.PANDORA): very rare ~1/250, rare ~1/60, uncommon ~1/20, common ~1/4.
 */
public class Vorath extends NPCCombat {

    public static final String NAME = "Pandora, the Enigma Keeper";
    public static final String EXAMINE = "A chaotic entity bound within a dimensional prize chest. Slay it to claim its treasures.";

    private static final int PUNCH = 422, KICK = 423, CRYSTAL_TOSS = 7617, SLAM = 2068;
    /** The violet prism cube (model 33007, spinning) and the purple burst it shatters into. */
    private static final int CRYSTAL = 2534, CRYSTAL_BURST = 2535, PRIZE_DROP = 2536;
    private static final Projectile CRYSTAL_PROJECTILE = new Projectile(CRYSTAL, 70, 31, 30, 50, 10, 12, 64);
    /** Falls from high above onto a marked tile. */
    private static final Projectile PRIZE_FALL = new Projectile(PRIZE_DROP, 250, 0, 0, 60, 0, 0, 0);
    private static final int WARNING_GFX = 1026, SLAM_GFX = 1209, FREEZE_GFX = 369;

    /**
     * Living eyes: the model has no bones on its eyes, so every {@link #EYE_EVERY} ticks a colour sparkle pops just in
     * front of its face (2 tiles from its centre toward whoever it's facing, at eye height), cycling white star / blue /
     * purple / gold.
     */
    private static final int[] EYE_SPARKLES = {3379, 1873, 2836, 313};
    private static final int EYE_HEIGHT = 560, EYE_EVERY = 5;
    private int eyeTicks = 0, eyeColour = 0;

    private static final int CHRONOS = 16201, MAX_KEEPERS = 2, ATTACKER_MEMORY = 17, SPECIAL_EVERY = 6;

    private static final int MYSTERY_BOX = 6199, SUPER_MYSTERY_BOX = 290, LIMITED_MYSTERY_BOX = 30426, ULTRA_MYSTERY_BOX = 30430,
        DONATOR_MYSTERY_BOX = 30461, ARCANE_MYSTERY_BOX = 31618, COINS = 995, ZULRAHS_SCALES = 12934, MASTER_CLUE = 19835,
        DRAGON_BOLTS = 9341, ONYX_BOLTS_E = 9245,
        CELESTIAL_CROWN = 31870, CELESTIAL_TOP = 31872, CELESTIAL_BOTTOM = 31874, CELESTIAL_STAFF = 31876;

    /**
     * The one roll after the guaranteed box + coins, per 10,000 kills: unique (a Celestial piece) 500 (1/20), very rare 40 (1/250), rare 166 (~1/60), uncommon
     * 490 (~1/20), common 2,326 (~1/4), nothing the rest. Rare + very rare drops are broadcast.
     */
    public static final LootTable TABLE = new LootTable()
        .guaranteedItems(new LootItem(MYSTERY_BOX, 1, 1), new LootItem(COINS, 15_000, 30_000, 1))
        // the Celestial set (2026-10-20 armour batch): Pandora's own unique, 1 in 20 kills for a piece
        .addTable("Unique", 500,
            new LootItem(CELESTIAL_CROWN, 1, 1).broadcast(Broadcast.GLOBAL),
            new LootItem(CELESTIAL_TOP, 1, 1).broadcast(Broadcast.GLOBAL),
            new LootItem(CELESTIAL_BOTTOM, 1, 1).broadcast(Broadcast.GLOBAL),
            new LootItem(CELESTIAL_STAFF, 1, 1).broadcast(Broadcast.GLOBAL))
        .addTable("Very rare", 40,
            new LootItem(ARCANE_MYSTERY_BOX, 1, 1).broadcast(Broadcast.GLOBAL))
        .addTable("Rare", 166,
            new LootItem(DONATOR_MYSTERY_BOX, 1, 1).broadcast(Broadcast.GLOBAL),
            new LootItem(ULTRA_MYSTERY_BOX, 1, 1).broadcast(Broadcast.GLOBAL),
            new LootItem(COINS, 10_000_000, 1).broadcast(Broadcast.GLOBAL))
        .addTable("Uncommon", 490,
            new LootItem(SUPER_MYSTERY_BOX, 1, 1),
            new LootItem(LIMITED_MYSTERY_BOX, 1, 1),
            new LootItem(DRAGON_BOLTS, 500, 1),
            new LootItem(ONYX_BOLTS_E, 500, 1))
        .addTable("Common", 2326,
            new LootItem(ZULRAHS_SCALES, 50, 150, 1), // the upgrader's binding catalyst
            new LootItem(MASTER_CLUE, 1, 1),
            new LootItem(MYSTERY_BOX, 2, 1))
        .addTable("Nothing", 6478);

    private int attackCount = 0;
    private boolean enraged = false;
    private boolean prizeRainNext = false;
    private int keeperPhase = 0; // how many of the 75 / 50 / 25% calls have happened
    /** userId -> the tick it last hit Pandora (everyone in it qualifies for loot). */
    private final Map<Integer, Long> attackers = new HashMap<>();
    private final List<NPC> keepers = new ArrayList<>();

    @Override
    public void init() {
        npc.setIgnoreMulti(true);

        npc.hitListener = new HitListener().preDefend(hit -> {
            if (hit.attacker != null && hit.attacker.isPlayer())
                attackers.put(hit.attacker.player.getUserId(), io.ruin.Server.currentTick());
        }).preDamage(hit -> {
            // always praying protect from melee: melee does nothing, bring ranged or magic
            if (hit.attackStyle != null && hit.attackStyle.isMelee())
                hit.block();
        });

        npc.deathEndListener = (entity, killer, killHit) -> {
            for (NPC keeper : new ArrayList<>(keepers))
                if (!keeper.isRemoved()) {
                    keeper.graphics(CRYSTAL_BURST, 0, 0);
                    keeper.remove();
                }
            keepers.clear();
            rewardPlayers();
            VorathHandler.onDeath();
            npc.remove();
        };
    }

    /** It chases its target, but never further than this many tiles from its spawn point (the emblem's north end). */
    private static final int LEASH = 5;

    @Override
    public void follow() {
        Position home = npc.spawnPosition;
        if (target == null || home == null || withinDistance(1))
            return;
        // walk toward where it would stand next to the target, clamped to the leash box around home
        int half = npc.getSize() / 2;
        int wantX = Math.max(home.getX() - LEASH, Math.min(home.getX() + LEASH, target.getAbsX() - half));
        int wantY = Math.max(home.getY() - LEASH, Math.min(home.getY() + LEASH, target.getAbsY() - half));
        if (npc.getAbsX() != wantX || npc.getAbsY() != wantY)
            io.ruin.model.map.route.routes.DumbRoute.step(npc, wantX, wantY);
    }

    /** Players who hit Pandora recently and are still close by (with more than one, it fights them all). */
    private List<Player> activeAttackers() {
        long now = io.ruin.Server.currentTick();
        List<Player> list = new ArrayList<>();
        for (Player p : npc.localPlayers())
            if (!p.getCombat().isDead() && p.getPosition().isWithinDistance(npc.getPosition(), 16)) {
                Long last = attackers.get(p.getUserId());
                if (last != null && now - last <= ATTACKER_MEMORY)
                    list.add(p);
            }
        return list;
    }

    @Override
    public boolean attack() {
        if (!withinDistance(16))
            return false;

        attackCount++;

        if (!enraged && npc.getHp() < npc.getMaxHp() * 0.4) {
            enraged = true;
            npc.forceText("THE BOX WILL NOT CLOSE!");
            npc.graphics(CRYSTAL_BURST, 0, 0);
            for (Player p : World.players())
                p.sendMessage("<col=660000>[Pandora]</col> has burst open in a rage!");
        }

        checkKeepers();

        if (attackCount % SPECIAL_EVERY == 0) {
            if (prizeRainNext)
                prizeRain();
            else
                shadowSlam();
            prizeRainNext = !prizeRainNext;
            return true;
        }

        List<Player> targets = activeAttackers();
        if (targets.size() <= 1) {
            // one-on-one: punch / kick when adjacent (a crystal every 4th), crystals at range
            Player one = target != null ? target.player : null;
            if (one == null)
                return false;
            if (one.getPosition().isWithinDistance(npc.getPosition(), 2) && attackCount % 4 != 0)
                melee(List.of(one));
            else
                crystals(List.of(one));
            return true;
        }

        // several attackers: one swing / volley at all of them
        List<Player> close = new ArrayList<>(), far = new ArrayList<>();
        for (Player p : targets)
            (p.getPosition().isWithinDistance(npc.getPosition(), 2) ? close : far).add(p);
        if (!close.isEmpty())
            melee(close);
        if (!far.isEmpty())
            crystals(far);
        return true;
    }

    /** The protect-from-melee overhead goes on here: from init() it silently no-ops (no avatar yet). */
    private boolean iconApplied = false;

    @Override
    public void process() {
        if (!iconApplied) {
            npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromMelee);
            iconApplied = true;
        }
        // nobody to fight: drift back to its spot
        Position home = npc.spawnPosition;
        if (target == null && home != null && !npc.getCombat().isDead()
                && (npc.getAbsX() != home.getX() || npc.getAbsY() != home.getY()))
            io.ruin.model.map.route.routes.DumbRoute.step(npc, home.getX(), home.getY());
        if (++eyeTicks < EYE_EVERY || npc.getCombat().isDead())
            return;
        eyeTicks = 0;
        int half = npc.getSize() / 2;
        int cx = npc.getAbsX() + half, cy = npc.getAbsY() + half;
        int dx = 0, dy = -1; // idle: it faces south, toward the cave exit
        if (target != null) {
            dx = Integer.signum(target.getAbsX() - cx);
            dy = Integer.signum(target.getAbsY() - cy);
        }
        Position face = new Position(cx + dx * half, cy + dy * half, npc.getHeight());
        World.sendGraphics(EYE_SPARKLES[eyeColour++ % EYE_SPARKLES.length], EYE_HEIGHT, 0, face);
    }

    // ── Attacks ──────────────────────────────────────────────────────────────

    private void melee(List<Player> victims) {
        npc.animate(Random.rollDie(3) ? KICK : PUNCH);
        for (Player p : victims) {
            if (p.getCombat().isDead()) continue;
            p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(43));
            p.graphics(1430, 0, 0);
        }
    }

    /** Enigma Crystals: a spinning violet prism at each victim, shattering in a purple burst. */
    private void crystals(List<Player> victims) {
        npc.animate(CRYSTAL_TOSS);
        for (Player p : victims) {
            if (p.getCombat().isDead() || !ProjectileRoute.allow(npc, p)) continue;
            int duration = CRYSTAL_PROJECTILE.send(npc, p);
            int maxHit = enraged ? 35 : 26;
            if (!enraged && p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
                maxHit /= 2;
            Hit hit = new Hit(npc, AttackStyle.MAGIC).randDamage(maxHit).clientDelay(duration);
            hit.afterPostDamage(h -> p.graphics(CRYSTAL_BURST, 60, 0));
            p.hit(hit);
        }
    }

    /** Shadow Slam -- the two-handed slam, then a marked blast under everyone within 7 tiles 2 ticks later. */
    private void shadowSlam() {
        npc.animate(SLAM);
        npc.forceText("WHAT'S IN THE BOX?!");
        int slamMax = enraged ? 30 : 20;
        World.sendGraphics(SLAM_GFX, 92, 0, npc.getPosition());
        for (Player p : npc.localPlayers()) {
            if (!p.getPosition().isWithinDistance(npc.getPosition(), 7)) continue;
            Position marked = p.getPosition().copy();
            World.sendGraphics(WARNING_GFX, 0, 0, marked);
            npc.startEvent(e -> {
                e.delay(2);
                World.sendGraphics(SLAM_GFX, 0, 0, marked);
                if (!p.getCombat().isDead() && p.getPosition().equals(marked))
                    p.hit(new Hit(npc, AttackStyle.MAGIC).fixedDamage(Random.get(8, slamMax)).ignoreDefence().ignorePrayer());
            });
        }
    }

    /** Prize Rain: crystals fall on each player's tile and two beside it; whoever is on one when it lands is hit. */
    private void prizeRain() {
        npc.animate(SLAM);
        npc.forceText("A PRIZE FOR EVERYONE!");
        int max = enraged ? 28 : 18;
        List<Position> spots = new ArrayList<>();
        for (Player p : npc.localPlayers()) {
            if (!p.getPosition().isWithinDistance(npc.getPosition(), 12)) continue;
            spots.add(p.getPosition().copy());
            for (int i = 0; i < 2; i++)
                spots.add(p.getPosition().relative(Random.get(-2, 2), Random.get(-2, 2)));
        }
        for (Position spot : spots) {
            World.sendGraphics(WARNING_GFX, 0, 0, spot);
            PRIZE_FALL.send(spot.getX(), spot.getY() + 1, spot.getX(), spot.getY());
        }
        npc.startEvent(e -> {
            e.delay(3);
            for (Position spot : spots)
                World.sendGraphics(CRYSTAL_BURST, 0, 0, spot);
            for (Player p : npc.localPlayers())
                if (!p.getCombat().isDead() && spots.stream().anyMatch(s -> s.equals(p.getPosition())))
                    p.hit(new Hit(npc, AttackStyle.MAGIC).fixedDamage(Random.get(6, max)).ignoreDefence().ignorePrayer());
        });
    }

    // ── Chronos the Timekeeper ───────────────────────────────────────────────

    private void checkKeepers() {
        keepers.removeIf(NPC::isRemoved);
        double hp = npc.getHp() / (double) npc.getMaxHp();
        int phase = hp <= 0.25 ? 3 : hp <= 0.5 ? 2 : hp <= 0.75 ? 1 : 0;
        if (phase <= keeperPhase)
            return;
        keeperPhase = phase;
        if (keepers.size() >= MAX_KEEPERS)
            return;
        List<Player> targets = activeAttackers();
        if (targets.isEmpty())
            return;
        summonKeeper(Random.get(targets));
    }

    private void summonKeeper(Player victim) {
        Position at = null;
        for (int tries = 0; tries < 12 && at == null; tries++) {
            Position p = npc.getPosition().relative(Random.get(-3, npc.getSize() + 2), Random.get(-3, npc.getSize() + 2));
            Tile tile = Tile.get(p);
            if (tile != null && tile.clipping != 0)
                continue;
            if (p.getX() >= npc.getAbsX() && p.getX() < npc.getAbsX() + npc.getSize()
                    && p.getY() >= npc.getAbsY() && p.getY() < npc.getAbsY() + npc.getSize())
                continue; // not under Pandora
            at = p;
        }
        if (at == null)
            at = npc.getPosition().relative(-2, 0);
        npc.forceText("Chronos! Stop their clocks!");
        World.sendGraphics(CRYSTAL_BURST, 0, 0, at);
        NPC keeper = new NPC(CHRONOS).spawn(at);
        keeper.setIgnoreMulti(true);
        keeper.deathEndListener = (entity, killer, killHit) -> {
            keepers.remove(keeper);
            keeper.remove();
        };
        keepers.add(keeper);
        keeper.targetPlayer(victim, false);
        keeper.getCombat().setTarget(victim);
        for (Player p : npc.localPlayers())
            p.sendMessage(Color.PURPLE.wrap("Pandora calls Chronos the Timekeeper to its side!"));
        keeper.startEvent(e -> {
            int ticks = 0;
            while (!keeper.isRemoved() && !npc.getCombat().isDead()) {
                e.delay(1);
                ticks++;
                Player prey = keeper.getCombat().getTarget() != null ? keeper.getCombat().getTarget().player : null;
                if (prey == null || prey.getCombat().isDead() || !prey.getPosition().isWithinDistance(npc.getPosition(), 20)) {
                    List<Player> pool = activeAttackers();
                    if (pool.isEmpty())
                        continue;
                    prey = Random.get(pool);
                    keeper.targetPlayer(prey, false);
                    keeper.getCombat().setTarget(prey);
                }
                // Stop Time: every 10 ticks the keeper freezes its prey for 3 seconds
                if (ticks % 10 == 0 && prey.getPosition().isWithinDistance(keeper.getPosition(), 8)) {
                    keeper.forceText("Time... stands... still.");
                    prey.graphics(FREEZE_GFX, 0, 0);
                    prey.freeze(3, keeper);
                    prey.sendMessage(Color.PURPLE.wrap("Chronos stops your clock!"));
                }
            }
            if (!keeper.isRemoved())
                keeper.remove();
        });
    }

    // ── Rewards ──────────────────────────────────────────────────────────────

    private void rewardPlayers() {
        npc.getPosition().getRegion().players.forEach(p -> {
            if (!attackers.containsKey(p.getUserId())) return;
            p.getInventory().addOrDrop(MYSTERY_BOX, 1);
            int coins = Random.get(15_000, 30_000);
            p.getInventory().addOrDrop(COINS, coins);
            p.sendMessage(Color.RED.wrap("Pandora's chest bursts open: a Mystery box and " + String.format("%,d", coins) + " coins!"));
            List<Item> rolled = TABLE.rollItems(false, 0);
            if (rolled == null || rolled.isEmpty())
                return;
            Item bonus = rolled.get(0);
            p.getInventory().addOrDrop(bonus.getId(), bonus.getAmount());
            String what = (bonus.getAmount() > 1 ? String.format("%,d", bonus.getAmount()) + " x " : "") + bonus.getDef().name;
            p.sendMessage(Color.RED.wrap("You also received " + what + " from Pandora!"));
            if (bonus.lootBroadcast != null)
                Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP,
                    "<col=000000>" + p.getName(),
                    " just received " + bonus.getDef().descriptiveName + " from Pandora, the Enigma Keeper!");
        });
    }
}
