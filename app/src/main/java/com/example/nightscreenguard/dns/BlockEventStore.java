package com.example.nightscreenguard.dns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 域名拦截事件记录（纯 Java 核心，可单测；Android 侧负责持久化到 SharedPreferences）。
 *
 * 记录每次拦截：被拦域名、命中时间戳、累计次数；按域名聚合，并可按日期汇总。
 */
public final class BlockEventStore {

    public static final class BlockEvent {
        public final String domain;
        public final long timestampMillis;
        public final int count;

        BlockEvent(String domain, long timestampMillis, int count) {
            this.domain = domain;
            this.timestampMillis = timestampMillis;
            this.count = count;
        }
    }

    private final List<BlockEvent> events = new ArrayList<>();
    private final Map<String, Integer> totals = new LinkedHashMap<>();

    /** 记录一次拦截。返回该域名累计拦截次数。 */
    public synchronized int recordBlock(String domain, long timestampMillis) {
        if (domain == null || domain.isEmpty()) {
            return 0;
        }
        Integer total = totals.get(domain);
        int next = (total == null ? 0 : total) + 1;
        totals.put(domain, next);
        events.add(new BlockEvent(domain, timestampMillis, next));
        return next;
    }

    /** 读取某时间范围内的拦截事件（按记录顺序）。 */
    public synchronized List<BlockEvent> eventsIn(long fromMillis, long toMillis) {
        List<BlockEvent> result = new ArrayList<>();
        for (BlockEvent e : events) {
            if (e.timestampMillis >= fromMillis && e.timestampMillis < toMillis) {
                result.add(e);
            }
        }
        return result;
    }

    /** 全部事件数。 */
    public synchronized int totalEvents() {
        return events.size();
    }

    /** 按域名累计拦截次数（有序）。 */
    public synchronized Map<String, Integer> totals() {
        return new LinkedHashMap<>(totals);
    }

    /** 指定时间范围内的拦截总次数。 */
    public synchronized int countIn(long fromMillis, long toMillis) {
        int c = 0;
        for (BlockEvent e : events) {
            if (e.timestampMillis >= fromMillis && e.timestampMillis < toMillis) {
                c += 1;
            }
        }
        return c;
    }
}
