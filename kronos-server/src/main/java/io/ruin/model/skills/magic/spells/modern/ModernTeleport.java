package io.ruin.model.skills.magic.spells.modern;

import io.ruin.model.content.HomeHandler;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Position;
import io.ruin.model.skills.magic.Spell;
import io.ruin.model.skills.magic.rune.Rune;

public class ModernTeleport extends Spell {

	public static final ModernTeleport VARROCK_TELEPORT = new ModernTeleport(25, 35.0, new Bounds[]{new Bounds(3211, 3422, 3214, 3424, 0), new Bounds(3163, 3475, 3166, 3478, 0)}, Rune.LAW.toItem(1), Rune.AIR.toItem(3), Rune.FIRE.toItem(1));
	public static final ModernTeleport LUMBRIDGE_TELEPORT = new ModernTeleport(31, 41.0, new Bounds(3221, 3218, 3224, 3219, 0), Rune.LAW.toItem(1), Rune.AIR.toItem(3), Rune.EARTH.toItem(1));
	public static final ModernTeleport FALADOR_TELEPORT = new ModernTeleport(37, 48.0, new Bounds(2964, 3378, 2966, 3379, 0), Rune.LAW.toItem(1), Rune.AIR.toItem(3), Rune.WATER.toItem(1));
	public static final ModernTeleport CAMELOT_TELEPORT = new ModernTeleport(45, 55.5, new Bounds[]{new Bounds(2756, 3476, 2759, 3480, 0), new Bounds(2726, 3485, 2727, 3486, 0)}, Rune.LAW.toItem(1), Rune.AIR.toItem(5));
	public static final ModernTeleport ARDOUGNE_TELEPORT = new ModernTeleport(51, 61.0, new Bounds(2659, 3304, 2664, 3308, 0), Rune.LAW.toItem(2), Rune.WATER.toItem(2));
	public static final ModernTeleport WATCHTOWER_TELEPORT = new ModernTeleport(58, 68.0, new Bounds[]{new Bounds(2546, 3112, 2547, 3113, 2), new Bounds(2580, 3096, 2584, 3100, 0)}, Rune.LAW.toItem(2), Rune.EARTH.toItem(2));
	public static final ModernTeleport TROLLHEIM_TELEPORT = new ModernTeleport(61, 71.0, new Bounds(2890, 3678, 2893, 3680, 0), Rune.LAW.toItem(2), Rune.FIRE.toItem(2));
	public static final ModernTeleport APE_ATOLL_TELEPORT = new ModernTeleport(64, 74.0, new Bounds(2784, 2785, 2785, 2786, 0), Rune.LAW.toItem(2), Rune.FIRE.toItem(2), Rune.WATER.toItem(2));
	public static final ModernTeleport KOUREND_TELEPORT = new ModernTeleport(69, 82.0, new Bounds(1644, 3672, 1642, 3674, 0), Rune.LAW.toItem(2), Rune.SOUL.toItem(2), Rune.WATER.toItem(4), Rune.FIRE.toItem(5));


	public int getLvlReq() {
		return lvlReq;
	}

	public double getXp() {
		return xp;
	}

	public Item[] getRunes() {
		return runes;
	}

	public ModernTeleport(int lvlReq, double xp, Bounds bounds, Item... runes) {
		this.lvlReq = lvlReq;
		this.xp = xp;
		this.runes = runes;
		registerClick(lvlReq, xp, true, runes, (p, i) -> teleport(p, bounds));
	}

	public ModernTeleport(int lvlReq, double xp, Bounds[] bounds, Item... runes) {
		this.lvlReq = lvlReq;
		this.xp = xp;
		this.runes = runes;
		registerClick(lvlReq, xp, true, runes, (p, i) -> teleport(p, bounds[i]));
	}

	public ModernTeleport(int lvlReq, double xp, int x, int y, int z, Item... runes) {
		this.lvlReq = lvlReq;
		this.xp = xp;
		this.runes = runes;
		registerClick(lvlReq, xp, true, runes, (p, i) -> teleport(p, x, y, z));
	}

	private final int lvlReq;
	private final double xp;
	private final Item[] runes;

