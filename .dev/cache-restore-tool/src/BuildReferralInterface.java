import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1165, Zelus Referrals (content/referral/ReferralInterface.java, server):
//  - header strip: promotion countdown (left) + top reward banner (right);
//  - left: "Who invited you?" name box (click = chatbox name input) + Submit, a status line, the Rewards (what the
//    invitee / the inviter get, item slots), then Your referrals / Your rank;
//  - right: Top 10 Referrers (rank, name, count) and the Prize Showcase (5 item slots + caption).
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildReferralInterface {
    static final int GROUP = 1165, ROWS = 10, PRIZES = 5;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            // ---- header strip ----
            k.inset(0, 24, 36, 464, 20);
            k.mark("PROMO_TIMER");
            k.add(k.text(0, 32, 36, 200, 20, IfKit.TEXT_FONT, IfKit.ORANGE, ""));
            k.mark("PROMO_BANNER");
            k.add(IfKit.right(k.text(0, 236, 36, 246, 20, IfKit.TEXT_FONT, IfKit.GOLD, "")));

            // ---- left: submission + rewards + your stats ----
            k.mark("LEFT");
            int left = k.panel(0, 24, 60, 226, 246);
            k.add(IfKit.centred(k.text(left, 0, 4, 226, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Who invited you?")));
            k.mark("INPUT");
            var box = k.solid(left, 8, 24, 142, 22, 0x25201b);
            box.clickMask = 2;
            box.actions = new String[]{"Enter name"};
            box.onMouseOverListener = new Object[]{45, -2147483645, 0x2f2924};
            box.onMouseLeaveListener = new Object[]{45, -2147483645, 0x25201b};
            box.hasListener = true;
            k.add(box);
            k.add(k.solid(left, 8, 24, 142, 1, IfKit.SHADOW));
            k.add(k.solid(left, 8, 24, 1, 22, IfKit.SHADOW));
            k.add(k.outline(left, 8, 24, 142, 22, IfKit.TRIM, false));
            k.mark("INPUT_TEXT");
            k.add(k.text(left, 14, 24, 134, 22, IfKit.TEXT_FONT, IfKit.GREY, "Enter Name"));
            k.mark("SUBMIT");
            k.bezel(left, 154, 23, 64, 24, "Submit");
            k.mark("STATUS");
            k.add(k.block(left, 8, 50, 210, 26, IfKit.TEXT, 12, true));
            k.add(k.solid(left, 8, 79, 210, 1, IfKit.BORDER));

            k.add(IfKit.centred(k.text(left, 0, 82, 226, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Rewards")));
            k.add(k.text(left, 8, 98, 104, 14, IfKit.TEXT_FONT, IfKit.GREY, "You receive"));
            k.add(k.text(left, 116, 98, 104, 14, IfKit.TEXT_FONT, IfKit.GREY, "Your inviter receives"));
            k.mark("YOU_SLOT_1");
            k.slot(left, 8, 114, "Examine");
            k.mark("YOU_SLOT_2");
            k.slot(left, 48, 114, "Examine");
            k.mark("INVITER_SLOT");
            k.slot(left, 116, 114, "Examine");
            k.mark("INVITER_POINTS");
            k.add(k.block(left, 156, 114, 64, 32, IfKit.GREEN, 12, false));
            k.mark("REWARD_NOTE");
            k.add(k.block(left, 8, 150, 210, 26, 0x8f8f8f, 12, true));
            k.add(k.solid(left, 8, 179, 210, 1, IfKit.BORDER));

            k.inset(left, 8, 184, 210, 54);
            k.mark("MY_REFERRALS");
            k.add(k.text(left, 14, 187, 198, 16, IfKit.TEXT_FONT, IfKit.WHITE, ""));
            k.mark("MY_RANK");
            k.add(k.text(left, 14, 203, 198, 16, IfKit.TEXT_FONT, IfKit.WHITE, ""));
            k.mark("MY_NOTE");
            k.add(k.text(left, 14, 219, 198, 16, IfKit.TEXT_FONT, IfKit.GREY, ""));

            // ---- right: leaderboard + prize showcase ----
            k.mark("RIGHT");
            int right = k.panel(0, 256, 60, 232, 246);
            k.add(IfKit.centred(k.text(right, 0, 4, 232, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Top 10 Referrers")));
            k.add(k.text(right, 8, 21, 20, 13, IfKit.TEXT_FONT, IfKit.GREY, "#"));
            k.add(k.text(right, 30, 21, 118, 13, IfKit.TEXT_FONT, IfKit.GREY, "Player"));
            k.add(IfKit.right(k.text(right, 150, 21, 74, 13, IfKit.TEXT_FONT, IfKit.GREY, "Referrals")));
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int y = 36 + r * 14;
                k.add(k.solid(right, 5, y, 222, 14, r % 2 == 0 ? IfKit.ROW_A : IfKit.ROW_B));
                k.add(k.text(right, 8, y, 20, 14, IfKit.TEXT_FONT, IfKit.ORANGE, ""));
                k.add(k.text(right, 30, y, 118, 14, IfKit.TEXT_FONT, IfKit.WHITE, ""));
                k.add(IfKit.right(k.text(right, 150, y, 74, 14, IfKit.TEXT_FONT, IfKit.GREEN, "")));
            }
            k.add(k.solid(right, 8, 179, 216, 1, IfKit.BORDER));
            k.add(IfKit.centred(k.text(right, 0, 182, 232, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Prize Showcase")));
            k.mark("FIRST_PRIZE");
            for (int p = 0; p < PRIZES; p++)
                k.slot(right, 8 + p * 44, 200, "Examine");
            k.mark("PRIZE_CAPTION");
            k.add(IfKit.centred(k.text(right, 4, 233, 224, 13, IfKit.TEXT_FONT, IfKit.GOLD, "")));

            k.finish(apply);
        }
    }
}
