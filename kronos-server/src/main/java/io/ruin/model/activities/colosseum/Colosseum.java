package io.ruin.model.activities.colosseum;

import io.ruin.HooksV2;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.network.incoming.handlers.CommandHandler;

import java.util.*;

/**
 * The Colosseum of Trials (2026-11-07): the Roman arena in region {@link #REGION}. The Grand Arbiter (for now vanilla
 * Minimus, npc {@link #ARBITER}, until the cache clone) at the foot of the carpet stairs offers the trials ({@link Trial}),
 * solo or duo; each fight is a private copy of the arena ({@link TrialSession}). Duo: the leader names a partner in the
 * Colosseum, who gets an Accept / Decline prompt for {@link #INVITE_TICKS} ticks. Trial Marks and personal bests are on
 * the player ({@code trialMarks}, {@code trialBests}).
 * <p>
 * Commands: ::colosseum (to the lobby), ::trialmarks; staff: ::trial &lt;name&gt; (solo, from anywhere),
 * ::givetrialmarks &lt;n&gt;.
 * <p>
 * The positions below are first picks from the map (the platform centre 3231,5855, the carpet north of it); fine-tune
 * them with ::pos.
 */
public final class Colosseum {

	private Colosseum() {
	}

	public static final int REGION = 12891, ARBITER = 12807;
	/** where ::colosseum lands (user, 2026-11-07) */
	public static final Position LOBBY = new Position(3231, 5841, 0);
	static final Position ARENA_ENTRY = new Position(3231, 5861, 0), ARENA_BOSS = new Position(3231, 5852, 0);
	static final Direction ARENA_ENTRY_FACING = Direction.SOUTH, ARENA_BOSS_FACING = Direction.NORTH;
	static final int INVITE_TICKS = 50; // 30 seconds

	private static final Set<TrialSession> SESSIONS = new HashSet<>();
	private static final Map<Player, Invite> INVITES = new WeakHashMap<>();

	private record Invite(Player from, Trial trial, long expires) {}

	/** Object 29559 (the vanilla Death figure at 3230,5869, recoloured and renamed by cache stage 21): the shop. */
	public static final int RELIQUARY = 29559;

	private static final int HALL_PORTAL = 4150, HALL_BANK_CHEST = 14886; // 1x1 "Use" portal; a bank chest (BankActions handles it by name)
	private static final Position PORTAL_AT = new Position(3231, 5838, 0), PORTAL_TO = new Position(3231, 5865, 0);

	/** The lobby's portal up to the Arbiter's end of the hall, and the two bank chests beside him (user, 2026-11-08). */
	private static void spawnHallObjects() {
		io.ruin.model.map.object.GameObject portal = io.ruin.model.map.object.GameObject.spawn(HALL_PORTAL, PORTAL_AT, 10, 0);
		io.ruin.model.map.object.actions.ObjectAction.register(portal, 1, (player, obj) -> player.getMovement().startTeleport(event -> {
			player.animate(3864);
			player.graphics(1039);
			event.delay(2);
			player.getMovement().teleport(PORTAL_TO);
		}));
		io.ruin.model.map.object.GameObject.spawn(HALL_BANK_CHEST, 3235, 5869, 0, 10, 0);
		io.ruin.model.map.object.GameObject.spawn(HALL_BANK_CHEST, 3227, 5869, 0, 10, 0);
	}

