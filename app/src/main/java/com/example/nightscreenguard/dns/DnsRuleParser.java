package com.example.nightscreenguard.dns;

import java.util.ArrayList;
import java.util.List;

/**
 * 域名规则文本解析（纯 Java，可单测）。
 *
 * 支持的规则行：
 *  - 空行 / 以 # 或 // 开头：注释，忽略
 *  - baidu.com       精确
 *  - *.baidu.com     子域通配
 *  - ||baidu.com^    adblock（含自身与子域）
 *  - baidu.com/xxx   带路径（当前按域名部分处理，路径段预留）
 */
public final class DnsRuleParser {

    private DnsRuleParser() {
    }

    /** 解析规则文本（每行一条），返回规则字符串列表；非法/空/注释行被跳过。 */
    public static List<String> parse(String text) {
        List<String> rules = new ArrayList<>();
        if (text == null) {
            return rules;
        }
        for (String line : text.split("\\r?\\n")) {
            String rule = line.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (rule.startsWith("#") || rule.startsWith("//")) {
                continue;
            }
            // 去掉行内注释（以空格 # 分隔）
            int hashIdx = rule.indexOf(" #");
            if (hashIdx >= 0) {
                rule = rule.substring(0, hashIdx).trim();
            }
            if (rule.isEmpty()) {
                continue;
            }
            rules.add(rule);
        }
        return rules;
    }

    /** 解析并直接灌入 matcher。返回实际添加的规则数。 */
    public static int parseInto(DomainMatcher matcher, String text) {
        List<String> rules = parse(text);
        matcher.addRules(rules);
        return rules.size();
    }
}
