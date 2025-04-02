package com.jiangdg.usbcamera.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.jiangdg.usbcamera.R;

import java.io.File;
import java.util.List;

public class FileListAdapter extends BaseAdapter {
    private Context mContext;
    private List<File> mFileList;
    private LayoutInflater mInflater;

    public FileListAdapter(Context context, List<File> fileList) {
        this.mContext = context;
        this.mFileList = fileList;
        this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return mFileList != null ? mFileList.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mFileList != null ? mFileList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_file, null);
            holder = new ViewHolder();
            holder.tvFileName = convertView.findViewById(R.id.tv_file_name);
            holder.btnShare = convertView.findViewById(R.id.btn_share);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final File file = mFileList.get(position);
        holder.tvFileName.setText(file.getName());
        
        holder.btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareFile(file);
            }
        });
        
        // 设置整个列表项的点击事件，点击也可以分享
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareFile(file);
            }
        });

        return convertView;
    }

    private void shareFile(File file) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            Uri fileUri;
            
            // 根据Android版本使用不同的方式获取Uri
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                fileUri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".provider",
                        file);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                fileUri = Uri.fromFile(file);
            }
            
            // 设置分享类型和数据
            shareIntent.setType("video/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            
            // 启动分享意图
            mContext.startActivity(Intent.createChooser(shareIntent, "分享视频"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void updateData(List<File> fileList) {
        this.mFileList = fileList;
        notifyDataSetChanged();
    }

    private class ViewHolder {
        TextView tvFileName;
        ImageButton btnShare;
    }
} 