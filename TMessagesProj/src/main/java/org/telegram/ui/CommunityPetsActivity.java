package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TelebotsPetScanner;
import org.telegram.messenger.TelebotsPetState;
import org.telegram.messenger.TelebotsPetStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TelebotsPetAvatarView;
import org.telegram.ui.Components.TelebotsCompanionOverlayView;

import java.io.InputStream;

/**
 * First-party Comunidad Telebots virtual mascot. No chat access; all pet data is local.
 * The scanner uses bundled on-device ML Kit and discards image data after classification.
 */
public class CommunityPetsActivity extends BaseFragment {
    private static final int PICK_IMAGE = 9021;
    private static final int CAMERA_THUMBNAIL = 9022;

    private TelebotsPetAvatarView avatar;
    private TextView status;
    private TextView scanInfo;
    private EditText name;
    private Spinner species;
    private CheckBox enabled;
    private Button feed;
    private Button play;
    private Button rest;
    private Button encourage;

    private static int dp(int n) {
        return AndroidUtilities.dp(n);
    }

    private TextView label(Context context, String title) {
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(15);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setPadding(0, dp(10), 0, dp(10));
        return label;
    }

    private Button button(Context context, int title, Runnable click) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(LocaleController.getString(title));
        button.setOnClickListener(v -> click.run());
        return button;
    }

    private void refresh() {
        if (status == null || avatar == null) return;
        TelebotsPetState state = TelebotsPetStore.load();
        status.setText(LocaleController.formatString(R.string.TelebotsPetsStats,
                state.mood, state.energy, state.affection, state.interactions));
        scanInfo.setText(LocaleController.formatString(R.string.TelebotsPetsOrigin,
                TelebotsPetStore.originLabel().isEmpty()
                        ? LocaleController.getString(R.string.TelebotsPetsNoOrigin)
                        : TelebotsPetStore.originLabel()));
        avatar.setPet(TelebotsPetStore.species(), TelebotsPetStore.color());
        boolean active = TelebotsPetStore.enabled();
        if (feed != null) feed.setEnabled(active);
        if (play != null) play.setEnabled(active);
        if (rest != null) rest.setEnabled(active);
        if (encourage != null) encourage.setEnabled(active);
    }

    private void interact(int action) {
        if (!TelebotsPetStore.enabled()) return;
        TelebotsPetStore.save(TelebotsPetStore.load().interact(action));
        refresh();
    }

    private void persistAppearance() {
        TelebotsPetStore.setName(name.getText().toString());
        TelebotsPetStore.setSpecies(species.getSelectedItemPosition());
        refresh();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.TelebotsPetsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(30));
        scroll.addView(body);

        enabled = new CheckBox(context);
        enabled.setText(LocaleController.getString(R.string.TelebotsPetsEnabled));
        enabled.setChecked(TelebotsPetStore.enabled());
        enabled.setMinHeight(dp(52));
        enabled.setOnCheckedChangeListener((v, checked) -> {
            TelebotsPetStore.setEnabled(checked);
            refresh();
        });
        body.addView(enabled);

        TextView section = label(context, LocaleController.getString(R.string.TelebotsCompanionSettings));
        section.setTextSize(18);
        body.addView(section);

        CheckBox inChats = new CheckBox(context);
        inChats.setText(LocaleController.getString(R.string.TelebotsCompanionInChats));
        inChats.setMinHeight(dp(52));
        inChats.setChecked(TelebotsPetStore.companionInChats());
        inChats.setOnCheckedChangeListener((v, checked) ->
                TelebotsPetStore.setCompanionInChats(checked));
        body.addView(inChats);

        CheckBox reduceMotion = new CheckBox(context);
        reduceMotion.setText(LocaleController.getString(R.string.TelebotsCompanionReduceMotion));
        reduceMotion.setMinHeight(dp(52));
        reduceMotion.setChecked(TelebotsPetStore.reduceMotion());
        reduceMotion.setOnCheckedChangeListener((v, checked) ->
                TelebotsPetStore.setReduceMotion(checked));
        body.addView(reduceMotion);
        body.addView(label(context, LocaleController.getString(R.string.TelebotsCompanionPrivacy)));

        TextView previewLabel = label(context, LocaleController.getString(R.string.TelebotsCompanionPreview));
        previewLabel.setTextSize(17);
        body.addView(previewLabel);
        FrameLayout preview = new FrameLayout(context);
        preview.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        TelebotsCompanionOverlayView demoPet = new TelebotsCompanionOverlayView(context);
        FrameLayout.LayoutParams demoParams = new FrameLayout.LayoutParams(dp(92), dp(92),
                Gravity.CENTER);
        preview.addView(demoPet, demoParams);
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(136)));

        avatar = new TelebotsPetAvatarView(context);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(-1, dp(175));
        avatarParams.topMargin = dp(6);
        body.addView(avatar, avatarParams);

        body.addView(label(context, LocaleController.getString(R.string.TelebotsPetsName)));
        name = new EditText(context);
        name.setSingleLine(true);
        name.setMaxLines(1);
        name.setText(TelebotsPetStore.name());
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        body.addView(name);
        body.addView(label(context, LocaleController.getString(R.string.TelebotsPetsSpecies)));
        species = new Spinner(context);
        species.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        LocaleController.getString(R.string.TelebotsPetsCat),
                        LocaleController.getString(R.string.TelebotsPetsDog),
                        LocaleController.getString(R.string.TelebotsPetsFox),
                        LocaleController.getString(R.string.TelebotsPetsDuck)
                }));
        species.setSelection(TelebotsPetStore.species());
        body.addView(species);
        body.addView(button(context, R.string.TelebotsPetsSave, this::persistAppearance));

        status = label(context, "");
        body.addView(status);

        feed = button(context, R.string.TelebotsPetsFeed, () -> interact(TelebotsPetState.FEED));
        play = button(context, R.string.TelebotsPetsPlay, () -> interact(TelebotsPetState.PLAY));
        rest = button(context, R.string.TelebotsPetsRest, () -> interact(TelebotsPetState.REST));
        encourage = button(context, R.string.TelebotsPetsEncourage, () -> interact(TelebotsPetState.ENCOURAGE));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(feed, new LinearLayout.LayoutParams(0, dp(54), 1));
        buttons.addView(play, new LinearLayout.LayoutParams(0, dp(54), 1));
        buttons.addView(rest, new LinearLayout.LayoutParams(0, dp(54), 1));
        body.addView(buttons);
        body.addView(encourage);

        TextView scannerTitle = label(context, LocaleController.getString(R.string.TelebotsPetsScannerTitle));
        scannerTitle.setTextSize(18);
        body.addView(scannerTitle);
        body.addView(label(context, LocaleController.getString(R.string.TelebotsPetsScannerExplanation)));
        scanInfo = label(context, "");
        body.addView(scanInfo);

        body.addView(button(context, R.string.TelebotsPetsChoosePhoto, () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        }));
        body.addView(button(context, R.string.TelebotsPetsCamera, () -> {
            try {
                startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_THUMBNAIL);
            } catch (Exception exception) {
                Toast.makeText(context, LocaleController.getString(R.string.TelebotsPetsCameraUnavailable),
                        Toast.LENGTH_LONG).show();
            }
        }));

        body.addView(label(context, LocaleController.getString(R.string.TelebotsPetsPrivacy)));
        body.addView(button(context, R.string.TelebotsPetsReset, () -> {
            TelebotsPetStore.clear();
            enabled.setChecked(false);
            name.setText(TelebotsPetStore.name());
            species.setSelection(TelebotsPetStore.species());
            refresh();
        }));
        refresh();
        fragmentView = scroll;
        return scroll;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || getParentActivity() == null) return;
        Bitmap bitmap = null;
        try {
            if (requestCode == PICK_IMAGE && data.getData() != null) {
                // Decode at most ~1024px to avoid allocating full-size camera images.
                try (InputStream stream = getParentActivity().getContentResolver().openInputStream(data.getData())) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(stream, null, bounds);
                    int sample = 1;
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2;
                    try (InputStream second = getParentActivity().getContentResolver().openInputStream(data.getData())) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = sample;
                        bitmap = BitmapFactory.decodeStream(second, null, opts);
                    }
                }
            } else if (requestCode == CAMERA_THUMBNAIL && data.getExtras() != null) {
                Object thumbnail = data.getExtras().get("data");
                if (thumbnail instanceof Bitmap) bitmap = (Bitmap) thumbnail;
            }
        } catch (Exception error) {
            Toast.makeText(getParentActivity(), LocaleController.getString(R.string.TelebotsPetsScanFailed),
                    Toast.LENGTH_LONG).show();
        }
        if (bitmap == null) return;
        Bitmap finalBitmap = bitmap;
        TelebotsPetScanner.analyze(finalBitmap, new TelebotsPetScanner.Callback() {
            @Override public void success(int color, String label, float confidence) {
                if (fragmentView == null) return;
                TelebotsPetStore.setScannedAppearance(color, label);
                refresh();
                Toast.makeText(getParentActivity(), LocaleController.getString(R.string.TelebotsPetsScanDone),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void failure(Exception error) {
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), LocaleController.getString(R.string.TelebotsPetsScanFailed),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
