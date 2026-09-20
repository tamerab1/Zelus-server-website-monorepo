package io.ruin.model.map.ground;

import discord.webhooks.logs.GroundItemHook;
import io.ruin.HooksV2;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.services.Loggers;
import io.ruin.utility.Utils;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

import core.task.Continuations;
import org.json.JSONObject;

import static core.task.api.API.*;

import static io.ruin.model.item.actions.impl.storage.LootingBag.CLOSED_LOOTING_BAG;
import static io.ruin.model.item.actions.impl.storage.LootingBag.OPENED_LOOTING_BAG;

@ToString
@Slf4j
public class GroundItem extends Position {
	public static interface Hook {
		record Pickup(Player player, GroundItem item) implements Hook {
		};

		/**
		 * Fired before a ground item is placed into the world, right after the
		 * built-in phantom-item guard. A listener that wants to silently swallow
		 * the spawn (e.g. an economy-protection module vetoing spawnable/dissolvable
		 * items) should return {@link io.ruin.HooksV2.Result#Return}.
		 */
		record PreSpawn(GroundItem item) implements Hook {
		};
	}

	public static HooksV2<Hook> hooks = new HooksV2<>(Hook.class);

	// Classic OldSchool ObjDel packets bitwise-and the item id down to 15 bits client-side (max
	// 32767, see net/rsprot/protocol/game/outgoing/zone/payload/ObjDel.kt's own doc comment), so
	// any ground item with id >= 32768 (every custom item added to this server) gets silently
	// orphaned on pickup -- the removal packet's id gets masked to an unrelated value the client
	// never matches, leaving the visual mesh stuck on the ground forever. Confirmed 2026-09-15 via
	// live DIAG-GROUND logging (server-side removal proven correct) plus a vanilla-item A/B test
	// (unaffected, since real item ids are almost all under 32768). ObjAdd/ObjCount have no such
	// restriction, but empirically an ObjCount-to-zero fallback did NOT hide the ghost either.
	// Fix: every affected custom item has a dedicated "ground display proxy" -- a minimal,
	// never-granted item at a real, safe low id (<32768) sharing the exact same inventoryModel --
	// and every ground-item network packet (add/update/remove) uses that proxy's id instead of
	// the real one. All server-side logic (pickup, hooks, inventory crediting) keeps using the
	// real `id` field unchanged; only what's sent over the wire is remapped.
	private static final java.util.Map<Integer, Integer> GROUND_DISPLAY_PROXY_ID = java.util.Map.ofEntries(
			java.util.Map.entry(60256, 31132), // Aetherial Scythe
			java.util.Map.entry(60257, 31133), // Angelic Greatsword
			java.util.Map.entry(60258, 31134), // Nether Defender
			java.util.Map.entry(60259, 31135), // Ward of the Depths
			java.util.Map.entry(60260, 31136), // Owner Cape
			java.util.Map.entry(60261, 31137), // Owner Wings
			java.util.Map.entry(60262, 31138), // Souly Wings
			java.util.Map.entry(60263, 31139), // Zealot's Horned Visage
			java.util.Map.entry(60264, 31140), // Zealot's Warplate
			java.util.Map.entry(60265, 31141), // Zealot's Greaves
			java.util.Map.entry(60266, 31142), // Zealot's Gloves
			java.util.Map.entry(60267, 31143), // Zealot's Striders
			java.util.Map.entry(60268, 31144), // Ancient Cleaver
			java.util.Map.entry(60269, 31145), // Ancient Signet
			java.util.Map.entry(60270, 31146), // Warlord's Dreadcloak
			java.util.Map.entry(60271, 31147), // Zarosian Dreadhelm
			java.util.Map.entry(60272, 31148), // Zarosian Platelegs
			java.util.Map.entry(60273, 31149), // Zarosian Sabatons
			java.util.Map.entry(60274, 31150), // Zarosian Warplate
			java.util.Map.entry(60275, 31151), // Draconic Hornbow
			java.util.Map.entry(60276, 31152), // Brimstone Hornbow
			java.util.Map.entry(60277, 31153), // Custom Bond #1
			java.util.Map.entry(60278, 31154), // Custom Bond #2
			java.util.Map.entry(60279, 31155), // Custom Bond #3
			java.util.Map.entry(60280, 31156), // Custom Bond #4
			java.util.Map.entry(60281, 31157), // Custom Bond #5
			java.util.Map.entry(60282, 31158), // Custom Bond #6
			java.util.Map.entry(59531, 31159), // AsVal Gun
			java.util.Map.entry(59548, 31160), // Icenier Sword
			java.util.Map.entry(59604, 31161), // Imperial staff
			java.util.Map.entry(59605, 31162), // Imperial bow
			java.util.Map.entry(59606, 31163), // Imperial top
			java.util.Map.entry(59609, 31164), // Crimson hat
			java.util.Map.entry(59626, 31165), // Nightfall bow
			java.util.Map.entry(34027, 31166), // Hallowfell
			java.util.Map.entry(34030, 31167), // Jar of light
			java.util.Map.entry(34032, 31168), // Sunstone crystal
			java.util.Map.entry(34033, 31169), // Ardeaglais teleport
			java.util.Map.entry(59603, 31170), // Nox
			java.util.Map.entry(60236, 31171), // Teumo
			java.util.Map.entry(60237, 31172), // Orrvor quo Maten
			java.util.Map.entry(60238, 31173), // Lord Trobin
			java.util.Map.entry(60239, 31174), // Wandering impling
			java.util.Map.entry(60240, 31175), // Wagchin
			java.util.Map.entry(60241, 31176), // The Monkey's Aunt
			java.util.Map.entry(60242, 31177), // Tempoross
			java.util.Map.entry(60243, 31178), // Strisath
			java.util.Map.entry(60244, 31179), // Starlight
			java.util.Map.entry(60245, 31180), // Star Sprite
			java.util.Map.entry(60246, 31181), // Spooky dog
			java.util.Map.entry(60247, 31182), // Spit goblin
			java.util.Map.entry(60248, 31183), // Shadow Keeper
			java.util.Map.entry(60249, 31184), // Slimetoes
			java.util.Map.entry(60250, 31185), // SteelWill
			java.util.Map.entry(60251, 31186), // Mossfists
			java.util.Map.entry(60252, 31187), // Damo
			java.util.Map.entry(60253, 31188), // Sliske
			java.util.Map.entry(60254, 31189), // Light Leech
			java.util.Map.entry(60255, 31190), // ToxiSkele
			java.util.Map.entry(34042, 31191), // Aggy
			java.util.Map.entry(60330, 31192), // Sylvaroth's Sprout (pet)
			java.util.Map.entry(60331, 31193), // Draconic Cape
			java.util.Map.entry(60332, 31194), // Draconic Godsword
			java.util.Map.entry(60333, 31195), // Draconic Helmet
			java.util.Map.entry(60334, 31196), // Draconic Longsword
			java.util.Map.entry(60335, 31197), // Draconic Platebody
			java.util.Map.entry(60336, 31198), // Draconic Platelegs
			java.util.Map.entry(60337, 31199), // Draconic Wings
			java.util.Map.entry(60338, 31200), // Draconies Hood
			java.util.Map.entry(60339, 31201), // Draconies Platebody
			java.util.Map.entry(60340, 31202), // Draconies Platelegs
			java.util.Map.entry(60341, 31203), // Draconies Warhammer
			java.util.Map.entry(60342, 31204)  // Draconies Wings
	);

