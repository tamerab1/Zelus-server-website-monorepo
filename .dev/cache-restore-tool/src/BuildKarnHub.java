import java.io.File;
import java.nio.file.Files;

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

// The Karn hub windows (user 2026-11-15, from the user's mock-ups; art: tools/rev241-migration/assets/karn_hub/build_hub.py):
//   1185 Welcome screen (every login): events, daily tasks, Discord / vote, the Noticeboard (News / Updates / Feed), Enter World;
//   1186 Battle Pass: season + tier ring, Premium (2,200 donator points), free / premium reward tracks 5 tiers a page,
//        daily / weekly challenges;
//   1187 Best in Slot: the equipment slot grid, Stab / Slash / Crush / Magic / Range, a ranked list, the picked item's
//        model + stats against what is worn, Compare.
// The painted background (one sprite) carries the frames and panels; every word is a text component (p12 / b12, the
// user asked for readable text), buttons / tabs / rows are sprites with hover swaps. Ink text on the parchment has no
// shadow. rev 231 components; cache stage 42 (PatchKarnHub) converts them to rev 241 and writes the groups.
// Prints "CONST <group> NAME = id" for the server classes. Usage: verify <cache231> | export <cache231> <assets/karn_hub>
public class BuildKarnHub {
    static final int EV = -2147483645; // "this component" in a hook
    static final int P11 = 494, P12 = 495, B12 = 496, Q8 = 497, QUILL_LARGE = 646, VERDANA_13B = 1446;
    // round 2 (user 2026-11-15: no white backgrounds): dark panels, so the "ink" colours are light now
    static final int GOLD = 0xf5d27a, CREAM = 0xf3e6cc, INK = 0xf3e6cc, INK_SOFT = 0xc8b48e, CRIMSON = 0xf5c55a, SEG = 0xd8434b,
            TRACK = 0x1e1210, GREEN_DK = 0x7fe08a;
    static final int CLOSE = 24861, CLOSE_H = 24862;
    // build_hub.py sprite ids
    static final int BTN = 25220, BTN_H = 25221, SBTN = 25222, SBTN_H = 25223, TAB_W = 25224, TAB_W_ON = 25225, TAB_W_H = 25226,
            ARROW_L = 25227, ARROW_L_H = 25228, ARROW_R = 25229, ARROW_R_H = 25230, GBTN = 25231, GBTN_H = 25232,
            BG_WELCOME = 25233, ENTER = 25234, ENTER_H = 25235, NEWS_ICON = 25236,
            BG_BP = 25239, SLOT_GLOW = 25240, TICK = 25241, LOCK = 25242, TIER_OFF = 25243, TIER_ON = 25244,
            TAB_BP = 25245, TAB_BP_ON = 25246, TAB_BP_H = 25247,
            BG_BIS = 25248, CELL_SEL = 25249, ROW = 25250, ROW_H = 25251, ROW_SEL = 25252, TAB_BIS = 25253, TAB_BIS_ON = 25254, TAB_BIS_H = 25255,
            BG_DAILY = 25256, CLAIM = 25257, CLAIM_H = 25258, COMPARE_BG = 25260;
    /** Best in Slot rows built (the list scrolls) */
    static final int BIS_ROWS = 40;

