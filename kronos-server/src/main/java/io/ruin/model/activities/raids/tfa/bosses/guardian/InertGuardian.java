package io.ruin.model.activities.raids.tfa.bosses.guardian;

import io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;

/**
 * An active inert guardian (TFA_GUARDIAN_BATTERY_ACTIVE). It never fights back; its hp is the charge meter, and
 * "killing" it charges it. Only its battery's style lands.
 */
public class InertGuardian extends NPCCombat {

	private ArchiveGuardianRoom room;
	private Battery battery;

	public void bind(ArchiveGuardianRoom room, Battery battery) {
		this.room = room;
		this.battery = battery;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener()
				.preDefend(this::restrictStyle)
				.postDamage(hit -> {
					if (room != null && hit.attacker != null && hit.attacker.player != null)
						room.recordDamage(hit.attacker.player, hit.damage);
				});
		npc.deathStartListener = (entity, killer, killHit) -> {
			if (room != null)
				room.charge(battery, killer == null ? null : killer.player);
		};
	}

	private void restrictStyle(Hit hit) {
		if (battery == null || hit.attacker == null || hit.attacker.player == null)
			return;
		if (!accepts(hit)) {
			hit.nullify();
			Player player = hit.attacker.player;
			player.sendMessage(switch (battery.charge) {
				case MELEE -> "This guardian can only be charged up close.";
				case RANGED_OR_EARTH -> "This guardian can only be charged with ranged attacks or earth spells.";
				case BEAM -> "This guardian is out of reach.";
			});
		}
	}

	private boolean accepts(Hit hit) {
		if (hit.attackStyle == null)
			return false;
		return switch (battery.charge) {
			case MELEE, RANGED_OR_EARTH -> true; // guardian_plan 7: attackable by any style
			case BEAM -> false; // the south-east battery: no ops the whole fight
		};
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		return false;
	}

	@Override
	public void process() {
	}

	@Override
	public boolean allowRetaliate(Entity attacker) {
		return false;
	}

	@Override
	public boolean isAggressive() {
		return false;
	}
}
