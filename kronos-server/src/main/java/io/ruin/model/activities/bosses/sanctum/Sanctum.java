package io.ruin.model.activities.bosses.sanctum;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;

/**
 * The Sanctum (2026-10-16): Zelus' guardian/boss dungeon.
 * <ul>
 * <li>Hub ("Obsidian Reliquary" in the teleport menu): region 7852, a re-themed copy of the ToB Bloat room without its
 * central tank (.dev/cache-restore-tool/BuildSanctumHub; objects in data/objects/spawns/sanctum.json, the T3 pet showcase
 * in npcs/spawns/sanctum.json). Reached with {@code ::sanctum}. Bank chest, pool, home portal and one portal per wing.</li>
 * <li>Each wing portal puts the player in their own private copy of the God Wars Dungeon ({@link SanctumInstance}),
 * at that wing. No obstacles: the portals carry players straight into the wing.</li>
 * <li>Every wing holds 10 guardians. Kill 10 of the wing's guardians, then the old god's boss door lets you into the
 * boss room (the 10 kills are used up); kill counts show on the God Wars killcount overlay.</li>
 * </ul>
 */
public final class Sanctum {

	public static final Position HUB = new Position(1945, 11040, 0);

	/**
	 * Hub portals along the south wall: objects 60020-60022 (portal 4150 clones named and coloured after their boss,
	 * .dev/cache-restore-tool/build_sanctum_portals.sh, in SanctumWing order), and 60023, Saradomin's, still sealed.
	 */
	private static final int FIRST_PORTAL = 65062, SEALED_PORTAL_ID = 65065, HUB_EXIT = 4389;
	private static final int[][] HUB_PORTALS = {{1946, 11033}, {1950, 11033}, {1955, 11033}};
	private static final int[] SEALED_PORTAL = {1959, 11033};
	private static final int[] HUB_EXIT_POS = {1952, 11045};

	private Sanctum() {
	}

	public static void register() {
		SanctumWing[] wings = SanctumWing.values();
		for (int i = 0; i < wings.length; i++) {
			SanctumWing wing = wings[i];
			ObjectAction.register(FIRST_PORTAL + i, HUB_PORTALS[i][0], HUB_PORTALS[i][1], 0, 1,
					(player, obj) -> SanctumInstance.enter(player, wing));
		}
		ObjectAction.register(SEALED_PORTAL_ID, SEALED_PORTAL[0], SEALED_PORTAL[1], 0, 1,
				(player, obj) -> player.sendMessage("<col=ff0000>This wing of the Sanctum is still sealed.</col>"));
		ObjectAction.register(HUB_EXIT, HUB_EXIT_POS[0], HUB_EXIT_POS[1], 0, 1,
				(player, obj) -> ModernTeleport.teleport(player, World.HOME));
	}

	/** {@code ::sanctum}. */
	public static void teleportToHub(Player player) {
		ModernTeleport.teleport(player, HUB);
	}

}
