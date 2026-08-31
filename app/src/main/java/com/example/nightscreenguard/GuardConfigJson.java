package com.example.nightscreenguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.text.Typography;

/* loaded from: classes2.dex */
public final class GuardConfigJson {
    private GuardConfigJson() {
    }

    public static GuardConfig parse(String json) {
        if (json != null) {
            Object value = new Reader(json).read();
            if (!(value instanceof Map)) {
                throw error("根对象无效");
            }
            Map<?, ?> root = (Map) value;
            int version = integer(root, "version");
            if (version != 1) {
                throw error("version 无效");
            }
            boolean enabled = bool(root, "enabled");
            Map<?, ?> window = object(root, "monitorWindow");
            int start = GuardConfig.parseClock(string(window, "start"));
            int end = GuardConfig.parseClock(string(window, "end"));
            List<Integer> points = clocks(array(root, "reminderPoints"));
            int strongStart = GuardConfig.parseClock(string(root, "strongReminderStart"));
            int normal = positive(integer(root, "normalIntervalMinutes"));
            List<Integer> strong = positives(array(root, "strongIntervalsMinutes"));
            int cooldown = integer(root, "cooldownSeconds");
            return new GuardConfig(enabled, start, end, points, strongStart, normal, strong, cooldown);
        }
        throw error("JSON 不能为空");
    }

    public static String stringify(GuardConfig c) {
        if (c == null) {
            throw error("配置不能为空");
        }
        return "{\"version\":1,\"enabled\":" + c.enabled + ",\"monitorWindow\":{\"start\":\"" + GuardConfig.formatClock(c.monitorStartMinute) + "\",\"end\":\"" + GuardConfig.formatClock(c.monitorEndMinute) + "\"},\"reminderPoints\":" + clocksJson(c.reminderPoints) + ",\"strongReminderStart\":\"" + GuardConfig.formatClock(c.strongStartMinute) + "\",\"normalIntervalMinutes\":" + c.normalIntervalMinutes + ",\"strongIntervalsMinutes\":" + intsJson(c.strongIntervalsMinutes) + ",\"cooldownSeconds\":" + c.cooldownSeconds + "}";
    }

    private static List<Integer> clocks(List<Object> values) {
        if (values.isEmpty()) {
            throw error("提醒时间点不能为空");
        }
        List<Integer> result = new ArrayList<>();
        for (Object v : values) {
            if (!(v instanceof String)) {
                throw error("时间类型无效");
            }
            result.add(Integer.valueOf(GuardConfig.parseClock((String) v)));
        }
        return result;
    }

    private static List<Integer> positives(List<Object> values) {
        if (values.isEmpty()) {
            throw error("间隔不能为空");
        }
        List<Integer> result = new ArrayList<>();
        for (Object v : values) {
            if (!(v instanceof Integer)) {
                throw error("间隔类型无效");
            }
            result.add(Integer.valueOf(positive(((Integer) v).intValue())));
        }
        return result;
    }

    private static int positive(int v) {
        if (v > 0) {
            return v;
        }
        throw error("间隔必须为正数");
    }

    private static Object required(Map<?, ?> m, String k) {
        if (m.containsKey(k)) {
            return m.get(k);
        }
        throw error("缺少字段: " + k);
    }

    private static String string(Map<?, ?> m, String k) {
        Object v = required(m, k);
        if (v instanceof String) {
            return (String) v;
        }
        throw error("字段类型无效: " + k);
    }

    private static boolean bool(Map<?, ?> m, String k) {
        Object v = required(m, k);
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        throw error("字段类型无效: " + k);
    }

    private static int integer(Map<?, ?> m, String k) {
        Object v = required(m, k);
        if (v instanceof Integer) {
            return ((Integer) v).intValue();
        }
        throw error("字段类型无效: " + k);
    }

    private static Map<?, ?> object(Map<?, ?> m, String k) {
        Object v = required(m, k);
        if (v instanceof Map) {
            return (Map) v;
        }
        throw error("字段类型无效: " + k);
    }

