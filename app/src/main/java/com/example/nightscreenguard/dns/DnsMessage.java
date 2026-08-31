package com.example.nightscreenguard.dns;

/**
 * 最小 DNS 报文编解码（纯 Java，无 Android 依赖，可 JVM 单测）。
 *
 * 只处理本地拦截所需的最小子集：
 *  - 解析一个 UDP DNS 查询报文的 ID、Flags、Question（QNAME/QTYPE/QCLASS）
 *  - 构造两类拦截响应：
 *      a) NXDOMAIN（RCODE=3，域名不存在）—— 默认拦截，客户端解析失败
 *      b) A 记录 0.0.0.0（空地址）—— 备选
 *  - 未命中时透传原报文（转发交给上层）
 *
 * 解析支持压缩指针（0xC0 前缀，带跳转上限防环）；构造响应时用非压缩格式（安全、无歧义）。
 */
public final class DnsMessage {

    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;
    public static final int CLASS_IN = 1;

    public static final int RCODE_NOERROR = 0;
    public static final int RCODE_SERVFAIL = 2;
    public static final int RCODE_NXDOMAIN = 3;

    public static final class DnsParseException extends Exception {
        public DnsParseException(String message) {
            super(message);
        }
    }

    private final int id;
    private final int flags;
    private final int qdCount;
    private final int anCount;
    private final int nsCount;
    private final int arCount;
    private final String qname;   // 小写、无末尾点，如 "www.baidu.com"
    private final int qtype;
    private final int qclass;

    private DnsMessage(int id, int flags, int qdCount, int anCount, int nsCount, int arCount,
                       String qname, int qtype, int qclass) {
        this.id = id;
        this.flags = flags;
        this.qdCount = qdCount;
        this.anCount = anCount;
        this.nsCount = nsCount;
        this.arCount = arCount;
        this.qname = qname;
        this.qtype = qtype;
        this.qclass = qclass;
    }

    public int id() {
        return id;
    }

    public boolean isQuery() {
        return (flags & 0x8000) == 0;
    }

    public String qname() {
        return qname;
    }

    public int qtype() {
        return qtype;
    }

    public int qclass() {
        return qclass;
    }

    public boolean isAddressQuery() {
        return qtype == TYPE_A || qtype == TYPE_AAAA;
    }

    /** 解析 UDP DNS 报文（UDP payload 即报文本身）。 */
    public static DnsMessage parse(byte[] packet) throws DnsParseException {
        if (packet == null || packet.length < 12) {
            throw new DnsParseException("packet too short");
        }
        int id = readU16(packet, 0);
        int flags = readU16(packet, 2);
        int qd = readU16(packet, 4);
        int an = readU16(packet, 6);
        int ns = readU16(packet, 8);
        int ar = readU16(packet, 10);
        if (qd < 1) {
            throw new DnsParseException("no question");
        }
        NameResult name = readName(packet, 12);
        if (name.next + 4 > packet.length) {
            throw new DnsParseException("question truncated");
        }
        int qtype = readU16(packet, name.next);
        int qclass = readU16(packet, name.next + 2);
        return new DnsMessage(id, flags, qd, an, ns, ar, name.name.toLowerCase(), qtype, qclass);
    }

    /** 构造 NXDOMAIN 拦截响应（RCODE=3）。 */
    public byte[] buildNxdomainResponse() {
        return buildResponse(RCODE_NXDOMAIN, null);
    }

    /** 构造 0.0.0.0 A 记录拦截响应。 */
    public byte[] buildBlockAResponse() {
        return buildResponse(RCODE_NOERROR, new byte[] {0, 0, 0, 0});
    }

