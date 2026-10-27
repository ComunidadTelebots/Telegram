package org.telegram.ui.web;

import org.telegram.tgnet.tl.TL_iv;

/** Local reader-only marker. Never sent to Telegram or treated as publisher-supplied HTML. */
public final class AmpInteractiveBlock extends TL_iv.pageBlockEmbed {
    public String label;
    public AmpInteractiveBlock(String source, String title) {
        if (!AmpEmbedPolicy.allows(source)) throw new IllegalArgumentException("Unsupported embed");
        url = source;
        label = title == null || title.trim().isEmpty() ? "Interactive graphic" : title.substring(0, Math.min(160, title.length()));
        w = 640;
        h = 360;
        caption = new TL_iv.PageCaption();
        caption.text = new TL_iv.textEmpty();
        caption.credit = new TL_iv.textEmpty();
    }
}
