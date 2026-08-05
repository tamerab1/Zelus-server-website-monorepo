package io.ruin.model.content.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlackjackHand {

	private final List<BlackjackCard> cards = new ArrayList<>(8);
	private int bet;
	private boolean completed;
	private boolean doubled;
	private boolean splitHand;
	private boolean splitAces;
	private boolean settled;

	public BlackjackHand(int bet) {
		this.bet = bet;
	}

	public void add(BlackjackCard card) {
		cards.add(card);
	}

	public BlackjackCard remove(int index) {
		return cards.remove(index);
	}

	public void clear() {
		cards.clear();
		bet = 0;
		completed = false;
		doubled = false;
		splitHand = false;
		splitAces = false;
		settled = false;
	}

	public List<BlackjackCard> cards() {
		return Collections.unmodifiableList(cards);
	}

	public int size() {
		return cards.size();
	}

	public BlackjackCard get(int index) {
		return cards.get(index);
	}

	public int value() {
		int value = 0;
		int aces = 0;
		for (BlackjackCard card : cards) {
			value += card.blackjackValue();
			if (card.rank == BlackjackCard.Rank.ACE)
				aces++;
		}
		while (value > 21 && aces-- > 0)
			value -= 10;
		return value;
	}

	public int hardValue() {
		int value = 0;
		for (BlackjackCard card : cards)
			value += card.rank == BlackjackCard.Rank.ACE ? 1 : card.blackjackValue();
		return value;
	}

	public String displayValue() {
		int hardValue = hardValue();
		int bestValue = value();
		return hardValue != bestValue && bestValue <= 21 ? hardValue + " / " + bestValue : String.valueOf(bestValue);
	}

	public boolean soft() {
		int value = 0;
		int aces = 0;
		for (BlackjackCard card : cards) {
			value += card.blackjackValue();
			if (card.rank == BlackjackCard.Rank.ACE)
				aces++;
		}
		return aces > 0 && value <= 21;
	}

	public boolean blackjack() {
		return cards.size() == 2 && !splitHand && value() == 21;
	}

	public boolean bust() {
		return value() > 21;
	}

	public boolean canDouble() {
		return cards.size() == 2 && !completed && !doubled;
	}

	public boolean canSplit() {
		return cards.size() == 2 && cards.get(0).sameSplitValue(cards.get(1));
	}

	public int bet() {
		return bet;
	}

	public void addBet(int amount) {
		bet += amount;
	}

	public boolean completed() {
		return completed;
	}

	public void complete() {
		completed = true;
	}

	public boolean doubled() {
		return doubled;
	}

	public void markDoubled() {
		doubled = true;
	}

	public boolean splitAces() {
		return splitAces;
	}

	public void setSplitHand(boolean splitHand) {
		this.splitHand = splitHand;
	}

	public void setSplitAces(boolean splitAces) {
		this.splitAces = splitAces;
	}

	public boolean settled() {
		return settled;
	}

	public void settle() {
		settled = true;
		completed = true;
	}
}
