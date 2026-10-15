package friendlist.module;

import core.module.api.IModule;
import friendlist.hook.PlayerHook;
import friendlist.hook.PrivacyHook;
import friendlist.hook.ProtocolHook;
import friendlist.hook.TickHook;
import friendlist.legacy.LegacyMigrationProcess;

public class Module implements IModule {

	@Override
	public void init() {
		ProtocolHook.register();
	}

	@Override
	public void start() {
		LegacyMigrationProcess.start();
		PlayerHook.register();
		PrivacyHook.register();
		TickHook.register();
		// Bounty Hunter never pairs friends: the cached list only (no blocking loads on the matching loop)
		io.ruin.model.activities.wilderness.BountyHunter.friendLink = (a, b) -> {
			var list = friendlist.FriendListDb.db().cached(a.uuid());
			if (list == null)
				list = friendlist.FriendListDb.db().cached(a.getName());
			return list != null && list.containsFriend(b.getName());
		};
	}

}