	public static void register() {
		// The Grand Arbiter is object 29559 (cache stage 21): Trade = the Reliquary, Trials = the picker, Check-marks.
		// (Minimus 12807 stood in for him before; its spawn is gone, the handler stays harmless.)
		NPCAction.register(ARBITER, 1, (player, npc) -> TrialSelectInterface.open(player));
		io.ruin.model.map.object.actions.ObjectAction.register(RELIQUARY, 1, (player, obj) -> ReliquaryInterface.open(player));
		io.ruin.model.map.object.actions.ObjectAction.register(RELIQUARY, 2, (player, obj) -> TrialSelectInterface.open(player));
		io.ruin.model.map.object.actions.ObjectAction.register(RELIQUARY, 3,
				(player, obj) -> player.sendMessage("You hold <col=7f0000>" + player.trialMarks + "</col> Trial Marks."));
		io.ruin.model.map.object.actions.ObjectAction.register(RELIQUARY, 4, (player, obj) -> EggForge.open(player));
		ReliquaryInterface.register();
		EggForge.register();
		TrialHosts.register();
		World.startEvent(e -> {
			e.delay(1);
			TrialHosts.spawn();
			spawnHallObjects();
		});
		TrialSelectInterface.register();
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, Colosseum::command);
	}

	// ------------------------------------------------------------------ entering

	/** Why the player can't start or join a trial now, or null. */
	private static String blocked(Player player, boolean needLobby) {
		if (player.getCombat().isDead() || player.isLocked())
			return "You can't do that right now.";
		if (player.isInstanced || player.currentDynamicMap != null || inSession(player))
			return "You are already in a fight.";
		if (needLobby && player.getPosition().regionId() != REGION)
			return "You need to be in the Colosseum of Trials.";
		return null;
	}

	static void enter(Player player, Trial t) {
		String why = blocked(player, true);
		if (why != null) {
			player.sendMessage(why);
			return;
		}
		player.closeDialogue();
		start(t, List.of(player));
	}

	private static void start(Trial t, List<Player> members) {
		TrialSession s = TrialSession.start(t, members);
		if (s != null)
			SESSIONS.add(s);
	}

	static void ended(TrialSession s) {
		SESSIONS.remove(s);
	}

	private static boolean inSession(Player player) {
		for (TrialSession s : SESSIONS)
			if (s.members.contains(player))
				return true;
		return false;
	}

	// ------------------------------------------------------------------ duo invites

	static void invite(Player player, Trial t) {
		String why = blocked(player, true);
		if (why != null) {
			player.sendMessage(why);
			return;
		}
		player.stringInput("Invite which player to " + t.title + "?", name -> {
			Optional<Player> found = World.getPlayerByName(name);
			if (found.isEmpty()) {
				player.sendMessage(name + " is not online.");
				return;
			}
			Player target = found.get();
			if (target == player) {
				player.sendMessage("You can't invite yourself.");
				return;
			}
			String theirs = blocked(target, true);
			if (theirs != null) {
				player.sendMessage(target.getName() + " can't join right now (they need to be free, in the Colosseum).");
				return;
			}
			Invite inv = new Invite(player, t, io.ruin.Server.currentTick() + INVITE_TICKS);
			INVITES.put(target, inv);
			player.sendMessage("You invite " + target.getName() + " to " + t.title + ". The invite lasts 30 seconds.");
			target.sendMessage("<col=7f0000>" + player.getName() + " invites you to a duo: " + t.title + ".</col>");
			target.dialogue(new OptionsDialogue(player.getName() + " invites you: " + t.title + " (duo)",
					new Option("Accept", () -> accept(target)),
					new Option("Decline", () -> decline(target))));
			World.startEvent(e -> {
				e.delay(INVITE_TICKS);
				if (INVITES.get(target) == inv) {
					INVITES.remove(target);
					player.sendMessage("Your invite to " + target.getName() + " has expired.");
					target.sendMessage("The duo invite from " + player.getName() + " has expired.");
				}
			});
		});
	}

	private static void accept(Player target) {
		Invite inv = INVITES.remove(target);
		target.closeDialogue();
		if (inv == null || io.ruin.Server.currentTick() > inv.expires()) {
			target.sendMessage("That invite has expired.");
			return;
		}
		Player leader = inv.from();
		String why = !leader.isOnline() ? "is gone" : blocked(leader, true) != null ? "is busy" : null;
		if (why != null) {
			target.sendMessage(leader.getName() + " " + why + ".");
			return;
		}
		String mine = blocked(target, true);
		if (mine != null) {
			target.sendMessage(mine);
			return;
		}
		leader.sendMessage(target.getName() + " accepts. To the arena!");
		start(inv.trial(), List.of(leader, target));
	}

	private static void decline(Player target) {
		Invite inv = INVITES.remove(target);
		target.closeDialogue();
		if (inv != null && inv.from().isOnline())
			inv.from().sendMessage(target.getName() + " declines your invite.");
	}

	// ------------------------------------------------------------------ the raid-style move (fade to black and back)

	/** Raid 4's fade: overlay 174 + script 948 (alpha 0 -> 255 over 50 cycles). */
	private static final int FADE_OVERLAY = 174, SCRIPT_FADE = 948;

	static void fadeOut(Player player) {
		player.getPacketSender().sendInterface(FADE_OVERLAY, io.ruin.model.inter.ToplevelComponent.OVERLAY);
		player.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 255, 0, 0, 50);
	}

	static void fadeIn(Player player) {
		player.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 0, 0, 255, 50);
		World.startEvent(e -> {
			e.delay(2);
			if (player.isOnline() && player.isVisibleInterface(FADE_OVERLAY))
				player.closeInterface(io.ruin.model.inter.ToplevelComponent.OVERLAY);
		});
	}

	/** Back to the lobby out of the dark, facing the arena. */
	static void walkOut(Player player) {
		player.getMovement().teleport(LOBBY);
		player.face(Direction.SOUTH);
		fadeIn(player);
	}

	// ------------------------------------------------------------------ bests, commands

	static void recordBest(Player p, Trial t, boolean duo, int ticks) {
		if (p.trialBests == null)
			p.trialBests = new HashMap<>();
		Integer old = p.trialBests.get(t.bestKey(duo));
		if (old == null || ticks < old) {
			p.trialBests.put(t.bestKey(duo), ticks);
			p.sendMessage("<col=005f00>New personal best!</col>" + (old == null ? "" : " (was " + formatTicks(old) + ")"));
		}
	}

	static String best(Player p, Trial t, boolean duo) {
		Integer b = p.trialBests == null ? null : p.trialBests.get(t.bestKey(duo));
		return b == null ? "-" : formatTicks(b);
	}

	static String formatTicks(int ticks) {
		int tenths = ticks * 6;
		return (tenths / 600) + ":" + String.format("%02d", tenths / 10 % 60) + "." + tenths % 10;
	}

	private static HooksV2.Result command(CommandHandler.Hook.Handle cmd) {
		Player player = cmd.player();
		String[] args = cmd.args();
		switch (cmd.command()) {
			case "colosseum" -> {
				if (blocked(player, false) != null || player.wildernessLevel > 0) {
					player.sendMessage("You can't do that from here.");
					return HooksV2.Result.Return;
				}
				player.getMovement().startTeleport(e -> {
					player.animate(714);
					player.graphics(111, 92, 0);
					e.delay(3);
					player.getMovement().teleport(LOBBY);
				});
			}
			case "trialmarks" -> player.sendMessage("You hold " + player.trialMarks + " Trial Marks.");
			case "trial" -> {
				if (!player.isAdmin())
					return HooksV2.Result.Pass;
				Trial t = args == null || args.length == 0 ? null : find(args[0]);
				if (t == null) {
					player.sendMessage("::trial <" + String.join("|", Arrays.stream(Trial.values()).map(Enum::name).toList()).toLowerCase() + ">");
					return HooksV2.Result.Return;
				}
				String why = blocked(player, false);
				if (why != null)
					player.sendMessage(why);
				else
					start(t, List.of(player));
			}
			case "givetrialmarks" -> {
				if (!player.isAdmin())
					return HooksV2.Result.Pass;
				int n = args == null || args.length == 0 ? 100 : Integer.parseInt(args[0]);
				player.trialMarks += n;
				player.sendMessage("Trial Marks: " + player.trialMarks);
			}
			default -> {
				return HooksV2.Result.Pass;
			}
		}
		return HooksV2.Result.Return;
	}

	private static Trial find(String s) {
		for (Trial t : Trial.values())
			if (t.name().equalsIgnoreCase(s) || t.name().toLowerCase().startsWith(s.toLowerCase()))
				return t;
		return null;
	}
}
