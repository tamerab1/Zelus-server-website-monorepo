package io.ruin.model.content.blackjack;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.network.PacketSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BlackjackSession {

	private static final int SHOE_DECKS = 8;
	private static final int DEFAULT_BET = 10_000;
	private static final int MIN_BET = 1;
	private static final int MAX_BET = 500_000_000;
	private static final int DECK_X = 417;
	private static final int DECK_Y = 32;
	private static final int CARD_TWEEN_STEPS = 18;
	private static final long CARD_TWEEN_STEP_MILLIS = 15L;
	private static final ScheduledExecutorService CARD_TWEEN_EXECUTOR = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "blackjack-card-tween");
		thread.setDaemon(true);
		return thread;
	});
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final List<BlackjackCard> shoe = new ArrayList<>(52 * SHOE_DECKS);
	private final List<BlackjackHand> hands = new ArrayList<>(2);
	private final BlackjackHand dealer = new BlackjackHand(0);

	private BlackjackCurrency currency = BlackjackCurrency.DONATOR_POINTS;
	private Stage stage = Stage.BETTING;
	private int betAmount = DEFAULT_BET;
	private int activeHand;
	private int wins;
	private int losses;
	private int pushes;
	private long profit;
	private int insuranceBet;
	private boolean dealerHoleRevealed;
	private boolean splitRound;
	private boolean animating;
	private boolean rulesOpen;
	private boolean transparentBackground;
	private boolean provablyFairOpen;
	private String clientSeed;
	private String serverSeed = randomHex(32);
	private String revealedServerSeed = "";
	private int nextNonce = 1;
	private int visibleNonce = 1;
	private boolean serverSeedRevealed;
	private String status = "Place Bets";
	private String result = "";

	public void render(Player player) {
		renderMain(player);
		renderSide(player);
	}

	/** Called when the player closes the blackjack interface -- the session object itself
	 * persists (keyed by name in BlackjackInterface.SESSIONS) so a mid-round hand can survive
	 * an accidental close/reopen, but the running "session stats" tally should not carry over. */
	public void resetSessionStats() {
		wins = 0;
		losses = 0;
		pushes = 0;
		profit = 0;
		result = "";
		status = "Place Bets";
	}

	public void toggleRules(Player player) {
		rulesOpen = !rulesOpen;
		if (rulesOpen)
			provablyFairOpen = false;
		renderMain(player);
	}

	public void toggleTransparentBackground(Player player) {
		transparentBackground = !transparentBackground;
		renderMain(player);
	}

	public void toggleProvablyFair(Player player) {
		provablyFairOpen = !provablyFairOpen;
		if (provablyFairOpen)
			rulesOpen = false;
		renderMain(player);
	}

	public void closeOverlays(Player player) {
		rulesOpen = false;
		provablyFairOpen = false;
		renderMain(player);
	}

	public void editClientSeed(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can only edit your blackjack client seed between hands.");
			return;
		}
		ensureFairSeeds(player);
		player.stringInput("Enter blackjack client seed:", seed -> {
			if (!canChangeBet()) {
				player.sendMessage("You can only edit your blackjack client seed between hands.");
				return;
			}
			clientSeed = sanitizeSeed(seed);
			status = "Client seed updated.";
			render(player);
		});
	}

	public void revealOrShuffleServerSeed(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can reveal or shuffle the blackjack server seed between hands.");
			return;
		}
		ensureFairSeeds(player);
		if (serverSeedRevealed) {
			rotateServerSeed();
			status = "Server seed shuffled.";
		} else {
			revealedServerSeed = serverSeed;
			serverSeedRevealed = true;
			status = "Server seed revealed.";
		}
		render(player);
	}

	public void sendProvablyFairInfo(Player player) {
		ensureFairSeeds(player);
		player.sendMessage("Blackjack client seed: " + clientSeed);
		player.sendMessage("Blackjack server seed hash: " + serverSeedHash());
		player.sendMessage("Blackjack hand nonce: " + visibleNonce);
		if (serverSeedRevealed)
			player.sendMessage("Revealed server seed: " + revealedServerSeed);
	}

	public void chooseCurrency(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can't change currency while a hand is in play.");
			return;
		}
		player.dialogue(new OptionsDialogue("Please select a currency to bet:",
				currencyOption(player, BlackjackCurrency.DONATOR_POINTS),
				currencyOption(player, BlackjackCurrency.PLATINUM_TOKENS),
				new Option("Cancel", player::closeDialogue)));
	}

	private Option currencyOption(Player player, BlackjackCurrency selected) {
		// Not a literal "|" -- OptionsDialogue joins every option's text with "|" as the
		// delimiter the client script splits on, so a real pipe inside one option's own text
		// gets misread as an extra option boundary and wraps onto a second line.
		String text = Color.COOL_BLUE.wrap(selected.displayName) + " - Your Balance: "
				+ Color.RED.wrap(NumberUtils.formatNumber(selected.balance(player)));
		return new Option(text, () -> setCurrency(player, selected));
	}

	public void enterBet(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can't change the bet while a hand is in play.");
			return;
		}
		player.integerInput("Enter blackjack bet amount:", amount -> {
			if (!canChangeBet()) {
				player.sendMessage("You can't change the bet while a hand is in play.");
				return;
			}
			setBetAmount(player, amount);
		});
	}

	public void halveBet(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can't change the bet while a hand is in play.");
			return;
		}
		setBetAmount(player, Math.max(MIN_BET, betAmount / 2));
	}

	public void doubleBet(Player player) {
		if (!canChangeBet()) {
			player.sendMessage("You can't change the bet while a hand is in play.");
			return;
		}
		if (betAmount > MAX_BET / 2) {
			setBetAmount(player, MAX_BET);
			return;
		}
		setBetAmount(player, betAmount * 2);
	}

	public void placeBet(Player player) {
		if (!canPlaceBet()) {
			player.sendMessage("Finish the current blackjack hand first.");
			return;
		}
		if (betAmount < MIN_BET) {
			player.sendMessage("Your blackjack bet must be at least " + MIN_BET + ".");
			return;
		}
		if (currency.balance(player) < betAmount) {
			player.sendMessage("You don't have enough " + currency.displayName.toLowerCase() + " to place that bet.");
			return;
		}
		if (!currency.remove(player, betAmount)) {
			player.sendMessage("Unable to take the blackjack bet from your selected currency.");
			return;
		}
		startRound(player);
	}

	public void hitOrTakeInsurance(Player player) {
		if (animating) {
			player.sendMessage("Please wait for the cards to finish moving.");
			return;
		}
		if (stage == Stage.INSURANCE) {
			takeInsurance(player);
			return;
		}
		if (!canAct(player))
			return;
		BlackjackHand hand = currentHand();
		if (hand == null)
			return;
		animating = true;
		status = "Dealing...";
		renderSide(player);
		World.startEvent(event -> {
			dealToHand(player, Row.forHand(activeHand), hand, true, event);
			if (hand.bust()) {
				hand.complete();
				status = handTitle(activeHand) + " busts.";
				result = "Bust";
				render(player);
				event.delay(1);
				nextHandOrDealer(player, event);
			} else if (hand.value() == 21) {
				hand.complete();
				nextHandOrDealer(player, event);
			} else {
				animating = false;
				status = "Hit or Stand";
				render(player);
			}
		});
	}

	public void standOrDeclineInsurance(Player player) {
		if (animating) {
			player.sendMessage("Please wait for the cards to finish moving.");
			return;
		}
		if (stage == Stage.INSURANCE) {
			declineInsurance(player);
			return;
		}
		if (!canAct(player))
			return;
		BlackjackHand hand = currentHand();
		if (hand == null)
			return;
		hand.complete();
		status = handTitle(activeHand) + " stands.";
		animating = true;
		render(player);
		World.startEvent(event -> nextHandOrDealer(player, event));
	}

	public void split(Player player) {
		if (!canAct(player))
			return;
		BlackjackHand hand = currentHand();
		if (hand == null || !canSplit(hand)) {
			player.sendMessage("You can only split two starting cards with the same blackjack value.");
			return;
		}
		if (currency.balance(player) < hand.bet()) {
			player.sendMessage("You need another " + NumberUtils.formatNumber(hand.bet()) + " " + currency.shortName + " to split.");
			return;
		}
		if (!currency.remove(player, hand.bet())) {
			player.sendMessage("Unable to take the additional split bet.");
			return;
		}
		splitRound = true;
		BlackjackCard moved = hand.remove(1);
		BlackjackHand split = new BlackjackHand(hand.bet());
		split.add(moved);
		hand.setSplitHand(true);
		split.setSplitHand(true);
		boolean splitAces = hand.get(0).rank == BlackjackCard.Rank.ACE;
		hand.setSplitAces(splitAces);
		split.setSplitAces(splitAces);
		hands.add(split);
		activeHand = 0;
		animating = true;
		status = "Splitting...";
		render(player);
		World.startEvent(event -> {
			layoutExistingCards(player);
			event.delay(1);
			dealToHand(player, Row.HAND_ONE, hand, true, event);
			dealToHand(player, Row.HAND_TWO, split, true, event);
			if (splitAces) {
				hand.complete();
				split.complete();
				nextHandOrDealer(player, event);
				return;
			}
			animating = false;
			status = "Playing Hand 1";
			render(player);
		});
	}

	public void doubleDown(Player player) {
		if (!canAct(player))
			return;
		BlackjackHand hand = currentHand();
		if (hand == null || !hand.canDouble()) {
			player.sendMessage("You can only double on your first two cards.");
			return;
		}
		if (currency.balance(player) < hand.bet()) {
			player.sendMessage("You need another " + NumberUtils.formatNumber(hand.bet()) + " " + currency.shortName + " to double.");
			return;
		}
		if (!currency.remove(player, hand.bet())) {
			player.sendMessage("Unable to take the additional double bet.");
			return;
		}
		hand.addBet(hand.bet());
		hand.markDoubled();
		hand.complete();
		animating = true;
		status = "Doubling...";
		render(player);
		World.startEvent(event -> {
			dealToHand(player, Row.forHand(activeHand), hand, true, event);
			nextHandOrDealer(player, event);
		});
	}

	private void setCurrency(Player player, BlackjackCurrency selected) {
		currency = selected;
		status = "Selected " + selected.displayName;
		player.closeDialogue();
		BlackjackInterface.open(player);
	}

	private void setBetAmount(Player player, int amount) {
		if (amount < MIN_BET) {
			player.sendMessage("Your blackjack bet must be at least " + MIN_BET + ".");
			return;
		}
		if (amount > MAX_BET)
			amount = MAX_BET;
		betAmount = amount;
		status = "Bet set to " + NumberUtils.formatNumber(betAmount);
		render(player);
	}

	private void startRound(Player player) {
		prepareShoe(player);
		hands.clear();
		dealer.clear();
		dealerHoleRevealed = false;
		splitRound = false;
		insuranceBet = 0;
		activeHand = 0;
		result = "";
		status = "Dealing...";
		stage = Stage.DEALING;
		animating = true;
		BlackjackHand playerHand = new BlackjackHand(betAmount);
		hands.add(playerHand);
		clearCards(player);
		render(player);
		World.startEvent(event -> {
			dealToHand(player, Row.HAND_ONE, playerHand, true, event);
			dealToDealer(player, true, event);
			dealToHand(player, Row.HAND_ONE, playerHand, true, event);
			dealToDealer(player, false, event);
			if (dealer.get(0).rank == BlackjackCard.Rank.ACE && !playerHand.blackjack()) {
				stage = Stage.INSURANCE;
				animating = false;
				status = "Insurance?";
				render(player);
				return;
			}
			finishInitialDeal(player, event);
		});
	}

	private void takeInsurance(Player player) {
		int amount = Math.max(1, currentHand().bet() / 2);
		if (currency.balance(player) < amount) {
			player.sendMessage("You need " + NumberUtils.formatNumber(amount) + " " + currency.shortName + " for insurance.");
			return;
		}
		if (!currency.remove(player, amount)) {
			player.sendMessage("Unable to take the insurance bet.");
			return;
		}
		insuranceBet = amount;
		animating = true;
		status = "Insurance placed.";
		render(player);
		resolveInsurance(player);
	}

	private void declineInsurance(Player player) {
		animating = true;
		status = "Insurance declined.";
		render(player);
		resolveInsurance(player);
	}

	private void finishInitialDeal(Player player, io.ruin.process.event.Event event) {
		BlackjackHand playerHand = currentHand();
		if (dealer.blackjack() || playerHand.blackjack()) {
			dealerHoleRevealed = true;
			revealDealerHole(player);
			event.delay(1);
			settleRound(player);
			return;
		}
		stage = Stage.PLAYER_TURN;
		animating = false;
		status = "Hit or Stand";
		render(player);
	}

	private void resolveInsurance(Player player) {
		animating = true;
		World.startEvent(event -> {
			if (dealer.blackjack()) {
				dealerHoleRevealed = true;
				revealDealerHole(player);
				int insurancePayout = insuranceBet * 3;
				if (insurancePayout > 0) {
					currency.add(player, insurancePayout);
					profit += insurancePayout - insuranceBet;
				}
				event.delay(1);
				settleRound(player);
				return;
			}
			if (insuranceBet > 0)
				profit -= insuranceBet;
			insuranceBet = 0;
			finishInitialDeal(player, event);
		});
	}

	private void nextHandOrDealer(Player player, io.ruin.process.event.Event event) {
		if (activeHand + 1 < hands.size()) {
			activeHand++;
			stage = Stage.PLAYER_TURN;
			animating = false;
			status = "Playing Hand " + (activeHand + 1);
			render(player);
			return;
		}
		if (allHandsBust()) {
			settleRound(player);
			return;
		}
		playDealer(player, event);
	}

	private boolean allHandsBust() {
		for (BlackjackHand hand : hands) {
			if (!hand.bust())
				return false;
		}
		return !hands.isEmpty();
	}

	private void playDealer(Player player, io.ruin.process.event.Event event) {
		stage = Stage.DEALER_TURN;
		status = "Dealer's turn";
		dealerHoleRevealed = true;
		revealDealerHole(player);
		render(player);
		event.delay(1);
		while (dealer.value() < 17) {
			dealToDealer(player, true, event);
		}
		settleRound(player);
	}

	private void settleRound(Player player) {
		stage = Stage.ROUND_OVER;
		animating = false;
		dealerHoleRevealed = true;
		int dealerValue = dealer.value();
		boolean dealerBlackjack = dealer.blackjack();
		boolean dealerBust = dealer.bust();
		long net = 0;
		int settledWins = 0;
		int settledLosses = 0;
		int settledPushes = 0;
		for (BlackjackHand hand : hands) {
			if (hand.settled())
				continue;
			hand.settle();
			int payout = payoutFor(hand, dealerValue, dealerBust, dealerBlackjack);
			net += payout - hand.bet();
			if (payout > hand.bet())
				settledWins++;
			else if (payout == hand.bet())
				settledPushes++;
			else
				settledLosses++;
			if (payout > 0)
				currency.add(player, payout);
		}
		wins += settledWins;
		losses += settledLosses;
		pushes += settledPushes;
		profit += net;
		status = outcomeStatus(settledWins, settledLosses, settledPushes, net);
		result = status;
		player.sendMessage(chatOutcomeMessage(settledWins, settledLosses, settledPushes, net));
		render(player);
	}

	private String chatOutcomeMessage(int settledWins, int settledLosses, int settledPushes, long net) {
		if (settledWins > 0 && settledLosses == 0 && settledPushes == 0)
			return "<col=00ff00>[Blackjack] You won " + signed(net) + " " + currency.shortName + "!";
		if (settledLosses > 0 && settledWins == 0 && settledPushes == 0)
			return "<col=ff0000>[Blackjack] You lost. (" + signed(net) + " " + currency.shortName + ")";
		if (settledPushes > 0 && settledWins == 0 && settledLosses == 0)
			return "<col=ffff00>[Blackjack] Push - your bet was returned.";
		return "<col=ffffff>[Blackjack] Round settled: " + signed(net) + " " + currency.shortName;
	}

	private int payoutFor(BlackjackHand hand, int dealerValue, boolean dealerBust, boolean dealerBlackjack) {
		if (dealerBlackjack && hand.blackjack())
			return hand.bet();
		if (dealerBlackjack)
			return 0;
		if (hand.blackjack())
			return Math.toIntExact(Math.min(Integer.MAX_VALUE, hand.bet() * 5L / 2L));
		if (hand.bust())
			return 0;
		if (dealerBust)
			return hand.bet() * 2;
		if (hand.value() > dealerValue)
			return hand.bet() * 2;
		if (hand.value() == dealerValue)
			return hand.bet();
		return 0;
	}

	private String outcomeStatus(int settledWins, int settledLosses, int settledPushes, long net) {
		if (settledWins > 0 && settledLosses == 0 && settledPushes == 0)
			return "You win " + signed(net) + " " + currency.shortName;
		if (settledLosses > 0 && settledWins == 0 && settledPushes == 0)
			return "Dealer wins " + signed(net) + " " + currency.shortName;
		if (settledPushes > 0 && settledWins == 0 && settledLosses == 0)
			return "Push";
		return "Round settled " + signed(net) + " " + currency.shortName;
	}

	private void dealToHand(Player player, Row row, BlackjackHand hand, boolean faceUp, io.ruin.process.event.Event event) {
		BlackjackCard card = draw();
		hand.add(card);
		animateCard(player, row, hand.size() - 1, card, faceUp, event);
		renderScores(player);
	}

	private void dealToDealer(Player player, boolean faceUp, io.ruin.process.event.Event event) {
		BlackjackCard card = draw();
		dealer.add(card);
		animateCard(player, Row.DEALER, dealer.size() - 1, card, faceUp, event);
		renderScores(player);
	}

	private BlackjackCard draw() {
		if (shoe.isEmpty())
			reshuffle(new Random());
		return shoe.remove(shoe.size() - 1);
	}

	private void prepareShoe(Player player) {
		ensureFairSeeds(player);
		if (serverSeedRevealed)
			rotateServerSeed();
		visibleNonce = nextNonce++;
		reshuffle(new Random(seedForHand()));
	}

	private void reshuffle(Random shuffleRandom) {
		shoe.clear();
		for (int deck = 0; deck < SHOE_DECKS; deck++)
			Collections.addAll(shoe, BlackjackCard.VALUES);
		Collections.shuffle(shoe, shuffleRandom);
	}

	private boolean canPlaceBet() {
		return stage == Stage.BETTING || stage == Stage.ROUND_OVER;
	}

	private boolean canChangeBet() {
		return canPlaceBet() && !animating;
	}

	private boolean canAct(Player player) {
		if (stage != Stage.PLAYER_TURN) {
			player.sendMessage("You need to place a bet before using that option.");
			return false;
		}
		if (animating) {
			player.sendMessage("Please wait for the cards to finish moving.");
			return false;
		}
		return true;
	}

	private boolean canSplit(BlackjackHand hand) {
		return !splitRound && hand.canSplit();
	}

	private BlackjackHand currentHand() {
		if (activeHand < 0 || activeHand >= hands.size())
			return null;
		return hands.get(activeHand);
	}

	private void revealDealerHole(Player player) {
		for (int slot = 0; slot < dealer.size(); slot++) {
			int component = BlackjackInterface.cardComponent(Row.DEALER, slot);
			if (component != -1)
				player.getPacketSender().setGraphic(BlackjackInterface.MAIN_INTERFACE_ID, component, dealer.get(slot).spriteId);
		}
		renderScores(player);
	}

	private void layoutExistingCards(Player player) {
		for (int h = 0; h < hands.size(); h++) {
			BlackjackHand hand = hands.get(h);
			for (int slot = 0; slot < hand.size(); slot++) {
				CardPoint point = cardPoint(Row.forHand(h), slot);
				int component = BlackjackInterface.cardComponent(Row.forHand(h), slot);
				if (component != -1)
					player.getPacketSender().setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, point.x, point.y);
			}
		}
	}

	private void animateCard(Player player, Row row, int slot, BlackjackCard card, boolean faceUp, io.ruin.process.event.Event event) {
		int component = BlackjackInterface.cardComponent(row, slot);
		if (component == -1)
			return;
		PacketSender ps = player.getPacketSender();
		CardPoint target = cardPoint(row, slot);
		ps.setGraphic(BlackjackInterface.MAIN_INTERFACE_ID, component, faceUp ? card.spriteId : BlackjackInterface.CARD_BACK_SPRITE);
		ps.setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, DECK_X, DECK_Y);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, false);
		scheduleCardTween(player, component, target);
		event.delay(1);
	}

	private void scheduleCardTween(Player player, int component, CardPoint target) {
		for (int step = 1; step <= CARD_TWEEN_STEPS; step++) {
			final int currentStep = step;
			CARD_TWEEN_EXECUTOR.schedule(() -> {
				if (!player.isOnline() || !player.isVisibleInterface(BlackjackInterface.MAIN_INTERFACE_ID))
					return;
				double t = currentStep / (double) CARD_TWEEN_STEPS;
				double eased = 1 - Math.pow(1 - t, 3);
				int x = DECK_X + (int) Math.round((target.x - DECK_X) * eased);
				int y = DECK_Y + (int) Math.round((target.y - DECK_Y) * eased);
				player.getPacketSender().setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, x, y);
				if (player.rsprotSession != null)
					player.rsprotSession.flush();
			}, currentStep * CARD_TWEEN_STEP_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	private CardPoint cardPoint(Row row, int slot) {
		if (row == Row.DEALER)
			return new CardPoint(197 + (slot * 29), 32);
		if (!splitRound)
			return new CardPoint(197 + (slot * 29), 190);
		if (row == Row.HAND_ONE)
			return new CardPoint(119 + (slot * 29), 190);
		return new CardPoint(291 + (slot * 29), 190);
	}

	private void clearCards(Player player) {
		for (Row row : Row.values()) {
			for (int slot = 0; slot < BlackjackInterface.MAX_CARDS_PER_ROW; slot++) {
				int component = BlackjackInterface.cardComponent(row, slot);
				if (component == -1)
					continue;
				player.getPacketSender().setGraphic(BlackjackInterface.MAIN_INTERFACE_ID, component, BlackjackInterface.CARD_BLANK_SPRITE);
				player.getPacketSender().setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, true);
				player.getPacketSender().setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, DECK_X, DECK_Y);
			}
		}
	}

	private void renderMain(Player player) {
		PacketSender ps = player.getPacketSender();
		ensureFairSeeds(player);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_BACKGROUND, transparentBackground);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_TRANSPARENT_BACKGROUND, true);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_PROVABLY_FAIR_BUTTON, true);
		BlackjackInterface.setComponentsHidden(player, BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_RULES_OVERLAY_COMPONENTS, !rulesOpen);
		BlackjackInterface.setComponentsHidden(player, BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_PROVABLY_FAIR_COMPONENTS, true);
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_STATS_TEXT,
				"<col=ffffff>Your session stats:<br>- Wins: <col=00ff00>" + wins
						+ "<br><col=ffffff>- Losses: <col=ff0000>" + losses
						+ "<br><col=ffffff>- Profit: " + profitText());
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_RULES_LINE_ONE_TEXT,
				"DEALER MUST DRAW TO 16 AND STAND ON ALL 17'S");
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_RULES_LINE_TWO_TEXT,
				"INSURANCE PAYS 2-1   |   BJ PAYS 3-2");
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_RESULT_TEXT, result);
		renderProvablyFair(player);
		renderCards(player);
		renderScores(player);
	}

	private void renderSide(Player player) {
		PacketSender ps = player.getPacketSender();
		boolean insuranceVisible = stage == Stage.INSURANCE;
		BlackjackInterface.setComponentsHidden(player, BlackjackInterface.SIDE_INTERFACE_ID, BlackjackInterface.SIDE_NORMAL_COMPONENTS, insuranceVisible);
		BlackjackInterface.setComponentsHidden(player, BlackjackInterface.SIDE_INTERFACE_ID, BlackjackInterface.SIDE_INSURANCE_COMPONENTS, !insuranceVisible);
		if (insuranceVisible)
			return;
		ps.sendString(BlackjackInterface.SIDE_INTERFACE_ID, BlackjackInterface.SIDE_BALANCE_TEXT,
				"<col=ffffff>Balance: <col=00ff00>" + NumberUtils.formatNumber(currency.balance(player)) + " " + currency.shortName);
		ps.sendString(BlackjackInterface.SIDE_INTERFACE_ID, BlackjackInterface.SIDE_CURRENCY_TEXT, "(change currency)");
		ps.sendString(BlackjackInterface.SIDE_INTERFACE_ID, BlackjackInterface.SIDE_BET_TEXT,
				"  " + NumberUtils.formatNumber(betAmount) + " " + currency.shortName);
	}

	private void renderCards(Player player) {
		for (int slot = 0; slot < BlackjackInterface.MAX_CARDS_PER_ROW; slot++) {
			renderDealerCard(player, slot);
			renderHandCard(player, Row.HAND_ONE, 0, slot);
			renderHandCard(player, Row.HAND_TWO, 1, slot);
		}
	}

	private void renderDealerCard(Player player, int slot) {
		int component = BlackjackInterface.cardComponent(Row.DEALER, slot);
		if (component == -1)
			return;
		if (slot >= dealer.size()) {
			player.getPacketSender().setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, true);
			return;
		}
		BlackjackCard card = dealer.get(slot);
		player.getPacketSender().setGraphic(BlackjackInterface.MAIN_INTERFACE_ID, component,
				slot == 1 && !dealerHoleRevealed ? BlackjackInterface.CARD_BACK_SPRITE : card.spriteId);
		player.getPacketSender().setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, false);
		CardPoint point = cardPoint(Row.DEALER, slot);
		player.getPacketSender().setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, point.x, point.y);
	}

	private void renderHandCard(Player player, Row row, int handIndex, int slot) {
		int component = BlackjackInterface.cardComponent(row, slot);
		if (component == -1)
			return;
		if (handIndex >= hands.size() || slot >= hands.get(handIndex).size()) {
			player.getPacketSender().setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, true);
			return;
		}
		BlackjackCard card = hands.get(handIndex).get(slot);
		player.getPacketSender().setGraphic(BlackjackInterface.MAIN_INTERFACE_ID, component, card.spriteId);
		player.getPacketSender().setHidden(BlackjackInterface.MAIN_INTERFACE_ID, component, false);
		CardPoint point = cardPoint(row, slot);
		player.getPacketSender().setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, component, point.x, point.y);
	}

	private void renderScores(Player player) {
		PacketSender ps = player.getPacketSender();
		String dealerScore = dealer.size() == 0 ? "" : dealerHoleRevealed ? dealer.displayValue() : cardDisplayValue(dealer.get(0));
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_DEALER_SCORE_TEXT, dealerScore);
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_HAND_ONE_SCORE_TEXT, handScoreText(0));
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_HAND_TWO_SCORE_TEXT, handScoreText(1));
		boolean splitVisible = hands.size() > 1;
		setScoreBox(player, BlackjackInterface.MAIN_DEALER_SCORE_BOX, BlackjackInterface.MAIN_DEALER_SCORE_GLOW,
				BlackjackInterface.MAIN_DEALER_SCORE_TEXT, 211, 133, false);
		setScoreBox(player, BlackjackInterface.MAIN_HAND_ONE_SCORE_BOX, BlackjackInterface.MAIN_HAND_ONE_SCORE_GLOW,
				BlackjackInterface.MAIN_HAND_ONE_SCORE_TEXT, splitVisible ? 148 : 211, 291, false);
		setScoreBox(player, BlackjackInterface.MAIN_HAND_TWO_SCORE_BOX, BlackjackInterface.MAIN_HAND_TWO_SCORE_GLOW,
				BlackjackInterface.MAIN_HAND_TWO_SCORE_TEXT, 292, 291, !splitVisible);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_HAND_ONE_ARROW, !splitVisible);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_HAND_TWO_ARROW, !splitVisible);
	}

	private void setScoreBox(Player player, int boxComponent, int glowComponent, int textComponent, int x, int y, boolean hidden) {
		PacketSender ps = player.getPacketSender();
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, boxComponent, hidden);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, glowComponent, true);
		ps.setHidden(BlackjackInterface.MAIN_INTERFACE_ID, textComponent, hidden);
		ps.setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, boxComponent, x, y);
		ps.setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, glowComponent, x - 3, y - 3);
		ps.setAlignment(BlackjackInterface.MAIN_INTERFACE_ID, textComponent, x, y + 6);
	}

	private String cardDisplayValue(BlackjackCard card) {
		return card.rank == BlackjackCard.Rank.ACE ? "1 / 11" : String.valueOf(card.blackjackValue());
	}

	private void renderProvablyFair(Player player) {
		PacketSender ps = player.getPacketSender();
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_CLIENT_SEED_BUTTON,
				"<img=31:2> <col=ffbe71>Client Seed: <col=00ff00>" + clientSeed + " <col=ffbe71>(edit)");
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_SERVER_SEED_BUTTON,
				"<img=332:2> <col=ffbe71>Server Seed: <col=ff0000>" + serverSeedDisplay());
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_SERVER_HASH_BUTTON,
				"<img=333:2> <col=ffbe71>Server Seed Hashed: <col=ff0000>" + shortHash(serverSeedHash()));
		ps.sendString(BlackjackInterface.MAIN_INTERFACE_ID, BlackjackInterface.MAIN_NONCE_BUTTON,
				"<img=334:2> <col=ffbe71>Hand Number (nonce): <col=00ffff>" + visibleNonce);
	}

	private String handScoreText(int index) {
		if (index >= hands.size())
			return index == 0 && canPlaceBet() ? "Place bets" : "";
		BlackjackHand hand = hands.get(index);
		String prefix = hands.size() > 1 ? "H" + (index + 1) + ": " : "";
		String suffix = index == activeHand && stage == Stage.PLAYER_TURN ? " *" : "";
		return prefix + hand.displayValue() + suffix;
	}

	private String sideHint() {
		return switch (stage) {
			case BETTING, ROUND_OVER -> "Ready";
			case INSURANCE -> "Dealer shows ace";
			case PLAYER_TURN -> "Hand " + (activeHand + 1) + " / " + hands.size();
			case DEALING, DEALER_TURN -> "Please wait";
		};
	}

	private String profitText() {
		String color = profit >= 0 ? "00ff00" : "ff0000";
		return "<col=" + color + ">" + NumberUtils.formatNumber(profit) + " " + currency.shortName + "</col>";
	}

	private String handTitle(int index) {
		return hands.size() > 1 ? "Hand " + (index + 1) : "Hand";
	}

	private String signed(long amount) {
		String sign = amount > 0 ? "+" : "";
		return sign + NumberUtils.formatNumber(amount);
	}

	private void ensureFairSeeds(Player player) {
		if (clientSeed != null && serverSeed != null)
			return;
		if (clientSeed == null) {
			String name = player == null || player.getName() == null ? "player" : player.getName();
			clientSeed = sanitizeSeed(name + "-" + randomHex(4));
		}
		if (serverSeed == null)
			serverSeed = randomHex(32);
	}

	private void rotateServerSeed() {
		serverSeed = randomHex(32);
		revealedServerSeed = "";
		serverSeedRevealed = false;
	}

	private long seedForHand() {
		byte[] hash = sha256(serverSeed + ":" + clientSeed + ":" + visibleNonce);
		long seed = 0L;
		for (int i = 0; i < 8; i++)
			seed = (seed << 8) | (hash[i] & 0xffL);
		return seed;
	}

	private String serverSeedHash() {
		return hex(sha256(serverSeed));
	}

	private String serverSeedDisplay() {
		return serverSeedRevealed ? revealedServerSeed : "Click to reveal or shuffle";
	}

	private String shortHash(String hash) {
		return hash.length() <= 16 ? hash : hash.substring(0, 16) + "...";
	}

	private String sanitizeSeed(String seed) {
		if (seed == null || seed.isBlank())
			return "reason-" + randomHex(4);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < seed.length() && builder.length() < 32; i++) {
			char c = seed.charAt(i);
			if (c >= 32 && c <= 126)
				builder.append(c);
		}
		return builder.length() == 0 ? "reason-" + randomHex(4) : builder.toString();
	}

	private static String randomHex(int bytes) {
		byte[] seed = new byte[bytes];
		SECURE_RANDOM.nextBytes(seed);
		return hex(seed);
	}

	private static byte[] sha256(String input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private static String hex(byte[] bytes) {
		char[] chars = new char[bytes.length * 2];
		char[] alphabet = "0123456789abcdef".toCharArray();
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			chars[i * 2] = alphabet[value >>> 4];
			chars[i * 2 + 1] = alphabet[value & 0x0f];
		}
		return new String(chars);
	}

	private enum Stage {
		BETTING,
		DEALING,
		INSURANCE,
		PLAYER_TURN,
		DEALER_TURN,
		ROUND_OVER
	}

	public enum Row {
		DEALER,
		HAND_ONE,
		HAND_TWO;

		public static Row forHand(int index) {
			return index == 0 ? HAND_ONE : HAND_TWO;
		}
	}

	private record CardPoint(int x, int y) {
	}
}
