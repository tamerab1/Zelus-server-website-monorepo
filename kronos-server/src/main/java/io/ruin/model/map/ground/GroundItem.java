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
			java.util.Map.entry(60256, 41932), // Aetherial Scythe
			java.util.Map.entry(60257, 41933), // Angelic Greatsword
			java.util.Map.entry(60258, 41934), // Nether Defender
			java.util.Map.entry(60259, 41935), // Ward of the Depths
			java.util.Map.entry(60260, 41936), // Owner Cape
			java.util.Map.entry(60261, 41937), // Owner Wings
			java.util.Map.entry(60262, 41938), // Souly Wings
			java.util.Map.entry(60263, 41939), // Zealot's Horned Visage
			java.util.Map.entry(60264, 41940), // Zealot's Warplate
			java.util.Map.entry(60265, 41941), // Zealot's Greaves
			java.util.Map.entry(60266, 41942), // Zealot's Gloves
			java.util.Map.entry(60267, 41943), // Zealot's Striders
			java.util.Map.entry(60268, 41944), // Ancient Cleaver
			java.util.Map.entry(60269, 41945), // Ancient Signet
			java.util.Map.entry(60270, 41946), // Warlord's Dreadcloak
			java.util.Map.entry(60271, 41947), // Zarosian Dreadhelm
			java.util.Map.entry(60272, 41948), // Zarosian Platelegs
			java.util.Map.entry(60273, 41949), // Zarosian Sabatons
			java.util.Map.entry(60274, 41950), // Zarosian Warplate
			java.util.Map.entry(60275, 41951), // Draconic Hornbow
			java.util.Map.entry(60276, 41952), // Brimstone Hornbow
			java.util.Map.entry(60277, 41953), // 1: Bond of the Blood Titan
			java.util.Map.entry(60278, 41954), // 2: Bond of the Slayer King
			java.util.Map.entry(60279, 41955), // 3: Bond of the Astral Archmage
			java.util.Map.entry(60280, 41956), // 4: Bond of the Sovereign Monarch
			java.util.Map.entry(60281, 41957), // 5: Bond of the Artisan
			java.util.Map.entry(60282, 41958), // 6: Bond of the Void Deadeye
			java.util.Map.entry(59531, 41959), // AsVal Gun
			java.util.Map.entry(59548, 41960), // Icenier Sword
			java.util.Map.entry(59604, 41961), // Imperial staff
			java.util.Map.entry(59605, 41962), // Imperial bow
			java.util.Map.entry(59606, 41963), // Imperial top
			java.util.Map.entry(59609, 41964), // Crimson hat
			java.util.Map.entry(59626, 41965), // Nightfall bow
			java.util.Map.entry(44827, 41966), // Hallowfell
			java.util.Map.entry(44830, 41967), // Jar of light
			java.util.Map.entry(44832, 41968), // Sunstone crystal
			java.util.Map.entry(44833, 41969), // Ardeaglais teleport
			java.util.Map.entry(59603, 41970), // Nox
			java.util.Map.entry(60236, 41971), // Teumo
			java.util.Map.entry(60237, 41972), // Orrvor quo Maten
			java.util.Map.entry(60238, 41973), // Lord Trobin
			java.util.Map.entry(60239, 41974), // Wandering impling
			java.util.Map.entry(60240, 41975), // Wagchin
			java.util.Map.entry(60241, 41976), // The Monkey's Aunt
			java.util.Map.entry(60242, 41977), // Tempoross
			java.util.Map.entry(60243, 41978), // Strisath
			java.util.Map.entry(60244, 41979), // Starlight
			java.util.Map.entry(60245, 41980), // Star Sprite
			java.util.Map.entry(60246, 41981), // Spooky dog
			java.util.Map.entry(60247, 41982), // Spit goblin
			java.util.Map.entry(60248, 41983), // Shadow Keeper
			java.util.Map.entry(60249, 41984), // Slimetoes
			java.util.Map.entry(60250, 41985), // SteelWill
			java.util.Map.entry(60251, 41986), // Mossfists
			java.util.Map.entry(60252, 41987), // Damo
			java.util.Map.entry(60253, 41988), // Sliske
			java.util.Map.entry(60254, 41989), // Light Leech
			java.util.Map.entry(60255, 41990), // ToxiSkele
			java.util.Map.entry(44842, 41991), // Aggy
			java.util.Map.entry(60330, 41992), // Sylvaroth's Sprout (pet)
			java.util.Map.entry(60331, 41993), // Draconic Cape
			java.util.Map.entry(60332, 41994), // Draconic Godsword
			java.util.Map.entry(60333, 41995), // Draconic Helmet
			java.util.Map.entry(60334, 41996), // Draconic Longsword
			java.util.Map.entry(60335, 41997), // Draconic Platebody
			java.util.Map.entry(60336, 41998), // Draconic Platelegs
			java.util.Map.entry(60337, 41999), // Draconic Wings
			java.util.Map.entry(60338, 42000), // Draconies Hood
			java.util.Map.entry(60339, 42001), // Draconies Platebody
			java.util.Map.entry(60340, 42002), // Draconies Platelegs
			java.util.Map.entry(60341, 42003), // Draconies Warhammer
			java.util.Map.entry(60342, 42004), // Draconies Wings
			java.util.Map.entry(60355, 42005), // Drakonbone Boots
			java.util.Map.entry(60356, 42006), // Drakonbone Crest
			java.util.Map.entry(60357, 42007), // Drakonbone Cuirass
			java.util.Map.entry(60358, 42008), // Drakonbone Gloves
			java.util.Map.entry(60359, 42009), // Drakonbone Platelegs
			java.util.Map.entry(60360, 42010), // Hallowed Cape
			java.util.Map.entry(60361, 42011), // Hallowed Elf Boots
			java.util.Map.entry(60362, 42012), // Hallowed Gloves
			java.util.Map.entry(60363, 42013), // Hallowed Goster
			java.util.Map.entry(60364, 42014), // Hallowed Jack-o
			java.util.Map.entry(60365, 42015), // Hallowed Trousers
			java.util.Map.entry(60366, 42016), // Halloween Bow
			java.util.Map.entry(60367, 42017), // Halloween Godsword
			java.util.Map.entry(60368, 42018), // Halloween Key
			java.util.Map.entry(60369, 42019), // Halloween Scimitar
			java.util.Map.entry(60370, 42020), // Halloween Staff
			java.util.Map.entry(60371, 42021), // Halloween Whip
			java.util.Map.entry(60389, 42022), // Dracodust
			java.util.Map.entry(60391, 42023), // Baby Draco (pet)
			java.util.Map.entry(60400, 42024), // Draconic Pickaxe
			java.util.Map.entry(60405, 42025)  // Draco Pickaxe
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
	/**
	 * Loot the world created (an npc drop), not an item a player put down. The economy-protection ground guard deletes
	 * SPAWNABLE ids by id to stop ::spawn -> drop -> alt pick-up duping, which also deleted every legitimate drop of
	 * those ids (2026-10-19: Warriors' Guild rune defenders logged to the collection log but never appeared, so the
	 * player kept rolling and "getting" them). World loot can't be a spawned copy, so the guard lets it through.
	 */
	public boolean worldLoot;

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

	/** Marks this as npc / world loot (see {@link #worldLoot}). */
	public GroundItem loot() {
		this.worldLoot = true;
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
