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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Trace;
import android.provider.CallLog;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.contacts.common.ContactsUtils;
import com.fissy.dialer.R;
import com.fissy.dialer.app.calllog.CallLogFragment.CallLogFragmentListener;
import com.fissy.dialer.app.calllog.CallLogGroupBuilder.GroupCreator;
import com.fissy.dialer.app.calllog.calllogcache.CallLogCache;
import com.fissy.dialer.app.contactinfo.ContactInfoCache;
import com.fissy.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.fissy.dialer.calldetails.CallDetailsEntries;
import com.fissy.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.fissy.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.fissy.dialer.calllogutils.PhoneCallDetails;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.FragmentUtils.FragmentUtilListener;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.AsyncTaskExecutor;
import com.fissy.dialer.common.concurrent.AsyncTaskExecutors;
import com.fissy.dialer.configprovider.ConfigProviderComponent;
import com.fissy.dialer.contacts.ContactsComponent;
import com.fissy.dialer.duo.Duo;
import com.fissy.dialer.duo.DuoComponent;
import com.fissy.dialer.duo.DuoListener;
import com.fissy.dialer.enrichedcall.EnrichedCallCapabilities;
import com.fissy.dialer.enrichedcall.EnrichedCallComponent;
import com.fissy.dialer.enrichedcall.EnrichedCallManager;
import com.fissy.dialer.logging.ContactSource;
import com.fissy.dialer.logging.ContactSource.Type;
import com.fissy.dialer.logging.DialerImpression;
import com.fissy.dialer.logging.Logger;
import com.fissy.dialer.logging.LoggingBindings.ContactsProviderMatchInfo;
import com.fissy.dialer.logging.UiAction;
import com.fissy.dialer.main.MainActivityPeer;
import com.fissy.dialer.performancereport.PerformanceReport;
import com.fissy.dialer.phonenumbercache.CallLogQuery;
import com.fissy.dialer.phonenumbercache.ContactInfo;
import com.fissy.dialer.phonenumbercache.ContactInfoHelper;
import com.fissy.dialer.phonenumberutil.PhoneNumberHelper;
import com.fissy.dialer.spam.SpamComponent;
import com.fissy.dialer.telecom.TelecomUtil;
import com.fissy.dialer.util.PermissionsUtil;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements GroupCreator, DuoListener {

    // Types of activities the call log adapter is used for
    public static final int ACTIVITY_TYPE_CALL_LOG = 1;
    public static final int ACTIVITY_TYPE_DIALTACTS = 2;
    public static final int ALERT_POSITION = 0;
    public static final String LOAD_DATA_TASK_IDENTIFIER = "load_data";
    public static final String ENABLE_CALL_LOG_MULTI_SELECT = "enable_call_log_multiselect";
    public static final boolean ENABLE_CALL_LOG_MULTI_SELECT_FLAG = true;
    @VisibleForTesting
    static final String FILTER_EMERGENCY_CALLS_FLAG = "filter_emergency_calls";
    private static final int NO_EXPANDED_LIST_ITEM = -1;
    private static final int VIEW_TYPE_ALERT = 1;
    private static final int VIEW_TYPE_CALLLOG = 2;
    private static final String KEY_EXPANDED_POSITION = "expanded_position";
    private static final String KEY_EXPANDED_ROW_ID = "expanded_row_id";
    private static final String KEY_ACTION_MODE = "action_mode_selected_items";
    protected final Activity activity;
    /**
     * Cache for repeated requests to Telecom/Telephony.
     */
    protected final CallLogCache callLogCache;

    private final CallFetcher callFetcher;
    private final OnActionModeStateChangedListener actionModeStateChangedListener;
    private final MultiSelectRemoveView multiSelectRemoveView;
    @NonNull
    private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;
    private final int activityType;

    /**
     * Instance of helper class for managing views.
     */
    private final CallLogListItemHelper callLogListItemHelper;
    /**
     * Helper to group call log entries.
     */
    private final CallLogGroupBuilder callLogGroupBuilder;

    private final AsyncTaskExecutor asyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
    private final ContactInfoCache contactInfoCache;
    private final CallLogAlertManager callLogAlertManager;
    private final SparseArray<String> selectedItems = new SparseArray<>();
    /**
     * Maps a raw input number to match info. We only log one MatchInfo per raw input number to reduce
     * the amount of data logged.
     *
     * <p>Note that this has to be a {@link ConcurrentMap} as the match info for each row in the UI is
     * loaded in a background thread spawned when the ViewHolder is bound.
     */
    private final ConcurrentMap<String, ContactsProviderMatchInfo> contactsProviderMatchInfos =
            new ConcurrentHashMap<>();
    /**
     * A list of {@link CallLogQuery#ID} that will be hidden. The hide might be temporary so instead
     * if removing an item, it will be shown as an invisible view. This simplifies the calculation of
     * item position.
     */
    @NonNull
    private final Set<Long> hiddenRowIds = new ArraySet<>();
    /**
     * Holds a list of URIs that are pending deletion or undo. If the activity ends before the undo
     * timeout, all of the pending URIs will be deleted.
     */
    @NonNull
    private final Set<Uri> hiddenItemUris = new ArraySet<>();
    private final CallLogListItemViewHolder.OnClickListener blockReportSpamListener;
    /**
     * Map, keyed by call ID, used to track the callback action for a call. Calls associated with the
     * same callback action will be put into the same primary call group in {@link
     * com.fissy.dialer.app.calllog.CallLogGroupBuilder}. This information is used to set the
     * callback icon and trigger the corresponding action.
     */
    private final Map<Long, Integer> callbackActions = new ArrayMap<>();
    /**
     * Map, keyed by call ID, used to track the day group for a call. As call log entries are put into
     * the primary call groups in {@link com.fissy.dialer.app.calllog.CallLogGroupBuilder}, they are
     * also assigned a secondary "day group". This map tracks the day group assigned to all calls in
     * the call log. This information is used to trigger the display of a day group header above the
     * call log entry at the start of a day group. Note: Multiple calls are grouped into a single
     * primary "call group" in the call log, and the cursor used to bind rows includes all of these
     * calls. When determining if a day group change has occurred it is necessary to look at the last
     * entry in the call log to determine its day group. This map provides a means of determining the
     * previous day group without having to reverse the cursor to the start of the previous day call
     * log entry.
     */
    private final Map<Long, Integer> dayGroups = new ArrayMap<>();
    public ActionMode actionMode = null;
    public boolean selectAllMode = false;
    public boolean deselectAllMode = false;
    private final ActionMode.Callback actionModeCallback =
            new ActionMode.Callback() {

                // Called when the action mode is created; startActionMode() was called
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    if (activity != null) {
                        announceforAccessibility(
                                activity.getCurrentFocus(),
                                activity.getString(R.string.description_entering_bulk_action_mode));
                    }
                    actionMode = mode;
                    // Inflate a menu resource providing context menu items
                    MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(R.menu.actionbar_delete, menu);
                    multiSelectRemoveView.showMultiSelectRemoveView(true);
                    actionModeStateChangedListener.onActionModeStateChanged(mode, true);
                    return true;
                }

                // Called each time the action mode is shown. Always called after onCreateActionMode, but
                // may be called multiple times if the mode is invalidated.
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false; // Return false if nothing is done
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }


                // Called when the user exits the action mode
                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    if (activity != null) {
                        announceforAccessibility(
                                activity.getCurrentFocus(),
                                activity.getString(R.string.description_leaving_bulk_action_mode));
                    }
                    selectedItems.clear();
                    actionMode = null;
                    selectAllMode = false;
                    deselectAllMode = false;
                    multiSelectRemoveView.showMultiSelectRemoveView(false);
                    actionModeStateChangedListener.onActionModeStateChanged(null, false);
                    notifyDataSetChanged();
                }
            };
    // Tracks the position of the currently expanded list item.
    private int currentlyExpandedPosition = RecyclerView.NO_POSITION;
    // Tracks the rowId of the currently expanded list item, so the position can be updated if there
    // are any changes to the call log entries, such as additions or removals.
    private long currentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;
    /**
     * The OnClickListener used to expand or collapse the action buttons of a call log entry.
     */
    private final View.OnClickListener expandCollapseListener =
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PerformanceReport.recordClick(UiAction.Type.CLICK_CALL_LOG_ITEM);

                    CallLogListItemViewHolder viewHolder = (CallLogListItemViewHolder) v.getTag();
                    if (viewHolder == null) {
                        return;
                    }
                    if (actionMode != null && viewHolder.voicemailUri != null) {
                        selectAllMode = false;
                        deselectAllMode = false;
                        multiSelectRemoveView.setSelectAllModeToFalse();
                        int id = getVoicemailId(viewHolder.voicemailUri);
                        if (selectedItems.get(id) != null) {
                            Logger.get(activity)
                                    .logImpression(DialerImpression.Type.MULTISELECT_SINGLE_PRESS_UNSELECT_ENTRY);
                            uncheckMarkCallLogEntry(viewHolder, id);
                        } else {
                            Logger.get(activity)
                                    .logImpression(DialerImpression.Type.MULTISELECT_SINGLE_PRESS_SELECT_ENTRY);
                            checkMarkCallLogEntry(viewHolder);
                            // select all check box logic
                            if (getItemCount() == selectedItems.size()) {
                                LogUtil.i(
                                        "mExpandCollapseListener.onClick",
                                        "getitem count %d is equal to items select count %d, check select all box",
                                        getItemCount(),
                                        selectedItems.size());
                                multiSelectRemoveView.tapSelectAll();
                            }
                        }
                        return;
                    }

                    // If enriched call capabilities were unknown on the initial load,
                    // viewHolder.isCallComposerCapable may be unset. Check here if we have the capabilities
                    // as a last attempt at getting them before showing the expanded view to the user
                    EnrichedCallCapabilities capabilities = null;

                    if (viewHolder.number != null) {
                        capabilities = getEnrichedCallManager().getCapabilities(viewHolder.number);
                    }

                    if (capabilities == null) {
                        capabilities = EnrichedCallCapabilities.NO_CAPABILITIES;
                    }

                    viewHolder.isCallComposerCapable = capabilities.isCallComposerCapable();

                    if (capabilities.isTemporarilyUnavailable()) {
                        LogUtil.i(
                                "mExpandCollapseListener.onClick",
                                "%s is temporarily unavailable, requesting capabilities",
                                LogUtil.sanitizePhoneNumber(viewHolder.number));
                        // Refresh the capabilities when temporarily unavailable.
                        // Similarly to when we request capabilities the first time, the 'Share and call' button
                        // won't pop in with the new capabilities. Instead the row needs to be collapsed and
                        // expanded again.
                        getEnrichedCallManager().requestCapabilities(viewHolder.number);
                    }

                    if (viewHolder.rowId == currentlyExpandedRowId) {
                        // Hide actions, if the clicked item is the expanded item.
                        viewHolder.showActions(false);

                        currentlyExpandedPosition = RecyclerView.NO_POSITION;
                        currentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;
                    } else {
                        if (viewHolder.callType == CallLog.Calls.MISSED_TYPE) {
                            CallLogAsyncTaskUtil.markCallAsRead(activity, viewHolder.callIds);
                            if (activityType == ACTIVITY_TYPE_DIALTACTS) {
                                Assert.checkState(
                                        v.getContext() instanceof MainActivityPeer.PeerSupplier,
                                        "%s is not a PeerSupplier",
                                        v.getContext().getClass());
                                // This is really bad, but we must do this to prevent a dependency cycle, enforce
                                // best practices in new code, and avoid refactoring DialtactsActivity.
                                Objects.requireNonNull(((FragmentUtilListener) ((MainActivityPeer.PeerSupplier) v.getContext()).getPeer())
                                                .getImpl(CallLogFragmentListener.class))
                                        .updateTabUnreadCounts();
                            }
                        }
                        expandViewHolderActions(viewHolder);
                    }
                }
            };
    private final View.OnLongClickListener longPressListener =
            new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (ConfigProviderComponent.get(v.getContext())
                            .getConfigProvider()
                            .getBoolean(ENABLE_CALL_LOG_MULTI_SELECT, ENABLE_CALL_LOG_MULTI_SELECT_FLAG)) {
                        if (v.getId() == R.id.primary_action_view || v.getId() == R.id.quick_contact_photo) {
                            if (actionMode == null) {
                                Logger.get(activity)
                                        .logImpression(
                                                DialerImpression.Type.MULTISELECT_LONG_PRESS_ENTER_MULTI_SELECT_MODE);
                                actionMode = v.startActionMode(actionModeCallback);
                            }
                            Logger.get(activity)
                                    .logImpression(DialerImpression.Type.MULTISELECT_LONG_PRESS_TAP_ENTRY);
                            CallLogListItemViewHolder viewHolder = (CallLogListItemViewHolder) v.getTag();
                            viewHolder.quickContactView.setVisibility(View.GONE);
                            viewHolder.checkBoxView.setVisibility(View.VISIBLE);
                            expandCollapseListener.onClick(v);
                            return true;
                        }
                    }
                    return true;
                }
            };
    private boolean loading = true;
    private boolean isSpamEnabled;

    public CallLogAdapter(
            Activity activity,
            ViewGroup alertContainer,
            CallFetcher callFetcher,
            MultiSelectRemoveView multiSelectRemoveView,
            OnActionModeStateChangedListener actionModeStateChangedListener,
            CallLogCache callLogCache,
            ContactInfoCache contactInfoCache,
            @NonNull FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler,
            int activityType) {
        super();

        this.activity = activity;
        this.callFetcher = callFetcher;
        this.actionModeStateChangedListener = actionModeStateChangedListener;
        this.multiSelectRemoveView = multiSelectRemoveView;

        this.activityType = activityType;

        this.contactInfoCache = contactInfoCache;

        if (!PermissionsUtil.hasContactsReadPermissions(activity)) {
            this.contactInfoCache.disableRequestProcessing();
        }

        Resources resources = this.activity.getResources();

        this.callLogCache = callLogCache;

        PhoneCallDetailsHelper phoneCallDetailsHelper =
                new PhoneCallDetailsHelper(this.activity, resources, this.callLogCache);
        callLogListItemHelper =
                new CallLogListItemHelper(phoneCallDetailsHelper, resources, this.callLogCache);
        callLogGroupBuilder = new CallLogGroupBuilder(activity.getApplicationContext(), this);
        this.filteredNumberAsyncQueryHandler = Assert.isNotNull(filteredNumberAsyncQueryHandler);

        blockReportSpamListener =
                new BlockReportSpamListener(
                        this.activity,
                        this.activity.findViewById(R.id.call_log_fragment_root),
                        ((AppCompatActivity) this.activity).getSupportFragmentManager(),
                        this,
                        this.filteredNumberAsyncQueryHandler);
        setHasStableIds(true);

        callLogAlertManager =
                new CallLogAlertManager(this, LayoutInflater.from(this.activity), alertContainer);
    }

    private static int getVoicemailId(String voicemailUri) {
        Assert.checkArgument(voicemailUri != null);
        Assert.checkArgument(Objects.requireNonNull(voicemailUri).length() > 0);
        return (int) ContentUris.parseId(Uri.parse(voicemailUri));
    }

    private static String getNumberType(Resources res, PhoneCallDetails details) {
        // Label doesn't make much sense if the information is coming from CNAP or Cequint Caller ID.
        if (details.sourceType == ContactSource.Type.SOURCE_TYPE_CNAP
                || details.sourceType == ContactSource.Type.SOURCE_TYPE_CEQUINT_CALLER_ID) {
            return "";
        }
        // Returns empty label instead of "custom" if the custom label is empty.
        if (details.numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(details.numberLabel)) {
            return "";
        }
        return (String) Phone.getTypeLabel(res, details.numberType, details.numberLabel);
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     *
     * <p>It uses the next {@code count} rows in the cursor to extract the types.
     *
     * <p>It position in the cursor is unchanged by this function.
     */
    private static int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }


    @Nullable
    public RecyclerView.OnScrollListener getOnScrollListener() {
        return null;
    }

    private void checkMarkCallLogEntry(CallLogListItemViewHolder viewHolder) {
        announceforAccessibility(
                activity.getCurrentFocus(),
                activity.getString(
                        R.string.description_selecting_bulk_action_mode, viewHolder.nameOrNumber));
        viewHolder.quickContactView.setVisibility(View.GONE);
        viewHolder.checkBoxView.setVisibility(View.VISIBLE);
        selectedItems.put(getVoicemailId(viewHolder.voicemailUri), viewHolder.voicemailUri);
        updateActionBar();
    }

    private void announceforAccessibility(View view, String announcement) {
        if (view != null) {
            view.announceForAccessibility(announcement);
        }
    }

    private void updateActionBar() {
        if (actionMode == null && selectedItems.size() > 0) {
            Logger.get(activity)
                    .logImpression(DialerImpression.Type.MULTISELECT_ROTATE_AND_SHOW_ACTION_MODE);
            activity.startActionMode(actionModeCallback);
        }
        if (actionMode != null) {
            actionMode.setTitle(
                    activity
                            .getResources()
                            .getString(
                                    R.string.voicemailMultiSelectActionBarTitle,
                                    Integer.toString(selectedItems.size())));
        }
    }

    private void uncheckMarkCallLogEntry(CallLogListItemViewHolder viewHolder, int id) {
        announceforAccessibility(
                activity.getCurrentFocus(),
                activity.getString(
                        R.string.description_unselecting_bulk_action_mode, viewHolder.nameOrNumber));
        selectedItems.delete(id);
        viewHolder.checkBoxView.setVisibility(View.GONE);
        viewHolder.quickContactView.setVisibility(View.VISIBLE);
        updateActionBar();
    }

    private void expandViewHolderActions(CallLogListItemViewHolder viewHolder) {
        if (!TextUtils.isEmpty(viewHolder.voicemailUri)) {
            Logger.get(activity).logImpression(DialerImpression.Type.VOICEMAIL_EXPAND_ENTRY);
        }

        int lastExpandedPosition = currentlyExpandedPosition;
        // Show the actions for the clicked list item.
        viewHolder.showActions(true);
        currentlyExpandedPosition = viewHolder.getAdapterPosition();
        currentlyExpandedRowId = viewHolder.rowId;

        // If another item is expanded, notify it that it has changed. Its actions will be
        // hidden when it is re-binded because we change mCurrentlyExpandedRowId above.
        if (lastExpandedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(lastExpandedPosition);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_EXPANDED_POSITION, currentlyExpandedPosition);
        outState.putLong(KEY_EXPANDED_ROW_ID, currentlyExpandedRowId);

        ArrayList<String> listOfSelectedItems = new ArrayList<>();

        if (selectedItems.size() > 0) {
            for (int i = 0; i < selectedItems.size(); i++) {
                int id = selectedItems.keyAt(i);
                String voicemailUri = selectedItems.valueAt(i);
                LogUtil.i(
                        "CallLogAdapter.onSaveInstanceState", "index %d, id=%d, uri=%s ", i, id, voicemailUri);
                listOfSelectedItems.add(voicemailUri);
            }
        }
        outState.putStringArrayList(KEY_ACTION_MODE, listOfSelectedItems);

        LogUtil.i(
                "CallLogAdapter.onSaveInstanceState",
                "saved: %d, selectedItemsSize:%d",
                listOfSelectedItems.size(),
                selectedItems.size());
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentlyExpandedPosition =
                    savedInstanceState.getInt(KEY_EXPANDED_POSITION, RecyclerView.NO_POSITION);
            currentlyExpandedRowId =
                    savedInstanceState.getLong(KEY_EXPANDED_ROW_ID, NO_EXPANDED_LIST_ITEM);
            // Restoring multi selected entries
            ArrayList<String> listOfSelectedItems =
                    savedInstanceState.getStringArrayList(KEY_ACTION_MODE);
            if (listOfSelectedItems != null) {
                LogUtil.i(
                        "CallLogAdapter.onRestoreInstanceState",
                        "restored selectedItemsList:%d",
                        listOfSelectedItems.size());

                if (!listOfSelectedItems.isEmpty()) {
                    for (int i = 0; i < listOfSelectedItems.size(); i++) {
                        String voicemailUri = listOfSelectedItems.get(i);
                        int id = getVoicemailId(voicemailUri);
                        LogUtil.i(
                                "CallLogAdapter.onRestoreInstanceState",
                                "restoring selected index %d, id=%d, uri=%s ",
                                i,
                                id,
                                voicemailUri);
                        selectedItems.put(id, voicemailUri);
                    }

                    LogUtil.i(
                            "CallLogAdapter.onRestoreInstance",
                            "restored selectedItems %s",
                            selectedItems.toString());
                    updateActionBar();
                }
            }
        }
    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        callFetcher.fetchCalls();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isEmpty() {
        if (loading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return getItemCount() == 0;
        }
    }

    public void clearFilteredNumbersCache() {
        filteredNumberAsyncQueryHandler.clearCache();
    }

    public void onResume() {
        contactsProviderMatchInfos.clear();
        if (PermissionsUtil.hasPermission(activity, android.Manifest.permission.READ_CONTACTS)) {
            contactInfoCache.start();
        }
        isSpamEnabled = SpamComponent.get(activity).spamSettings().isSpamEnabled();
        getDuo().registerListener(this);
        notifyDataSetChanged();
    }

    public void onPause() {
        // The call log can be resumed/paused without loading any contacts. Don't log these events.
        if (!contactsProviderMatchInfos.isEmpty()) {
            Logger.get(activity).logContactsProviderMetrics(contactsProviderMatchInfos.values());
        }

        getDuo().unregisterListener(this);
        pauseCache();
    }

    public void onStop() {
        getEnrichedCallManager().clearCachedData();
    }

    public CallLogAlertManager getAlertManager() {
        return callLogAlertManager;
    }

    @VisibleForTesting
        /* package */ void pauseCache() {
        contactInfoCache.stop();
        callLogCache.reset();
    }

    @Override
    protected void addGroups(Cursor cursor) {
        callLogGroupBuilder.addGroups(cursor);
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ALERT) {
            return callLogAlertManager.createViewHolder(parent);
        }
        return createCallLogEntryViewHolder(parent);
    }

    /**
     * Creates a new call log entry {@link ViewHolder}.
     *
     * @param parent the parent view.
     * @return The {@link ViewHolder}.
     */
    private ViewHolder createCallLogEntryViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        CallLogListItemViewHolder viewHolder =
                CallLogListItemViewHolder.create(
                        view,
                        activity,
                        blockReportSpamListener,
                        expandCollapseListener,
                        longPressListener,
                        actionModeStateChangedListener,
                        callLogCache,
                        callLogListItemHelper);

        viewHolder.callLogEntryView.setTag(viewHolder);

        viewHolder.primaryActionView.setTag(viewHolder);
        viewHolder.quickContactView.setTag(viewHolder);

        return viewHolder;
    }

    /**
     * Binds the views in the entry to the data in the call log. TODO: This gets called 20-30 times
     * when Dialer starts up for a single call log entry and should not. It invokes cross-process
     * methods and the repeat execution can get costly.
     *
     * @param viewHolder The view corresponding to this entry.
     * @param position   The position of the entry.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        Trace.beginSection("onBindViewHolder: " + position);
        if (getItemViewType(position) == VIEW_TYPE_ALERT) {// Do nothing
        } else {
            bindCallLogListViewHolder(viewHolder, position);
        }
        Trace.endSection();
    }

    @Override
    public void onViewRecycled(ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
            CallLogListItemViewHolder views = (CallLogListItemViewHolder) viewHolder;
            updateCheckMarkedStatusOfEntry(views);

            if (views.asyncTask != null) {
                views.asyncTask.cancel(true);
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
            ((CallLogListItemViewHolder) viewHolder).isAttachedToWindow = true;
        }
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
            ((CallLogListItemViewHolder) viewHolder).isAttachedToWindow = false;
        }
    }

    /**
     * Binds the view holder for the call log list item view.
     *
     * @param viewHolder The call log list item view holder.
     * @param position   The position of the list item.
     */
    private void bindCallLogListViewHolder(final ViewHolder viewHolder, final int position) {
        Cursor c = (Cursor) getItem(position);
        if (c == null) {
            return;
        }
        CallLogListItemViewHolder views = (CallLogListItemViewHolder) viewHolder;
        updateCheckMarkedStatusOfEntry(views);

        views.isLoaded = false;
        int groupSize = getGroupSize(position);
        CallDetailsEntries callDetailsEntries = createCallDetailsEntries(c, groupSize);
        PhoneCallDetails details = createPhoneCallDetails(c, groupSize, views);
        if (isHiddenRow(views.number, c.getLong(CallLogQuery.ID))) {
            views.callLogEntryView.setVisibility(View.GONE);
            views.dayGroupHeader.setVisibility(View.GONE);
            return;
        } else {
            views.callLogEntryView.setVisibility(View.VISIBLE);
            // dayGroupHeader will be restored after loadAndRender() if it is needed.
        }
        if (currentlyExpandedRowId == views.rowId) {
            views.inflateActionViewStub();
        }
        loadAndRender(views, views.rowId, details, callDetailsEntries);
    }

    private void updateCheckMarkedStatusOfEntry(CallLogListItemViewHolder views) {
        if (selectedItems.size() > 0 && views.voicemailUri != null) {
            int id = getVoicemailId(views.voicemailUri);
            if (selectedItems.get(id) != null) {
                checkMarkCallLogEntry(views);
            } else {
                uncheckMarkCallLogEntry(views, id);
            }
        }
    }

    private boolean isHiddenRow(@Nullable String number, long rowId) {
        if (isHideableEmergencyNumberRow(number)) {
            return true;
        }
        return hiddenRowIds.contains(rowId);
    }

    private boolean isHideableEmergencyNumberRow(@Nullable String number) {
        if (!ConfigProviderComponent.get(activity)
                .getConfigProvider()
                .getBoolean(FILTER_EMERGENCY_CALLS_FLAG, false)) {
            return false;
        }
        return number != null && PhoneNumberUtils.isEmergencyNumber(number);
    }

    private void loadAndRender(
            final CallLogListItemViewHolder viewHolder,
            final long rowId,
            final PhoneCallDetails details,
            final CallDetailsEntries callDetailsEntries) {
        LogUtil.d("CallLogAdapter.loadAndRender", "position: %d", viewHolder.getAdapterPosition());
        // Reset block and spam information since this view could be reused which may contain
        // outdated data.
        viewHolder.isSpam = false;
        viewHolder.blockId = null;
        viewHolder.isSpamFeatureEnabled = false;

        // Attempt to set the isCallComposerCapable field. If capabilities are unknown for this number,
        // the value will be false while capabilities are requested. mExpandCollapseListener will
        // attempt to set the field properly in that case
        viewHolder.isCallComposerCapable = isCallComposerCapable(viewHolder.number);
        viewHolder.setDetailedPhoneDetails(callDetailsEntries);
        @SuppressLint("StaticFieldLeak") final AsyncTask<Void, Void, Boolean> loadDataTask =
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        viewHolder.blockId =
                                filteredNumberAsyncQueryHandler.getBlockedIdSynchronous(
                                        viewHolder.number, viewHolder.countryIso);
                        details.isBlocked = viewHolder.blockId != null;
                        if (isCancelled()) {
                            return false;
                        }
                        if (isSpamEnabled) {
                            viewHolder.isSpamFeatureEnabled = true;
                            // Only display the call as a spam call if there are incoming calls in the list.
                            // Call log cards with only outgoing calls should never be displayed as spam.
                            viewHolder.isSpam =
                                    details.hasIncomingCalls()
                                            && SpamComponent.get(activity)
                                            .spam()
                                            .checkSpamStatusSynchronous(viewHolder.number, viewHolder.countryIso);
                            details.isSpam = viewHolder.isSpam;
                        }
                        return !isCancelled() && loadData(viewHolder, rowId, details);
                    }

                    @Override
                    protected void onPostExecute(Boolean success) {
                        viewHolder.isLoaded = true;
                        if (success) {
                            viewHolder.callbackAction = getCallbackAction(viewHolder.rowId);
                            int currentDayGroup = getDayGroup(viewHolder.rowId);
                            if (currentDayGroup != details.previousGroup) {
                                viewHolder.dayGroupHeaderVisibility = View.VISIBLE;
                                viewHolder.dayGroupHeaderText = getGroupDescription(currentDayGroup);
                            } else {
                                viewHolder.dayGroupHeaderVisibility = View.GONE;
                            }
                            render(viewHolder, details, rowId);
                        }
                    }
                };

        viewHolder.asyncTask = loadDataTask;
        asyncTaskExecutor.submit(LOAD_DATA_TASK_IDENTIFIER, loadDataTask);
    }

    @MainThread
    private boolean isCallComposerCapable(@Nullable String number) {
        if (number == null) {
            return false;
        }

        EnrichedCallCapabilities capabilities = getEnrichedCallManager().getCapabilities(number);
        if (capabilities == null) {
            getEnrichedCallManager().requestCapabilities(number);
            return false;
        }
        return capabilities.isCallComposerCapable();
    }

    /**
     * Initialize PhoneCallDetails by reading all data from cursor. This method must be run on main
     * thread since cursor is not thread safe.
     */
    @MainThread
    private PhoneCallDetails createPhoneCallDetails(
            Cursor cursor, int count, final CallLogListItemViewHolder views) {
        Assert.isMainThread();
        final String number = cursor.getString(CallLogQuery.NUMBER);
        final String postDialDigits = cursor.getString(CallLogQuery.POST_DIAL_DIGITS);
        final String viaNumber = cursor.getString(CallLogQuery.VIA_NUMBER);
        final int numberPresentation = cursor.getInt(CallLogQuery.NUMBER_PRESENTATION);
        final ContactInfo cachedContactInfo = ContactInfoHelper.getContactInfo(cursor);
        final PhoneCallDetails details =
                new PhoneCallDetails(number, numberPresentation, postDialDigits);
        details.viaNumber = viaNumber;
        details.countryIso = cursor.getString(CallLogQuery.COUNTRY_ISO);
        details.date = cursor.getLong(CallLogQuery.DATE);
        details.duration = cursor.getLong(CallLogQuery.DURATION);
        details.features = getCallFeatures(cursor, count);
        details.geocode = cursor.getString(CallLogQuery.GEOCODED_LOCATION);
        details.callTypes = getCallTypes(cursor, count);

        details.accountComponentName = cursor.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME);
        details.accountId = cursor.getString(CallLogQuery.ACCOUNT_ID);
        details.cachedContactInfo = cachedContactInfo;

        if (!cursor.isNull(CallLogQuery.DATA_USAGE)) {
            details.dataUsage = cursor.getLong(CallLogQuery.DATA_USAGE);
        }

        views.rowId = cursor.getLong(CallLogQuery.ID);
        // Stash away the Ids of the calls so that we can support deleting a row in the call log.
        views.callIds = getCallIds(cursor, count);
        details.previousGroup = getPreviousDayGroup(cursor);

        // Store values used when the actions ViewStub is inflated on expansion.
        views.number = number;
        views.countryIso = details.countryIso;
        views.postDialDigits = details.postDialDigits;
        views.numberPresentation = numberPresentation;

        if (details.callTypes[0] == CallLog.Calls.VOICEMAIL_TYPE
                || details.callTypes[0] == CallLog.Calls.MISSED_TYPE) {
            details.isRead = cursor.getInt(CallLogQuery.IS_READ) == 1;
        }
        views.callType = cursor.getInt(CallLogQuery.CALL_TYPE);
        views.voicemailUri = cursor.getString(CallLogQuery.VOICEMAIL_URI);
        details.voicemailUri = views.voicemailUri;

        return details;
    }

    @MainThread
    private CallDetailsEntries createCallDetailsEntries(Cursor cursor, int count) {
        Assert.isMainThread();
        int position = cursor.getPosition();
        CallDetailsEntries.Builder entries = CallDetailsEntries.newBuilder();
        for (int i = 0; i < count; i++) {
            CallDetailsEntry.Builder entry =
                    CallDetailsEntry.newBuilder()
                            .setCallId(cursor.getLong(CallLogQuery.ID))
                            .setCallType(cursor.getInt(CallLogQuery.CALL_TYPE))
                            .setDataUsage(cursor.getLong(CallLogQuery.DATA_USAGE))
                            .setDate(cursor.getLong(CallLogQuery.DATE))
                            .setDuration(cursor.getLong(CallLogQuery.DURATION))
                            .setFeatures(cursor.getInt(CallLogQuery.FEATURES))

                            .setCallMappingId(String.valueOf(cursor.getLong(CallLogQuery.DATE)));


            String phoneAccountComponentName = cursor.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME);
            if (DuoComponent.get(activity).getDuo().isDuoAccount(phoneAccountComponentName)) {
                entry.setIsDuoCall(true);
            }

            entries.addEntries(entry.build());
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return entries.build();
    }

    /**
     * Load data for call log. Any expensive operation should be put here to avoid blocking main
     * thread. Do NOT put any cursor operation here since it's not thread safe.
     */
    @WorkerThread
    private boolean loadData(CallLogListItemViewHolder views, long rowId, PhoneCallDetails details) {
        Assert.isWorkerThread();
        if (rowId != views.rowId) {
            LogUtil.i(
                    "CallLogAdapter.loadData",
                    "rowId of viewHolder changed after load task is issued, aborting load");
            return false;
        }

        final PhoneAccountHandle accountHandle =
                TelecomUtil.composePhoneAccountHandle(details.accountComponentName, details.accountId);

        final boolean isVoicemailNumber = callLogCache.isVoicemailNumber(accountHandle, details.number);

        // Note: Binding of the action buttons is done as required in configureActionViews when the
        // user expands the actions ViewStub.

        ContactInfo info = ContactInfo.EMPTY;
        if (PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation)
                && !isVoicemailNumber) {
            // Lookup contacts with this number
            // Only do remote lookup in first 5 rows.
            int position = views.getAdapterPosition();
            info =
                    contactInfoCache.getValue(
                            details.number + details.postDialDigits,
                            details.countryIso,
                            details.cachedContactInfo,
                            position
                                    < ConfigProviderComponent.get(activity)
                                    .getConfigProvider()
                                    .getLong("number_of_call_to_do_remote_lookup", 5L));
            logCp2Metrics(details, info);
        }
        CharSequence formattedNumber =
                info.formattedNumber == null
                        ? null
                        : PhoneNumberUtils.createTtsSpannable(info.formattedNumber);
        details.updateDisplayNumber(activity, formattedNumber, isVoicemailNumber);

        views.displayNumber = details.displayNumber;
        views.accountHandle = accountHandle;
        details.accountHandle = accountHandle;

        if (!TextUtils.isEmpty(info.name) || !TextUtils.isEmpty(info.nameAlternative)) {
            details.contactUri = info.lookupUri;
            details.namePrimary = info.name;
            details.nameAlternative = info.nameAlternative;
            details.nameDisplayOrder =
                    ContactsComponent.get(activity).contactDisplayPreferences().getDisplayOrder();
            details.numberType = info.type;
            details.numberLabel = info.label;
            details.photoUri = info.photoUri;
            details.sourceType = info.sourceType;
            details.objectId = info.objectId;
            details.contactUserType = info.userType;
        }
        LogUtil.d(
                "CallLogAdapter.loadData",
                "position:%d, update geo info: %s, cequint caller id geo: %s, photo uri: %s <- %s",
                views.getAdapterPosition(),
                details.geocode,
                info.geoDescription,
                details.photoUri,
                info.photoUri);
        if (!TextUtils.isEmpty(info.geoDescription)) {
            details.geocode = info.geoDescription;
        }

        views.info = info;
        views.numberType = getNumberType(activity.getResources(), details);

        callLogListItemHelper.updatePhoneCallDetails(details);
        return true;
    }

    /**
     * Render item view given position. This is running on UI thread so DO NOT put any expensive
     * operation into it.
     */
    @MainThread
    private void render(CallLogListItemViewHolder views, PhoneCallDetails details, long rowId) {
        Assert.isMainThread();
        if (rowId != views.rowId) {
            LogUtil.i(
                    "CallLogAdapter.render",
                    "rowId of viewHolder changed after load task is issued, aborting render");
            return;
        }

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);
        views.workIconView.setVisibility(
                details.contactUserType == ContactsUtils.USER_TYPE_WORK ? View.VISIBLE : View.GONE);

        if (selectAllMode && views.voicemailUri != null) {
            selectedItems.put(getVoicemailId(views.voicemailUri), views.voicemailUri);
        }
        if (deselectAllMode && views.voicemailUri != null) {
            selectedItems.delete(getVoicemailId(views.voicemailUri));
        }
        if (views.voicemailUri != null
                && selectedItems.get(getVoicemailId(views.voicemailUri)) != null) {
            views.checkBoxView.setVisibility(View.VISIBLE);
            views.quickContactView.setVisibility(View.GONE);
        } else if (views.voicemailUri != null) {
            views.checkBoxView.setVisibility(View.GONE);
            views.quickContactView.setVisibility(View.VISIBLE);
        }
        callLogListItemHelper.setPhoneCallDetails(views, details);
        if (currentlyExpandedRowId == views.rowId) {
            // In case ViewHolders were added/removed, update the expanded position if the rowIds
            // match so that we can restore the correct expanded state on rebind.
            currentlyExpandedPosition = views.getAdapterPosition();
            views.showActions(true);
        } else {
            views.showActions(false);
        }
        views.dayGroupHeader.setVisibility(views.dayGroupHeaderVisibility);
        views.dayGroupHeader.setText(views.dayGroupHeaderText);
    }

    @Override
    public int getItemCount() {
        return super.getItemCount() + (callLogAlertManager.isEmpty() ? 0 : 1);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == ALERT_POSITION && !callLogAlertManager.isEmpty()) {
            return VIEW_TYPE_ALERT;
        }
        return VIEW_TYPE_CALLLOG;
    }

    /**
     * Retrieves an item at the specified position, taking into account the presence of a promo card.
     *
     * @param position The position to retrieve.
     * @return The item at that position.
     */
    @Override
    public Object getItem(int position) {
        return super.getItem(position - (callLogAlertManager.isEmpty() ? 0 : 1));
    }

    @Override
    public long getItemId(int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor != null) {
            return cursor.getLong(CallLogQuery.ID);
        } else {
            return 0;
        }
    }

    @Override
    public int getGroupSize(int position) {
        return super.getGroupSize(position - (callLogAlertManager.isEmpty() ? 0 : 1));
    }

    protected boolean isCallLogActivity() {
        return activityType == ACTIVITY_TYPE_CALL_LOG;
    }

    /**
     * In order to implement the "undo" function, when a voicemail is "deleted" i.e. when the user
     * clicks the delete button, the deleted item is temporarily hidden from the list. If a user
     * clicks delete on a second item before the first item's undo option has expired, the first item
     * is immediately deleted so that only one item can be "undoed" at a time.
     */

    private void collapseExpandedCard() {
        currentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;
        currentlyExpandedPosition = RecyclerView.NO_POSITION;
    }

    /**
     * When the list is changing all stored position is no longer valid.
     */
    public void invalidatePositions() {
        currentlyExpandedPosition = RecyclerView.NO_POSITION;
    }

    /**
     * Retrieves the day group of the previous call in the call log. Used to determine if the day
     * group has changed and to trigger display of the day group text.
     *
     * @param cursor The call log cursor.
     * @return The previous day group, or DAY_GROUP_NONE if this is the first call.
     */
    private int getPreviousDayGroup(Cursor cursor) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        moveToPreviousNonHiddenRow(cursor);
        if (cursor.isBeforeFirst()) {
            cursor.moveToPosition(startingPosition);
            return CallLogGroupBuilder.DAY_GROUP_NONE;
        }
        int result = getDayGroup(cursor.getLong(CallLogQuery.ID));
        cursor.moveToPosition(startingPosition);
        return result;
    }

    private void moveToPreviousNonHiddenRow(Cursor cursor) {
        while (cursor.moveToPrevious() && hiddenRowIds.contains(cursor.getLong(CallLogQuery.ID))) {
        }
    }

    /**
     * Given a call ID, look up its callback action. Callback action data are populated in {@link
     * com.fissy.dialer.app.calllog.CallLogGroupBuilder}.
     *
     * @param callId The call ID to retrieve the callback action.
     * @return The callback action for the call.
     */
    @MainThread
    private int getCallbackAction(long callId) {
        Integer result = callbackActions.get(callId);
        if (result != null) {
            return result;
        }
        return CallbackAction.NONE;
    }

    /**
     * Given a call ID, look up the day group the call belongs to. Day group data are populated in
     * {@link com.fissy.dialer.app.calllog.CallLogGroupBuilder}.
     *
     * @param callId The call ID to retrieve the day group.
     * @return The day group for the call.
     */
    @MainThread
    private int getDayGroup(long callId) {
        Integer result = dayGroups.get(callId);
        if (result != null) {
            return result;
        }
        return CallLogGroupBuilder.DAY_GROUP_NONE;
    }

    /**
     * Determine the features which were enabled for any of the calls that make up a call log entry.
     *
     * @param cursor The cursor.
     * @param count  The number of calls for the current call log entry.
     * @return The features.
     */
    private int getCallFeatures(Cursor cursor, int count) {
        int features = 0;
        int position = cursor.getPosition();
        for (int index = 0; index < count; ++index) {
            features |= cursor.getInt(CallLogQuery.FEATURES);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return features;
    }

    /**
     * Sets whether processing of requests for contact details should be enabled.
     *
     * <p>This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        // TODO: Remove this and test the cache directly.
        contactInfoCache.disableRequestProcessing();
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        // TODO: Remove this and test the cache directly.
        contactInfoCache.injectContactInfoForTest(number, countryIso, contactInfo);
    }

    /**
     * Stores the callback action associated with a call in the call log.
     *
     * @param rowId          The row ID of the current call.
     * @param callbackAction The current call's callback action.
     */
    @Override
    @MainThread
    public void setCallbackAction(long rowId, @CallbackAction int callbackAction) {
        callbackActions.put(rowId, callbackAction);
    }

    /**
     * Stores the day group associated with a call in the call log.
     *
     * @param rowId    The row ID of the current call.
     * @param dayGroup The day group the call belongs in.
     */
    @Override
    @MainThread
    public void setDayGroup(long rowId, int dayGroup) {
        dayGroups.put(rowId, dayGroup);
    }

    /**
     * Clears the day group associations on re-bind of the call log.
     */
    @Override
    @MainThread
    public void clearDayGroups() {
        dayGroups.clear();
    }

    /**
     * Retrieves the call Ids represented by the current call log row.
     *
     * @param cursor    Call log cursor to retrieve call Ids from.
     * @param groupSize Number of calls associated with the current call log row.
     * @return Array of call Ids.
     */
    private long[] getCallIds(final Cursor cursor, final int groupSize) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        long[] ids = new long[groupSize];
        // Copy the ids of the rows in the group.
        for (int index = 0; index < groupSize; ++index) {
            ids[index] = cursor.getLong(CallLogQuery.ID);
            cursor.moveToNext();
        }
        cursor.moveToPosition(startingPosition);
        return ids;
    }

    /**
     * Determines the description for a day group.
     *
     * @param group The day group to retrieve the description for.
     * @return The day group description.
     */
    private CharSequence getGroupDescription(int group) {
        if (group == CallLogGroupBuilder.DAY_GROUP_TODAY) {
            return activity.getResources().getString(R.string.call_log_header_today);
        } else if (group == CallLogGroupBuilder.DAY_GROUP_YESTERDAY) {
            return activity.getResources().getString(R.string.call_log_header_yesterday);
        } else {
            return activity.getResources().getString(R.string.call_log_header_other);
        }
    }

    @NonNull
    private EnrichedCallManager getEnrichedCallManager() {
        return EnrichedCallComponent.get(activity).getEnrichedCallManager();
    }

    @NonNull
    private Duo getDuo() {
        return DuoComponent.get(activity).getDuo();
    }

    @Override
    public void onDuoStateChanged() {
        notifyDataSetChanged();
    }

    public void onAllSelected() {
        selectAllMode = true;
        deselectAllMode = false;
        selectedItems.clear();
        for (int i = 0; i < getItemCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                Assert.checkArgument(CallLogQuery.VOICEMAIL_URI == c.getColumnIndex("voicemail_uri"));
                String voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
                selectedItems.put(getVoicemailId(voicemailUri), voicemailUri);
            }
        }
        updateActionBar();
        notifyDataSetChanged();
    }

    public void onAllDeselected() {
        selectAllMode = false;
        deselectAllMode = true;
        selectedItems.clear();
        updateActionBar();
        notifyDataSetChanged();
    }

    @WorkerThread
    private void logCp2Metrics(PhoneCallDetails details, ContactInfo contactInfo) {
        if (details == null) {
            return;
        }
        CharSequence inputNumber = details.number;
        if (inputNumber == null) {
            return;
        }

        ContactsProviderMatchInfo.Builder matchInfo =
                ContactsProviderMatchInfo.builder()
                        .setInputNumberLength(PhoneNumberUtils.normalizeNumber(inputNumber.toString()).length())
                        .setInputNumberHasPostdialDigits(
                                !PhoneNumberUtils.extractPostDialPortion(inputNumber.toString()).isEmpty()
                                        || (details.postDialDigits != null && !details.postDialDigits.isEmpty()));

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            PhoneNumber phoneNumber = phoneNumberUtil.parse(inputNumber, details.countryIso);
            matchInfo.setInputNumberValid(phoneNumberUtil.isValidNumber(phoneNumber));
        } catch (NumberParseException e) {
            // Do nothing
            matchInfo.setInputNumberValid(false);
        }

        if (contactInfo != null
                && contactInfo.number != null
                && contactInfo.sourceType == Type.SOURCE_TYPE_DIRECTORY) {
            matchInfo
                    .setMatchedContact(true)
                    .setMatchedNumberLength(PhoneNumberUtils.normalizeNumber(contactInfo.number).length())
                    .setMatchedNumberHasPostdialDigits(
                            !PhoneNumberUtils.extractPostDialPortion(contactInfo.number).isEmpty());
        }

        contactsProviderMatchInfos.put(inputNumber.toString(), matchInfo.build());
    }

    /**
     * Interface used to initiate a refresh of the content.
     */
    public interface CallFetcher {

        void fetchCalls();
    }

    /**
     * Interface used to allow single tap multi select for contact photos.
     */
    public interface OnActionModeStateChangedListener {

        void onActionModeStateChanged(ActionMode mode, boolean isEnabled);

        boolean isActionModeStateEnabled();
    }

    /**
     * Interface used to hide the fragments.
     */
    public interface MultiSelectRemoveView {

        void showMultiSelectRemoveView(boolean show);

        void setSelectAllModeToFalse();

        void tapSelectAll();
    }
}
