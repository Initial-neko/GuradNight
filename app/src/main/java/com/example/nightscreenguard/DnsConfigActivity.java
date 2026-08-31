package com.example.nightscreenguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import java.net.InetAddress;
import android.net.VpnService;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.nightscreenguard.dns.DnsInterceptor;
import com.example.nightscreenguard.dns.DnsQueryClient;
import com.example.nightscreenguard.dns.DnsRuleParser;
import com.example.nightscreenguard.dns.DomainMatcher;
import com.example.nightscreenguard.dns.NightVpnService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.net.InetSocketAddress;

/**
 * 域名拦截的配置与测试界面（App 内自带测试手段）。
 *
 * 三个测试手段：
 *  1) 匹配测试：输入域名，用当前规则立即判定“将被拦截 / 放行”（无需 VPN 运行）。
 *  2) 真实 DNS 测试：拦截服务运行中时，向虚拟 DNS 10.1.10.1:53 发起真实查询，
 *     返回 NXDOMAIN 即“拦截已在真实链路生效”；返回 IP 即“该域名未被拦截”。
 *  3) 拦截统计：累计命中次数展示。
 *
 * 规则保存到 SharedPreferences(night_dns_rules)，与 NightVpnService 读取同一处；
 * 保存后若拦截服务运行中会自动重启以立即生效。
 */
public final class DnsConfigActivity extends Activity {

