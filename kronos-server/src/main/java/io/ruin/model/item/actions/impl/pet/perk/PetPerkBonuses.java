package io.ruin.model.item.actions.impl.pet.perk;

/// Shared defaults for the pet perk system. Individual pets carry their own tuned values (see
/// Pet.java's enum declarations + PetPerk factory methods) -- this only holds values meant to
/// stay constant across an entire category unless a pet explicitly overrides them.
public final class PetPerkBonuses {

	private PetPerkBonuses() {
	}

	/// Default defence bonus for a Mage-perk pet when not explicitly overridden.
	public static final double MAGE_DEFENCE_BOOST = 0.10;
}