	/** The id to use for ground-item network packets -- see GROUND_DISPLAY_PROXY_ID above. */
	public int displayId() {
		if (id < 32768) {
			return id;
		}
		Integer proxy = GROUND_DISPLAY_PROXY_ID.get(id);
		return proxy != null ? proxy : id;
	}

	public String originalOwner;
	public String activeOwner;
	public String diedToIron;
	public int id;
	public int amount;
	public Tile tile;
	// The client only ever renders one ground-item "slot" per (displayId, tile, owner) -- ObjDel
	// matches purely by (id, quantity, coord), with no per-instance token, so a second sendAdd()
	// for an id already visible on this tile is indistinguishable from the first once either is
	// picked up: picking up one silently makes the other's visual vanish too, even though it's
	// still a live object server-side (root cause of the stacked-same-tile item-loss report,
	// 2026-09-17). Tile now only sends ObjAdd for the first copy and queues extras invisibly,
	// revealing the next one as each visible copy is picked up or despawns -- see
	// Tile.addOrQueueVisibility/revealNextHidden. This flag tracks whether that has happened yet.
	public boolean visible = false;
	private int respawnMinutes;
	private long timeDropped;
	private String dropperName, dropperIp;
	private Map<String, String> attributes;

	public GroundItem(Item item) {
		this(item.getId(), item.getAmount(), item.copyOfAttributes());
	}

