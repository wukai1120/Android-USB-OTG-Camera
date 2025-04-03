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
    @BindView(R.id.et_file_name)
    public EditText mEtFileName;
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
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
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
                            
                            // 相机已经打开，更新UI
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateRecordButtonState();
                                }
                            });
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
                }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        ButterKnife.bind(this);
        
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
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
        
        // 记录初始化日志，不设置亮度和对比度
        Log.d(TAG, "Camera initialized, keeping default camera settings");
    }

    private void initView() {
        setSupportActionBar(mToolbar);
        
        // 初始设置录制按钮为禁用状态并添加提示
        mBtnRecord.setEnabled(false);
        mBtnRecord.setAlpha(0.5f);
        showShortMsg("请先输入文件名点击开始录制");
        
        // 添加文本变化监听器
        mEtFileName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要实现
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 当文本变化时，更新按钮状态
                updateRecordButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 不需要实现
            }
        });
        
        // 初始化录制按钮
        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("抱歉，相机未打开");
                    return;
                }
                
                if (!isRecording) {
                    String fileName = mEtFileName.getText().toString().trim();
                    if (TextUtils.isEmpty(fileName)) {
                        showShortMsg("请输入文件名");
                        return;
                    }
                    
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
                    
                    // 移除直接创建文件的部分，让MediaMuxer来处理文件创建
                    // FileUtils.createfile(videoPath);
                    
                    // 开始录制
                    RecordParams params = new RecordParams();
                    params.setRecordPath(videoPath);
                    params.setRecordDuration(0);
                    params.setVoiceClose(true);
                    params.setSupportOverlay(false);
                    
                    mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                        @Override
                        public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                            // 移除直接写入文件的操作，由MediaMuxer处理
                            // if (type == 1) {
                            //     FileUtils.putFileStream(data, offset, length);
                            // }
                        }

                        @Override
                        public void onRecordResult(String videoPath) {
                            if(TextUtils.isEmpty(videoPath)) {
                                return;
                            }
                            new Handler(getMainLooper()).post(() -> 
                                Toast.makeText(USBCameraActivity.this, "视频保存路径: " + videoPath, Toast.LENGTH_SHORT).show());
                        }
                    });
                    
                    isRecording = true;
                    mBtnRecord.setText("结束录制");
                    // 设置录制按钮为红色
                    mBtnRecord.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                    // 禁用文件名输入框
                    mEtFileName.setEnabled(false);
                    
                    // 显示录制时间
                    mTvRecordTime.setVisibility(View.VISIBLE);
                    // 重置计时器
                    mRecordSeconds = 0;
                    mTvRecordTime.setText("已录制时长: 00:00:00");
                    // 启动计时器
                    mTimerHandler.removeCallbacks(mTimerRunnable);
                    mTimerHandler.post(mTimerRunnable);
                    
                    showShortMsg("开始录制...");
                } else {
                    // 停止录制
                    // FileUtils.releaseFile(); // 移除对FileUtils的调用
                    mCameraHelper.stopPusher();
                    
                    isRecording = false;
                    mBtnRecord.setText("开始录制");
                    // 恢复按钮默认颜色
                    mBtnRecord.setBackgroundResource(android.R.drawable.btn_default);
                    // 重新启用文件名输入框
                    mEtFileName.setEnabled(true);
                    
                    // 停止计时器
                    mTimerHandler.removeCallbacks(mTimerRunnable);
                    // 隐藏录制时间
                    mTvRecordTime.setVisibility(View.GONE);
                    
                    showShortMsg("录制完成，点击右上角查看文件");
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
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
            
            // 预览开始后，立即设置固定亮度值，防止预览变暗
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                        mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 75);
                        Log.d(TAG, "Surface创建后设置亮度值: 75");
                    }
                }
            }, 500); // 延迟500毫秒，确保预览已经正常启动
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
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        
        final AlertDialog dialog = builder.create();
        
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
        boolean hasText = !TextUtils.isEmpty(mEtFileName.getText().toString().trim());
        boolean cameraReady = mCameraHelper != null && mCameraHelper.isCameraOpened();
        
        // 只有当相机已连接且有文件名时按钮才可用
        boolean enabled = hasText && cameraReady;
        
        mBtnRecord.setEnabled(enabled);
        mBtnRecord.setAlpha(enabled ? 1.0f : 0.5f);
        
        if (!cameraReady) {
            showShortMsg("请先连接相机，并输入文件名");
        } else if (!hasText) {
            showShortMsg("请输入文件名后点击开始录制");
        } else {
            showShortMsg("现在可以开始录制了");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在活动恢复时，如果摄像头已经打开并且在预览中，再次设置亮度
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mCameraHelper != null && mCameraHelper.isCameraOpened() && isPreview) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 75);
                    Log.d(TAG, "Activity恢复后设置亮度值: 75");
                }
            }
        }, 500);
    }
}
