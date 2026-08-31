package com.example.nightscreenguard.dns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 轻量 DNS 拦截 VpnService。
 *
 * 工作方式：建立 tun，把虚拟地址 10.1.10.1:53 设为系统 DNS 且只路由该地址进入 VPN，
 * 因此仅 DNS 流量进入本服务，其余网络流量完全不受影响。
 * 对每个 DNS 查询：解析域名 -> DomainMatcher 判定：
 *   - 命中：返回 NXDOMAIN 拦截应答（客户端解析失败）
 *   - 未命中：用 protect 的 socket 转发真实 DNS，回传响应
 */
public final class NightVpnService extends VpnService implements Runnable {

    private static final String TAG = "NightScreenGuard";
    private static final String VIRTUAL_DNS_IP = "10.1.10.1";
    private static final int DNS_PORT = 53;
    private static final int FORWARD_TIMEOUT_MILLIS = 3000;
    private static final int CHANNEL_ID_DNS = 101;

    private volatile boolean running = false;
    private ParcelFileDescriptor tunnelFd;
    private Thread thread;
    private DnsInterceptor interceptor;
    private DnsForwarder forwarder;
    private BlockEventStore eventStore;

    @Override
    public void onCreate() {
        super.onCreate();
        this.eventStore = new BlockEventStore();
        DomainMatcher matcher = loadMatcher();
        this.interceptor = new DnsInterceptor(matcher, (qname, ts) -> {
            eventStore.recordBlock(qname, ts);
            persistStats();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startVpn();
        return START_STICKY;
    }

    /** 启动 VPN；若未授权则返回 false（调用方应引导用户授权）。 */
    public synchronized boolean startVpn() {
        if (running) {
            return true;
        }
        try {
            tunnelFd = establishTunnel();
            if (tunnelFd == null) {
                Log.w(TAG, "vpn_establish_null");
                return false;
            }
            running = true;
            thread = new Thread(this, "night-dns-vpn");
            thread.start();
            showRunningNotification();
            Log.i(TAG, "vpn_started");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "vpn_start_failed", e);
            stopVpn();
            return false;
        }
    }

    private ParcelFileDescriptor establishTunnel() throws Exception {
        Builder builder = new Builder();
        builder.setSession("NightScreenGuard DNS 拦截");
        builder.setMtu(1500);
        builder.addAddress(VIRTUAL_DNS_IP, 32);
        builder.addRoute(VIRTUAL_DNS_IP, 32);
        builder.addDnsServer(VIRTUAL_DNS_IP);
        return builder.establish();
    }

    @Override
    public void run() {
        FileInputStream in = new FileInputStream(tunnelFd.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(tunnelFd.getFileDescriptor());
        // 获取系统 DNS 作为上游
        InetAddress upstream = pickUpstream();
        if (upstream == null) {
            Log.w(TAG, "no_upstream_dns");
            stopVpn();
            return;
        }
        try {
            forwarder = new DnsForwarder(upstream, DNS_PORT, new DnsForwarder.SocketProtector() {
                @Override
                public void protect(DatagramSocket socket) {
                    NightVpnService.this.protect(socket);
                }
            });
        } catch (IOException e) {
            Log.w(TAG, "forwarder_init_failed", e);
            stopVpn();
            return;
        }

        byte[] buf = new byte[65535];
        while (running) {
            int len;
            try {
                len = in.read(buf);
            } catch (IOException e) {
                break;
            }
            if (len <= 0) {
                break;
            }
            byte[] packet = new byte[len];
            System.arraycopy(buf, 0, packet, 0, len);
            handlePacket(packet, out);
        }
        cleanup();
    }

    private void handlePacket(byte[] packet, FileOutputStream out) {
        DnsPacket.IpPacket ip;
        try {
            ip = DnsPacket.parseIpv4(packet);
        } catch (DnsPacket.IpParseException e) {
            return;
        }
        byte[] blocked = interceptor.maybeBlock(packet);
        if (blocked != null) {
            writePacket(out, blocked);
            return;
        }
        // 未命中：转发真实 DNS
        byte[] query = DnsPacket.udpPayload(packet, ip);
        byte[] response = forwarder.forward(query, FORWARD_TIMEOUT_MILLIS);
        if (response != null) {
            writePacket(out, DnsPacket.buildResponsePacket(packet, ip, response));
        }
    }

    private void writePacket(FileOutputStream out, byte[] data) {
        try {
            out.write(data);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private InetAddress pickUpstream() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) {
                return InetAddress.getByName("223.5.5.5");
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return InetAddress.getByName("223.5.5.5");
            }
            LinkProperties lp = cm.getLinkProperties(network);
            if (lp == null) {
                return InetAddress.getByName("223.5.5.5");
            }
            List<InetAddress> dns = lp.getDnsServers();
            if (dns != null && !dns.isEmpty()) {
                return dns.get(0);
            }
        } catch (UnknownHostException ignored) {
        }
        try {
            return InetAddress.getByName("223.5.5.5");
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** 从 SharedPreferences 加载拦截规则（后续与 GuardConfig 统一）。 */
    private DomainMatcher loadMatcher() {
        DomainMatcher matcher = new DomainMatcher();
        String rules = getSharedPreferences("night_dns_rules", MODE_PRIVATE)
                .getString("rules", "||baidu.com^\n*.douyin.com\n||bytecdn.cn^");
        DnsRuleParser.parseInto(matcher, rules);
        Log.i(TAG, "rules_loaded count=" + matcher.ruleCount());
        return matcher;
    }

    private void persistStats() {
        // 简单持久化：把累计拦截数写入 SharedPreferences（供统计页展示）
        getSharedPreferences("night_dns_stats", MODE_PRIVATE)
                .edit()
                .putInt("total_blocks", eventStore.totalEvents())
                .apply();
    }

    private void showRunningNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(
                        "dns_service", "域名拦截服务", NotificationManager.IMPORTANCE_LOW));
            }
            Notification notification = new Notification.Builder(this, "dns_service")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("NightScreenGuard")
                    .setContentText("域名拦截运行中")
                    .setOngoing(true)
                    .build();
            startForeground(CHANNEL_ID_DNS, notification);
        }
    }

    /** 停止 VPN 并清理资源。 */
    public synchronized void stopVpn() {
        running = false;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        thread = null;
        if (forwarder != null) {
            forwarder.close();
            forwarder = null;
        }
        if (tunnelFd != null) {
            try {
                tunnelFd.close();
            } catch (IOException ignored) {
            }
            tunnelFd = null;
        }
        stopForeground(true);
        stopSelf();
        Log.i(TAG, "vpn_stopped");
    }

    private void cleanup() {
        if (forwarder != null) {
            forwarder.close();
            forwarder = null;
        }
        if (tunnelFd != null) {
            try {
                tunnelFd.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        cleanup();
        super.onDestroy();
    }

    /** 外部（GuardService/DomainGuardController）的启停入口。 */
    public static void start(Context context) {
        Intent intent = new Intent(context, NightVpnService.class);
        context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, NightVpnService.class));
    }

    /** 检查是否已获得 VPN 授权。 */
    public static boolean isAuthorized(Context context) {
        return VpnService.prepare(context) == null;
    }
}
