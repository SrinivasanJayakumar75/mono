package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grounded answer composer that works without an external model.
 *
 * <p>The retrieved knowledge base passages are split into sentences and scored
 * against the customer's question with a lexical overlap measure. The best
 * matching sentences are then stitched into a readable answer together with the
 * titles they came from.
 *
 * <p>This is deliberately conservative: it never invents facts, it only reports
 * what the knowledge base actually contains, and it says so when nothing
 * relevant was found.
 */
public class LocalLlmService implements LlmService {

    private static final int MAX_SENTENCES = 4;
    private static final double MIN_SENTENCE_SCORE = 0.25;

    @Override
    public ChatResult complete(ChatRequest request) {
        List<ContextSnippet> context = request.context() == null ? List.of() : request.context();
        if (context.isEmpty()) {
            return new ChatResult(
                    "I could not find anything about that in the knowledge base yet. "
                            + "I have flagged this for a human teammate who will get back to you shortly.",
                    provider(), model());
        }

        Set<String> questionTokens = significantTokens(request.userPrompt());
        Map<String, ScoredSentence> best = new LinkedHashMap<>();

        for (ContextSnippet snippet : context) {
            for (String sentence : sentences(snippet.content())) {
                double score = similarity(questionTokens, significantTokens(sentence), request.userPrompt(), sentence);
                if (score <= 0) {
                    continue;
                }
                String key = snippet.title() + "::" + sentence;
                ScoredSentence current = best.get(key);
                if (current == null || current.score() < score) {
                    best.put(key, new ScoredSentence(snippet.title(), sentence, score));
                }
            }
        }

        List<ScoredSentence> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble(ScoredSentence::score).reversed());

        List<ScoredSentence> selected = new ArrayList<>();
        for (ScoredSentence candidate : ranked) {
            if (selected.size() >= MAX_SENTENCES) {
                break;
            }
            if (candidate.score() < MIN_SENTENCE_SCORE) {
                break;
            }
            selected.add(candidate);
        }

        if (selected.isEmpty()) {
            return new ChatResult(
                    "I do not have a confident answer for that in the knowledge base yet. "
                            + "Let me pass this to a human teammate so you get an accurate response.",
                    provider(), model());
        }

        // Group by source so the answer reads coherently.
        Map<String, List<String>> byTitle = new LinkedHashMap<>();
        for (ScoredSentence sentence : selected) {
            byTitle.computeIfAbsent(sentence.title(), key -> new ArrayList<>()).add(sentence.text());
        }

        StringBuilder answer = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : byTitle.entrySet()) {
            answer.append(entry.getKey()).append(": ");
            answer.append(String.join(" ", entry.getValue()));
            answer.append('\n');
        }
        return new ChatResult(answer.toString().trim(), provider(), model());
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String model() {
        return "grounded-extractive";
    }

    // ------------------------------------------------------------------

    private static Set<String> significantTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : LocalEmbeddingService.tokenize(text == null ? "" : text)) {
            if (!LocalEmbeddingService.stopWords().contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double similarity(Set<String> questionTokens, Set<String> sentenceTokens, String question,
            String sentence) {
        if (questionTokens.isEmpty() || sentenceTokens.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String token : questionTokens) {
            if (sentenceTokens.contains(token)) {
                hits++;
            }
        }
        if (questionTokens.size() >= 4 && hits < 2) {
            return 0;
        }
        double coverage = (double) hits / questionTokens.size();

        // Reward passages that also share a phrase with the question.
        String lowerQuestion = (question == null ? "" : question).toLowerCase(Locale.ROOT);
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);
        double phraseBonus = 0;
        for (String token : questionTokens) {
            if (token.length() >= 5 && lowerQuestion.contains(" " + token + " ")
                    && lowerSentence.contains(" " + token + " ")) {
                phraseBonus += 0.08;
            }
        }
        return coverage + phraseBonus;
    }

    private static List<String> sentences(String content) {
        List<String> sentences = new ArrayList<>();
        if (content == null) {
            return sentences;
        }
        String normalised = content.replaceAll("\\s+", " ").trim();
        for (String part : normalised.split("(?<=[.!?])\\s+")) {
            String cleaned = part.trim();
            if (cleaned.length() >= 25) {
                sentences.add(cleaned);
            }
        }
        if (sentences.isEmpty() && normalised.length() >= 25) {
            sentences.add(normalised);
        }
        return sentences;
    }

    private record ScoredSentence(String title, String text, double score) {
    }
}
