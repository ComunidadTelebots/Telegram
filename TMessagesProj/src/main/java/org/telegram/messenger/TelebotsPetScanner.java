package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.List;

/** Offline object-to-pet palette. Input images are never uploaded or stored. */
public final class TelebotsPetScanner {
    public interface Callback {
        void success(int color, String label, float confidence);
        void failure(Exception error);
    }

    private TelebotsPetScanner() { }

    public static void analyze(Bitmap bitmap, Callback callback) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            callback.failure(new IllegalArgumentException("Empty image"));
            return;
        }
        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * Math.min(1f, 768f / Math.max(bitmap.getWidth(), bitmap.getHeight())))),
                Math.max(1, Math.round(bitmap.getHeight() * Math.min(1f, 768f / Math.max(bitmap.getWidth(), bitmap.getHeight())))),
                true);
        int color = dominantColor(scaled);
        com.google.mlkit.vision.label.ImageLabeler labeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build());
        labeler.process(InputImage.fromBitmap(scaled, 0))
                .addOnSuccessListener(labels -> {
                    String name = labels.isEmpty() ? "Objeto" : labels.get(0).getText();
                    float confidence = labels.isEmpty() ? 0f : labels.get(0).getConfidence();
                    callback.success(color, name, confidence);
                })
                .addOnFailureListener(callback::failure)
                .addOnCompleteListener(task -> labeler.close());
    }

    static int dominantColor(Bitmap bitmap) {
        long red = 0, green = 0, blue = 0, count = 0;
        for (int y = bitmap.getHeight() / 5; y < bitmap.getHeight() * 4 / 5; y += Math.max(1, bitmap.getHeight() / 20)) {
            for (int x = bitmap.getWidth() / 5; x < bitmap.getWidth() * 4 / 5; x += Math.max(1, bitmap.getWidth() / 20)) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 128) continue;
                red += Color.red(c);
                green += Color.green(c);
                blue += Color.blue(c);
                count++;
            }
        }
        return count == 0 ? 0xff58c7b1 : Color.rgb((int)(red / count), (int)(green / count), (int)(blue / count));
    }
}
