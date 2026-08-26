package io.ruin.data.impl.economy;

import com.google.gson.annotations.Expose;
import economy.protection.config.EconomyConfig;
import io.ruin.api.utils.JsonUtils;
import io.ruin.data.DataFile;

/** Loads tunable economy-protection knobs from {@code economy/economy_config.json}. */
public class EconomyConfigLoader extends DataFile {

	@Override
	public String path() {
		return "economy/economy_config.json";
	}

	@Override
	public Object fromJson(String fileName, String json) {
		Settings settings = JsonUtils.GSON.fromJson(json, Settings.class);
		EconomyConfig.pkpConversionDivisor = settings.pkpConversionDivisor;
		EconomyConfig.highValueSinkPercent = settings.highValueSinkPercent;
		EconomyConfig.minDamageShare = settings.minDamageShare;
		EconomyConfig.minCombatDurationMs = settings.minCombatDurationMs;
		EconomyConfig.hardBlockSameSubnet = settings.hardBlockSameSubnet;
		return settings;
	}

	private static final class Settings {
		@Expose
		public long pkpConversionDivisor = 1000;
		@Expose
		public int highValueSinkPercent = 5;
		@Expose
		public double minDamageShare = 0.40;
		@Expose
		public long minCombatDurationMs = 15_000;
		@Expose
		public boolean hardBlockSameSubnet = false;
	}
}
