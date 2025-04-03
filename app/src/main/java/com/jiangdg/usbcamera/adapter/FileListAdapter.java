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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileListAdapter extends BaseAdapter {
    private Context mContext;
    private List<File> mFileList;
    private LayoutInflater mInflater;
    private SimpleDateFormat mDateFormat;
    private DecimalFormat mDecimalFormat;

    public FileListAdapter(Context context, List<File> fileList) {
        this.mContext = context;
        this.mFileList = fileList;
        this.mInflater = LayoutInflater.from(context);
        this.mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        this.mDecimalFormat = new DecimalFormat("#0.00");
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
            holder.tvFileSize = convertView.findViewById(R.id.tv_file_size);
            holder.tvFileTime = convertView.findViewById(R.id.tv_file_time);
            holder.btnShare = convertView.findViewById(R.id.btn_share);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final File file = mFileList.get(position);
        holder.tvFileName.setText(file.getName());
        
        // 设置文件大小
        holder.tvFileSize.setText(formatFileSize(file.length()));
        
        // 设置文件创建时间（实际上是最后修改时间）
        holder.tvFileTime.setText(mDateFormat.format(new Date(file.lastModified())));
        
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

    /**
     * 格式化文件大小
     * @param size 文件大小（字节）
     * @return 格式化后的文件大小字符串
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return mDecimalFormat.format(size / 1024.0) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return mDecimalFormat.format(size / (1024.0 * 1024.0)) + " MB";
        } else {
            return mDecimalFormat.format(size / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
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
        TextView tvFileSize;
        TextView tvFileTime;
        ImageButton btnShare;
    }
} 