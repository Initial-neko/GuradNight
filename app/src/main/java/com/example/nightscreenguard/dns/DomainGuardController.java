package com.example.nightscreenguard.dns;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.provider.Settings;

/**
 * 域名拦截门面：由 GuardService 在监测窗口内调用。
 * 封装 VPN 授权检查、启停、以及授权引导 Intent。
 */
public final class DomainGuardController {

    private DomainGuardController() {
    }

    /** 启动域名拦截。返回 true 表示已启动；false 表示未授权（调用方应引导授权）。 */
    public static boolean start(Context context) {
        if (!NightVpnService.isAuthorized(context)) {
            return false;
        }
        NightVpnService.start(context);
        return true;
    }

    /** 停止域名拦截。 */
    public static void stop(Context context) {
        NightVpnService.stop(context);
    }

    /** 是否已获 VPN 授权。 */
    public static boolean isAuthorized(Context context) {
        return VpnService.prepare(context) == null;
    }

    /** 构造 VPN 授权引导 Intent（若已授权返回 null）。 */
    public static Intent authorizationIntent(Context context) {
        if (isAuthorized(context)) {
            return null;
        }
        return VpnService.prepare(context);
    }

    /** 打开 VPN 授权引导（需要 startActivity 调用）。 */
    public static void openAuthorization(Context context) {
        Intent intent = authorizationIntent(context);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
