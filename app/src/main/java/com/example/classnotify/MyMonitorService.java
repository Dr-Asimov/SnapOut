package com.example.classnotify;

import android.accessibilityservice.AccessibilityService;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import android.os.Handler;
import java.util.Arrays;
import java.util.List;

import java.util.logging.LogRecord;

public class MyMonitorService extends AccessibilityService {
    private Handler handler=new Handler(Looper.getMainLooper());
    private Runnable forceExitRunnable;//指挥官
    private String currentPackageName="";//即用户正在浏览的APP的包名

    private final List<String> BLACKLIST= Arrays.asList("com.ss.android.u");//样例黑名单，后面要改
    private final long TIME_LIMIT=5000;
    @Override
    public void onServiceConnected()
    {
        super.onServiceConnected();
        Log.d("Monitor","服务已连接");

        //定义超时后动作
        forceExitRunnable=new Runnable() {
            @Override
            public void run() {
                if(BLACKLIST.contains(currentPackageName)){
                    Toast.makeText(getApplicationContext(),"使用时间超限，强制休息!",Toast.LENGTH_LONG).show();
                    //核心动作：模拟点击Home键，实现“强制退出”视觉效果
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    //进阶后面再考虑
                }
            }
        };


    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        {
            String newPackageName=event.getPackageName().toString();

            //如果切换APP，先移除之前的计时器
            if(!newPackageName.equals(currentPackageName))
            {
                //用户切屏了
                handler.removeCallbacks(forceExitRunnable);
                currentPackageName=newPackageName;

                Log.d("Monitor","检测到当前应用："+currentPackageName);

                //检查是否在黑名单中
                if(BLACKLIST.contains(currentPackageName))
                {
                    Log.d("Monitor","命中黑名单，开始5秒倒计时...");
                    //开始5秒倒计时
                    handler.postDelayed(forceExitRunnable,TIME_LIMIT);
                }
            }

        }
    }

    @Override
    public void onInterrupt()
    {
        //服务被异常中断时的处理
        handler.removeCallbacks(forceExitRunnable);
    }


}
