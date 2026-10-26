package com.monotron.support.ai;

import java.util.List;

/**
 * Produces the natural language answer for a customer question.
 */
public interface LlmService {

    ChatResult complete(ChatRequest request);

    /** Identifier of the backing provider, e.g. {@code openai} or {@code local}. */
    String provider();

    /** Name of the model in use. */
    String model();

    /**
     * A grounded question.
     *
     * @param systemPrompt persona and policy for the assistant
     * @param userPrompt   the customer's question
     * @param context      knowledge base passages retrieved for this question
     * @param temperature  sampling temperature
     */
    record ChatRequest(
            String systemPrompt,
            String userPrompt,
            List<ContextSnippet> context,
            double temperature) {
    }

    /**
     * A retrieved knowledge base passage.
     *
     * @param title   document or article title
     * @param content passage text
     * @param score   similarity between the question and the passage
     */
    record ContextSnippet(String title, String content, double score) {
    }

    record ChatResult(String content, String provider, String model) {
    }
}
