import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

/**
 * Stage 10 (2026-10-29): the PvM Ticket (42765), a tradeable token of 1 PvM point like the PK (59599), Donator (59600),
 * Bounty Hunter (59601) and Vote (59602) tickets: the Donator Ticket's definition (ticket model 63889, stackable) with
 * its own colours (9227/32661 -> green 23356/23326, used by no other ticket). Sold in the PvM shop for 1 point;
 * Claim/Check in DonatorClaimScroll. Usage: PatchPvmTicket apply &lt;cache241Dir&gt;. Idempotent.
 */
public final class PatchPvmTicket {

	private PatchPvmTicket() {
	}

	static final int TICKET = 42765, TEMPLATE = 59600, INV_MODEL = 63889;
	static final int[] RECOLOURS = { 9227, 23356, 32661, 23326 };

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		byte[] template = items.get(TEMPLATE);
		if (template == null)
			throw new IllegalStateException("template " + TEMPLATE + " missing");
		byte[] old = items.get(TICKET);
		byte[] def = PatchCustoms27.item(template, INV_MODEL, -1, "PvM Ticket", null, RECOLOURS);
		if (old != null && !Arrays.equals(old, def) && !new String(old, java.nio.charset.StandardCharsets.ISO_8859_1).contains("PvM Ticket"))
			throw new IllegalStateException("item " + TICKET + " is already something else in this cache");
		int changed = PatchCustoms27.putConfigs(dst, 10, Map.of(TICKET, def));
		dst.close();
		System.out.println("RESULT pvm ticket: " + changed + " change(s)");
	}
}
