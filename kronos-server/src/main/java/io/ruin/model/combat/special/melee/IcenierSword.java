package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

// Frozen Strike: Deal 25% bonus damage with an icy blast. (50%)
public class IcenierSword implements Special {

    @Override
    public boolean accept(ObjType def, String name) {
        return name.contains("icenier sword");
    }

    @Override
    public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
        player.animate(1067);
        target.graphics(366, 96, 0);
        player.publicSound(2525);
        target.hit(new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostDamage(0.25));
        return true;
    }

    @Override
    public int getDrainAmount() {
        return 50;
    }

}
