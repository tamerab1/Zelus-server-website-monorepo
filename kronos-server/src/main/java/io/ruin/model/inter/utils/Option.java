package io.ruin.model.inter.utils;

import io.ruin.model.entity.player.Player;

import java.util.function.Consumer;

public class Option {

	public final String name;

	public final Consumer<Player> consumer;

	public Option(String name) {
		this(name, p -> {
		});
	}

	public Option(String name, Runnable runnable) {
		this(name, p -> runnable.run());
	}

	public Option(String name, Consumer<Player> consumer) {
		this.name = name;
		this.consumer = consumer;
	}

	/** Marker action for {@link #info}: an OptionScroll row that is display-only. */
	private static final Consumer<Player> INFO_ROW = p -> {
	};

	/**
	 * A display-only row (headers, status lines, spacers) for an OptionScroll: it is not made
	 * clickable at all, so the client never shows "Please wait..." for it.
	 */
	public static Option info(String name) {
		return new Option(name, INFO_ROW);
	}

	public boolean isInfo() {
		return consumer == INFO_ROW;
	}

	public void select(Player player) {
		consumer.accept(player);
	}

}