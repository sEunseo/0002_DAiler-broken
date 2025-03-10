/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.fissy.dialer.blocking;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.contacts.common.util.ContactDisplayUtils;
import com.fissy.dialer.R;
import com.fissy.dialer.blocking.FilteredNumberAsyncQueryHandler.OnBlockNumberListener;
import com.fissy.dialer.blocking.FilteredNumberAsyncQueryHandler.OnUnblockNumberListener;
import com.fissy.dialer.logging.InteractionEvent;
import com.fissy.dialer.logging.Logger;
import com.google.android.material.snackbar.Snackbar;

/**
 * Fragment for confirming and enacting blocking/unblocking a number. Also invokes snackbar
 * providing undo functionality.
 */

public class BlockNumberDialogFragment extends DialogFragment {

    private static final String BLOCK_DIALOG_FRAGMENT = "BlockNumberDialog";
    private static final String ARG_BLOCK_ID = "argBlockId";
    private static final String ARG_NUMBER = "argNumber";
    private static final String ARG_COUNTRY_ISO = "argCountryIso";
    private static final String ARG_DISPLAY_NUMBER = "argDisplayNumber";
    private static final String ARG_PARENT_VIEW_ID = "parentViewId";
    private String number;
    private String displayNumber;
    private String countryIso;
    private FilteredNumberAsyncQueryHandler handler;
    private View parentView;
    private Callback callback;

