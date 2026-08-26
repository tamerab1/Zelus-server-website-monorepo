package io.ruin.model.entity.npc.actions.edgeville;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import discord.webhooks.logs.PlayerCreation;
import io.ruin.HooksV2;
import io.ruin.Server;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.ItemID;
import io.ruin.data.impl.Help;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Difficulty;
import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.SecurityPin;
import io.ruin.model.entity.shared.LockType;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.XpCounter;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.utility.Broadcast;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.ruin.cache.ItemID.COINS_995;

@Slf4j
public class StarterGuide {
	public static interface Hook {
		record Finished(Player player, GameMode mode) implements Hook {};
	}

	public static HooksV2<Hook> hooks = new HooksV2<>(Hook.class);
	// IP -> number of accounts on that IP that have already received the tradeable STANDARD-mode
	// starter kit (500k coins, thousands of runes, etc). Capped so alt-account farming can't be
	// used to funnel unlimited free starter wealth into a main account via trading.
	public static final int MAX_STARTER_CLAIMS_PER_IP = 5;
	public static java.util.Map<String, Integer> ipClaimCounts = new java.util.HashMap<>();

	// Accounts under this playtime are still plausibly "just registered" -- used only to gate
	// the one-time login heal below so it can't retroactively fire for a long-established
	// account that legitimately has starterKitGranted=false just because that field is new.
	private static final long HEAL_ELIGIBLE_PLAYTIME_SECONDS = 3600;

	public static void register() {
		loadIps();
		LoginListener.register(player -> {
			if (player.newPlayer) {
				player.hasFreePerkUnlock = true;
				XpCounter.select(player, 1);
				tutorial(player);
			} else if (!player.starterKitGranted) {
				// starterKitGranted is a new field -- every pre-existing account reads false for
				// it once, regardless of whether they're a day-one veteran or someone who hit the
				// interrupted-mid-tutorial bug this guards against. Recently-created accounts get
				// healed (their missed kit granted for real); anything older is just marked so
				// this branch never re-checks it, no assumptions made about what an old account
				// "should" have.
				if (player.playTime < HEAL_ELIGIBLE_PLAYTIME_SECONDS) {
					grantStarterKitIfNeeded(player);
					player.sendMessage("<col=FF0000>Looks like your account setup got interrupted last time -- here's the starter kit you missed!");
				} else {
					player.starterKitGranted = true;
				}
			}
		});
	}

	private static void optionsDialogue(Player player, NPC npc) {
		player.dialogue(new NPCDialogue(npc, "Hello " + player.getName() + ", is there something I could assist you with?"),
				new OptionsDialogue(
						new Option("View help pages", () -> Help.open(player)),
						// new Option("Replay tutorial", () -> ecoTutorial(player)),
						new Option("Change home point", () -> {
							npc.startEvent(event -> {
								if (!player.edgeHome) {
									player.dialogue(new NPCDialogue(npc, "I can move your spawn point and <br>" +
											"home teleport location to Edgeville.<br>" +
											"It will cost 5,000,000 GP.<br>" +
											"Would you like to do this?"),
											new OptionsDialogue(
													new Option("No thanks, I like this home.", player::closeDialogue),
													new Option("Certainly! I would like to respawn in Edgeville.", () -> {
														if (player.getInventory().hasItem(995, 5000000)) {
															player.getInventory().remove(995, 5000000);
															player.edgeHome = true;
															player.dialogue(new NPCDialogue(npc, "Your spawn point has been changed<br>" +
																	"to Edgeville! If you'd like to change<br>" +
																	"it back, just speak with me again."));
														} else {
															player.dialogue(new NPCDialogue(npc, "I'm sorry, but it doesn't look like<br>" +
																	"you can afford this."));
														}
													})));
								} else {
									player.dialogue(
											new NPCDialogue(npc, "Are you wanting to move your<br>" +
													"spawn point back to home? It will cost<br>" +
													"another 5,000,000 GP."),
											new OptionsDialogue(
													new Option("No thanks.", player::closeDialogue),
													new Option("Yes please!", () -> {
														if (player.getInventory().hasItem(995, 5000000)) {
															player.getInventory().remove(995, 5000000);
															player.edgeHome = false;
															player.dialogue(new NPCDialogue(npc, "Your spawn point has been changed<br>" +
																	"back to home. If you'd like it changed,<br>" +
																	"just speak with me again!"));
														} else {
															player.dialogue(new NPCDialogue(npc, "I'm sorry, but it doesn't look like<br>" +
																	"you can afford this."));
														}
													})));
								}
							});
						})));
	}

