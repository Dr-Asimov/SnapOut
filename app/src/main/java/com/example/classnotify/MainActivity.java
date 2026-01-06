package com.example.classnotify;

import android.content.Intent;
import android.provider.Settings;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //检查服务是否开启，未开启则跳转设置页
        if(!isAccessibilitySettingsOn())
        {
            Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
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
}