package io.ruin.model.activities.raids.tfa;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A Fractured Archive party: 1-8 players, keyed by lower-case name so a relog keeps the slot. The leader starts
 * the raid; leadership passes to the next member when the leader leaves.
 */
public class Raid4Party {

	public static final int MAX_SIZE = 8;

	@Getter
	private String leader;
	private final List<String> members = new ArrayList<>(MAX_SIZE);
	private final Set<String> invites = new LinkedHashSet<>();
	/** Players who applied on the party board (976), waiting for the leader's Accept / Reject. */
	public final Set<String> applicants = new LinkedHashSet<>();
	/** When the party was made: the board's Age column. */
	public final long createdAt = System.currentTimeMillis();
	/** Members who died in the current room. They spectate until the room is cleared. */
	private final Set<String> dead = new LinkedHashSet<>();

	@Getter
	private Raid4Controller controller;

	public Raid4Party(Player leader) {
		this.leader = key(leader);
		this.members.add(this.leader);
	}

	static String key(Player player) {
		return player.getName().toLowerCase();
	}

	public List<String> getMembers() {
		return Collections.unmodifiableList(members);
	}

	public int size() {
		return members.size();
	}

	public boolean isMember(Player player) {
		return members.contains(key(player));
	}

	public boolean isLeader(Player player) {
		return leader.equals(key(player));
	}

	public boolean isFull() {
		return members.size() >= MAX_SIZE;
	}

	public boolean hasStarted() {
		return controller != null;
	}

	void setController(Raid4Controller controller) {
		this.controller = controller;
	}

	public void invite(Player player) {
		invites.add(key(player));
	}

	public boolean isInvited(Player player) {
		return invites.contains(key(player));
	}

	/** Adds an invited player. Returns false if the party is full, started, or they weren't invited. */
	public boolean accept(Player player) {
		String name = key(player);
		if (!invites.remove(name) || isFull() || hasStarted() || members.contains(name))
			return false;
		members.add(name);
		return true;
	}

	/** Removes a member, handing leadership on. Returns true if the party is now empty. */
	public boolean remove(String name) {
		members.remove(name);
		dead.remove(name);
		if (members.isEmpty())
			return true;
		if (leader.equals(name))
			leader = members.get(0);
		return false;
	}

	public void markDead(Player player) {
		dead.add(key(player));
	}

	public boolean isDead(Player player) {
		return dead.contains(key(player));
	}

	public Set<String> getDead() {
		return Collections.unmodifiableSet(dead);
	}

	public void clearDead() {
		dead.clear();
	}

	/** Online members. */
	public List<Player> online() {
		List<Player> list = new ArrayList<>(members.size());
		for (String name : members) {
			Player player = World.getPlayer(name);
			if (player != null)
				list.add(player);
		}
		return list;
	}

	/** Online members who are alive. */
	public List<Player> alive() {
		List<Player> list = online();
		list.removeIf(p -> dead.contains(key(p)) || p.getCombat().isDead());
		return list;
	}

	public void forEach(Consumer<Player> action) {
		online().forEach(action);
	}

	public void message(String message) {
		forEach(p -> p.sendMessage(message));
	}

	public Player leaderPlayer() {
		return World.getPlayer(leader);
	}

	@Override
	public String toString() {
		return "Raid4Party{leader=" + leader + ", members=" + members + "}";
	}
}
