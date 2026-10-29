package io.ruin.model.activities.raids.tfa.bosses;

import io.ruin.model.activities.raids.tfa.rooms.Raid4BossRoom;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.shared.listeners.HitListener;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * A raid NPC whose every move is scripted by its room: no auto attacks, no chasing unless the room sets a
 * target and {@link #setChase(boolean)}. Damage dealt to it counts towards points, its death goes to
 * {@link Raid4BossRoom#onNpcDeath}, and the room can hook {@link #setPreDamage} for armour, immunities and the like.
 */
public class Raid4Puppet extends NPCCombat {

	@Getter
	private Raid4BossRoom room;
	@Setter
	private Consumer<Hit> preDamage;
	@Setter
	private Consumer<Hit> preDefend;
	@Setter
	private Consumer<Hit> postDamage;
	@Setter
	private boolean chase;
	@Setter
	private int chaseDistance = 1;

	public void bind(Raid4BossRoom room) {
		this.room = room;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener()
				.preDefend(hit -> {
					if (preDefend != null)
						preDefend.accept(hit);
				})
				.preDamage(hit -> {
					if (preDamage != null)
						preDamage.accept(hit);
				})
				.postDamage(hit -> {
					if (room != null && hit.attacker != null && hit.attacker.player != null)
						room.recordDamage(hit.attacker.player, hit.damage);
					if (postDamage != null)
						postDamage.accept(hit);
				});
		npc.deathStartListener = (entity, killer, killHit) -> {
			if (room != null)
				room.onNpcDying(npc);
		};
		npc.deathEndListener = (entity, killer, killHit) -> {
			if (room != null)
				room.onNpcDeath(npc);
		};
	}

	@Override
	public boolean isAggressive() {
		return false;
	}

	@Override
	public boolean allowRetaliate(Entity attacker) {
		return false;
	}

	@Override
	public void process() {
	}

	@Override
	public void follow() {
		if (chase && target != null)
			follow(chaseDistance);
	}

	@Override
	public boolean attack() {
		return false;
	}

	public void chase(Entity entity) {
		setTarget(entity);
		chase = entity != null;
	}
}