	public GroundItem(int id, int amount) {
		this(id, amount, null);
	}

	public GroundItem(int id, int amount, Map<String, String> attributes) {
		super(0, 0, 0);
		this.id = id;
		this.amount = amount;
		this.attributes = attributes;
	}

	public GroundItem owner(Player player) {
		return owner(player.getName());
	}

	public GroundItem owner(String ownerId) {
		this.originalOwner = ownerId;
		this.activeOwner = ownerId;
		return this;
	}

	public GroundItem diedToIron(Player player) {
		return diedToIron(player.getName());
	}

	public GroundItem diedToIron(String ownerId) {
		this.diedToIron = ownerId;
		return this;
	}

	public GroundItem position(Position pos) {
		return position(pos.getX(), pos.getY(), pos.getZ());
	}

	public GroundItem position(int x, int y, int z) {
		this.set(x, y, z);
		return this;
	}

	public boolean droppedByIronPlayer(Player player) {
		if (originalOwner != null && !originalOwner.isEmpty() && !originalOwner.equalsIgnoreCase(player.getName())) {
			return false;
		}
		return true;
	}

	/**
	 * Spawning - goes global after 60 seconds
	 */

	public GroundItem spawn() {
		return spawn(1);
	}

	/**
	 * wont appear globally, but will despawn as normal after eclapsed
	 * {@link #getDespawnTime()}
	 *
	 * @return
	 */
	public GroundItem spawnPrivate() {
		return spawn(-1);
	}

	public GroundItem spawn(int appearMinutes) {
		// Phantom rental items must never land on the ground
		if (isPhantom()) return this;
		// Economy-protection: semi-spawnable/dissolvable items never land on the ground
		if (hooks.handle(new Hook.PreSpawn(this))) return this;
		Tile.get(getX(), getY(), getZ(), true).addItem(this);
		if (appearMinutes != 0) {
			boolean allowAppear = appearMinutes > 0 && activeOwner != null && !activeOwner.isEmpty()
					&& ObjType.get(id).tradeable;

			queue(() -> {
				sleep((int) ((Math.abs(appearMinutes) * 60000L) / 600));
				if (allowAppear) {
					Continuations.schedule(this::appear);
				}
				sleep((int) ((getDespawnTime() * 60000L) / 600));
				queue(this::disappear);
			});
		}
		return this;
	}

	public GroundItem spawnPublic() {
		// Phantom rental items must never land on the ground
		if (isPhantom()) return this;
		// Economy-protection: semi-spawnable/dissolvable items never land on the ground
		if (hooks.handle(new Hook.PreSpawn(this))) return this;
		Tile.get(getX(), getY(), getZ(), true).addItem(this);
		queue(() -> {
			queue(this::appear);
			sleep((int) ((getDespawnTime() * 60000L) / 600));
			queue(this::disappear);
		});
		return this;
	}

	/** Returns true if this ground item carries the phantom rental attribute. */
	private boolean isPhantom() {
		return attributes != null && "1".equals(attributes.get("phantom"));
	}

	public GroundItem spawnWithRespawn(int respawnMinutes) {
		this.respawnMinutes = respawnMinutes;
		return spawn(0);
	}

	/**
	 * Appear
	 */

	private void appear() {
		if (isRemoved()) {
			/* this is possible because the task never gets stopped! */
			return;
		}
		if (!visible) {
			// Still queued behind another copy on this tile -- nothing to re-send yet, it was
			// never shown. Just flip ownership; it'll go out with the correct (now-public) owner
			// whenever it's actually revealed.
			activeOwner = null;
			return;
		}
		sendRemove();
		activeOwner = null;
		sendAdd();
	}

	/**
	 * Disappear
	 */

	private void disappear() {
		if (isRemoved()) {
			/* this is possible because the task never gets stopped! */
			return;
		}
		remove();
	}

