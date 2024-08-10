/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.fissy.dialer.main.impl;

import static com.fissy.dialer.app.settings.DialerSettingsActivity.PrefsFragment.getThemeButtonBehavior;
import static com.fissy.dialer.common.LogUtil.TAG;
import static com.fissy.dialer.performancereport.PerformanceReport.startRecording;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.incallui.Log;
import com.fissy.dialer.R;
import com.fissy.dialer.app.settings.DialerSettingsActivity;
import com.fissy.dialer.blockreportspam.ShowBlockReportSpamDialogReceiver;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.constants.ActivityRequestCodes;
import com.fissy.dialer.interactions.PhoneNumberInteraction.DisambigDialogDismissedListener;
import com.fissy.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.fissy.dialer.interactions.PhoneNumberInteraction.InteractionErrorListener;

import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements com.fissy.dialer.main.MainActivityPeer.PeerSupplier,
        // TODO(calderwoodra): remove these 2 interfaces when we migrate to new speed dial fragment
        InteractionErrorListener,
        DisambigDialogDismissedListener {

    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    public static Activity main;
    private com.fissy.dialer.main.MainActivityPeer activePeer;
    /**
     * {@link android.content.BroadcastReceiver} that shows a dialog to block a number and/or report
     * it as spam when notified.
     */
    private ShowBlockReportSpamDialogReceiver showBlockReportSpamDialogReceiver;

    /**
     * Returns intent that will open MainActivity to the specified tab.
     * <p>
     * <p>
     * /**
     *
     * @param context Context of the application package implementing MainActivity class.
     * @return intent for MainActivity.class
     */
    private final ActivityResultLauncher<Intent> setDefaultDialerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    LogUtil.enterBlock("Default dialer set successfully");
                } else {
                    LogUtil.enterBlock("Failed to set default dialer");
                }
            }
    );

    private final ActivityResultLauncher<Intent> manageStoragePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        LogUtil.enterBlock("Manage storage permission granted");
                        Intent intent = new Intent("com.fissy.dialer.MANAGE_STORAGE_PERMISSION_RESULT");
                        intent.putExtra("permissionGranted", true);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    } else {
                        LogUtil.enterBlock("Manage storage permission not granted");
                        Intent intent = new Intent("com.fissy.dialer.MANAGE_STORAGE_PERMISSION_RESULT");
                        intent.putExtra("permissionGranted", false);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    }
                }
            }
    );

    public static Intent getIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences themeprefs = DialerSettingsActivity.PrefsFragment.getSharedPreferences(this);
        DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior mThemeBehavior = getThemeButtonBehavior(themeprefs);

        if (mThemeBehavior == DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior.DARK) {
            LogUtil.enterBlock("MainActivity.dark");
            this.getTheme().applyStyle(R.style.MainActivityThemeDark, true);
        }
        if (mThemeBehavior == DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior.LIGHT) {
            LogUtil.enterBlock("MainActivity.light");
            this.getTheme().applyStyle(R.style.MainActivityThemeLight, true);
        }

        super.onCreate(savedInstanceState);
        main = this;
        LogUtil.enterBlock("MainActivity.onCreate");
        // If peer was set by the super, don't reset it.

        activePeer = getNewPeer();
        activePeer.onActivityCreate(savedInstanceState);

        showBlockReportSpamDialogReceiver =
                new ShowBlockReportSpamDialogReceiver(getSupportFragmentManager());

        setDialer();
        checkManageStoragePermission();
        checkAndRequestRecordingPermission();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                manageStoragePermissionReceiver, new IntentFilter("com.fissy.dialer.REQUEST_MANAGE_STORAGE_PERMISSION")
        );
    }

    private void setDialer() {
        TelecomManager manager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (Objects.equals(manager.getDefaultDialerPackage(), getPackageName())) {
            LogUtil.enterBlock("App Already Default Dialer");
        } else {
            launchSetDefaultDialerIntent();
        }
    }

    private void launchSetDefaultDialerIntent() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
        setDefaultDialerLauncher.launch(intent);
    }

    private void checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                manageStoragePermissionLauncher.launch(intent);
            }
        }
    }

    private void checkAndRequestRecordingPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            String[] permissions = {
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAPTURE_AUDIO_OUTPUT
            };

            boolean allPermissionsGranted = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (!allPermissionsGranted) {
                recordAudioPermissionLauncher.launch(permissions);
            }
        }
    }

    ActivityResultLauncher<String[]> recordAudioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Handle the permission grant results
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    String permissionName = entry.getKey();
                    boolean isGranted = entry.getValue();
                    // TODO: Process the result for each permission
                }});

    private final BroadcastReceiver manageStoragePermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Intent manageStorageIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    manageStoragePermissionLauncher.launch(manageStorageIntent);
                }
            }
        }
    };

    protected com.fissy.dialer.main.MainActivityPeer getNewPeer() {
        return new MainActivityPeer(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        activePeer.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activePeer.onActivityResume();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(
                        showBlockReportSpamDialogReceiver, ShowBlockReportSpamDialogReceiver.getIntentFilter());
    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 부여된 경우
                startRecording();
            } else {
                // 권한이 거부된 경우
                Log.w(TAG, "RECORD_AUDIO 권한이 거부되었습니다.");
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        activePeer.onUserLeaveHint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        activePeer.onActivityPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(showBlockReportSpamDialogReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        activePeer.onActivityStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        activePeer.onSaveInstanceState(bundle);
    }

    @Override
    public void onBackPressed() {
        if (activePeer.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void interactionError(@InteractionErrorCode int interactionErrorCode) {
        switch (interactionErrorCode) {
            case InteractionErrorCode.USER_LEAVING_ACTIVITY:
                // This is expected to happen if the user exits the activity before the interaction occurs.
                return;
            case InteractionErrorCode.CONTACT_NOT_FOUND:
            case InteractionErrorCode.CONTACT_HAS_NO_NUMBER:
            case InteractionErrorCode.OTHER_ERROR:
            default:
                // All other error codes are unexpected. For example, it should be impossible to start an
                // interaction with an invalid contact from this activity.
                throw Assert.createIllegalStateFailException(
                        "PhoneNumberInteraction error: " + interactionErrorCode);
        }
    }

    @Override
    public void onDisambigDialogDismissed() {
        // Do nothing; the app will remain open with favorites tiles displayed.
    }

    @Override
    public com.fissy.dialer.main.MainActivityPeer getPeer() {
        return activePeer;
    }
}
