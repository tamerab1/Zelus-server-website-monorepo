package io.ruin.model.skills.magic.spells.modern;

import io.ruin.model.item.EquipmentCheck;
import io.ruin.model.map.Projectile;
import io.ruin.model.skills.magic.rune.Rune;
import io.ruin.model.skills.magic.spells.TargetSpell;

public class MagicDart extends TargetSpell {

	public static final MagicDart INSTANCE = new MagicDart();

	public MagicDart() {
		setLvlReq(50);
		setBaseXp(30.0);
		setMaxDamage(10);
		setAnimationId(1576);
		setCastSound(1718, 1, 0);
		setHitGfx(329, 124);
		setHitSound(174);
		setProjectile(new Projectile(328, 43, 31, 51, 56, 10, 16, 64));
		setRunes(Rune.DEATH.toItem(1), Rune.MIND.toItem(4));
		setAutoCast(18);
		// OSRS: Magic Dart needs a Slayer's staff (or the staves built on it); the Staff of light set the Saradomin staff
		// borrows for autocast lists it too, so the staff is checked here
		setCastCheck(new EquipmentCheck("You need a Slayer's staff, Staff of the dead or Staff of light to cast Magic Dart.",
				4170, 21255, 11791, 12904, 22296, 24144));
	}

}