    public static void main(String[] args) throws Exception {
        boolean export = args[0].equals("export");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit[] kits = {welcome(store), battlePass2(store), bestInSlot(store), dailyTasks(store), fiveSealsHud(store), fiveSealsParty(store), fiveSealsHall(store), fiveSealsShop(store)}; // 1186: v2 (2026-11-16)
            for (IfKit k : kits) {
                for (var e : k.ids.entrySet())
                    System.out.println("CONST " + k.group + " " + e.getKey() + " = " + e.getValue());
                k.ids.clear();
                k.finish(false);
                if (export) {
                    File out = new File(args[2], "interface_" + k.group);
                    out.mkdirs();
                    for (File f : out.listFiles())
                        f.delete();
                    for (int c = 0; c < k.comps.size(); c++)
                        Files.write(new File(out, c + ".dat").toPath(), k.comps.get(c));
                    System.out.println("EXPORTED " + k.comps.size() + " rev 231 components to " + out);
                }
            }
            if (export)
                exportBattlePassOrb(store, new File(args[2], "orb_160"));
        }
    }

    /**
     * The minimap Battle Pass button (user 2026-11-15: "change the store button to the battlepass button"): the store
     * orb (231 160:42-46 = rev 241 160:43-47, beside the Daily tasks button) becomes it. Its container loses script 2396
     * (which set up the STORE option, hover and hiding), the click layer gets a fixed "Battle Pass" option with the orb
     * frame's hover swap (2138 / 3517), the icon becomes sprite 25261. Written with rev 241 ids (stage 42, identity
     * conversion); the server's MainFrame handles 160:46.
     */
    static void exportBattlePassOrb(Store store, File out) throws Exception {
        final int G = 160, FROM = 42, TO = 43;
        net.runelite.cache.fs.Index index = store.getIndex(net.runelite.cache.IndexType.INTERFACES);
        net.runelite.cache.fs.Archive ar = index.getArchive(G);
        net.runelite.cache.fs.ArchiveFiles files = ar.getFiles(store.getStorage().loadArchive(ar));
        net.runelite.cache.definitions.loaders.InterfaceLoader loader = new net.runelite.cache.definitions.loaders.InterfaceLoader();
        out.mkdirs();
        for (File f : out.listFiles())
            f.delete();
        for (int i = 0; i < 5; i++) {
            InterfaceDefinition d = loader.load(G << 16 | (TO + i), files.findFile(FROM + i).getContents());
            if (i == 0) {
                d.onLoadListener = null;
                d.hasListener = false;
            } else
                d.parentId = G << 16 | TO;
            if (i == 3) {
                d.actions = new String[]{"Battle Pass"};
                d.name = "<col=ff9040>Battle Pass</col>";
                d.onMouseOverListener = new Object[]{44, G << 16 | (TO + 1), 3517};
                d.onMouseLeaveListener = new Object[]{44, G << 16 | (TO + 1), 2138};
                d.onMouseRepeatListener = null;
                d.onOpListener = null;
                d.hasListener = true;
            }
            if (i == 4) {
                d.spriteId = 25261;
                d.originalWidth = 20;
                d.originalHeight = 20;
            }
            Files.write(new File(out, (TO + i) + ".dat").toPath(), BuildBondsInterface.encodeIf3(d));
        }
        System.out.println("EXPORTED the Battle Pass orb (160:" + TO + "-" + (TO + 4) + ", the old store orb) to " + out);
    }

    // ------------------------------------------------------------------ helpers

    static IfKit kit(int group, Store store) throws Exception {
        IfKit k = new IfKit(group, store, true);
        for (int c = 1; c <= 3; c++) { // the 1002 frame's background, close and title stay in the group, hidden
            InterfaceDefinition d = k.want.get(c);
            d.isHidden = true;
            k.comps.set(c, BuildBondsInterface.encodeIf3(d));
        }
        return k;
    }

    static InterfaceDefinition sprite(IfKit k, int parent, int x, int y, int w, int h, int sprite) {
        return k.sprite(parent, x, y, w, h, sprite, false);
    }

    /** a sprite button: op, hover swaps its own graphic */
    static InterfaceDefinition button(IfKit k, int parent, int x, int y, int w, int h, int sprite, int hover, String op) {
        InterfaceDefinition d = sprite(k, parent, x, y, w, h, sprite);
        d.clickMask = 2;
        d.actions = new String[]{op};
        if (hover >= 0) {
            d.onMouseOverListener = new Object[]{44, EV, hover};
            d.onMouseLeaveListener = new Object[]{44, EV, sprite};
            d.hasListener = true;
        }
        return d;
    }

    static InterfaceDefinition text(IfKit k, int parent, int x, int y, int w, int h, int font, int colour, String s, int align, boolean shadow) {
        InterfaceDefinition d = k.text(parent, x, y, w, h, font, colour, s);
        d.xTextAlignment = align;
        d.textShadowed = colour != 0x3a1a08; // round 2: every text sits on a dark panel (dark labels on gold buttons excepted)
        return d;
    }

    static InterfaceDefinition block(IfKit k, int parent, int x, int y, int w, int h, int font, int colour, int lineHeight, int align, boolean shadow) {
        InterfaceDefinition d = text(k, parent, x, y, w, h, font, colour, "", align, shadow);
        d.yTextAlignment = 0;
        d.lineHeight = lineHeight;
        return d;
    }

    /** a click area with no look of its own */
    static InterfaceDefinition hotspot(IfKit k, int parent, int x, int y, int w, int h, String op) {
        InterfaceDefinition d = k.solid(parent, x, y, w, h, 0);
        d.opacity = 255;
        d.clickMask = 2;
        d.actions = new String[]{op};
        return d;
    }

    static void close(IfKit k, int x, int y) {
        k.mark("CLOSE");
        k.add(button(k, 0, x, y, 18, 18, CLOSE, CLOSE_H, "Close"));
    }

    /** a crimson / gold sprite button with its label on top; marks NAME at the button, the label is +1 */
    static void labelled(IfKit k, String name, int x, int y, int w, int h, int sprite, int hover, String op, int font, int colour, boolean shadow, String label) {
        k.mark(name);
        k.add(button(k, 0, x, y, w, h, sprite, hover, op));
        k.add(text(k, 0, x, y, w, h, font, colour, label, 1, shadow));
    }

    // ------------------------------------------------------------------ 1185 Welcome

    static IfKit welcome(Store store) throws Exception {
        IfKit k = kit(1185, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, BG_WELCOME));
        close(k, 487, 34);
        k.mark("TITLE");
        k.add(text(k, 0, 128, 47, 256, 14, B12, GOLD, "Welcome to Karn Resurrection", 1, true));
        k.mark("SUBTITLE");
        k.add(text(k, 0, 128, 61, 256, 13, P12, CREAM, "", 1, true));
        k.mark("ONLINE");
        k.add(text(k, 0, 413, 54, 82, 14, P12, CREAM, "", 0, true));

        // current events
        k.add(text(k, 0, 58, 79, 128, 14, B12, GOLD, "Current Events", 0, true));
        labelled(k, "EVENTS_BUTTON", 188, 80, 40, 14, SBTN, SBTN_H, "View", P11, GOLD, true, "View");
        k.mark("EVENTS_TEXT");
        k.add(block(k, 0, 44, 96, 184, 37, P12, INK, 12, 0, false));

        // daily tasks: 3 rows (name, count, a 20-step bar)
        k.add(text(k, 0, 58, 139, 128, 14, B12, GOLD, "Daily Tasks", 0, true));
        labelled(k, "DAILY_BUTTON", 188, 140, 40, 14, SBTN, SBTN_H, "Open", P11, GOLD, true, "Open");
        k.mark("FIRST_TASK");
        for (int r = 0; r < 3; r++) {
            int y0 = 157 + 25 * r;
            if (r == 1)
                System.out.println("CONST 1185 TASK_STRIDE = " + (k.next() - k.ids.get("FIRST_TASK")));
            k.add(text(k, 0, 44, y0, 136, 13, P12, INK, "", 0, false));          // +0 name
            k.add(text(k, 0, 172, y0, 54, 13, P12, INK_SOFT, "", 2, false));    // +1 count
            for (int s = 0; s < 20; s++)                                         // +2.. bar
                k.add(IfKit.hidden(k.solid(0, 44 + 9 * s, y0 + 15, 9, 5, SEG)));
        }

        // discord & voting
        k.add(text(k, 0, 58, 236, 160, 14, B12, GOLD, "Discord & Voting", 0, true));
        labelled(k, "DISCORD", 44, 258, 90, 20, BTN, BTN_H, "Join Discord", P12, GOLD, true, "Join Discord");
        labelled(k, "VOTE", 138, 258, 90, 20, BTN, BTN_H, "Vote", P12, GOLD, true, "Vote Now");

        // the noticeboard
        k.add(text(k, 0, 260, 77, 200, 14, B12, GOLD, "The Noticeboard", 0, true));
        k.mark("FIRST_TAB");
        String[] tabs = {"News", "Updates", "Feed"};
        for (int t = 0; t < 3; t++) {
            int x = 246 + 86 * t;
            k.add(button(k, 0, x, 96, 80, 16, TAB_W, TAB_W_H, "View"));              // +0
            k.add(IfKit.hidden(sprite(k, 0, x, 96, 80, 16, TAB_W_ON)));             // +1 active
            k.add(text(k, 0, x, 96, 80, 16, P12, CREAM, tabs[t], 1, true));        // +2
        }
        k.mark("FIRST_POST");
        for (int r = 0; r < 3; r++) {
            int y0 = 116 + 42 * r;
            if (r == 1)
                System.out.println("CONST 1185 POST_STRIDE = " + (k.next() - k.ids.get("FIRST_POST")));
            for (int i = 0; i < 3; i++)                                             // +0..2 icon (news / update / feed)
                k.add(IfKit.hidden(sprite(k, 0, 250, y0 + 4, 32, 32, NEWS_ICON + i)));
            k.add(text(k, 0, 288, y0 + 2, 150, 13, P12, CRIMSON, "", 0, false));    // +3 title
            k.add(text(k, 0, 436, y0 + 2, 56, 13, P11, INK_SOFT, "", 2, false));    // +4 date
            k.add(block(k, 0, 288, y0 + 15, 204, 25, P12, INK, 12, 0, false));     // +5 body
            k.add(hotspot(k, 0, 246, y0, 250, 40, "Read"));                        // +6
        }
        k.mark("PREV");
        k.add(button(k, 0, 250, 248, 14, 14, ARROW_L, ARROW_L_H, "Previous page"));
        k.mark("PAGE");
        k.add(text(k, 0, 266, 248, 70, 14, P12, INK, "", 1, false));
        k.mark("NEXT");
        k.add(button(k, 0, 338, 248, 14, 14, ARROW_R, ARROW_R_H, "Next page"));
        labelled(k, "READ_MORE", 402, 245, 90, 20, BTN, BTN_H, "Open website", P12, GOLD, true, "Read More");

        k.mark("ENTER");
        k.add(button(k, 0, 158, 291, 196, 40, ENTER, ENTER_H, "Enter World"));
        return k;
    }

    // ------------------------------------------------------------------ 1186 Battle Pass

    static IfKit battlePass(Store store) throws Exception {
        IfKit k = kit(1186, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, BG_BP));
        close(k, 484, 38);
        k.mark("SEASON");
        k.add(text(k, 0, 14, 60, 137, 16, B12, GOLD, "", 1, true));
        k.mark("ENDS");
        k.add(text(k, 0, 14, 80, 137, 13, P12, INK, "", 1, false));
        k.add(text(k, 0, 28, 105, 110, 12, P11, GOLD, "TIER", 1, true));
        k.mark("TIER");
        k.add(text(k, 0, 28, 118, 110, 18, 1447, CREAM, "", 1, true)); // round 2: verdana 15 (the quill numeral overlapped the XP line)
        k.mark("TIER_XP");
        k.add(text(k, 0, 28, 140, 110, 12, P11, CREAM, "", 1, true));
        k.mark("PREMIUM_NAME");
        k.add(text(k, 0, 44, 184, 104, 13, B12, CRIMSON, "Premium", 0, false));
        k.mark("PREMIUM_STATUS");
        k.add(text(k, 0, 44, 196, 104, 13, P12, INK, "", 0, false));
        labelled(k, "UNLOCK", 16, 212, 134, 20, GBTN, GBTN_H, "Unlock Premium", P12, 0x3a1a08, false, "");
        labelled(k, "CLAIM_ALL", 16, 236, 134, 20, GBTN, GBTN_H, "Claim all", P12, 0x3a1a08, false, "");
        k.mark("TOTALS");
        k.add(block(k, 0, 14, 260, 137, 26, P12, INK, 12, 1, false));

        k.add(text(k, 0, 184, 60, 140, 14, B12, GOLD, "Rewards", 0, true));
        k.mark("PREV");
        k.add(button(k, 0, 392, 60, 14, 14, ARROW_L, ARROW_L_H, "Previous tiers"));
        k.mark("PAGE");
        k.add(text(k, 0, 406, 60, 66, 14, P12, CREAM, "", 1, true));
        k.mark("NEXT");
        k.add(button(k, 0, 472, 60, 14, 14, ARROW_R, ARROW_R_H, "Next tiers"));
        k.add(text(k, 0, 167, 80, 46, 34, B12, GOLD, "Free", 1, true));
        k.add(text(k, 0, 167, 134, 46, 34, P12, GOLD, "Premium", 1, true));
        k.mark("FIRST_TIER");
        for (int i = 0; i < 5; i++) {
            int x = 218 + 55 * i + 17;
            k.add(sprite(k, 0, x, 116, 16, 16, TIER_OFF));                     // +0
            k.add(IfKit.hidden(sprite(k, 0, x, 116, 16, 16, TIER_ON)));        // +1 reached
            k.add(text(k, 0, x, 116, 16, 16, P11, CREAM, "", 1, true));        // +2 number
        }
        for (String track : new String[]{"FREE", "PREMIUM"}) {
            int ry = track.equals("FREE") ? 80 : 134;
            k.mark("FIRST_" + track);
            for (int i = 0; i < 5; i++) {
                int x = 218 + 55 * i;
                k.add(IfKit.hidden(sprite(k, 0, x, ry, 50, 34, SLOT_GLOW)));   // +0 claimable
                k.add(k.layer(0, x + 7, ry + 1, 36, 32));                      // +1 item grid (clientscript 149: "Claim <item>")
                k.add(IfKit.hidden(sprite(k, 0, x + 35, ry + 1, 14, 14, TICK))); // +2 claimed
                k.add(IfKit.hidden(sprite(k, 0, x + 39, ry + 2, 10, 12, LOCK))); // +3 locked (premium)
            }
        }
        k.mark("FIRST_CHALLENGE_TAB");
        String[] tabs = {"Daily", "Weekly"};
        for (int t = 0; t < 2; t++) {
            int x = 168 + 88 * t;
            k.add(button(k, 0, x, 188, 84, 16, TAB_BP, TAB_BP_H, "View"));
            k.add(IfKit.hidden(sprite(k, 0, x, 188, 84, 16, TAB_BP_ON)));
            k.add(text(k, 0, x, 188, 84, 16, P12, CREAM, tabs[t], 1, true));
        }
        k.mark("RESET");
        k.add(text(k, 0, 346, 189, 148, 14, P12, INK, "", 2, false));
        k.mark("FIRST_CHALLENGE");
        for (int r = 0; r < 5; r++) {
            int y0 = 207 + 15 * r;
            if (r == 1)
                System.out.println("CONST 1186 CHALLENGE_STRIDE = " + (k.next() - k.ids.get("FIRST_CHALLENGE")));
            k.add(text(k, 0, 170, y0 + 1, 160, 13, P12, INK, "", 0, false));     // +0 name (round 2: wider, the bar narrower)
            k.add(IfKit.hidden(k.solid(0, 334, y0 + 5, 40, 5, TRACK)));          // +1 track
            for (int s = 0; s < 20; s++)                                          // +2..21 bar (20 steps)
                k.add(IfKit.hidden(k.solid(0, 334 + 2 * s, y0 + 5, 2, 5, SEG)));
            k.add(text(k, 0, 376, y0 + 1, 58, 13, P12, INK_SOFT, "", 1, false)); // +22 progress
            k.add(text(k, 0, 436, y0 + 1, 58, 13, P12, GREEN_DK, "", 2, false)); // +23 xp
        }
        return k;
    }

    // ------------------------------------------------------------------ 1186 Battle Pass v2 (user 2026-11-16 mock-up)

    // build_bp2.py sprite ids
    static final int BP2_BG = 25270, BP2_TAB = 25271, BP2_TAB_ON = 25272, BP2_TAB_H = 25273, BP2_RANK = 25274, BP2_RANK_H = 25275,
            BP2_UPGRADE = 25276, BP2_UPGRADE_H = 25277, BP2_BADGE = 25278, BP2_XP = 25279, BP2_CELL = 25280, BP2_CELL_ELITE = 25281,
            BP2_GLOW = 25282, BP2_TICK = 25283, BP2_LOCK = 25284, BP2_HDR_ON = 25285, BP2_HDR_OFF = 25286, BP2_HDR_MILE = 25287,
            BP2_ARROW_L = 25288, BP2_ARROW_L_H = 25289, BP2_ARROW_R = 25290, BP2_ARROW_R_H = 25291, BP2_CLOSE = 25292,
            BP2_CLOSE_H = 25293, BP2_MISSIONS = 25294, BP2_SUB = 25295, BP2_SUB_ON = 25296, BP2_SUB_H = 25297, BP2_CLAIM = 25298,
            BP2_CLAIM_H = 25299, BP2_ELITE_LOCK = 25300, BP2_ICON_GIFT = 25301, BP2_ICON_TARGET = 25302, BP2_ICON_TICKET = 25303,
            BP2_ICON_CHEVRON = 25304, BP2_ICON_CROWN = 25305;
    static final int DARK_LABEL = 0x3a1a08;

    static IfKit battlePass2(Store store) throws Exception {
        IfKit k = kit(1186, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, BP2_BG));
        k.mark("CLOSE");
        k.add(button(k, 0, 486, 6, 20, 20, BP2_CLOSE, BP2_CLOSE_H, "Close"));

        // tabs: Rewards / Missions (views), Redeem (claims every reached reward)
        k.mark("FIRST_TAB");
        String[] tabs = {"Rewards", "Missions", "Redeem"};
        String[] ops = {"View", "View", "Redeem rewards"};
        int[] icons = {BP2_ICON_GIFT, BP2_ICON_TARGET, BP2_ICON_TICKET};
        for (int t = 0; t < 3; t++) {
            int x = 248 + 78 * t;
            k.add(button(k, 0, x, 6, 76, 20, BP2_TAB, BP2_TAB_H, ops[t]));               // +0
            k.add(IfKit.hidden(sprite(k, 0, x, 6, 76, 20, BP2_TAB_ON)));               // +1 active
            k.add(sprite(k, 0, x + 6, 9, 14, 13, icons[t]));                           // +2 icon
            k.add(text(k, 0, x + 21, 6, 53, 20, P12, GOLD, tabs[t], 1, true));         // +3
        }

        // season
        k.mark("SEASON");
        k.add(text(k, 0, 210, 25, 80, 16, B12, GOLD, "", 1, true));
        k.mark("ENDS");
        k.add(text(k, 0, 220, 43, 110, 13, P12, CREAM, "", 0, true));
        k.mark("RANK");
        k.add(button(k, 0, 404, 30, 100, 20, BP2_RANK, BP2_RANK_H, "Purchase Rank"));
        k.add(text(k, 0, 420, 30, 84, 20, P11, DARK_LABEL, "", 1, false));          // RANK + 1 label
        k.add(sprite(k, 0, 408, 33, 14, 13, BP2_ICON_CHEVRON));

        // tier badge + XP bar (20 steps)
        k.add(sprite(k, 0, 178, 58, 46, 40, BP2_BADGE));
        k.mark("TIER");
        k.add(text(k, 0, 178, 75, 46, 14, B12, 0xffe08c, "", 1, true));
        k.mark("TIER_XP");
        k.add(text(k, 0, 232, 59, 140, 14, P12, CREAM, "", 0, true));
        k.mark("FIRST_XP_SEG");
        for (int sgm = 0; sgm < 20; sgm++)
            k.add(IfKit.hidden(sprite(k, 0, 230 + (int) Math.round(13.4 * sgm), 77, 13, 6, BP2_XP)));

        // the Elite card
        k.mark("CARD_TITLE");
        k.add(block(k, 0, 12, 178, 140, 34, B12, GOLD, 15, 1, true));
        k.mark("CARD_TEXT");
        k.add(block(k, 0, 14, 228, 136, 40, P11, CREAM, 12, 1, true));
        k.mark("UPGRADE");
        k.add(button(k, 0, 16, 272, 132, 24, BP2_UPGRADE, BP2_UPGRADE_H, "Upgrade Pass"));
        k.add(text(k, 0, 40, 272, 104, 24, B12, DARK_LABEL, "", 1, false));        // UPGRADE + 1 label
        k.add(sprite(k, 0, 24, 279, 14, 10, BP2_ICON_CROWN));

        // claim all (always shown)
        labelled(k, "CLAIM_ALL", 404, 290, 98, 22, BP2_CLAIM, BP2_CLAIM_H, "Claim all", P12, DARK_LABEL, false, "");

        // ---- rewards view
        k.mark("REWARDS");
        int rewards = k.add(k.layer(0, 0, 0, 512, 334));
        k.mark("FIRST_HEADER");
        for (int i = 0; i < 5; i++) {
            int x = 214 + 58 * i;
            k.add(sprite(k, rewards, x, 98, 56, 15, BP2_HDR_OFF));                       // +0 not reached
            k.add(IfKit.hidden(sprite(k, rewards, x, 98, 56, 15, BP2_HDR_ON)));         // +1 reached
            k.add(IfKit.hidden(sprite(k, rewards, x, 98, 56, 15, BP2_HDR_MILE)));       // +2 reached milestone (5th)
            k.add(text(k, rewards, x, 98, 56, 15, B12, CREAM, "", 1, true));           // +3 number
        }
        for (String track : new String[]{"FREE", "ELITE"}) {
            int ry = track.equals("FREE") ? 115 : 190;
            k.mark("FIRST_" + track);
            for (int i = 0; i < 5; i++) {
                int x = 214 + 58 * i;
                k.add(sprite(k, rewards, x + 6, ry + 16, 44, 42, track.equals("FREE") ? BP2_CELL : BP2_CELL_ELITE)); // +0
                k.add(IfKit.hidden(sprite(k, rewards, x + 6, ry + 16, 44, 42, BP2_GLOW)));                      // +1 claimable
                k.add(k.layer(rewards, x + 10, ry + 21, 36, 32));                                               // +2 item grid
                InterfaceDefinition dim = k.solid(rewards, x, ry, 56, 74, 0x000000);
                dim.opacity = 120;
                k.add(IfKit.hidden(dim));                                                                       // +3 not reached
                k.add(IfKit.hidden(sprite(k, rewards, x + 39, ry + 12, 12, 12, BP2_TICK)));                    // +4 claimed
                k.add(IfKit.hidden(sprite(k, rewards, x + 41, ry + 12, 10, 12, BP2_LOCK)));                    // +5 locked (Elite)
            }
        }
        k.mark("ELITE_LOCK");
        k.add(sprite(k, rewards, 194, 240, 12, 14, BP2_ELITE_LOCK));
        k.mark("PREV");
        k.add(button(k, rewards, 214, 292, 16, 16, BP2_ARROW_L, BP2_ARROW_L_H, "Previous tiers"));
        k.mark("PAGE");
        k.add(text(k, rewards, 232, 292, 98, 16, P12, CREAM, "", 1, true));
        k.mark("NEXT");
        k.add(button(k, rewards, 332, 292, 16, 16, BP2_ARROW_R, BP2_ARROW_R_H, "Next tiers"));
        k.mark("FOOTER");
        k.add(text(k, rewards, 176, 319, 228, 12, P11, INK_SOFT, "Complete missions, earn XP, claim rewards!", 1, true));

        // ---- missions view (hidden until the Missions tab)
        k.mark("MISSIONS");
        int missions = k.add(IfKit.hidden(k.layer(0, 0, 0, 512, 334)));
        k.add(sprite(k, missions, 164, 96, 342, 192, BP2_MISSIONS));
        k.mark("FIRST_SUBTAB");
        String[] sub = {"Daily", "Weekly"};
        for (int t = 0; t < 2; t++) {
            int x = 172 + 76 * t;
            k.add(button(k, missions, x, 106, 72, 16, BP2_SUB, BP2_SUB_H, "View"));    // +0
            k.add(IfKit.hidden(sprite(k, missions, x, 106, 72, 16, BP2_SUB_ON)));     // +1 active
            k.add(text(k, missions, x, 106, 72, 16, P12, GOLD, sub[t], 1, true));      // +2
        }
        k.mark("RESET");
        k.add(text(k, missions, 330, 106, 168, 16, P11, INK_SOFT, "", 2, true));
        k.mark("FIRST_CHALLENGE");
        for (int r = 0; r < 5; r++) {
            int y0 = 136 + 29 * r;
            if (r == 1)
                System.out.println("CONST 1186 CHALLENGE_STRIDE = " + (k.next() - k.ids.get("FIRST_CHALLENGE")));
            k.add(text(k, missions, 176, y0 + 2, 250, 13, P12, CREAM, "", 0, true));          // +0 name
            for (int sgm = 0; sgm < 20; sgm++)                                                  // +1..20 bar
                k.add(IfKit.hidden(k.solid(missions, 315 + (int) Math.round(5.5 * sgm), y0 + 17, 5, 4, 0xf0c050)));
            k.add(text(k, missions, 430, y0 + 2, 66, 13, P11, INK_SOFT, "", 2, true));         // +21 progress
            k.add(text(k, missions, 430, y0 + 14, 66, 12, P11, GREEN_DK, "", 2, true));         // +22 xp
        }
        return k;
    }

    // ------------------------------------------------------------------ 1189 The Five Seals HUD (overlay, user 2026-11-16)

    static IfKit fiveSealsHud(Store store) throws Exception {
        IfKit k = kit(1189, store);
        int x = 6, y = 6, w = 178, h = 80;
        InterfaceDefinition back = k.solid(0, x, y, w, h, 0x120a1a);
        back.opacity = 60;
        k.add(back);
        k.add(k.outline(0, x, y, w, h, 0xb08a4a, false));
        k.add(k.outline(0, x + 1, y + 1, w - 2, h - 2, 0x4a2a66, false));
        k.mark("TITLE");
        k.add(text(k, 0, x + 6, y + 4, w - 12, 14, B12, GOLD, "The Five Seals", 1, true));
        k.mark("SEAL");
        k.add(text(k, 0, x + 8, y + 20, w - 16, 13, P12, CREAM, "", 0, true));
        k.mark("OBJECTIVE");
        k.add(text(k, 0, x + 8, y + 34, w - 16, 13, P11, CREAM, "", 0, true));
        k.add(k.solid(0, x + 8, y + 49, 161, 7, 0x0a0610));                    // bar track
        k.mark("FIRST_SEG");
        for (int s = 0; s < 20; s++)
            k.add(IfKit.hidden(k.solid(0, x + 9 + 8 * s, y + 50, 7, 5, 0xf0c050)));
        k.mark("MULT");
        k.add(text(k, 0, x + 8, y + 60, 90, 13, P11, GOLD, "", 0, true));
        k.mark("TIMER");
        k.add(text(k, 0, x + 90, y + 60, w - 98, 13, P11, CREAM, "", 2, true));
        // an overlay over the whole game view: nothing in it may swallow clicks (the 1002 root did: players couldn't walk)
        for (int c = 0; c < k.want.size(); c++) {
            InterfaceDefinition d = k.want.get(c);
            if (d != null && d.noClickThrough) {
                d.noClickThrough = false;
                k.comps.set(c, BuildBondsInterface.encodeIf3(d));
            }
        }
        return k;
    }

    // ------------------------------------------------------------------ 1190 / 1191 The Five Seals lobby windows (2026-11-16)

    static final int FS_PARTY_BG = 25306, FS_HALL_BG = 25307;

    /** the Party Board: 5 member rows (leader first), run info, Invite / Leave / Start */
    static IfKit fiveSealsParty(Store store) throws Exception {
        IfKit k = kit(1190, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, FS_PARTY_BG));
        k.mark("CLOSE");
        k.add(button(k, 0, 486, 6, 20, 20, BP2_CLOSE, BP2_CLOSE_H, "Close"));
        k.mark("TITLE");
        k.add(text(k, 0, 124, 14, 250, 18, B12, GOLD, "The Five Seals - Party Board", 1, true));
        k.add(text(k, 0, 16, 57, 282, 18, B12, GOLD, "Party", 1, true));
        k.add(text(k, 0, 318, 57, 178, 18, B12, GOLD, "The Run", 1, true));
        k.mark("FIRST_MEMBER");
        for (int r = 0; r < 5; r++) {
            int y0 = 82 + 37 * r;
            if (r == 1)
                System.out.println("CONST 1190 MEMBER_STRIDE = " + (k.next() - k.ids.get("FIRST_MEMBER")));
            k.add(text(k, 0, 22, y0 + 2, 22, 30, B12, GOLD, String.valueOf(r + 1), 1, true));   // +0 slot
            k.add(text(k, 0, 48, y0 + 3, 170, 14, P12, CREAM, "", 0, true));                    // +1 name
            k.add(text(k, 0, 48, y0 + 17, 170, 13, P11, INK_SOFT, "", 0, true));                // +2 level / status
            k.add(IfKit.hidden(button(k, 0, 222, y0 + 8, 72, 16, BP2_SUB, BP2_SUB_H, "Kick"))); // +3 kick
            k.add(IfKit.hidden(text(k, 0, 222, y0 + 8, 72, 16, P11, GOLD, "Kick", 1, true)));   // +4
        }
        k.mark("INFO");
        k.add(block(k, 0, 320, 82, 174, 180, P11, CREAM, 13, 0, true));
        k.mark("INVITE");
        k.add(button(k, 0, 14, 278, 100, 20, BP2_RANK, BP2_RANK_H, "Invite"));
        k.add(text(k, 0, 14, 278, 100, 20, P12, DARK_LABEL, "Invite", 1, false));
        k.mark("LEAVE");
        k.add(button(k, 0, 120, 278, 100, 20, BP2_RANK, BP2_RANK_H, "Leave"));
        k.add(text(k, 0, 120, 278, 100, 20, P12, DARK_LABEL, "Leave party", 1, false));
        k.mark("START");
        k.add(button(k, 0, 370, 276, 132, 24, BP2_UPGRADE, BP2_UPGRADE_H, "Start"));
        k.add(text(k, 0, 370, 276, 132, 24, B12, DARK_LABEL, "Start Run", 1, false));
        k.mark("FOOTER");
        k.add(text(k, 0, 176, 319, 228, 12, P11, INK_SOFT, "Carpet members enter with the leader.", 1, true));
        return k;
    }

    /** the Hall of Seals: this week's top 5 solo and group full clears + the weekly reset countdown */
    static IfKit fiveSealsHall(Store store) throws Exception {
        IfKit k = kit(1191, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, FS_HALL_BG));
        k.mark("CLOSE");
        k.add(button(k, 0, 486, 6, 20, 20, BP2_CLOSE, BP2_CLOSE_H, "Close"));
        k.mark("TITLE");
        k.add(text(k, 0, 124, 14, 250, 18, B12, GOLD, "The Hall of Seals", 1, true));
        k.add(text(k, 0, 16, 57, 230, 18, B12, GOLD, "Fastest Solo", 1, true));
        k.add(text(k, 0, 266, 57, 230, 18, B12, GOLD, "Fastest Group", 1, true));
        for (String col : new String[]{"SOLO", "GROUP"}) {
            int x0 = col.equals("SOLO") ? 16 : 266;
            k.mark("FIRST_" + col);
            for (int r = 0; r < 5; r++) {
                int y0 = 82 + 40 * r;
                k.add(text(k, 0, x0 + 4, y0 + 2, 24, 32, B12, GOLD, String.valueOf(r + 1), 1, true));  // +0 rank
                k.add(text(k, 0, x0 + 30, y0 + 3, 196, 14, P12, CREAM, "", 0, true));                  // +1 time
                k.add(text(k, 0, x0 + 30, y0 + 18, 196, 14, P11, INK_SOFT, "", 0, true));              // +2 names
            }
        }
        k.mark("RESET");
        k.add(text(k, 0, 16, 300, 236, 14, P12, CREAM, "", 0, true));
        k.mark("BEST");
        k.add(text(k, 0, 260, 300, 236, 14, P12, GOLD, "", 2, true));
        return k;
    }

    // ------------------------------------------------------------------ 1192 The Five Seals Token Shop

    /** build_fs_shop.py: the window, the two card sizes, tabs and buttons */
    static final int FS_SHOP_BG = 25314, FS_CARD = 25315, FS_CARD_BIG = 25316, FS_TAB = 25317, FS_TAB_H = 25318, FS_TAB_ON = 25319,
            FS_BUY = 25320, FS_BUY_H = 25321, FS_UNLOCK = 25322, FS_UNLOCK_H = 25323;

    /**
     * The Five Seals Token Shop (user 2026-11-16: "a new design shop interface, not the osrs one, unique, match Karn"):
     * a Karn crimson / gold window with two tabs. Rewards: eight cards (item icon, name, shard price, a Buy button with
     * Buy 1 / 5 / 10 / X). Ascension: three account upgrade cards (icon, name, what it does, tier / owned, price, Unlock).
     * The shard purse top right, the selected card's details in the footer. Every value is sent by FiveSealsShop.
     */
    static IfKit fiveSealsShop(Store store) throws Exception {
        IfKit k = kit(1192, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, FS_SHOP_BG));
        k.mark("CLOSE");
        k.add(button(k, 0, 486, 6, 20, 20, BP2_CLOSE, BP2_CLOSE_H, "Close"));
        k.mark("TITLE");
        k.add(text(k, 0, 124, 14, 250, 18, B12, GOLD, "The Five Seals - Token Shop", 1, true));
        // tabs: the button, a "selected" plate over it (no ops, hidden when off), the label
        int[] tabX = {14, 140};
        String[] tabs = {"Rewards", "Ascension"};
        for (int t = 0; t < 2; t++) {
            k.mark("TAB_" + t);
            k.add(button(k, 0, tabX[t], 53, 120, 22, FS_TAB, FS_TAB_H, "View " + tabs[t]));      // +0
            k.add(IfKit.hidden(sprite(k, 0, tabX[t], 53, 120, 22, FS_TAB_ON)));                    // +1 selected
            k.add(text(k, 0, tabX[t], 53, 120, 22, B12, GOLD, tabs[t], 1, true));                   // +2 label
        }
        k.mark("PURSE_ITEM");
        k.add(k.layer(0, 302, 48, 36, 32));
        k.mark("PURSE");
        k.add(text(k, 0, 340, 56, 156, 16, P12, GOLD, "", 0, true));

        // ---- rewards: 4 x 2 cards
        k.mark("REWARDS");
        int rewards = k.add(k.layer(0, 0, 0, 512, 334));
        k.mark("FIRST_REWARD");
        for (int i = 0; i < 8; i++) {
            int x = 14 + 122 * (i % 4), y = 84 + 106 * (i / 4);
            if (i == 1)
                System.out.println("CONST 1192 REWARD_STRIDE = " + (k.next() - k.ids.get("FIRST_REWARD")));
            k.add(sprite(k, rewards, x, y, 116, 100, FS_CARD));                                    // +0 card
            k.add(k.layer(rewards, x + 40, y + 6, 36, 32));                                        // +1 item (clientscript 149)
            k.add(text(k, rewards, x + 3, y + 44, 110, 13, P11, CREAM, "", 1, true));             // +2 name
            k.add(text(k, rewards, x + 3, y + 58, 110, 13, P11, GOLD, "", 1, true));              // +3 price
            InterfaceDefinition buy = button(k, rewards, x + 16, y + 74, 84, 20, FS_BUY, FS_BUY_H, "Buy 1");
            buy.actions = new String[]{"Buy 1", "Buy 5", "Buy 10", "Buy X"};
            k.add(buy);                                                                            // +4 buy
            k.add(text(k, rewards, x + 16, y + 74, 84, 20, P12, DARK_LABEL, "Buy", 1, false));    // +5 label
        }

        // ---- ascension upgrades: 3 big cards (hidden until the tab is picked)
        k.mark("UPGRADES");
        int upgrades = k.add(IfKit.hidden(k.layer(0, 0, 0, 512, 334)));
        k.mark("FIRST_UPGRADE");
        for (int i = 0; i < 3; i++) {
            int x = 14 + 162 * i, y = 84;
            if (i == 1)
                System.out.println("CONST 1192 UPGRADE_STRIDE = " + (k.next() - k.ids.get("FIRST_UPGRADE")));
            k.add(sprite(k, upgrades, x, y, 156, 206, FS_CARD_BIG));                               // +0 card
            k.add(k.layer(upgrades, x + 60, y + 9, 36, 32));                                       // +1 icon
            k.add(text(k, upgrades, x + 4, y + 50, 148, 15, B12, GOLD, "", 1, true));             // +2 name
            k.add(block(k, upgrades, x + 10, y + 68, 136, 66, P11, CREAM, 12, 1, true));          // +3 what it does
            k.add(text(k, upgrades, x + 4, y + 138, 148, 13, P11, INK_SOFT, "", 1, true));        // +4 tier / owned
            k.add(text(k, upgrades, x + 4, y + 154, 148, 13, P12, GOLD, "", 1, true));            // +5 price
            k.add(button(k, upgrades, x + 26, y + 176, 104, 22, FS_UNLOCK, FS_UNLOCK_H, "Unlock")); // +6 unlock
            k.add(text(k, upgrades, x + 26, y + 176, 104, 22, P12, DARK_LABEL, "Unlock", 1, false)); // +7 label
        }

        k.mark("INFO");
        k.add(block(k, 0, 16, 300, 480, 26, P11, CREAM, 12, 1, true));
        for (java.util.Map.Entry<String, Integer> e : k.ids.entrySet())
            System.out.println("CONST 1192 " + e.getKey() + " = " + e.getValue());
        return k;
    }

    // ------------------------------------------------------------------ 1188 Daily Tasks

    static IfKit dailyTasks(Store store) throws Exception {
        IfKit k = kit(1188, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, BG_DAILY));
        close(k, 485, 30);
        k.mark("SUBTITLE");
        k.add(text(k, 0, 122, 58, 268, 14, P12, CREAM, "", 1, true));
        k.mark("FIRST_TASK");
        for (int r = 0; r < 5; r++) {
            int y0 = 80 + 40 * r;
            if (r == 1)
                System.out.println("CONST 1188 TASK_STRIDE = " + (k.next() - k.ids.get("FIRST_TASK")));
            k.add(text(k, 0, 24, y0 + 2, 222, 14, B12, GOLD, "", 0, true));      // +0 name
            k.add(text(k, 0, 250, y0 + 2, 80, 14, P12, INK_SOFT, "", 2, true));  // +1 progress
            k.add(text(k, 0, 24, y0 + 16, 304, 13, P12, CREAM, "", 0, true));    // +2 description
            for (int s = 0; s < 20; s++)                                          // +3..22 bar
                k.add(IfKit.hidden(k.solid(0, 24 + 11 * s, y0 + 30, 11, 4, SEG)));
            k.add(k.layer(0, 335, y0 + 3, 36, 32));                              // +23 reward item grid
            k.add(k.layer(0, 375, y0 + 3, 36, 32));                              // +24 coins grid
            k.add(button(k, 0, 418, y0 + 8, 70, 20, CLAIM, -1, "Claim"));          // +25 (no hover: the server shows its state)
            k.add(text(k, 0, 418, y0 + 8, 70, 20, P12, GOLD, "Claim", 1, true)); // +26
        }
        k.mark("BONUS_TITLE");
        k.add(text(k, 0, 26, 286, 176, 14, B12, GOLD, "Completion bonus", 0, true));
        k.mark("BONUS_TEXT");
        k.add(text(k, 0, 26, 301, 176, 14, P12, CREAM, "", 0, true));
        k.mark("BONUS_ITEM");
        k.add(k.layer(0, 207, 285, 36, 32));
        k.mark("BONUS_INFO");
        k.add(text(k, 0, 250, 293, 100, 16, P12, INK_SOFT, "", 0, true));
        labelled(k, "BONUS_CLAIM", 354, 291, 134, 20, GBTN, GBTN_H, "Claim bonus", P12, 0x3a1a08, false, "Claim Bonus");
        return k;
    }

    // ------------------------------------------------------------------ 1187 Best in Slot

    /** the grid cells (game px, from build_hub.py BIS_CELLS / 3): x, y, size; their equipment slots */
    static final int[][] CELLS = {{59, 101}, {28, 126}, {59, 126}, {91, 126}, {28, 154}, {59, 154}, {91, 154},
            {28, 185}, {59, 185}, {91, 185}, {28, 213}};

    static IfKit bestInSlot(Store store) throws Exception {
        IfKit k = kit(1187, store);
        k.add(sprite(k, 0, 0, 0, 512, 334, BG_BIS));
        close(k, 485, 30);
        k.mark("SUBTITLE");
        k.add(text(k, 0, 122, 58, 268, 14, P12, CREAM, "The best gear for each slot and combat style", 1, true));
        k.mark("FIRST_CELL");
        for (int[] c : CELLS) {
            k.add(IfKit.hidden(sprite(k, 0, c[0] - 1, c[1] - 1, 30, 30, CELL_SEL))); // +0 selected
            k.add(hotspot(k, 0, c[0], c[1], 28, 28, "Select"));                       // +1
        }
        k.mark("WORN");
        k.add(text(k, 0, 21, 267, 104, 13, P12, CRIMSON, "", 0, false));
        k.add(text(k, 0, 21, 279, 104, 13, P12, INK, "", 0, false));
        k.add(text(k, 0, 21, 291, 104, 13, P12, INK, "", 0, false));
        k.mark("FIRST_STYLE");
        String[] styles = {"Stab", "Slash", "Crush", "Magic", "Range"};
        for (int i = 0; i < 5; i++) {
            int x = 133 + 47 * i;
            k.add(button(k, 0, x, 82, 44, 20, TAB_BIS, TAB_BIS_H, "View"));
            k.add(IfKit.hidden(sprite(k, 0, x, 82, 44, 20, TAB_BIS_ON)));
            k.add(text(k, 0, x, 82, 44, 20, P12, CREAM, styles[i], 1, true));
        }
        k.add(text(k, 0, 137, 106, 16, 14, P12, GOLD, "#", 1, true));
        k.mark("HEAD_ITEM");
        k.add(text(k, 0, 199, 106, 120, 14, P12, GOLD, "", 0, true));
        k.mark("HEAD_VALUE");
        k.add(text(k, 0, 270, 106, 94, 14, P12, GOLD, "", 2, true));
        k.mark("SCROLL");
        int scroll = k.add(k.layer(0, 135, 127, 214, 173));
        k.mark("SCROLLBAR");
        k.add(k.layer(0, 352, 127, 16, 173));
        k.mark("FIRST_ROW");
        for (int r = 0; r < BIS_ROWS; r++) {
            if (r == 1)
                System.out.println("CONST 1187 ROW_STRIDE = " + (k.next() - k.ids.get("FIRST_ROW")));
            int row = k.add(k.layer(scroll, 0, r * 36, 214, 34));                       // +0
            k.add(button(k, row, 0, 0, 214, 34, ROW, ROW_H, "View"));                   // +1
            k.add(IfKit.hidden(sprite(k, row, 0, 0, 214, 34, ROW_SEL)));                 // +2
            k.add(text(k, row, 2, 0, 20, 34, P12, INK, "", 1, false));                   // +3 rank
            k.add(k.itemSlot(row, 23, 1, 36, 32, null));                                 // +4 item
            k.add(text(k, row, 64, 3, 104, 13, P12, INK, "", 0, false));                 // +5 name
            k.add(k.solid(row, 64, 22, 100, 5, TRACK));                                  // +6 track
            for (int s = 0; s < 10; s++)                                                 // +7..16 bar
                k.add(IfKit.hidden(k.solid(row, 64 + 10 * s, 22, 10, 5, SEG)));
            k.add(text(k, row, 160, 3, 50, 13, P12, INK, "", 2, false));                 // +17 value
        }
        k.mark("MODEL");
        k.add(k.model(0, 380, 79, 116, 64));
        k.mark("NAME");
        k.add(text(k, 0, 380, 146, 116, 14, B12, GOLD, "", 1, true));
        k.mark("TYPE");
        k.add(text(k, 0, 380, 160, 116, 13, P12, CREAM, "", 1, true));
        k.mark("RANK");
        k.add(text(k, 0, 380, 173, 116, 13, P12, CREAM, "", 1, true));
        k.mark("FIRST_STAT");
        int[] sy = {201, 213, 224, 236, 247};
        for (int s = 0; s < 5; s++) {
            k.add(text(k, 0, 402, sy[s], 40, 12, P12, CREAM, styles[s], 0, true));
            k.add(text(k, 0, 440, sy[s], 52, 12, P12, CREAM, "", 2, true));
        }
        k.mark("COMPARE");
        k.add(hotspot(k, 0, 387, 276, 98, 20, "Compare"));

        // the compare page (user 2026-11-15: "the compare message text is so boring"): over the list, hidden until Compare
        k.mark("CMP");
        int cmp = k.add(IfKit.hidden(k.layer(0, 131, 80, 241, 222)));
        k.add(sprite(k, cmp, 0, 0, 241, 222, COMPARE_BG));
        k.add(text(k, cmp, 8, 1, 150, 14, B12, GOLD, "Compare", 0, true));
        k.mark("CMP_BACK");
        k.add(button(k, cmp, 193, 1, 40, 14, SBTN, SBTN_H, "Back"));
        k.add(text(k, cmp, 193, 1, 40, 14, P11, GOLD, "Back", 1, true));
        k.mark("CMP_NAMES");
        k.add(text(k, cmp, 6, 17, 229, 13, P12, CREAM, "", 1, true));
        k.add(text(k, cmp, 8, 36, 84, 13, P12, GOLD, "Bonus", 0, true));
        k.add(text(k, cmp, 8, 50, 84, 13, P11, INK_SOFT, "picked / worn", 0, true));
        k.mark("CMP_ITEM_A");
        k.add(k.itemSlot(cmp, 99, 32, 36, 32, null));
        k.mark("CMP_ITEM_B");
        k.add(k.itemSlot(cmp, 147, 32, 36, 32, null));
        k.add(text(k, cmp, 194, 44, 42, 13, P11, INK_SOFT, "diff", 2, true));
        String[] bonuses = {"Stab att", "Slash att", "Crush att", "Magic att", "Range att", "Stab def", "Slash def",
                "Crush def", "Magic def", "Range def", "Melee str", "Ranged str", "Magic dmg", "Prayer"};
        k.mark("CMP_ROW");
        for (int r = 0; r < bonuses.length; r++) {
            int y = 67 + 11 * r;
            k.add(text(k, cmp, 8, y - 1, 84, 12, P12, CREAM, bonuses[r], 0, true)); // +0 label
            k.add(text(k, cmp, 92, y - 1, 50, 12, P12, CREAM, "", 1, true));        // +1 picked
            k.add(text(k, cmp, 142, y - 1, 50, 12, P12, CREAM, "", 1, true));       // +2 worn
            k.add(text(k, cmp, 192, y - 1, 43, 12, P12, CREAM, "", 2, true));       // +3 difference
        }
        return k;
    }
}