    public static BlockNumberDialogFragment show(
            Integer blockId,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId,
            FragmentManager fragmentManager,
            Callback callback) {
        final BlockNumberDialogFragment newFragment =
                BlockNumberDialogFragment.newInstance(
                        blockId, number, countryIso, displayNumber, parentViewId);

        newFragment.setCallback(callback);
        newFragment.show(fragmentManager, BlockNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
        return newFragment;
    }

    private static BlockNumberDialogFragment newInstance(
            Integer blockId,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId) {
        final BlockNumberDialogFragment fragment = new BlockNumberDialogFragment();
        final Bundle args = new Bundle();
        if (blockId != null) {
            args.putInt(ARG_BLOCK_ID, blockId);
        }
        if (parentViewId != null) {
            args.putInt(ARG_PARENT_VIEW_ID, parentViewId);
        }
        args.putString(ARG_NUMBER, number);
        args.putString(ARG_COUNTRY_ISO, countryIso);
        args.putString(ARG_DISPLAY_NUMBER, displayNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        assert getArguments() != null;
        final boolean isBlocked = getArguments().containsKey(ARG_BLOCK_ID);

        number = getArguments().getString(ARG_NUMBER);
        displayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);
        countryIso = getArguments().getString(ARG_COUNTRY_ISO);

        if (TextUtils.isEmpty(displayNumber)) {
            displayNumber = number;
        }

        handler = new FilteredNumberAsyncQueryHandler(requireContext());
        // Choose not to update VoicemailEnabledChecker, as checks should already been done in
        // all current use cases.
        parentView = requireActivity().findViewById(getArguments().getInt(ARG_PARENT_VIEW_ID));

        CharSequence title;
        String okText;
        String message;
        if (isBlocked) {
            title = null;
            okText = getString(R.string.unblock_number_ok);
            message =
                    ContactDisplayUtils.getTtsSpannedPhoneNumber(
                                    getResources(), R.string.unblock_number_confirmation_title, displayNumber)
                            .toString();
        } else {
            title =
                    ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            getResources(), R.string.old_block_number_confirmation_title, displayNumber);
            okText = getString(R.string.block_number_ok);
            if (FilteredNumberCompat.useNewFiltering(getContext())) {
                message = getString(R.string.block_number_confirmation_message_new_filtering);
            } else {
                message = getString(R.string.block_number_confirmation_message_no_vvm);
            }
        }

        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(
                                okText,
                                (dialog, id) -> {
                                    if (isBlocked) {
                                        unblockNumber();
                                    } else {
                                        blockNumber();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        if (!FilteredNumbersUtil.canBlockNumber(getContext(), e164Number, number)) {
            dismiss();
            Toast.makeText(
                            getContext(),
                            ContactDisplayUtils.getTtsSpannedPhoneNumber(
                                    getResources(), R.string.invalidNumber, displayNumber),
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public void onPause() {
        // Dismiss on rotation.
        dismiss();
        callback = null;

        super.onPause();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private CharSequence getBlockedMessage() {
        return ContactDisplayUtils.getTtsSpannedPhoneNumber(
                getResources(), R.string.snackbar_number_blocked, displayNumber);
    }

    private CharSequence getUnblockedMessage() {
        return ContactDisplayUtils.getTtsSpannedPhoneNumber(
                getResources(), R.string.snackbar_number_unblocked, displayNumber);
    }

    private int getActionTextColor() {
        return requireContext().getResources().getColor(R.color.dialer_snackbar_action_text_color, null);
    }

    private void blockNumber() {
        final CharSequence message = getBlockedMessage();
        final CharSequence undoMessage = getUnblockedMessage();
        final Callback callback = this.callback;
        final int actionTextColor = getActionTextColor();
        final Context applicationContext = requireContext().getApplicationContext();

        final OnUnblockNumberListener onUndoListener =
                (rows, values) -> {
                    Snackbar.make(parentView, undoMessage, Snackbar.LENGTH_LONG).show();
                    if (callback != null) {
                        callback.onChangeFilteredNumberUndo();
                    }
                };

        final OnBlockNumberListener onBlockNumberListener =
                uri -> {
                    final View.OnClickListener undoListener =
                            view -> {
                                // Delete the newly created row on 'undo'.
                                Logger.get(applicationContext)
                                        .logInteraction(InteractionEvent.Type.UNDO_BLOCK_NUMBER);
                                handler.unblock(onUndoListener, uri);
                            };

                    Snackbar.make(parentView, message, Snackbar.LENGTH_LONG)
                            .setAction(R.string.snackbar_undo, undoListener)
                            .setActionTextColor(actionTextColor)
                            .show();

                    if (callback != null) {
                        callback.onFilterNumberSuccess();
                    }

                    if (FilteredNumbersUtil.hasRecentEmergencyCall(applicationContext)) {
                        FilteredNumbersUtil.maybeNotifyCallBlockingDisabled(applicationContext);
                    }
                };

        handler.blockNumber(onBlockNumberListener, number, countryIso);
    }

    private void unblockNumber() {
        final CharSequence message = getUnblockedMessage();
        final CharSequence undoMessage = getBlockedMessage();
        final Callback callback = this.callback;
        final int actionTextColor = getActionTextColor();
        final Context applicationContext = requireContext().getApplicationContext();

        final OnBlockNumberListener onUndoListener =
                uri -> {
                    Snackbar.make(parentView, undoMessage, Snackbar.LENGTH_LONG).show();
                    if (callback != null) {
                        callback.onChangeFilteredNumberUndo();
                    }
                };

        handler.unblock(
                (rows, values) -> {
                    final View.OnClickListener undoListener =
                            view -> {
                                // Re-insert the row on 'undo', with a new ID.
                                Logger.get(applicationContext)
                                        .logInteraction(InteractionEvent.Type.UNDO_UNBLOCK_NUMBER);
                                handler.blockNumber(onUndoListener, values);
                            };

                    Snackbar.make(parentView, message, Snackbar.LENGTH_LONG)
                            .setAction(R.string.snackbar_undo, undoListener)
                            .setActionTextColor(actionTextColor)
                            .show();

                    if (callback != null) {
                        callback.onUnfilterNumberSuccess();
                    }
                },
                requireArguments().getInt(ARG_BLOCK_ID));
    }

    /**
     * Use a callback interface to update UI after success/undo. Favor this approach over other more
     * standard paradigms because of the variety of scenarios in which the DialogFragment can be
     * invoked (by an Activity, by a fragment, by an adapter, by an adapter list item). Because of
     * this, we do NOT support retaining state on rotation, and will dismiss the dialog upon rotation
     * instead.
     */
    public interface Callback {

        /**
         * Called when a number is successfully added to the set of filtered numbers
         */
        void onFilterNumberSuccess();

        /**
         * Called when a number is successfully removed from the set of filtered numbers
         */
        void onUnfilterNumberSuccess();

        /**
         * Called when the action of filtering or unfiltering a number is undone
         */
        void onChangeFilteredNumberUndo();
    }
}
