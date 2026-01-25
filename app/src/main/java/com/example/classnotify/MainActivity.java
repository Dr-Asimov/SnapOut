package com.example.classnotify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button btnOpen,btnTeach;
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


        //检查服务是否开启，未开启则跳转设置页
        if(!isAccessibilitySettingsOn())
        {
            Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
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

        if(!isIgnoringBatteryOptimizations())
        {
            Toast.makeText(this,"请开启后台防杀设置",Toast.LENGTH_LONG).show();
            jumpToBatteryOptimizationSettings();
            return;
        }

    }
    // 【新增】跳转到电池优化设置页
    private void jumpToBatteryOptimizationSettings()
    {
        try {
            android.content.Intent intent =new android.content.Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:"+getPackageName()));
            startActivity(intent);
        }catch (Exception e)
        {
            try {
                Intent intent=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }catch (Exception e2)
            {
                Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private boolean isIgnoringBatteryOptimizations()
    {
        android.os.PowerManager pm=(android.os.PowerManager)getSystemService(Context.POWER_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
        {
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
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
                // 华为/荣耀：应用启动管理
                intent.setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"));
            } else if (manufacturer.contains("xiaomi")) {
                // 小米：自启动管理
                intent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (manufacturer.contains("oppo")) {
                // OPPO：自启动管理
                intent.setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"));
            } else if (manufacturer.contains("vivo")) {
                // Vivo：自启动管理
                intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else {
                // 其他品牌：跳转到应用详情页，引导用户手动找“耗电”或“启动”设置
                intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            // 如果特定路径失败，跳转到总设置
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            Toast.makeText(this, "未找到设置页，请手动开启‘应用启动管理’", Toast.LENGTH_LONG).show();
        }
    }
}