	// Generous cap on how long we'll wait for the player to close the makeover/mode-select
	// interfaces (100 ticks = 60s) -- long enough for genuine customization, but bounded so
	// a stuck/unresponsive client can't leave the new account locked indefinitely.
	private static final int MAX_INTERFACE_WAIT_TICKS = 100;

	@SneakyThrows
	public static void ecoTutorial(Player player) {
		boolean actuallyNew = player.newPlayer;
		player.inTutorial = true;
		World.startEvent(event -> {

			player.lock(LockType.FULL_ALLOW_LOGOUT);
			player.getMovement().teleport(3085, 3492, 0);
			if (actuallyNew) {
				player.openInterface(ToplevelComponent.MAINMODAL, Interface.MAKE_OVER_MAGE);
				player.getPacketSender().sendIfEvents(679, 78, 0, 4, new int[] { 1 << 1 });
				int waitTicks = 0;
				while (player.isVisibleInterface(Interface.MAKE_OVER_MAGE) && waitTicks++ < MAX_INTERFACE_WAIT_TICKS) {
					event.delay(1);
				}
				if (player.isVisibleInterface(Interface.MAKE_OVER_MAGE)) {
					player.closeInterfaces();
				}
			}

			// EDGE_CASE: player might've logged out
			if (!player.isOnline()) {
				return;
			}

			if (actuallyNew) {
				player.getGameModeInterface().openIronmanSettingsInterface(player);
				int waitTicks = 0;
				while (player.isVisibleInterface(1100) && waitTicks++ < MAX_INTERFACE_WAIT_TICKS) {
					event.delay(1);
				}
				if (player.isVisibleInterface(1100)) {
					player.closeInterfaces();
				}
			}

		});
	}

	// Grants the appropriate starter kit and finalizes the account. Split out of continueTutorial
	// and called synchronously, BEFORE any dialogue is shown -- previously this only happened
	// deep inside a dialogue-continuation callback, so a disconnect anywhere in that dialogue
	// chain (client crash, closed window, bad connection) left the account permanently in a
	// "mode picked, no kit, newPlayer still true forever" state with no recovery path. Idempotent
	// via starterKitGranted so a caller can't accidentally double-grant.
	public static void grantStarterKitIfNeeded(Player player) {
		if (player.starterKitGranted)
			return;
		if (player.getGameMode().isIronMan()) {
			giveEcoStarter(player);
		} else if (claimLimitReached(player)) {
			player.sendMessage("This IP has already claimed the starter pack on "
					+ MAX_STARTER_CLAIMS_PER_IP + " accounts, so you won't receive it again.");
		} else if (player.getPlayMode() == PlayMode.PVP_MODE) {
			givePvpModeStarter(player);
			recordClaim(player.getIp());
		} else {
			giveEcoStarter(player);
		}
		player.starterKitGranted = true;
		player.newPlayer = false;
	}

