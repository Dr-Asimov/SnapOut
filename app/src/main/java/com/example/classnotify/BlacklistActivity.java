package com.example.classnotify;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlacklistActivity extends AppCompatActivity {
    private AppAdapter adapter;
    private List<AppInfo> appList=new ArrayList<>();
    private Set<String> currentBlacklist;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);
        // 1. 获取 RecyclerView 实例
        RecyclerView recyclerView=findViewById(R.id.rv_blacklist);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 2. 读取磁盘数据
        SharedPreferences prefs=getSharedPreferences("MonitorPrefs",MODE_PRIVATE);
        Set<String> savedSet=prefs.getStringSet("blacklist_pkgs",new HashSet<>());

        // 3. 读取本地已存的黑名单数据

        if(currentBlacklist==null)
        {
            currentBlacklist=new HashSet<>();
        }
        currentBlacklist.clear();
        if(savedSet!=null)
        {
            currentBlacklist.addAll(savedSet);
        }

        new Thread(()->{
            List<AppInfo> data=getInstalledApps();
            runOnUiThread(()->{
                appList=data;
                adapter=new AppAdapter(appList,currentBlacklist);
                recyclerView.setAdapter(adapter);
            });
        }).start();

    }


    public List<AppInfo> getInstalledApps()
    {
        List<AppInfo> res=new ArrayList<>();
        PackageManager pm=getPackageManager();


        List<PackageInfo> packs=pm.getInstalledPackages(0);
        for(PackageInfo p:packs)
        {
            AppInfo newInfo=new AppInfo();
            newInfo.appName=p.applicationInfo.loadLabel(pm).toString();
            newInfo.packageName=p.packageName;
            newInfo.icon=p.applicationInfo.loadIcon(pm);
            res.add(newInfo);
        }
        Log.d("AppScan", "系统返回的包总数: " + packs.size());
        return res;
    }

}
