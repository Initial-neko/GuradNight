package com.example.nightscreenguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Small dependency-free JSON codec for the versioned guard configuration. */
public final class GuardConfigJson {
    private GuardConfigJson() { }

    public static GuardConfig parse(String json) {
        if (json == null) throw error("JSON 不能为空");
        Object value = new Reader(json).read();
        if (!(value instanceof Map)) throw error("根对象无效");
        Map<?, ?> root = (Map<?, ?>) value;
        int version = integer(root, "version");
        if (version != 1) throw error("version 无效");
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

    public static String stringify(GuardConfig c) {
        if (c == null) throw error("配置不能为空");
        return "{\"version\":1,\"enabled\":" + c.enabled
                + ",\"monitorWindow\":{\"start\":\"" + GuardConfig.formatClock(c.monitorStartMinute)
                + "\",\"end\":\"" + GuardConfig.formatClock(c.monitorEndMinute)
                + "\"},\"reminderPoints\":" + clocksJson(c.reminderPoints)
                + ",\"strongReminderStart\":\"" + GuardConfig.formatClock(c.strongStartMinute)
                + "\",\"normalIntervalMinutes\":" + c.normalIntervalMinutes
                + ",\"strongIntervalsMinutes\":" + intsJson(c.strongIntervalsMinutes)
                + ",\"cooldownSeconds\":" + c.cooldownSeconds + "}";
    }

    private static List<Integer> clocks(List<Object> values) {
        if (values.isEmpty()) throw error("提醒时间点不能为空");
        List<Integer> result = new ArrayList<>();
        for (Object v : values) { if (!(v instanceof String)) throw error("时间类型无效"); result.add(GuardConfig.parseClock((String) v)); }
        return result;
    }
    private static List<Integer> positives(List<Object> values) {
        if (values.isEmpty()) throw error("间隔不能为空");
        List<Integer> result = new ArrayList<>();
        for (Object v : values) { if (!(v instanceof Integer)) throw error("间隔类型无效"); result.add(positive((Integer) v)); }
        return result;
    }
    private static int positive(int v) { if (v <= 0) throw error("间隔必须为正数"); return v; }
    private static Object required(Map<?, ?> m, String k) { if (!m.containsKey(k)) throw error("缺少字段: " + k); return m.get(k); }
    private static String string(Map<?, ?> m, String k) { Object v=required(m,k); if (!(v instanceof String)) throw error("字段类型无效: "+k); return (String)v; }
    private static boolean bool(Map<?, ?> m, String k) { Object v=required(m,k); if (!(v instanceof Boolean)) throw error("字段类型无效: "+k); return (Boolean)v; }
    private static int integer(Map<?, ?> m, String k) { Object v=required(m,k); if (!(v instanceof Integer)) throw error("字段类型无效: "+k); return (Integer)v; }
    private static Map<?, ?> object(Map<?, ?> m, String k) { Object v=required(m,k); if (!(v instanceof Map)) throw error("字段类型无效: "+k); return (Map<?, ?>)v; }
    @SuppressWarnings("unchecked") private static List<Object> array(Map<?, ?> m, String k) { Object v=required(m,k); if (!(v instanceof List)) throw error("字段类型无效: "+k); return (List<Object>)v; }
    private static IllegalArgumentException error(String s) { return new IllegalArgumentException(s); }
    private static String clocksJson(List<Integer> xs) { StringBuilder b=new StringBuilder("["); for(int i=0;i<xs.size();i++){if(i>0)b.append(',');b.append('"').append(GuardConfig.formatClock(xs.get(i))).append('"');} return b.append(']').toString(); }
    private static String intsJson(List<Integer> xs) { StringBuilder b=new StringBuilder("["); for(int i=0;i<xs.size();i++){if(i>0)b.append(',');b.append(xs.get(i));} return b.append(']').toString(); }

    private static final class Reader {
        private final String s; private int p;
        Reader(String s){this.s=s;}
        Object read(){skip(); Object v=value(); skip(); if(p!=s.length())throw error("JSON 尾部无效"); return v;}
        Object value(){skip(); if(p>=s.length())throw error("JSON 不完整"); char c=s.charAt(p); if(c=='{')return obj(); if(c=='[')return arr(); if(c=='"')return str(); if(s.startsWith("true",p)){p+=4;return true;} if(s.startsWith("false",p)){p+=5;return false;} return num();}
        Map<String,Object> obj(){Map<String,Object> m=new LinkedHashMap<>();p++;skip();if(take('}'))return m;while(true){skip();if(p>=s.length()||s.charAt(p)!='"')throw error("对象键无效");String k=str();skip();if(!take(':'))throw error("缺少冒号");m.put(k,value());skip();if(take('}'))return m;if(!take(','))throw error("缺少逗号");}}
        List<Object> arr(){List<Object> a=new ArrayList<>();p++;skip();if(take(']'))return a;while(true){a.add(value());skip();if(take(']'))return a;if(!take(','))throw error("缺少逗号");}}
        String str(){if(!take('"'))throw error("字符串无效");StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c=='\\'){if(p>=s.length())throw error("转义无效");char e=s.charAt(p++);if(e=='"'||e=='\\'||e=='/')b.append(e);else if(e=='n')b.append('\n');else if(e=='r')b.append('\r');else if(e=='t')b.append('\t');else throw error("转义无效");}else b.append(c);}throw error("字符串不完整");}
        Integer num(){int st=p;if(p<s.length()&&s.charAt(p)=='-')p++;while(p<s.length()&&Character.isDigit(s.charAt(p)))p++;if(st==p)throw error("值无效");try{return Integer.valueOf(s.substring(st,p));}catch(NumberFormatException e){throw error("整数无效");}}
        boolean take(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;} void skip(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
    }
}