	@SneakyThrows
	public static void continueTutorial(Player player) {
		grantStarterKitIfNeeded(player);
		AtomicBoolean startTutorial = new AtomicBoolean(false);
		player.startEvent(event -> {
			String text = "You've chosen to play an account with no restrictions, good luck!";
			if (player.getGameMode() == GameMode.IRONMAN) {
				text = "You've chosen an account with ironman restrictions; I hope RNG is on your side!";
			} else if (player.getGameMode() == GameMode.HARDCORE_IRONMAN) {
				text = "You've chosen an account with hardcore ironman restrictions; stay alive at all costs!";
			} else if (player.getGameMode() == GameMode.ULTIMATE_IRONMAN) {
				text = "You've chosen an account with ultimate ironman restrictions; I hope you have a plan!";
			} else if (player.getGameMode() == GameMode.GROUP_IRONMAN) {
				text = "You've chosen group ironman restrictions; I hope your friends are up for the task!";
			} else if (player.getGameMode() == GameMode.HARDCORE_GROUP_IRONMAN) {
				text = "You've chosen hardcore group ironman restrictions; hopefully you picked the right people!";
			}

			if (player.getGameMode().isIronMan()) {
				player.dialogue(new NPCDialogue(3525, text),
						new NPCDialogue(3525, "Before you start, I'll give you items to start your ironman adventure."),
						new NPCDialogue(3525, "And you're all set, good luck on your journey!"));
			} else {
				player.dialogue(new NPCDialogue(3525, text),
						new NPCDialogue(3525, "Before you start, I'll give you some items to start your adventure."),
						new NPCDialogue(3525, "If you need any other items, be sure to check out the shops!"));
			}

			event.waitForDialogue(player);
			Broadcast.WORLD.sendNews(player.getName() + " has just joined " + World.type.getWorldName()
					+ ". For an early boost, use code ::zelus!");
			// Direct message to the new player themselves -- the world broadcast above
			// announces them to everyone else, but is easy for the new player to miss in
			// a scrolling chatbox and reads as third-person, not an instruction to them.
			player.sendMessage("<col=FF9900>Don't forget to type <col=FF0000>::zelus<col=FF9900> to claim your referral rewards!");
			startTutorial.set(true);
//			PlayerCreationWebhook.sendAccountCreationHook(player);

			var dto = new JSONObject()
				.put("player", player.getName())
				.put("hwid", player.hwid);

			PlayerCreation.createAnsSendWebhookMessageWithEmbed(dto);

			if (startTutorial.get()) {
				player.dialogue(new NPCDialogue(3531,
						"Ah, I've been expecting you, " + player.getName() + "! Welcome to " + World.type.getWorldName()
								+ " I can give you a quick tutorial, or you can just start playing if you'd prefer!"),
						new OptionsDialogue("Show the tutorial?",
								new Option("Show me the ropes.", () -> introCutscene(player)),
								new Option("I've got this!", () -> {
									player.closeDialogue();
									player.logoutListener = null;
									VarPlayerRepository.ESCAPE_CLOSES.toggle(player);
									player.tutorialStage = 0;
									SecurityPin.CreateSecurityPin(player);
									if (hooks.handle(new Hook.Finished(player, player.getGameMode()))) {
										return;
									}
								})));
			}
		});
	}

