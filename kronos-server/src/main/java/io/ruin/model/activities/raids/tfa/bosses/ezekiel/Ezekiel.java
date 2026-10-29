package io.ruin.model.activities.raids.tfa.bosses.ezekiel;

import io.ruin.model.activities.raids.tfa.rooms.EzekielRoom;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import lombok.Getter;

/**
 * Ezekiel's npc (16652). Every move he makes is scripted by {@link EzekielRoom} from the RSProx captures
 * (docs/rsprox-analysis/guthixian_archive/ezekiel_plan.md): this class only takes damage (earth weakness from the
 * cache's stats), reports it, and tells the room when his health crosses the finale line or reaches zero.
 */
public class Ezekiel extends NPCCombat {

	/** Cache/wiki: 25% elemental weakness to earth spells. */
	static final double EARTH_WEAKNESS = 0.25;

	private EzekielRoom room;
	@Getter
	private int maxHp;
	private boolean dying;

	public void bind(EzekielRoom room, int maxHp) {
		this.room = room;
		this.maxHp = maxHp;
		Stat hp = getStat(StatType.Hitpoints);
		hp.fixedLevel = maxHp;
		hp.currentLevel = maxHp;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener()
				.preDefend(this::preDefend)
				.postDamage(this::postDamage);
		npc.deathStartListener = (entity, killer, killHit) -> {
			if (room != null && !dying) {
				dying = true;
				room.onDying();
			}
		};
		npc.deathEndListener = (entity, killer, killHit) -> {
			if (room != null)
				room.onNpcDeath(npc);
		};
	}

	private void preDefend(Hit hit) {
		if (hit.attackSpell != null && hit.attackSpell.getClass().getSimpleName().startsWith("Earth"))
			hit.boostDamage(EARTH_WEAKNESS);
	}

	private void postDamage(Hit hit) {
		if (room == null)
			return;
		if (hit.attacker != null && hit.attacker.player != null)
			room.recordDamage(hit.attacker.player, hit.damage);
		room.onBossDamaged();
	}

	public void heal(int amount) {
		Stat hp = getStat(StatType.Hitpoints);
		hp.currentLevel = Math.min(maxHp, hp.currentLevel + amount);
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
	}

	@Override
	public boolean attack() {
		return false;
	}
}
