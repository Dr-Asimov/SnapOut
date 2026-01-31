package com.example.classnotify;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
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
import java.util.HashSet;
import java.util.List;
import android.view.WindowManager;
// 必须精准导入 WindowManager 下的 LayoutParams
import android.view.WindowManager.LayoutParams;

import androidx.core.app.NotificationCompat;

import org.w3c.dom.Text;

import java.util.Set;
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
    // 删掉之前的 List<String> BLACKLIST = ...
    private Set<String> dynamicBlacklist = new HashSet<>();
    //    private final int MINUTES = 15;
//    private final long TIME_LIMIT = MINUTES * 60 * 1000L;
    private final long TIME_LIMIT=5000;


    @Override
    public void onServiceConnected()
    {
        super.onServiceConnected();
        Log.d("Monitor","服务已连接");
        refreshBlacklist(); // 服务一启动，先从硬盘抄一遍名单
        Log.d("Monitor", "服务启动，初始名单长度：" + dynamicBlacklist.size());

        String channelId="class_ambition_channel";
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            android.app.NotificationChannel channel=new android.app.NotificationChannel(
                    channelId,
                    "防沉迷",
                    NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager manager=getSystemService(android.app.NotificationManager.class);
            if(manager != null)
            {
                manager.createNotificationChannel(channel);
            }
        }
        android.app.Notification notification=null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification=new Notification.Builder(this,channelId)
                    .setContentTitle("自律检察官监督中")
                    .setContentText("正在为您监督")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .build();
        }
        if(notification!=null)
        {
            startForeground(101,notification);
            Log.d("Monitor","已经进入前台服务");
        }


        //定义任务：强制退出
        forceExitRunnable=new Runnable() {
            @Override
            public void run() {
                if(dynamicBlacklist.contains(currentPackageName)){
                    isSessionActive = false; // 【重要】计时结束，通行证立刻作废
                    Toast.makeText(getApplicationContext(),"使用时间超限，强制休息!",Toast.LENGTH_LONG).show();
                    //发送休息时间通知
                    sendRestTimeNotification();
                    //进阶
                    startCombineLock();
                }
            }
        };


    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        //如果黑名单因为某些意外空了，刷新它
        if (dynamicBlacklist.isEmpty()) {
            refreshBlacklist();
        }

        String newPackageName = (event.getPackageName() != null) ? event.getPackageName().toString() : "";
        Log.d("MonitorCheck", "--- 收到新事件 ---");
        Log.d("MonitorCheck", "当前检测到包名: [" + newPackageName + "]");
        // 【第一步：排除干扰项】
        if (newPackageName.isEmpty() ||
                newPackageName.equals(getPackageName()) ||
                newPackageName.equals("com.example.ambition") || // 显式排除你的包名
                newPackageName.equals("android") ||                 // <--- 【必加】防止搜索框干扰
                newPackageName.equals("com.android.systemui") ||    // <--- 【必加】防止下拉栏干扰
                newPackageName.contains("inputmethod") ||
                newPackageName.contains("baidu.input")) {
            Log.d("MonitorCheck", "跳过处理：属于系统UI、输入法或应用自身");
            return;
        }
        // 2、保安：在禁闭期&&点开黑名单，直接踢走，不走后面的查票流程
        if(isInPunishmentMode&&dynamicBlacklist.contains(newPackageName))
        {
            Log.d("MonitorCheck", "【保安拦截】禁闭中，强制踢回桌面！");
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }

        // 3、检票员：
        if (!newPackageName.equals(currentPackageName)) {
            Log.d("MonitorCheck", "检测到应用切换: " + currentPackageName + " -> " + newPackageName);

            // 先存一下旧包名，用来判断是不是“离场”
            String oldPackageName = currentPackageName;
            currentPackageName = newPackageName;

            boolean inBlacklist = dynamicBlacklist.contains(newPackageName);

            if (inBlacklist) {
                //当前APP是黑名单应用
                if (isSessionActive) {
                    Log.d("MonitorCheck", "【检票放行】有票，随便玩");
                } else {
                    Log.d("MonitorCheck", "【检票拦截】没票，去选时间！");
                    showTimeSelector();
                }
            } else {
                // 切到了非黑名单应用
                if (dynamicBlacklist.contains(oldPackageName)) {
                    Log.d("MonitorCheck", "【检票收票】离开黑名单，注销通行证");
                    isSessionActive = false;
                    handler.removeCallbacks(forceExitRunnable); // 停止还没跑完的强制退出任务
                }

                if (isUiShowing) {
                    Log.d("MonitorCheck", "用户没选时间就跑了，关闭滚轮弹窗");
                    if(timeSelectorView != null && windowManager != null) {
                        windowManager.removeView(timeSelectorView);
                        timeSelectorView = null;
                        isUiShowing = false;
                    }
                }
            }
        } else {
            Log.d("MonitorCheck", "包名没变，保持现状");
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
        npPlayTime.setMinValue(0);
        npPlayTime.setMaxValue(60);
        npPlayTime.setValue(0);

        Button btnConfirm=timeSelectorView.findViewById(R.id.btn_confirm_time);
        btnConfirm.setOnClickListener(v->{
            int minutes=npPlayTime.getValue();
            long durationMillis;

            if(minutes==0)
            {
                durationMillis=3000L;
                Log.d("Monitor","检测到设定为0分钟，进入开发模式：3秒");
                Toast.makeText(this, "测试模式：3秒后将强制休息", Toast.LENGTH_SHORT).show();
            }else {
                durationMillis=minutes*60*1000L;
                Toast.makeText(this, "已设定游玩时间：" + minutes + "分钟", Toast.LENGTH_SHORT).show();
            }

            //long durationMillis=5000L;//测试:用的5秒
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

    private void sendRestTimeNotification()
    {
        String channelId="channel";
        NotificationManager manager=(NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if(manager==null)return;

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            NotificationChannel channel=new NotificationChannel(
                    channelId,
                    "休息时间提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("用于提醒用户休息和完成任务");
            channel.enableLights(true);
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,channelId)
                .setContentTitle("休息时间提醒")
                .setContentText("咕咕咕！请完成你的任务！")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);;

        Log.d("Monitor", "尝试发送通知：ID=102, Channel=" + channelId);
        manager.notify(102,builder.build());

    }


    private void startCombineLock() {
        if (lockView != null) return;

        performGlobalAction(GLOBAL_ACTION_HOME);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 2. 延时渲染 UI（给系统动画留出时间）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            executeRealRender();
        }, 500); // 延时 500 毫秒
    }

    private void executeRealRender() {
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
    //把读取硬盘的操作封装
    private void refreshBlacklist() {

        SharedPreferences prefs = getSharedPreferences("MonitorPrefs", MODE_PRIVATE);
        Set<String> savedSet = prefs.getStringSet("blacklist_pkgs", new HashSet<>());
        dynamicBlacklist.clear();
        dynamicBlacklist.addAll(savedSet);
        Log.d("Monitor", "已刷新黑名单，当前包含：" + dynamicBlacklist.toString());
    }
}
