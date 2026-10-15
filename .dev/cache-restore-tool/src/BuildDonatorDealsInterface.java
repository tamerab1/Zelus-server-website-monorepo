import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1166, Zelus Donator Deals (content/donatordeals/DonatorDeals.java, server):
//  - [ Shop ] | [ Deals ] bezel tabs sitting flush on the cards (y38-60) + the donator point balance (y40, clear of
//    the frame border and the close button);
//  - 4 horizontal deal cards (polish rounds 2-3, 2026-10-15), top to bottom with fixed spacing: bold title,
//    bevelled green discount badge, one recessed item tray (6 item comps the server centres with if_setposition, so a
//    1-2 item deal shows no empty grid), muted crossed-out price, green price, divider, hourglass + "Time remaining:"
//    and the time as 2 fixed single lines (round 2 used a wrapping block that spilled into the button), then the
//    Purchase button 9px below them and 8px above the card edge (+ a hidden dark shade that greys it out when claimed);
//  - footer: a sunken stone progress bar (green fill, gold fill at max rank, 1px highlight each; width via
//    clientscript 10606) with the status text centred inside it.
// Card c starts at FIRST_CARD + c * CARD_STRIDE; the offsets inside a card are printed as CARD_* consts.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildDonatorDealsInterface {
    static final int GROUP = 1166, CARDS = 4, CARD_W = 112, CARD_GAP = 5, CARD_Y = 60, CARD_H = 218;  // round 3: tabs sit flush on the cards (y38-60)
    static final int BAR_X = 24, BAR_Y = 286, BAR_W = 464, BAR_H = 18;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            k.mark("SHOP_TAB");
            k.bezelTab(0, 24, 38, 96, 22, "Shop", true, "Shop");
            k.mark("DEALS_TAB");
            k.bezelTab(0, 124, 38, 96, 22, "Deals", true, "Deals");
            k.mark("BALANCE");
            k.add(IfKit.right(k.text(0, 220, 40, 260, 18, IfKit.TEXT_FONT, IfKit.GOLD, "")));

            for (int c = 0; c < CARDS; c++) {
                int base = k.next();
                if (c == 0)
                    k.mark("FIRST_CARD");
                else if (c == 1)
                    System.out.println("CONST CARD_STRIDE = " + (base - k.ids.get("FIRST_CARD")));
                int card = k.panel(0, 24 + c * (CARD_W + CARD_GAP), CARD_Y, CARD_W, CARD_H);
                rel(k, c, base, "CARD_TITLE");
                var title = k.block(card, 4, 6, 104, 24, IfKit.GOLD, 12, true);
                title.yTextAlignment = 1;
                title.fontId = IfKit.HEADING_FONT;
                k.add(title);
                // bevelled badge: border, fill, top highlight, bottom shadow, text
                k.add(k.solid(card, 6, 32, 100, 16, 0x0f2e11));
                k.add(k.solid(card, 7, 33, 98, 14, 0x1e6b24));
                k.add(k.solid(card, 7, 33, 98, 1, 0x4fa852));
                k.add(k.solid(card, 7, 46, 98, 1, 0x123f16));
                rel(k, c, base, "CARD_BADGE_TEXT");
                k.add(IfKit.centred(k.text(card, 6, 32, 100, 16, IfKit.TEXT_FONT, IfKit.WHITE, "")));
                k.inset(card, 3, 52, 106, 66);
                rel(k, c, base, "CARD_FIRST_ITEM");
                for (int s = 0; s < 6; s++)
                    k.add(k.itemSlot(card, 4 + (s % 3) * 35, 52 + (s / 3) * 33, 36, 32, "Examine"));
                rel(k, c, base, "CARD_OLD_PRICE");
                k.add(IfKit.centred(k.text(card, 4, 121, 104, 12, IfKit.TEXT_FONT, 0x9a7a72, "")));
                rel(k, c, base, "CARD_PRICE");
                k.add(IfKit.centred(k.text(card, 4, 133, 104, 16, IfKit.BIG_FONT, 0x4fd14a, "")));
                k.add(k.solid(card, 8, 152, 96, 1, IfKit.BORDER));
                rel(k, c, base, "CARD_HOURGLASS");
                k.add(k.itemSlot(card, 5, 154, 18, 16, null));
                rel(k, c, base, "CARD_TIMER");
                k.add(IfKit.centred(k.text(card, 24, 155, 84, 14, IfKit.TEXT_FONT, 0xc0c0c0, "Time remaining:")));
                rel(k, c, base, "CARD_TIME");
                k.add(IfKit.centred(k.text(card, 4, 168, 104, 14, IfKit.TEXT_FONT, IfKit.WHITE, "")));
                rel(k, c, base, "CARD_BUY");
                k.bezel(card, 10, 190, 92, 20, "Purchase");
                rel(k, c, base, "CARD_BUY_SHADE");
                var shade = k.solid(card, 11, 191, 90, 18, 0x000000);
                shade.opacity = 110;
                shade.isHidden = true;
                k.add(shade);
                // the label sits above the shade so "Claimed" stays readable
                rel(k, c, base, "CARD_BUY_LABEL");
                k.add(IfKit.centred(k.text(card, 10, 190, 92, 20, IfKit.TEXT_FONT, 0xff981f, "")));
            }
            k.mark("NO_DEALS");
            k.add(k.block(0, 24, 140, 464, 40, IfKit.GREY, 14, true));

            // sunken progress bar: stone frame, dark well, inner shadow, fills, text inside
            k.add(k.solid(0, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, 0x0d0b09));
            k.add(k.solid(0, BAR_X, BAR_Y, BAR_W, BAR_H, 0x1a1714));
            k.add(k.solid(0, BAR_X, BAR_Y, BAR_W, 1, 0x0a0908));
            k.add(k.outline(0, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, IfKit.TRIM, false));
            k.mark("BAR_GREEN");
            k.add(k.solid(0, BAR_X + 1, BAR_Y + 1, 1, BAR_H - 2, 0x1b5e20));
            k.add(k.solid(0, BAR_X + 1, BAR_Y + 1, 1, 1, 0x4caf50));
            k.mark("BAR_GOLD");
            k.add(IfKit.hidden(k.solid(0, BAR_X + 1, BAR_Y + 1, 1, BAR_H - 2, 0x7a5a16)));
            k.add(IfKit.hidden(k.solid(0, BAR_X + 1, BAR_Y + 1, 1, 1, 0xd8b04a)));
            k.mark("BAR_TEXT");
            // one px higher than the recess: p11 sits low when centred
            k.add(IfKit.centred(k.text(0, BAR_X, BAR_Y - 1, BAR_W, BAR_H, IfKit.TEXT_FONT, IfKit.WHITE, "")));

            k.finish(apply);
        }
    }

    /** Prints a card-relative offset once (from card 0). */
    static void rel(IfKit k, int card, int base, String name) {
        if (card == 0)
            System.out.println("CONST " + name + " = " + (k.next() - base));
    }
}
