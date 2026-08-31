package com.example.nightscreenguard.dns;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 纯通配符域名匹配器（无 Android 依赖，可在 JVM 直接单测）。
 *
 * 支持三种规则语义：
 *  - 精确：   baidu.com          —— 仅命中 baidu.com 本身
 *  - 子域：   *.baidu.com        —— 命中 baidu.com 的所有子域（不含 baidu.com 本身）
 *  - adblock：||baidu.com^       —— 命中 baidu.com 本身及其所有子域
 *
 * 匹配用「逐层去最左标签的后缀匹配」，单次匹配 O(标签数)，规则集增大不影响单次耗时。
 * 域名统一小写、去末尾点。
 */
public final class DomainMatcher {

    /** 精确命中集合：baidu.com */
    private final Set<String> exactSet = new HashSet<>();
    /** 子域命中集合：*.baidu.com -> 存 baidu.com */
    private final Set<String> subdomainSet = new HashSet<>();
    /** adblock 命中集合：||baidu.com^ -> 存 baidu.com（含自身与子域） */
    private final Set<String> adblockSet = new HashSet<>();

    private final boolean caseInsensitive;

    public DomainMatcher() {
        this(true);
    }

    public DomainMatcher(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * 添加一条规则。语法前缀决定语义：
     *  - "||domain^"  -> adblock（domain 及其子域）
     *  - "*.domain"   -> 子域
     *  - 否则         -> 精确
     */
    public void addRule(String rule) {
        if (rule == null) {
            return;
        }
        String r = normalize(rule.trim());
        if (r.isEmpty()) {
            return;
        }
        if (r.startsWith("||") && r.endsWith("^")) {
            String domain = normalize(r.substring(2, r.length() - 1));
            if (!domain.isEmpty()) {
                adblockSet.add(domain);
            }
        } else if (r.startsWith("*.")) {
            String domain = normalize(r.substring(2));
            if (!domain.isEmpty()) {
                subdomainSet.add(domain);
            }
        } else {
            exactSet.add(r);
        }
    }

    /** 批量添加规则。 */
    public void addRules(Iterable<String> rules) {
        if (rules == null) {
            return;
        }
        for (String rule : rules) {
            addRule(rule);
        }
    }

    /** 判断 hostname 是否命中任一规则。 */
    public boolean match(String hostname) {
        if (hostname == null) {
            return false;
        }
        String host = normalize(hostname);
        if (host.isEmpty()) {
            return false;
        }
        if (exactSet.contains(host)) {
            return true;
        }
        if (adblockSet.contains(host)) {
            return true;
        }
        // 逐层去掉最左标签，检查后缀是否命中子域 / adblock
        String suffix = host;
        int idx;
        while ((idx = suffix.indexOf('.')) >= 0) {
            suffix = suffix.substring(idx + 1);
            if (adblockSet.contains(suffix)) {
                return true;
            }
            if (subdomainSet.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    public int ruleCount() {
        return exactSet.size() + subdomainSet.size() + adblockSet.size();
    }

    private String normalize(String s) {
        String v = s.trim();
        if (v.endsWith(".")) {
            v = v.substring(0, v.length() - 1);
        }
        return caseInsensitive ? v.toLowerCase(Locale.ROOT) : v;
    }
}
