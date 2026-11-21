package com.example.fitplan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java lightweight Markdown block and inline parser for chat bubbles (headings, bullets,
 * numbered lists, dividers, bold, inline code) with zero third-party dependencies.
 */
public final class MarkdownParser {
    public enum BlockType {
        HEADING,
        BULLET,
        NUMBERED,
        DIVIDER,
        PARAGRAPH
    }

    public enum SpanType {
        PLAIN,
        BOLD,
        CODE
    }

    public static final class InlineSpan {
        public final SpanType type;
        public final String text;

        public InlineSpan(SpanType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public static final class Block {
        public final BlockType type;
        public final int level;
        public final String prefix;
        public final List<InlineSpan> spans;

        public Block(BlockType type, int level, String prefix, List<InlineSpan> spans) {
            this.type = type;
            this.level = level;
            this.prefix = prefix == null ? "" : prefix;
            this.spans =
                    spans == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(spans));
        }
    }

    private MarkdownParser() {}

    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isDivider(trimmed)) {
                blocks.add(new Block(BlockType.DIVIDER, 0, "", Collections.emptyList()));
                continue;
            }
            int headingLevel = headingLevel(trimmed);
            if (headingLevel > 0) {
                String text = trimmed.substring(headingLevel).trim();
                blocks.add(new Block(BlockType.HEADING, headingLevel, "", parseInline(text)));
                continue;
            }
            if (isBullet(trimmed)) {
                String text = trimmed.substring(2).trim();
                blocks.add(new Block(BlockType.BULLET, 0, "• ", parseInline(text)));
                continue;
            }
            String numberPrefix = numberedPrefix(trimmed);
            if (numberPrefix != null) {
                String text = trimmed.substring(numberPrefix.length()).trim();
                blocks.add(new Block(BlockType.NUMBERED, 0, numberPrefix + " ", parseInline(text)));
                continue;
            }
            blocks.add(new Block(BlockType.PARAGRAPH, 0, "", parseInline(trimmed)));
        }
        return blocks;
    }

    public static List<InlineSpan> parseInline(String text) {
        List<InlineSpan> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        StringBuilder plain = new StringBuilder();
        int i = 0;
        int len = text.length();
        while (i < len) {
            if (i + 1 < len && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) {
                    flushPlain(spans, plain);
                    spans.add(new InlineSpan(SpanType.BOLD, text.substring(i + 2, end)));
                    i = end + 2;
                    continue;
                }
            }
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    flushPlain(spans, plain);
                    spans.add(new InlineSpan(SpanType.CODE, text.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }
            plain.append(text.charAt(i));
            i++;
        }
        flushPlain(spans, plain);
        return spans;
    }

    private static void flushPlain(List<InlineSpan> spans, StringBuilder plain) {
        if (plain.length() > 0) {
            spans.add(new InlineSpan(SpanType.PLAIN, plain.toString()));
            plain.setLength(0);
        }
    }

    private static boolean isDivider(String line) {
        if (line.length() < 3) {
            return false;
        }
        char first = line.charAt(0);
        if (first != '-' && first != '*' && first != '_') {
            return false;
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != first && c != ' ') {
                return false;
            }
        }
        return true;
    }

    private static int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && count < 6 && line.charAt(count) == '#') {
            count++;
        }
        if (count > 0 && count < line.length() && line.charAt(count) == ' ') {
            return count;
        }
        return 0;
    }

    private static boolean isBullet(String line) {
        return line.length() >= 2
                && (line.charAt(0) == '-' || line.charAt(0) == '*' || line.charAt(0) == '•')
                && line.charAt(1) == ' ';
    }

    private static String numberedPrefix(String line) {
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        if (i > 0 && i + 1 < line.length() && line.charAt(i) == '.' && line.charAt(i + 1) == ' ') {
            return line.substring(0, i + 1);
        }
        return null;
    }
}
