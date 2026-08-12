package tradepost;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.ruin.model.entity.player.Player;
import lombok.Data;
import tradepost.db.TradePostCoffersDb;
import tradepost.inter.TradePostInterface;

@Data
public class TradePostPlayer {
	// Temporary slots for a player's offers, they are provded by database
	// and managed in sync with it.
	public final TradePostOffer[] offers = new TradePostOffer[10];
	public final List<TradePostHistory> history = new ArrayList<>();
	public final List<TradePostCoffersDb.CofferEntry> coffer = new ArrayList<>();
	public final TradePostInterface inter = new TradePostInterface();
	public BigInteger cofferCoins = BigInteger.ZERO;

	// maxSlots caps the search to the player's rank-unlocked slots -- see TradePost.maxOfferSlots().
	// Always pass it explicitly (no no-arg overload) so a future call site can't silently bypass
	// the rank gate by forgetting to cap it.
	public int offersEmptySlot(int maxSlots) {
		for (var i = 0; i < Math.min(offers.length, maxSlots); i++) {
			if (offers[i] == null) {
				return i;
			}
		}
		return -1;
	}

	public boolean hasEmptySlot(int maxSlots) {
		return offersEmptySlot(maxSlots) != -1;
	}
}
