package io.ruin.model.entity.npc.combatpet;

import io.ruin.Server;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.player.Player;

/**
 * Per-follower combat state for a pet registered in {@link CombatPets}. Lives on the {@link PetNPC}
 * and is ticked from {@link PetNPC#process()}.
 *
 * The pet never picks its own target: it only assists against the NPC its owner is actively attacking,
 * and drops aggro as soon as the owner stops fighting, swaps to something invalid, or leaves the leash range.
 * While engaged, the follow loop in {@code Pet.spawn} hands movement over to {@link CombatPetCombat}; once
 * disengaged the follow loop walks the pet back (or teleports it when it is more than 8 tiles away).
 */
public class CombatPet {

	public enum State { IDLE, ENGAGED }

	/** Owner further than this from the pet breaks aggro. */
	static final int LEASH_DISTANCE = 10;

	/** The owner must have attacked within this many ticks for the pet to keep assisting. */
	static final int OWNER_COMBAT_TIMEOUT_TICKS = 10;

	/** Pets stay passive in the Wilderness (keeps them out of contested PvM/PvP situations). */
	static final boolean ALLOW_IN_WILDERNESS = false;

	final PetNPC pet;
	final Player owner;
	final CombatPetProfile profile;

	State state = State.IDLE;
	NPC target;
	long nextAttackTick;
	/** Engaged ticks since the last special (tiered pets fire a special every N of these). */
	int ticksSinceLastSpecial;

	CombatPet(PetNPC pet, CombatPetProfile profile) {
		this.pet = pet;
		this.owner = pet.owner;
		this.profile = profile;
	}

	public boolean isEngaged() {
		return state == State.ENGAGED;
	}

	public NPC getTarget() {
		return target;
	}

	public void process() {
		if (!AscendedBestiary.check(pet)) {
			disengage();
			return;
		}
		NPC newTarget = findTarget();
		if (newTarget == null) {
			disengage();
			return;
		}
		if (newTarget != target) {
			target = newTarget;
			// small wind-up so a retarget can't fire instantly
			nextAttackTick = Math.max(nextAttackTick, Server.currentTick() + 1);
		}
		state = State.ENGAGED;
		ticksSinceLastSpecial++;
		CombatPetCombat.tick(this);
	}

	public void disengage() {
		if (state == State.IDLE)
			return;
		state = State.IDLE;
		target = null;
		pet.getMovement().reset();
		pet.faceNone(false);
	}

	/**
	 * The owner's current NPC target, or null when the pet should not be fighting.
	 */
	private NPC findTarget() {
		if (owner == null || !owner.isOnline() || owner.getCombat().isDead())
			return null;
		if (pet.isHidden() || pet.isRemoved() || owner.getMovement().isTeleportQueued())
			return null;
		if (!ALLOW_IN_WILDERNESS && owner.wildernessLevel > 0)
			return null;
		if (!pet.getPosition().isWithinDistance(owner.getPosition(), LEASH_DISTANCE))
			return null;
		if (!owner.getCombat().isAttacking(OWNER_COMBAT_TIMEOUT_TICKS))
			return null;

		Entity ownerTarget = owner.getCombat().getTarget();
		if (ownerTarget instanceof Player) // never PvP
			return null;
		if (!(ownerTarget instanceof NPC npc) || npc instanceof PetNPC)
			return null;
		if (npc.isRemoved() || npc.isHidden() || npc.getCombat() == null || npc.getCombat().isDead())
			return null;
		if (npc.getHeight() != pet.getHeight())
			return null;
		return npc;
	}

}
