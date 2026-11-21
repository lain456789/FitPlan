package com.example.fitplan;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.List;

/** Renders parsed lightweight Markdown into a FitPlan dark-theme Android CharSequence. */
final class MarkdownFormatter {
    private static final int BRIGHT = Color.rgb(250, 252, 250);
    private static final int CODE_BG = Color.rgb(42, 47, 56);

    private MarkdownFormatter() {}

    static CharSequence format(String markdown) {
        List<MarkdownParser.Block> blocks = MarkdownParser.parse(markdown);
        if (blocks.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            MarkdownParser.Block block = blocks.get(i);
            if (i > 0) {
                out.append(block.type == MarkdownParser.BlockType.HEADING ? "\n\n" : "\n");
            }
            if (block.type == MarkdownParser.BlockType.DIVIDER) {
                int start = out.length();
                out.append("────────────────────");
                out.setSpan(
                        new ForegroundColorSpan(MainActivity.MUTED),
                        start,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            int blockStart = out.length();
            if (!block.prefix.isEmpty()) {
                int prefixStart = out.length();
                out.append(block.prefix);
                out.setSpan(
                        new ForegroundColorSpan(MainActivity.ACCENT),
                        prefixStart,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        prefixStart,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for (MarkdownParser.InlineSpan span : block.spans) {
                int start = out.length();
                out.append(span.text);
                int end = out.length();
                if (span.type == MarkdownParser.SpanType.BOLD) {
                    out.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(
                            new ForegroundColorSpan(BRIGHT),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (span.type == MarkdownParser.SpanType.CODE) {
                    out.setSpan(
                            new TypefaceSpan("monospace"),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(
                            new BackgroundColorSpan(CODE_BG),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(
                            new ForegroundColorSpan(MainActivity.ACCENT),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (block.type == MarkdownParser.BlockType.HEADING) {
                float scale = block.level <= 2 ? 1.16f : 1.08f;
                out.setSpan(
                        new RelativeSizeSpan(scale),
                        blockStart,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        blockStart,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(
                        new ForegroundColorSpan(MainActivity.ACCENT),
                        blockStart,
                        out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }
}
