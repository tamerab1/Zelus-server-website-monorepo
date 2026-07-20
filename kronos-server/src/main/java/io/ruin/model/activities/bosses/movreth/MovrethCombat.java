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
import io.ruin.model.stat.StatType;

public class MovrethCombat extends NPCCombat {

    private static final Projectile FIRE_PROJECTILE   = new Projectile(393, 43, 31, 51, 56, 10, 15, 250);
    private static final Projectile FREEZE_PROJECTILE = new Projectile(396, 43, 31, 51, 56, 10, 15, 250);

    private enum AttackType  { MELEE, RANGE, MAGIC }
    private enum SpecialType { INFERNO_BREATH, DRAGON_SLAM, SOUL_REND }
    private enum Overhead    { MELEE, RANGE, MAGIC }

    private AttackType  currentAttack  = AttackType.MELEE;
    private SpecialType nextSpecial    = SpecialType.INFERNO_BREATH;
    private Overhead    currentOverhead;

    private int damageSincePrayerSwap = 0;
    private int attackCount = 0;

    @Override
    public void init() {
        currentAttack   = AttackType.values()[Random.get(2)];
        currentOverhead = Overhead.values()[Random.get(2)];
        setOverheadIcon();

        npc.hitListener = new HitListener()
            .preDamage(this::preDamage)
            .postDamage(this::postDamage);
    }

    private void preDamage(Hit hit) {
        if (hit.attackStyle == null) return;
        if (hit.attackStyle.isMelee()  && currentOverhead == Overhead.MELEE)  hit.block();
        else if (hit.attackStyle.isRanged() && currentOverhead == Overhead.RANGE) hit.block();
        else if (hit.attackStyle.isMagic()  && currentOverhead == Overhead.MAGIC) hit.block();
    }

    private void postDamage(Hit hit) {
        // show boss HP bar to the attacker
        if (hit.attacker != null && hit.attacker.isPlayer()) {
            if (!hit.attacker.player.getHealthHud().isOpened())
                hit.attacker.player.getHealthHud().open(true, npc.getId(), npc.getMaxHp());
            hit.attacker.player.getHealthHud().updateValue(npc.getHp());
        }

        // switch overhead once 450 cumulative damage is taken — copy the style that hit
        damageSincePrayerSwap += hit.damage;
        if (damageSincePrayerSwap >= 450 && hit.attackStyle != null) {
            if (hit.attackStyle.isMelee())       currentOverhead = Overhead.MELEE;
            else if (hit.attackStyle.isRanged()) currentOverhead = Overhead.RANGE;
            else if (hit.attackStyle.isMagic())  currentOverhead = Overhead.MAGIC;
            setOverheadIcon();
            damageSincePrayerSwap = 0;
        }
    }

    private void setOverheadIcon() {
        NPC.DefaultHeadIconIndex icon;
        switch (currentOverhead) {
            case MELEE: icon = NPC.DefaultHeadIconIndex.ProtectFromMelee;   break;
            case RANGE: icon = NPC.DefaultHeadIconIndex.ProtectFromRanged;  break;
            default:    icon = NPC.DefaultHeadIconIndex.ProtectFromMagic;   break;
        }
        npc.setHeadIcon(icon);
    }

    @Override
    public void follow() {
        follow(currentAttack == AttackType.MELEE ? 2 : 8);
    }

    @Override
    public boolean attack() {
        if (!withinDistance(8)) return false;

        attackCount++;

        // special attack every 8 attacks
        if (attackCount >= 8) {
            attackCount = 0;
            doSpecialAttack();
            // rotate special for next trigger
            nextSpecial = SpecialType.values()[(nextSpecial.ordinal() + 1) % 3];
            // pick a new random normal attack style after a special
            currentAttack = AttackType.values()[Random.get(2)];
            return true;
        }

        // if selected melee but too far, swap to a ranged style
        if (currentAttack == AttackType.MELEE && !withinDistance(2))
            currentAttack = Random.rollDie(2, 1) ? AttackType.RANGE : AttackType.MAGIC;

        switch (currentAttack) {
            case MELEE: meleeAttack();  break;
            case RANGE: rangeAttack();  break;
            case MAGIC: magicAttack();  break;
        }

        // randomly vary the attack style each hit to keep players on their toes
        if (Random.rollDie(3, 1))
            currentAttack = AttackType.values()[Random.get(2)];

        return true;
    }

    // ─── normal attacks ─────────────────────────────────────────────────────

    private void meleeAttack() {
        npc.animate(80);
        int dmg = 45;
        if (target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
            dmg = 10;
        target.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
    }

    private void rangeAttack() {
        if (target == null) return;
        npc.animate(81);
        final var t = target;
        World.startEvent(e -> {
            int delay = FIRE_PROJECTILE.send(npc, t);
            e.delay(World.getTicks(delay) + 1);
            if (t.player == null) return;
            int dmg = 45;
            if (t.player.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES))
                dmg = 10;
            t.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
        });
    }

    private void magicAttack() {
        if (target == null) return;
        npc.animate(81);
        final var t = target;
        World.startEvent(e -> {
            int delay = FREEZE_PROJECTILE.send(npc, t);
            e.delay(World.getTicks(delay) + 1);
            if (t.player == null) return;
            int dmg = 45;
            if (t.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
                dmg = 10;
            t.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
            t.graphics(369); // magic impact gfx
        });
    }

    // ─── special attacks ────────────────────────────────────────────────────

    private void doSpecialAttack() {
        switch (nextSpecial) {

            case INFERNO_BREATH: {
                // fires at every player in the room — no safe spot
                npc.animate(81);
                World.startEvent(e -> {
                    e.delay(1);
                    for (Player p : npc.localPlayers()) {
                        int delay = FIRE_PROJECTILE.send(npc, p);
                        int dmg = 55;
                        if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES)) dmg = 15;
                        p.hit(new Hit(npc).randDamage(dmg).ignorePrayer().clientDelay(delay));
                        p.sendMessage("<col=ff4500>Movreth unleashes his inferno breath!</col>");
                    }
                });
                break;
            }

            case DRAGON_SLAM: {
                // huge melee hit with a ground-quake graphic
                npc.animate(80);
                World.sendGraphics(1537, 0, 0, npc.getPosition());
                int dmg = 58;
                if (target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
                    dmg = 15;
                target.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
                if (target.player != null)
                    target.player.sendMessage("<col=ff4500>Movreth slams the ground with tremendous force!</col>");
                break;
            }

            case SOUL_REND: {
                // magic bolt that drains 20 prayer points on top of damage
                if (target == null) break;
                npc.animate(81);
                final var t = target;
                World.startEvent(e -> {
                    int delay = FREEZE_PROJECTILE.send(npc, t);
                    e.delay(World.getTicks(delay) + 1);
                    if (t.player == null) return;
                    int dmg = 42;
                    if (t.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC)) dmg = 10;
                    t.hit(new Hit(npc).randDamage(dmg).ignorePrayer());
                    t.graphics(369);
                    t.player.getStats().get(StatType.Prayer).drain(20);
                    t.player.sendMessage("<col=7f00ff>Movreth rends your soul — your prayers weaken!</col>");
                });
                break;
            }
        }
    }

    @Override
    public void process() {
        // logic is driven by attack() and the hit listeners; nothing needed per-tick here
    }
}
