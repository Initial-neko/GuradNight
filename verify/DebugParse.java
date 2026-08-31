package com.example.nightscreenguard.dns;
import java.util.Arrays;
public class DebugParse {
    public static void main(String[] a) {
        byte[] ok = new byte[48];
        ok[0] = 0x56; ok[1] = 0x78;
        ok[2] = (byte) 0x81; ok[3] = (byte) 0x80;
        ok[4] = 0; ok[5] = 1; ok[6] = 0; ok[7] = 1;
        int p = 12;
        ok[p++] = 2; ok[p++] = 'b'; ok[p++] = 'a';
        ok[p++] = 5; ok[p++] = 'b'; ok[p++] = 'a'; ok[p++] = 'i'; ok[p++] = 'd'; ok[p++] = 'u';
        ok[p++] = 3; ok[p++] = 'c'; ok[p++] = 'o'; ok[p++] = 'm';
        ok[p++] = 0;
        ok[p++] = 0; ok[p++] = 1; ok[p++] = 0; ok[p++] = 1;
        System.out.println("qend=" + p);
        ok[p++] = (byte) 0xC0; ok[p++] = 0x0C;
        ok[p++] = 0; ok[p++] = 1;
        ok[p++] = 0; ok[p++] = 1;
        ok[p++] = 0; ok[p++] = 0; ok[p++] = 1; ok[p++] = 44;
        ok[p++] = 0; ok[p++] = 4;
        ok[p++] = 110; ok[p++] = (byte) 242; ok[p++] = 68; ok[p++] = 66;
        System.out.println("answer end p=" + p);
        System.out.println("bytes: " + Arrays.toString(ok));
        DnsQueryClient.Result r = DnsQueryClient.parseResponse(ok, p);
        System.out.println("rcode=" + r.rcode + " answers=" + r.answers);
    }
}
