package com.example.classnotify;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.widget.SearchView;

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
    private SearchView searchView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);

        progressBar=findViewById(R.id.progress_loader);

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

        searchView=findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {

                if(adapter!=null)
                {
                    adapter.getFilter().filter(newText);
                }
                return true;
            }
        });


        loadAppData(recyclerView);
    }

    private void loadAppData(RecyclerView recyclerView)
    {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);

        new Thread(()->{
            List<AppInfo> data=getInstalledApps(progress -> {
                runOnUiThread(()->progressBar.setProgress(progress));
            });

            runOnUiThread(()->{
                appList=data;
                adapter=new AppAdapter(appList,currentBlacklist);
                recyclerView.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);
            });

        }).start();
    }

    public List<AppInfo> getInstalledApps(ProgressListener listener)
    {
        List<AppInfo> res=new ArrayList<>();
        PackageManager pm=getPackageManager();
        List<PackageInfo> packs=pm.getInstalledPackages(0);
        int total=packs.size();

        for(int i=0;i<total;i++)
        {
            PackageInfo p=packs.get(i);
            AppInfo newInfo=new AppInfo();
            newInfo.appName = p.applicationInfo.loadLabel(pm).toString();
            newInfo.packageName = p.packageName;
            newInfo.icon = p.applicationInfo.loadIcon(pm);
            res.add(newInfo);

            if(listener!=null)
            {
                int progress=(int) (((float) (i + 1) / total) * 100);
                listener.onProgressUpdate(progress);
            }
        }
        //Log.d("AppScan", "系统返回的包总数: " + packs.size());
        return res;
    }

    public interface ProgressListener{
        void onProgressUpdate(int progress);
    }

}
