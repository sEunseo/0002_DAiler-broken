/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.fissy.dialer.app.calllog;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.DialerExecutor.Worker;
import com.fissy.dialer.common.concurrent.DialerExecutorComponent;
import com.fissy.dialer.notification.missedcalls.MissedCallNotificationCanceller;
import com.fissy.dialer.telecom.TelecomUtil;
import com.fissy.dialer.util.PermissionsUtil;

/**
 * Provides operations for managing call-related notifications.
 *
 * <p>It handles the following actions:
 *
 * <ul>
 *   <li>Updating voicemail notifications
 *   <li>Marking new voicemails as old
 *   <li>Updating missed call notifications
 *   <li>Marking new missed calls as old
 *   <li>Calling back from a missed call
 *   <li>Sending an SMS from a missed call
 * </ul>
 */
public class CallLogNotificationsService extends IntentService {
    /**
     * Action to call back a missed call.
     */
    public static final String ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION =
            "com.fissy.dialer.calllog.CALL_BACK_FROM_MISSED_CALL_NOTIFICATION";
    /**
     * Action mark legacy voicemail as dismissed.
     */
    public static final String ACTION_LEGACY_VOICEMAIL_DISMISSED =
            "com.fissy.dialer.calllog.ACTION_LEGACY_VOICEMAIL_DISMISSED";
    public static final int UNKNOWN_MISSED_CALL_COUNT = -1;
    @VisibleForTesting
    static final String ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD =
            "com.fissy.dialer.calllog.ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD";
    @VisibleForTesting
    static final String ACTION_CANCEL_ALL_MISSED_CALLS =
            "com.fissy.dialer.calllog.ACTION_CANCEL_ALL_MISSED_CALLS";
    private static final String ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD =
            "com.fissy.dialer.calllog.ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD ";
    private static final String ACTION_CANCEL_SINGLE_MISSED_CALL =
            "com.fissy.dialer.calllog.ACTION_CANCEL_SINGLE_MISSED_CALL";
    private static final String EXTRA_PHONE_ACCOUNT_HANDLE = "PHONE_ACCOUNT_HANDLE";

    public CallLogNotificationsService() {
        super("CallLogNotificationsService");
    }

    public static void markAllNewVoicemailsAsOld(Context context) {
        LogUtil.enterBlock("CallLogNotificationsService.markAllNewVoicemailsAsOld");
        Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
        serviceIntent.setAction(CallLogNotificationsService.ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD);
        context.startService(serviceIntent);
    }

    public static void cancelAllMissedCalls(Context context) {
        LogUtil.enterBlock("CallLogNotificationsService.cancelAllMissedCalls");
        DialerExecutorComponent.get(context)
                .dialerExecutorFactory()
                .createNonUiTaskBuilder(new CancelAllMissedCallsWorker())
                .build()
                .executeSerial(context);
    }

    public static PendingIntent createCancelAllMissedCallsPendingIntent(@NonNull Context context) {
        Intent intent = new Intent(context, CallLogNotificationsService.class);
        intent.setAction(ACTION_CANCEL_ALL_MISSED_CALLS);
        return PendingIntent.getService(context, 0, intent, 0);
    }

    public static PendingIntent createCancelSingleMissedCallPendingIntent(
            @NonNull Context context, @Nullable Uri callUri) {
        Intent intent = new Intent(context, CallLogNotificationsService.class);
        intent.setAction(ACTION_CANCEL_SINGLE_MISSED_CALL);
        intent.setData(callUri);
        return PendingIntent.getService(context, 0, intent, 0);
    }

    @WorkerThread
    private static void cancelAllMissedCallsBackground(Context context) {
        LogUtil.enterBlock("CallLogNotificationsService.cancelAllMissedCallsBackground");
        Assert.isWorkerThread();
        CallLogNotificationsQueryHelper.markAllMissedCallsInCallLogAsRead(context);
        MissedCallNotificationCanceller.cancelAll(context);
        TelecomUtil.cancelMissedCallsNotification(context);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            LogUtil.e("CallLogNotificationsService.onHandleIntent", "could not handle null intent");
            return;
        }

        if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)
                || !PermissionsUtil.hasPermission(this, android.Manifest.permission.WRITE_CALL_LOG)) {
            LogUtil.e("CallLogNotificationsService.onHandleIntent", "no READ_CALL_LOG permission");
            return;
        }

        String action = intent.getAction();
        LogUtil.i("CallLogNotificationsService.onHandleIntent", "action: " + action);
        switch (action) {
            case ACTION_CANCEL_ALL_MISSED_CALLS:
                cancelAllMissedCalls(this);
                break;
            case ACTION_CANCEL_SINGLE_MISSED_CALL:
                Uri callUri = intent.getData();
                CallLogNotificationsQueryHelper.markSingleMissedCallInCallLogAsRead(this, callUri);
                MissedCallNotificationCanceller.cancelSingle(this, callUri);
                TelecomUtil.cancelMissedCallsNotification(this);
                break;
            case ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION:
                MissedCallNotifier.getInstance(this)
                        .callBackFromMissedCall(
                                intent.getStringExtra(
                                        MissedCallNotificationReceiver.EXTRA_NOTIFICATION_PHONE_NUMBER),
                                intent.getData());
                break;
            default:
                LogUtil.e("CallLogNotificationsService.onHandleIntent", "no handler for action: " + action);
                break;
        }
    }

    /**
     * Worker that cancels all missed call notifications and updates call log entries.
     */
    private static class CancelAllMissedCallsWorker implements Worker<Context, Void> {

        @Nullable
        @Override
        public Void doInBackground(@Nullable Context context) throws Throwable {
            if (context != null) {
                cancelAllMissedCallsBackground(context);
            }
            return null;
        }
    }
}
