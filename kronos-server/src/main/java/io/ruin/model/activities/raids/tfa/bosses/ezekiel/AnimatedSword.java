package io.ruin.model.activities.raids.tfa.bosses.ezekiel;

import io.ruin.model.activities.raids.tfa.rooms.EzekielRoom;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.shared.listeners.HitListener;

/**
 * An Animated Sword (16653). It never attacks and can't die: its 80 hp bar is the yellow charge bar, so damage
 * dealt to it is charge, and a full bar is 80. Charge raises what it deals when it flies, to Ezekiel and to
 * players alike (wiki). Swords resist ranged and take double damage from air spells.
 */
public class AnimatedSword extends NPCCombat {

	public static final int MAX_CHARGE = 80;
	/** "Resistant to ranged": not quantified on the wiki. */
	static final double RANGED_MULTIPLIER = 0.5;

	private EzekielRoom room;

	public void bind(EzekielRoom room) {
		this.room = room;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener()
				.preDefend(this::preDefend)
				.preDamage(this::preDamage)
				.postDamage(this::postDamage);
	}

	private void preDefend(Hit hit) {
		if (hit.attackSpell != null && hit.attackSpell.getClass().getSimpleName().startsWith("Wind"))
			hit.boostDamage(1.0);
	}

	private void preDamage(Hit hit) {
		if (hit.attackStyle != null && hit.attackStyle.isRanged())
			hit.damage = (int) (hit.damage * RANGED_MULTIPLIER);
		// Full charge keeps the sword at 1 hp: it is a bar, not a life.
		int hp = npc.getHp();
		if (hit.damage >= hp)
			hit.damage = Math.max(0, hp - 1);
	}

	private void postDamage(Hit hit) {
		if (room != null && hit.attacker != null && hit.attacker.player != null && hit.damage > 0)
			room.recordDamage(hit.attacker.player, hit.damage);
	}

	/** 0-80: how much of the yellow bar is filled. */
	public int charge() {
		return Math.min(MAX_CHARGE, getStat(io.ruin.model.stat.StatType.Hitpoints).fixedLevel - npc.getHp() + (npc.getHp() <= 1 ? 1 : 0));
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
	public boolean isAggressive() {
		return false;
	}

	@Override
	public boolean allowRetaliate(Entity attacker) {
		return false;
	}
}
