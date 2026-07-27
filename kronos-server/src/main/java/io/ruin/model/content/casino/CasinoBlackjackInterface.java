package io.ruin.model.content.casino;

import io.ruin.cache.ItemID;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.process.event.EventWorker;

import java.util.List;

/**
 * Ported from the source casino minigame (BlackjackInterface.java). Component ids below
 * are decoded 1:1 from the source interface's raw cache archive -- see
 * data zelus/data/cache/toml/1_patches/interface/5104.toml.
 */
public class CasinoBlackjackInterface {

	public static final int INTERFACE_ID = 5104;

	private static final int CLOSE = 2;
	private static final int HIT = 8;
	private static final int PLACE = 27;
	private static final int STAND = 30;
	private static final int SPLIT_PAIRS = 54;
	private static final int CLEAR_BET = 48;
	private static final int RESTART = 51;

	private static final int CHIP_100K = 12;
	private static final int CHIP_500K = 15;
	private static final int CHIP_1M = 18;
	private static final int CHIP_10M = 21;
	private static final int CHIP_100M = 24;

	private static final int TOTAL_BET_DISPLAY = 6;
	private static final int HOUSE_POINTS_DISPLAY = 59;
	private static final int PLAYER_POINTS_DISPLAY = 63;

	private static final int[] PLAYER_CARD_SLOTS = {38, 40, 42, 44, 46, 71};
	private static final int[] HOUSE_CARD_SLOTS = {34, 36, 65, 67, 69, 85};

	private static final int DEAL_DELAY_TICKS = 2;

	public static void register() {
		InterfaceHandler.register(INTERFACE_ID, h -> {
			h.simpleAction(CLOSE, CasinoBlackjackInterface::forfeitAndClose);
			h.simpleAction(HIT, CasinoBlackjackInterface::onHit);
			h.simpleAction(PLACE, CasinoBlackjackInterface::onPlace);
			h.simpleAction(STAND, CasinoBlackjackInterface::onStand);
			h.simpleAction(SPLIT_PAIRS, p -> p.sendMessage("Splitting pairs isn't available yet."));
			h.simpleAction(CLEAR_BET, CasinoBlackjackInterface::onClearBet);
			h.simpleAction(RESTART, CasinoBlackjackInterface::onRestart);
			h.simpleAction(CHIP_100K, p -> onSelectChip(p, 100_000));
			h.simpleAction(CHIP_500K, p -> onSelectChip(p, 500_000));
			h.simpleAction(CHIP_1M, p -> onSelectChip(p, 1_000_000));
			h.simpleAction(CHIP_10M, p -> onSelectChip(p, 10_000_000));
			h.simpleAction(CHIP_100M, p -> onSelectChip(p, 100_000_000));
			h.simpleAction(TOTAL_BET_DISPLAY, CasinoBlackjackInterface::onCustomBetPrompt);
			h.closedAction = (p, i) -> forfeit(p);
		});

		NPCAction.register(5840, "BlackJack", (player, npc) -> open(player));
	}

	public static void open(Player player) {
		if (player.getCasinoBlackjack() == null)
			player.setCasinoBlackjack(new CasinoBlackjackGame(player));
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		clearTable(player);
		updateBetDisplay(player);

		CasinoBlackjackGame game = player.getCasinoBlackjack();
		player.sendMessage("<col=808080>Provably fair - client seed: " + game.getClientSeed()
				+ " | this round's seed hash: " + game.getServerSeedHash() + ". Change your client seed with ::casinoseed followed by your new seed.");
	}

	/** Bound to ::casinoseed <value> in CommandHandlerRegular. */
	public static void setClientSeed(Player player, String seed) {
		if (!io.ruin.model.content.casino.provablyfair.ProvablyFair.isSeedValid(seed)) {
			player.sendMessage("Seeds can only contain letters, numbers, and hyphens.");
			return;
		}
		if (player.getCasinoBlackjack() == null)
			player.setCasinoBlackjack(new CasinoBlackjackGame(player));
		if (player.getCasinoBlackjack().isRoundActive()) {
			player.sendMessage("You can't change your seed mid-round.");
			return;
		}
		player.getCasinoBlackjack().setClientSeed(seed);
		player.sendMessage("Your provably fair client seed is now: " + seed
				+ " | this round's seed hash: " + player.getCasinoBlackjack().getServerSeedHash());
	}

	private static void forfeitAndClose(Player player) {
		player.closeInterface(ToplevelComponent.MAINMODAL);
	}

