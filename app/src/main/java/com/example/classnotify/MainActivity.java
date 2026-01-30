package com.example.classnotify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    Button btnOpen,btnTeach;
    public static final String CHANNEL_ID = "channel_v2";
    private static final String PREFS_NAME = "MonitorPrefs";
    private static final String KEY_AUTO_START_GUIDED = "hasGuidedAutoStart";
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOpen=findViewById(R.id.btn_open_blacklist);
        btnTeach=findViewById(R.id.btn_teach);

        btnOpen.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this,BlacklistActivity.class);
            startActivity(intent);
        });

        btnTeach.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this,TeachActivity.class);
            startActivity(intent);
        });

        // Java 代码
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // 1. 检查无障碍服务 (这是功能核心)
        if(!isAccessibilitySettingsOn())
        {
            Toast.makeText(this,"请开启无障碍服务",Toast.LENGTH_LONG).show();
            jumpToAccessibilitySettings();
            return;
        }

        // 2. 检查横幅通知权限
        if (!isBannerPermissionOn()) {
            // 弹一个简单的对话框告诉用户为什么要跳过去
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("关键设置未开启")
                    .setMessage("检测到您的“横幅通知”未开启，这会导致监督提醒无法弹出。请在接下来的页面中手动勾选。")
                    .setPositiveButton("去开启", (dialog, which) -> {
                        goToNotificationSettings(CHANNEL_ID); // 这里填入你的 channelId
                    })
                    .setCancelable(false)
                    .show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasGuided = prefs.getBoolean(KEY_AUTO_START_GUIDED, false);
        // 第三关：检查后台活动权限（电池优化 + 启动管理）
        if(!hasGuided){
            new AlertDialog.Builder(this)
                    .setTitle("最后一步：保活设置")
                    .setMessage("请执行以下操作：\n\n" +
                            "1. 手动搜索找到这个APP”\n" +
                            "2. 取消自动管理”\n" +
                            "3. 手动管理界面：允许后台活动")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        // 动作 A：先跳转官方电池优化
                        prefs.edit().putBoolean(KEY_AUTO_START_GUIDED, true).apply();
                        jumpToAutoStartSettings();
                    })
                    .setNegativeButton("暂时不用", (dialog, which) -> {
                        prefs.edit().putBoolean(KEY_AUTO_START_GUIDED, true).apply();
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        //如果上面两关都过了，才说明 App 处于“完全体”状态
        Toast.makeText(this, "监控服务已就绪", Toast.LENGTH_SHORT).show();
    }


    private void createNotificationChannel(){

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            NotificationChannel channel=new NotificationChannel(
                    CHANNEL_ID,
                    "横幅检测",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("用于弹出横幅提醒");
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

    }

    private void jumpToAccessibilitySettings()
    {
        Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean isAccessibilitySettingsOn()
    {
        //总开关
        int accessibilityEnabled=0;
        //包名（以及完整路径）
        final String service=getPackageName()+"/"+MyMonitorService.class.getCanonicalName();
        try {
            accessibilityEnabled=Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        }catch (Settings.SettingNotFoundException e){
            //error
        }

        if(accessibilityEnabled==1)
        {
            String settingValue=Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(settingValue!=null)
            {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    public void jumpToAutoStartSettings() {
        Intent intent = new Intent();
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();

        try {
            if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                // 这就是你说的“第一版”：直接冲击总列表
                // 即使报 -92，只要能把用户甩进这个列表页，用户就能自己找到 App 并关闭自动管理
                intent.setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"));
            } else if (manufacturer.contains("xiaomi")) {
                intent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (manufacturer.contains("oppo")) {
                intent.setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"));
            } else if (manufacturer.contains("vivo")) {
                intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else {
                // 其他品牌，直接给电池优化，简单明了
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            }
            startActivity(intent);

        } catch (Exception e) {
            // 如果暴力跳转彻底崩了（抛出异常），才走最后的总设置兜底
            Log.e("JumpDebug", "暴力跳转失败: " + e.getMessage());
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "请手动进入‘手机管家’->‘应用启动管理’", Toast.LENGTH_LONG).show();
            }
        }
    }

    //登记一个channel，后面才能判断权限是否开启
    private void goToNotificationSettings(String channelId) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 直接跳转到“休息时间提醒”这个子类别的设置界面
            intent.setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        } else {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    //查看这个权限是否开启
    private boolean isBannerPermissionOn() {
        NotificationManager manager=(NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N)
        {
            if(!manager.areNotificationsEnabled())
            {
                Log.d("TruthSearch", "发现真相：App 总通知开关是关着的！");
                return false; // 这里返回 false，才会触发弹窗
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);

            // 情况 A：经理说墙上没这账本
            if (channel == null) {
                Log.d("TruthSearch", "账本不存在，现在去创建...");
                createNotificationChannel();
                return true; // 返回 false 触发弹窗
            }
            return channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
        }
        return true;
    }

    private boolean isIgnoringBatteryOptimizations()
    {
        PowerManager pm=(PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

}