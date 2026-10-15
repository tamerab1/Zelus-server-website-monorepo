package tradepost;

import java.math.BigInteger;
import java.util.List;

import core.task.Continuation;
import core.task.Continuations;
import io.ruin.api.utils.MathUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import tradepost.db.TradePostCoffersDb;
import tradepost.hook.Attributes;
import tradepost.inter.ZelusMarket;

/**
 * The collection box: coins earned from sales and items bought while offline (or that did not fit). Both are stored
 * in the database, so a seller who is offline when their listing sells is paid safely and collects on their next
 * visit (My Shop -> Collect coins / Collect items). Every payout claims its row in the database first and only pays
 * if that claim succeeded.
 */
@Slf4j
@ExtensionMethod(Attributes.class)
public class TradePostCoffer {

	public static void load(Player player) {
		resetEntries(player);
	}

	private static void resetEntries(Player player) {
		Continuations.schedule(resetEntriesCont(player));
	}

	private static Continuation.Void resetEntriesCont(Player player) {
		return () -> {
			var entries = TradePostCoffersDb.fetchCollectList(player.uuid()).await();
			if (!player.isOnline()) {
				return;
			}
			if (entries == null) {
				log.error("Unable load coffer items fro player: " + player.uuid());
				return;
			}
			setEntries(player, entries);
		};
	}

	private static void setEntries(Player player, List<TradePostCoffersDb.CofferEntry> entries) {
		var pTradePost = player.tradePost();
		pTradePost.coffer.clear();
		pTradePost.coffer.addAll(entries);
	}

	public static void collectAll(Player player) {
		Continuations.schedule(() -> {
			if (!player.isOnline()) {
				return;
			}

			var pTradePost = player.tradePost();
			if (pTradePost.coffer.isEmpty()) {
				player.sendMessage("Your collection box has no items.");
				return;
			}

			var entries = List.copyOf(pTradePost.coffer);
			pTradePost.coffer.removeAll(entries);

			// One row at a time: each item is paid only if its own row was really deleted.
			for (var entry : entries) {
				if (!TradePostCoffersDb.deleteEntry(player.uuid(), entry).await()) {
					continue;
				}
				if (!player.isOnline()) {
					// logged out mid-claim: put it back in the box instead of losing it
					add(player.uuid(), entry.itemId(), BigInteger.valueOf(entry.itemAmount()));
					continue;
				}
				collect(player, entry);
			}
			TradePost.refreshViews(player);
		});
	}

	public static Continuation.Void collect(TradePostCoffersDb.CofferEntry entry, Player player) {
		return () -> {
			if (!player.isOnline()) {
				return;
			}

			var pTradePost = player.tradePost();
			if (!pTradePost.coffer.remove(entry)) {
				player.sendMessage("That item has already been collected.");
				return;
			}

			if (!TradePostCoffersDb.deleteEntry(player.uuid(), entry).await()) {
				player.sendMessage("Unable to collect that item, please try again.");
				resetEntries(player);
				return;
			}

			if (!player.isOnline()) {
				add(player.uuid(), entry.itemId(), BigInteger.valueOf(entry.itemAmount()));
				return;
			}
			collect(player, entry);
		};
	}

	/** Inventory, then bank; whatever still doesn't fit goes back into the box (never onto the floor). */
	private static void collect(Player player, TradePostCoffersDb.CofferEntry entry) {
		var unnotedId = ObjType.unnotedId(entry.itemId());
		var notedId = ObjType.notedId(unnotedId);
		var itemDef = ObjType.get(unnotedId);

		var pInv = player.getInventory();
		var pBank = player.getBank();
		var remainingAmount = entry.itemAmount();

		var depositInvCnt = pInv.add(notedId, remainingAmount);
		if (depositInvCnt > 0) {
			player.sendMessage(ZelusMarket.TAG + "Collected " + depositInvCnt + " x " + itemDef.name + " into your inventory.");
			remainingAmount -= depositInvCnt;
		}

		if (remainingAmount > 0) {
			var depositBankCnt = pBank.deposit(unnotedId, remainingAmount);
			if (depositBankCnt > 0) {
				player.sendMessage(ZelusMarket.TAG + "Collected " + depositBankCnt + " x " + itemDef.name + " into your bank.");
				remainingAmount -= depositBankCnt;
			}
		}

		if (remainingAmount > 0) {
			add(player.uuid(), unnotedId, BigInteger.valueOf(remainingAmount));
			player.sendMessage(ZelusMarket.TAG + remainingAmount + " x " + itemDef.name
					+ " did not fit and stays in your collection box.");
		}
	}