	/** Bet is only ever "at risk" once a round is active -- an unstarted bet is just refunded. */
	private static void forfeit(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null)
			return;
		if (game.isRoundActive()) {
			player.sendMessage("Your bet is gone, so is your money. Next time don't close mid-round.");
		} else if (game.getBetAmount() > 0) {
			player.getInventory().addOrDrop(ItemID.COINS_995, game.clearBet());
		}
		game.reset();
		player.sendMessage("<col=808080>Provably fair - session closed.");
	}

	private static void onSelectChip(Player player, int chipValue) {
		addToBet(player, chipValue);
	}

	private static void onCustomBetPrompt(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null || game.isRoundActive()) {
			player.sendMessage("You cannot place a bet while a round is in progress.");
			return;
		}
		player.integerInput("Enter the amount to bet:", amount -> addToBet(player, amount));
	}

	private static void addToBet(Player player, int amount) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null || game.isRoundActive()) {
			player.sendMessage("You cannot place a bet while a round is in progress.");
			return;
		}
		if (amount <= 0) {
			player.sendMessage("Enter a positive amount.");
			return;
		}
		if (player.getInventory().getAmount(ItemID.COINS_995) < amount) {
			player.sendMessage("You don't have enough coins to place this bet.");
			return;
		}
		player.getInventory().remove(ItemID.COINS_995, amount);
		game.addToBet(amount);
		player.sendMessage("You added " + amount + " to your bet. Total bet: " + game.getBetAmount() + ".");
		updateBetDisplay(player);
	}

	private static void onClearBet(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null || game.isRoundActive()) {
			player.sendMessage("You can't clear your bet right now.");
			return;
		}
		int refund = game.clearBet();
		if (refund > 0)
			player.getInventory().addOrDrop(ItemID.COINS_995, refund);
		updateBetDisplay(player);
		player.sendMessage("Bet cleared.");
	}

	private static void onPlace(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null)
			return;
		if (game.isRoundActive()) {
			player.sendMessage("The game is already in progress.");
			return;
		}
		if (game.getBetAmount() <= 0) {
			player.sendMessage("You must place a bet first!");
			return;
		}
		dealOpeningHand(player, game);
	}

	private static void onHit(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null || !game.isRoundActive()) {
			player.sendMessage("You must place a bet first!");
			return;
		}
		game.hit();
		refreshHands(player, game);
		if (!game.isRoundActive()) {
			settleRound(player, game);
		} else if (game.isPlayerStood()) {
			// hit() auto-stands the player on reaching 21 -- proceed straight to the
			// dealer's turn instead of waiting for a Stand click that will now refuse
			// (they're already marked stood).
			player.sendMessage("21! It's now the dealer's turn.");
			playDealerTurn(player, game);
		}
	}

	private static void onStand(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null || !game.isRoundActive() || game.isPlayerStood()) {
			player.sendMessage("You are not in a game.");
			return;
		}
		game.stand();
		player.sendMessage("You have stood. It's now the dealer's turn.");
		playDealerTurn(player, game);
	}

	private static void onRestart(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		if (game == null) {
			player.sendMessage("You cannot restart now.");
			return;
		}
		if (game.getBetAmount() > 0 && !game.isRoundActive())
			player.getInventory().addOrDrop(ItemID.COINS_995, game.clearBet());
		game.reset();
		clearTable(player);
		updateBetDisplay(player);
		player.sendMessage("The game has been restarted. Please place your bets.");
	}

	private static void dealOpeningHand(Player player, CasinoBlackjackGame game) {
		clearTable(player);
		EventWorker.startEvent(event -> {
			game.startRound();
			// player, dealer, player, dealer -- matches the source deal order/timing
			updateCardSlot(player, PLAYER_CARD_SLOTS[0], game.getPlayerHand().get(0).getSpriteId());
			event.delay(DEAL_DELAY_TICKS);
			updateCardSlot(player, HOUSE_CARD_SLOTS[0], game.getDealerHand().get(0).getSpriteId());
			event.delay(DEAL_DELAY_TICKS);
			updateCardSlot(player, PLAYER_CARD_SLOTS[1], game.getPlayerHand().get(1).getSpriteId());
			event.delay(DEAL_DELAY_TICKS);
			// dealer's second card stays face-down until the round resolves
			updateCardSlot(player, HOUSE_CARD_SLOTS[1], CardSprite.CARD_BACK_SPRITE);
			updateScoreDisplays(player, game, true);

			if (!game.isRoundActive()) {
				settleRound(player, game);
			}
		});
	}

	private static void refreshHands(Player player, CasinoBlackjackGame game) {
		List<CardSprite> hand = game.getPlayerHand();
		for (int i = 0; i < hand.size() && i < PLAYER_CARD_SLOTS.length; i++) {
			updateCardSlot(player, PLAYER_CARD_SLOTS[i], hand.get(i).getSpriteId());
		}
		updateScoreDisplays(player, game, true);
	}

	private static void playDealerTurn(Player player, CasinoBlackjackGame game) {
		EventWorker.startEvent(event -> {
			// reveal the hidden second card first
			updateCardSlot(player, HOUSE_CARD_SLOTS[1], game.getDealerHand().get(1).getSpriteId());
			updateScoreDisplays(player, game, false);
			event.delay(DEAL_DELAY_TICKS);

			boolean drew = true;
			while (drew) {
				int beforeSize = game.getDealerHand().size();
				drew = game.dealerDrawStep();
				if (game.getDealerHand().size() > beforeSize) {
					int slotIndex = game.getDealerHand().size() - 1;
					if (slotIndex < HOUSE_CARD_SLOTS.length) {
						updateCardSlot(player, HOUSE_CARD_SLOTS[slotIndex],
								game.getDealerHand().get(slotIndex).getSpriteId());
					}
					updateScoreDisplays(player, game, false);
					event.delay(DEAL_DELAY_TICKS);
				}
			}
			settleRound(player, game);
		});
	}

	private static void settleRound(Player player, CasinoBlackjackGame game) {
		// dealer's hole card may still be showing face-down (e.g. a natural player blackjack
		// resolves before playDealerTurn() ever flips it) -- always reveal it on settle
		if (game.getDealerHand().size() > 1) {
			updateCardSlot(player, HOUSE_CARD_SLOTS[1], game.getDealerHand().get(1).getSpriteId());
		}
		updateScoreDisplays(player, game, false);
		int payout = game.payout();
		if (payout > 0)
			player.getInventory().addOrDrop(ItemID.COINS_995, payout);

		switch (game.getOutcome()) {
			case PLAYER_BLACKJACK, DEALER_BUST, PLAYER_WIN -> player.sendMessage(io.ruin.cache.Color.GREEN, game.outcomeMessage());
			case PLAYER_BUST, DEALER_WIN -> player.sendMessage(io.ruin.cache.Color.RED, game.outcomeMessage());
			default -> player.sendMessage(game.outcomeMessage());
		}

		String[] proof = game.revealAndRotateSeed();
		player.sendMessage("<col=808080>Provably fair - server seed: " + proof[0] + " | client seed: "
				+ proof[1] + " | nonce: " + proof[2] + ". Next round's seed hash: " + game.getServerSeedHash());

		game.clearBet();
		updateBetDisplay(player);
	}

	private static void updateScoreDisplays(Player player, CasinoBlackjackGame game, boolean hideDealerHoleCard) {
		player.getPacketSender().sendString(INTERFACE_ID, PLAYER_POINTS_DISPLAY,
				String.valueOf(game.getPlayerScore()));
		int dealerScore = hideDealerHoleCard && game.getDealerHand().size() > 1
				? game.getDealerHand().get(0).getValue(true)
				: game.getDealerScore();
		player.getPacketSender().sendString(INTERFACE_ID, HOUSE_POINTS_DISPLAY, String.valueOf(dealerScore));
	}

	private static void updateBetDisplay(Player player) {
		CasinoBlackjackGame game = player.getCasinoBlackjack();
		int bet = game == null ? 0 : game.getBetAmount();
		player.getPacketSender().sendString(INTERFACE_ID, TOTAL_BET_DISPLAY, formatAmount(bet));
	}

	private static void clearTable(Player player) {
		for (int slot : PLAYER_CARD_SLOTS)
			updateCardSlot(player, slot, -1);
		for (int slot : HOUSE_CARD_SLOTS)
			updateCardSlot(player, slot, -1);
		player.getPacketSender().sendString(INTERFACE_ID, PLAYER_POINTS_DISPLAY, "0");
		player.getPacketSender().sendString(INTERFACE_ID, HOUSE_POINTS_DISPLAY, "0");
	}

	private static void updateCardSlot(Player player, int componentId, int spriteId) {
		int packed = (INTERFACE_ID << 16) | componentId;
		player.getPacketSender().sendClientScript(44, packed, spriteId);
	}

	private static String formatAmount(int amount) {
		if (amount >= 1_000_000_000) return (amount / 1_000_000_000) + "B";
		if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
		if (amount >= 1_000) return (amount / 1_000) + "K";
		return String.valueOf(amount);
	}
}
