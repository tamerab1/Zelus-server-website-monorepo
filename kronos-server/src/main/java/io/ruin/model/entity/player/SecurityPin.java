package io.ruin.model.entity.player;

import io.ruin.model.World;
import io.ruin.model.activities.jail.Jail;
import io.ruin.model.entity.shared.LockType;
import io.ruin.model.entity.shared.listeners.TeleportListener;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Position;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security PIN: optional, set during onboarding or later with ::changesecuritypin.
 * <p>
 * A player WITH a PIN who logs in from a new device (hardware id) is held in the jail cell until they enter it; a
 * player without one is never prompted or held. While held, the PIN prompt re-opens on its own if it gets closed, and
 * ::pin or clicking the cell gate opens it too, so the account can't get stuck. The right PIN puts them back where
 * they were (saved in {@link PlayerAttributes#securityPinReturnPosition}, so it survives a logout in the cell).
 * <p>
 * All state is per player. It used to be static and shared, so one player's wrong guesses counted against everyone
 * (three in total banned whoever was next) and two players setting a PIN at once could confirm each other's.
 */
public class SecurityPin {

	private static final int ATTEMPTS = 3;
	/** while held: re-open the prompt this often if no answer is pending */
	private static final int REPROMPT_TICKS = 10;
	private static final int[] TOO_EASY = {
		1234, 4321, 1111, 2222, 3333, 4444, 5555, 6666, 7777, 8888, 9999,
		1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 1212, 2121
	};

	/** per-player, online only */
	private static final class State {
		boolean held;          // in the cell waiting for the PIN
		boolean answering;     // a prompt or the result dialogue is showing
		int attempts = ATTEMPTS;
		int newPin;            // first entry while setting a PIN
		int confirmMisses;
	}

	private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUserId(), k -> new State());
	}

	/** True while the player is held for their PIN. */
	public static boolean isHeld(Player player) {
		State s = STATES.get(player.getUserId());
		return s != null && s.held;
	}

	public static void onLogin(Player player) {
		if (player == null || player.inTutorial)
			return;
		STATES.remove(player.getUserId());
		boolean newDevice = player.lastHwid != null && player.hwid != null && !player.lastHwid.equalsIgnoreCase(player.hwid);
		if (player.getSecurityPin() == 0) {
			// no PIN: nothing to check (it's optional); just remember this device. If staff cleared the PIN while
			// they were held (::unban), they log in standing in the cell: send them back.
			player.lastHwid = player.hwid;
			if (player.securityPinReturnPosition != null) {
				player.getMovement().teleport(player.securityPinReturnPosition);
				player.securityPinReturnPosition = null;
			}
			return;
		}
		if (player.lastHwid == null) {
			player.lastHwid = player.hwid;
			return;
		}
		if (newDevice || player.securityPinReturnPosition != null)
			hold(player);
	}

	public static void onLogout(Player player) {
		STATES.remove(player.getUserId());
	}

	/** Holds the player in the cell until the PIN is entered. */
	private static void hold(Player player) {
		State s = state(player);
		s.held = true;
		s.attempts = ATTEMPTS;
		if (player.securityPinReturnPosition == null && !atJail(player))
			player.securityPinReturnPosition = player.getPosition().copy();
		player.lock(LockType.FULL_ALLOW_LOGOUT);
		player.allowedAccess = false;
		player.resetActions(true, true, true);
		if (player.currentDynamicMap != null) {
			player.currentDynamicMap.destroy();
			player.currentDynamicMap = null;
		}
		player.inDynamicMap = false;
		player.closeInterfaces();
		player.getMovement().teleport(Jail.JAIL_LOCATION);
		player.teleportListener = new TeleportListener() {
			@Override
			public boolean allow(Player p) {
				p.sendMessage("Enter your security PIN first.");
				return false;
			}
		};
		player.sendMessage("<col=ff0000>New device detected. Enter your security PIN to continue (type ::pin if the box closes).");

		// keep them in the cell and keep the prompt available until verified or logged out
		World.startEvent(e -> {
			int sinceAsked = REPROMPT_TICKS;
			while (player.isOnline() && s.held) {
				if (!player.isLocked())
					player.lock(LockType.FULL_ALLOW_LOGOUT);
				if (!atJail(player))
					player.getMovement().teleport(Jail.JAIL_LOCATION);
				if (!s.answering && ++sinceAsked >= REPROMPT_TICKS) {
					sinceAsked = 0;
					prompt(player);
				}
				e.delay(1);
			}
		});
	}

	private static boolean atJail(Player player) {
		Position p = player.getPosition();
		return p.getX() == Jail.JAIL_LOCATION.getX() && p.getY() == Jail.JAIL_LOCATION.getY() && p.getZ() == Jail.JAIL_LOCATION.getZ();
	}

	/** Opens the PIN prompt for a held player (also ::pin and the cell gate). */
	public static void prompt(Player player) {
		State s = STATES.get(player.getUserId());
		if (s == null || !s.held) {
			player.sendMessage("You don't need to enter your security PIN right now.");
			return;
		}
		s.answering = true;
		player.integerInput("Enter your 4 digit security PIN to continue.", pin -> {
			s.answering = false;
			if (!s.held || !player.isOnline())
				return;
			if (pin == player.getSecurityPin()) {
				release(player, s);
				return;
			}
			s.attempts--;
			if (s.attempts <= 0) {
				player.setPermBanned(true, "Too many wrong security PIN attempts.");
				World.startEvent(e -> {
					e.delay(1);
					player.forceLogout();
				});
				return;
			}
			s.answering = true;
			player.startEvent(e -> {
				player.dialogue(new NPCDialogue(io.ruin.model.entity.npc.actions.edgeville.StarterGuide.WARDEN_NPC,
					"That's not your security PIN. <col=ff0000>" + s.attempts + " tries remaining!"));
				e.waitForDialogue(player);
				s.answering = false;
				prompt(player);
			});
		});
	}

	private static void release(Player player, State s) {
		s.held = false;
		player.lastHwid = player.hwid;
		Position back = player.securityPinReturnPosition;
		player.securityPinReturnPosition = null;
		player.unlock();
		player.teleportListener = null;
		player.logoutListener = null;
		player.allowedAccess = true;
		player.getMovement().teleport(back != null ? back : World.HOME);
		player.dialogue(new NPCDialogue(io.ruin.model.entity.npc.actions.edgeville.StarterGuide.WARDEN_NPC, "Thank you, have fun playing Zelus."));
	}

	// ---------------------------------------------------------------- onboarding

	/** End of the starter tutorial: setting a PIN is offered, not required. */
	public static void offerSetup(Player player) {
		player.dialogue(
			new NPCDialogue(io.ruin.model.entity.npc.actions.edgeville.StarterGuide.WARDEN_NPC,
				"You can protect your account with a 4 digit security PIN. It's asked for when you log in from a new"
					+ " device. You can also set one later with ::changesecuritypin."),
			new OptionsDialogue("Set a security PIN now?",
				new Option("Yes, set a security PIN.", () -> CreateSecurityPin(player)),
				new Option("Not now.", () -> {
					player.sendMessage("No security PIN set. You can add one any time with ::changesecuritypin.");
					allowAccountAccess(player);
				})));
	}

	/** Asks for a new PIN twice, then sets it (onboarding, and ::changesecuritypin after the current PIN). */
	public static void CreateSecurityPin(Player player) {
		CreateSecurityPin(player, "Enter a 4 digit security PIN.");
	}

	private static void CreateSecurityPin(Player player, String message) {
		State s = state(player);
		player.integerInput(message, pin -> {
			if (String.valueOf(pin).length() != 4) {
				CreateSecurityPin(player, "Your PIN must be exactly 4 digits (1-9). Try again.");
				return;
			}
			for (int easy : TOO_EASY)
				if (pin == easy) {
					CreateSecurityPin(player, "That PIN is too easy to guess. Try another.");
					return;
				}
			s.newPin = pin;
			s.confirmMisses = 0;
			confirmPin(player, "Enter your security PIN again.");
		});
	}

	private static void confirmPin(Player player, String message) {
		State s = state(player);
		player.integerInput(message, pin -> {
			if (pin != s.newPin) {
				if (++s.confirmMisses >= 2) {
					CreateSecurityPin(player, "Those didn't match. Enter a new 4 digit security PIN.");
					return;
				}
				confirmPin(player, "That didn't match, try again.");
				return;
			}
			player.securityPin = pin;
			player.lastHwid = player.hwid;
			if (!player.inTutorial) { // ::changesecuritypin
				player.sendMessage("Your security PIN has been set.");
				return;
			}
			player.startEvent(e -> {
				player.dialogue(new NPCDialogue(io.ruin.model.entity.npc.actions.edgeville.StarterGuide.WARDEN_NPC,
					"Thank you, your security PIN is now set."));
				e.waitForDialogue(player);
				allowAccountAccess(player);
			});
		});
	}

	/** Staff reset: clears the PIN and its device memory, and frees a held player. */
	public static void resetSecurity(Player player) {
		State s = STATES.remove(player.getUserId());
		player.securityPin = 0;
		player.lastHwid = player.hwid;
		if (s != null && s.held && player.isOnline())
			release(player, s); // online: frees them now; offline: onLogin sends them back to securityPinReturnPosition
		player.allowedAccess = true;
	}

	private static void allowAccountAccess(Player player) {
		player.unlock();
		player.logoutListener = null;
		player.teleportListener = null;
		player.allowedAccess = true;
		if (player.inTutorial) {
			if (player.getGameMode() == GameMode.GROUP_IRONMAN || player.getGameMode() == GameMode.HARDCORE_GROUP_IRONMAN) {
				player.getMovement().teleport(3761, 3668, 0);
				player.sendMessage("You must join or form a group before leaving the island!");
			}
			player.inTutorial = false;
			player.dialogue(new NPCDialogue(7958,
					"Welcome to Zelus. The perk ticket in your inventory will allow you claim a free level 5 perk.")
				.action(() -> {
					if (!World.isLive())
						player.join(PlayerGroup.OWNER);
				}));
		}
	}
}