    private static List<Object> array(Map<?, ?> m, String k) {
        Object v = required(m, k);
        if (v instanceof List) {
            return (List) v;
        }
        throw error("字段类型无效: " + k);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static IllegalArgumentException error(String s) {
        return new IllegalArgumentException(s);
    }

    private static String clocksJson(List<Integer> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(Typography.quote).append(GuardConfig.formatClock(xs.get(i).intValue())).append(Typography.quote);
        }
        return b.append(']').toString();
    }

    private static String intsJson(List<Integer> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(xs.get(i));
        }
        return b.append(']').toString();
    }

    private static final class Reader {
        private int p;
        private final String s;

        Reader(String s) {
            this.s = s;
        }

        Object read() {
            skip();
            Object v = value();
            skip();
            if (this.p == this.s.length()) {
                return v;
            }
            throw GuardConfigJson.error("JSON 尾部无效");
        }

        Object value() {
            boolean z;
            skip();
            if (this.p >= this.s.length()) {
                throw GuardConfigJson.error("JSON 不完整");
            }
            char c = this.s.charAt(this.p);
            if (c == '{') {
                return obj();
            }
            if (c == '[') {
                return arr();
            }
            if (c == '"') {
                return str();
            }
            if (this.s.startsWith("true", this.p)) {
                this.p += 4;
                z = true;
            } else {
                if (!this.s.startsWith("false", this.p)) {
                    return num();
                }
                this.p += 5;
                z = false;
            }
            return Boolean.valueOf(z);
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            this.p++;
            skip();
            if (take('}')) {
                return m;
            }
            do {
                skip();
                if (this.p >= this.s.length() || this.s.charAt(this.p) != '"') {
                    throw GuardConfigJson.error("对象键无效");
                }
                String k = str();
                skip();
                if (!take(':')) {
                    throw GuardConfigJson.error("缺少冒号");
                }
                m.put(k, value());
                skip();
                if (take('}')) {
                    return m;
                }
            } while (take(','));
            throw GuardConfigJson.error("缺少逗号");
        }

        List<Object> arr() {
            List<Object> a = new ArrayList<>();
            this.p++;
            skip();
            if (take(']')) {
                return a;
            }
            do {
                a.add(value());
                skip();
                if (take(']')) {
                    return a;
                }
            } while (take(','));
            throw GuardConfigJson.error("缺少逗号");
        }

        String str() {
            char c;
            if (!take(Typography.quote)) {
                throw GuardConfigJson.error("字符串无效");
            }
            StringBuilder b = new StringBuilder();
            while (this.p < this.s.length()) {
                String str = this.s;
                int i = this.p;
                this.p = i + 1;
                char c2 = str.charAt(i);
                if (c2 == '"') {
                    return b.toString();
                }
                if (c2 != '\\') {
                    b.append(c2);
                } else {
                    if (this.p >= this.s.length()) {
                        throw GuardConfigJson.error("转义无效");
                    }
                    String str2 = this.s;
                    int i2 = this.p;
                    this.p = i2 + 1;
                    char e = str2.charAt(i2);
                    if (e == '"' || e == '\\' || e == '/') {
                        b.append(e);
                    } else {
                        if (e == 'n') {
                            c = '\n';
                        } else if (e == 'r') {
                            c = '\r';
                        } else {
                            if (e != 't') {
                                throw GuardConfigJson.error("转义无效");
                            }
                            c = '\t';
                        }
                        b.append(c);
                    }
                }
            }
            throw GuardConfigJson.error("字符串不完整");
        }

        Integer num() {
            int st = this.p;
            if (this.p < this.s.length() && this.s.charAt(this.p) == '-') {
                this.p++;
            }
            while (this.p < this.s.length() && Character.isDigit(this.s.charAt(this.p))) {
                this.p++;
            }
            if (st == this.p) {
                throw GuardConfigJson.error("值无效");
            }
            try {
                return Integer.valueOf(this.s.substring(st, this.p));
            } catch (NumberFormatException e) {
                throw GuardConfigJson.error("整数无效");
            }
        }

        void skip() {
            while (this.p < this.s.length() && Character.isWhitespace(this.s.charAt(this.p))) {
                this.p++;
            }
        }

        boolean take(char c) {
            if (this.p >= this.s.length() || this.s.charAt(this.p) != c) {
                return false;
            }
            this.p++;
            return true;
        }
    }
}
