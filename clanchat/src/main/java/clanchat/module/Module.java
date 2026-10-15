package clanchat.module;

import clanchat.Attributes;
import clanchat.FriendChats;
import clanchat.hook.ChatHook;
import clanchat.hook.FriendListHook;
import clanchat.hook.PlayerHook;
import clanchat.hook.ProtocolHook;
import clanchat.hook.TickHook;
import clanchat.inter.FriendChatSettingsInterface;
import clanchat.inter.TabFriendsChat;
import core.module.api.IModule;

public class Module implements IModule {
	@Override
	public void init() {
		ProtocolHook.register();
	}

	@Override
	public void start() {
		Attributes.register();
		FriendChats.ensureHelpChannelExists();
		FriendChatSettingsInterface.register();
		TabFriendsChat.register();
		PlayerHook.register();
		FriendListHook.register();
		ChatHook.register();
		TickHook.register();
		// Bounty Hunter never pairs members of the same clan chat (the public "help" channel doesn't count)
		io.ruin.model.activities.wilderness.BountyHunter.clanLink = (a, b) -> {
			String chat = Attributes.clan(a).joinedName;
			return chat != null && !chat.isEmpty() && !chat.equalsIgnoreCase("help")
				&& chat.equalsIgnoreCase(Attributes.clan(b).joinedName);
		};
	}
}
