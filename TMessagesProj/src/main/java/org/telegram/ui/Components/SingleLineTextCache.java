package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;

/** One bounded, UI-thread text measurement. Colors and alpha intentionally do not invalidate it. */
public final class SingleLineTextCache {
    private String source, text, features, variations;
    private Object locales;
    private Typeface typeface;
    private float width, size, scale, skew, spacing, wordSpacing;
    private int flags;
    private boolean elegant, flatten;
    private float measuredWidth;

    public String get(String value, TextPaint paint, float availableWidth, boolean flattenNewlines) {
        if (value == null) value = "";
        availableWidth = Math.max(1, availableWidth);
        Object currentLocales = Build.VERSION.SDK_INT >= 24 ? paint.getTextLocales() : paint.getTextLocale();
        String currentVariations = Build.VERSION.SDK_INT >= 26 ? paint.getFontVariationSettings() : null;
        float currentWordSpacing = Build.VERSION.SDK_INT >= 29 ? paint.getWordSpacing() : 0;
        if (text != null && TextUtils.equals(source, value) && width == availableWidth && flatten == flattenNewlines
                && size == paint.getTextSize() && scale == paint.getTextScaleX() && skew == paint.getTextSkewX()
                && spacing == paint.getLetterSpacing() && wordSpacing == currentWordSpacing
                && flags == paint.getFlags() && typeface == paint.getTypeface()
                && elegant == paint.isElegantTextHeight() && currentLocales.equals(locales)
                && TextUtils.equals(features, paint.getFontFeatureSettings())
                && TextUtils.equals(variations, currentVariations)) {
            return text;
        }
        source = value;
        width = availableWidth;
        flatten = flattenNewlines;
        size = paint.getTextSize();
        scale = paint.getTextScaleX();
        skew = paint.getTextSkewX();
        spacing = paint.getLetterSpacing();
        wordSpacing = currentWordSpacing;
        flags = paint.getFlags();
        typeface = paint.getTypeface();
        elegant = paint.isElegantTextHeight();
        locales = currentLocales;
        features = paint.getFontFeatureSettings();
        variations = currentVariations;
        text = TextUtils.ellipsize(flattenNewlines ? value.replace('\n', ' ') : value,
                paint, availableWidth, TextUtils.TruncateAt.END).toString();
        measuredWidth = paint.measureText(text);
        return text;
    }

    public float measuredWidth() { return measuredWidth; }
}
