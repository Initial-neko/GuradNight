package com.example.nightscreenguard.dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * App 内置的 DNS 查询客户端，用于“真实链路测试”：
 * 当 VPN 拦截服务运行时，向虚拟 DNS（10.1.10.1:53）发起查询，
 * 若返回 NXDOMAIN 说明该域名被拦截生效；返回 NOERROR + IP 说明未拦截/放行。
 */
public final class DnsQueryClient {
    /** 与 NightVpnService 保持一致的虚拟 DNS 地址。 */
    public static final String VIRTUAL_DNS = "10.1.10.1";

    /** 一次 DNS 查询的结果。 */
    public static final class Result {
        /** 0=NOERROR, 3=NXDOMAIN, 2=SERVFAIL。 */
        public final int rcode;
        /** 解析到的 A 记录 IPv4 地址（未命中时通常有值）。 */
        public final List<String> answers;
        public Result(int rcode, List<String> answers) {
            this.rcode = rcode;
            this.answers = answers;
        }
        public boolean isNxdomain() {
            return rcode == 3;
        }
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (isNxdomain()) {
                sb.append("NXDOMAIN（已被拦截）");
            } else if (rcode == 0) {
                sb.append("NOERROR（未被拦截）");
                if (!answers.isEmpty()) {
                    sb.append(" → ").append(String.join(", ", answers));
                }
            } else {
                sb.append("rcode=").append(rcode);
            }
            return sb.toString();
        }
    }

    private DnsQueryClient() {
    }

    /** 构造一个 DNS A 查询报文。 */
    public static byte[] buildQuery(String domain) {
        byte[] name = encodeName(domain);
        byte[] q = new byte[12 + name.length + 4];
        Random rnd = new Random();
        q[0] = (byte) rnd.nextInt(256);
        q[1] = (byte) rnd.nextInt(256);
        q[2] = 0x01; // RD=1
        q[3] = 0;
        q[4] = 0;
        q[5] = 1; // QDCOUNT=1
        System.arraycopy(name, 0, q, 12, name.length);
        int p = 12 + name.length;
        q[p] = 0;
        q[p + 1] = 1; // QTYPE A
        q[p + 2] = 0;
        q[p + 3] = 1; // QCLASS IN
        return q;
    }

    /** 向指定 DNS 服务器发起 A 查询并解析响应。 */
    public static Result query(InetAddress server, int port, String domain, int timeoutMillis)
            throws IOException {
        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setSoTimeout(timeoutMillis);
            byte[] q = buildQuery(domain);
            socket.send(new DatagramPacket(q, q.length, server, port));
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            return parseResponse(resp.getData(), resp.getLength());
        } finally {
            socket.close();
        }
    }

    /** 解析 DNS 响应：提取 rcode 与 A 记录。 */
    static Result parseResponse(byte[] data, int len) {
        if (len < 12) {
            return new Result(-1, new ArrayList<String>());
        }
        int flags = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int rcode = flags & 0x0F;
        int qdcount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int ancount = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        int offset = 12;
        // 跳过 question 段
        for (int i = 0; i < qdcount; i++) {
            offset = skipName(data, offset, len);
            if (offset < 0 || offset + 4 > len) {
                return new Result(rcode, new ArrayList<String>());
            }
            offset += 4;
        }
        List<String> answers = new ArrayList<String>();
        for (int i = 0; i < ancount && offset >= 0 && offset < len; i++) {
            offset = skipName(data, offset, len);
            if (offset < 0 || offset + 10 > len) {
                break;
            }
            int type = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            int rdlength = ((data[offset + 8] & 0xFF) << 8) | (data[offset + 9] & 0xFF);
            offset += 10;
            if (type == 1 && rdlength == 4 && offset + 4 <= len) { // A 记录
                answers.add((data[offset] & 0xFF) + "." + (data[offset + 1] & 0xFF) + "."
                        + (data[offset + 2] & 0xFF) + "." + (data[offset + 3] & 0xFF));
            }
            offset += rdlength;
        }
        return new Result(rcode, answers);
    }

    private static int skipName(byte[] data, int offset, int len) {
        while (offset < len) {
            int b = data[offset] & 0xFF;
            if (b == 0) {
                return offset + 1;
            }
            if ((b & 0xC0) == 0xC0) { // 压缩指针
                return offset + 2;
            }
            offset += 1 + b;
        }
        return -1;
    }

    /** 把域名编码为 DNS wire format（labels）。 */
    static byte[] encodeName(String domain) {
        String d = domain.trim();
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        String[] labels = d.split("\\.");
        int size = 1;
        for (String label : labels) {
            size += 1 + label.length();
        }
        byte[] out = new byte[size];
        int p = 0;
        for (String label : labels) {
            out[p++] = (byte) label.length();
            byte[] lb = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(lb, 0, out, p, lb.length);
            p += lb.length;
        }
        out[p] = 0;
        return out;
    }
}
