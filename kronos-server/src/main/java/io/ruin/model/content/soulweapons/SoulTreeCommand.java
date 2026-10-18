package io.ruin.model.content.soulweapons;

import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Direction;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;

/**
 * ::soultree (2026-10-18): teleports to the front of the Soul Sanctuary at home, one tile south of its floor, facing
 * north to the Reliquary of Souls (centre 3124,3491). Same rules as ::home: refused deep in the wilderness or in a PvP
 * zone, cancelled by a teleport block, the Zelus cyan teleport effect. The Soul Tree window itself opens from the
 * Reliquary's Commune option or the weapon's Check window.
 */
public final class SoulTreeCommand {
	private static final int X = 3124, Y = 3488, Z = 0;

	private SoulTreeCommand() {
	}

	public static void teleport(Player player) {
		if (player.wildernessLevel > 20 || player.pvpAttackZone) {
			player.sendMessage("You can't use this command from where you are standing.");
			return;
		}
		player.getMovement().startTeleport(event -> {
			event.setCancelCondition(() -> player.teleportListener != null && !player.teleportListener.allow(player));
			player.privateSound(200, 0, 10);
			ModernTeleport.playEffect(event, player);
			player.getMovement().teleport(X, Y, Z);
			player.face(Direction.NORTH);
			player.sendMessage(SoulWeapons.TAG + "You feel your soul drawn to the Reliquary...");
		});
	}
}
