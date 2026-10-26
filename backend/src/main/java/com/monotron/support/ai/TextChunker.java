package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long text into overlapping, retrieval friendly chunks.
 *
 * <p>The splitter works top down: paragraphs first, then sentences, then words.
 * That keeps chunks aligned with the structure of the source document, which
 * makes the retrieved excerpts far more readable than a fixed size split would.
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * @param text     the source text
     * @param maxChars soft limit for a chunk
     * @param overlap  number of characters shared between two consecutive chunks
     */
    public static List<String> split(String text, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalised = normalise(text);
        if (normalised.length() <= maxChars) {
            chunks.add(normalised);
            return chunks;
        }

        List<String> paragraphs = List.of(normalised.split("\\s{2,}"));
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String candidate = paragraph.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.length() > maxChars) {
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer.setLength(0);
                }
                chunks.addAll(splitLongParagraph(candidate, maxChars));
                continue;
            }
            if (buffer.length() + candidate.length() + 2 > maxChars && buffer.length() > 0) {
                chunks.add(buffer.toString());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(candidate);
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }

        return applyOverlap(chunks, Math.max(0, overlap));
    }

    private static List<String> splitLongParagraph(String paragraph, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > maxChars) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(splitByWords(trimmed, maxChars));
                continue;
            }
            if (current.length() + trimmed.length() + 1 > maxChars && current.length() > 0) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static List<String> splitByWords(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxChars && current.length() > 0) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * Prepends the tail of the previous chunk to the next one so that a
     * sentence spanning a chunk boundary is still retrievable from both sides.
     */
    private static List<String> applyOverlap(List<String> chunks, int overlap) {
        if (overlap <= 0 || chunks.size() < 2) {
            return chunks;
        }
        List<String> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            if (i == 0) {
                result.add(chunks.get(i));
                continue;
            }
            String previous = chunks.get(i - 1);
            String tail = previous.length() > overlap ? previous.substring(previous.length() - overlap) : previous;
            tail = tail.replaceFirst("^\\S+\\s+", "");
            result.add(tail.isBlank() ? chunks.get(i) : tail + " " + chunks.get(i));
        }
        return result;
    }

    /** Collapses exotic whitespace and removes soft hyphen artefacts. */
    public static String normalise(String text) {
        return text
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("-\n", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