    private TextView vpnStatus;
    private MaterialButton authButton;
    private MaterialButton startButton;
    private MaterialButton stopButton;
    private EditText rulesInput;
    private EditText testDomain;
    private TextView resultText;
    private TextView statsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadRules();
        refreshVpnStatus();
        refreshStats();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF4F3EE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));

        // 标题
        TextView title = new TextView(this);
        title.setText("域名拦截 · 配置与测试");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1B1C);
        root.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("配置域名后，可直接在本页验证是否被拦截（精确 / *.通配 / adblock 语法）");
        sub.setTextSize(13);
        sub.setTextColor(0xFF6B7280);
        sub.setPadding(0, dp(2), 0, dp(4));
        root.addView(sub, matchWrap());

        // ===== VPN 状态卡片 =====
        MaterialCardView vpnCard = card();
        LinearLayout vpnBox = box();
        vpnStatus = new TextView(this);
        vpnStatus.setTextSize(14);
        vpnStatus.setTextColor(0xFF1A1B1C);
        vpnBox.addView(vpnStatus, matchWrap());

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, 0);
        authButton = new MaterialButton(this);
        authButton.setText("授权 VPN");
        authButton.setOnClickListener(v -> {
            Intent intent = NightVpnService.prepare(DnsConfigActivity.this);
            if (intent == null) {
                toast("已授权 VPN");
            } else {
                startActivityForResult(intent, 1001);
            }
        });
        btnRow.addView(authButton, weightWrap(1));

        startButton = new MaterialButton(this);
        startButton.setText("启动拦截");
        startButton.setOnClickListener(v -> {
            if (!NightVpnService.isAuthorized(this)) {
                toast("请先授权 VPN");
                return;
            }
            NightVpnService.start(this);
            refreshVpnStatus();
            toast("拦截服务已启动");
        });
        btnRow.addView(startButton, weightWrap(1));

        stopButton = new MaterialButton(this);
        stopButton.setText("停止拦截");
        stopButton.setOnClickListener(v -> {
            NightVpnService.stop(this);
            refreshVpnStatus();
            toast("拦截服务已停止");
        });
        btnRow.addView(stopButton, weightWrap(1));
        vpnBox.addView(btnRow, matchWrap());
        vpnCard.addView(vpnBox);
        root.addView(vpnCard, matchWrapMarginTop(10));

        // ===== 规则编辑卡片 =====
        MaterialCardView ruleCard = card();
        LinearLayout ruleBox = box();

        TextView ruleLabel = new TextView(this);
        ruleLabel.setText("拦截规则（每行一条）");
        ruleLabel.setTextSize(15);
        ruleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        ruleLabel.setTextColor(0xFF1A1B1C);
        ruleBox.addView(ruleLabel, matchWrap());

        TextView ruleHint = new TextView(this);
        ruleHint.setText("支持：baidu.com（精确）| *.baidu.com（子域通配）| ||baidu.com^（adblock，含自身）| notbaidu.com 不会误伤");
        ruleHint.setTextSize(12);
        ruleHint.setTextColor(0xFF6B7280);
        ruleBox.addView(ruleHint, matchWrap());

        rulesInput = new EditText(this);
        rulesInput.setGravity(Gravity.TOP | Gravity.START);
        rulesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        rulesInput.setTypeface(Typeface.MONOSPACE);
        rulesInput.setTextSize(14);
        rulesInput.setBackgroundColor(0xFFEFF1F4);
        rulesInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        rulesInput.setMinHeight(dp(140));
        rulesInput.setTextColor(0xFF1A1B1C);
        LinearLayout.LayoutParams rlp = matchWrap();
        rlp.topMargin = dp(8);
        ruleBox.addView(rulesInput, rlp);

        MaterialButton saveBtn = new MaterialButton(this);
        saveBtn.setText("保存规则并立即生效");
        saveBtn.setOnClickListener(v -> saveRules());
        LinearLayout.LayoutParams slp = matchWrap();
        slp.topMargin = dp(8);
        ruleBox.addView(saveBtn, slp);

        ruleCard.addView(ruleBox);
        root.addView(ruleCard, matchWrapMarginTop(10));

        // ===== 测试卡片 =====
        MaterialCardView testCard = card();
        LinearLayout testBox = box();

        TextView testLabel = new TextView(this);
        testLabel.setText("验证拦截效果");
        testLabel.setTextSize(15);
        testLabel.setTypeface(Typeface.DEFAULT_BOLD);
        testLabel.setTextColor(0xFF1A1B1C);
        testBox.addView(testLabel, matchWrap());

        TextView testHint = new TextView(this);
        testHint.setText("输入要测试的域名：\n· 匹配测试 → 用当前规则判定（无需 VPN）\n· 真实 DNS 测试 → 走虚拟 DNS 10.1.10.1 真实查询，需拦截服务运行中");
        testHint.setTextSize(12);
        testHint.setTextColor(0xFF6B7280);
        testBox.addView(testHint, matchWrap());

        testDomain = new EditText(this);
        testDomain.setSingleLine(true);
        testDomain.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        testDomain.setText("baidu.com");
        testDomain.setTextSize(15);
        testDomain.setHint("例如 baidu.com 或 a.baidu.com");
        testDomain.setBackgroundColor(0xFFEFF1F4);
        testDomain.setPadding(dp(10), dp(8), dp(10), dp(8));
        testDomain.setTextColor(0xFF1A1B1C);
        LinearLayout.LayoutParams tlp = matchWrap();
        tlp.topMargin = dp(8);
        testBox.addView(testDomain, tlp);

        LinearLayout testRow = new LinearLayout(this);
        testRow.setOrientation(LinearLayout.HORIZONTAL);
        testRow.setPadding(0, dp(8), 0, 0);
        MaterialButton matchBtn = new MaterialButton(this);
        matchBtn.setText("匹配测试");
        matchBtn.setOnClickListener(v -> runMatchTest());
        testRow.addView(matchBtn, weightWrap(1));
        MaterialButton dnsBtn = new MaterialButton(this);
        dnsBtn.setText("真实 DNS 测试");
        dnsBtn.setOnClickListener(v -> runRealDnsTest());
        testRow.addView(dnsBtn, weightWrap(1));
        testBox.addView(testRow, matchWrap());

        resultText = new TextView(this);
        resultText.setTextSize(14);
        resultText.setPadding(0, dp(8), 0, 0);
        resultText.setText("结果将显示在这里");
        resultText.setTextColor(0xFF1A1B1C);
        testBox.addView(resultText, matchWrap());

        testCard.addView(testBox);
        root.addView(testCard, matchWrapMarginTop(10));

        // ===== 统计卡片 =====
        MaterialCardView statCard = card();
        LinearLayout statBox = box();
        TextView statLabel = new TextView(this);
        statLabel.setText("拦截统计");
        statLabel.setTextSize(15);
        statLabel.setTypeface(Typeface.DEFAULT_BOLD);
        statLabel.setTextColor(0xFF1A1B1C);
        statBox.addView(statLabel, matchWrap());

        statsText = new TextView(this);
        statsText.setTextSize(14);
        statsText.setTextColor(0xFF1A1B1C);
        statBox.addView(statsText, matchWrap());

        MaterialButton refreshBtn = new MaterialButton(this);
        refreshBtn.setText("刷新统计");
        refreshBtn.setOnClickListener(v -> refreshStats());
        LinearLayout.LayoutParams rflp = matchWrap();
        rflp.topMargin = dp(6);
        statBox.addView(refreshBtn, rflp);

        statCard.addView(statBox);
        root.addView(statCard, matchWrapMarginTop(10));

        scroll.addView(root);
        return scroll;
    }

    // ---------- 逻辑 ----------

    private void loadRules() {
        String rules = getSharedPreferences("night_dns_rules", MODE_PRIVATE)
                .getString("rules", "||baidu.com^\n*.douyin.com\n||bytecdn.cn^");
        rulesInput.setText(rules);
    }

    private void saveRules() {
        String rules = rulesInput.getText().toString();
        getSharedPreferences("night_dns_rules", MODE_PRIVATE)
                .edit().putString("rules", rules).apply();
        // 若拦截服务运行中则重启，让新规则立即生效
        if (NightVpnService.isAuthorized(this)) {
            NightVpnService.stop(this);
            NightVpnService.start(this);
        }
        toast("规则已保存" + (NightVpnService.isAuthorized(this) ? "，拦截服务已重启" : ""));
        refreshVpnStatus();
    }

    private void refreshVpnStatus() {
        boolean authorized = NightVpnService.isAuthorized(this);
        vpnStatus.setText("VPN 授权状态：" + (authorized ? "已授权" : "未授权")
                + "\n提示：授权后点击“启动拦截”即开始拦截配置的域名。");
        authButton.setEnabled(!authorized);
        startButton.setEnabled(authorized);
    }

    private void refreshStats() {
        int total = getSharedPreferences("night_dns_stats", MODE_PRIVATE)
                .getInt("total_blocks", 0);
        statsText.setText("累计拦截域名数：" + total + " 次");
    }

    private void runMatchTest() {
        String host = testDomain.getText().toString().trim().toLowerCase();
        if (host.isEmpty()) {
            toast("请输入测试域名");
            return;
        }
        DomainMatcher matcher = new DomainMatcher();
        String rules = getSharedPreferences("night_dns_rules", MODE_PRIVATE)
                .getString("rules", "");
        DnsRuleParser.parseInto(matcher, rules);
        boolean hit = matcher.match(host);
        if (hit) {
            resultText.setText("匹配测试：域名 " + host + " 命中规则 —— 将被拦截 ❌");
            resultText.setTextColor(0xFFEA6668);
        } else {
            resultText.setText("匹配测试：域名 " + host + " 未命中规则 —— 放行 ✅");
            resultText.setTextColor(0xFF52C41A);
        }
    }

    private void runRealDnsTest() {
        final String host = testDomain.getText().toString().trim().toLowerCase();
        if (host.isEmpty()) {
            toast("请输入测试域名");
            return;
        }
        if (!NightVpnService.isAuthorized(this)) {
            toast("拦截服务未运行，请先授权并启动拦截");
            return;
        }
        resultText.setText("正在向 10.1.10.1:53 查询 " + host + " ...");
        resultText.setTextColor(0xFF1A1B1C);
        new Thread(() -> {
            try {
                InetAddress server = InetAddress.getByName(DnsQueryClient.VIRTUAL_DNS);
                DnsQueryClient.Result r = DnsQueryClient.query(server, 53, host, 3000);
                final String msg = "真实 DNS 测试 " + host + " → " + r.describe()
                        + (r.isNxdomain() ? "\n结论：拦截已在真实链路生效 ✓" : "\n结论：该域名未被拦截（被放行）");
                runOnUiThread(() -> {
                    resultText.setText(msg);
                    resultText.setTextColor(r.isNxdomain() ? 0xFFEA6668 : 0xFF52C41A);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    resultText.setText("真实 DNS 测试失败：" + e.getMessage()
                            + "\n请确认拦截服务已启动且网络可用");
                    resultText.setTextColor(0xFF6B7280);
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            refreshVpnStatus();
            toast(resultCode == RESULT_OK ? "VPN 授权成功" : "VPN 授权被取消");
        }
    }

    // ---------- UI 工具 ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardBackgroundColor(0xFFFFFFFF);
        c.setRadius(dp(14));
        c.setCardElevation(0);
        c.setStrokeWidth(0);
        return c;
    }

    private LinearLayout box() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapMarginTop(int top) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(top);
        return lp;
    }

    private LinearLayout.LayoutParams weightWrap(int weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        return lp;
    }
}
