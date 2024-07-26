/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fissy.dialer.callrecord.impl;

import static com.fissy.dialer.main.impl.MainActivity.REQUEST_RECORD_AUDIO_PERMISSION;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fissy.dialer.R;
import com.fissy.dialer.callrecord.CallRecording;
import com.fissy.dialer.callrecord.ICallRecorderService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallRecorderService extends Service {
    private static final String TAG = "CallRecorderService";
    private static final boolean DBG = false;
    private static final String CHANNEL_ID = "call_recording";
    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd_HHmmssSSS", Locale.US);
    private MediaRecorder mMediaRecorder = null;
    private CallRecording mCurrentRecording = null;

    private final ICallRecorderService.Stub mBinder = new ICallRecorderService.Stub() {
        @Override
        public CallRecording stopRecording() {
            return stopRecordingInternal();
        }

        @Override
        public boolean startRecording(String phoneNumber, long creationTime) throws RemoteException {
            return startRecordingInternal(phoneNumber, creationTime);
        }

        @Override
        public boolean isRecording() throws RemoteException {
            return mMediaRecorder != null;
        }

        @Override
        public CallRecording getActiveRecording() throws RemoteException {
            return mCurrentRecording;
        }
    };

    public static boolean isEnabled(Context context) {
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "Creating CallRecorderService");

        LocalBroadcastManager.getInstance(this).registerReceiver(
                manageStoragePermissionResultReceiver, new IntentFilter("com.fissy.dialer.MANAGE_STORAGE_PERMISSION_RESULT")
        );
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DBG) Log.d(TAG, "Destroying CallRecorderService");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(manageStoragePermissionResultReceiver);
    }

    private int getAudioSource() {
        return getResources().getInteger(R.integer.call_recording_audio_source);
    }

    private int getAudioFormatChoice() {
        // This replicates PreferenceManager.getDefaultSharedPreferences, except
        // that we need multi process preferences, as the pref is written in a separate
        // process (com.android.dialer vs. com.android.incallui)
        final String prefName = getPackageName() + "_preferences";
        final SharedPreferences prefs = getSharedPreferences(prefName, MODE_MULTI_PROCESS);

        try {
            String value = prefs.getString(getString(R.string.call_recording_format_key), null);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // ignore and fall through
        }
        return 0;
    }

    private synchronized boolean startRecordingInternal(String phoneNumber, long creationTime) {
        if (mMediaRecorder != null) {
            Log.d(TAG, "Start called with recording in progress, stopping current recording");
            stopRecordingInternal();
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Record audio permission not granted, can't record call");
            requestAudioPermissions();
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "Manage storage permission not granted, can't record call");
                requestManageStoragePermission();
                return false;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Write external storage permission not granted, can't record call");
                requestWriteExternalStoragePermission();
                return false;
            }
        }

        Log.d(TAG, "Starting recording");

        mMediaRecorder = new MediaRecorder();
        try {
            int audioSource = getAudioSource();
            int formatChoice = getAudioFormatChoice();
            mMediaRecorder.setAudioSource(audioSource);
            mMediaRecorder.setOutputFormat(formatChoice == 0 ? MediaRecorder.OutputFormat.AMR_WB : MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(formatChoice == 0 ? MediaRecorder.AudioEncoder.AMR_WB : MediaRecorder.AudioEncoder.AAC);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Error initializing media recorder", e);
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            return false;
        }

        String fileName = generateFilename(phoneNumber);
        ContentValues values = CallRecording.generateMediaInsertValues(fileName, creationTime);

        Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.w(TAG, "Failed to create new MediaStore record.");
            return false;
        }

        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd == null) {
                Log.w(TAG, "Failed to create file descriptor for recording");
                return false;
            }
            mMediaRecorder.setOutputFile(pfd.getFileDescriptor());
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            mCurrentRecording = new CallRecording(phoneNumber, creationTime, fileName, System.currentTimeMillis(), ContentUris.parseId(uri));
        } catch (IOException | IllegalStateException e) {
            Log.w(TAG, "Error starting recording", e);
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            getContentResolver().delete(uri, null, null);
            return false;
        }

        return true;
    }

    private void requestAudioPermissions() {
        Intent intent = new Intent("com.fissy.dialer.REQUEST_RECORD_AUDIO_PERMISSION");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void requestWriteExternalStoragePermission() {
        Intent intent = new Intent("com.fissy.dialer.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void requestManageStoragePermission() {
        Intent intent = new Intent("com.fissy.dialer.REQUEST_MANAGE_STORAGE_PERMISSION");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private final BroadcastReceiver manageStoragePermissionResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean permissionGranted = intent.getBooleanExtra("permissionGranted", false);
            if (permissionGranted) {
                Log.d(TAG, "Manage storage permission granted");
                // 권한이 부여된 경우, 다시 녹음을 시도할 수 있습니다.
            } else {
                Log.w(TAG, "Manage storage permission not granted");
            }
        }
    };

    private synchronized CallRecording stopRecordingInternal() {
        CallRecording recording = mCurrentRecording;
        if (DBG) Log.d(TAG, "Stopping current recording");
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Exception closing media recorder", e);
            }

            Uri uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrentRecording.mediaId);
            getContentResolver().update(uri, CallRecording.generateCompletedValues(), null, null);

            mMediaRecorder = null;
            mCurrentRecording = null;
        }
        return recording;
    }

    private String generateFilename(String number) {
        String timestamp = DATE_FORMAT.format(new Date());

        if (TextUtils.isEmpty(number)) {
            number = "unknown";
        }

        int formatChoice = getAudioFormatChoice();
        String extension = formatChoice == 0 ? ".mp3" : ".m4a";
        return number + "_" + timestamp + extension;
    }
}