    /**
     * 构造响应：QR=1、保留 RD、设 RA、写入 rcode，复制 question，可选带一个 A 记录 answer。
     */
    private byte[] buildResponse(int rcode, byte[] ipv4) {
        byte[] qnameEnc = encodeName(qname);
        int answerLen = ipv4 == null ? 0 : 16; // name(2)+type(2)+class(2)+ttl(4)+rdlen(2)+rdata(4)
        byte[] out = new byte[12 + qnameEnc.length + 4 + answerLen];

        int respFlags = 0x8000 | (flags & 0x0100) | 0x0080 | (rcode & 0x0F); // QR|RD|RA|RCODE
        writeU16(out, 0, id);
        writeU16(out, 2, respFlags);
        writeU16(out, 4, qdCount);
        writeU16(out, 6, ipv4 == null ? 0 : 1);
        writeU16(out, 8, 0);
        writeU16(out, 10, 0);

        int o = 12;
        System.arraycopy(qnameEnc, 0, out, o, qnameEnc.length);
        o += qnameEnc.length;
        writeU16(out, o, qtype);
        writeU16(out, o + 2, qclass);
        o += 4;

        if (ipv4 != null) {
            writeU16(out, o, 0xC00C);      // 名称指针指向报文 offset 12（question 起点）
            writeU16(out, o + 2, TYPE_A);
            writeU16(out, o + 4, CLASS_IN);
            writeU32(out, o + 6, 60);      // TTL 60s
            writeU16(out, o + 10, 4);      // RDLENGTH
            System.arraycopy(ipv4, 0, out, o + 12, 4);
        }
        return out;
    }

    /** 将域名编码为 DNS 名称（非压缩）。 */
    private static byte[] encodeName(String name) {
        // 计算总长：每段 1+len，最后 1 字节 0
        int size = 1;
        for (String label : name.split("\\.")) {
            size += 1 + label.length();
        }
        byte[] out = new byte[size];
        int o = 0;
        for (String label : name.split("\\.")) {
            out[o++] = (byte) label.length();
            for (int i = 0; i < label.length(); i++) {
                out[o++] = (byte) label.charAt(i);
            }
        }
        out[o] = 0;
        return out;
    }

    private static final class NameResult {
        final String name;
        final int next; // question 中 QTYPE 的偏移
        NameResult(String name, int next) {
            this.name = name;
            this.next = next;
        }
    }

    /** 读取 DNS 名称，支持压缩指针（带 64 次跳转上限防环）。 */
    private static NameResult readName(byte[] packet, int start) throws DnsParseException {
        StringBuilder sb = new StringBuilder();
        int offset = start;
        int endOffset = -1;
        boolean jumped = false;
        int jumps = 0;
        while (true) {
            if (offset >= packet.length) {
                throw new DnsParseException("name overflow");
            }
            int len = packet[offset] & 0xFF;
            if (len == 0) {
                offset++;
                if (!jumped) {
                    endOffset = offset;
                }
                break;
            }
            if ((len & 0xC0) == 0xC0) {
                if (offset + 1 >= packet.length) {
                    throw new DnsParseException("bad pointer");
                }
                int ptr = ((len & 0x3F) << 8) | (packet[offset + 1] & 0xFF);
                if (!jumped) {
                    endOffset = offset + 2;
                    jumped = true;
                }
                if (++jumps > 64) {
                    throw new DnsParseException("too many pointer jumps");
                }
                offset = ptr;
                continue;
            }
            if ((len & 0xC0) != 0) {
                throw new DnsParseException("unsupported label type: " + (len & 0xC0));
            }
            if (offset + 1 + len > packet.length) {
                throw new DnsParseException("label overflow");
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            for (int i = 0; i < len; i++) {
                sb.append((char) (packet[offset + 1 + i] & 0xFF));
            }
            offset += 1 + len;
        }
        if (endOffset < 0) {
            throw new DnsParseException("no name end");
        }
        return new NameResult(sb.toString(), endOffset);
    }

    private static int readU16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static void writeU16(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 8) & 0xFF);
        b[o + 1] = (byte) (v & 0xFF);
    }

    private static void writeU32(byte[] b, int o, long v) {
        b[o] = (byte) ((v >> 24) & 0xFF);
        b[o + 1] = (byte) ((v >> 16) & 0xFF);
        b[o + 2] = (byte) ((v >> 8) & 0xFF);
        b[o + 3] = (byte) (v & 0xFF);
    }
}