	private int getDespawnTime() {
		return Tile.get(getX(), getY(), getZ(), true).region.dynamicData != null ? 15 : 1; // ORIGINAL: 60 : 2
	}

	/**
	 * Remove
	 */

	public void remove() {
		// Warning: This MAY null if isRemoved isn't checked first!
		if (tile == null)
			throw new RuntimeException(
					"gitem null tile! " + this + " " + getClass().getName() + '@' + Integer.toHexString(hashCode()));
		tile.removeItem(this);
	}

	public boolean isRemoved() {
		return tile == null;
	}

	/**
	 * Pickup
	 */

	public void pickup(Player player, int distance) {
		if (isRemoved()) {
			player.sendMessage("Can't pick up item not spawned for you.");
			return;
		}

		if (activeOwner != null && !activeOwner.isEmpty() && !activeOwner.equalsIgnoreCase(player.getName())) {
			player.sendMessage("Can't pick up item not spawned for you.");
			return;
		}

		if (diedToIron != null && !diedToIron.isEmpty() && !diedToIron.equalsIgnoreCase(player.getName())) {
			return;
		}

		if (hooks.handle(new Hook.Pickup(player, this))) {
			return;
		}

		if (player.getGameMode().isIronMan()
				&& originalOwner != null
				&& !originalOwner.isEmpty()
				&& !originalOwner.equalsIgnoreCase(player.getName())) {
			player.sendMessage("Ironmen cannot pick up items dropped by or for other players.");
			return;
		}

		if (player.getDuel().stage >= 4) {
			player.sendMessage("You can't pickup items in a duel.");
			return;
		}

		if (player.joinedTournament) {
			player.sendMessage("You can't pickup items while you're signed up for a tournament.");
			return;
		}

		boolean hasLootingBag = player.getBank().hasId(CLOSED_LOOTING_BAG) || player.getInventory().hasId(CLOSED_LOOTING_BAG)
				|| player.getInventory().hasId(OPENED_LOOTING_BAG) || player.getBank().hasId(OPENED_LOOTING_BAG);
		boolean isLootingBag = id == CLOSED_LOOTING_BAG || id == OPENED_LOOTING_BAG;

		if (hasLootingBag && isLootingBag) {
			player.sendMessage("You already have a looting bag!");
			return;
		}
		// DTO for pickup logs
		var object = new JSONObject();
			object.put("player", player.getName());
			object.put("item_name", ObjType.get(id).name);
			object.put("item_amount", NumberUtils.formatNumber(amount));
			object.put("item_noted", ObjType.get(id).isNote());
			object.put("pickup_x", getX());
			object.put("pickup_y", getY());
			object.put("pickup_z", getZ());

		// looting bag logic
		if (player.getInventory().hasId(OPENED_LOOTING_BAG)) {
			if (!player.getLootingBag().isFull()) {
				if (player.wildernessLevel > 0) {

					if (player.getLootingBag().add(id, amount, attributes) == 0) {
						player.sendMessage("Not enough space in your looting bag, added item to your inventory instead.");
						player.getInventory().addOrDrop(id, amount, attributes);
						remove();
						// These logs are ONLY triggered if the item was NOT added to the bag
						Loggers.logPickup(player.getUserId(), player.getName(), player.getIp(), id, amount, getX(), getY(), getZ());
						if (getTimeDropped() > 0) { // this item was manually dropped by someone, log as trade
							Loggers.logDropTrade(player.getName(), originalOwner, player.getIp(), getDropperIp(), player.getName(),
								getDropperName(), id, amount, getX(), getY(), getZ(), getTimeDropped());
//							RareDropEmbedMessage.sendPickupLogsToDiscord(player, new Item(id, amount), true);
							GroundItemHook.sendPickupLogsToDiscord(object, true);
						}
						return;
					}
					else {
						log.debug("Added {} x {} to looting bag ", Utils.formatMoneyString(amount), ObjType.get(id).name);
						// Added Pickup log here
						Loggers.logPickup(player.getUserId(), player.getName(), player.getIp(), id, amount, getX(), getY(), getZ());
						if (getTimeDropped() > 0) { // someone manually dropped this item, log as trade
							Loggers.logDropTrade(player.getName(), originalOwner, player.getIp(), getDropperIp(), player.getName(),
								getDropperName(), id, amount, getX(), getY(), getZ(), getTimeDropped());
//							RareDropEmbedMessage.sendPickupLogsToDiscord(player, new Item(id, amount), true);
							GroundItemHook.sendPickupLogsToDiscord(object, true);
						}
					}
				}
				else {
					log.debug("{} x {} was not added as player is not in wildy ", Utils.formatMoneyString(amount), ObjType.get(id).name);
					player.getInventory().addOrDrop(id, amount, attributes);
				}
			}
			else {
				log.debug("{} x {} was not added as player loot bag is full ", Utils.formatMoneyString(amount), ObjType.get(id).name);
				player.getInventory().addOrDrop(id, amount, attributes);
			}
		}
		else if (player.getInventory().add(id, amount, attributes) == 0) {
			player.sendMessage("Not enough space in your inventory.");
			return;
		}
		// Remove from list + send ObjDel directly to picker + all visible players.
		// pickupRemoveItem guarantees the picker always receives ObjDel even if
		// they are not found in tile.region.players at this exact moment.
		if (tile != null) {
			tile.pickupRemoveItem(this, player);
		}
		if (distance > 0)
			player.animate(832);
		player.privateSound(2582);
		if (respawnMinutes > 0) {
			queue(() -> {
				sleep((int) ((respawnMinutes * 60000L) / 600));
				queue(() -> this.spawnWithRespawn(respawnMinutes));
			});
		}
		Loggers.logPickup(player.getUserId(), player.getName(), player.getIp(), id, amount, getX(), getY(), getZ());
		if (getTimeDropped() > 0) { // this item was manually dropped by someone, log as trade
			Loggers.logDropTrade(player.getName(), originalOwner, player.getIp(), getDropperIp(), player.getName(),
					getDropperName(), id, amount, getX(), getY(), getZ(), getTimeDropped());
//			RareDropEmbedMessage.sendPickupLogsToDiscord(player, new Item(id, amount), true);
			GroundItemHook.sendPickupLogsToDiscord(object, true);
		}
	}

