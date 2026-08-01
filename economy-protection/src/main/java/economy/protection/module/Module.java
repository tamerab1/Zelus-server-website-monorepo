package economy.protection.module;

import core.module.api.IModule;
import economy.protection.antifarm.KillValidationPipeline;
import economy.protection.bounty.BountyContractsManager;
import economy.protection.classification.ItemClassification;
import economy.protection.escort.EscortDutyManager;
import economy.protection.ground.GroundItemGuard;
import economy.protection.pvm.PvmPointsManager;
import economy.protection.spawn.Attributes;
import economy.protection.spawn.SpawnCommandHook;

/**
 * Semi-Spawn & Economy Protection module.
 *
 * Item classification and economy config are loaded separately by
 * {@code io.ruin.data.impl.economy.ItemClassificationLoader} / {@code EconomyConfigLoader}
 * (plain {@code DataFile}s, loaded during {@code Server.loadData()} — before this,
 * or any module's, {@code start()} runs), so nothing needs to be registered for those here.
 *
 * The SPAWNABLE ObjType overrides (tradeable/value/alch) are applied here rather than in the
 * DataFile loader itself, since {@code start()} is the first point guaranteed to run after
 * every DataFile — including {@code item_info}, which otherwise races it — has finished.
 */
public class Module implements IModule {

	@Override
	public void start() {
		ItemClassification.applyObjTypeOverrides();
		Attributes.register();
		SpawnCommandHook.register();
		GroundItemGuard.register();
		KillValidationPipeline.register();
		PvmPointsManager.register();
		BountyContractsManager.register();
		EscortDutyManager.register();
	}
}
