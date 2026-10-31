package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.activities.raids.tfa.bosses.guardian.ArchiveGuardian;
import io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.dynamic.DynamicMap;

/**
 * {@code ::raid4} admin commands, standing in for the lobby interfaces until they're wired:
 * create, invite, join, leave, start, lobby, status.
 */
public final class Raid4Commands {

	private Raid4Commands() {
	}

	public static boolean handle(Player player, String[] args) {
		String sub = args.length == 0 ? "status" : args[0].toLowerCase();
		String name = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
		switch (sub) {
			case "create" -> {
				Raid4Party party = Raid4.createParty(player);
				player.sendMessage("Archive party: " + party.getMembers());
			}
			case "invite" -> {
				Player target = name == null ? null : World.getPlayer(name);
				if (target == null)
					player.sendMessage("Usage: ::raid4 invite <online player>");
				else
					Raid4.invite(player, target);
			}
			case "join" -> {
				Player leader = name == null ? null : World.getPlayer(name);
				if (leader == null)
					player.sendMessage("Usage: ::raid4 join <leader>");
				else
					Raid4.join(player, leader);
			}
			case "leave" -> Raid4.leaveParty(player);
			case "start" -> {
				Raid4Party party = Raid4.createParty(player);
				if (party.hasStarted())
					player.sendMessage("Your party is already inside.");
				else if (!party.isLeader(player))
					player.sendMessage("Only the leader can start.");
				else
					Raid4.start(party);
			}
			case "lobby" -> player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
			case "rock" -> rock(player, args);
			case "breath" -> {
				Raid4Controller raid = Raid4.controllerOf(player);
				if (raid != null && raid.getRoom() instanceof ArchiveGuardianRoom room && room.getGuardian() != null
						&& room.getGuardian().getCombat() instanceof ArchiveGuardian boss)
					boss.debugBreath();
				else
					player.sendMessage("No awake Archive Guardian in your raid.");
			}
			case "phase" -> {
				Raid4Controller raid = Raid4.controllerOf(player);
				if (raid != null && raid.getRoom() instanceof ArchiveGuardianRoom room && room.getGuardian() != null
						&& room.getGuardian().getCombat() instanceof ArchiveGuardian boss) {
					boss.debugAdvancePhase();
					player.sendMessage("Guardian phase -> " + boss.getPhase());
				} else {
					player.sendMessage("No awake Archive Guardian in your raid.");
				}
			}
			case "skip" -> {
				// testing: clear the Guardian without the fight and open the path doors
				Raid4Controller raid = Raid4.controllerOf(player);
				if (raid != null && raid.getRoom() instanceof ArchiveGuardianRoom room && !room.isComplete()) {
					raid.roomComplete(room);
					raid.openNexus(room);
				} else {
					player.sendMessage("You need to be in the Guardian's Nexus with the Guardian alive.");
				}
			}
			case "ezhp" -> {
				// testing: knock Ezekiel down to the given % of his max hp (33 or less enrages him)
				Raid4Controller raid = Raid4.controllerOf(player);
				if (raid != null && raid.getRoom() instanceof io.ruin.model.activities.raids.tfa.rooms.EzekielRoom room
						&& room.isStarted() && room.getEzekiel().getCombat() instanceof io.ruin.model.activities.raids.tfa.bosses.ezekiel.Ezekiel boss) {
					int pct = name == null ? 34 : Integer.parseInt(name.trim());
					int target = (int) (boss.getMaxHp() * pct / 100.0);
					int damage = room.getEzekiel().getHp() - Math.max(0, target);
					if (damage > 0)
						room.getEzekiel().hit(new io.ruin.model.combat.Hit().fixedDamage(damage).ignoreDefence().ignorePrayer());
				} else {
					player.sendMessage("No started Ezekiel fight in your raid.");
				}
			}
			case "goto" -> {
				// testing: jump the whole raid straight into any boss room (or the vault)
				Raid4Controller raid = Raid4.controllerOf(player);
				Raid4RoomType type = null;
				for (Raid4RoomType t : Raid4RoomType.values()) {
					if (name != null && t.name().equalsIgnoreCase(name.trim()))
						type = t;
				}
				if (raid == null || type == null || type == Raid4RoomType.NEXUS || type == Raid4RoomType.NEXUS_CLEARED) {
					player.sendMessage("::raid4 goto ezekiel|bork|moia|dreadnoth|dagonhai|xarth|ocellus|vault (inside a started raid)");
				} else if (!raid.debugEnter(type)) {
					player.sendMessage("Couldn't build " + type.displayName + ".");
				}
			}
			case "hp" -> {
				// testing: knock the room's biggest npc down to the given % of its max hp
				Raid4Controller raid = Raid4.controllerOf(player);
				if (raid == null || raid.getRoom() == null || !raid.getInstance().isBuilt(raid.getRoom().type())) {
					player.sendMessage("You need to be in a raid room.");
					return true;
				}
				io.ruin.model.entity.npc.NPC best = null;
				var npcs = raid.getInstance().get(raid.getRoom().type()).getNpcs();
				if (npcs != null) {
					for (io.ruin.model.entity.npc.NPC npc : npcs) {
						if (npc.getCombat() != null && !npc.getCombat().isDead() && (best == null || npc.getMaxHp() > best.getMaxHp()))
							best = npc;
					}
				}
				if (best == null) {
					player.sendMessage("No boss here.");
					return true;
				}
				int pct = name == null ? 50 : Integer.parseInt(name.trim());
				int damage = best.getHp() - Math.max(1, (int) (best.getMaxHp() * pct / 100.0));
				if (damage > 0)
					best.hit(new io.ruin.model.combat.Hit(player).fixedDamage(damage).ignoreDefence().ignorePrayer());
				player.sendMessage(best.getDef().name + " -> " + pct + "%");
			}
			case "rescue" -> {
				// staff: send a party stuck after Ocellus on to the vault, as The Blade's Travel does (2026-10-31: a
				// party couldn't reach The Blade); refused unless Ocellus is dead, so it never skips any of the raid
				io.ruin.model.entity.player.Player target = name == null ? null : io.ruin.model.World.getPlayer(name.trim());
				Raid4Controller raid = target == null ? null : Raid4.controllerOf(target);
				if (raid == null || raid.getRoom() == null || raid.getRoom().type() != Raid4RoomType.OCELLUS || !raid.getRoom().isComplete()) {
					player.sendMessage("::raid4 rescue <player> - they must be online, in a raid, with Ocellus dead.");
				} else {
					raid.travelToVault(target);
					player.sendMessage("Sent " + target.getName() + "'s party on to the vault.");
				}
			}
			case "compensate" -> {
				// staff: one Fractured Archive chest, rolled as a solo vault chest and banked as the vault does, for a
				// raider whose run was lost to a bug (2026-10-31: a party couldn't reach The Blade after Ocellus).
				// Logged: it's a reward grant.
				Player target = name == null ? null : World.getPlayer(name.trim());
				if (target == null) {
					player.sendMessage("::raid4 compensate <player> - they must be online.");
					return true;
				}
				Raid4Points points = new Raid4Points();
				points.damageDealt(target, 1); // any points: a plain chest, never the no-points cabbage
				Raid4Loot.Reward reward = Raid4Loot.roll(java.util.List.of(target), points).get(target);
				Raid4VaultRoom.claim(target, reward);
				target.sendMessage("<col=ef1020>Staff have given you a Fractured Archive chest for your lost run.</col>");
				player.sendMessage("Gave " + target.getName() + " one Fractured Archive chest (" + reward.items.size() + " item(s)).");
				org.slf4j.LoggerFactory.getLogger(Raid4Commands.class).info("Raid 4: {} compensated {} with a chest: {}",
						player.getName(), target.getName(), reward.items);
			}
			case "stop" -> Raid4Lobby.stopSpectating(player);
			case "claim" -> {
				Raid4Loot.Reward reward = Raid4.unclaimed.remove(Raid4Party.key(player));
				if (reward == null)
					player.sendMessage("You have no unclaimed Fractured Archive loot.");
				else
					Raid4VaultRoom.claim(player, reward);
			}
			case "status" -> status(player);
			default -> player.sendMessage("::raid4 create | invite <name> | join <leader> | leave | start | lobby | status | phase | breath | rock | skip | goto <room> | hp <pct> | claim | ezhp <pct>");
		}
		return true;
	}

