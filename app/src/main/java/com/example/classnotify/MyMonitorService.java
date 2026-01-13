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
import android.widget.NumberPicker;
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
    private Runnable forceExitRunnable;//打包的任务：强制退出
    private String currentPackageName="";//即用户正在浏览的APP的包名
    private View lockView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private boolean isInPunishmentMode=false;//是否处于“严厉监视”状态
    private Runnable liftPunishmentRunnable;// 解除监视的任务
    private static final int STAGE_SETTING = 0;   // 场景1：初次进入设定
    private static final int STAGE_PUNISHMENT = 1; // 场景2：游玩结束惩罚
    private int currentStage = STAGE_SETTING;
    private View timeSelectorView;
    private boolean isSessionActive=false;//标记：当前是否处于“已经批准”时间
    private boolean isUiShowing=false;

    private final List<String> BLACKLIST= Arrays.asList("tv.danmaku.bili", "com.cahx.honor");//样例黑名单，后面要改
//    private final int MINUTES = 15;
//    private final long TIME_LIMIT = MINUTES * 60 * 1000L;
    private final long TIME_LIMIT=5000;
    @Override
    public void onServiceConnected()
    {
        super.onServiceConnected();
        Log.d("Monitor","服务已连接");

        //定义任务：强制退出
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

            if(newPackageName.equals(getPackageName())||
                    newPackageName.equals("com.android.systemui")||
                    newPackageName.contains("inputmethod")){
                return;
            }


            //如果切换APP，先移除之前的计时器
            if(!newPackageName.equals(currentPackageName))
            {
                //用户切屏了
                handler.removeCallbacks(forceExitRunnable);

                if(isSessionActive&&!BLACKLIST.contains(newPackageName))
                {
                    isSessionActive=false;
                    Log.d("Monitor","切出目标应用，计时结束");
                }

                if(timeSelectorView!=null&&windowManager!=null)
                {
                    windowManager.removeView(timeSelectorView);
                    timeSelectorView=null;
                    isUiShowing=false;
                }

                currentPackageName=newPackageName;
                Log.d("Monitor","检测到当前应用："+currentPackageName);

                //检查是否在黑名单中
                if(BLACKLIST.contains(currentPackageName))
                {
                    if(isInPunishmentMode)
                    {
                        Log.d("Monitor", "监视期内违规！强制退出");
                        Toast.makeText(this,"请关注您的任务",Toast.LENGTH_LONG).show();
                        performGlobalAction(GLOBAL_ACTION_HOME);
                    }else
                    {
                        //isSessionActive存在的意义是什么
                        if(isSessionActive)
                        {
                            Log.d("Monitor","用户正在合法游戏时间内，无需操作");
                        }else
                        {
                            Log.d("Monitor","命中黑名单，弹出时间选择器...");
                            showTimeSelector();
                        }

//                        //开始倒计时
//                        handler.removeCallbacks(forceExitRunnable);
//                        //在这里设定倒计时时间，然后开始delay
//
//                        handler.postDelayed(forceExitRunnable,TIME_LIMIT);
                    }

                }
            }

        }
    }

    private void showTimeSelector()
    {
        //如果已经在显示中，直接拦截，不做重复执行逻辑
        if(isUiShowing||timeSelectorView!=null) return;//防止重复弹窗

        isUiShowing=true;//同时可以理解为：上锁，不允许再弹框
        windowManager=(WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams selectorParams=new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        timeSelectorView=LayoutInflater.from(this).inflate(R.layout.layout_time_selector,null);

        NumberPicker npPlayTime=timeSelectorView.findViewById(R.id.np_play_time);
        npPlayTime.setMinValue(1);
        npPlayTime.setMaxValue(60);
        npPlayTime.setValue(15);

        Button btnConfirm=timeSelectorView.findViewById(R.id.btn_confirm_time);
        btnConfirm.setOnClickListener(v->{
            int minutes=npPlayTime.getValue();
            long durationMillis=minutes*60*1000L;

            if(timeSelectorView!=null && windowManager != null)
            {
                windowManager.removeView(timeSelectorView);
                timeSelectorView=null;
            }

            isUiShowing=false;
            isSessionActive=true;
            Log.d("Monitor","用户设定时间："+minutes+"分钟");
            Toast.makeText(this,"开始计时："+minutes+"分钟后将强制休息",Toast.LENGTH_SHORT).show();

            handler.removeCallbacks(forceExitRunnable);
            handler.postDelayed(()->{
                isSessionActive=false;
                forceExitRunnable.run();
            },durationMillis);
        });
        Log.d("Monitor", "isUiShowing 状态: " + isUiShowing);
        Log.d("Monitor", "timeSelectorView 是否为空: " + (timeSelectorView == null));
        try{
            windowManager.addView(timeSelectorView,selectorParams);
            Log.d("Monitor","addView成功调用");
        }catch (Exception e)
        {
            isUiShowing=false;
            Log.e("Monitor","addView抛出异常："+e.getMessage());
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
        isUiShowing=false;
        isSessionActive=false;
    }

    private void startCombineLock(){
        if(lockView!=null) return;

        windowManager=(WindowManager) getSystemService(WINDOW_SERVICE);

        //初始参数：必须获得焦点以支持输入
        params=new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                LayoutParams.FLAG_LAYOUT_IN_SCREEN| WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        lockView= LayoutInflater.from(this).inflate(R.layout.layout_lock,null);

        //UI引用
        View layoutInteraction=lockView.findViewById(R.id.layout_interaction);
        EditText etGoal=lockView.findViewById(R.id.et_work_goal);
        Button btnExit=lockView.findViewById(R.id.btn_exit);




        btnExit.setOnClickListener(v -> {
            String goal=etGoal.getText().toString().trim();
            if(!goal.isEmpty())
            {
                etGoal.clearFocus();

                if(lockView!=null&&windowManager!=null)
                {
                    //移除输入框
                    windowManager.removeView(lockView);
                    lockView=null;
                }

                isInPunishmentMode=true;
                Log.d("Monitor","进入1分钟自控监视间，目标："+goal);


                performGlobalAction(GLOBAL_ACTION_HOME);//模拟Home键：返回主界面

                Log.d("Monitor","用户目标已经锁定："+goal);

                if(liftPunishmentRunnable !=null) handler.removeCallbacks(liftPunishmentRunnable);
                liftPunishmentRunnable=()->{
                    isInPunishmentMode=false;
                    Log.d("Monitor","1分钟监控结束");
                };
                handler.postDelayed(liftPunishmentRunnable,60*1000);
            }
            else
            {
                Toast.makeText(this,"请输入您的目标",Toast.LENGTH_SHORT).show();
            }
        });

        windowManager.addView(lockView,params);
    }

}
