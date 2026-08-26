package io.ruin.model.activities.wilderness;

import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.Broadcast;

/**
 * WANTED System
 * ─────────────────────────────────────────────────────────────────────────────
 * Players can walk up to the BH Chest, choose "Place a WANTED bounty", type a
 * target name, type an amount of Blood Money, then confirm.
 *
 * The BM is deducted immediately and stored on the target.  When the target
 * dies in the Wilderness the killer receives all of it.
 *
 * Integration points
 *  • StaticInit    → WantedManager.register();
 *  • PlayerCombat  → WantedManager.handleDeath(victim, pKiller); (both death paths)
 *  • PlayerAttributes → int wantedBountyAmount, String wantedByName
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class WantedManager {

    /** Object ID of the BH Chest — defined in 1_patches/object/60010.toml. */
    private static final int BH_CHEST_ID = 60010;

    /** Blood Money item ID (13307). */
    private static final int BLOOD_MONEY = ItemID.BLOOD_MONEY;

    /** Smallest bounty a player may place. Keeps the system meaningful. */
    public static final int MIN_BOUNTY = 100;

    /**
     * Hard cap per placement.  Multiple players can stack their contributions
     * on the same target (amounts are added together), but no single placement
     * may exceed this value.
     */
    public static final int MAX_BOUNTY = 2_000_000;

    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called from StaticInit — nothing to register here since the ObjectAction is
     * attached to the spawned instance in HomeHandler.init() to bypass the LocType
     * lookup (custom object IDs above the base cache max are never loaded into LocType).
     */
    public static void register() {
        // Instance-based registration is handled in HomeHandler.init() via:
        //   ObjectAction.register(bhChest, 1, (player, obj) -> WantedManager.openMenu(player));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chest interaction → main menu
    // ─────────────────────────────────────────────────────────────────────────

    /** Entry point called from the chest's ObjectAction (registered on the instance in HomeHandler). */
    public static void openMenu(Player player) {
        openChestMenu(player);
    }

    private static void openChestMenu(Player player) {
        player.dialogue(
            new OptionsDialogue("Bounty Hunter Chest",
                new Option("Open BH Shop", () -> {
                    player.closeDialogue();
                    io.ruin.model.inter.handlers.shopinterface.CustomShop2.openBhPointsShop(player);
                }),
                new Option("Place a WANTED bounty", () -> {
                    player.closeDialogue();
                    promptTargetName(player);
                })
            )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 – prompt for target player name
    // ─────────────────────────────────────────────────────────────────────────

    private static void promptTargetName(Player player) {
        player.nameInput("Enter the name of the player you want to place a bounty on:", rawName -> {
            if (rawName == null || rawName.trim().isEmpty()) {
                player.sendMessage("You must enter a valid player name.");
                return;
            }

            String targetName = rawName.trim();

            // Cannot target yourself
            if (targetName.equalsIgnoreCase(player.getName())) {
                player.sendMessage("You cannot place a bounty on yourself.");
                return;
            }

            // Target must be online — resolve once so we hold the reference
            Player target = World.getPlayerByName(targetName).orElse(null);
            if (target == null) {
                player.sendMessage("'" + targetName + "' is not currently online.");
                return;
            }

            // Cannot target staff members (Moderator, Support, Admin, Owner)
            if (target.isStaff() || target.isAdmin() || target.isOwner()) {
                player.sendMessage("You cannot place a bounty on a staff member.");
                return;
            }

            promptBountyAmount(player, target);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 – prompt for BM amount
    // ─────────────────────────────────────────────────────────────────────────

    private static void promptBountyAmount(Player player, Player target) {
        int currentBm = getBloodMoney(player);
        player.integerInput(
            "How much Blood Money to place on <col=cc0000>" + target.getName() + "</col>'s head?<br>" +
            "Min: " + MIN_BOUNTY + "  |  Max: " + MAX_BOUNTY + "<br>" +
            "You have: " + currentBm + " BM in your inventory.",
            amount -> validateAndConfirm(player, target, amount)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 – validate then show confirmation dialogue
    // ─────────────────────────────────────────────────────────────────────────

    private static void validateAndConfirm(Player player, Player target, int amount) {
        // Amount sanity checks
        if (amount < MIN_BOUNTY) {
            player.sendMessage("The minimum bounty is " + MIN_BOUNTY + " Blood Money.");
            return;
        }
        if (amount > MAX_BOUNTY) {
            player.sendMessage("The maximum single bounty is " + MAX_BOUNTY + " Blood Money.");
            return;
        }

        // Still online? (player may have logged during the input prompt)
        if (World.getPlayerByName(target.getName()).isEmpty()) {
            player.sendMessage(target.getName() + " has logged out — bounty cancelled.");
            return;
        }

        // Enough BM in inventory?
        if (getBloodMoney(player) < amount) {
            player.sendMessage("You don't have " + amount + " Blood Money in your inventory.");
            return;
        }

        // Show existing bounty hint if one is already active on this target
        String existingNote = target.wantedBountyAmount > 0
            ? "<br><col=888888>(existing bounty: " + target.wantedBountyAmount + " BM — amounts will stack)</col>"
            : "";

        player.dialogue(
            new MessageDialogue(
                "Place a bounty of <col=cc0000>" + amount + " Blood Money</col> on " +
                "<col=cc0000>" + target.getName() + "</col>?" + existingNote +
                "<br>The BM is deducted now. The killer collects it all."
            ).lineHeight(20),
            new OptionsDialogue(
                new Option("Yes — place the bounty!", () -> applyBounty(player, target, amount)),
                new Option("No — cancel.", player::closeDialogue)
            )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Final application – deduct, store, broadcast
    // ─────────────────────────────────────────────────────────────────────────

    private static void applyBounty(Player player, Player target, int amount) {
        // Race-condition guard: re-validate everything at the moment of confirmation
        if (World.getPlayerByName(target.getName()).isEmpty()) {
            player.sendMessage(target.getName() + " has logged out. Bounty cancelled.");
            player.closeDialogue();
            return;
        }
        if (getBloodMoney(player) < amount) {
            player.sendMessage("You no longer have enough Blood Money.");
            player.closeDialogue();
            return;
        }

        // Deduct BM from the placer
        player.getInventory().remove(BLOOD_MONEY, amount);

        // Stack onto any existing bounty on this target
        target.wantedBountyAmount += amount;
        target.wantedByName      = player.getName();

        player.closeDialogue();

        // Feedback to both parties
        player.sendMessage(
            "<col=cc0000>[WANTED]</col> You placed a bounty of <col=ffd700>" + amount +
            " BM</col> on " + target.getName() + "."
        );
        target.sendMessage(
            "<col=cc0000>[WANTED]</col> A bounty of <col=ffd700>" + amount +
            " Blood Money</col> has been placed on your head by " + player.getName() + "!" +
            (target.wantedBountyAmount > amount
                ? " Total bounty: " + target.wantedBountyAmount + " BM."
                : "")
        );

        // World broadcast
        Broadcast.WORLD.sendNews(
            Icon.WILDERNESS,
            "WANTED",
            "<col=ffffff>" + player.getName() + " has placed a <col=cc0000>WANTED</col>" +
            " bounty of <col=ffd700>" + target.wantedBountyAmount + " BM</col>" +
            " on " + target.getName() + "!"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Death hook
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call this from {@code PlayerCombat} immediately after {@code killer.reward()} in
     * both PvP death paths (regular death and pvpPreset death).
     *
     * <pre>
     *     boolean bhTarget = player.getBountyHunter().deathByTarget(pKiller);
     *     if (pKiller != null) {
     *         killer.reward(player, bhTarget);
     *         WantedManager.handleDeath(player, pKiller);   // ← add this line
     *         ...
     *     }
     * </pre>
     *
     * @param victim the player who died
     * @param killer the player who dealt the killing blow (may be {@code null} for
     *               environment/NPC deaths — in that case the bounty is forfeited)
     */
    public static void handleDeath(Player victim, Player killer) {
        if (victim.wantedBountyAmount <= 0)
            return; // No active bounty — nothing to do

        int reward     = victim.wantedBountyAmount;
        String placedBy = victim.wantedByName != null && !victim.wantedByName.isEmpty()
                          ? victim.wantedByName : "someone";

        // Clear the bounty immediately so it cannot be double-awarded
        victim.wantedBountyAmount = 0;
        victim.wantedByName       = "";

        if (killer == null) {
            // Died to an NPC or environment — bounty is lost.
            // Swap to:  victim.getInventory().addOrDrop(BLOOD_MONEY, reward);
            // if you want to refund it to the victim instead.
            victim.sendMessage(
                "<col=cc0000>[WANTED]</col> The bounty of <col=ffd700>" + reward +
                " BM</col> on your head was lost as you died without a player killer."
            );
            return;
        }

        // Award the full bounty to the killer
        killer.getInventory().addOrDrop(BLOOD_MONEY, reward);

        killer.sendMessage(
            "<col=cc0000>[WANTED]</col> You claimed the WANTED bounty on " +
            victim.getName() + " and received <col=ffd700>" + reward + " Blood Money</col>!"
        );
        victim.sendMessage(
            "<col=cc0000>[WANTED]</col> Your bounty (placed by " + placedBy +
            ") was claimed by " + killer.getName() + "."
        );

        // World broadcast
        Broadcast.WORLD.sendNews(
            Icon.WILDERNESS,
            "WANTED",
            "<col=ffffff>" + killer.getName() + " claimed the <col=cc0000>WANTED</col>" +
            " bounty of <col=ffd700>" + reward + " BM</col>" +
            " on " + victim.getName() + "!"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns how much Blood Money the player currently has in their inventory. */
    private static int getBloodMoney(Player player) {
        Item bm = player.getInventory().findItem(BLOOD_MONEY);
        return bm == null ? 0 : bm.getAmount();
    }
}
