package com.jiangdg.usbcamera.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.jiangdg.usbcamera.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.application.MyApplication;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.AspectRatioTextureView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.app.AlertDialog;
import android.widget.ListView;
import android.widget.TextView;
import java.io.FilenameFilter;
import com.jiangdg.usbcamera.adapter.FileListAdapter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * UVCCamera use demo
 * <p>
 * Created by jiangdongguo on 2017/9/30.
 */

public class USBCameraActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "Debug";
    private static final int REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    
    @BindView(R.id.camera_view)
    public View mTextureView;
    @BindView(R.id.toolbar)
    public Toolbar mToolbar;
    @BindView(R.id.btn_record)
    public Button mBtnRecord;
    @BindView(R.id.tv_record_time)
    public TextView mTvRecordTime;

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;

    private boolean isRequest;
    private boolean isPreview;
    private boolean isRecording = false;
    private List<String> mMissPermissions = new ArrayList<>();
    
    // 日期格式化
    private SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
    
    // 录制时间相关变量
    private Handler mTimerHandler = new Handler();
    private long mRecordingStartTime = 0;
    private int mRecordSeconds = 0;
    private Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                mRecordSeconds++;
                
                // 计算时、分、秒
                int hours = mRecordSeconds / 3600;
                int minutes = (mRecordSeconds % 3600) / 60;
                int seconds = mRecordSeconds % 60;
                
                // 更新UI显示
                mTvRecordTime.setText(String.format("已录制时长: %02d:%02d:%02d", hours, minutes, seconds));
                
                // 每秒更新一次
                mTimerHandler.postDelayed(this, 1000);
            }
        }
    };

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
                Log.d(TAG, "摄像头已连接: " + (device != null ? device.getDeviceName() : "未知设备") + 
                      "，正在请求权限");
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                
                // 如果正在录制，先保存录制的视频
                if (isRecording) {
                    try {
                        Log.d(TAG, "摄像头拔出，中断录制并保存视频");
                        // 停止录制
                        mCameraHelper.stopPusher();
                        
                        // 更新UI状态
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isRecording = false;
                                mBtnRecord.setText("开始录制");
                                mBtnRecord.setBackgroundResource(android.R.drawable.btn_default);
                                
                                // 停止计时器
                                mTimerHandler.removeCallbacks(mTimerRunnable);
                                mTvRecordTime.setVisibility(View.GONE);
                                
                                showShortMsg("摄像头已断开连接，录制已中断");
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "停止录制失败: " + e.getMessage(), e);
                    }
                }
                
                // 关闭相机
                mCameraHelper.closeCamera();
                isPreview = false;
                
                // 禁用录制按钮
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateRecordButtonState();
                    }
                });
                
                Log.d(TAG, "摄像头已拔出: " + (device != null ? device.getDeviceName() : "未知设备"));
                showShortMsg(device.getDeviceName() + " 已断开连接");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
                Log.e(TAG, "相机连接失败: " + (device != null ? device.getDeviceName() : "未知设备"));
            } else {
                isPreview = true;
                showShortMsg("connecting");
                Log.d(TAG, "相机连接成功: " + (device != null ? device.getDeviceName() : "未知设备") + 
                      ", VID: " + (device != null ? device.getVendorId() : "未知") + 
                      ", PID: " + (device != null ? device.getProductId() : "未知"));
                
                // 更新分辨率为1920x1080
                updateResolution(1920, 1080);
                
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                            // 不设置亮度和对比度，保持摄像头默认状态
                            Log.d(TAG, "相机已连接，保持默认亮度和对比度设置");
                            
                            // 检查预览状态
                            if (!isPreview) {
                                Log.d(TAG, "相机已连接但预览未启动，尝试启动预览");
                                if (mUVCCameraView != null && mUVCCameraView.getSurfaceTexture() != null) {
                                    try {
                                        mCameraHelper.startPreview(mUVCCameraView);
                                        isPreview = true;
                                        Log.d(TAG, "重新连接后启动预览成功");
                                    } catch (Exception e) {
                                        Log.e(TAG, "重新连接后启动预览失败: " + e.getMessage(), e);
                                    }
                                } else {
                                    Log.d(TAG, "无法启动预览，SurfaceTexture未准备好");
                                }
                            }
                            
                            // 相机已经打开，更新UI
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateRecordButtonState();
                                    showShortMsg("摄像头已连接，可以开始使用");
                                }
                            });
                        } else {
                            Log.e(TAG, "相机未打开或连接异常");
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
            // 切换预览状态为关闭
            isPreview = false;
            // 相机断开，禁用录制按钮
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnRecord.setEnabled(false);
                    mBtnRecord.setAlpha(0.5f);
                    showShortMsg("相机已断开，无法录制");
                    Log.d(TAG, "相机已断开连接: " + (device != null ? device.getDeviceName() : "未知设备"));
                }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        ButterKnife.bind(this);
        
        // 记录设备信息，有助于诊断问题
        logDeviceInfo();
        
        // 在应用启动时检查并创建视频保存目录
        createVideoSaveDirectory();
        
        // 检查权限
        if (isVersionM()) {
            checkAndRequestPermissions();
        } else {
            initView();
            initCamera();
        }
    }
    
    private boolean isVersionM() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    private void checkAndRequestPermissions() {
        mMissPermissions.clear();
        for (String permission : REQUIRED_PERMISSION_LIST) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                mMissPermissions.add(permission);
            }
        }
        // 检查是否已经授予权限
        if (mMissPermissions.isEmpty()) {
            initView();
            initCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    mMissPermissions.toArray(new String[mMissPermissions.size()]),
                    REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    mMissPermissions.remove(permissions[i]);
                }
            }
        }
        // 判断是否获得了权限
        if (mMissPermissions.isEmpty()) {
            initCamera();
        } else {
            Toast.makeText(USBCameraActivity.this, "获取权限失败，退出应用", Toast.LENGTH_SHORT).show();
            USBCameraActivity.this.finish();
        }
    }
    
    private void initCamera() {
        // 初始化UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();

        // 尝试设置更加普遍兼容的格式和分辨率
        mCameraHelper.setDefaultPreviewSize(1920, 1080); // 设置默认预览分辨率为1920x1080
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG); // 使用MJPEG格式
        
        // 初始化USB监视器
        try {
            mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
            Log.d(TAG, "USB监视器初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "USB监视器初始化失败: " + e.getMessage(), e);
        }
        
        // 记录初始化日志
        Log.d(TAG, "相机初始化完成，预览分辨率: 1920x1080, 格式: MJPEG");
    }

    private void initView() {
        setSupportActionBar(mToolbar);
        
        // 设置相机预览视图的宽高比，确保预览显示正常
        if (mUVCCameraView instanceof AspectRatioTextureView) {
            Log.d(TAG, "设置相机预览宽高比为4:3");
            ((AspectRatioTextureView) mUVCCameraView).setAspectRatio(4, 3); // 设置4:3的宽高比
        }
        
        // 初始设置录制按钮为禁用状态
        mBtnRecord.setEnabled(false);
        mBtnRecord.setAlpha(0.5f);
        showShortMsg("请先连接相机");
        
        // 初始化录制按钮
        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("抱歉，相机未打开");
                    return;
                }
                
                if (!isRecording) {
                    // 使用当前时间作为文件名
                    String fileName = mDateFormat.format(new Date());
                    
                    // 获取视频保存目录
                    File usbCameraDir = getVideoSaveDirectory();
                    // 双重检查目录是否存在
                    if (!usbCameraDir.exists()) {
                        boolean created = usbCameraDir.mkdirs();
                        if (!created) {
                            showShortMsg("无法创建视频保存目录，请检查存储权限");
                            return;
                        }
                    }
                    
                    String videoPath = usbCameraDir.getAbsolutePath() + "/" + fileName + UVCCameraHelper.SUFFIX_MP4;
                    Log.d(TAG, "开始录制视频到路径: " + videoPath);
                    
                    // 确保目录存在
                    File videoFile = new File(videoPath);
                    if (videoFile.exists()) {
                        videoFile.delete(); // 删除之前可能存在的同名文件
                    }
                    
                    try {
                        // 确保相机已打开且正在预览中
                        if (!mCameraHelper.isCameraOpened()) {
                            showShortMsg("相机未打开，无法录制");
                            return;
                        }
                        
                        if (!isPreview) {
                            showShortMsg("预览未启动，无法录制");
                            return;
                        }
                        
                        // 开始录制
                        RecordParams params = new RecordParams();
                        params.setRecordPath(videoPath);
                        params.setRecordDuration(0); // 不限制录制时长
                        params.setVoiceClose(false); // 开启声音录制可能有助于解决问题
                        params.setSupportOverlay(false);
                        
                        Log.d(TAG, "开始录制参数: 路径=" + videoPath + 
                               ", 格式=" + UVCCameraHelper.SUFFIX_MP4 + 
                               ", 关闭声音=" + params.isVoiceClose());
                        
                        mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                            @Override
                            public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                                // 添加编码数据日志
                                if (data != null && length > 0) {
                                    Log.v(TAG, "接收到编码数据: 类型=" + type + ", 长度=" + length + ", 时间戳=" + timestamp);
                                }
                            }

                            @Override
                            public void onRecordResult(String videoPath) {
                                if(TextUtils.isEmpty(videoPath)) {
                                    Log.e(TAG, "录制结果回调中视频路径为空");
                                    return;
                                }
                                
                                // 检查文件是否存在及大小
                                File recordedFile = new File(videoPath);
                                if (recordedFile.exists()) {
                                    Log.d(TAG, "录制完成，文件大小: " + recordedFile.length() + " bytes");
                                    if (recordedFile.length() <= 0) {
                                        Log.e(TAG, "录制的文件大小为0，可能录制失败");
                                    }
                                } else {
                                    Log.e(TAG, "录制的文件不存在");
                                }
                                
                                Log.d(TAG, "录制完成，保存到: " + videoPath);
                                new Handler(getMainLooper()).post(() -> 
                                    Toast.makeText(USBCameraActivity.this, "视频保存路径: " + videoPath, Toast.LENGTH_SHORT).show());
                            }
                        });
                        
                        isRecording = true;
                        mBtnRecord.setText("结束录制");
                        // 设置录制按钮为红色
                        mBtnRecord.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                        
                        // 显示录制时间
                        mTvRecordTime.setVisibility(View.VISIBLE);
                        // 重置计时器
                        mRecordSeconds = 0;
                        mTvRecordTime.setText("已录制时长: 00:00:00");
                        // 启动计时器
                        mTimerHandler.removeCallbacks(mTimerRunnable);
                        mTimerHandler.post(mTimerRunnable);
                        
                        showShortMsg("开始录制...");
                    } catch (Exception e) {
                        Log.e(TAG, "录制过程发生异常: " + e.getMessage(), e);
                        showShortMsg("录制出错: " + e.getMessage());
                    }
                } else {
                    // 停止录制
                    try {
                        // FileUtils.releaseFile(); // 移除对FileUtils的调用
                        mCameraHelper.stopPusher();
                        Log.d(TAG, "停止录制");
                        
                        isRecording = false;
                        mBtnRecord.setText("开始录制");
                        // 恢复按钮默认颜色
                        mBtnRecord.setBackgroundResource(android.R.drawable.btn_default);
                        
                        // 停止计时器
                        mTimerHandler.removeCallbacks(mTimerRunnable);
                        // 隐藏录制时间
                        mTvRecordTime.setVisibility(View.GONE);
                        
                        showShortMsg("录制完成，点击右上角查看文件");
                    } catch (Exception e) {
                        Log.e(TAG, "停止录制过程发生异常: " + e.getMessage(), e);
                        showShortMsg("停止录制出错: " + e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toobar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open_folder) {
            openVideoFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // FileUtils.releaseFile(); // 移除对FileUtils的调用
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
        
        // 清理计时器资源
        if (mTimerHandler != null) {
            mTimerHandler.removeCallbacks(mTimerRunnable);
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    public boolean isCameraOpened() {
        return mCameraHelper.isCameraOpened();
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        Log.d(TAG, "onSurfaceCreated: 开始创建Surface");
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            Log.d(TAG, "开始预览：相机已打开，预览标志为false");
            try {
                // 在启动预览前确保Surface有效
                if (surface != null && surface.isValid()) {
                    mCameraHelper.startPreview(mUVCCameraView);
                    isPreview = true;
                    Log.d(TAG, "预览启动成功");
                    
                    // 预览开始后，使用动态亮度设置
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                                // 尝试调整不同的亮度值
                                try {
                                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 75);
                                    Log.d(TAG, "Surface创建后设置亮度值: 75");
                                } catch (Exception e) {
                                    Log.e(TAG, "设置亮度失败: " + e.getMessage());
                                }
                            } else {
                                Log.e(TAG, "相机已关闭或未初始化，无法设置亮度");
                            }
                        }
                    }, 500); // 延迟500毫秒，确保预览已经正常启动
                } else {
                    Log.e(TAG, "Surface无效，无法启动预览");
                    showShortMsg("摄像头预览区域未准备好，请重启应用");
                }
            } catch (Exception e) {
                Log.e(TAG, "启动预览失败: " + e.getMessage(), e);
                isPreview = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showShortMsg("预览启动失败，请检查相机兼容性");
                    }
                });
            }
        } else {
            Log.d(TAG, "不启动预览：相机打开状态=" + mCameraHelper.isCameraOpened() + "，预览状态=" + isPreview);
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }

    /**
     * 打开视频保存文件夹
     */
    private void openVideoFolder() {
        File usbCameraDir = getVideoSaveDirectory();
        if (!usbCameraDir.exists() || !usbCameraDir.isDirectory()) {
            showShortMsg("文件夹不存在，将创建文件夹");
            boolean created = usbCameraDir.mkdirs();
            if (!created) {
                showShortMsg("无法创建文件夹，请检查权限");
                return;
            }
        }
        
        // 显示视频文件列表对话框
        showVideoFilesDialog(usbCameraDir);
    }
    
    /**
     * 显示视频文件列表对话框
     * @param directory 视频文件所在目录
     */
    private void showVideoFilesDialog(File directory) {
        // 获取目录中的所有MP4文件
        File[] files = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".mp4");
            }
        });
        
        // 创建对话框视图
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_file_list, null);
        ListView listView = dialogView.findViewById(R.id.list_files);
        TextView emptyView = dialogView.findViewById(R.id.tv_empty_files);
        Button btnOpenFolder = dialogView.findViewById(R.id.btn_open_folder);
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        
        final AlertDialog dialog = builder.create();
        
        // 设置打开文件夹按钮点击事件
        btnOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 使用系统文件管理器打开文件夹
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = FileProvider.getUriForFile(
                            USBCameraActivity.this,
                            getPackageName() + ".provider",
                            directory);
                    intent.setDataAndType(uri, "resource/folder");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                    
                    // 关闭对话框
                    dialog.dismiss();
                } catch (Exception e) {
                    Log.e(TAG, "打开文件夹失败: " + e.getMessage(), e);
                    showShortMsg("无法打开文件夹: " + e.getMessage());
                    
                    // 尝试使用另一种方式打开
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        Uri uri = Uri.parse("file://" + directory.getAbsolutePath());
                        intent.setDataAndType(uri, "*/*");
                        startActivity(intent);
                        dialog.dismiss();
                    } catch (Exception ex) {
                        Log.e(TAG, "备用方式打开文件夹也失败: " + ex.getMessage(), ex);
                        showShortMsg("无法打开文件夹，请手动浏览文件位置: " + directory.getAbsolutePath());
                    }
                }
            }
        });
        
        if (files != null && files.length > 0) {
            // 将文件数组转为列表
            List<File> fileList = new ArrayList<>(Arrays.asList(files));
            
            // 按照修改时间排序（最新的在前面）
            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
            
            // 创建适配器并设置给ListView
            FileListAdapter adapter = new FileListAdapter(this, fileList);
            listView.setAdapter(adapter);
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        } else {
            // 没有文件时显示空视图
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
        
        // 显示对话框
        dialog.show();
    }

    /**
     * 创建视频保存目录
     */
    private void createVideoSaveDirectory() {
        File usbCameraDir = new File(FileUtils.ROOT_PATH, "usbcamera");
        if (!usbCameraDir.exists()) {
            boolean created = usbCameraDir.mkdirs();
            if (created) {
                Log.d(TAG, "视频保存目录创建成功: " + usbCameraDir.getAbsolutePath());
            } else {
                Log.e(TAG, "视频保存目录创建失败: " + usbCameraDir.getAbsolutePath());
                showShortMsg("无法创建视频保存目录，请检查存储权限");
            }
        } else {
            Log.d(TAG, "视频保存目录已存在: " + usbCameraDir.getAbsolutePath());
        }
    }
    
    /**
     * 获取视频保存目录路径
     * @return 保存目录文件对象
     */
    private File getVideoSaveDirectory() {
        return new File(FileUtils.ROOT_PATH, MyApplication.VIDEO_DIRECTORY_NAME);
    }

    /**
     * 更新录制按钮状态
     */
    private void updateRecordButtonState() {
        boolean cameraReady = mCameraHelper != null && mCameraHelper.isCameraOpened();
        
        // 只有当相机已连接时按钮才可用
        boolean enabled = cameraReady;
        
        mBtnRecord.setEnabled(enabled);
        mBtnRecord.setAlpha(enabled ? 1.0f : 0.5f);
        
        if (!cameraReady) {
            showShortMsg("请先连接相机");
        } else {
            showShortMsg("现在可以开始录制了");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: 活动恢复");
        
        // 检查相机状态
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mCameraHelper != null) {
                    if (mCameraHelper.isCameraOpened() && isPreview) {
                        // 相机已打开且在预览中，设置亮度
                        try {
                            mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 75);
                            Log.d(TAG, "Activity恢复后设置亮度值: 75");
                        } catch (Exception e) {
                            Log.e(TAG, "设置亮度失败: " + e.getMessage());
                        }
                    } else if (mCameraHelper.isCameraOpened() && !isPreview) {
                        // 相机已打开但未预览，尝试启动预览
                        Log.d(TAG, "相机已打开但没有预览，尝试启动预览");
                        if (mUVCCameraView != null) {
                            try {
                                // 强制设置TextureView宽高比
                                if (mUVCCameraView instanceof AspectRatioTextureView) {
                                    ((AspectRatioTextureView) mUVCCameraView).setAspectRatio(4, 3); // 4:3宽高比
                                }
                                
                                if (mUVCCameraView.getSurfaceTexture() != null) {
                                    mCameraHelper.startPreview(mUVCCameraView);
                                    isPreview = true;
                                    Log.d(TAG, "onResume中重新启动预览成功");
                                } else {
                                    Log.d(TAG, "SurfaceTexture为空，无法启动预览");
                                    // 尝试重新创建TextureView
                                    mUVCCameraView.onResume();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "onResume中启动预览失败: " + e.getMessage(), e);
                            }
                        }
                    } else if (!mCameraHelper.isCameraOpened()) {
                        // 相机未打开，检查是否有设备连接
                        Log.d(TAG, "相机未打开，检查是否有USB设备连接");
                        // 注册USB监听器以检测设备
                        mCameraHelper.registerUSB();
                    }
                    
                    // 更新录制按钮状态
                    updateRecordButtonState();
                }
            }
        }, 500);
    }

    /**
     * 记录设备信息，帮助诊断问题
     */
    private void logDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("设备信息：\n");
        sb.append("型号: ").append(Build.MODEL).append("\n");
        sb.append("厂商: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("SDK版本: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("屏幕密度: ").append(getResources().getDisplayMetrics().density).append("\n");
        sb.append("屏幕分辨率: ")
          .append(getResources().getDisplayMetrics().widthPixels).append("x")
          .append(getResources().getDisplayMetrics().heightPixels);
        
        Log.d(TAG, sb.toString());
    }

    /**
     * 更新视频分辨率
     * @param width 宽度
     * @param height 高度
     */
    private void updateResolution(final int width, final int height) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraHelper != null) {
                    try {
                        mCameraHelper.updateResolution(width, height);
                        Log.d(TAG, "视频分辨率已更新为: " + width + "x" + height);
                        
                        // 更新预览的宽高比
                        if (mUVCCameraView instanceof AspectRatioTextureView) {
                            ((AspectRatioTextureView) mUVCCameraView).setAspectRatio(width, height);
                            Log.d(TAG, "更新预览宽高比为: " + width + ":" + height);
                        }
                        
                        showShortMsg("视频分辨率已设置为: " + width + "x" + height);
                    } catch (Exception e) {
                        Log.e(TAG, "更新分辨率失败: " + e.getMessage(), e);
                        showShortMsg("设置分辨率失败");
                    }
                }
            }
        });
    }
}