	public static boolean teleport(Player player, Bounds bounds) {
		return teleport(player, bounds.randomX(), bounds.randomY(), bounds.z);
	}

	public static boolean teleport(Player player, Position position) {
		return teleport(player, position.getX(), position.getY(), position.getZ());
	}

	public static boolean teleport(Player player, int x, int y, int z) {
		return player.getMovement().startTeleport(e -> {
			e.setCancelCondition(() -> player.teleportListener != null && !player.teleportListener.allow(player));
			if (player.getInventory().contains(25104)) {
				player.sendMessage("The crystal of memories stores your last location as an available teleport.");
				player.crystalMemoryPosition = player.getPosition().copy();
			}
			player.publicSound(200);
			playEffect(e, player);
			player.getMovement().teleport(x, y, z);
		});
	}

	/**
	 * The server's main teleport effect: the Arceuus teleport (anim 8805 / 8807, gfx 1822 / 1823) recoloured cyan in
	 * the cache as spotanims 4291 / 4292 (.dev/cache-restore-tool/teleport_cyan.txt).
	 */
	public static final int CYAN_TELEPORT_ANIM = 8805, CYAN_TELEPORT_GFX = 6698;
	public static final int CYAN_ARRIVE_ANIM = 8807, CYAN_ARRIVE_GFX = 6699;

	/**
	 * The teleport animations a player can pick from the home teleport's "Animation" option (saved as
	 * {@code player.teleportStyle}, so never reorder). ZELUS is the default; GRANDMASTER is the Combat Achievements
	 * tier 6 teleport and LAST_RECALL the Trailblazer league one.
	 */
	public enum TeleportStyle {
		ZELUS("Zelus (cyan)"),
		STANDARD("Standard"),
		GRANDMASTER("Grandmaster"),
		LAST_RECALL("Last Recall");

		public final String name;

		TeleportStyle(String name) {
			this.name = name;
		}

		public static final TeleportStyle[] VALUES = values();

		public static TeleportStyle of(Player player) {
			int i = player.teleportStyle;
			return i >= 0 && i < VALUES.length ? VALUES[i] : ZELUS;
		}
	}

	public static void chooseStyle(Player player) {
		TeleportStyle current = TeleportStyle.of(player);
		io.ruin.model.inter.utils.Option[] options = new io.ruin.model.inter.utils.Option[TeleportStyle.VALUES.length];
		for (TeleportStyle style : TeleportStyle.VALUES) {
			options[style.ordinal()] = new io.ruin.model.inter.utils.Option(style == current ? style.name + " (current)" : style.name, () -> {
				player.teleportStyle = style.ordinal();
				player.sendMessage("Your teleport animation is now: " + style.name + ".");
			});
		}
		player.dialogue(new io.ruin.model.inter.dialogue.OptionsDialogue("Choose your teleport animation", options));
	}

	/**
	 * The player's chosen teleport animation. ZELUS is the full Arceuus teleport, same timing as the original spell:
	 * the casting animation (5 ticks), then the vanishing animation (3 ticks). Call from inside a teleport event,
	 * then move the player. The move is only queued (applied on the next update), so a reappear animation started on
	 * the last tick here plays at the destination.
	 */
	public static void playEffect(io.ruin.process.event.Event e, Player player) {
		switch (TeleportStyle.of(player)) {
			case STANDARD -> {
				player.animate(714);
				player.graphics(111, 92, 0);
				e.delay(3);
			}
			case GRANDMASTER -> {
				player.animate(9131);
				player.graphics(1945);
				e.delay(5);
				player.animate(9133);
				player.graphics(1951);
			}
			case LAST_RECALL -> {
				player.animate(8794);
				player.graphics(1824);
				e.delay(3);
				player.animate(8796);
				player.graphics(1825);
			}
			default -> {
				player.animate(CYAN_TELEPORT_ANIM);
				player.graphics(CYAN_TELEPORT_GFX, 92, 0);
				e.delay(5);
				player.animate(CYAN_ARRIVE_ANIM);
				player.graphics(CYAN_ARRIVE_GFX, 92, 0);
				e.delay(3);
			}
		}
	}
}