package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * The reward island (region 16474, plane 2). Every member gets a chest; if the team earned a unique, the braziers
 * burn purple and the special chest on the northern platform holds it for its owner. Loot goes to the bank (or the
 * inventory for ultimate ironmen), and anything unclaimed when the raid ends waits in {@link Raid4#unclaimed} for
 * the lobby wheelbarrow.
 */
@Slf4j
public class Raid4VaultRoom implements Raid4Room {

	/**
	 * TFA_VAULT_OWN_CHEST: the slot (1-8) of the chest that is yours. The member chests are multinpcs
	 * (16736 + 2 * slot) that show this player's own chest as 16733 and everyone else's as 16732.
	 */
	private static final io.ruin.model.var.VarPlayerRepository OWN_CHEST = io.ruin.model.var.VarPlayerRepository.varpbit(16433, false);

	private Raid4Controller controller;
	private Raid4Instance instance;
	private DynamicMap map;
	private final List<NPC> chests = new ArrayList<>();
	private final Map<String, Raid4Loot.Reward> rewards = new HashMap<>();
	private final Map<NPC, String> owners = new HashMap<>();
	private NPC bigChest;
	private String uniqueOwner;
	/** Opened chests: the loot still in the chest's window (taken with Bank-all / Take-all). */
	private final Map<String, List<Item>> opened = new HashMap<>();
	/** tfa_vault_exit "Rift" by the arrival, and tfa_vault_exit_small, which opens by the chests after the first opening. */
	private io.ruin.model.map.object.GameObject rift;
	private NPC smallRift;
	private long ticks, smallRiftAt = -1;

	/** tfa_chests (973): the loot window over inv tfa_chests (1029, 6 slots). */
	public static final int CHEST_INTERFACE = 973, CHEST_BANK = 5, CHEST_TAKE = 7, CHEST_DISCARD = 9, CHEST_ITEMS = 11,
			CHEST_INV = 1029;

	@Override
	public Raid4RoomType type() {
		return Raid4RoomType.VAULT;
	}

	@Override
	public void init(Raid4Controller controller, DynamicMap map) {
		this.controller = controller;
		this.instance = controller.getInstance();
		this.map = map;
		List<Player> players = controller.getParty().online();
		Raid4Lobby.recordClear(controller, players);
		controller.markCompleted();
		Raid4Stats.count(players, Raid4Stats.OVERALL, Raid4Stats.COMPLETIONS);
		Raid4Stats.time(players, Raid4Stats.OVERALL, players.size(), controller.raidTicks());
		for (Player player : players) {
			player.fracturedArchiveKills.increment(player);
			player.fracturedArchiveKills.messageOnKill();
		}
		Map<Player, Raid4Loot.Reward> rolled = Raid4Loot.roll(players, controller.getPoints());
		// all eight chests every run, facing the middle; chest N is the raider in party slot N (5..8 stay shut unused)
		for (int i = 0; i < VAULT_CHESTS.length; i++) {
			NPC chest = spawn(NPC_VAULT_CHEST_FIRST + 2 * i, local(VAULT_CHESTS[i][0], VAULT_CHESTS[i][1]), Direction.SOUTH);
			Raid4Fx.faceTile(chest, local(VAULT_CHEST_FACE_X, VAULT_CHEST_FACE_Y));
			chests.add(chest);
		}
		List<String> members = controller.getParty().getMembers();
		for (Map.Entry<Player, Raid4Loot.Reward> e : rolled.entrySet()) {
			String key = Raid4Party.key(e.getKey());
			rewards.put(key, e.getValue());
			if (e.getValue().unique >= 0)
				uniqueOwner = key;
			int slot = members.indexOf(key);
			if (slot < 0 || slot >= chests.size())
				continue;
			owners.put(chests.get(slot), key);
			OWN_CHEST.set(e.getKey(), slot + 1);
			log.info("Raid 4: vault loot for {}: {}", e.getKey().getName(), e.getValue().items);
		}
		for (int[] b : VAULT_BUTTERFLIES) {
			Position at = local(b[0], b[1]);
			NPC fly = new NPC(NPC_VAULT_BUTTERFLY).spawn(at.getX(), at.getY(), at.getZ(), Direction.SOUTH, 10);
			map.addNpc(fly);
		}
		if (uniqueOwner != null)
			bigChest = spawn(NPC_VAULT_CHEST_BIG, local(VAULT_BIG_CHEST_X, VAULT_BIG_CHEST_Y), Direction.SOUTH);
		rift = instance.spawnObject(LOC_VAULT_EXIT, local(VAULT_RIFT_X, VAULT_RIFT_Y), 10, 0);
	}

	private Position local(int x, int y) {
		return instance.local(type(), x, y, VAULT_PLANE);
	}

	private NPC spawn(int id, Position pos, Direction dir) {
		NPC npc = new NPC(id).spawn(pos.getX(), pos.getY(), pos.getZ(), dir, 0);
		map.addNpc(npc);
		return npc;
	}

	@Override
	public void onEnter(Player player) {
	}

	/** A member's chest, or the special chest. */
	public void openChest(Player player, NPC chest) {
		String key = Raid4Party.key(player);
		Raid4Loot.Reward reward = rewards.get(key);
		if (chest == bigChest) {
			if (!key.equals(uniqueOwner)) {
				player.sendMessage("This chest isn't meant for you.");
				return;
			}
		} else if (!key.equals(owners.get(chest))) {
			player.sendMessage("This chest belongs to someone else.");
			return;
		} else if (reward != null && reward.unique >= 0) {
			player.sendMessage("Your reward waits in the special chest on the platform.");
			return;
		}
		if (reward == null) {
			if (opened.containsKey(key))
				showLoot(player);
			else
				player.sendMessage("The chest is empty.");
			return;
		}
		rewards.remove(key);
		player.face(chest);
		player.animate(SEQ_SMALL_CHEST_OPEN);
		if (chest == bigChest)
			chest.transform(NPC_VAULT_CHEST_BIG_OPEN);
		else
			chest.transform(chest.getId() + 1); // vault_plan 2: the chest's own _open form (multinpc on the same varbit)
		chest.animate(SEQ_VAULT_CHEST_OPEN);
		player.graphics(GFX_CHEST_SPARKLES);
		List<Item> loot = new ArrayList<>();
		long value = 0;
		for (Item item : reward.items) {
			int id = item.getDef().isNote() ? item.getDef().unnotedId() : item.getId();
			player.addToCollectionLog(new Item(id, item.getAmount()));
			if (Raid4Loot.isUnique(item.getId())) {
				for (int[] fx : GFX_LOOT_UNIQUE) {
					if (fx[0] == item.getId())
						player.graphics(fx[1]);
				}
				for (Player p : controller.getParty().online())
					p.sendMessage("<col=a53fff>" + player.getName() + " found something special: " + item.getDef().name + "!</col>");
				Broadcast.WORLD.sendNews(player.getName() + " just received " + item.getDef().descriptiveName + " from The Fractured Archive!");
			}
			value += (long) Math.max(0, item.getDef().value) * item.getAmount();
			loot.add(new Item(item.getId(), item.getAmount()));
		}
		opened.put(key, loot);
		if (smallRiftAt < 0)
			smallRiftAt = ticks + 5;
		player.sendMessage("<col=ef1020>Your Fractured Archive chest is worth " + io.ruin.api.utils.NumberUtils.formatNumber(value) + " coins.</col>");
		showLoot(player);
	}

	/** The chest window: the items left in it, Bank-all / Take-all / Discard-all. */
	private void showLoot(Player player) {
		List<Item> loot = opened.get(Raid4Party.key(player));
		if (loot == null)
			return;
		var ps = player.getPacketSender();
		player.openInterface(io.ruin.model.inter.ToplevelComponent.MAINMODAL, CHEST_INTERFACE);
		ps.sendItems(CHEST_INV, loot.toArray(new Item[0]));
		ps.sendClientScript(149, "IviiiIsssss", CHEST_INTERFACE << 16 | CHEST_ITEMS, CHEST_INV, 2, 3, 0, -1, "", "", "", "", "");
		ps.sendIfEvents(CHEST_INTERFACE, CHEST_BANK, -1, -1, 1 << 6);
		ps.sendIfEvents(CHEST_INTERFACE, CHEST_TAKE, -1, -1, 1 << 1);
		ps.sendIfEvents(CHEST_INTERFACE, CHEST_DISCARD, -1, -1, 1 << 6);
	}

	/** A loot-window button: 0 bank, 1 take, 2 discard. */
	private void lootButton(Player player, int action) {
		String key = Raid4Party.key(player);
		List<Item> loot = opened.get(key);
		if (loot == null) {
			player.closeInterface(io.ruin.model.inter.ToplevelComponent.MAINMODAL);
			return;
		}
		boolean uim = player.getGameMode() != null && player.getGameMode().isUltimateIronman();
		if (action == 0 && uim) {
			player.sendMessage("Ultimate ironmen can't bank their loot; take it instead.");
			return;
		}
		if (action == 0) {
			bankItems(player, loot);
			player.sendMessage("Your loot has been sent to your bank.");
		} else if (action == 1) {
			for (Item item : loot) {
				int id = item.getId();
				if (item.getAmount() > 1 && !item.getDef().stackable && item.getDef().notedId > 0)
					id = item.getDef().notedId;
				player.getInventory().addOrDrop(id, item.getAmount());
			}
		} else
			player.sendMessage("You discard the contents of the chest.");
		opened.remove(key);
		player.getPacketSender().sendItems(CHEST_INV);
		player.closeInterface(io.ruin.model.inter.ToplevelComponent.MAINMODAL);
	}

	private static void bankItems(Player player, List<Item> items) {
		for (Item item : items) {
			int id = item.getDef().isNote() ? item.getDef().unnotedId() : item.getId();
			player.getBank().add(id, item.getAmount());
		}
	}

	public static void register() {
		io.ruin.model.inter.InterfaceHandler.register(CHEST_INTERFACE, h -> {
			int[][] buttons = { { CHEST_BANK, 0 }, { CHEST_TAKE, 1 }, { CHEST_DISCARD, 2 } };
			for (int[] b : buttons)
				h.actions[b[0]] = (io.ruin.model.inter.actions.SimpleAction) p -> {
					Raid4Controller raid = Raid4.controllerOf(p);
					if (raid != null && raid.getRoom() instanceof Raid4VaultRoom vault)
						vault.lootButton(p, b[1]);
					else
						p.closeInterface(io.ruin.model.inter.ToplevelComponent.MAINMODAL);
				};
		});
	}

	/** Bank (inventory for ultimate ironmen), collection log, broadcasts. */
	public static void claim(Player player, Raid4Loot.Reward reward) {
		boolean uim = player.getGameMode() != null && player.getGameMode().isUltimateIronman();
		StringBuilder sb = new StringBuilder();
		for (Item item : reward.items) {
			player.addToCollectionLog(new Item(item.getDef().isNote() ? item.getDef().unnotedId() : item.getId(), item.getAmount()));
			if (Raid4Loot.isUnique(item.getId())) {
				for (int[] fx : GFX_LOOT_UNIQUE) {
					if (fx[0] == item.getId())
						player.graphics(fx[1]);
				}
				Broadcast.WORLD.sendNews(player.getName() + " just received " + item.getDef().descriptiveName + " from The Fractured Archive!");
			}
			if (uim)
				player.getInventory().addOrDrop(item.getId(), item.getAmount());
			else {
				int id = item.getDef().isNote() ? item.getDef().unnotedId() : item.getId();
				player.getBank().add(id, item.getAmount());
			}
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(item.getAmount()).append(" x ").append(item.getDef().name);
		}
		player.sendMessage("<col=ef1020>Your Fractured Archive loot" + (uim ? "" : " (sent to your bank)") + ": " + sb + ".</col>");
	}

	/**
	 * Loot nobody opened before the raid ended: online members get it in the bank straight away (OSRS keeps it in
	 * the lobby wheelbarrow); offline members' loot waits for {@code ::raid4 claim}.
	 */
	@Override
	public void cleanup() {
		for (Map.Entry<String, Raid4Loot.Reward> e : rewards.entrySet()) {
			Player online = null;
			for (Player p : controller.getParty().online()) {
				if (Raid4Party.key(p).equals(e.getKey()))
					online = p;
			}
			if (online != null) {
				online.sendMessage("Your unopened Fractured Archive chest has been delivered.");
				claim(online, e.getValue());
			} else {
				Raid4.unclaimed.put(e.getKey(), e.getValue());
			}
		}
		for (Map.Entry<String, List<Item>> e : opened.entrySet()) {
			Player online = null;
			for (Player p : controller.getParty().online()) {
				if (Raid4Party.key(p).equals(e.getKey()))
					online = p;
			}
			if (online != null) {
				online.closeInterface(io.ruin.model.inter.ToplevelComponent.MAINMODAL);
				online.sendMessage("The loot left in your Fractured Archive chest has been sent to your bank.");
				bankItems(online, e.getValue());
			} else {
				Raid4Loot.Reward leftover = new Raid4Loot.Reward();
				leftover.items.addAll(e.getValue());
				Raid4.unclaimed.put(e.getKey(), leftover);
			}
		}
		opened.clear();
		rewards.clear();
		if (rift != null)
			instance.removeObject(rift);
		if (smallRift != null)
			smallRift.remove();
		for (Player p : controller.getParty().online())
			OWN_CHEST.set(p, 0);
		for (NPC npc : chests)
			npc.remove();
		chests.clear();
		if (bigChest != null)
			bigChest.remove();
	}

	@Override
	public void start() {
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks == 9) // A+9 the braziers; the unique's colour is the wiki's (no recording has a unique)
			for (int[] b : VAULT_BRAZIERS)
				instance.spawnObject(uniqueOwner != null ? LOC_VAULT_BRAZIER_UNIQUE : LOC_VAULT_BRAZIER, local(b[0], b[1]), 22, b[2]);
		if (ticks == smallRiftAt && smallRift == null)
			smallRift = spawn(NPC_VAULT_EXIT_SMALL, local(VAULT_SMALL_RIFT_X, VAULT_SMALL_RIFT_Y), Direction.NORTH);
	}

	@Override
	public void onNpcDeath(NPC npc) {
	}

	@Override
	public boolean isStarted() {
		return true;
	}

	@Override
	public boolean isComplete() {
		return true;
	}

	@Override
	public Position entryPosition() {
		return instance.walkable(type(), VAULT_ARRIVAL_X, VAULT_ARRIVAL_Y, VAULT_PLANE, 2);
	}

	@Override
	public Position spectatePosition() {
		return entryPosition();
	}
}
