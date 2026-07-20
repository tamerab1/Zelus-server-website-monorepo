package io.ruin.model.activities.bosses.eventboss;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.activities.VorathHandler;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.route.routes.ProjectileRoute;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.utility.Broadcast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vorath — custom boss for Zelus RSPS.
 *
 * Attack pattern:
 *   • Follows target aggressively (range 12).
 *   • Melee (CRUSH) when adjacent — max hit 43 (enraged: 43 always).
 *   • Magic volley (hits all in-range players) at distance — max hit 35 (prayer halves it).
 *   • Every 7th attack: Shadow Slam — AOE delayed explosion on all nearby players.
 *   • Below 40 % HP: enrage — force-text warning, magic becomes prayer-piercing.
 */
public class Vorath extends NPCCombat {

    // Magic projectile — borrows DonationBoss projectile (stable, works server-side)
    private static final Projectile MAGIC_PROJECTILE = new Projectile(1046, 90, 43, 35, 60, 6, 16, 192);

    private static final LootTable LOOT_TABLE = new LootTable().addTable(1,
        new LootItem(995,  50000, 250000, 100),           // Coins 50k–250k
        new LootItem(537,  10, 50, 80),                    // Dragon bones x10–50
        new LootItem(385,  20, 70),                        // Shark x20
        new LootItem(1149, 1, 40),                         // Dragon med helm
        new LootItem(2364, 5, 30),                         // Rune bar x5
        new LootItem(13576, 1, 12).broadcast(Broadcast.GLOBAL),  // Draconic visage (rare)
        new LootItem(59562, 1, 4).broadcast(Broadcast.GLOBAL),  // Revenant Shardbow
		new LootItem(59539, 1, 3).broadcast(Broadcast.GLOBAL),  // Frostbound Fulllhelm
		new LootItem(59540, 1, 2).broadcast(Broadcast.GLOBAL),  // Frostbound Platebody
		new LootItem(59541, 1, 2).broadcast(Broadcast.GLOBAL)  // Frostbound Platelegs
    );

    private int attackCount = 0;
    private boolean enraged = false;
    private final Set<Integer> damagingPlayers = new HashSet<>();

    @Override
    public void init() {
        npc.setIgnoreMulti(true);

        // Track every player who lands a hit so they qualify for loot
        npc.hitListener = new HitListener().preDefend(hit -> {
            if (hit.attacker != null && hit.attacker.isPlayer())
                damagingPlayers.add(hit.attacker.player.getUserId());
        });

        npc.deathEndListener = (entity, killer, killHit) -> {
            rewardPlayers();
            VorathHandler.onDeath();
            npc.remove();
        };
    }

    @Override
    public void follow() {
        follow(12);
    }

    @Override
    public boolean attack() {
        if (!withinDistance(16))
            return false;

        attackCount++;

        // Enrage trigger at 40 % HP
        if (!enraged && npc.getHp() < npc.getMaxHp() * 0.4) {
            enraged = true;
            npc.forceText("YOU CANNOT STOP ME!");
            for (Player p : World.players())
                p.sendMessage("<col=660000>[Vorath]</col> has entered an enraged state!");
        }

        // Every 7th attack: Shadow Slam (replaces normal attack)
        if (attackCount % 7 == 0) {
            shadowSlam();
            return true;
        }

        // Melee when adjacent; magic at range (magic every 4th attack even if adjacent)
        boolean adjacent = npc.localPlayers().stream()
            .anyMatch(p -> p.getPosition().isWithinDistance(npc.getPosition(), 2));

        if (adjacent && attackCount % 4 != 0)
            meleeAttack();
        else
            magicAttack();

        return true;
    }

    @Override
    public void process() {
        // Reserved for future mechanics
    }

    // ── Attacks ──────────────────────────────────────────────────────────────

    private void meleeAttack() {
        int maxHit = 43;
        // GFX: dark swirl burst on the boss when it swings
        World.sendGraphics(1727, 92, 0, npc.getPosition());
        npc.localPlayers().forEach(p -> {
            if (!p.getPosition().isWithinDistance(npc.getPosition(), 2)) return;
            // GFX: slash impact on the player
            p.graphics(1430, 0, 0);
            p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(maxHit));
        });
    }

    private void magicAttack() {
        List<Player> targets = npc.localPlayers().stream()
            .filter(p -> ProjectileRoute.allow(npc, p))
            .collect(Collectors.toList());
        if (targets.isEmpty()) return;

        // GFX: cast flash on the boss
        World.sendGraphics(1680, 92, 0, npc.getPosition());

        targets.forEach(p -> {
            int duration = MAGIC_PROJECTILE.send(npc, p);
            int maxHit = enraged ? 35 : 26;
            if (!enraged && p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
                maxHit /= 2;
            p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(maxHit).clientDelay(duration));
            // GFX: impact spark on the player, timed to match projectile arrival
            p.graphics(157, 0, duration / 2);
        });
    }

    /**
     * Shadow Slam — delayed AOE explosion under all nearby players.
     * Warning graphic appears first; damage + explosion graphic land 2 ticks later.
     */
    private void shadowSlam() {
        npc.forceText("SHADOW FALLS!");
        int slamMax = enraged ? 30 : 20;

        // GFX: large burst on the boss itself to signal the special
        World.sendGraphics(1209, 92, 0, npc.getPosition());

        npc.localPlayers().forEach(p -> {
            if (!p.getPosition().isWithinDistance(npc.getPosition(), 7)) return;

            // GFX: warning pool under the player's feet
            World.sendGraphics(1026, 0, 0, p.getPosition().copy());

            npc.startEvent(e -> {
                e.delay(2); // 1.2 s warning before damage lands
                if (!p.getCombat().isDead()) {
                    // GFX: explosion on impact
                    p.graphics(1209, 0, 0);
                    p.hit(new Hit(npc, AttackStyle.MAGIC)
                        .fixedDamage(Random.get(8, slamMax))
                        .ignoreDefence()
                        .ignorePrayer());
                }
            });
        });
    }

    // ── Rewards ──────────────────────────────────────────────────────────────

    private void rewardPlayers() {
        npc.getPosition().getRegion().players.forEach(p -> {
            if (!damagingPlayers.contains(p.getUserId())) return;
            var reward = LOOT_TABLE.rollItem();
            p.getInventory().addOrDrop(reward.getId(), reward.getAmount());
            p.sendMessage(Color.RED.wrap("You received " + reward.getDef().name + " from Vorath!"));
            if (reward.lootBroadcast != null)
                Broadcast.WORLD.sendNewsDropMessage(p, Icon.ADMINISTRATOR,
                    "<col=000000>" + p.getName(),
                    " just received " + reward.getDef().descriptiveName + " from Vorath!");
        });
    }
}
