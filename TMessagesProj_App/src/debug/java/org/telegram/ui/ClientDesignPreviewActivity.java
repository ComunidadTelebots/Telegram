package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.skins.SkinBase;
import org.telegram.ui.skins.SkinManager;
import org.telegram.ui.skins.SkinPreviewDialog;

/** Debug-only host for screenshots of the existing native design preview. */
public final class ClientDesignPreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ApplicationLoader.postInitApplication();
        TextView label = new TextView(this);
        label.setText("VISTA PREVIA NATIVA · DATOS DE EJEMPLO");
        label.setTextSize(12);
        label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        label.setPadding(12, 110, 12, 12);
        label.setTextColor(0xff46566a);
        label.setBackgroundColor(0xffedf1f5);
        setContentView(label);
        String id = getIntent().getStringExtra("skin");
        for (SkinBase skin : SkinManager.get().getSkins()) {
            if (skin.getId().equals(id)) {
                label.post(() -> SkinPreviewDialog.show(this, skin, this::finish));
                return;
            }
        }
        finish();
    }
}
