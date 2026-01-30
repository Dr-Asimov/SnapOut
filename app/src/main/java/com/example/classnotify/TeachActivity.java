package com.example.classnotify;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class TeachActivity extends AppCompatActivity {

    ImageButton btnBack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //继承的是 AppCompatActivity，用这行隐藏系统自带的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // 1. 绑定布局文件（对应你刚才修改的那个 XML）
        setContentView(R.layout.activity_teach);

        // 2. 初始化返回按钮
        btnBack= findViewById(R.id.btn_teach_back);

        // 3. 实现点击事件：点击 "<-" 直接关闭当前页面，回到上一层
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 执行销毁动作，相当于按了手机物理返回键

                finish();
            }
        });
    }

}
