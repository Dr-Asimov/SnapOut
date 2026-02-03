package com.example.classnotify;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> implements Filterable {
    private List<AppInfo> applist;
    private List<AppInfo> appListFull;
    private Set<String> mBlacklist;

    public AppAdapter(List<AppInfo> data,Set<String> blacklist)
    {
        this.applist=data;
        this.appListFull=new ArrayList<>(applist);
        this.mBlacklist=blacklist;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int viewType)
    {
        View view=LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder,int position)
    {
        AppInfo app=applist.get(position);
        holder.tvName.setText(app.appName);
        holder.ivIcon.setImageDrawable(app.icon);

        //1.先解绑监听器，防止初始化状态时干扰
        holder.appSwitch.setOnCheckedChangeListener(null);

        //2.设置开关状态(根据当前黑名单)
        boolean isBlacklisted=mBlacklist.contains(app.packageName);
        holder.appSwitch.setChecked(isBlacklisted);

        //3.【核心修改】给整行（itemView）设置点击监听
//        holder.itemView.setOnClickListener(v -> {
//            boolean newCheckedState=!holder.appSwitch.isChecked();
//            holder.appSwitch.setChecked(newCheckedState);
//
//            if(newCheckedState)
//            {
//                mBlacklist.add(app.packageName);
//            }else{
//                mBlacklist.remove(app.packageName);
//            }
//
//            Log.d("AppAdapter", "通过整行点击 - " + app.appName + " 新状态: " + newCheckedState);
//        });

        holder.appSwitch.setOnClickListener(v->{
            boolean isChecked=holder.appSwitch.isChecked();
            if(isChecked) mBlacklist.add(app.packageName);
            else mBlacklist.remove(app.packageName);

            // 保存到正确的SharedPreferences文件和键名
            boolean success=v.getContext().getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE)
                            .edit().putStringSet("blacklist_pkgs",new HashSet<>(mBlacklist))
                            .commit();

            Log.d("AppAdapter", "直接点击开关 - " + app.appName + " 状态: " + isChecked);
            Log.d("AppDebug", "当前内存集合内容: " + mBlacklist.toString());
            Log.d("AppDebug","硬盘写入结果："+success);
        });

    }

    @Override
    public int getItemCount()
    {
        return applist!=null?applist.size():0;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder{
        ImageView ivIcon;
        TextView tvName;
        androidx.appcompat.widget.SwitchCompat appSwitch;

        public ViewHolder(@NonNull View itemView)
        {
            super(itemView);

            ivIcon=itemView.findViewById(R.id.iv_icon);
            tvName=itemView.findViewById(R.id.tv_name);
            appSwitch=itemView.findViewById(R.id.app_switch);
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<AppInfo> filteredList = new ArrayList<>();
                FilterResults results = new FilterResults();

                if (constraint == null || constraint.length() == 0) {
                    //此时搜索框是空的
                    filteredList.addAll(appListFull);
                } else {
                    //获得用户的输入信息（大小写、空格误触优化）
                    String filterPattern = constraint.toString().toLowerCase().trim();

                    for (AppInfo item : appListFull) {
                        if (item.appName.toLowerCase().contains(filterPattern) ||
                                item.packageName.toLowerCase().contains(filterPattern)) {
                            filteredList.add(item);
                        }
                    }
                }
                    results.values = filteredList;
                    results.count = filteredList.size();
                    return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

                if (results.values != null) {
                    applist.clear();
                    applist.addAll((List) results.values);
                    notifyDataSetChanged();
                }
            }
        };
    }

}
