package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.ai.dto.CitationDto;
import com.monotron.support.ai.dto.SearchHitDto;
import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Retrieval augmented generation over the tenant's knowledge base.
 *
 * <p>Retrieval is a cosine similarity search over {@code content_chunks.embedding}
 * backed by an HNSW index. The tenant restriction is not applied in the query:
 * it is enforced by the Row Level Security policies of the database, which is
 * what makes it impossible for one customer to retrieve another customer's
 * documents.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a friendly, concise customer support assistant.
            Answer using the knowledge base excerpts you are given.
            If the excerpts do not answer the question, say so honestly and offer to hand the \
            conversation over to a human teammate. Never invent policies, prices or dates.
            Keep answers short and friendly.""";

    private static final int SNIPPET_LENGTH = 320;

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    private final LlmService llm;
    private final AppProperties properties;

    public RagService(JdbcTemplate jdbc, EmbeddingService embeddings, LlmService llm, AppProperties properties) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.llm = llm;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Retrieval
    // ------------------------------------------------------------------

    /**
     * Semantic search over the tenant's knowledge base.
     */
    public List<SearchHitDto> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int effectiveLimit = Math.max(1, Math.min(limit <= 0 ? properties.getAi().getRetrieval().getTopK() : limit, 50));
        String literal = VectorCodec.toLiteral(embeddings.embed(query));

        List<SearchHitDto> hits = jdbc.query("""
                select c.source_type,
                       c.source_id,
                       c.title,
                       c.url,
                       c.content,
                       c.chunk_index,
                       1 - (c.embedding <=> ?::vector) as score
                from content_chunks c
                where c.embedding is not null
                order by c.embedding <=> ?::vector
                limit ?
                """, (rs, i) -> new SearchHitDto(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("content"),
                rs.getDouble("score"),
                rs.getInt("chunk_index")), literal, literal, effectiveLimit);

        double minScore = properties.getAi().getRetrieval().getMinScore();
        List<SearchHitDto> semantic = hits.stream().filter(hit -> hit.score() >= minScore).toList();
        List<SearchHitDto> lexical = lexicalSearch(query, Math.min(200, Math.max(20, effectiveLimit * 4)));

        // Merge both signals instead of letting one weak vector hit suppress a
        // stronger exact term match. Semantic relevance remains the main signal;
        // lexical relevance helps local/offline embeddings match PDF wording.
        Map<String, SearchHitDto> candidates = new LinkedHashMap<>();
        Map<String, Double> semanticScores = new LinkedHashMap<>();
        for (SearchHitDto hit : semantic) {
            String key = keyOf(hit);
            candidates.put(key, hit);
            semanticScores.put(key, hit.score());
        }

        int queryTermCount = lexicalTermCount(query);
        double minLexicalScore = queryTermCount <= 2 ? 0.5 : 0.34;
        Map<String, Double> lexicalScores = new LinkedHashMap<>();
        for (SearchHitDto hit : lexical) {
            if (hit.score() < minLexicalScore) {
                continue;
            }
            String key = keyOf(hit);
            candidates.putIfAbsent(key, hit);
            lexicalScores.put(key, hit.score());
        }

        List<SearchHitDto> ranked = new ArrayList<>();
        for (Map.Entry<String, SearchHitDto> entry : candidates.entrySet()) {
            String key = entry.getKey();
            SearchHitDto hit = entry.getValue();
            Double vectorScore = semanticScores.get(key);
            Double keywordScore = lexicalScores.get(key);
            double score = vectorScore != null && keywordScore != null
                    ? 0.55 * vectorScore + 0.45 * keywordScore
                    : vectorScore != null ? vectorScore : 0.9 * keywordScore;
            ranked.add(new SearchHitDto(hit.sourceType(), hit.sourceId(), hit.title(), hit.url(), hit.content(),
                    score, hit.chunkIndex()));
        }
        ranked.sort((left, right) -> Double.compare(right.score(), left.score()));
        return ranked.size() > effectiveLimit ? ranked.subList(0, effectiveLimit) : ranked;
    }

    /**
     * Keyword fallback used when no chunk clears the cosine threshold.
     *
     * <p>Terms are matched independently (OR) and the score is the share of the
     * query terms the chunk contains, so the result is still in {@code [0, 1]}
     * and can be presented to clients just like a cosine score.
     */
    private List<SearchHitDto> lexicalSearch(String query, int limit) {
        List<String> terms = new ArrayList<>();
        for (String raw : query.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 3 && !LocalEmbeddingService.stopWords().contains(raw) && !terms.contains(raw)) {
                terms.add(raw);
            }
        }
        if (terms.isEmpty()) {
            return List.of();
        }
        if (terms.size() > 8) {
            terms = terms.subList(0, 8);
        }

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (String term : terms) {
            if (!where.isEmpty()) {
                where.append(" or ");
            }
            where.append("lower(coalesce(c.title, '') || ' ' || c.content) like ?");
            args.add("%" + term + "%");
        }

        List<SearchHitDto> candidates = jdbc.query("""
                select c.source_type,
                       c.source_id,
                       c.title,
                       c.url,
                       c.content,
                       c.chunk_index
                from content_chunks c
                where c.embedding is not null and (""" + where + """
                )
                limit 200
                """, (rs, i) -> new SearchHitDto(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("content"),
                0d,
                rs.getInt("chunk_index")), args.toArray());

        List<SearchHitDto> scored = new ArrayList<>();
        for (SearchHitDto hit : candidates) {
            String haystack = ((hit.title() == null ? "" : hit.title()) + " " + hit.content())
                    .toLowerCase(java.util.Locale.ROOT);
            int matched = 0;
            for (String term : terms) {
                if (haystack.contains(term)) {
                    matched++;
                }
            }
            scored.add(new SearchHitDto(hit.sourceType(), hit.sourceId(), hit.title(), hit.url(), hit.content(),
                    (double) matched / terms.size(), hit.chunkIndex()));
        }
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));

        if (!scored.isEmpty()) {
            log.debug("Semantic search found nothing for '{}'; keyword fallback returned {} chunk(s)",
                    query, Math.min(scored.size(), limit));
        }
        return scored.size() > limit ? scored.subList(0, limit) : scored;
    }

    private static String keyOf(SearchHitDto hit) {
        return hit.sourceType() + "|" + hit.sourceId() + "|" + hit.chunkIndex();
    }

    private static int lexicalTermCount(String query) {
        int count = 0;
        for (String raw : query.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 3 && !LocalEmbeddingService.stopWords().contains(raw)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Answering
    // ------------------------------------------------------------------
    /**
     * Answers a customer question from the knowledge base.
     *
     * @param question      the customer's question
     * @param systemPrompt  optional override for the assistant persona
     * @param temperature   sampling temperature for the model
     */
    public AnswerDto answer(String question, String systemPrompt, double temperature) {
        if (isSmallTalk(question)) {
            return new AnswerDto(
                    smallTalkReply(question),
                    "small-talk",
                    "local",
                    List.of());
        }

        List<SearchHitDto> hits = search(question, properties.getAi().getRetrieval().getTopK());
        List<LlmService.ContextSnippet> snippets = hits.stream()
                .map(hit -> new LlmService.ContextSnippet(
                        hit.title() == null ? "Knowledge base" : hit.title(),
                        hit.content(),
                        hit.score()))
                .toList();

        LlmService.ChatRequest request = new LlmService.ChatRequest(
                systemPrompt == null || systemPrompt.isBlank() ? DEFAULT_SYSTEM_PROMPT : systemPrompt,
                question,
                snippets,
                temperature);

        LlmService.ChatResult result = llm.complete(request);

        List<CitationDto> citations = new ArrayList<>();
        for (SearchHitDto hit : hits) {
            citations.add(new CitationDto(
                    hit.title() == null ? "Knowledge base" : hit.title(),
                    hit.sourceType(),
                    hit.sourceId(),
                    hit.url(),
                    round(hit.score()),
                    snippet(hit.content())));
        }

        return new AnswerDto(result.content(), result.model(), result.provider(), citations);
    }

    private static boolean isSmallTalk(String question) {
        if (question == null) {
            return false;
        }
        String normalised = question.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return switch (normalised) {
            case "hi", "hi there", "hello", "hello there", "hey", "hey there", "good morning",
                    "good afternoon", "good evening", "thanks", "thank you", "bye", "goodbye" -> true;
            default -> false;
        };
    }

    private static String smallTalkReply(String question) {
        String normalised = question.toLowerCase(java.util.Locale.ROOT).trim();
        if (normalised.startsWith("thank")) {
            return "You’re welcome! Is there anything else I can help you with?";
        }
        if (normalised.equals("bye") || normalised.equals("goodbye")) {
            return "Goodbye! Feel free to message us if you need anything else.";
        }
        return "Hi! How can I help you today?";
    }

    public String defaultSystemPrompt() {
        return DEFAULT_SYSTEM_PROMPT;
    }

    public String embeddingProvider() {
        return embeddings.provider();
    }

    public Optional<SearchHitDto> bestMatch(String question) {
        List<SearchHitDto> hits = search(question, 1);
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    // ------------------------------------------------------------------

    private static String snippet(String content) {
        if (content == null) {
            return null;
        }
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= SNIPPET_LENGTH ? cleaned : cleaned.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