	private static void introCutscene(Player player) {

		player.startEvent((e) -> {
			player.getPacketSender().sendClientScript(39, "i", 100);
			VarPlayerRepository.LOCK_CAMERA.set(player, 1);
			player.dialogue(new NPCDialogue(3531,
					"Welcome to Zelus, where you'll embark on an unforgettable journey! Expect nothing less than an unparalleled experience. Best of luck on your adventure!"));
			e.waitForDialogue(player);

			player.getMovement().teleport(3089, 3511, 0);
			e.delay(1);

			player.dialogue(new NPCDialogue(3531,
					"Starting off with Zelus's main bank! Here, you can take care of all your banking needs."));
			e.waitForDialogue(player);
			player.getMovement().teleport(3115, 3486, 0);
			e.delay(2);
			player.dialogue(new NPCDialogue(3531,
					"Exhibit two, the Trading Post & Upgrade Station! This bustling marketplace is where players can buy and sell all kinds of items.<br>"
							+
							"Think of it as Zelus's version of the Grand Exchange."));
			e.waitForDialogue(player);
			player.dialogue(new NPCDialogue(3531,
					"Right next to the Upgrade Station is the Item Workbench, where you will be able to place perks onto your equipment with minerals and enhancers from breaking down items."));
			e.waitForDialogue(player);
			e.delay(2);
			// construction portal
			player.getMovement().teleport(3089, 3475, 0);
			player.dialogue(new NPCDialogue(3531,
					"Now to make you feel at home... the POH portal! This is where you can begin building your dream estate. Visit the estate agent to purchase a house..."));
			e.waitForDialogue(player);
			e.delay(2);

			player.getMovement().teleport(3096, 3512, 0);

			player.dialogue(new NPCDialogue(3531,
					"Zelus's central shops! Here you'll find all the basic shops to assist your journey. Ironman players also have access to some of the shops, so be sure to explore everything this marketplace has to offer."));
			e.waitForDialogue(player);
			e.delay(2);

			player.getMovement().teleport(3090, 3486, 0);

			player.dialogue(new NPCDialogue(3531,
					"Here, we have the Zelus teleport nexus.<br>" +
							"Interacting with the nexus will open the teleport menu. Once the menu is open, select one of the many options and you'll be transported there."));
			e.waitForDialogue(player);
			e.delay(2);
			player.getMovement().teleport(3105, 3513, 0);
			player.dialogue(new NPCDialogue(3531,
					"Right behind you is the healing pool! This pool will rid you of any status conditions and heal you completely. You'll also be healed when you teleport home."));
			e.waitForDialogue(player);
			player.getMovement().teleport(3105, 3488, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"Feeling belligerent already? Look no further than the Slayer Masters, who can assign you tasks. You can also loot the crystal, larran's, brimstone, and slayer chests here."));
			e.waitForDialogue(player);
			player.getMovement().teleport(3116, 3487, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"This is where you will find the perk master. Talk to him for information about obtaining and equipping perks! Perks play a huge role in Zelus, so check them out."));
			e.waitForDialogue(player);
			player.getMovement().teleport(3083, 3502, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"A little fairy magic here. Take full advantage of this resource!"));
			e.waitForDialogue(player);
			player.getMovement().teleport(3094, 3463, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"Here is the Camel Statue, where certain boosts can be activated by offering gold. Note: These are global boosts. There is also a thieving area to the left!"));
			e.waitForDialogue(player);
			player.getMovement().teleport(3078, 3484, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"And finally, here are the point store NPCs! Spend your various points here."));
			e.waitForDialogue(player);
			e.delay(1);

			player.getMovement().teleport(3123, 3479, 0);
			e.delay(1);
			player.dialogue(new NPCDialogue(3531,
					"And here's the gambling zone! Feeling lucky? Step inside and try your hand -- just remember, gambling carries the risk of losing whatever you stake."));
			e.waitForDialogue(player);
			e.delay(1);

			// player.getPacketSender().moveCameraToLocation(2064, 3583, 1000, 0, 12);
			// player.getPacketSender().turnCameraToLocation(2062, 3570, 0, 0, 30);
			// e.delay(1);
			// player.dialogue(new NPCDialogue(guide,
			// "Last, but not least, there is a skilling area to the east of home just over
			// the bridge. Fishing is also available to the north."));
			// e.delay(10);
			// player.getPacketSender().moveCameraToLocation(2064, 3572, 1200, 0, 12);
			// player.getPacketSender().turnCameraToLocation(2064, 3590, 0, 0, 32);
			// e.waitForDialogue(player);
			VarPlayerRepository.LOCK_CAMERA.set(player, 0);
			player.getPacketSender().resetCamera();
			player.tutorialStage = 1;

			player.getMovement().teleport(3092, 3494, 0);
			player.getPacketSender().moveCameraToLocation(3092, 3494, 450, 0, 12);
			player.getPacketSender().turnCameraToLocation(3092, 3494, 400, 0, 30);
			player.dialogue(new NPCDialogue(3531,
					"Looks like you're ready to begin your adventure, good luck!"));
			e.waitForDialogue(player);
			e.delay(3);

			VarPlayerRepository.ESCAPE_CLOSES.toggle(player);
			SecurityPin.CreateSecurityPin(player);
			player.tutorialStage = 0;
			player.getPacketSender().resetCamera();
		});
	}

	/** True once this IP has already claimed the STANDARD-mode starter kit on MAX_STARTER_CLAIMS_PER_IP accounts. */
	public static boolean claimLimitReached(Player player) {
		return ipClaimCounts.getOrDefault(player.getIp(), 0) >= MAX_STARTER_CLAIMS_PER_IP;
	}

	public static void recordClaim(String ip) {
		ipClaimCounts.merge(ip, 1, Integer::sum);
		saveIps();
	}

	public static void saveIps() {
		Server.executeAsync(() -> {
			File file = new File(ServerWrapper.dataFolder, "/runtime/starter_pack_ips.json");
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				FileWriter fileWriter = new FileWriter(file);
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String toJson = gson.toJson(ipClaimCounts);
				fileWriter.write(toJson);
				fileWriter.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	public static void loadIps() {
		File file = new File(ServerWrapper.dataFolder, "/runtime/starter_pack_ips.json");
		if (!file.exists()) {
			try {
				file.createNewFile();
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
			if (json.isBlank())
				return;
			if (json.trim().startsWith("[")) {
				// migrate the old format (a plain list of IPs that had claimed once) -- each
				// existing entry already used up one of its two claims, not zero.
				Type oldType = new TypeToken<ArrayList<String>>() {
				}.getType();
				ArrayList<String> oldIps = gson.fromJson(json, oldType);
				if (oldIps != null)
					for (String ip : oldIps)
						ipClaimCounts.put(ip, 1);
				log.info("Migrated " + ipClaimCounts.size() + " starter pack ips from the old one-claim format.");
				saveIps();
			} else {
				Type type = new TypeToken<java.util.HashMap<String, Integer>>() {
				}.getType();
				java.util.Map<String, Integer> temp = gson.fromJson(json, type);
				if (temp != null)
					ipClaimCounts = temp;
				log.info("Loaded " + ipClaimCounts.size() + " starter pack ip claim counts.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void giveEcoStarter(Player player) {
		player.getInventory().add(30035);
		player.getInventory().add(30496);
		player.getInventory().add(30477);
		player.getInventory().add(30588);
		player.getInventory().add(30589);
		if (player.getDifficulty() == Difficulty.EXTREME) {
			Item trainingStaff = new Item(30480);
			AttributeExtensions.addCharges(trainingStaff, 10000);
			Item trainingBow = new Item(30478);
			AttributeExtensions.addCharges(trainingBow, 10000);
			Item trainingSword = new Item(30479);
			AttributeExtensions.addCharges(trainingSword, 10000);
			player.getInventory().add(trainingStaff);
			player.getInventory().add(trainingBow);
			player.getInventory().add(trainingSword);
		}
		switch (player.getGameMode()) {
			case IRONMAN:
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(12810, 1);
				player.getInventory().add(12811, 1);
				player.getInventory().add(12812, 1);
				player.getInventory().add(1323, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(new Item(ItemID.LOBSTER + 1, 300));
				break;
			case HARDCORE_IRONMAN:
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(20792, 1);
				player.getInventory().add(20794, 1);
				player.getInventory().add(20796, 1);
				player.getInventory().add(1323, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(new Item(ItemID.LOBSTER + 1, 300));
				break;
			case ULTIMATE_IRONMAN:
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(12813, 1);
				player.getInventory().add(12814, 1);
				player.getInventory().add(12815, 1);
				player.getInventory().add(1323, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(new Item(ItemID.LOBSTER + 1, 300));
				break;
			case GROUP_IRONMAN:
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(26156, 1);
				player.getInventory().add(26158, 1);
				player.getInventory().add(26166, 1);
				player.getInventory().add(1323, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(new Item(ItemID.LOBSTER + 1, 300));
				break;
			case HARDCORE_GROUP_IRONMAN:
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(26170, 1);
				player.getInventory().add(26172, 1);
				player.getInventory().add(26180, 1);
				player.getInventory().add(1323, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(new Item(ItemID.LOBSTER + 1, 300));
				break;
			case STANDARD:
				// Per-IP claim limit for this (and the PVP_MODE kit) is enforced by the caller
				// in continueTutorial(), before this method is ever invoked for STANDARD mode.
				player.getInventory().add(COINS_995, 500000);
				player.getInventory().add(562, 500);
				player.getInventory().add(558, 1000);
				player.getInventory().add(554, 10000);
				player.getInventory().add(557, 10000);
				player.getInventory().add(555, 10000);
				player.getInventory().add(556, 10000);
				player.getInventory().add(884, 10000);
				player.getInventory().add(ItemID.STAFF_OF_AIR, 1);
				player.getInventory().add(6108, 1);
				player.getInventory().add(6107, 1);
				player.getInventory().add(6109, 1);
				player.getInventory().add(841, 1);
				player.getInventory().add(1095, 1);
				player.getInventory().add(1129, 1);
				player.getInventory().add(1167, 1);
				player.getInventory().add(1067, 1);
				player.getInventory().add(1115, 1);
				player.getInventory().add(1153, 1);
				player.getInventory().add(1323, 1);
				recordClaim(player.getIp());
				break;
		}
	}

	private static NPC find(Player player, int id) {
		for (NPC n : player.localNpcs()) {
			if (n.getId() == id)
				return n;
		}
		throw new IllegalArgumentException();
	}

	private static void setDrag(Player player) {
		player.dialogue(
				new OptionsDialogue("What drag setting would you like to use?",
						new Option("5 (OSRS) (2007) Drag", () -> setDrag(player, 5)),
						new Option("10 (Pre-EoC) (2011) Drag", () -> setDrag(player, 10))));
	}

	private static void setDrag(Player player, int drag) {
		player.dragSetting = drag;
	}

	private static void tutorial(Player player) {
		ecoTutorial(player);
	}

	/**
	 * PVP_MODE's starter kit -- equips a PK-ready loadout immediately (whip+defender rather
	 * than the 2h dharok's greataxe, which is carried in the inventory instead so both options
	 * are available) plus the rest of the requested gear/supplies in the inventory.
	 */
	private static void givePvpModeStarter(Player player) {
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_WEAPON, new Item(ItemID.ABYSSAL_WHIP));
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_SHIELD, new Item(ItemID.DRAGON_DEFENDER));
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_HAT, new Item(ItemID.DHAROKS_HELM));
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_CHEST, new Item(ItemID.DHAROKS_PLATEBODY));
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_LEGS, new Item(ItemID.DHAROKS_PLATELEGS));
		player.getEquipment().set(io.ruin.model.item.containers.Equipment.SLOT_FEET, new Item(ItemID.DRAGON_BOOTS));

		player.getInventory().add(ItemID.ARMADYL_GODSWORD, 1);
		player.getInventory().add(ItemID.FIRE_CAPE, 1);
		player.getInventory().add(ItemID.DHAROKS_GREATAXE, 1);
		player.getInventory().add(3145, 100); // Cooked karambwan (noted) -- 3144's own def points noted_id here
		player.getInventory().add(12696, 20); // Super combat potion(4), noted -- 12695's own def points noted_id here
		player.getInventory().add(ItemID.BERSERKER_RING, 1);
		player.getInventory().add(ItemID.KARILS_LEATHERSKIRT, 1);
		player.getInventory().add(ItemID.KARILS_LEATHERTOP, 1);
		player.getInventory().add(ItemID.KARILS_COIF, 1);
		player.getInventory().add(ItemID.KARILS_CROSSBOW, 1);
		player.getInventory().add(19625, 15); // Home teleport

		player.getEquipment().sendUpdates();
		player.getInventory().sendUpdates();
	}

}
