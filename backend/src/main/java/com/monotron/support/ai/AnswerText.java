package com.monotron.support.ai;

import java.util.List;
import java.util.regex.Pattern;

import com.monotron.support.ai.dto.CitationDto;

/**
 * Normalises AI answer text before it is stored or shown in a client.
 */
public final class AnswerText {

    /**
     * Older prompts asked the model to cite its sources, so many stored answers
     * end with text such as {@code (Source: Employee Handbook)}. The UI already
     * renders citations separately, so that annotation is noise.
     */
    private static final Pattern SOURCE_ANNOTATION =
            Pattern.compile("\\s*\\(Sources?:\\s*[^)]*\\)", Pattern.CASE_INSENSITIVE);

    private AnswerText() {
    }

    public static String stripSourceAnnotations(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SOURCE_ANNOTATION.matcher(text).replaceAll("").strip();
    }

    /**
     * Cleans older AI messages that were written by the local fallback, which
     * used to prefix an answer with the source title followed by a colon.
     */
    public static String forAiMessage(String text, List<CitationDto> citations) {
        String cleaned = stripSourceAnnotations(text);
        if (cleaned == null || cleaned.isBlank() || citations == null || citations.isEmpty()) {
            return cleaned;
        }
        for (CitationDto citation : citations) {
            String title = citation.title();
            if (title == null || title.isBlank()) {
                continue;
            }
            String prefix = title.strip() + ":";
            if (cleaned.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return cleaned.substring(prefix.length()).stripLeading();
            }
        }
        return cleaned;
    }
}
