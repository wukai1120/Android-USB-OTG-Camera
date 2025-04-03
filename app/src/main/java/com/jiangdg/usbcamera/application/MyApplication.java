package com.jiangdg.usbcamera.application;

import android.app.Application;
import android.os.Environment;
import android.util.Log;
import java.io.File;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.CrashHandler;
import com.jiangdg.usbcamera.utils.FileUtils;

/**application class
 *
 * Created by jianddongguo on 2017/7/20.
 */

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    private CrashHandler mCrashHandler;
    // File Directory in sd card
    public static final String DIRECTORY_NAME = "USBCamera";
    // 视频保存目录名称
    public static final String VIDEO_DIRECTORY_NAME = "usbcamera";
    
    // 默认亮度值（50%，保持画面充分可见）
    public static final int DEFAULT_BRIGHTNESS = 50;

    @Override
    public void onCreate() {
        super.onCreate();
        mCrashHandler = CrashHandler.getInstance();
        mCrashHandler.init(getApplicationContext(), getClass());
        
        // 应用启动时初始化视频保存目录
        initVideoSaveDirectory();
    }
    
    /**
     * 初始化视频保存目录
     */
    private void initVideoSaveDirectory() {
        File videoDir = new File(FileUtils.ROOT_PATH, VIDEO_DIRECTORY_NAME);
        if (!videoDir.exists()) {
            boolean created = videoDir.mkdirs();
            if (created) {
                Log.d(TAG, "应用启动时创建视频目录成功: " + videoDir.getAbsolutePath());
            } else {
                Log.e(TAG, "应用启动时创建视频目录失败: " + videoDir.getAbsolutePath());
            }
        } else {
            Log.d(TAG, "视频目录已存在: " + videoDir.getAbsolutePath());
        }
    }
}
