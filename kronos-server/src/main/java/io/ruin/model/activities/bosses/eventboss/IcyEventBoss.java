package io.ruin.model.activities.bosses.eventboss;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.activities.IcyEventBossHandler;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.utility.TickDelay;

import java.util.ArrayList;
import java.util.List;

/**
 * Icy Event Boss — reuses Malakar's walk/attack animations and attack pattern
 * (magic/ranged volley + portal special) as a starting point.
 */
public class IcyEventBoss extends NPCCombat {

    private static final Projectile RANGED_PROJECTILE = new Projectile(1329, 20, 31, 35, 35, 10, 0, 32);
    private static final Projectile MAGIC_PROJECTILE = new Projectile(1729, 120, 31, 25, 56, 10, 15, 220);

    @Override
    public void init() {
        npc.deathEndListener = (entity, killer, killHit) -> {
            npc.remove();
            IcyEventBossHandler.onDeath();
        };
    }

    @Override
    public void follow() {

    }

    @Override
    public void process() {

    }

    TickDelay portalDelay = new TickDelay();

    @Override
    public boolean attack() {
        if (Random.get(15) == 0 && portalDelay.remaining() < 1) {
            portalDelay.delay(50);
            handlePortalSpecial();
        } else {
            if (Random.get(1) == 0)
                magicAttack();
            else
                rangedAttack();
        }
        return true;
    }

    Position yellowPortalPosition = null;
    Position greenPortalPosition = null;

    private void handlePortalSpecial() {
        assignPortalPositions();
        List<Player> greenPlayers = new ArrayList<>();
        List<Player> yellowPlayers = new ArrayList<>();
        for (Player player : getPlayers()) {
            if (Random.get(1) == 0) {
                yellowPlayers.add(player);
                player.sendMessage(Color.YELLOW2.wrap("<shad=000000>You have been marked YELLOW for death."));
            } else {
                greenPlayers.add(player);
                player.sendMessage(Color.GREEN.wrap("<shad=000000>You have been marked GREEN for death."));
            }
        }
        World.startEvent(e -> {
            e.setCancelCondition(() -> npc.getHp() < 1);
            for (int i = 0; i < 6; i++) {
                World.sendGraphics(1360, 0, 0, greenPortalPosition);
                World.sendGraphics(1361, 0, 0, yellowPortalPosition);
                e.delay(3);
                if (i == 5) {
                    for (Player player : greenPlayers) {
                        if (player.getPosition().distance(greenPortalPosition) > 0) {
                            player.hit(new Hit().randDamage(45, 90));
                        }
                    }
                    for (Player player : yellowPlayers) {
                        if (player.getPosition().distance(yellowPortalPosition) > 0) {
                            player.hit(new Hit().randDamage(45, 90));
                        }
                    }
                }
            }
        });
    }

    private void assignPortalPositions() {
        List<Position> possiblePositions = new ArrayList<>();
        for (int x = -9; x < 9; x++) {
            for (int y = -9; y < 9; y++) {
                Position pos = new Position(npc.getPosition().getX() + x, npc.getPosition().getY() + y, npc.getPosition().getZ());
                if (pos.distance(npc.getPosition()) > 2)
                    possiblePositions.add(pos);
            }
        }
        Position yellowPos = Random.get(possiblePositions);
        possiblePositions.remove(yellowPos);
        Position greenPos = Random.get(possiblePositions);
        yellowPortalPosition = yellowPos;
        greenPortalPosition = greenPos;
    }

    private List<Player> getPlayers() {
        List<Player> players = new ArrayList<>();
        npc.getPosition().getRegion().players.stream().filter(player -> player.getHp() > 0).forEach(players::add);
        return players;
    }

    private void magicAttack() {
        npc.animate(10402);
        getPlayers().forEach(p -> {
            World.startEvent(e -> {
                e.setCancelCondition(() -> npc.getHp() < 1);
                int delay = MAGIC_PROJECTILE.send(npc, p);
                int maxDamage = 78;
                e.delay(World.getTicks(delay) + 1);
                if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC))
                    maxDamage = 6;

                p.hit(new Hit(npc, AttackStyle.RANGED).randDamage(maxDamage).ignorePrayer().ignoreDefence());
            });
        });
    }

    private void rangedAttack() {
        npc.animate(10403);
        getPlayers().forEach(p -> {
            World.startEvent(e -> {
                e.setCancelCondition(() -> npc.getHp() < 1);
                int delay = RANGED_PROJECTILE.send(npc, p);
                int maxDamage = 78;
                e.delay(World.getTicks(delay) + 1);
                if (p.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES))
                    maxDamage = 6;

                p.hit(new Hit(npc, AttackStyle.RANGED).randDamage(maxDamage).ignorePrayer().ignoreDefence());
            });
        });
    }
}
