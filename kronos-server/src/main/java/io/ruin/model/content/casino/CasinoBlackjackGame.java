package io.ruin.model.content.casino;

import io.ruin.model.content.casino.provablyfair.ProvablyFairBlackjack;
import io.ruin.model.content.casino.provablyfair.seeds.ClientSeed;
import io.ruin.model.content.casino.provablyfair.seeds.ServerSeed;
import io.ruin.model.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Single-player-vs-house 21. State + rules only -- packet sending and component wiring
 * lives in CasinoBlackjackInterface. Ported from the source casino minigame
 * (BlackjackGame.java / BlackjackInterface.java), adapted off ScheduledExecutorService
 * onto this codebase's own tick-based EventWorker sequencing.
 */
public class CasinoBlackjackGame {

	public enum Outcome {
		NONE, PLAYER_BLACKJACK, PLAYER_BUST, DEALER_BUST, PLAYER_WIN, DEALER_WIN, PUSH
	}

	private final Player player;
	private final Deque<CardSprite> deck = new ArrayDeque<>();
	private final List<CardSprite> playerHand = new java.util.ArrayList<>();
	private final List<CardSprite> dealerHand = new java.util.ArrayList<>();

	private int betAmount = 0;
	private boolean roundActive = false;
	private boolean playerStood = false;
	private Outcome outcome = Outcome.NONE;

	// Provably fair: server seed rotates (and reveals) once per settled round; client seed
	// is player-controlled and stays fixed until they change it; nonce is a per-round counter
	// that's part of the shuffle input even though the seed rotates too, matching the
	// library's shuffle(deck, clientSeed, serverSeed, nonce) signature.
	private ClientSeed clientSeed = new ClientSeed(null);
	private ServerSeed serverSeed = new ServerSeed(null);
	private int roundNonce = 0;

	public CasinoBlackjackGame(Player player) {
		this.player = player;
	}

	public String getClientSeed() {
		return clientSeed.getSeed();
	}

	public void setClientSeed(String seed) {
		this.clientSeed = new ClientSeed(seed);
	}

	/** Unrevealed -- safe to show before a round starts so the player can commit their client seed. */
	public String getServerSeedHash() {
		return serverSeed.getHashedSeed();
	}

	/** Only call after a round has settled -- this is the secret that made the shuffle verifiable. */
	public String revealServerSeed() {
		serverSeed.setHasBeenRevealed(true);
		return serverSeed.getSeed();
	}

	public int getRoundNonce() {
		return roundNonce;
	}

	/** Starts a fresh, unrevealed server seed for the next round. Call after revealing the previous one. */
	private void rotateServerSeed() {
		serverSeed = new ServerSeed(null);
		roundNonce = 0;
	}

	public int getBetAmount() {
		return betAmount;
	}

	public boolean isRoundActive() {
		return roundActive;
	}

	public boolean isPlayerStood() {
		return playerStood;
	}

	public List<CardSprite> getPlayerHand() {
		return playerHand;
	}

	public List<CardSprite> getDealerHand() {
		return dealerHand;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public int getPlayerScore() {
		return score(playerHand);
	}

	public int getDealerScore() {
		return score(dealerHand);
	}

	public boolean addToBet(int amount) {
		if (roundActive)
			return false;
		betAmount += amount;
		return true;
	}

	public int clearBet() {
		int refund = betAmount;
		betAmount = 0;
		return refund;
	}

	/** Shuffles a fresh shoe and deals the opening two cards to player and dealer. */
	public void startRound() {
		playerHand.clear();
		dealerHand.clear();
		outcome = Outcome.NONE;
		playerStood = false;
		roundActive = true;

		deck.clear();
		List<CardSprite> shoe = new ArrayList<>(Arrays.asList(CardSprite.freshDeck()));
		ProvablyFairBlackjack.shuffle(shoe, clientSeed.getSeed(), serverSeed.getSeed(), roundNonce);
		deck.addAll(shoe);

		playerHand.add(deck.poll());
		dealerHand.add(deck.poll());
		playerHand.add(deck.poll());
		dealerHand.add(deck.poll());

		if (getPlayerScore() == 21) {
			outcome = Outcome.PLAYER_BLACKJACK;
			roundActive = false;
		}
	}

	public void hit() {
		if (!roundActive || playerStood)
			return;
		playerHand.add(deck.poll());
		if (getPlayerScore() > 21) {
			outcome = Outcome.PLAYER_BUST;
			roundActive = false;
		} else if (getPlayerScore() == 21) {
			stand();
		}
	}

	public void stand() {
		if (!roundActive || playerStood)
			return;
		playerStood = true;
	}

	/** @return true if the dealer drew a card and should keep going. */
	public boolean dealerDrawStep() {
		if (getDealerScore() >= 17) {
			resolve();
			return false;
		}
		dealerHand.add(deck.poll());
		if (getDealerScore() > 21) {
			outcome = Outcome.DEALER_BUST;
			roundActive = false;
			return false;
		}
		return true;
	}

	private void resolve() {
		int p = getPlayerScore();
		int d = getDealerScore();
		if (d > 21) {
			outcome = Outcome.DEALER_BUST;
		} else if (p > d) {
			outcome = Outcome.PLAYER_WIN;
		} else if (d > p) {
			outcome = Outcome.DEALER_WIN;
		} else {
			outcome = Outcome.PUSH;
		}
		roundActive = false;
	}

	/** @return coins to pay out for the resolved outcome (0 for a loss). */
	public int payout() {
		return switch (outcome) {
			case PLAYER_BLACKJACK -> betAmount * 3;
			case DEALER_BUST, PLAYER_WIN -> betAmount * 2;
			case PUSH -> betAmount;
			default -> 0;
		};
	}

	/**
	 * Reveals the server seed that was actually used for this round's shuffle, then rotates to a
	 * fresh (unrevealed) one for the next round. Call once, right after a round settles.
	 *
	 * @return the revealed server seed, the client seed, and the nonce -- everything a player needs
	 *         to independently re-run the shuffle and verify it themselves.
	 */
	public String[] revealAndRotateSeed() {
		String[] proof = {revealServerSeed(), clientSeed.getSeed(), String.valueOf(roundNonce)};
		rotateServerSeed();
		return proof;
	}

	public String outcomeMessage() {
		return switch (outcome) {
			case PLAYER_BLACKJACK -> "Blackjack! You win with 21 - paid out 3x your bet.";
			case PLAYER_BUST -> "You busted! The house wins.";
			case DEALER_BUST -> "Dealer busted! You win!";
			case PLAYER_WIN -> "You win!";
			case DEALER_WIN -> "The house wins!";
			case PUSH -> "It's a tie - your bet has been returned.";
			default -> "";
		};
	}

	private int score(List<CardSprite> hand) {
		int total = 0;
		int aces = 0;
		for (CardSprite card : hand) {
			total += card.getValue(true);
			if (card.isAce())
				aces++;
		}
		while (total > 21 && aces > 0) {
			total -= 10;
			aces--;
		}
		return total;
	}

	public void reset() {
		playerHand.clear();
		dealerHand.clear();
		deck.clear();
		betAmount = 0;
		roundActive = false;
		playerStood = false;
		outcome = Outcome.NONE;
	}
}
