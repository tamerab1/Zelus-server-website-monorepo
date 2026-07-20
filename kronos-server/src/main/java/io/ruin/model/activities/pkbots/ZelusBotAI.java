package io.ruin.model.activities.pkbots;

import io.ruin.Server;
import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.WeaponType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.map.Position;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;

/**
 * ZelusBotAI — per-tick artificial-intelligence controller for a
 * {@link ZelusBotPlayer}.
 *
 * <h3>Behaviour summary</h3>
 * <ul>
 *   <li>IDLE — scans for the nearest real player within 15 tiles and engages.</li>
 *   <li>COMBAT — maintains adaptive overhead prayer, eats at HP thresholds,
 *       and delegates attacking to the engine's own {@link PlayerCombat} target system.</li>
 *   <li>On respawn — re-scans for targets.</li>
 * </ul>
 */
public class ZelusBotAI {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Range at which the bot will pick up new targets (tiles). */
    private static final int AGGRO_RANGE = 15;

    /** HP % at which the bot eats a "shark" (light heal). */
    private static final int EAT_THRESHOLD_LIGHT  = 70;
    /** HP % at which the bot eats a "brew" (medium heal). */
    private static final int EAT_THRESHOLD_MEDIUM = 50;
    /** HP % at which the bot uses a "karambwan" combo (large heal). */
    private static final int EAT_THRESHOLD_COMBO  = 30;

    private static final int HEAL_LIGHT  = 20;   // Shark
    private static final int HEAL_MEDIUM = 35;   // Brew
    private static final int HEAL_COMBO  = 9999; // Full restore (clamped by healHp)

    /** Minimum ticks between consecutive eats. */
    private static final int EAT_COOLDOWN_TICKS = 3;

    /** Ticks of prayer-switch reaction jitter (0 = immediate). */
    private static final int PRAYER_REACTION_TICKS = 1;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final ZelusBotPlayer bot;

    /** The player we're currently fighting. */
    private Player target;

    /** Last tick we ate food. */
    private long lastEatTick = -10;

    /** Whether we've eaten this tick already (prevents double-heal). */
    private boolean ateThisTick = false;

    /** Ticks until the queued overhead prayer is applied. */
    private int prayerReactionTimer = 0;

    /** The prayer we want to switch to once the reaction delay fires. */
    private Prayer desiredPrayer = null;

    /** Currently-active overhead protection prayer (null = none). */
    private Prayer activePrayer = null;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ZelusBotAI(ZelusBotPlayer bot) {
        this.bot = bot;
    }

    // -----------------------------------------------------------------------
    // Main tick entry point
    // -----------------------------------------------------------------------

    /**
     * Called every server tick by {@link ZelusBotManager#processBots()}.
     */
    public void process() {
        // Dead / locked — wait for respawn sequence to finish
        if (bot.getCombat().isDead()) {
            return;
        }

        ateThisTick = false;

        // ── Prayer-point restoration (simulate prayer pots) ────────────────
        restorePrayerIfNeeded();

        // ── Prayer reaction delay ──────────────────────────────────────────
        if (prayerReactionTimer > 0) {
            if (--prayerReactionTimer == 0 && desiredPrayer != null) {
                applyPrayer(desiredPrayer);
                desiredPrayer = null;
            }
        }

        // ── Target validation ──────────────────────────────────────────────
        if (target != null) {
            if (!isValidTarget(target)) {
                abandonTarget();
            }
        }

        // ── No target — scan for one ───────────────────────────────────────
        if (target == null) {
            target = findNearestPlayer();
            if (target != null) {
                engageTarget(target);
            }
            return; // Wait until next tick so combat setup completes
        }

        // ── In combat ─────────────────────────────────────────────────────
        processEating();
        processAdaptivePrayer(target);

        // Ensure the engine's combat is still targeting the right player
        if (bot.getCombat().getTarget() != target) {
            bot.getCombat().setTarget(target);
        }
    }

    // -----------------------------------------------------------------------
    // Respawn hook
    // -----------------------------------------------------------------------

    /**
     * Called by {@link ZelusBotPlayer#handleBotDeath()} after respawn —
     * resets AI state so the bot picks a fresh target next tick.
     */
    public void onRespawn() {
        target        = null;
        lastEatTick   = -10;
        ateThisTick   = false;
        activePrayer  = null;
        desiredPrayer = null;
        prayerReactionTimer = 0;
        bot.getPrayer().deactivateAll();
    }

    // -----------------------------------------------------------------------
    // Target scanning
    // -----------------------------------------------------------------------

