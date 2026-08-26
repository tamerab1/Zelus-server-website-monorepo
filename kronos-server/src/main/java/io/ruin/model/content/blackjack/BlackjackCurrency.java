package io.ruin.model.content.blackjack;

import io.ruin.model.entity.player.Player;

public enum BlackjackCurrency {
	PLATINUM_TOKENS("Platinum Tokens", "PT", 13204) {
		@Override
		public int balance(Player player) {
			return player.getInventory().count(iconItemId);
		}

		@Override
		public boolean remove(Player player, int amount) {
			return amount > 0 && player.getInventory().remove(iconItemId, amount) == amount;
		}

		@Override
		public void add(Player player, int amount) {
			if (amount > 0)
				player.getInventory().addOrDrop(iconItemId, amount);
		}
	},
	DONATOR_POINTS("Donator Points", "DP", 8851) {
		@Override
		public int balance(Player player) {
			return player.getDonatorPoints();
		}

		@Override
		public boolean remove(Player player, int amount) {
			if (amount <= 0 || player.getDonatorPoints() < amount)
				return false;
			player.updateDonatorPoints(-amount);
			return true;
		}

		@Override
		public void add(Player player, int amount) {
			if (amount <= 0)
				return;
			if ((long) player.donatorPoints + amount > Integer.MAX_VALUE)
				player.donatorPoints = Integer.MAX_VALUE;
			else
				player.updateDonatorPoints(amount);
		}
	},
	PK_POINTS("PK Points", "PKP", 964) {
		@Override
		public int balance(Player player) {
			return player.getPKPoints();
		}

		@Override
		public boolean remove(Player player, int amount) {
			if (amount <= 0 || player.getPKPoints() < amount)
				return false;
			player.updatePKPoints(-amount);
			return true;
		}

		@Override
		public void add(Player player, int amount) {
			if (amount <= 0)
				return;
			if ((long) player.pkPoints + amount > Integer.MAX_VALUE)
				player.pkPoints = Integer.MAX_VALUE;
			else
				player.updatePKPoints(amount);
		}
	};

	public final String displayName;
	public final String shortName;
	public final int iconItemId;

	BlackjackCurrency(String displayName, String shortName, int iconItemId) {
		this.displayName = displayName;
		this.shortName = shortName;
		this.iconItemId = iconItemId;
	}

	public abstract int balance(Player player);

	public abstract boolean remove(Player player, int amount);

	public abstract void add(Player player, int amount);

	public BlackjackCurrency next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
