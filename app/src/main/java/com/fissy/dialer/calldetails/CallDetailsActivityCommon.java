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

import static com.fissy.dialer.app.settings.DialerSettingsActivity.PrefsFragment.getThemeButtonBehavior;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fissy.dialer.R;
import com.fissy.dialer.app.settings.DialerSettingsActivity;
import com.fissy.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.fissy.dialer.callintent.CallInitiationType;
import com.fissy.dialer.callintent.CallIntentBuilder;
import com.fissy.dialer.callrecord.CallRecordingDataStore;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.fissy.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.fissy.dialer.common.concurrent.DialerExecutor.Worker;
import com.fissy.dialer.common.concurrent.DialerExecutorComponent;
import com.fissy.dialer.common.concurrent.UiListener;
import com.fissy.dialer.common.database.Selection;
import com.fissy.dialer.enrichedcall.EnrichedCallComponent;
import com.fissy.dialer.enrichedcall.EnrichedCallManager;
import com.fissy.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.fissy.dialer.glidephotomanager.PhotoInfo;
import com.fissy.dialer.logging.DialerImpression;
import com.fissy.dialer.logging.Logger;
import com.fissy.dialer.logging.UiAction;
import com.fissy.dialer.main.impl.MainActivityPeer;
import com.fissy.dialer.performancereport.PerformanceReport;
import com.fissy.dialer.postcall.PostCall;
import com.fissy.dialer.precall.PreCall;
import com.fissy.dialer.rtt.RttTranscriptActivity;
import com.fissy.dialer.rtt.RttTranscriptUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Contains common logic shared between {@link OldCallDetailsActivity} and {@link
 * CallDetailsActivity}.
 */
abstract class CallDetailsActivityCommon extends AppCompatActivity {

    public static final String EXTRA_PHONE_NUMBER = "phone_number";
    public static final String EXTRA_HAS_ENRICHED_CALL_DATA = "has_enriched_call_data";
    public static final String EXTRA_CAN_REPORT_CALLER_ID = "can_report_caller_id";
    public static final String EXTRA_CAN_SUPPORT_ASSISTED_DIALING = "can_support_assisted_dialing";

    private final CallDetailsEntryViewHolder.CallDetailsEntryListener callDetailsEntryListener =
            new CallDetailsEntryListener(this);
    private final CallDetailsHeaderViewHolder.CallDetailsHeaderListener callDetailsHeaderListener =
            new CallDetailsHeaderListener(this);
    private final CallDetailsFooterViewHolder.DeleteCallDetailsListener deleteCallDetailsListener =
            new DeleteCallDetailsListener(this);
    private final CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener =
            new ReportCallIdListener(this);
    private final EnrichedCallManager.HistoricalDataChangedListener
            enrichedCallHistoricalDataChangedListener =
            new EnrichedCallHistoricalDataChangedListener(this);

    private CallDetailsAdapterCommon adapter;
    private CallDetailsEntries callDetailsEntries;
    private UiListener<ImmutableSet<String>> checkRttTranscriptAvailabilityListener;
    private CallRecordingDataStore callRecordingDataStore;

    /**
     * Handles the intent that launches {@link OldCallDetailsActivity} or {@link CallDetailsActivity},
     * e.g., extract data from intent extras, start loading data, etc.
     */
    protected abstract void handleIntent(Intent intent);

    /**
     * Creates an adapter for {@link OldCallDetailsActivity} or {@link CallDetailsActivity}.
     */
    protected abstract CallDetailsAdapterCommon createAdapter(
            CallDetailsEntryViewHolder.CallDetailsEntryListener callDetailsEntryListener,
            CallDetailsHeaderViewHolder.CallDetailsHeaderListener callDetailsHeaderListener,
            CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener,
            CallDetailsFooterViewHolder.DeleteCallDetailsListener deleteCallDetailsListener,
            CallRecordingDataStore callRecordingDataStore);

    /**
     * Returns the phone number of the call details.
     */
    protected abstract String getNumber();

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior mThemeBehavior = getThemeButtonBehavior(MainActivityPeer.themeprefs);

