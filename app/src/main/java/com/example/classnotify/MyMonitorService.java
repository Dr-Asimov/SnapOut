package com.example.classnotify;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import java.util.Arrays;
import java.util.List;
import android.view.WindowManager;
// 必须精准导入 WindowManager 下的 LayoutParams
import android.view.WindowManager.LayoutParams;

import org.w3c.dom.Text;

import java.util.logging.LogRecord;

public class MyMonitorService extends AccessibilityService {
    private Handler handler=new Handler(Looper.getMainLooper());
    private Runnable forceExitRunnable;//指挥官
    private String currentPackageName="";//即用户正在浏览的APP的包名
    private View lockView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;

    private final List<String> BLACKLIST= Arrays.asList("tv.danmaku.bili");//样例黑名单，后面要改
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
                    //performGlobalAction(GLOBAL_ACTION_HOME);
                    //进阶
                    startCombineLock();
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

            if(newPackageName.equals("com.android.systemui")||
            newPackageName.contains("inputmethod")){
                return;
            }


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
        if(lockView!=null && windowManager !=null)
        {
            windowManager.removeView(lockView);
            lockView=null;
        }
    }

    private void startCombineLock(){
        if(lockView!=null) return;

        windowManager=(WindowManager) getSystemService(WINDOW_SERVICE);

        //初始参数：必须获得焦点以支持输入
        params=new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        lockView= LayoutInflater.from(this).inflate(R.layout.layout_lock,null);

        //UI引用
        View layoutInteraction=lockView.findViewById(R.id.layout_interaction);
        EditText etGoal=lockView.findViewById(R.id.et_work_goal);
        Button btnConfirm=lockView.findViewById(R.id.btn_confirm);

        btnConfirm.setOnClickListener(v -> {
            String goal=etGoal.getText().toString().trim();
            if(!goal.isEmpty())
            {
                etGoal.clearFocus();

                if(lockView!=null&&windowManager!=null)
                {
                    windowManager.removeView(lockView);
                    lockView=null;
                }

                performGlobalAction(GLOBAL_ACTION_HOME);

                Log.d("Monitor","用户目标已经锁定："+goal);

            }
            else
            {
                Toast.makeText(this,"请输入您的目标",Toast.LENGTH_SHORT).show();
            }
        });

        windowManager.addView(lockView,params);
    }

}
