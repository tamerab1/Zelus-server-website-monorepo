package io.ruin.model.content;

import io.ruin.model.World;
import io.ruin.model.map.object.actions.ObjectAction;

/**
 * The Discord Statue (object 62443, .dev/cache-restore-tool CreateDecorObjects newcustoms_1018_objects.txt,
 * 2026-10-18): option 1 "Join Discord" opens the same invite as ::discord. Its partner, the Donator Platform 62444, is
 * walk-through decoration with no options. Both are placed with custom_objects.json.
 */
public class DiscordStatue {
	public static final int OBJECT = 65523;

	public static void register() {
		ObjectAction.register(OBJECT, 1, (player, obj) ->
				player.openUrl(World.type.getWorldName() + " Discord", "https://discord.gg/sVrcatBdyG"));
	}
}
