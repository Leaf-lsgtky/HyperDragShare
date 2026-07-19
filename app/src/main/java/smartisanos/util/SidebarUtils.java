package smartisanos.util;

import android.content.Context;
import android.view.View;

/** DragShare has no Smartisan sidebar integration; keep the imported chip view's optional path inert. */
public final class SidebarUtils {
    private SidebarUtils() {}

    public static boolean isSidebarShowing(Context context) {
        return false;
    }

    public static void dragText(View source, Context context, String text) {
        // The sidebar is unavailable in DragShare.
    }
}
