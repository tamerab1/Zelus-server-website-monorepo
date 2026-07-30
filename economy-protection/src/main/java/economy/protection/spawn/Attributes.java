package economy.protection.spawn;

import io.ruin.model.entity.player.Player;

import static player.attributes.api.API.attrib;

public class Attributes {

	public static void register() {
		attrib().register().persistent(SemiSpawnData.class, SemiSpawnData::new);
	}

	public static SemiSpawnData semiSpawnData(Player player) {
		return attrib(SemiSpawnData.class, player);
	}
}
