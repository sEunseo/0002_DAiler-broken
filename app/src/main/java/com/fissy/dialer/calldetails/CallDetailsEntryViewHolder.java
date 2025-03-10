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

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.os.BuildCompat;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.fissy.dialer.R;
import com.fissy.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.fissy.dialer.calllogutils.CallLogDates;
import com.fissy.dialer.calllogutils.CallLogDurations;
import com.fissy.dialer.calllogutils.CallTypeHelper;
import com.fissy.dialer.calllogutils.CallTypeIconsView;
import com.fissy.dialer.callrecord.CallRecording;
import com.fissy.dialer.callrecord.CallRecordingDataStore;
import com.fissy.dialer.callrecord.impl.CallRecorderService;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.fissy.dialer.enrichedcall.historyquery.proto.HistoryResult.Type;
import com.fissy.dialer.glidephotomanager.PhotoInfo;
import com.fissy.dialer.oem.MotorolaUtils;
import com.fissy.dialer.util.DialerUtils;
import com.fissy.dialer.util.IntentUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * ViewHolder for call entries in {@link OldCallDetailsActivity} or {@link CallDetailsActivity}.
 */
public class CallDetailsEntryViewHolder extends ViewHolder {

    private final CallDetailsEntryListener callDetailsEntryListener;
    private final CallTypeIconsView callTypeIcon;
    private final TextView callTypeText;
    private final TextView callTime;
    private final TextView callDuration;
    private final View multimediaImageContainer;
    private final View multimediaDetailsContainer;
    private final TextView multimediaDetails;
    private final TextView postCallNote;
    private final TextView rttTranscript;
    private final ImageView multimediaImage;
    private final Context context;
    private final TextView playbackButton;

    public CallDetailsEntryViewHolder(
            View container, CallDetailsEntryListener callDetailsEntryListener) {
        super(container);
        context = container.getContext();

        callTypeIcon = (CallTypeIconsView) container.findViewById(R.id.call_direction);
        callTypeText = (TextView) container.findViewById(R.id.call_type);
        callTime = (TextView) container.findViewById(R.id.call_time);
        callDuration = (TextView) container.findViewById(R.id.call_duration);
        playbackButton = (TextView) container.findViewById(R.id.play_recordings);
        multimediaImageContainer = container.findViewById(R.id.multimedia_image_container);
        multimediaDetailsContainer = container.findViewById(R.id.ec_container);
        multimediaDetails = (TextView) container.findViewById(R.id.multimedia_details);
        postCallNote = (TextView) container.findViewById(R.id.post_call_note);
        multimediaImage = (ImageView) container.findViewById(R.id.multimedia_image);
        // TODO(maxwelb): Display this when location is stored - a bug
        TextView multimediaAttachmentsNumber = (TextView) container.findViewById(R.id.multimedia_attachments_number);
        rttTranscript = container.findViewById(R.id.rtt_transcript);
        this.callDetailsEntryListener = callDetailsEntryListener;
    }

    private static boolean isIncoming(@NonNull HistoryResult historyResult) {
        return historyResult.getType() == Type.INCOMING_POST_CALL
                || historyResult.getType() == Type.INCOMING_CALL_COMPOSER;
    }

    private static @ColorInt
    int getColorForCallType(Context context, int callType) {
        switch (callType) {
            case Calls.OUTGOING_TYPE:
            case Calls.VOICEMAIL_TYPE:
            case Calls.BLOCKED_TYPE:
            case Calls.INCOMING_TYPE:
            case Calls.ANSWERED_EXTERNALLY_TYPE:
            case Calls.REJECTED_TYPE:
                return ContextCompat.getColor(context, R.color.dialer_secondary_text_color);
            case Calls.MISSED_TYPE:
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return ContextCompat.getColor(context, R.color.dialer_red);
        }
    }

