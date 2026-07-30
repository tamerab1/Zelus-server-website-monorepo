package economy.protection.ground;

import economy.protection.classification.ItemClassification;
import io.ruin.HooksV2.Result;
import io.ruin.model.map.ground.GroundItem;

/**
 * Registers on {@link GroundItem.Hook.PreSpawn} so that SPAWNABLE/DISSOLVABLE items
 * silently vanish instead of landing on the ground — closes the
 * spawn-item -> drop -> alt-picks-up -> re-spawn duplication loop.
 */
public class GroundItemGuard {

	public static void register() {
		GroundItem.hooks.register(GroundItem.Hook.PreSpawn.class, GroundItemGuard::handle);
	}

	private static Result handle(GroundItem.Hook.PreSpawn ctx) {
		if (ItemClassification.vanishesOnGround(ctx.item().id)) {
			return Result.Return;
		}
		return Result.Pass;
	}
}
