package com.example.nightscreenguard.dns;

/**
 * 最小 IPv4/UDP 封装（纯 Java，可单测）。
 * 用于在 tun 设备上读取/回写 DNS 报文：
 *  - 从 IP 包中剥离 UDP payload（DNS 报文）
 *  - 构造 DNS 应答的 IP+UDP 封装（交换源/目的，重算 IP 头校验和）
 */
public final class DnsPacket {

    public static final int IPPROTO_UDP = 17;

    public static final class IpParseException extends Exception {
        public IpParseException(String message) {
            super(message);
        }
    }

    private DnsPacket() {
    }

    /** 解析出的 IP 包视图。 */
    public static final class IpPacket {
        public final int totalLength;
        public final int protocol;
        public final int srcIp;   // 4 字节大端
        public final int dstIp;
        public final int udpOffset; // IP 包内 UDP 头偏移
        public final int udpLength; // UDP 段总长（头+payload）

        IpPacket(int totalLength, int protocol, int srcIp, int dstIp,
                 int udpOffset, int udpLength) {
            this.totalLength = totalLength;
            this.protocol = protocol;
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.udpOffset = udpOffset;
            this.udpLength = udpLength;
        }
    }

    /** 解析 IPv4 包，返回包含 UDP 视图的解析结果；非 IPv4/UDP 抛异常。 */
    public static IpPacket parseIpv4(byte[] pkt) throws IpParseException {
        if (pkt == null || pkt.length < 20) {
            throw new IpParseException("ip packet too short");
        }
        int versionIhl = pkt[0] & 0xFF;
        int version = versionIhl >> 4;
        int ihl = (versionIhl & 0x0F) * 4;
        if (version != 4) {
            throw new IpParseException("not ipv4: version=" + version);
        }
        if (ihl < 20 || pkt.length < ihl) {
            throw new IpParseException("bad ihl: " + ihl);
        }
        int totalLen = ((pkt[2] & 0xFF) << 8) | (pkt[3] & 0xFF);
        if (totalLen < ihl) {
            totalLen = pkt.length;
        }
        if (totalLen > pkt.length) {
            totalLen = pkt.length;
        }
        int proto = pkt[9] & 0xFF;
        int srcIp = readIp(pkt, 12);
        int dstIp = readIp(pkt, 16);
        if (proto != IPPROTO_UDP) {
            throw new IpParseException("not udp: proto=" + proto);
        }
        int udpOffset = ihl;
        if (udpOffset + 8 > totalLen) {
            throw new IpParseException("udp header truncated");
        }
        int udpLen = ((pkt[udpOffset + 4] & 0xFF) << 8) | (pkt[udpOffset + 5] & 0xFF);
        if (udpLen < 8) {
            throw new IpParseException("bad udp length: " + udpLen);
        }
        int avail = totalLen - udpOffset;
        if (udpLen > avail) {
            udpLen = avail;
        }
        return new IpPacket(totalLen, proto, srcIp, dstIp, udpOffset, udpLen);
    }

    /** 从 IP 包中取 UDP payload（即 DNS 报文）。 */
    public static byte[] udpPayload(byte[] pkt, IpPacket ip) {
        int dataLen = ip.udpLength - 8;
        byte[] out = new byte[dataLen];
        System.arraycopy(pkt, ip.udpOffset + 8, out, 0, dataLen);
        return out;
    }

    /** 读取 UDP 头中的源端口。 */
    public static int udpSrcPort(byte[] pkt, IpPacket ip) {
        return ((pkt[ip.udpOffset] & 0xFF) << 8) | (pkt[ip.udpOffset + 1] & 0xFF);
    }

    /** 读取 UDP 头中的目的端口。 */
    public static int udpDstPort(byte[] pkt, IpPacket ip) {
        return ((pkt[ip.udpOffset + 2] & 0xFF) << 8) | (pkt[ip.udpOffset + 3] & 0xFF);
    }

    /**
     * 构造对原 DNS 查询的应答 IP 包：交换 IP 源/目的与 UDP 源/目的端口，
     * 用 dnsResponse 作为新 UDP payload，重算 IP 头校验和。
     */
    public static byte[] buildResponsePacket(byte[] queryPkt, IpPacket ip, byte[] dnsResponse) {
        int udpLen = 8 + dnsResponse.length;
        int total = ip.udpOffset + udpLen;
        byte[] out = new byte[total];

        // 复制并改写 IP 头（前 ip.udpOffset 字节）
        System.arraycopy(queryPkt, 0, out, 0, ip.udpOffset);
        // total length
        out[2] = (byte) (total >> 8);
        out[3] = (byte) total;
        // 交换源/目的 IP
        writeIp(out, 12, ip.dstIp);
        writeIp(out, 16, ip.srcIp);
        // ID、flags、frag、TTL、proto 保持原样（从 query 复制）
        // header checksum 置 0 后重算
        out[10] = 0;
        out[11] = 0;
        int sum = checksum(out, 0, ip.udpOffset);
        out[10] = (byte) (sum >> 8);
        out[11] = (byte) sum;

        // UDP 头：交换端口（应答 src=原 dst，dst=原 src），长度 = 8 + payload，checksum=0（IPv4 允许）
        int u = ip.udpOffset;
        out[u] = (byte) (udpDstPort(queryPkt, ip) >> 8);
        out[u + 1] = (byte) udpDstPort(queryPkt, ip);
        out[u + 2] = (byte) (udpSrcPort(queryPkt, ip) >> 8);
        out[u + 3] = (byte) udpSrcPort(queryPkt, ip);
        out[u + 4] = (byte) (udpLen >> 8);
        out[u + 5] = (byte) udpLen;
        out[u + 6] = 0;
        out[u + 7] = 0;

        System.arraycopy(dnsResponse, 0, out, u + 8, dnsResponse.length);
        return out;
    }

    private static int readIp(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16)
                | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static void writeIp(byte[] b, int o, int ip) {
        b[o] = (byte) (ip >> 24);
        b[o + 1] = (byte) (ip >> 16);
        b[o + 2] = (byte) (ip >> 8);
        b[o + 3] = (byte) ip;
    }

    /** 标准 IP 头校验和。 */
    private static int checksum(byte[] b, int start, int len) {
        long sum = 0;
        int i = start;
        while (i < start + len - 1) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            i += 2;
        }
        if (i < start + len) {
            sum += (b[i] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }
}
