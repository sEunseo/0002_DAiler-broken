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
 * limitations under the License.
 */

package com.fissy.dialer.calldetails;

import android.content.Context;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fissy.dialer.R;
import com.fissy.dialer.calldetails.CallDetailsActivityCommon.AssistedDialingNumberParseWorker;
import com.fissy.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.fissy.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.fissy.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.fissy.dialer.compat.telephony.TelephonyManagerCompat;
import com.fissy.dialer.contactphoto.ContactPhotoManager;
import com.fissy.dialer.dialercontact.DialerContact;
import com.fissy.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.fissy.dialer.logging.InteractionEvent;
import com.fissy.dialer.logging.Logger;
import com.fissy.dialer.phonenumberutil.PhoneNumberHelper;
import com.fissy.dialer.widget.BidiTextView;

/**
 * ViewHolder for the header in {@link OldCallDetailsActivity} or {@link CallDetailsActivity}.
 *
 * <p>The header contains contact info and the primary callback button.
 */
public class CallDetailsHeaderViewHolder extends RecyclerView.ViewHolder
        implements OnClickListener, FailureListener {

    private final CallDetailsHeaderListener callDetailsHeaderListener;
    private final ImageView callbackButton;
    private final BidiTextView nameView;
    private final BidiTextView numberView;
    private final TextView networkView;
    private final QuickContactBadge contactPhoto;
    private final Context context;
    private final TextView assistedDialingInternationalDirectDialCodeAndCountryCodeText;
    private final RelativeLayout assistedDialingContainer;

    private final String number;
    private final String postDialDigits;

    private @CallbackAction
    int callbackAction;

    CallDetailsHeaderViewHolder(
            View container,
            String number,
            String postDialDigits,
            CallDetailsHeaderListener callDetailsHeaderListener) {
        super(container);
        context = container.getContext();
        callbackButton = container.findViewById(R.id.call_back_button);
        nameView = container.findViewById(R.id.contact_name);
        numberView = container.findViewById(R.id.phone_number);
        networkView = container.findViewById(R.id.network);
        contactPhoto = container.findViewById(R.id.quick_contact_photo);
        assistedDialingInternationalDirectDialCodeAndCountryCodeText =
                container.findViewById(R.id.assisted_dialing_text);
        assistedDialingContainer = container.findViewById(R.id.assisted_dialing_container);

        assistedDialingContainer.setOnClickListener(
                callDetailsHeaderListener::openAssistedDialingSettings);

        callbackButton.setOnClickListener(this);

        this.number = number;
        this.postDialDigits = postDialDigits;
        this.callDetailsHeaderListener = callDetailsHeaderListener;

        Logger.get(context)
                .logQuickContactOnTouch(
                        contactPhoto, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CALL_DETAILS, true);
    }

    private boolean hasAssistedDialingFeature(Integer features) {
        return (features & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
                == TelephonyManagerCompat.FEATURES_ASSISTED_DIALING;
    }

    void updateAssistedDialingInfo(CallDetailsEntry callDetailsEntry) {

        if (callDetailsEntry != null && hasAssistedDialingFeature(callDetailsEntry.getFeatures())) {
            showAssistedDialingContainer(true);
            callDetailsHeaderListener.createAssistedDialerNumberParserTask(
                    new CallDetailsActivityCommon.AssistedDialingNumberParseWorker(),
                    this::updateAssistedDialingText,
                    this);

        } else {
            showAssistedDialingContainer(false);
        }
    }

    private void showAssistedDialingContainer(boolean shouldShowContainer) {
        if (shouldShowContainer) {
            assistedDialingContainer.setVisibility(View.VISIBLE);
        } else {
            LogUtil.i(
                    "CallDetailsHeaderViewHolder.updateAssistedDialingInfo",
                    "hiding assisted dialing ui elements");
            assistedDialingContainer.setVisibility(View.GONE);
        }
    }

    private void updateAssistedDialingText(Integer countryCode) {

        // Try and handle any poorly formed inputs.
        if (countryCode <= 0) {
            onFailure(new IllegalStateException());
            return;
        }

        LogUtil.i(
                "CallDetailsHeaderViewHolder.updateAssistedDialingText", "Updating Assisted Dialing Text");
        assistedDialingInternationalDirectDialCodeAndCountryCodeText.setText(
                context.getString(
                        R.string.assisted_dialing_country_code_entry, String.valueOf(countryCode)));
    }

    @Override
    public void onFailure(@NonNull Throwable unused) {
        assistedDialingInternationalDirectDialCodeAndCountryCodeText.setText(
                R.string.assisted_dialing_country_code_entry_failure);
    }

    /**
     * Populates the contact info fields based on the current contact information.
     */
    void updateContactInfo(DialerContact contact, @CallbackAction int callbackAction) {
        ContactPhotoManager.getInstance(context)
                .loadDialerThumbnailOrPhoto(
                        contactPhoto,
                        contact.getContactUri().isEmpty() ? null : Uri.parse(contact.getContactUri()),
                        contact.getPhotoId(),
                        contact.getPhotoUri().isEmpty() ? null : Uri.parse(contact.getPhotoUri()),
                        contact.getNameOrNumber(),
                        contact.getContactType());

        // Hide the secondary text of the header by default.
        // We will show it if needed (see below).
        numberView.setVisibility(View.GONE);
        numberView.setText(null);

        if (PhoneNumberHelper.isLocalEmergencyNumber(context, contact.getNumber())) {
            nameView.setText(context.getResources().getString(R.string.emergency_number));
        } else {
            nameView.setText(contact.getNameOrNumber());
            if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
                numberView.setVisibility(View.VISIBLE);
                String secondaryInfo =
                        TextUtils.isEmpty(contact.getNumberLabel())
                                ? contact.getDisplayNumber()
                                : context.getString(
                                R.string.call_subject_type_and_number,
                                contact.getNumberLabel(),
                                contact.getDisplayNumber());
                numberView.setText(secondaryInfo);
            }
        }

        if (!TextUtils.isEmpty(contact.getSimDetails().getNetwork())) {
            networkView.setVisibility(View.VISIBLE);
            networkView.setText(contact.getSimDetails().getNetwork());
            if (contact.getSimDetails().getColor() != PhoneAccount.NO_HIGHLIGHT_COLOR) {
                networkView.setTextColor(contact.getSimDetails().getColor());
            }
        }

        setCallbackAction(callbackAction);
    }

    void updateContactInfo(CallDetailsHeaderInfo headerInfo, @CallbackAction int callbackAction) {
        GlidePhotoManagerComponent.get(context)
                .glidePhotoManager()
                .loadQuickContactBadge(contactPhoto, headerInfo.getPhotoInfo());

        nameView.setText(headerInfo.getPrimaryText());
        if (!headerInfo.getSecondaryText().isEmpty()) {
            numberView.setVisibility(View.VISIBLE);
            numberView.setText(headerInfo.getSecondaryText());
        } else {
            numberView.setVisibility(View.GONE);
            numberView.setText(null);
        }

        setCallbackAction(callbackAction);
    }

    private void setCallbackAction(@CallbackAction int callbackAction) {
        this.callbackAction = callbackAction;
        switch (callbackAction) {
            case CallbackAction.DUO:
            case CallbackAction.IMS_VIDEO:
                callbackButton.setVisibility(View.VISIBLE);
                callbackButton.setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
                break;
            case CallbackAction.VOICE:
                callbackButton.setVisibility(View.VISIBLE);
                callbackButton.setImageResource(R.drawable.quantum_ic_call_vd_theme_24);
                break;
            case CallbackAction.NONE:
                callbackButton.setVisibility(View.GONE);
                break;
            default:
                throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == callbackButton) {
            switch (callbackAction) {
                case CallbackAction.IMS_VIDEO:
                    callDetailsHeaderListener.placeImsVideoCall(number);
                    break;
                case CallbackAction.DUO:
                    callDetailsHeaderListener.placeDuoVideoCall(number);
                    break;
                case CallbackAction.VOICE:
                    callDetailsHeaderListener.placeVoiceCall(number, postDialDigits);
                    break;
                case CallbackAction.NONE:
                default:
                    throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
            }
        } else {
            throw Assert.createIllegalStateFailException("View OnClickListener not implemented: " + view);
        }
    }

    /**
     * Listener for the call details header
     */
    interface CallDetailsHeaderListener {

        /**
         * Places an IMS video call.
         */
        void placeImsVideoCall(String phoneNumber);

        /**
         * Places a Duo video call.
         */
        void placeDuoVideoCall(String phoneNumber);

        /**
         * Place a traditional voice call.
         */
        void placeVoiceCall(String phoneNumber, String postDialDigits);

        /**
         * Access the Assisted Dialing settings *
         */
        void openAssistedDialingSettings(View view);

        void createAssistedDialerNumberParserTask(
                AssistedDialingNumberParseWorker worker,
                SuccessListener<Integer> onSuccess,
                FailureListener onFailure);
    }
}
