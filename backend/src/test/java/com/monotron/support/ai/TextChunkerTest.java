package com.monotron.support.ai;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    @DisplayName("blank input produces no chunks")
    void blankInput() {
        assertThat(TextChunker.split(null, 100, 10)).isEmpty();
        assertThat(TextChunker.split("   \n\t ", 100, 10)).isEmpty();
    }

    @Test
    @DisplayName("short text is returned as a single chunk")
    void shortText() {
        List<String> chunks = TextChunker.split("Reset your password from the settings page.", 200, 20);
        assertThat(chunks).containsExactly("Reset your password from the settings page.");
    }

    @Test
    @DisplayName("long text is split without producing oversized chunks")
    void longTextIsSplit() {
        String paragraph = "Open account settings to update your contact information. "
                + "Contact support if you need help changing your profile. ";
        String text = paragraph.repeat(20);

        List<String> chunks = TextChunker.split(text, 300, 0);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(300));
    }

    @Test
    @DisplayName("consecutive chunks share an overlapping tail")
    void overlapIsApplied() {
        String text = "Alpha paragraph about account access. ".repeat(10) + "\n\n"
                + "Beta paragraph about shipping. ".repeat(10);

        List<String> chunks = TextChunker.split(text, 120, 30);

        assertThat(chunks).hasSizeGreaterThan(1);
        String firstTail = chunks.get(0).substring(chunks.get(0).length() - 10);
        assertThat(chunks.get(1)).contains(firstTail.trim().split("\\s+")[0]);
    }

    @Test
    @DisplayName("normalise collapses whitespace and joins hyphenated line breaks")
    void normalise() {
        assertThat(TextChunker.normalise("hello\r\nworld")).isEqualTo("hello\nworld");
        assertThat(TextChunker.normalise("re-\nset")).isEqualTo("reset");
        assertThat(TextChunker.normalise("a   b")).isEqualTo("a b");
        assertThat(TextChunker.normalise("a\n\n\n\nb")).isEqualTo("a\n\nb");
    }
}
