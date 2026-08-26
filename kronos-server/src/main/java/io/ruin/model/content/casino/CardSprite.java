package io.ruin.model.content.casino;

/**
 * Sprite ids 14000-14052, ported from the source casino UI (card sprite export lives in
 * data zelus/data/cache/toml/1_patches/sprite/14000-14052).
 */
public enum CardSprite {

	ACE_OF_SPADES(14000, "Ace of Spades", 1, 11),
	TWO_OF_SPADES(14001, "Two of Spades", 2),
	THREE_OF_SPADES(14002, "Three of Spades", 3),
	FOUR_OF_SPADES(14003, "Four of Spades", 4),
	FIVE_OF_SPADES(14004, "Five of Spades", 5),
	SIX_OF_SPADES(14005, "Six of Spades", 6),
	SEVEN_OF_SPADES(14006, "Seven of Spades", 7),
	EIGHT_OF_SPADES(14007, "Eight of Spades", 8),
	NINE_OF_SPADES(14008, "Nine of Spades", 9),
	TEN_OF_SPADES(14009, "Ten of Spades", 10),
	JACK_OF_SPADES(14010, "Jack of Spades", 10),
	QUEEN_OF_SPADES(14011, "Queen of Spades", 10),
	KING_OF_SPADES(14012, "King of Spades", 10),

	ACE_OF_DIAMONDS(14013, "Ace of Diamonds", 1, 11),
	TWO_OF_DIAMONDS(14014, "Two of Diamonds", 2),
	THREE_OF_DIAMONDS(14015, "Three of Diamonds", 3),
	FOUR_OF_DIAMONDS(14016, "Four of Diamonds", 4),
	FIVE_OF_DIAMONDS(14017, "Five of Diamonds", 5),
	SIX_OF_DIAMONDS(14018, "Six of Diamonds", 6),
	SEVEN_OF_DIAMONDS(14019, "Seven of Diamonds", 7),
	EIGHT_OF_DIAMONDS(14020, "Eight of Diamonds", 8),
	NINE_OF_DIAMONDS(14021, "Nine of Diamonds", 9),
	TEN_OF_DIAMONDS(14022, "Ten of Diamonds", 10),
	JACK_OF_DIAMONDS(14023, "Jack of Diamonds", 10),
	QUEEN_OF_DIAMONDS(14024, "Queen of Diamonds", 10),
	KING_OF_DIAMONDS(14025, "King of Diamonds", 10),

	ACE_OF_CLUBS(14026, "Ace of Clubs", 1, 11),
	TWO_OF_CLUBS(14027, "Two of Clubs", 2),
	THREE_OF_CLUBS(14028, "Three of Clubs", 3),
	FOUR_OF_CLUBS(14029, "Four of Clubs", 4),
	FIVE_OF_CLUBS(14030, "Five of Clubs", 5),
	SIX_OF_CLUBS(14031, "Six of Clubs", 6),
	SEVEN_OF_CLUBS(14032, "Seven of Clubs", 7),
	EIGHT_OF_CLUBS(14033, "Eight of Clubs", 8),
	NINE_OF_CLUBS(14034, "Nine of Clubs", 9),
	TEN_OF_CLUBS(14035, "Ten of Clubs", 10),
	JACK_OF_CLUBS(14036, "Jack of Clubs", 10),
	QUEEN_OF_CLUBS(14037, "Queen of Clubs", 10),
	KING_OF_CLUBS(14038, "King of Clubs", 10),

	ACE_OF_HEARTS(14039, "Ace of Hearts", 1, 11),
	TWO_OF_HEARTS(14040, "Two of Hearts", 2),
	THREE_OF_HEARTS(14041, "Three of Hearts", 3),
	FOUR_OF_HEARTS(14042, "Four of Hearts", 4),
	FIVE_OF_HEARTS(14043, "Five of Hearts", 5),
	SIX_OF_HEARTS(14044, "Six of Hearts", 6),
	SEVEN_OF_HEARTS(14045, "Seven of Hearts", 7),
	EIGHT_OF_HEARTS(14046, "Eight of Hearts", 8),
	NINE_OF_HEARTS(14047, "Nine of Hearts", 9),
	TEN_OF_HEARTS(14048, "Ten of Hearts", 10),
	JACK_OF_HEARTS(14049, "Jack of Hearts", 10),
	QUEEN_OF_HEARTS(14050, "Queen of Hearts", 10),
	KING_OF_HEARTS(14051, "King of Hearts", 10);

	public static final int CARD_BACK_SPRITE = 14052;

	private static final CardSprite[] VALUES = values();

	private final int spriteId;
	private final String cardName;
	private final int value;
	private final int aceValue;

	CardSprite(int spriteId, String cardName, int value) {
		this(spriteId, cardName, value, value);
	}

	CardSprite(int spriteId, String cardName, int value, int aceValue) {
		this.spriteId = spriteId;
		this.cardName = cardName;
		this.value = value;
		this.aceValue = aceValue;
	}

	public int getSpriteId() {
		return spriteId;
	}

	public String getCardName() {
		return cardName;
	}

	/**
	 * @param aceHigh whether an ace should count as 11 instead of 1.
	 */
	public int getValue(boolean aceHigh) {
		return aceHigh ? aceValue : value;
	}

	public boolean isAce() {
		return value == 1;
	}

	public static CardSprite[] freshDeck() {
		return VALUES.clone();
	}
}
