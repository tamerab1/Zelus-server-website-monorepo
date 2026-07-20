package io.ruin.model.activities.bosses.movreth;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Projectile;
import io.ruin.model.skills.prayer.Prayer;

import java.util.ArrayList;
import java.util.List;

public class MovrethGuard extends NPCCombat {

    private static final Projectile FIRE_PROJECTILE = new Projectile(393, 43, 31, 51, 56, 10, 15, 250);

    // next[phase-1] = {x, y, z} where players go after killing that guard
    private static final int[][] NEXT_PHASE = {
        {3019, 4391, 0},  // phase 1 → phase 2
        {3020, 4391, 0},  // phase 2 → phase 3
        {3014, 4381, 0}   // phase 3 → Movreth
    };

    private int tickCounter = 0;
    private boolean showFirstIcon = true;

    private int getPhase() {
        switch (npc.getId()) {
            case 17037: return 1;
            case 17040: return 2;
            case 17041: return 3;
            default:    return 1;
        }
    }

    @Override
    public void init() {
        npc.hitListener = new HitListener().preDamage(this::preDamage);
        npc.deathEndListener = (entity, killer, killHit) -> {
            int phase = getPhase();
            int[] next = NEXT_PHASE[phase - 1];
            String msg = (phase < 3)
                ? "<col=ff4500>The Movreth Guard falls. The next chamber opens...</col>"
                : "<col=ff0000>The final guard falls. Movreth awaits!</col>";
            // capture player list before NPC is removed
            List<Player> nearby = new ArrayList<>(npc.localPlayers());
            for (Player p : nearby) {
                p.sendMessage(msg);
                p.getMovement().teleport(next[0], next[1], next[2]);
            }
        };
        updateOverhead();
    }

    private void preDamage(Hit hit) {
        if (hit.attackStyle == null) return;
        int phase = getPhase();
        if (phase == 1) {
            // weak to magic only — blocks melee and range
            if (hit.attackStyle.isMelee() || hit.attackStyle.isRanged()) hit.block();
        } else if (phase == 2) {
            // weak to melee only — blocks magic and range
            if (hit.attackStyle.isMagic() || hit.attackStyle.isRanged()) hit.block();
        } else {
            // weak to range only — blocks melee and magic
            if (hit.attackStyle.isMelee() || hit.attackStyle.isMagic()) hit.block();
        }
    }

    private void updateOverhead() {
        int phase = getPhase();
        NPC.DefaultHeadIconIndex icon;
        if (phase == 1) {
            icon = showFirstIcon
                ? NPC.DefaultHeadIconIndex.ProtectFromMelee
                : NPC.DefaultHeadIconIndex.ProtectFromRanged;
        } else if (phase == 2) {
            icon = showFirstIcon
                ? NPC.DefaultHeadIconIndex.ProtectFromMagic
                : NPC.DefaultHeadIconIndex.ProtectFromRanged;
        } else {
            icon = showFirstIcon
                ? NPC.DefaultHeadIconIndex.ProtectFromMelee
                : NPC.DefaultHeadIconIndex.ProtectFromMagic;
        }
        npc.setHeadIcon(icon);
    }

    @Override
    public void follow() {
        int phase = getPhase();
        // phase 3 only does ranged/magic — can stay at distance
        follow(phase == 3 ? 6 : 2);
    }

    @Override
    public boolean attack() {
        if (!withinDistance(8)) return false;

        int phase = getPhase();
        if (phase == 1) {
            // melee + range
            if (withinDistance(1) && Random.rollDie(2, 1))
                meleeAttack();
            else
                projectileAttack(false);
        } else if (phase == 2) {
            // melee + magic
            if (withinDistance(1) && Random.rollDie(2, 1))
                meleeAttack();
            else
                projectileAttack(true);
        } else {
            // range + magic (phase 3 never melees)
            if (Random.rollDie(2, 1))
                projectileAttack(false);
            else
                projectileAttack(true);
        }
        return true;
    }

    private void meleeAttack() {
        npc.animate(80);
        int dmg = 28;
        if (target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
            dmg = 7;
        target.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
    }

    private void projectileAttack(boolean magic) {
        if (target == null) return;
        npc.animate(81);
        final var cachedTarget = target;
        World.startEvent(e -> {
            int delay = FIRE_PROJECTILE.send(npc, cachedTarget);
            e.delay(World.getTicks(delay) + 1);
            if (cachedTarget.player == null) return;
            int dmg = 28;
            if (!magic && cachedTarget.player.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES))
                dmg = 7;
            else if (magic && cachedTarget.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
                dmg = 7;
            cachedTarget.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
        });
    }

    @Override
    public void process() {
        // alternate overhead icon every 8 ticks so players can see both blocked styles
        if (++tickCounter >= 8) {
            tickCounter = 0;
            showFirstIcon = !showFirstIcon;
            updateOverhead();
        }
    }
}
