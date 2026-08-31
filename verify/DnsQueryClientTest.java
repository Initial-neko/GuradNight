package com.example.nightscreenguard.dns;

import com.example.nightscreenguard.dns.DnsQueryClient;

import java.util.List;

/** 纯 JVM 验证 app 内 DNS 测试客户端的报文构造与响应解析。 */
public class DnsQueryClientTest {
    static int pass = 0, fail = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            pass++;
            System.out.println("  [PASS] " + name);
        } else {
            fail++;
            System.out.println("  [FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== buildQuery ==");
        byte[] q = DnsQueryClient.buildQuery("baidu.com");
        check("query 长度 = 12+name+4", q.length == 27);
        check("RD 标志=1", (q[2] & 0x01) == 1);
        check("QDCOUNT=1", q[4] == 0 && q[5] == 1);
        // qname: 2|ba|5|idu|3|com|0
        check("label 1 len=5", (q[12] & 0xFF) == 5);
        check("label 1='baidu'", q[13]=='b'&&q[14]=='a'&&q[15]=='i'&&q[16]=='d'&&q[17]=='u');
        check("label 2 len=3", (q[18] & 0xFF) == 3);
        check("label 2='com'", q[19]=='c'&&q[20]=='o'&&q[21]=='m');
        check("terminator=0(2)", (q[22] & 0xFF) == 0);
        check("QTYPE=A(2)", q[23]==0 && q[24]==1);
        check("QCLASS=IN(2)", q[25]==0 && q[26]==1);
        
        
        // 随机 id
        byte[] q2 = DnsQueryClient.buildQuery("baidu.com");
        check("两次查询 id 不同（随机）", !(q[0] == q2[0] && q[1] == q2[1]));

        System.out.println("== parseResponse: NXDOMAIN ==");
        // header: id + flags=0x8183 (QR RD RA NXDOMAIN) + qd=1 an=0 ns=0 ar=0 + question
        byte[] nx = new byte[48];
        nx[0] = 0x12; nx[1] = 0x34;
        nx[2] = (byte) 0x81; nx[3] = (byte) 0x83; // NXDOMAIN
        nx[4] = 0; nx[5] = 1; // qd
        // question: 2|ba|5|idu|3|com|0|type A|class IN
        int p = 12;
        nx[p++] = 2; nx[p++] = 'b'; nx[p++] = 'a';
        nx[p++] = 5; nx[p++] = 'b'; nx[p++] = 'a'; nx[p++] = 'i'; nx[p++] = 'd'; nx[p++] = 'u';
        nx[p++] = 3; nx[p++] = 'c'; nx[p++] = 'o'; nx[p++] = 'm';
        nx[p++] = 0;
        nx[p++] = 0; nx[p++] = 1; nx[p++] = 0; nx[p++] = 1;
        DnsQueryClient.Result r1 = DnsQueryClient.parseResponse(nx, p);
        check("rcode=3 (NXDOMAIN)", r1.rcode == 3);
        check("isNxdomain=true", r1.isNxdomain());
        check("无 answer", r1.answers.isEmpty());

        System.out.println("== parseResponse: NOERROR + A 记录 ==");
        // header: id + flags=0x8180 + qd=1 an=1 + question + answer(compressed name C00C, A, ttl, rdlen=4, 110.242.68.66)
        byte[] ok = new byte[12 + 16 + 4 + 12 + 4];
        ok[0] = 0x56; ok[1] = 0x78;
        ok[2] = (byte) 0x81; ok[3] = (byte) 0x80; // NOERROR
        ok[4] = 0; ok[5] = 1; // qd
        ok[6] = 0; ok[7] = 1; // an
        p = 12;
        ok[p++] = 2; ok[p++] = 'b'; ok[p++] = 'a';
        ok[p++] = 5; ok[p++] = 'b'; ok[p++] = 'a'; ok[p++] = 'i'; ok[p++] = 'd'; ok[p++] = 'u';
        ok[p++] = 3; ok[p++] = 'c'; ok[p++] = 'o'; ok[p++] = 'm';
        ok[p++] = 0;
        ok[p++] = 0; ok[p++] = 1; ok[p++] = 0; ok[p++] = 1;
        int qend = p;
        // answer: name=C00C, type=1, class=1, ttl=300, rdlen=4
        ok[p++] = (byte) 0xC0; ok[p++] = 0x0C;
        ok[p++] = 0; ok[p++] = 1;
        ok[p++] = 0; ok[p++] = 1;
        ok[p++] = 0; ok[p++] = 0; ok[p++] = 1; ok[p++] = 44;
        ok[p++] = 0; ok[p++] = 4;
        ok[p++] = 110; ok[p++] = (byte) 242; ok[p++] = 68; ok[p++] = 66;
        DnsQueryClient.Result r2 = DnsQueryClient.parseResponse(ok, p);
        check("rcode=0 (NOERROR)", r2.rcode == 0);
        check("非 NXDOMAIN", !r2.isNxdomain());
        check("有 1 个 A 记录", r2.answers.size() == 1);
        check("IP=110.242.68.66", "110.242.68.66".equals(r2.answers.get(0)));
        check("describe 含 NOERROR", r2.describe().contains("NOERROR"));

        System.out.println("== encodeName ==");
        byte[] en = DnsQueryClient.encodeName("a.b.com.");
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (en[i] != 0) {
            int len = en[i++] & 0xFF;
            for (int k = 0; k < len; k++) sb.append((char) en[i++]);
            sb.append('.');
        }
        check("encode a.b.com → a.b.com.", "a.b.com.".equals(sb.toString()));

        System.out.println("\n===== 结果: " + pass + " 通过, " + fail + " 失败 =====");
        if (fail > 0) System.exit(1);
    }
}
