package com.example.nightscreenguard.dns;

/**
 * 单包拦截判定器（纯 Java，可单测）：输入一个进入 tun 的 IP 包，
 * 若命中域名黑名单则返回构造好的拦截应答 IP 包；否则返回 null（交由转发）。
 */
public final class DnsInterceptor {

    private final DomainMatcher matcher;
    private final DnsPacketListener listener;

    /** 拦截事件回调（供统计使用；可为 null）。 */
    public interface DnsPacketListener {
        void onBlocked(String qname, long timestampMillis);
    }

    public DnsInterceptor(DomainMatcher matcher) {
        this(matcher, null);
    }

    public DnsInterceptor(DomainMatcher matcher, DnsPacketListener listener) {
        this.matcher = matcher;
        this.listener = listener;
    }

    /**
     * 处理一个 IP 包：
     *  - 非 IPv4/UDP、非 DNS 查询、未命中规则 -> 返回 null（放行/转发）
     *  - 命中规则 -> 返回 NXDOMAIN 拦截应答 IP 包
     */
    public byte[] maybeBlock(byte[] ipPacket) {
        if (ipPacket == null) {
            return null;
        }
        DnsPacket.IpPacket ip;
        try {
            ip = DnsPacket.parseIpv4(ipPacket);
        } catch (DnsPacket.IpParseException e) {
            return null;
        }
        byte[] dns;
        try {
            dns = DnsPacket.udpPayload(ipPacket, ip);
        } catch (Exception e) {
            return null;
        }
        DnsMessage msg;
        try {
            msg = DnsMessage.parse(dns);
        } catch (DnsMessage.DnsParseException e) {
            return null;
        }
        if (!msg.isQuery() || !msg.isAddressQuery()) {
            return null;
        }
        String qname = msg.qname();
        if (!matcher.match(qname)) {
            return null;
        }
        if (listener != null) {
            listener.onBlocked(qname, System.currentTimeMillis());
        }
        byte[] response = msg.buildNxdomainResponse();
        return DnsPacket.buildResponsePacket(ipPacket, ip, response);
    }
}
