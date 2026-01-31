package com.example.classnotify;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
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
    private RecyclerView recyclerView; // 提到成员变量
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);


        btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(); // 销毁当前 Activity，返回上一个界面
                }
            });
        }else {
            // 如果这里还是 null，说明 XML 里的 ID 没有写对，或者 ID 不叫 btn_back
            Log.e("Error", "找不到 ID 为 btn_back 的控件");
        }

        progressBar=findViewById(R.id.progress_loader);
        // 1. 获取 RecyclerView 实例
        recyclerView=findViewById(R.id.rv_blacklist);
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

        // Java 代码
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }


    }

    private void loadAppData()
    {
        if (progressBar.getVisibility() == View.VISIBLE) return;
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);

        new Thread(()->{
            List<AppInfo> data=getInstalledApps(progress -> {
                runOnUiThread(()->progressBar.setProgress(progress));
            });

            runOnUiThread(()->{
                appList=data;
                adapter = new AppAdapter(appList, currentBlacklist);
                recyclerView.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);

                if (appList.size() > 1) {
                    Log.d("AppListSearch", "加载成功，数量：" + appList.size());
                }
            });


        }).start();
    }

    public List<AppInfo> getInstalledApps(ProgressListener listener)
    {
        List<AppInfo> res=new ArrayList<>();
        PackageManager pm=getPackageManager();
        List<PackageInfo> packs=pm.getInstalledPackages(0);
        Log.d("AppListSearch", "查询到的应用总数: " + packs.size());
        int total=packs.size();

        for(int i=0;i<total;i++)
        {
            PackageInfo p=packs.get(i);

            // 【核心逻辑】：判断是否为系统应用
            // 如果 (flags & FLAG_SYSTEM) != 0，说明它是系统应用
            boolean isSystemApp = (p.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!isSystemApp) {
                AppInfo newInfo = new AppInfo();
                newInfo.appName = p.applicationInfo.loadLabel(pm).toString();
                newInfo.packageName = p.packageName;
                newInfo.icon = p.applicationInfo.loadIcon(pm);
                res.add(newInfo);
            }
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

    @Override
    protected void onResume(){
        super.onResume();

        if(appList==null||appList.size()<=1){
            Log.d("AppListSearch", "onResume: 检测到列表未就绪或受限，尝试加载...");
            loadAppData();
        }
    }
}
