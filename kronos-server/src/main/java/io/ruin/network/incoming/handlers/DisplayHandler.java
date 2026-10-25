package io.ruin.network.incoming.handlers;

import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerDisplayMode;
import io.ruin.Server;
import io.ruin.api.buffer.InBuffer;
import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.Color;
import io.ruin.cache.EnumType;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.*;
import io.ruin.model.inter.questtab.JournalTab;

import io.ruin.model.item.containers.bank.BankPin;
import io.ruin.model.skills.slayer.SlayerUnlock;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.network.PacketSender;
import io.ruin.network.incoming.Incoming;
import io.ruin.utility.CS2Script;
import io.ruin.utility.IdHolder;
import net.rsprot.protocol.game.incoming.misc.client.WindowStatus;
import net.rsprot.protocol.message.codec.incoming.MessageConsumer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.ruin.model.inter.InterfaceEventMask.*;
import static io.ruin.network.ClientProt204.WINDOW_STATUS;

@SuppressWarnings("Duplicates")
@IdHolder(ids = { WINDOW_STATUS })
public class DisplayHandler implements Incoming, MessageConsumer<Player, WindowStatus> {

	@Override
	public void consume(Player player, WindowStatus msg) {
		handle(player, msg.getWindowMode(), msg.getFrameWidth(), msg.getFrameHeight());
	}

	@Override
	public void handle(Player player, InBuffer in, int opcode) {
		int displayMode = in.readByte();
		int canvasWidth = in.readShort();
		int canvasHeight = in.readShort();
		handle(player, displayMode, canvasWidth, canvasHeight);
	}

	private void handle(Player player, int displayMode, int canvasWidth, int canvasHeight) {
		player.canvasWidth = canvasWidth;
		player.canvasHeight = canvasHeight;
		if (player.started && !player.fitsLargeInterface()) {
			io.ruin.model.content.equipmentpresets.GearPresetInterface.shrinkIfOpen(player);
			io.ruin.model.content.gearloadouts.GearLoadoutInterface.shrinkIfOpen(player);
		}
		// rev 241 switches the game client layout client-side (the Settings dropdown never reaches the server)
		// and reports it here: 1 = fixed, 2 = resizable. Follow it, or the client resizes its canvas around the
		// fixed 548 toplevel (black borders, wrongly scaled viewport). Classic vs modern resizable isn't in the
		// packet: keep the player's current resizable flavour, else their side-panels setting.
		var wanted = clientLayout(player, displayMode);
		if (!player.started) {
			if (wanted != null)
				player.displayMode = wanted.raw();
			PlayerDisplayMode.refresh(player);
			player.start();
		} else if (wanted != null && wanted != PlayerDisplayMode.mode(player)) {
			PlayerDisplayMode.set(player, wanted);
		}
	}

	private static PlayerDisplayMode.Mode clientLayout(Player player, int displayMode) {
		var current = PlayerDisplayMode.mode(player);
		if (displayMode == 1)
			return PlayerDisplayMode.Mode.Fixed;
		if (displayMode == 2) {
			if (current != PlayerDisplayMode.Mode.Fixed)
				return current;
			return io.ruin.model.var.VarPlayerRepository.SIDE_PANELS.get(player) == 1
				? PlayerDisplayMode.Mode.ResizableModern : PlayerDisplayMode.Mode.Resizable;
		}
		return null;
	}

	public static void sendTopLevel(Player player, ToplevelInterfaceType newMode) {
		ToplevelInterfaceType oldMode = player.getToplevelType();
		PacketSender ps = player.getPacketSender();
		ps.sendToplevel(newMode);
		moveSubInterfaces(oldMode, newMode, player);
	}

	public static void updateResizedTabs(Player player) {
		ToplevelInterfaceType oldMode = player.getToplevelType();
		PacketSender ps = player.getPacketSender();
		if (player.getToplevelType() == ToplevelInterfaceType.RESIZABLE_CLASSIC) {
			ps.sendToplevel(ToplevelInterfaceType.RESIZABLE_MODERN);
		} else {
			ps.sendToplevel(ToplevelInterfaceType.RESIZABLE_CLASSIC);
		}

		ToplevelInterfaceType newMode = player.getToplevelType();
		moveSubInterfaces(oldMode, newMode, player);
	}

	private static void moveSubInterfaces(ToplevelInterfaceType fromMode, ToplevelInterfaceType toMode, Player player) {
		PacketSender ps = player.getPacketSender();

		final HashMap<Integer, Integer> oldEnumMap = EnumType.get(fromMode.getTopLevelEnum()).getIntValues();
		final HashMap<Integer, Integer> newEnumMap = EnumType.get(toMode.getTopLevelEnum()).getIntValues();

		for (Map.Entry<Integer, Integer> newComponent : newEnumMap.entrySet()) {
			int key = newComponent.getKey();
			int to = newComponent.getValue();

			if (to == -1) {
				continue;
			}

			int from = oldEnumMap.getOrDefault(key, -1);

			if (from == -1) {
				continue;
			}

			ps.moveInterface(from >> 16, from & 0xffff, to >> 16, to & 0xffff);
		}
	}
}