	public static void add(String uid, int item, BigInteger amount) {
		Continuations.schedule(() -> {
			var newEntry = TradePostCoffersDb.addItemCont(uid, item, amount.intValueExact()).await();
			if (newEntry == null) {
				log.error("[Zelus Market] collection box insert FAILED: " + uid + " " + item + " x " + amount);
				return;
			}

			var player = World.getPlayer(uid);
			if (player != null) {
				var pTradePost = player.tradePost();
				pTradePost.coffer.add(newEntry);
				TradePost.refreshViews(player);
			}
		});
	}

	/** Credits sale proceeds: shown at once if the owner is online, then re-read once the database has them. */
	public static void addCoins(String uid, BigInteger amount) {
		if (MathUtils.lt(amount, BigInteger.ONE)) {
			return;
		}
		var player = World.getPlayer(uid);
		if (player != null) {
			var pTradePost = player.tradePost();
			pTradePost.cofferCoins = pTradePost.cofferCoins.add(amount);
		}
		Continuations.schedule(() -> {
			TradePostCoffersDb.addCoinsCont(uid, amount).await();
			reloadCoins(World.getPlayer(uid));
		});
	}

	private static void reloadCoins(Player player) {
		if (player == null || !player.isOnline()) {
			return;
		}
		Continuations.schedule(() -> {
			var stored = TradePostCoffersDb.fetchCoins(player.uuid()).await();
			if (stored != null && player.isOnline() && !player.tradePost().coinClaimInFlight) {
				player.tradePost().cofferCoins = BigInteger.valueOf(stored);
				TradePost.refreshViews(player);
			}
		});
	}

	public static void collectCoins(Player player) {
		var pTradePost = player.tradePost();
		collectCoins(player, pTradePost.cofferCoins);
	}

	public static void collectCoins(Player player, BigInteger amount) {
		if (MathUtils.lt(amount, BigInteger.ONE)) {
			player.sendMessage("Your collection box has no coins.");
			return;
		}

		var pTradePost = player.tradePost();
		if (pTradePost.coinClaimInFlight) {
			return;
		}
		var amt = MathUtils.min(pTradePost.cofferCoins, amount);

		if (MathUtils.lt(amt, BigInteger.ONE)) {
			player.sendMessage("Your collection box has no coins.");
			return;
		}

		var claim = amt.longValueExact();
		pTradePost.coinClaimInFlight = true;
		Continuations.schedule(() -> {
			boolean ok;
			try {
				ok = TradePostCoffersDb.withdrawCoins(player.uuid(), claim).await();
			} catch (RuntimeException e) {
				log.error("Coin withdrawal failed for " + player.uuid(), e);
				ok = false;
			} finally {
				pTradePost.coinClaimInFlight = false;
			}
			if (!ok) {
				player.sendMessage("Your collection box could not pay that out; please try again.");
				reloadCoins(player);
				return;
			}
			if (!player.isOnline()) {
				// claimed but logged out before payment: put it straight back
				TradePostCoffersDb.addCoinsCont(player.uuid(), BigInteger.valueOf(claim)).await();
				return;
			}
			player.getInventory().addCoinsOrPlatTokens(BigInteger.valueOf(claim));
			player.sendMessage(ZelusMarket.TAG + "Collected " + ZelusMarket.coins(BigInteger.valueOf(claim)) + " coins.");
			reloadCoins(player);
		});
	}

	public static BigInteger amount(Player player) {
		return player.tradePost().cofferCoins;
	}
}
