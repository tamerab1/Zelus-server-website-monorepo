package io.ruin.model.content.blackjack;

public enum BlackjackCard {
	ACE_CLUBS(Rank.ACE, Suit.CLUBS, 5844),
	TWO_CLUBS(Rank.TWO, Suit.CLUBS, 5845),
	THREE_CLUBS(Rank.THREE, Suit.CLUBS, 5846),
	FOUR_CLUBS(Rank.FOUR, Suit.CLUBS, 5847),
	FIVE_CLUBS(Rank.FIVE, Suit.CLUBS, 5848),
	SIX_CLUBS(Rank.SIX, Suit.CLUBS, 5849),
	SEVEN_CLUBS(Rank.SEVEN, Suit.CLUBS, 5850),
	EIGHT_CLUBS(Rank.EIGHT, Suit.CLUBS, 5851),
	NINE_CLUBS(Rank.NINE, Suit.CLUBS, 5852),
	TEN_CLUBS(Rank.TEN, Suit.CLUBS, 5853),
	JACK_CLUBS(Rank.JACK, Suit.CLUBS, 5854),
	QUEEN_CLUBS(Rank.QUEEN, Suit.CLUBS, 5855),
	KING_CLUBS(Rank.KING, Suit.CLUBS, 5856),
	ACE_DIAMONDS(Rank.ACE, Suit.DIAMONDS, 5857),
	TWO_DIAMONDS(Rank.TWO, Suit.DIAMONDS, 5858),
	THREE_DIAMONDS(Rank.THREE, Suit.DIAMONDS, 5859),
	FOUR_DIAMONDS(Rank.FOUR, Suit.DIAMONDS, 5860),
	FIVE_DIAMONDS(Rank.FIVE, Suit.DIAMONDS, 5861),
	SIX_DIAMONDS(Rank.SIX, Suit.DIAMONDS, 5862),
	SEVEN_DIAMONDS(Rank.SEVEN, Suit.DIAMONDS, 5863),
	EIGHT_DIAMONDS(Rank.EIGHT, Suit.DIAMONDS, 5864),
	NINE_DIAMONDS(Rank.NINE, Suit.DIAMONDS, 5865),
	TEN_DIAMONDS(Rank.TEN, Suit.DIAMONDS, 5866),
	JACK_DIAMONDS(Rank.JACK, Suit.DIAMONDS, 5867),
	QUEEN_DIAMONDS(Rank.QUEEN, Suit.DIAMONDS, 5868),
	KING_DIAMONDS(Rank.KING, Suit.DIAMONDS, 5869),
	ACE_HEARTS(Rank.ACE, Suit.HEARTS, 5870),
	TWO_HEARTS(Rank.TWO, Suit.HEARTS, 5871),
	THREE_HEARTS(Rank.THREE, Suit.HEARTS, 5872),
	FOUR_HEARTS(Rank.FOUR, Suit.HEARTS, 5873),
	FIVE_HEARTS(Rank.FIVE, Suit.HEARTS, 5874),
	SIX_HEARTS(Rank.SIX, Suit.HEARTS, 5875),
	SEVEN_HEARTS(Rank.SEVEN, Suit.HEARTS, 5876),
	EIGHT_HEARTS(Rank.EIGHT, Suit.HEARTS, 5877),
	NINE_HEARTS(Rank.NINE, Suit.HEARTS, 5878),
	TEN_HEARTS(Rank.TEN, Suit.HEARTS, 5879),
	JACK_HEARTS(Rank.JACK, Suit.HEARTS, 5880),
	QUEEN_HEARTS(Rank.QUEEN, Suit.HEARTS, 5881),
	KING_HEARTS(Rank.KING, Suit.HEARTS, 5882),
	ACE_SPADES(Rank.ACE, Suit.SPADES, 5831),
	TWO_SPADES(Rank.TWO, Suit.SPADES, 5832),
	THREE_SPADES(Rank.THREE, Suit.SPADES, 5833),
	FOUR_SPADES(Rank.FOUR, Suit.SPADES, 5834),
	FIVE_SPADES(Rank.FIVE, Suit.SPADES, 5835),
	SIX_SPADES(Rank.SIX, Suit.SPADES, 5836),
	SEVEN_SPADES(Rank.SEVEN, Suit.SPADES, 5837),
	EIGHT_SPADES(Rank.EIGHT, Suit.SPADES, 5838),
	NINE_SPADES(Rank.NINE, Suit.SPADES, 5839),
	TEN_SPADES(Rank.TEN, Suit.SPADES, 5840),
	JACK_SPADES(Rank.JACK, Suit.SPADES, 5841),
	QUEEN_SPADES(Rank.QUEEN, Suit.SPADES, 5842),
	KING_SPADES(Rank.KING, Suit.SPADES, 5843);

	public static final BlackjackCard[] VALUES = values();

	public final Rank rank;
	public final Suit suit;
	public final int spriteId;

	BlackjackCard(Rank rank, Suit suit, int spriteId) {
		this.rank = rank;
		this.suit = suit;
		this.spriteId = spriteId;
	}

	public int blackjackValue() {
		return rank.value;
	}

	public boolean sameSplitValue(BlackjackCard other) {
		return other != null && blackjackValue() == other.blackjackValue();
	}

	public enum Rank {
		ACE(11, "A"),
		TWO(2, "2"),
		THREE(3, "3"),
		FOUR(4, "4"),
		FIVE(5, "5"),
		SIX(6, "6"),
		SEVEN(7, "7"),
		EIGHT(8, "8"),
		NINE(9, "9"),
		TEN(10, "10"),
		JACK(10, "J"),
		QUEEN(10, "Q"),
		KING(10, "K");

		public final int value;
		public final String label;

		Rank(int value, String label) {
			this.value = value;
			this.label = label;
		}
	}

	public enum Suit {
		CLUBS("C"),
		DIAMONDS("D"),
		HEARTS("H"),
		SPADES("S");

		public final String label;

		Suit(String label) {
			this.label = label;
		}
	}
}
