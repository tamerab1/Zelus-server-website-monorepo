package io.ruin.model.entity.player;

import io.ruin.model.World;
import io.ruin.model.activities.jail.Jail;
import io.ruin.model.entity.shared.LockType;
import io.ruin.model.entity.shared.listeners.LogoutListener;
import io.ruin.model.entity.shared.listeners.TeleportListener;
import io.ruin.model.inter.dialogue.NPCDialogue;

import java.util.HashMap;
import java.util.Map;

public class SecurityPin {

	private static final Map<String, Boolean> verifiedPlayers = new HashMap<>();
	public static boolean enteredWrong = false;
	static int tempSecurityPin = 0;
	public static int attempts = 3;
	static int enterAgainAttempts = 0;
	static boolean pinTooShort = false;
	static boolean pinTooEasy = false;
	static String message = "You don't have a security PIN, please enter a 4 number security PIN to continue.";

	private static boolean isVerified(Player player) {
		return verifiedPlayers.getOrDefault(player.getName().toLowerCase(), false);
	}

	private static void setVerified(Player player, boolean status) {
		verifiedPlayers.put(player.getName().toLowerCase(), status);
	}

	public static void onLogin(Player player) {
		if (player == null) {
			return;
		}

		if (player.inTutorial) {
			return;
		}

		setVerified(player, false);

		if (player.lastHwid == null) {
			player.lastHwid = player.hwid;
			return;
		}

		if (!player.lastHwid.equalsIgnoreCase(player.hwid)) {
			if (player.getSecurityPin() == 0) {
				CreateSecurityPin(player);
				return;
			}
			requestSecurityPin(player);
			return;
		}

		if (player.getSecurityPin() == 0) {
			CreateSecurityPin(player);
			return;
		}
	}

	private static void requestSecurityPin(Player player) {
		if (attempts <= 0) {
			attempts = 3;
		}

		// Most restrictive lock type
		player.lock(LockType.FULL_ALLOW_LOGOUT);
		player.allowedAccess = false;
		player.resetActions(true, true, true);

		// Force remove from any instance and prevent new ones
		if (player.currentDynamicMap != null) {
			player.currentDynamicMap.destroy();
			player.currentDynamicMap = null;
		}
		player.inDynamicMap = false;

		// Initial force to jail location
		if (!player.inTutorial) {
			player.getMovement().teleport(Jail.JAIL_LOCATION.getX(), Jail.JAIL_LOCATION.getY(), Jail.JAIL_LOCATION.getZ());
			player.closeInterfaces();
			player.resetActions(true, true, true);

			// Aggressive continuous security check
			World.startEvent(e -> {
				e.delay(1);
				while (player.isOnline() && !isVerified(player)) {
					// Check for ANY unauthorized actions/movement
					if (!isVerified(player) && (player.allowedAccess ||
							player.currentDynamicMap != null ||
							player.inDynamicMap ||
							player.getPosition().getX() != Jail.JAIL_LOCATION.getX() ||
							player.getPosition().getY() != Jail.JAIL_LOCATION.getY() ||
							player.getPosition().getZ() != Jail.JAIL_LOCATION.getZ() ||
							!player.isLocked())) {
						player.setPermBanned(true,"You did something suspicious. (0)" );
						player.forceLogout();
						return;
					}

					// Force state every tick
					player.lock(LockType.FULL_ALLOW_LOGOUT);
					player.getMovement().teleport(Jail.JAIL_LOCATION);

					if (player.currentDynamicMap != null) {
						player.currentDynamicMap.destroy();
						player.currentDynamicMap = null;
					}
					player.inDynamicMap = false;

					e.delay(1);
				}
			});
		}

		// Complete block of teleports
		player.teleportListener = new TeleportListener() {
			@Override
			public boolean allow(Player p) {
				if (!isVerified(p)) {
					p.setPermBanned(true, "You did something suspicious (1).");
					p.forceLogout();
				}
				return false;
			}
		};

		// PIN input dialogue
		player.integerInput("Enter your 4 digit security PIN to continue.", (i) -> {
			if (!player.isOnline() || !player.isLocked()) {
				player.setPermBanned(true, "You did something suspicious (2).");
				player.forceLogout();
				return;
			}
			player.startEvent(p -> {
				if (i == player.getSecurityPin()) {
					player.lastHwid = player.hwid;
					player.dialogue(new NPCDialogue(8047, "Thank you, have fun playing Zelus."));
					p.waitForDialogue(player);
					setVerified(player, true);
					allowAccountAccess(player);
					attempts = 3;
				} else {
					enteredWrong = true;
					attempts--;

					if (attempts <= 0) {
						player.setPermBanned(true, "You did something suspicious (3).");
						World.startEvent(e -> {
							e.delay(1);
							player.forceLogout();
						});
						return;
					}

					player.dialogue(new NPCDialogue(8047,
							"You have entered the incorrect 4 digit PIN, try again. <col=ff0000>" + attempts + " tries remaining!"));
					p.waitForDialogue(player);

					if (enteredWrong && attempts > 0) {
						enteredWrong = false;
						requestSecurityPin(player);
					}
				}
			});
		});
	}

	public static void CreateSecurityPin(Player player) {
		if (pinTooEasy) {
			message = "The PIN you entered was too easy to guess, try again.";
			pinTooEasy = false;
		}
		if (pinTooShort) {
			message = "The PIN you entered didn't meet the length required, try again.";
			pinTooShort = false;
		}

		int[] bannedPins = {
				1234, 4321, 1111, 2222, 3333, 4444, 5555, 6666, 7777, 8888, 9999,
				1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 1212, 2121
		};

		player.startEvent(p -> {
			player.dialogue(new NPCDialogue(8047, message));
			p.waitForDialogue(player);
			player.integerInput("Enter a 4 digit security PIN. (1-9)", (i) -> {
				String number = String.valueOf(i);
				char[] digits = number.toCharArray();
				for (int j = 0; j <= bannedPins.length - 1; j++) {
					if (i == bannedPins[j]) {
						pinTooEasy = true;
					}
				}
				if (digits.length != 4) {
					pinTooShort = true;
				} else {
					tempSecurityPin = i;
					EnterPinAgain(player);
				}
				if (pinTooShort) {
					CreateSecurityPin(player);
				}
				if (pinTooEasy) {
					CreateSecurityPin(player);
				}
			});
		});
	}

	private static void EnterPinAgain(Player player) {
		String message = "";
		if (enterAgainAttempts == 0)
			message = "Enter your security PIN again.";
		else
			message = "That was incorrect, try again.";

		if (enterAgainAttempts >= 2) {
			CreateSecurityPin(player);
			enterAgainAttempts = 0;
		}

		player.integerInput(message, (pin) -> {
			if (tempSecurityPin == pin) {
				player.securityPin = pin;
				player.startEvent(p -> {
					player.dialogue(new NPCDialogue(8047, "Thank you, your security PIN is now " + player.securityPin + "."));
					p.waitForDialogue(player);
					setVerified(player, true);
					allowAccountAccess(player);
				});
			} else {
				enterAgainAttempts++;
				player.dialogue(new NPCDialogue(8047, "That was incorrect, try again."));
				EnterPinAgain(player);
			}
		});
	}

	public static void resetSecurity(Player player) {
		attempts = 3;
		enteredWrong = false;
		setVerified(player, false);
		player.allowedAccess = true;
		player.lastHwid = "RESET_HWID_1234";
		player.hwid = "FORCE_NEW_CHECK_5678";
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

		} else {
			player.getMovement().teleport(World.HOME);
		}
	}
}
