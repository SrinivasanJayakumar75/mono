package com.monotron.support.dashboard.dto;

import java.util.List;

/**
 * The aggregated numbers shown on the dashboard.
 *
 * @param openConversations      conversations currently open
 * @param closedConversations    conversations already resolved
 * @param unreadMessages         messages waiting for an agent
 * @param aiHandledConversations conversations answered by the AI
 * @param aiResolutionRate       share of conversations resolved by the AI (0..1)
 * @param totalContacts          distinct visitors
 * @param documentCount          PDFs in the knowledge base
 * @param articleCount           written articles
 * @param chunkCount             embedded chunks available for retrieval
 * @param conversationsByDay     new conversations per day
 * @param messagesByDay          new messages per day
 * @param topDocuments           the documents with the most chunks
 */
public record DashboardDto(
        long openConversations,
        long closedConversations,
        long unreadMessages,
        long aiHandledConversations,
        double aiResolutionRate,
        long totalContacts,
        long documentCount,
        long articleCount,
        long chunkCount,
        List<DayCountDto> conversationsByDay,
        List<DayCountDto> messagesByDay,
        List<TopDocumentDto> topDocuments) {

    /**
     * A single day of activity.
     */
    public record DayCountDto(String date, long count) {
    }

    /**
     * A document and the size of its contribution to the vector index.
     */
    public record TopDocumentDto(String id, String title, int chunkCount) {
    }
}
