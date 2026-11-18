package io.ruin.model.skills.farming.patch.impl;

import io.ruin.model.skills.farming.crop.Crop;
import io.ruin.model.skills.farming.crop.impl.HesporiCrop;
import io.ruin.model.skills.farming.patch.Patch;

public class HesporiPatch extends Patch {

	@Override
	public int getCropVarpbitValue() {
		// the patch multiloc (34630, varbit 7908): 4 seed, 5 / 6 growing, 7 fully grown, 8 the defeated Hespori.
		// (4 growth stages used to map straight onto 4..8, so a grown Hespori showed as the dead plant.)
		if (getProduceCount() > 0)
			return 8;
		int stage = getStage(), total = getPlantedCrop().getTotalStages();
		return getDiseaseStage() << 6 | (stage >= total ? 7 : 4 + Math.min(2, stage));
	}

	@Override
	public void cropInteract() {
		if (getProduceCount() > 0) { // the defeated Hespori: harvesting it gives the reward and clears the patch
			if (!player.getInventory().contains(952, 1)) {
				player.sendMessage("You'll need a spade to harvest the Hespori.");
				return;
			}
			setProduceCount(0);
			io.ruin.model.activities.bosses.Hespori.harvest(player);
			reset(false);
			return;
		}
		if (isDead()) {
			clear();
			return;
		}
		if (getStage() < getPlantedCrop().getTotalStages()) {
			player.sendMessage("The Hespori is still growing.");
			return;
		}
		// OSRS: harvesting the grown Hespori with a spade wakes it; the patch clears when it dies (Hespori.java)
		if (!player.getInventory().contains(952, 1)) {
			player.sendMessage("You'll need a spade to harvest the Hespori.");
			return;
		}
		io.ruin.model.activities.bosses.Hespori.start(player, this);
	}

	@Override
	public boolean canPlant(Crop crop) {
		return crop instanceof HesporiCrop;
	}

	@Override
	public boolean isDiseaseImmune() {
		return true;
	}

	@Override
	public int calculateProduceAmount() {
		return 0;
	}

	@Override
	public boolean requiresCure() {
		return false;
	}

	@Override
	public String getPatchName() {
		return "a Hespori";
	}
}