	/**
	 * Sending
	 */

	public void sendAdd() {
		visible = true;
		for (Player player : tile.region.players) {
			if (activeOwner == null || activeOwner.isEmpty()) {
				player.getPacketSender().sendGroundItem(this);
				continue;
			}
			if (activeOwner.equalsIgnoreCase(player.getName())) {
				player.getPacketSender().sendGroundItem(this);
				return;
			}
		}
	}

	public void sendAdd(Player player) {
		player.getPacketSender().sendGroundItem(this);
	}

	public void sendRemove(Player player) {
		player.getPacketSender().sendRemoveGroundItem(this);
	}

	public void sendRemove() {
		for (Player player : tile.region.players) {
			if (activeOwner == null || activeOwner.isEmpty()) {
				player.getPacketSender().sendRemoveGroundItem(this);
				continue;
			}
			if (activeOwner.equalsIgnoreCase(player.getName())) {
				player.getPacketSender().sendRemoveGroundItem(this);
				return;
			}
		}
	}

	public void sendUpdate(int previousAmount) {
		if (tile == null || tile.region == null) {
			throw new RuntimeException("grounditem NPE " + this);
		}

		for (Player player : tile.region.players) {
			if (activeOwner == null || activeOwner.isEmpty()) {
				this.sendUpdate(player, previousAmount);
				continue;
			}

			if (activeOwner.equalsIgnoreCase(player.getName())) {
				this.sendUpdate(player, previousAmount);
				return;
			}
		}
	}

	private void sendUpdate(Player player, int previousAmount) {
		player.getPacketSender().sendGroundItemUpdate(this, previousAmount);
	}

	/**
	 * For logging
	 */
	public GroundItem droppedBy(Player player) {
		timeDropped = System.currentTimeMillis();
		dropperName = player.getName();
		dropperIp = player.getIp();
		return this;
	}

	public String getDropperName() {
		return dropperName;
	}

	public String getDropperIp() {
		return dropperIp;
	}

	public long getTimeDropped() {
		return timeDropped;
	}

	@Override
	public boolean equals(Object other) {
		// NOTE: the equality is instance based, no matter what changes to the state
		return other == this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), originalOwner, activeOwner, diedToIron, id, amount, tile, respawnMinutes,
				timeDropped, dropperName, dropperIp, attributes);
	}
}
