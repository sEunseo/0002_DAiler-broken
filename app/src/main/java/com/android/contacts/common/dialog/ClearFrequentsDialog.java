/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.common.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.fissy.dialer.R;
import com.fissy.dialer.util.PermissionsUtil;

/**
 * Dialog that clears the frequently contacted list after confirming with the user.
 */
public class ClearFrequentsDialog extends DialogFragment {

    /**
     * Preferred way to show this dialog
     */
    public static void show(FragmentManager fragmentManager) {
        ClearFrequentsDialog dialog = new ClearFrequentsDialog();
        dialog.show(fragmentManager, "clearFrequents");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = requireActivity().getApplicationContext();
        final ContentResolver resolver = requireActivity().getContentResolver();
        final OnClickListener okListener =
                (dialog, which) -> {
                    if (!PermissionsUtil.hasContactsReadPermissions(context)) {
                        return;
                    }

                    @SuppressLint("StaticFieldLeak") final AsyncTask<Void, Void, Void> task =
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... params) {
                                    resolver.delete(
                                            ContactsContract.DataUsageFeedback.DELETE_USAGE_URI, null, null);
                                    return null;
                                }
                            };
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                };
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.clearFrequentsConfirmation_title)
                .setMessage(R.string.clearFrequentsConfirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okListener)
                .setCancelable(true)
                .create();
    }
}
