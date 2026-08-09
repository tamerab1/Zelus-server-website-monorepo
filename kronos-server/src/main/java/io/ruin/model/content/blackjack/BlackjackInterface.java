package io.ruin.model.content.blackjack;

import io.ruin.cache.InterfaceDef;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlackjackInterface {

	public static final int MAIN_INTERFACE_ID = 998;
	public static final int SIDE_INTERFACE_ID = 997;

	public static final int CARD_BLANK_SPRITE = 5829;
	public static final int CARD_BACK_SPRITE = 5830;

	public static final int MAIN_BACKGROUND = 1;
	public static final int MAIN_TRANSPARENT_BACKGROUND = 17;
	public static final int MAIN_CLOSE_BUTTON = 2;
	public static final int MAIN_LIGHT_BUTTON = 15;
	public static final int MAIN_RULES_BUTTON = 16;
	public static final int MAIN_PROVABLY_FAIR_BUTTON = 19;
	public static final int MAIN_PLACE_BETS_BUTTON = 122;
	public static final int MAIN_STATS_TEXT = 4;
	public static final int MAIN_RULES_LINE_ONE_TEXT = 11;
	public static final int MAIN_RULES_LINE_TWO_TEXT = 12;
	public static final int MAIN_HAND_ONE_ARROW = 13;
	public static final int MAIN_HAND_TWO_ARROW = 14;
	public static final int MAIN_RESULT_TEXT = 18;
	public static final int MAIN_HAND_ONE_SCORE_BOX = 120;
	public static final int MAIN_HAND_ONE_SCORE_GLOW = 121;
	public static final int MAIN_HAND_ONE_SCORE_TEXT = 129;
	public static final int MAIN_HAND_TWO_SCORE_BOX = 123;
	public static final int MAIN_HAND_TWO_SCORE_GLOW = 124;
	public static final int MAIN_HAND_TWO_SCORE_TEXT = 130;
	public static final int MAIN_DEALER_SCORE_BOX = 126;
	public static final int MAIN_DEALER_SCORE_GLOW = 127;
	public static final int MAIN_DEALER_SCORE_TEXT = 128;
	public static final int MAIN_RULES_CLOSE_BUTTON = 202;
	public static final int MAIN_PROVABLY_FAIR_CLOSE_BUTTON = 232;
	public static final int MAIN_CLIENT_SEED_BUTTON = 241;
	public static final int MAIN_SERVER_SEED_BUTTON = 242;
	public static final int MAIN_SERVER_HASH_BUTTON = 243;
	public static final int MAIN_NONCE_BUTTON = 244;
	public static final int MAIN_VERIFY_BUTTON = 245;

	public static final int DEALER_CARD_START = 40;
	public static final int HAND_ONE_CARD_START = 60;
	public static final int HAND_TWO_CARD_START = 80;
	public static final int MAX_CARDS_PER_ROW = 10;

	public static final int SIDE_CHANGE_CURRENCY_BUTTON = 11;
	public static final int SIDE_BET_INPUT_BUTTON = 21;
	public static final int SIDE_HALF_BUTTON = 22;
	public static final int SIDE_DOUBLE_BET_BUTTON = 23;
	public static final int SIDE_HIT_BUTTON = 30;
	public static final int SIDE_STAND_BUTTON = 35;
	public static final int SIDE_SPLIT_BUTTON = 40;
	public static final int SIDE_DOUBLE_BUTTON = 45;
	public static final int SIDE_BET_BUTTON = 50;
	public static final int SIDE_ACCEPT_INSURANCE_BUTTON = 61;
	public static final int SIDE_DECLINE_INSURANCE_BUTTON = 62;
	public static final int SIDE_BALANCE_TEXT = 10;
	public static final int SIDE_CURRENCY_TEXT = 11;
	public static final int SIDE_BET_TEXT = 21;

	public static final int[] MAIN_RULES_OVERLAY_COMPONENTS = {
			200, 201, 202, 203, 204, 205, 206, 207, 208, 209,
			210, 211, 212, 213, 214, 215, 217
	};
	public static final int[] MAIN_PROVABLY_FAIR_COMPONENTS = {
			230, 231, 232, 233, 234, 235, 236, 237, 238, 239,
			240, 241, 242, 243, 244, 245, 246, 247, 248, 249
	};
	public static final int[] SIDE_NORMAL_COMPONENTS = {
			10, 11, 12, 13, 20, 21, 22, 23, 24, 25, 26, 30, 35, 40, 45, 50
	};
	public static final int[] SIDE_INSURANCE_COMPONENTS = {
			60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72
	};

	private static final Map<String, BlackjackSession> SESSIONS = new ConcurrentHashMap<>();
	private static boolean registered;

	public static BlackjackSession get(Player player) {
		return SESSIONS.computeIfAbsent(key(player), ignored -> new BlackjackSession());
	}

	public static void open(Player player) {
		if (!isInterfacePacked()) {
			player.sendMessage("Blackjack interface is missing from the cache. Copy the TOML patches and rebuild the cache.");
			return;
		}
		register();
		player.getPacketSender().sendClientScript(917, "ii", -1, -1);
		player.openInterface(ToplevelComponent.MAINMODAL, MAIN_INTERFACE_ID);
		player.openInterface(ToplevelComponent.SIDEMODAL, SIDE_INTERFACE_ID);
		get(player).render(player);
	}

	public static int cardComponent(BlackjackSession.Row row, int slot) {
		if (slot < 0 || slot >= MAX_CARDS_PER_ROW)
			return -1;
		return switch (row) {
			case DEALER -> DEALER_CARD_START + slot;
			case HAND_ONE -> HAND_ONE_CARD_START + slot;
			case HAND_TWO -> HAND_TWO_CARD_START + slot;
		};
	}

	private static boolean isInterfacePacked() {
		return InterfaceDef.COUNTS != null
				&& MAIN_INTERFACE_ID < InterfaceDef.COUNTS.length
				&& SIDE_INTERFACE_ID < InterfaceDef.COUNTS.length
				&& MAIN_INTERFACE_ID < InterfaceHandler.HANDLERS.length
				&& SIDE_INTERFACE_ID < InterfaceHandler.HANDLERS.length
				&& InterfaceDef.COUNTS[MAIN_INTERFACE_ID] > MAIN_PROVABLY_FAIR_COMPONENTS[MAIN_PROVABLY_FAIR_COMPONENTS.length - 1]
				&& InterfaceDef.COUNTS[SIDE_INTERFACE_ID] > SIDE_INSURANCE_COMPONENTS[SIDE_INSURANCE_COMPONENTS.length - 1];
	}

	public static synchronized void register() {
		if (registered)
			return;
		InterfaceHandler.register(MAIN_INTERFACE_ID, h -> {
			h.closedAction = (player, replacement) -> {
				get(player).resetSessionStats();
				player.closeInterface(ToplevelComponent.SIDEMODAL);
			};
			bind(h, MAIN_CLOSE_BUTTON, player -> player.closeInterface(ToplevelComponent.MAINMODAL));
			bind(h, MAIN_LIGHT_BUTTON, player -> get(player).toggleTransparentBackground(player));
			bind(h, MAIN_RULES_BUTTON, player -> get(player).toggleRules(player));
			bind(h, MAIN_RULES_CLOSE_BUTTON, player -> get(player).closeOverlays(player));
			bind(h, MAIN_PROVABLY_FAIR_CLOSE_BUTTON, player -> get(player).closeOverlays(player));
			bind(h, MAIN_CLIENT_SEED_BUTTON, player -> get(player).editClientSeed(player));
			bind(h, MAIN_SERVER_SEED_BUTTON, player -> get(player).revealOrShuffleServerSeed(player));
			bind(h, MAIN_SERVER_HASH_BUTTON, player -> get(player).sendProvablyFairInfo(player));
			bind(h, MAIN_NONCE_BUTTON, player -> get(player).sendProvablyFairInfo(player));
			bind(h, MAIN_VERIFY_BUTTON, player -> get(player).sendProvablyFairInfo(player));
			bind(h, MAIN_PLACE_BETS_BUTTON, player -> get(player).placeBet(player));
		});
		InterfaceHandler.register(SIDE_INTERFACE_ID, h -> {
			bind(h, SIDE_CHANGE_CURRENCY_BUTTON, player -> get(player).chooseCurrency(player));
			bind(h, SIDE_BET_INPUT_BUTTON, player -> get(player).enterBet(player));
			bind(h, SIDE_HALF_BUTTON, player -> get(player).halveBet(player));
			bind(h, SIDE_DOUBLE_BET_BUTTON, player -> get(player).doubleBet(player));
			bind(h, SIDE_HIT_BUTTON, player -> get(player).hitOrTakeInsurance(player));
			bind(h, SIDE_STAND_BUTTON, player -> get(player).standOrDeclineInsurance(player));
			bind(h, SIDE_SPLIT_BUTTON, player -> get(player).split(player));
			bind(h, SIDE_DOUBLE_BUTTON, player -> get(player).doubleDown(player));
			bind(h, SIDE_BET_BUTTON, player -> get(player).placeBet(player));
			bind(h, SIDE_ACCEPT_INSURANCE_BUTTON, player -> get(player).hitOrTakeInsurance(player));
			bind(h, SIDE_DECLINE_INSURANCE_BUTTON, player -> get(player).standOrDeclineInsurance(player));
		});
		registered = true;
	}

	public static void setComponentsHidden(Player player, int interfaceId, int[] componentIds, boolean hidden) {
		for (int componentId : componentIds)
			player.getPacketSender().setHidden(interfaceId, componentId, hidden);
	}

	private static void bind(InterfaceHandler handler, int componentId, SimpleAction action) {
		if (componentId >= 0 && componentId < handler.actions.length)
			handler.actions[componentId] = action;
	}

	private static String key(Player player) {
		return player.getName() == null ? "unknown" : player.getName().toLowerCase();
	}
}
