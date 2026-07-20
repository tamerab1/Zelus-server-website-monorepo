package io.ruin.model.activities.pkbots;

import io.ruin.api.protocol.PlatformInfo;
import io.ruin.api.protocol.login.LoginInfo;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.DeathListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.stat.StatType;
import io.ruin.rsprot.RSProtService;
import net.rsprot.protocol.common.client.OldSchoolClientType;
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerInfo;
import player.attributes.PlayerAttributeCodec;
import player.attributes.PlayerAttributesRegistry;

/**
 * ZelusBotPlayer — a fake player entity that appears as a real player to
 * connected clients but has no actual network session.
 *
 * <p>Bot players are added to the world's player list via
 * {@link World#addPlayer(LoginInfo, Player, String)} and are processed by
 * {@link ZelusBotAI} every tick.</p>
 *
 * <p>All packet-send operations on the underlying {@link io.ruin.network.PacketSender}
 * are no-ops because {@code rsprotSession} is never set, so the bot never
 * crashes from sending outgoing messages.</p>
 */
public class ZelusBotPlayer extends Player {

    /** True for every instance of this class — used for fast bot checks. */
    public final boolean isBot = true;

    /** The position the bot will respawn at after death. */
    private final Position spawnPosition;

    /** The AI controller driving this bot's behaviour. */
    public final ZelusBotAI ai;

    /** Owner userId — used to track which player spawned this bot. */
    public int ownerUserId = -1;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates and registers a new bot player at the given position.
     *
     * @param username      the display name shown to other players
     * @param spawnPosition the tile where the bot spawns (and respawns)
     */
    public ZelusBotPlayer(String username, Position spawnPosition) {
        super();
        this.spawnPosition = spawnPosition;
        this.ai = new ZelusBotAI(this);

        // Build a fake LoginInfo — no DB record, just in-memory
        LoginInfo fakeInfo = new LoginInfo(
                0L,
                "0.0.0.0",
                username,
                "",        // no password
                "",        // no email
                (PlatformInfo) null,
                "bot-" + username,   // uuid (unique per bot)
                "bot-hwid",
                0, false, 0
        );

        // Register with the world (assigns a player-slot index)
        int slot = World.addPlayer(fakeInfo, this, username);
        if (slot < 0) {
            throw new IllegalStateException("[ZelusBotPlayer] No free player slot for bot " + username);
        }

        // Load fresh default attributes (no DB file exists, so all defaults)
        PlayerAttributesRegistry.load(slot, new PlayerAttributeCodec.LoadContext("bot-" + username));

        // Set max combat stats and equip default gear
        setMaxStats();
        equipDefaultGear();

        // Allocate RSProt player info so the bot appears to other clients.
        // rsprotSession is left null — playersWriteUpdate skips bots with no session.
        this.rsprotPlayerInfo = RSProtService.service()
                .getPlayerInfoProtocol()
                .alloc(slot, OldSchoolClientType.DESKTOP);
        // Also allocate NPC info so the bot's NPC updater doesn't crash
        this.rsprotNPCInfo = RSProtService.service()
                .getNpcInfoProtocol()
                .alloc(slot, OldSchoolClientType.DESKTOP);
        // Mark as "visible" (online=true so RSProt processes this player's coords)
        this.online   = true;
        // Allow Player.process() to run each tick (normally set by Player.start())
        this.started  = true;

        // Teleport to spawn location
        getMovement().teleport(spawnPosition);

        // Minimal combat start (sets combat level, no packets)
        getCombat().start();

        // Install a custom death handler so the bot respawns instead of
        // going through the normal player-death item-drop flow
        this.deathEndListener = (DeathListener.Simple) this::handleBotDeath;
    }

    // -----------------------------------------------------------------------
    // Online / logout overrides
    // -----------------------------------------------------------------------

