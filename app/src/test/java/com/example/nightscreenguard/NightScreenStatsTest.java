package com.example.nightscreenguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Test;

public class NightScreenStatsTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void nightKeyHandlesCrossMidnightWindow() throws Exception {
        assertEquals("2024-05-10", NightScreenStats.nightKey(millis("2024-05-10 23:00"),
                22 * 60 + 30, 7 * 60, UTC));
        assertEquals("2024-05-10", NightScreenStats.nightKey(millis("2024-05-11 01:00"),
                22 * 60 + 30, 7 * 60, UTC));
        assertNull(NightScreenStats.nightKey(millis("2024-05-11 12:00"),
                22 * 60 + 30, 7 * 60, UTC));
    }

    @Test
    public void nightKeyHandlesBoundaries() throws Exception {
        assertEquals("2024-05-10", NightScreenStats.nightKey(millis("2024-05-11 06:59"),
                22 * 60, 7 * 60, UTC));
        assertNull(NightScreenStats.nightKey(millis("2024-05-11 07:00"),
                22 * 60, 7 * 60, UTC));
        assertEquals("2024-05-10", NightScreenStats.nightKey(millis("2024-05-10 08:00"),
                8 * 60, 22 * 60, UTC));
        assertNull(NightScreenStats.nightKey(millis("2024-05-10 22:00"),
                8 * 60, 22 * 60, UTC));
    }

    @Test
    public void countInteractiveUsesLeftClosedRightOpenInterval() {
        List<Long> events = Arrays.asList(null, 99L, 100L, 150L, 200L, null);
        assertEquals(2, NightScreenStats.countInteractive(events, 100L, 200L));
        assertEquals(0, NightScreenStats.countInteractive(Arrays.<Long>asList(), 100L, 200L));
    }

    @Test
    public void formatSummarySortsDatesAndHandlesEmptyData() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        counts.put("2024-05-11", 1);
        counts.put("2024-05-09", 3);
        assertEquals("2024-05-09：3 次\n2024-05-11：1 次",
                NightScreenStats.formatSummary(counts));
        assertEquals("暂无统计数据", NightScreenStats.formatSummary(
                new LinkedHashMap<String, Integer>()));
    }

    @Test
    public void mergeCountsUsesFallbackOnlyWhenItIsLarger() {
        Map<String, Integer> primary = new LinkedHashMap<String, Integer>();
        primary.put("2024-05-10", 4);
        primary.put("2024-05-11", 0);
        Map<String, Integer> fallback = new LinkedHashMap<String, Integer>();
        fallback.put("2024-05-10", 2);
        fallback.put("2024-05-11", 3);
        fallback.put("2024-05-12", 1);

        Map<String, Integer> merged = NightScreenStats.mergeCounts(primary, fallback);
        assertEquals(Integer.valueOf(4), merged.get("2024-05-10"));
        assertEquals(Integer.valueOf(3), merged.get("2024-05-11"));
        assertEquals(Integer.valueOf(1), merged.get("2024-05-12"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidWindow() {
        NightScreenStats.nightKey(0L, 0, 0, UTC);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidInterval() {
        NightScreenStats.countInteractive(Arrays.asList(1L), 2L, 1L);
    }

    private static long millis(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        format.setTimeZone(UTC);
        Date date = format.parse(value);
        return date.getTime();
    }
}
