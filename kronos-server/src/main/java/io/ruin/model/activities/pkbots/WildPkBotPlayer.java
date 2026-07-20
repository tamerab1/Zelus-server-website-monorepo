package io.ruin.model.activities.pkbots;

/**
 * A server-spawned wilderness PK bot configured via {@link WildPkBotSetup}.
 *
 * The null-guard in both overrides handles the super() constructor hazard:
 * setMaxStats() and equipDefaultGear() are called by ZelusBotPlayer's
 * constructor before this subclass's `setup` field is assigned, so those
 * early calls fall through to the super defaults. After the super() returns
 * we reassign with the correct setup-specific stats and gear.
 */
public class WildPkBotPlayer extends ZelusBotPlayer {

    private WildPkBotSetup setup;

    public WildPkBotPlayer(WildPkBotSetup setup) {
        super(setup.username, setup.spawnPosition);
        this.setup = setup;
        // Re-apply correct stats/gear now that `setup` is assigned
        setMaxStats();
        equipDefaultGear();
    }

    public WildPkBotSetup getSetup() {
        return setup;
    }

    @Override
    public void setMaxStats() {
        if (setup == null) { super.setMaxStats(); return; }
        setup.applyStats(this);
    }

    @Override
    public void equipDefaultGear() {
        if (setup == null) { super.equipDefaultGear(); return; }
        setup.applyGear(this);
    }
}
