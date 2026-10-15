package tradepost.db;

import java.math.BigInteger;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import core.task.Continuation;
import core.task.Continuations;
import io.ruin.Server;
import io.ruin.model.entity.player.Player;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import tradepost.hook.Attributes;

@Slf4j
@ExtensionMethod(Attributes.class)
public class TradePostCoffersDb {

	public static record CofferEntry(int id, int itemId, int itemAmount) {
	}

	/** Login: loads the coin balance (the database is the source of truth; memory is only for display). */
	public static void loadCoffer(Player player) {
		Continuations.schedule(() -> {
			var amount = fetchCoins(player.uuid()).await();
			if (amount != null && player.isOnline()) {
				player.tradePost().cofferCoins = BigInteger.valueOf(amount);
			}
		});
	}

	public static Continuation<Long> fetchCoins(String owner) {
		return Server.gameDb.exec(con -> {
			try (var statement = con.prepareStatement("SELECT amount FROM tradepost_coffer WHERE username = ?")) {
				statement.setString(1, owner);
				try (var results = statement.executeQuery()) {
					return results.next() ? Math.max(0L, results.getLong("amount")) : 0L;
				}
			} catch (Exception e) {
				log.error("Unable to read coffer coins for: " + owner, e);
				return null;
			}
		});
	}

	/**
	 * Atomic withdrawal: only succeeds when the stored balance covers it (one conditional UPDATE), so two claims,
	 * a stale in-memory balance or a sale landing mid-claim can never pay out more than was really earned.
	 */
	public static Continuation<Boolean> withdrawCoins(String owner, long amount) {
		return Server.gameDb.exec(con -> {
			try (var statement = con.prepareStatement(
					"UPDATE tradepost_coffer SET amount = amount - ? WHERE username = ? AND amount >= ?")) {
				statement.setLong(1, amount);
				statement.setString(2, owner);
				statement.setLong(3, amount);
				return statement.executeUpdate() == 1;
			} catch (Exception e) {
				log.error("Unable to withdraw " + amount + " coffer coins for: " + owner, e);
				return false;
			}
		});
	}

	public static Continuation.Void addCoinsCont(String owner, BigInteger amount) {
		return Server.gameDb.exec(con -> {
			try (var statement = con.prepareStatement(
					"INSERT INTO tradepost_coffer (username, amount) VALUES (?, ?) " +
							"ON DUPLICATE KEY UPDATE amount = amount + ?")) {
				statement.setString(1, owner);
				statement.setLong(2, amount.longValueExact());
				statement.setLong(3, amount.longValueExact());
				statement.executeUpdate();
			} catch (Exception e) {
				log.error("Unable to add " + amount + " coffer coins for: " + owner, e);
			}
		});
	}

	/** Deletes one collection-box row; true only if this call really removed it (so it is paid out exactly once). */
	public static Continuation<Boolean> deleteEntry(String owner, CofferEntry entry) {
		return Server.gameDb.exec(con -> {
			try (var statement = con.prepareStatement(
					"DELETE FROM tradepost_collections WHERE player_name = ? AND id = ?")) {
				statement.setString(1, owner);
				statement.setInt(2, entry.id());
				return statement.executeUpdate() == 1;
			} catch (Exception e) {
				log.error("Unable to delete coffer entry " + entry + " for " + owner, e);
				return false;
			}
		});
	}

	public static void removeCoins(String player, long amount) {
		Server.gameDb.execute(con -> {
			try (var statement = con.prepareStatement(
					"INSERT INTO tradepost_coffer (username, amount) VALUES (?, ?) " +
							"ON DUPLICATE KEY UPDATE amount = amount - ?")) {
				statement.setString(1, player);
				statement.setLong(2, amount);
				statement.setLong(3, amount);
				statement.executeUpdate();
			} catch (Exception e) {
				log.error("Unable to remove from coffer player: " + player, e);
			}
		});
	}

	public static void addCoins(String owner, BigInteger amount) {
		Server.gameDb.execute(con -> {
			try (var statement = con.prepareStatement(
					"INSERT INTO tradepost_coffer (username, amount) VALUES (?, ?) " +
							"ON DUPLICATE KEY UPDATE amount = amount + ?")) {
				statement.setString(1, owner);
				statement.setLong(2, amount.longValue());
				statement.setLong(3, amount.longValue());
				statement.executeUpdate();
			} catch (Exception e) {
				log.error("Unable to add to coffer player: " + owner, e);
			}
		});
	}

	public static Continuation<CofferEntry> addItemCont(String owner, int item, int amount) {
		return Server.gameDb.exec(con -> {
			var SQL = "INSERT INTO tradepost_collections (player_name, item_id, item_amount) VALUES(?, ?, ?)";
			try (var statement = con.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, owner);
				statement.setInt(2, item);
				statement.setInt(3, amount);
				if (statement.executeUpdate() == 0) {
					log.error("Unable to add item " + owner + ":" + item + ":" + amount + " (no_rows_affected)");
					return null;
				}
				try (var result = statement.getGeneratedKeys()) {
					if (!result.next()) {
						return null;
					}
					var id = result.getInt(1);
					return new CofferEntry(id, item, amount);
				}
			} catch (Exception e) {
				log.error("Unable to add item " + owner + ":" + item + ":" + amount, e);
				return null;
			}
		});
	}

	public static Continuation<List<CofferEntry>> fetchCollectList(String owner) {
		return Server.gameDb.exec((con) -> {
			var collectList = new ArrayList<CofferEntry>();
			try (var statement = con.prepareStatement("SELECT * FROM tradepost_collections WHERE player_name = ?")) {
				statement.setString(1, owner);
				try (var result = statement.executeQuery()) {
					while (result.next()) {
						var id = result.getInt("id");
						var itemId = result.getInt("item_id");
						var itemAmount = result.getInt("item_amount");
						var entry = new CofferEntry(id, itemId, itemAmount);
						collectList.add(entry);
					}
					return collectList;
				} catch (Exception e) {
					log.error("Unable to claim items for: " + owner, e);
				}
			} catch (Exception e) {
				log.error("Unable to claim items for: " + owner, e);
			}
			return null;
		});
	}

	public static Continuation<Boolean> deleteEntriesCont(String owner, List<CofferEntry> entries) {
		return Server.gameDb.exec(con -> {
			try (var statement = con.prepareStatement(deleteEntriesSql(owner, entries))) {
				statement.setString(1, owner);
				var idx = 2;
				for (var entry : entries) {
					statement.setInt(idx, entry.id());
					idx += 1;
				}
				var rows = statement.executeUpdate();
				return rows == entries.size() ;
			} catch (Exception e) {
				log.error("Unable to delete coffer entries", e);
				return false;
			}
		});
	}

	private static String deleteEntriesSql(String owner, List<CofferEntry> entries) {
		var deleteStatement = "DELETE FROM tradepost_collections WHERE player_name = ? AND id IN";
		deleteStatement += "(";
		deleteStatement += entries.stream().map(it -> "?").collect(Collectors.joining(","));
		deleteStatement += ")";
		return deleteStatement;
	}
}