    void setCallDetails(
            String number,
            String primaryText,
            PhotoInfo photoInfo,
            CallDetailsEntry entry,
            CallTypeHelper callTypeHelper,
            CallRecordingDataStore callRecordingDataStore,
            boolean showMultimediaDivider) {
        int callType = entry.getCallType();
        boolean isVideoCall = (entry.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
        boolean isPulledCall =
                (entry.getFeatures() & Calls.FEATURES_PULLED_EXTERNALLY)
                        == Calls.FEATURES_PULLED_EXTERNALLY;
        boolean isDuoCall = entry.getIsDuoCall();
        boolean isRttCall =
                BuildCompat.isAtLeastP()
                        && (entry.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT;

        callTime.setTextColor(getColorForCallType(context, callType));
        callTypeIcon.clear();
        callTypeIcon.add(callType);
        callTypeIcon.setShowVideo(isVideoCall);
        callTypeIcon.setShowHd(
                (entry.getFeatures() & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL);
        callTypeIcon.setShowWifi(
                MotorolaUtils.shouldShowWifiIconInCallLog(context, entry.getFeatures()));
        if (BuildCompat.isAtLeastP()) {
            callTypeIcon.setShowRtt((entry.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT);
        }

        callTypeText.setText(
                callTypeHelper.getCallTypeText(callType, isVideoCall, isPulledCall, isDuoCall));
        callTime.setText(CallLogDates.formatDate(context, entry.getDate()));

        if (CallTypeHelper.isMissedCallType(callType)) {
            callDuration.setVisibility(View.GONE);
        } else {
            callDuration.setVisibility(View.VISIBLE);
            callDuration.setText(
                    CallLogDurations.formatDurationAndDataUsage(
                            context, entry.getDuration(), entry.getDataUsage()));
            callDuration.setContentDescription(
                    CallLogDurations.formatDurationAndDataUsageA11y(
                            context, entry.getDuration(), entry.getDataUsage()));
        }

        // do this synchronously to prevent recordings from "popping in" after detail item is displayed
        final List<CallRecording> recordings;
        if (CallRecorderService.isEnabled(context)) {
            callRecordingDataStore.open(context); // opens unless already open
            recordings = callRecordingDataStore.getRecordings(number, entry.getDate());
        } else {
            recordings = null;
        }

        int count = recordings != null ? recordings.size() : 0;
        playbackButton.setOnClickListener(v -> handleRecordingClick(v, Objects.requireNonNull(recordings)));
        playbackButton.setText(
                context.getResources().getQuantityString(R.plurals.play_recordings, count, count));
        playbackButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

        setMultimediaDetails(number, entry, showMultimediaDivider);
        if (isRttCall) {
            if (entry.getHasRttTranscript()) {
                rttTranscript.setText(R.string.rtt_transcript_link);
                rttTranscript.setTextAppearance(R.style.RttTranscriptLink);
                rttTranscript.setClickable(true);
                rttTranscript.setOnClickListener(
                        v ->
                                callDetailsEntryListener.showRttTranscript(
                                        entry.getCallMappingId(), primaryText, photoInfo));
            } else {
                rttTranscript.setText(R.string.rtt_transcript_not_available);
                rttTranscript.setTextAppearance(R.style.RttTranscriptMessage);
                rttTranscript.setClickable(false);
            }
            rttTranscript.setVisibility(View.VISIBLE);
        } else {
            rttTranscript.setVisibility(View.GONE);
        }
    }

    private void setMultimediaDetails(String number, CallDetailsEntry entry, boolean showDivider) {
        if (entry.getHistoryResultsList().isEmpty()) {
            LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no data, hiding UI");
            multimediaDetailsContainer.setVisibility(View.GONE);
        } else {

            HistoryResult historyResult = entry.getHistoryResults(0);
            multimediaDetailsContainer.setVisibility(View.VISIBLE);
            multimediaDetailsContainer.setOnClickListener((v) -> startSmsIntent(context, number));
            multimediaImageContainer.setOnClickListener((v) -> startSmsIntent(context, number));
            multimediaImageContainer.setClipToOutline(true);

            if (!TextUtils.isEmpty(historyResult.getImageUri())) {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "setting image");
                multimediaImageContainer.setVisibility(View.VISIBLE);
                multimediaImage.setImageURI(Uri.parse(historyResult.getImageUri()));
                multimediaDetails.setText(
                        isIncoming(historyResult) ? R.string.received_a_photo : R.string.sent_a_photo);
            } else {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no image");
            }

            // Set text after image to overwrite the received/sent a photo text
            if (!TextUtils.isEmpty(historyResult.getText())) {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "showing text");
                multimediaDetails.setText(
                        context.getString(R.string.message_in_quotes, historyResult.getText()));
            } else {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no text");
            }

            if (entry.getHistoryResultsList().size() > 1
                    && !TextUtils.isEmpty(entry.getHistoryResults(1).getText())) {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "showing post call note");
                postCallNote.setVisibility(View.VISIBLE);
                postCallNote.setText(
                        context.getString(R.string.message_in_quotes, entry.getHistoryResults(1).getText()));
                postCallNote.setOnClickListener((v) -> startSmsIntent(context, number));
            } else {
                LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no post call note");
            }
        }
    }

    private void startSmsIntent(Context context, String number) {
        DialerUtils.startActivityWithErrorToast(context, IntentUtil.getSendSmsIntent(number));
    }

    private void handleRecordingClick(View v, List<CallRecording> recordings) {
        final Context context = v.getContext();
        if (recordings.size() == 1) {
            playRecording(context, recordings.get(0));
        } else {
            PopupMenu menu = new PopupMenu(context, v);
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                    DateFormat.is24HourFormat(context) ? "Hmss" : "hmssa");
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);

            for (int i = 0; i < recordings.size(); i++) {
                final long startTime = recordings.get(i).startRecordingTime;
                final String formattedDate = format.format(new Date(startTime));
                menu.getMenu().add(Menu.NONE, i, i, formattedDate);
            }
            menu.setOnMenuItemClickListener(item -> {
                playRecording(context, recordings.get(item.getItemId()));
                return true;
            });
            menu.show();
        }
    }

    private void playRecording(Context context, CallRecording recording) {
        Uri uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, recording.mediaId);
        String extension = MimeTypeMap.getFileExtensionFromUrl(recording.fileName);
        String mime = !TextUtils.isEmpty(extension)
                ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : "audio/*";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.call_playback_no_app_found_toast, Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Listener for the call details header
     */
    interface CallDetailsEntryListener {
        /**
         * Shows RTT transcript.
         */
        void showRttTranscript(String transcriptId, String primaryText, PhotoInfo photoInfo);
    }
}
