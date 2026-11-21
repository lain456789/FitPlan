package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MarkdownParserTest {
    @Test
    public void parsesHeadingsListsDividersBoldAndCode() {
        String md =
                "### 训练诊断\n"
                        + "- **杠铃卧推** 在 `55kg` 停滞 3 周\n"
                        + "1. 先减载 **10%**\n"
                        + "---\n"
                        + "普通段落文本";

        List<MarkdownParser.Block> blocks = MarkdownParser.parse(md);
        assertEquals(5, blocks.size());

        assertEquals(MarkdownParser.BlockType.HEADING, blocks.get(0).type);
        assertEquals(3, blocks.get(0).level);
        assertEquals("训练诊断", blocks.get(0).spans.get(0).text);

        assertEquals(MarkdownParser.BlockType.BULLET, blocks.get(1).type);
        assertEquals("• ", blocks.get(1).prefix);
        assertEquals(4, blocks.get(1).spans.size());
        assertEquals(MarkdownParser.SpanType.BOLD, blocks.get(1).spans.get(0).type);
        assertEquals("杠铃卧推", blocks.get(1).spans.get(0).text);
        assertEquals(MarkdownParser.SpanType.CODE, blocks.get(1).spans.get(2).type);
        assertEquals("55kg", blocks.get(1).spans.get(2).text);

        assertEquals(MarkdownParser.BlockType.NUMBERED, blocks.get(2).type);
        assertEquals("1. ", blocks.get(2).prefix);

        assertEquals(MarkdownParser.BlockType.DIVIDER, blocks.get(3).type);
        assertEquals(MarkdownParser.BlockType.PARAGRAPH, blocks.get(4).type);
    }

    @Test
    public void handlesUnclosedMarkersAndEmptyInputSafely() {
        assertTrue(MarkdownParser.parse("").isEmpty());
        assertTrue(MarkdownParser.parse(null).isEmpty());

        List<MarkdownParser.Block> blocks = MarkdownParser.parse("未闭合 **加粗 与 `代码");
        assertEquals(1, blocks.size());
        assertEquals(1, blocks.get(0).spans.size());
        assertEquals(MarkdownParser.SpanType.PLAIN, blocks.get(0).spans.get(0).type);
        assertEquals("未闭合 **加粗 与 `代码", blocks.get(0).spans.get(0).text);
    }
}
