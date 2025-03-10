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
 * limitations under the License
 */

package com.fissy.dialer.app.calllog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.provider.CallLog.Calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.fissy.dialer.R;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.concurrent.DialerExecutor;
import com.fissy.dialer.common.concurrent.DialerExecutor.Worker;
import com.fissy.dialer.common.concurrent.DialerExecutorComponent;
import com.fissy.dialer.enrichedcall.EnrichedCallComponent;
import com.fissy.dialer.phonenumbercache.CachedNumberLookupService;
import com.fissy.dialer.phonenumbercache.PhoneNumberCache;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

/**
 * Dialog that clears the call log after confirming with the user
 */
public class ClearCallLogDialog extends DialogFragment {

    private DialerExecutor<Void> clearCallLogTask;
    private ProgressDialog progressDialog;

    /**
     * Preferred way to show this dialog
     */
    public static void show(FragmentManager fragmentManager) {
        ClearCallLogDialog dialog = new ClearCallLogDialog();
        dialog.show(fragmentManager, "deleteCallLog");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        clearCallLogTask =
                DialerExecutorComponent.get(Objects.requireNonNull(getContext()))
                        .dialerExecutorFactory()
                        .createUiTaskBuilder(
                                Objects.requireNonNull(getFragmentManager()),
                                "clearCallLogTask",
                                new ClearCallLogWorker(Objects.requireNonNull(getActivity()).getApplicationContext()))
                        .onSuccess(this::onSuccess)
                        .build();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        OnClickListener okListener =
                (dialog, which) -> {
                    progressDialog =
                            ProgressDialog.show(
                                    getActivity(), getString(R.string.clearCallLogProgress_title), "", true, false);
                    progressDialog.setOwnerActivity(getActivity());
                    CallLogNotificationsService.cancelAllMissedCalls(getContext());

                    // TODO: Once we have the API, we should configure this ProgressDialog
                    // to only show up after a certain time (e.g. 150ms)
                    progressDialog.show();

                    clearCallLogTask.executeSerial(null);
                };
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.clearCallLogConfirmation_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.clearCallLogConfirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okListener)
                .setCancelable(true)
                .create();
    }

    private void onSuccess(Void unused) {
        Assert.isNotNull(progressDialog);
        Activity activity = progressDialog.getOwnerActivity();

        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        maybeShowEnrichedCallSnackbar(activity);

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void maybeShowEnrichedCallSnackbar(Activity activity) {
        if (EnrichedCallComponent.get(activity).getEnrichedCallManager().hasStoredData()) {
            Snackbar.make(
                            activity.findViewById(R.id.calllog_frame),
                            activity.getString(R.string.multiple_ec_data_deleted),
                            5_000)
                    .show();
        }
    }

    private static class ClearCallLogWorker implements Worker<Void, Void> {
        private final Context appContext;

        private ClearCallLogWorker(Context appContext) {
            this.appContext = appContext;
        }

        @Nullable
        @Override
        public Void doInBackground(@Nullable Void unused) throws Throwable {
            appContext.getContentResolver().delete(Calls.CONTENT_URI, null, null);
            CachedNumberLookupService cachedNumberLookupService =
                    PhoneNumberCache.get(appContext).getCachedNumberLookupService();
            if (cachedNumberLookupService != null) {
                cachedNumberLookupService.clearAllCacheEntries(appContext);
            }
            return null;
        }
    }
}