        if (mThemeBehavior == DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior.DARK) {
            getTheme().applyStyle(R.style.DialerDark, true);
        }
        if (mThemeBehavior == DialerSettingsActivity.PrefsFragment.ThemeButtonBehavior.LIGHT) {
            getTheme().applyStyle(R.style.DialerLight, true);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_details_activity);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.call_details);
        toolbar.setNavigationOnClickListener(
                v -> {
                    PerformanceReport.recordClick(UiAction.Type.CLOSE_CALL_DETAIL_WITH_CANCEL_BUTTON);
                    finish();
                });
        checkRttTranscriptAvailabilityListener =
                DialerExecutorComponent.get(this)
                        .createUiListener(getSupportFragmentManager(), "Query RTT transcript availability");
        callRecordingDataStore = new CallRecordingDataStore();
        handleIntent(getIntent());
        setupRecyclerViewForEntries();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        callRecordingDataStore.close();
    }


    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();

        // Some calls may not be recorded (eg. from quick contact),
        // so we should restart recording after these calls. (Recorded call is stopped)
        PostCall.restartPerformanceRecordingIfARecentCallExist(this);
        if (!PerformanceReport.isRecording()) {
            PerformanceReport.startRecording();
        }

        PostCall.promptUserForMessageIfNecessary(this, findViewById(R.id.recycler_view));

        EnrichedCallComponent.get(this)
                .getEnrichedCallManager()
                .registerHistoricalDataChangedListener(enrichedCallHistoricalDataChangedListener);
        EnrichedCallComponent.get(this)
                .getEnrichedCallManager()
                .requestAllHistoricalData(getNumber(), callDetailsEntries);
    }

    protected void loadRttTranscriptAvailability() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
            builder.add(entry.getCallMappingId());
        }
        checkRttTranscriptAvailabilityListener.listen(
                this,
                RttTranscriptUtil.getAvailableRttTranscriptIds(this, builder.build()),
                this::updateCallDetailsEntriesWithRttTranscriptAvailability,
                throwable -> {
                    throw new RuntimeException(throwable);
                });
    }

    private void updateCallDetailsEntriesWithRttTranscriptAvailability(
            ImmutableSet<String> availableTranscripIds) {
        CallDetailsEntries.Builder mutableCallDetailsEntries = CallDetailsEntries.newBuilder();
        for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
            CallDetailsEntry.Builder newEntry = CallDetailsEntry.newBuilder().mergeFrom(entry);
            newEntry.setHasRttTranscript(availableTranscripIds.contains(entry.getCallMappingId()));
            mutableCallDetailsEntries.addEntries(newEntry.build());
        }
        setCallDetailsEntries(mutableCallDetailsEntries.build());
    }

    @Override
    @CallSuper
    protected void onPause() {
        super.onPause();

        EnrichedCallComponent.get(this)
                .getEnrichedCallManager()
                .unregisterHistoricalDataChangedListener(enrichedCallHistoricalDataChangedListener);
    }

    @Override
    @CallSuper
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
        setupRecyclerViewForEntries();
    }

    private void setupRecyclerViewForEntries() {
        adapter =
                createAdapter(
                        callDetailsEntryListener,
                        callDetailsHeaderListener,
                        reportCallIdListener,
                        deleteCallDetailsListener,
                        callRecordingDataStore);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        PerformanceReport.logOnScrollStateChange(recyclerView);
    }

    @Override
    @CallSuper
    public void onBackPressed() {
        PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);
        super.onBackPressed();
    }

    protected final CallDetailsEntries getCallDetailsEntries() {
        return callDetailsEntries;
    }

    @MainThread
    protected final void setCallDetailsEntries(CallDetailsEntries entries) {
        Assert.isMainThread();
        this.callDetailsEntries = entries;
        if (adapter != null) {
            adapter.updateCallDetailsEntries(entries);
        }
    }

    /**
     * A {@link Worker} that deletes specified entries from the call log.
     */
    private static final class DeleteCallsWorker implements Worker<CallDetailsEntries, Void> {
        // Use a weak reference to hold the Activity so that there is no memory leak.
        private final WeakReference<Context> contextWeakReference;

        DeleteCallsWorker(Context context) {
            this.contextWeakReference = new WeakReference<>(context);
        }

        private static List<String> getCallLogIdList(CallDetailsEntries callDetailsEntries) {
            Assert.checkArgument(callDetailsEntries.getEntriesCount() > 0);

            List<String> idStrings = new ArrayList<>(callDetailsEntries.getEntriesCount());

            for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
                idStrings.add(String.valueOf(entry.getCallId()));
            }

            return idStrings;
        }

        @Override
        // Suppress the lint check here as the user will not be able to see call log entries if
        // permission.WRITE_CALL_LOG is not granted.
        @SuppressLint("MissingPermission")
        @RequiresPermission(value = permission.WRITE_CALL_LOG)
        public Void doInBackground(CallDetailsEntries callDetailsEntries) {
            Context context = contextWeakReference.get();
            if (context == null) {
                return null;
            }

            Selection selection =
                    Selection.builder()
                            .and(Selection.column(CallLog.Calls._ID).in(getCallLogIdList(callDetailsEntries)))
                            .build();

            context
                    .getContentResolver()
                    .delete(Calls.CONTENT_URI, selection.getSelection(), selection.getSelectionArgs());
            return null;
        }
    }

    private static final class CallDetailsEntryListener
            implements CallDetailsEntryViewHolder.CallDetailsEntryListener {
        private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

        CallDetailsEntryListener(CallDetailsActivityCommon activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void showRttTranscript(String transcriptId, String primaryText, PhotoInfo photoInfo) {
            getActivity()
                    .startActivity(
                            RttTranscriptActivity.getIntent(getActivity(), transcriptId, primaryText, photoInfo));
        }

        private CallDetailsActivityCommon getActivity() {
            return Preconditions.checkNotNull(activityWeakReference.get());
        }
    }

    private static final class CallDetailsHeaderListener
            implements CallDetailsHeaderViewHolder.CallDetailsHeaderListener {
        private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

        CallDetailsHeaderListener(CallDetailsActivityCommon activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void placeImsVideoCall(String phoneNumber) {
            Logger.get(getActivity())
                    .logImpression(DialerImpression.Type.CALL_DETAILS_IMS_VIDEO_CALL_BACK);
            PreCall.start(
                    getActivity(),
                    new CallIntentBuilder(phoneNumber, CallInitiationType.Type.CALL_DETAILS)
                            .setIsVideoCall(true));
        }

        @Override
        public void placeDuoVideoCall(String phoneNumber) {
            Logger.get(getActivity())
                    .logImpression(DialerImpression.Type.CALL_DETAILS_LIGHTBRINGER_CALL_BACK);
            PreCall.start(
                    getActivity(),
                    new CallIntentBuilder(phoneNumber, CallInitiationType.Type.CALL_DETAILS)
                            .setIsDuoCall(true)
                            .setIsVideoCall(true));
        }

        @Override
        public void placeVoiceCall(String phoneNumber, String postDialDigits) {
            Logger.get(getActivity()).logImpression(DialerImpression.Type.CALL_DETAILS_VOICE_CALL_BACK);

            boolean canSupportedAssistedDialing =
                    getActivity()
                            .getIntent()
                            .getExtras()
                            .getBoolean(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, false);
            CallIntentBuilder callIntentBuilder =
                    new CallIntentBuilder(phoneNumber + postDialDigits, CallInitiationType.Type.CALL_DETAILS);
            if (canSupportedAssistedDialing) {
                callIntentBuilder.setAllowAssistedDial(true);
            }

            PreCall.start(getActivity(), callIntentBuilder);
        }

        private CallDetailsActivityCommon getActivity() {
            return Preconditions.checkNotNull(activityWeakReference.get());
        }

        @Override
        public void openAssistedDialingSettings(View unused) {
        }

        @Override
        public void createAssistedDialerNumberParserTask(
                AssistedDialingNumberParseWorker worker,
                SuccessListener<Integer> successListener,
                FailureListener failureListener) {
            DialerExecutorComponent.get(getActivity().getApplicationContext())
                    .dialerExecutorFactory()
                    .createUiTaskBuilder(
                            getActivity().getSupportFragmentManager(),
                            "CallDetailsActivityCommon.createAssistedDialerNumberParserTask",
                            new AssistedDialingNumberParseWorker())
                    .onSuccess(successListener)
                    .onFailure(failureListener)
                    .build()
                    .executeParallel(getActivity().getNumber());
        }
    }

    static final class AssistedDialingNumberParseWorker implements Worker<String, Integer> {

        @Override
        public Integer doInBackground(String phoneNumber) {
            PhoneNumber parsedNumber;
            try {
                parsedNumber = PhoneNumberUtil.getInstance().parse(phoneNumber, null);
            } catch (NumberParseException e) {
                LogUtil.w(
                        "AssistedDialingNumberParseWorker.doInBackground",
                        "couldn't parse phone number: " + LogUtil.sanitizePii(phoneNumber),
                        e);
                return 0;
            }
            return parsedNumber.getCountryCode();
        }
    }

    private static final class DeleteCallDetailsListener
            implements CallDetailsFooterViewHolder.DeleteCallDetailsListener {

        private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

        DeleteCallDetailsListener(CallDetailsActivityCommon activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void delete() {
            CallDetailsActivityCommon activity = getActivity();
            Logger.get(activity).logImpression(DialerImpression.Type.USER_DELETED_CALL_LOG_ITEM);
            DialerExecutorComponent.get(activity)
                    .dialerExecutorFactory()
                    .createNonUiTaskBuilder(new DeleteCallsWorker(activity))
                    .onSuccess(
                            unused -> {
                                Intent data = new Intent();
                                data.putExtra(EXTRA_PHONE_NUMBER, activity.getNumber());
                                for (CallDetailsEntry entry : activity.getCallDetailsEntries().getEntriesList()) {
                                    if (entry.getHistoryResultsCount() > 0) {
                                        data.putExtra(EXTRA_HAS_ENRICHED_CALL_DATA, true);
                                        break;
                                    }
                                }

                                activity.setResult(RESULT_OK, data);
                                activity.finish();
                            })
                    .build()
                    .executeSerial(activity.getCallDetailsEntries());
        }

        private CallDetailsActivityCommon getActivity() {
            return Preconditions.checkNotNull(activityWeakReference.get());
        }
    }

    private static final class EnrichedCallHistoricalDataChangedListener
            implements EnrichedCallManager.HistoricalDataChangedListener {
        private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

        EnrichedCallHistoricalDataChangedListener(CallDetailsActivityCommon activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        private static CallDetailsEntries generateAndMapNewCallDetailsEntriesHistoryResults(
                @Nullable String number,
                @NonNull CallDetailsEntries callDetailsEntries,
                @NonNull Map<CallDetailsEntry, List<HistoryResult>> mappedResults) {
            if (number == null) {
                return callDetailsEntries;
            }
            CallDetailsEntries.Builder mutableCallDetailsEntries = CallDetailsEntries.newBuilder();
            for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
                CallDetailsEntry.Builder newEntry = CallDetailsEntry.newBuilder().mergeFrom(entry);
                List<HistoryResult> results = mappedResults.get(entry);
                if (results != null) {
                    newEntry.addAllHistoryResults(mappedResults.get(entry));
                    LogUtil.v(
                            "CallDetailsActivityCommon.generateAndMapNewCallDetailsEntriesHistoryResults",
                            "mapped %d results",
                            newEntry.getHistoryResultsList().size());
                }
                mutableCallDetailsEntries.addEntries(newEntry.build());
            }
            return mutableCallDetailsEntries.build();
        }

        @Override
        public void onHistoricalDataChanged() {
            CallDetailsActivityCommon activity = getActivity();
            Map<CallDetailsEntry, List<HistoryResult>> mappedResults =
                    getAllHistoricalData(activity.getNumber(), activity.callDetailsEntries);

            activity.setCallDetailsEntries(
                    generateAndMapNewCallDetailsEntriesHistoryResults(
                            activity.getNumber(), activity.callDetailsEntries, mappedResults));
        }

        private CallDetailsActivityCommon getActivity() {
            return Preconditions.checkNotNull(activityWeakReference.get());
        }

        @NonNull
        private Map<CallDetailsEntry, List<HistoryResult>> getAllHistoricalData(
                @Nullable String number, @NonNull CallDetailsEntries entries) {
            if (number == null) {
                return Collections.emptyMap();
            }

            Map<CallDetailsEntry, List<HistoryResult>> historicalData =
                    EnrichedCallComponent.get(getActivity())
                            .getEnrichedCallManager()
                            .getAllHistoricalData(number, entries);
            if (historicalData == null) {
                return Collections.emptyMap();
            }
            return historicalData;
        }
    }

    private final class ReportCallIdListener
            implements CallDetailsFooterViewHolder.ReportCallIdListener {
        private final WeakReference<Activity> activityWeakReference;

        ReportCallIdListener(Activity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void reportCallId(String number) {
            ReportDialogFragment fragment = ReportDialogFragment.newInstance(number);
            fragment.show(getSupportFragmentManager(), null /* tag */);
        }

        @Override
        public boolean canReportCallerId(String number) {
            return getActivity().getIntent().getExtras().getBoolean(EXTRA_CAN_REPORT_CALLER_ID, false);
        }

        private Activity getActivity() {
            return Preconditions.checkNotNull(activityWeakReference.get());
        }
    }
}
