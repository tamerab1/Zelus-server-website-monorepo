package io.ruin.model.entity.npc.combatpet;

import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.impl.GraardorJrProfile;
import io.ruin.model.entity.npc.combatpet.impl.PrinceBlackDragonProfile;
import io.ruin.model.entity.npc.combatpet.impl.TinyTemporProfile;
import io.ruin.model.entity.npc.combatpet.impl.TzRekJadProfile;
import io.ruin.model.entity.npc.combatpet.tiered.DoubtProfile;
import io.ruin.model.entity.npc.combatpet.tiered.JalXilProfile;
import io.ruin.model.entity.npc.combatpet.tiered.LukeProfile;
import io.ruin.model.entity.npc.combatpet.tiered.MaledictusProfile;
import io.ruin.model.entity.npc.combatpet.tiered.ReanimatedTzHaarProfile;
import io.ruin.model.entity.npc.combatpet.tiered.XamphurProfile;
import io.ruin.model.item.actions.impl.pet.Pet;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Which pets fight. To add one: write a {@link CombatPetProfile} and register it below for every
 * {@link Pet} entry (incl. recolours) that should use it. Tiered pets ({@link TieredCombatPetProfile})
 * register all three tiers at once with {@link #registerTiered}, which also links them for upgrading.
 */
public final class CombatPets {

	private static final Map<Pet, CombatPetProfile> PROFILES = new EnumMap<>(Pet.class);
	private static final Map<Pet, PetTier> TIERS = new EnumMap<>(Pet.class);
	private static final Map<Pet, Pet> NEXT_TIER = new EnumMap<>(Pet.class);

	static {
		register(new GraardorJrProfile(),
				Pet.GENERAL_GRAARDOR, Pet.SHADOW_GENERAL_GRAARDOR, Pet.GOLDEN_GENERAL_GRAARDOR, Pet.BLOOD_GENERAL_GRAARDOR);
		register(new TinyTemporProfile(),
				Pet.TINY_TEMPOR, Pet.SHADOW_TINY_TEMPOR, Pet.GOLDEN_TINY_TEMPOR, Pet.BLOOD_TINY_TEMPOR);
		register(new TzRekJadProfile(),
				Pet.TZREK_JAD, Pet.SHADOW_TZREK_JAD, Pet.GOLDEN_TZREK_JAD, Pet.BLOOD_TZREK_JAD);
		register(new PrinceBlackDragonProfile(),
				Pet.PRINCE_BLACK_DRAGON, Pet.SHADOW_PRINCE_BLACK_DRAGON, Pet.GOLDEN_PRINCE_BLACK_DRAGON,
				Pet.BLOOD_PRINCE_BLACK_DRAGON);

		registerTiered(DoubtProfile::new, Pet.DOUBT_T1, Pet.DOUBT_T2, Pet.DOUBT_T3);
		registerTiered(LukeProfile::new, Pet.LUKE_T1, Pet.LUKE_T2, Pet.LUKE_T3);
		registerTiered(MaledictusProfile::new, Pet.MALEDICTUS_T1, Pet.MALEDICTUS_T2, Pet.MALEDICTUS_T3);
		registerTiered(ReanimatedTzHaarProfile::new,
				Pet.REANIMATED_TZHAAR_T1, Pet.REANIMATED_TZHAAR_T2, Pet.REANIMATED_TZHAAR_T3);
		registerTiered(JalXilProfile::new, Pet.JAL_XIL_T1, Pet.JAL_XIL_T2, Pet.JAL_XIL_T3);
		registerTiered(XamphurProfile::new, Pet.XAMPHUR_T1, Pet.XAMPHUR_T2, Pet.XAMPHUR_T3);
	}

	private CombatPets() {
	}

	public static void register(CombatPetProfile profile, Pet... pets) {
		for (Pet pet : pets)
			PROFILES.put(pet, profile);
	}

	/** Registers a T1/T2/T3 family: one profile per tier, and T1 -> T2 -> T3 as the upgrade path. */
	public static void registerTiered(Function<PetTier, CombatPetProfile> factory, Pet t1, Pet t2, Pet t3) {
		Pet[] family = { t1, t2, t3 };
		for (PetTier tier : PetTier.values()) {
			Pet pet = family[tier.ordinal()];
			register(factory.apply(tier), pet);
			TIERS.put(pet, tier);
		}
		NEXT_TIER.put(t1, t2);
		NEXT_TIER.put(t2, t3);
	}

	/** Tier of a tiered combat pet, or null. */
	public static PetTier tierOf(Pet pet) {
		return pet == null ? null : TIERS.get(pet);
	}

	/** The pet this one upgrades into, or null when it is not upgradeable (or already T3). */
	public static Pet nextTier(Pet pet) {
		return pet == null ? null : NEXT_TIER.get(pet);
	}

	public static Iterable<Pet> tieredPets() {
		return TIERS.keySet();
	}

	/** Every pet that fights (tiered pets and the older assist pets with their recolours). */
	public static Iterable<Pet> allPets() {
		return PROFILES.keySet();
	}

	public static CombatPetProfile profileOf(Pet pet) {
		return pet == null ? null : PROFILES.get(pet);
	}

	public static boolean isCombatPet(Pet pet) {
		return pet != null && PROFILES.containsKey(pet);
	}

	/** Combat state for a freshly spawned follower, or null when its pet type doesn't fight. */
	public static CombatPet create(PetNPC pet) {
		CombatPetProfile profile = pet.petType == null ? null : PROFILES.get(pet.petType);
		return profile == null ? null : new CombatPet(pet, profile);
	}

}
