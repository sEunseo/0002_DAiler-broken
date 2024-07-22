package com.fissy.dialer.main.impl;

import static com.fissy.dialer.app.settings.DialerSettingsActivity.PrefsFragment.getThemeButtonBehavior;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.telecom.TelecomManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fissy.dialer.R;
import com.fissy.dialer.app.settings.DialerSettingsActivity;
import com.fissy.dialer.blockreportspam.ShowBlockReportSpamDialogReceiver;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.constants.ActivityRequestCodes;
import com.fissy.dialer.interactions.PhoneNumberInteraction.DisambigDialogDismissedListener;
import com.fissy.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.fissy.dialer.interactions.PhoneNumberInteraction.InteractionErrorListener;

import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements com.fissy.dialer.main.MainActivityPeer.PeerSupplier,
        InteractionErrorListener,
        DisambigDialogDismissedListener {

    public static Activity main;
    private com.fissy.dialer.main.MainActivityPeer activePeer;
    private ShowBlockReportSpamDialogReceiver showBlockReportSpamDialogReceiver;

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

        activePeer = getNewPeer();
        activePeer.onActivityCreate(savedInstanceState);

        showBlockReportSpamDialogReceiver =
                new ShowBlockReportSpamDialogReceiver(getSupportFragmentManager());

        setDialer();
        checkManageStoragePermission();

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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        activePeer.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
                return;
            case InteractionErrorCode.CONTACT_NOT_FOUND:
            case InteractionErrorCode.CONTACT_HAS_NO_NUMBER:
            case InteractionErrorCode.OTHER_ERROR:
            default:
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
