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
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.fissy.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.fissy.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.fissy.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.fissy.dialer.callrecord.CallRecordingDataStore;
import com.fissy.dialer.glidephotomanager.PhotoInfo;

/**
 * A {@link RecyclerView.Adapter} for {@link CallDetailsActivity}.
 *
 * <p>See {@link CallDetailsAdapterCommon} for logic shared between this adapter and {@link
 * OldCallDetailsAdapter}.
 */
final class CallDetailsAdapter extends CallDetailsAdapterCommon {

    /**
     * Info to be shown in the header.
     */
    private final CallDetailsHeaderInfo headerInfo;

    CallDetailsAdapter(
            Context context,
            CallDetailsHeaderInfo calldetailsHeaderInfo,
            CallDetailsEntries callDetailsEntries,
            CallDetailsEntryListener callDetailsEntryListener,
            CallDetailsHeaderListener callDetailsHeaderListener,
            CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener,
            DeleteCallDetailsListener deleteCallDetailsListener,
            CallRecordingDataStore callRecordingDataStore) {
        super(
                context,
                callDetailsEntries,
                callDetailsEntryListener,
                callDetailsHeaderListener,
                reportCallIdListener,
                deleteCallDetailsListener,
                callRecordingDataStore);
        this.headerInfo = calldetailsHeaderInfo;
    }

    @Override
    protected CallDetailsHeaderViewHolder createCallDetailsHeaderViewHolder(
            View container, CallDetailsHeaderListener callDetailsHeaderListener) {
        return new CallDetailsHeaderViewHolder(
                container,
                headerInfo.getDialerPhoneNumber().getNormalizedNumber(),
                headerInfo.getDialerPhoneNumber().getPostDialPortion(),
                callDetailsHeaderListener);
    }

    @Override
    protected void bindCallDetailsHeaderViewHolder(
            CallDetailsHeaderViewHolder callDetailsHeaderViewHolder, int position) {
        callDetailsHeaderViewHolder.updateContactInfo(headerInfo, getCallbackAction());
        callDetailsHeaderViewHolder.updateAssistedDialingInfo(
                getCallDetailsEntries().getEntries(position));
    }

    @Override
    protected String getNumber() {
        return headerInfo.getDialerPhoneNumber().getNormalizedNumber();
    }

    @Override
    protected String getPrimaryText() {
        return headerInfo.getPrimaryText();
    }

    @Override
    protected PhotoInfo getPhotoInfo() {
        return headerInfo.getPhotoInfo();
    }
}