	/** {@code ::raid4 rock}: retired (the throw uses the recorded values, guardian_plan 4). */
	private static void rock(Player player, String[] args) {
		player.sendMessage("The rock uses the recorded projectile values now.");
	}

	private static void status(Player player) {
		player.sendMessage("Raid 4: " + Raid4.active().size() + " running, " + DynamicMap.FREE_REGIONS_SIZE() + " free regions.");
		Raid4Party party = Raid4.partyOf(player);
		if (party == null) {
			player.sendMessage("You're not in an Archive party.");
			return;
		}
		player.sendMessage("Party " + party.getMembers() + ", leader " + party.getLeader());
		Raid4Controller raid = party.getController();
		if (raid == null)
			return;
		Raid4Room room = raid.getRoom();
		player.sendMessage("Room " + (room == null ? "-" : room.type()) + ", next path slot " + raid.getNextSlot()
				+ ", your points " + raid.getPoints().points(player));
		if (room instanceof ArchiveGuardianRoom guardianRoom && guardianRoom.getGuardian() != null
				&& guardianRoom.getGuardian().getCombat() instanceof ArchiveGuardian boss) {
			player.sendMessage("Guardian " + boss.getPhase() + " (cycle " + boss.getCycle() + "), bar "
					+ guardianRoom.getGuardian().getHp() + "/" + guardianRoom.getGuardian().getMaxHp() + ", real hp "
					+ boss.getRealHp() + ", scale " + guardianRoom.getScale());
		}
	}
}
