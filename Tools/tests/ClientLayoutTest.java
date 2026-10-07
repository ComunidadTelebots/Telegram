import org.telegram.ui.skins.ClientLayout;

public final class ClientLayoutTest {
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        check(ClientLayout.forSkin("ios").placement == ClientLayout.Placement.BOTTOM, "iOS bottom navigation");
        check(ClientLayout.forSkin("telegramx").placement == ClientLayout.Placement.TOP, "X top navigation");
        check(ClientLayout.forSkin("unigram").placement == ClientLayout.Placement.SIDE, "Unigram side navigation");
        check(ClientLayout.forSkin("web") == ClientLayout.WEB_A, "Existing Web preference survives");
        check(ClientLayout.forSkin("webk") == ClientLayout.WEB_K, "Web K remains distinct");
        check(ClientLayout.forSkin("macos") == ClientLayout.MACOS, "macOS preference");
        for (String id : new String[]{null, "unknown", "android:current", "android:holo", "webogram"}) {
            check(ClientLayout.forSkin(id) == ClientLayout.ANDROID, "Legacy/default layout remains available: " + id);
        }
        check(ClientLayout.ANDROID.bottomInsetDp(80) == 80, "Native inset unchanged");
        check(ClientLayout.IOS.bottomInsetDp(80) == 56, "Bottom bar reserves own height");
        check(ClientLayout.MACOS.bottomInsetDp(80) == 48, "Compact bottom bar reserves own height");
        check(ClientLayout.TELEGRAM_X.bottomInsetDp(80) == 0, "Top bar removes obsolete bottom inset");
        check(ClientLayout.UNIGRAM.bottomInsetDp(80) == 0, "Side bar removes obsolete bottom inset");
        check(ClientLayout.IOS.extentDp(2f) > ClientLayout.IOS.extentDp(1f), "Large font expands navigation");
        check(ClientLayout.ANDROID.extentDp(2f) == 0, "Native layout unchanged");
        check(ClientLayout.IOS.bottomInsetDp(80, 2f) == ClientLayout.IOS.extentDp(2f), "Insets follow scaled bar");
        System.out.println("Client layout and inset regression tests passed");
    }
}