    private Player findNearestPlayer() {
        Player nearest      = null;
        int    nearestDist  = Integer.MAX_VALUE;
        Position botPos     = bot.getPosition();

        for (Player p : io.ruin.model.World.players()) {
            // Skip self, other bots, and dead/dying/logged-out players
            if (p == bot) continue;
            if (p instanceof ZelusBotPlayer) continue;
            if (p.getCombat().isDead()) continue;
            if (!p.isOnline()) continue;

            int dist = manhattanDist(botPos, p.getPosition());
            if (dist <= AGGRO_RANGE && dist < nearestDist) {
                nearestDist = dist;
                nearest     = p;
            }
        }
        return nearest;
    }

    private boolean isValidTarget(Player player) {
        if (player == null) return false;
        if (player.getCombat().isDead()) return false;
        if (!player.isOnline()) return false;
        if (player.getPosition().getZ() != bot.getPosition().getZ()) return false;
        return manhattanDist(bot.getPosition(), player.getPosition()) <= 25;
    }

    private void engageTarget(Player player) {
        this.target = player;
        bot.getCombat().setTarget(player);
        // Immediately queue the right overhead prayer (with 0-1t reaction)
        scheduleAdaptivePrayer(player);
    }

    private void abandonTarget() {
        bot.getCombat().reset();
        bot.getPrayer().deactivateProtectionPrayer();
        activePrayer = null;
        target       = null;
    }

    // -----------------------------------------------------------------------
    // Adaptive overhead prayer
    // -----------------------------------------------------------------------

    private void processAdaptivePrayer(Player player) {
        Prayer ideal = detectIdealPrayer(player);
        if (ideal == desiredPrayer) return;      // already queued
        if (ideal == activePrayer)  return;      // already active

        desiredPrayer        = ideal;
        // 0–1 tick reaction jitter to simulate human-like response
        prayerReactionTimer  = PRAYER_REACTION_TICKS + Random.get(0, 1);
    }

    private void scheduleAdaptivePrayer(Player player) {
        desiredPrayer       = detectIdealPrayer(player);
        prayerReactionTimer = Random.get(0, 1);
    }

    private Prayer detectIdealPrayer(Player player) {
        PlayerCombat pc = player.getCombat();
        if (pc == null) return Prayer.PROTECT_FROM_MELEE;

        AttackStyle style = pc.getAttackStyle();
        WeaponType  wt    = pc.weaponType;

        if (style != null) {
            if (style.isRanged() || style.isMagicalRanged()) {
                return Prayer.PROTECT_FROM_MISSILES;
            }
            if (style.isMagic() || style.isMagical()) {
                return Prayer.PROTECT_FROM_MAGIC;
            }
        }

        // Fallback: ranged weapons have max distance > 1
        if (wt != null && wt.maxDistance > 1) {
            return Prayer.PROTECT_FROM_MISSILES;
        }

        return Prayer.PROTECT_FROM_MELEE;
    }

    /**
     * Activates {@code prayer}, deactivating any conflicting overhead prayer first.
     */
    private void applyPrayer(Prayer prayer) {
        if (activePrayer != null && activePrayer != prayer) {
            bot.getPrayer().deactivate(activePrayer);
        }
        if (!bot.getPrayer().isActive(prayer)) {
            bot.getPrayer().toggle(prayer);
        }
        activePrayer = prayer;
    }

    // -----------------------------------------------------------------------
    // Eating / HP management
    // -----------------------------------------------------------------------

    private void processEating() {
        long currentTick = Server.currentTick();
        if (currentTick - lastEatTick < EAT_COOLDOWN_TICKS) return;

        int hpPct = bot.getHpPercent();

        if (hpPct <= EAT_THRESHOLD_COMBO && !ateThisTick) {
            bot.healHp(HEAL_COMBO);
            triggerEatAnimation();
            lastEatTick = currentTick;
            ateThisTick = true;
        } else if (hpPct <= EAT_THRESHOLD_MEDIUM && !ateThisTick) {
            bot.healHp(HEAL_MEDIUM);
            triggerEatAnimation();
            lastEatTick = currentTick;
            ateThisTick = true;
        } else if (hpPct <= EAT_THRESHOLD_LIGHT && !ateThisTick) {
            bot.healHp(HEAL_LIGHT);
            triggerEatAnimation();
            lastEatTick = currentTick;
            ateThisTick = true;
        }
    }

    private void triggerEatAnimation() {
        bot.animate(829); // standard eating animation (visible to nearby clients)
    }

    /**
     * If prayer drops below 30% restore to full (simulates drinking prayer pots).
     * This keeps overhead prayers active without requiring real inventory management.
     */
    private void restorePrayerIfNeeded() {
        Stat prayerStat = bot.stats.get(StatType.Prayer);
        if (prayerStat.currentLevel < prayerStat.fixedLevel * 0.30) {
            prayerStat.alter(prayerStat.fixedLevel); // full restore
            prayerStat.updated = true;
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static int manhattanDist(Position a, Position b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
