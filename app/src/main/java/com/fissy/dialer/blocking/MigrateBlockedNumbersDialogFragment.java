/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.fissy.dialer.blocking;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.DialogFragment;

import com.fissy.dialer.R;

import java.util.Objects;

/**
 * Dialog fragment shown to users when they need to migrate to use {@link
 * android.provider.BlockedNumberContract} for blocking.
 */
public class MigrateBlockedNumbersDialogFragment extends DialogFragment {

    private BlockedNumbersMigrator blockedNumbersMigrator;
    private BlockedNumbersMigrator.Listener migrationListener;

    /**
     * Creates a new MigrateBlockedNumbersDialogFragment.
     *
     * @param blockedNumbersMigrator The {@link BlockedNumbersMigrator} which will be used to migrate
     *                               the numbers.
     * @param migrationListener      The {@link BlockedNumbersMigrator.Listener} to call when the migration
     *                               is complete.
     * @return The new MigrateBlockedNumbersDialogFragment.
     * @throws NullPointerException if blockedNumbersMigrator or migrationListener are {@code null}.
     */
    public static DialogFragment newInstance(
            BlockedNumbersMigrator blockedNumbersMigrator,
            BlockedNumbersMigrator.Listener migrationListener) {
        MigrateBlockedNumbersDialogFragment fragment = new MigrateBlockedNumbersDialogFragment();
        fragment.blockedNumbersMigrator = Objects.requireNonNull(blockedNumbersMigrator);
        fragment.migrationListener = Objects.requireNonNull(migrationListener);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        AlertDialog dialog =
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.migrate_blocked_numbers_dialog_title)
                        .setMessage(R.string.migrate_blocked_numbers_dialog_message)
                        .setPositiveButton(R.string.migrate_blocked_numbers_dialog_allow_button, null)
                        .setNegativeButton(R.string.migrate_blocked_numbers_dialog_cancel_button, null)
                        .create();
        // The Dialog's buttons aren't available until show is called, so an OnShowListener
        // is used to set the positive button callback.
        dialog.setOnShowListener(
                dialog1 -> {
                    final AlertDialog alertDialog = (AlertDialog) dialog1;
                    alertDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(newPositiveButtonOnClickListener(alertDialog));
                });
        return dialog;
    }

    /*
     * Creates a new View.OnClickListener to be used as the positive button in this dialog. The
     * OnClickListener will grey out the dialog's positive and negative buttons while the migration
     * is underway, and close the dialog once the migrate is complete.
     */
    private View.OnClickListener newPositiveButtonOnClickListener(final AlertDialog alertDialog) {
        return v -> {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            blockedNumbersMigrator.migrate(
                    () -> {
                        alertDialog.dismiss();
                        migrationListener.onComplete();
                    });
        };
    }

    @Override
    public void onPause() {
        // The dialog is dismissed and state is cleaned up onPause, i.e. rotation.
        dismiss();
        blockedNumbersMigrator = null;
        migrationListener = null;
        super.onPause();
    }
}