    /**
     * Bot players are "online" whenever their player info is allocated
     * (so they appear to nearby clients), regardless of having a real session.
     */
    @Override
    public boolean isOnline() {
        return rsprotPlayerInfo != null && online;
    }

    /**
     * Bots never time out or log out — suppress the normal session-based
     * logout machinery entirely.
     */
    @Override
    public void checkLogout() {
        // no-op: bots are removed only through ZelusBotManager.despawnForPlayer()
    }

    // -----------------------------------------------------------------------
    // Stat / gear helpers
    // -----------------------------------------------------------------------

    /** Sets all combat-relevant stats to level 99. */
    public void setMaxStats() {
        stats.set(StatType.Attack,    99);
        stats.set(StatType.Strength,  99);
        stats.set(StatType.Defence,   99);
        stats.set(StatType.Hitpoints, 99);
        stats.set(StatType.Ranged,    99);
        stats.set(StatType.Magic,     99);
        stats.set(StatType.Prayer,    99);
    }

    /**
     * Equips the standard Abyssal-whip PK set.
     * Override in a subclass or call individual setters to change gear.
     */
    public void equipDefaultGear() {
        equipment.set(Equipment.SLOT_HAT,    new Item(10828, 1));  // Neitiznot helm
        equipment.set(Equipment.SLOT_CAPE,   new Item(6570,  1));  // Fire cape
        equipment.set(Equipment.SLOT_AMULET, new Item(6585,  1));  // Amulet of fury
        equipment.set(Equipment.SLOT_WEAPON, new Item(4151,  1));  // Abyssal whip
        equipment.set(Equipment.SLOT_CHEST,  new Item(11832, 1));  // Bandos chestplate
        equipment.set(Equipment.SLOT_SHIELD, new Item(8844,  1));  // Dragon defender
        equipment.set(Equipment.SLOT_LEGS,   new Item(11834, 1));  // Bandos tassets
        equipment.set(Equipment.SLOT_HANDS,  new Item(7462,  1));  // Barrows gloves
        equipment.set(Equipment.SLOT_FEET,   new Item(4127,  1));  // Dragon boots
        equipment.set(Equipment.SLOT_RING,   new Item(6737,  1));  // Berserker ring
        getCombat().updateWeapon(false, false);
    }

    // -----------------------------------------------------------------------
    // HP helpers
    // -----------------------------------------------------------------------

    /**
     * Returns current HP as a percentage of max HP (0–100).
     */
    public int getHpPercent() {
        int max = stats.get(StatType.Hitpoints).fixedLevel;
        return max > 0 ? (getHp() * 100) / max : 0;
    }

    /**
     * Directly restores HP (clamped to max). Used by the eating simulation.
     *
     * @param amount HP to restore
     */
    public void healHp(int amount) {
        int max = stats.get(StatType.Hitpoints).fixedLevel;
        int current = getHp();
        int healed = Math.min(current + amount, max);
        setHp(healed);
    }

    // -----------------------------------------------------------------------
    // Death / respawn
    // -----------------------------------------------------------------------

    /** Called instead of normal player death — respawns the bot cleanly. */
    private void handleBotDeath() {
        // Clear "dead" flag — deathEndListener bypasses the normal cleanup that does this
        getCombat().setDead(false);
        // Restore full stats (HP, prayer, etc.)
        setMaxStats();
        stats.restore(true);       // resets any drained/boosted levels
        // Deactivate all prayers (prayer drain etc.)
        getPrayer().deactivateAll();
        // Teleport to spawn tile
        getMovement().teleport(spawnPosition);
        // Re-equip gear (death sequence may have cleared equipment)
        equipDefaultGear();
        // Reset target, special energy, etc.
        getCombat().reset();
        getCombat().restoreSpecial(100);
        // Unlock movement/actions (death locked the player)
        unlock();
        // Notify AI so it scans for a fresh target next tick
        ai.onRespawn();
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Position getSpawnPosition() {
        return spawnPosition;
    }
}
