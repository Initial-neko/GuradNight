package com.example.nightscreenguard.dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 真实 DNS 转发器：把未命中的查询转发到上游 DNS，并把响应回传。
 * socket 通过 SocketProtector 交给 VpnService.protect，避免回环进入 VPN。
 */
public final class DnsForwarder {

    /** 由 VpnService 实现 protect(socket)，避免转发 socket 被 VPN 再次接管。 */
    public interface SocketProtector {
        void protect(DatagramSocket socket);
    }

    private final InetAddress upstream;
    private final int upstreamPort;
    private final SocketProtector protector;
    private final DatagramSocket socket;

    public DnsForwarder(InetAddress upstream, int upstreamPort) throws IOException {
        this(upstream, upstreamPort, null);
    }

    public DnsForwarder(InetAddress upstream, int upstreamPort, SocketProtector protector)
            throws IOException {
        this.upstream = upstream;
        this.upstreamPort = upstreamPort;
        this.protector = protector;
        this.socket = new DatagramSocket();
        if (protector != null) {
            protector.protect(socket);
        }
    }

    /**
     * 同步转发一个 DNS 查询并返回上游响应报文。
     * @param query 原始 DNS 查询报文（UDP payload）
     * @param timeoutMillis 等待响应的超时
     * @return 上游返回的 DNS 响应报文；超时/失败返回 null
     */
    public byte[] forward(byte[] query, int timeoutMillis) {
        try {
            socket.setSoTimeout(timeoutMillis);
            DatagramPacket out = new DatagramPacket(query, query.length, upstream, upstreamPort);
            socket.send(out);
            byte[] buf = new byte[4096];
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            socket.receive(in);
            byte[] resp = new byte[in.getLength()];
            System.arraycopy(buf, 0, resp, 0, in.getLength());
            return resp;
        } catch (IOException e) {
            return null;
        }
    }

    public void close() {
        socket.close();
    }
}
