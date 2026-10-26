package com.monotron.support.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.monotron.support.common.ForbiddenException;
import com.monotron.support.dashboard.dto.DashboardDto;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated statistics for the tenant dashboard.
 *
 * <p>Every query runs against Row Level Security protected tables, so a
 * workspace can only ever see its own numbers.
 */
@Service
public class DashboardService {

    private static final int TREND_DAYS = 30;
    private static final int TOP_DOCUMENTS = 5;

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardDto summary(AuthPrincipal principal) {
        requireWorkspace(principal);

        List<Counters> counters = jdbc.query("""
                select
                  count(*) filter (where status = 'OPEN') as open_count,
                  count(*) filter (where status = 'CLOSED') as closed_count,
                  coalesce(sum(unread_count), 0) as unread_total,
                  count(*) filter (where ai_handled) as ai_count,
                  count(*) as total_count
                from conversations
                """, (rs, i) -> new Counters(
                rs.getLong("open_count"),
                rs.getLong("closed_count"),
                rs.getLong("unread_total"),
                rs.getLong("ai_count"),
                rs.getLong("total_count")));

        Counters stats = counters.isEmpty() ? new Counters(0, 0, 0, 0, 0) : counters.get(0);

        long totalContacts = scalar("select count(*) from contacts");
        long documentCount = scalar("select count(*) from documents");
        long articleCount = scalar("select count(*) from articles");
        long chunkCount = scalar("select count(*) from content_chunks");

        double resolutionRate = stats.total() == 0
                ? 0d
                : Math.round((double) stats.aiHandled() / stats.total() * 10000d) / 10000d;

        return new DashboardDto(
                stats.open(),
                stats.closed(),
                stats.unread(),
                stats.aiHandled(),
                resolutionRate,
                totalContacts,
                documentCount,
                articleCount,
                chunkCount,
                conversationsByDay(),
                messagesByDay(),
                topDocuments());
    }

    // ------------------------------------------------------------------
    // Series
    // ------------------------------------------------------------------

    private List<DashboardDto.DayCountDto> conversationsByDay() {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query("""
                select (created_at at time zone 'utc')::date as day, count(*) as total
                from conversations
                where created_at >= now() - (? || ' days')::interval
                group by 1
                """, (RowCallbackHandler) rs -> {
            counts.put(rs.getObject("day", LocalDate.class), rs.getLong("total"));
        }, TREND_DAYS);

        return fillDays(counts);
    }

    private List<DashboardDto.DayCountDto> messagesByDay() {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query("""
                select (m.created_at at time zone 'utc')::date as day, count(*) as total
                from messages m
                join conversations c on c.id = m.conversation_id
                where m.created_at >= now() - (? || ' days')::interval
                group by 1
                """, (RowCallbackHandler) rs -> {
            counts.put(rs.getObject("day", LocalDate.class), rs.getLong("total"));
        }, TREND_DAYS);

        return fillDays(counts);
    }

    /**
     * Returns one entry per day of the window, including the quiet days, so
     * the chart keeps a continuous time axis.
     */
    private List<DashboardDto.DayCountDto> fillDays(Map<LocalDate, Long> counts) {
        LocalDate today = LocalDate.now(java.time.Clock.systemUTC());
        List<DashboardDto.DayCountDto> series = new ArrayList<>(TREND_DAYS);
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            series.add(new DashboardDto.DayCountDto(day.toString(), counts.getOrDefault(day, 0L)));
        }
        return series;
    }

    private List<DashboardDto.TopDocumentDto> topDocuments() {
        return jdbc.query("""
                select id, title, coalesce(chunk_count, 0) as chunk_count
                from documents
                order by chunk_count desc nulls last, created_at desc
                limit ?
                """, (rs, i) -> new DashboardDto.TopDocumentDto(
                rs.getString("id"),
                rs.getString("title"),
                rs.getInt("chunk_count")), TOP_DOCUMENTS);
    }

    // ------------------------------------------------------------------

    private long scalar(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private void requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
    }

    private record Counters(long open, long closed, long unread, long aiHandled, long total) {
    }
}
