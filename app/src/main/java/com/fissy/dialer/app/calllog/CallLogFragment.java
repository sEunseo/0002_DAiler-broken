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

import static android.Manifest.permission.READ_CALL_LOG;
import static com.fissy.dialer.app.contactinfo.ExpirableCacheHeadlessFragment.attach;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fissy.dialer.R;
import com.fissy.dialer.app.Bindings;
import com.fissy.dialer.app.calllog.CallLogAdapter.CallFetcher;
import com.fissy.dialer.app.calllog.CallLogAdapter.MultiSelectRemoveView;
import com.fissy.dialer.app.calllog.calllogcache.CallLogCache;
import com.fissy.dialer.app.contactinfo.ContactInfoCache;
import com.fissy.dialer.app.contactinfo.ContactInfoCache.OnContactInfoChangedListener;
import com.fissy.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.FragmentUtils;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.configprovider.ConfigProviderComponent;
import com.fissy.dialer.database.CallLogQueryHandler;
import com.fissy.dialer.database.CallLogQueryHandler.Listener;
import com.fissy.dialer.location.GeoUtil;
import com.fissy.dialer.logging.DialerImpression;
import com.fissy.dialer.logging.Logger;
import com.fissy.dialer.metrics.Metrics;
import com.fissy.dialer.metrics.MetricsComponent;
import com.fissy.dialer.metrics.jank.RecyclerViewJankLogger;
import com.fissy.dialer.oem.CequintCallerIdManager;
import com.fissy.dialer.performancereport.PerformanceReport;
import com.fissy.dialer.phonenumbercache.ContactInfoHelper;
import com.fissy.dialer.util.PermissionsUtil;
import com.fissy.dialer.widget.EmptyContentView;
import com.fissy.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;

import java.util.Objects;

/**
 * Displays a list of call log entries. To filter for a particular kind of call (all, missed or
 * voicemails), specify it in the constructor.
 */
public class CallLogFragment extends Fragment
        implements Listener,
        CallFetcher,
        MultiSelectRemoveView,
        OnEmptyViewActionButtonClickedListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        CallLogModalAlertManager.Listener,
        OnClickListener {
    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_LOG_LIMIT = "log_limit";
    private static final String KEY_DATE_LIMIT = "date_limit";
    private static final String KEY_IS_CALL_LOG_ACTIVITY = "is_call_log_activity";
    private static final String KEY_HAS_READ_CALL_LOG_PERMISSION = "has_read_call_log_permission";
    private static final String KEY_REFRESH_DATA_REQUIRED = "refresh_data_required";
    private static final String KEY_SELECT_ALL_MODE = "select_all_mode_checked";

    // No limit specified for the number of logs to show; use the CallLogQueryHandler's default.
    private static final int NO_LOG_LIMIT = -1;
    // No date-based filtering.
    private static final int NO_DATE_LIMIT = 0;

    private static final int PHONE_PERMISSIONS_REQUEST_CODE = 1;

    private static final int EVENT_UPDATE_DISPLAY = 1;

    private static final long MILLIS_IN_MINUTE = 60 * 1000;
    private final Handler handler = new Handler();
    // See issue 6363009
    private final ContentObserver callLogObserver = new CustomContentObserver();
    private final ContentObserver contactsObserver = new CustomContentObserver();
    protected CallLogModalAlertManager modalAlertManager;
    private View multiSelectUnSelectAllViewContent;
    private ImageView selectUnselectAllIcon;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private CallLogAdapter adapter;
    private final OnContactInfoChangedListener onContactInfoChangedListener =
            new OnContactInfoChangedListener() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onContactInfoChanged() {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            };
    private CallLogQueryHandler callLogQueryHandler;
    private boolean scrollToTop;
    private EmptyContentView emptyListView;
    private ContactInfoCache contactInfoCache;
    private boolean refreshDataRequired;
    private boolean hasReadCallLogPermission;
    // Exactly same variable is in Fragment as a package private.
    private boolean menuVisible = true;
    // Default to all calls.
    private int callTypeFilter;
    // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
    // will be used.
    private int logLimit;
    // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
    // the date filter are included.  If zero, no date-based filtering occurs.
    private long dateLimit;
    /*
     * True if this instance of the CallLogFragment shown in the CallLogActivity.
     */
    private boolean isCallLogActivity = false;
    private boolean selectAllMode;
    private ViewGroup modalAlertView;

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, NO_LOG_LIMIT);
    }

    public CallLogFragment(int filterType) {
        this(filterType, NO_LOG_LIMIT);
    }    @SuppressLint("HandlerLeak")
    private final Handler displayUpdateHandler =
            new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == EVENT_UPDATE_DISPLAY) {
                        refreshData();
                        rescheduleDisplayUpdate();
                    } else {
                        throw Assert.createAssertionFailException("Invalid message: " + msg);
                    }
                }
            };

    public CallLogFragment(int filterType, boolean isCallLogActivity) {
        this(filterType, NO_LOG_LIMIT);
        this.isCallLogActivity = isCallLogActivity;
    }

    public CallLogFragment(int filterType, int logLimit) {
        this(filterType, logLimit, NO_DATE_LIMIT);
    }

    public CallLogFragment(int filterType, int logLimit, long dateLimit) {
        callTypeFilter = filterType;
        this.logLimit = logLimit;
        this.dateLimit = dateLimit;
    }

    @Override
    public void onCreate(Bundle state) {
        LogUtil.enterBlock("CallLogFragment.onCreate");

        super.onCreate(state);
        refreshDataRequired = true;
        if (state != null) {
            callTypeFilter = state.getInt(KEY_FILTER_TYPE, callTypeFilter);
            logLimit = state.getInt(KEY_LOG_LIMIT, logLimit);
            dateLimit = state.getLong(KEY_DATE_LIMIT, dateLimit);
            isCallLogActivity = state.getBoolean(KEY_IS_CALL_LOG_ACTIVITY, isCallLogActivity);
            hasReadCallLogPermission = state.getBoolean(KEY_HAS_READ_CALL_LOG_PERMISSION, false);
            refreshDataRequired = state.getBoolean(KEY_REFRESH_DATA_REQUIRED, refreshDataRequired);
            selectAllMode = state.getBoolean(KEY_SELECT_ALL_MODE, false);
        }

        final Activity activity = getActivity();
        assert activity != null;
        final ContentResolver resolver = activity.getContentResolver();
        callLogQueryHandler = new CallLogQueryHandler(activity, resolver, this, logLimit);
        setHasOptionsMenu(true);
    }

    /**
     * Called by the CallLogQueryHandler when the list of calls has been fetched or updated.
     */
    @Override
    public boolean onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            // Return false; we did not take ownership of the cursor
            return false;
        }
        adapter.invalidatePositions();
        adapter.setLoading(false);
        adapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();

        if (cursor != null && cursor.getCount() > 0) {
            recyclerView.setPaddingRelative(
                    recyclerView.getPaddingStart(),
                    0,
                    recyclerView.getPaddingEnd(),
                    getResources().getDimensionPixelSize(R.dimen.floating_action_button_list_bottom_padding));
            emptyListView.setVisibility(View.GONE);
        } else {
            recyclerView.setPaddingRelative(
                    recyclerView.getPaddingStart(), 0, recyclerView.getPaddingEnd(), 0);
            emptyListView.setVisibility(View.VISIBLE);
        }
        if (scrollToTop) {
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (layoutManager.findFirstVisibleItemPosition() > 5) {
                // TODO: Jump to near the top, then begin smooth scroll.
                recyclerView.smoothScrollToPosition(0);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            handler.post(
                    () -> {
                        if (getActivity() == null || getActivity().isFinishing()) {
                            return;
                        }
                        recyclerView.smoothScrollToPosition(0);
                    });

            scrollToTop = false;
        }
        return true;
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        setupView(view);
        return view;
    }

    private String concatCallIds(long[] callIds) {
        if (callIds == null || callIds.length == 0) {
            return null;
        }

        StringBuilder str = new StringBuilder();
        for (long callId : callIds) {
            if (str.length() != 0) {
                str.append(",");
            }
            str.append(callId);
        }

        return str.toString();
    }

    protected void setupView(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(50, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                if (holder instanceof CallLogListItemViewHolder) {
                    CallLogListItemViewHolder viewHolder = ((CallLogListItemViewHolder) holder);
                    if (direction == ItemTouchHelper.LEFT) {
                        requireContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", viewHolder.number, null)));
                        adapter.notifyItemChanged(holder.getAdapterPosition());
                    } else {
                        //TODO: Make it thread safe
                        requireContext()
                                .getContentResolver()
                                .delete(
                                        CallLog.Calls.CONTENT_URI,
                                        CallLog.Calls._ID + " IN (" + concatCallIds(viewHolder.callIds) + ")" /* where */,
                                        null /* selectionArgs */);
                    }
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    boolean isTowardsRight = dX > 0;
                    Drawable icon = isTowardsRight ? ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.quantum_ic_delete_vd_theme_24) : ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.quantum_ic_message_vd_theme_24);
                    int iconHorizontalMargin = 20;
                    int halfIconSize = Objects.requireNonNull(icon).getIntrinsicHeight() / 2;
                    int top = viewHolder.itemView.getTop() + ((viewHolder.itemView.getBottom() - viewHolder.itemView.getTop()) / 2 - halfIconSize);
                    if (dX > 0) { // Right swipe
                        canvas.clipRect(viewHolder.itemView.getLeft(), viewHolder.itemView.getTop(), viewHolder.itemView.getLeft() + (int) dX, viewHolder.itemView.getBottom());
                        icon.setBounds(viewHolder.itemView.getLeft() + iconHorizontalMargin, top, viewHolder.itemView.getLeft() + iconHorizontalMargin + icon.getIntrinsicWidth(), top + icon.getIntrinsicHeight()
                        );
                    } else if (dX < 0) { // Left swipe
                        canvas.clipRect(viewHolder.itemView.getRight() + (int) dX, viewHolder.itemView.getTop(), viewHolder.itemView.getRight(), viewHolder.itemView.getBottom());
                        int imgLeft = viewHolder.itemView.getRight() - iconHorizontalMargin - halfIconSize * 2;
                        icon.setBounds(imgLeft, top, viewHolder.itemView.getRight() - iconHorizontalMargin, top + icon.getIntrinsicHeight());
                    }
                    icon.draw(canvas);
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        if (ConfigProviderComponent.get(requireContext())
                .getConfigProvider()
                .getBoolean("is_call_log_item_anim_null", false)) {
            recyclerView.setItemAnimator(null);
        }
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(
                new RecyclerViewJankLogger(
                        MetricsComponent.get(requireContext()).metrics(), Metrics.OLD_CALL_LOG_JANK_EVENT_NAME));
        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        PerformanceReport.logOnScrollStateChange(recyclerView);
        emptyListView = view.findViewById(R.id.empty_list_view);
        emptyListView.setImage(R.drawable.empty_call_log);
        emptyListView.setActionClickedListener(this);
        modalAlertView = view.findViewById(R.id.modal_message_container);
        modalAlertManager =
                new CallLogModalAlertManager(LayoutInflater.from(getContext()), modalAlertView, this);
        multiSelectUnSelectAllViewContent =
                view.findViewById(R.id.multi_select_select_all_view_content);
        TextView selectUnselectAllViewText = view.findViewById(R.id.select_all_view_text);
        selectUnselectAllIcon = view.findViewById(R.id.select_all_view_icon);
        multiSelectUnSelectAllViewContent.setOnClickListener(null);
        selectUnselectAllIcon.setOnClickListener(this);
        selectUnselectAllViewText.setOnClickListener(this);
    }

    protected void setupData() {
        int activityType =
                isCallLogActivity
                        ? CallLogAdapter.ACTIVITY_TYPE_CALL_LOG
                        : CallLogAdapter.ACTIVITY_TYPE_DIALTACTS;
        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());

        contactInfoCache =
                new ContactInfoCache(attach(getChildFragmentManager()).getRetainedCache(),
                        new ContactInfoHelper(getActivity(), currentCountryIso),
                        onContactInfoChangedListener);
        adapter =
                Bindings.getLegacy(getActivity())
                        .newCallLogAdapter(
                                getActivity(),
                                recyclerView,
                                this,
                                this,
                                // We aren't calling getParentUnsafe because CallLogActivity doesn't need to
                                // implement this listener
                                FragmentUtils.getParent(
                                        this, CallLogAdapter.OnActionModeStateChangedListener.class),
                                new CallLogCache(getActivity()),
                                contactInfoCache,
                                new FilteredNumberAsyncQueryHandler(requireActivity()),
                                activityType);
        recyclerView.setAdapter(adapter);
        if (adapter.getOnScrollListener() != null) {
            recyclerView.addOnScrollListener(adapter.getOnScrollListener());
        }
        fetchCalls();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtil.enterBlock("CallLogFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        setupData();
        updateSelectAllState(savedInstanceState);
        adapter.onRestoreInstanceState(savedInstanceState);
    }

    private void updateSelectAllState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_SELECT_ALL_MODE, false)) {
                updateSelectAllIcon();
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateEmptyMessage(callTypeFilter);
    }

    @Override
    public void onResume() {
        LogUtil.enterBlock("CallLogFragment.onResume");
        super.onResume();
        final boolean hasReadCallLogPermission =
                PermissionsUtil.hasPermission(getActivity(), READ_CALL_LOG);
        if (!this.hasReadCallLogPermission && hasReadCallLogPermission) {
            // We didn't have the permission before, and now we do. Force a refresh of the call log.
            // Note that this code path always happens on a fresh start, but mRefreshDataRequired
            // is already true in that case anyway.
            refreshDataRequired = true;
            updateEmptyMessage(callTypeFilter);
        }

        ContentResolver resolver = requireActivity().getContentResolver();
        if (PermissionsUtil.hasCallLogReadPermissions(getContext())) {
            resolver.registerContentObserver(CallLog.CONTENT_URI, true, callLogObserver);
        } else {
            LogUtil.w("CallLogFragment.onCreate", "call log permission not available");
        }
        if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
            resolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true, contactsObserver);
        } else {
            LogUtil.w("CallLogFragment.onCreate", "contacts permission not available.");
        }

        this.hasReadCallLogPermission = hasReadCallLogPermission;

        /*
         * Always clear the filtered numbers cache since users could have blocked/unblocked numbers
         * from the settings page
         */
        adapter.clearFilteredNumbersCache();
        refreshData();
        adapter.onResume();

        rescheduleDisplayUpdate();
        // onResume() may also be called as a "side" page on the ViewPager, which is not visible.
        if (getUserVisibleHint()) {
            onVisible();
        }
    }

    @Override
    public void onPause() {
        LogUtil.enterBlock("CallLogFragment.onPause");
        requireActivity().getContentResolver().unregisterContentObserver(callLogObserver);
        requireActivity().getContentResolver().unregisterContentObserver(contactsObserver);
        if (getUserVisibleHint()) {
            onNotVisible();
        }
        cancelDisplayUpdate();
        adapter.onPause();
        super.onPause();
    }

    @Override
    public void onStart() {
        LogUtil.enterBlock("CallLogFragment.onStart");
        super.onStart();
        CequintCallerIdManager cequintCallerIdManager = null;
        if (CequintCallerIdManager.isCequintCallerIdEnabled(requireContext())) {
            cequintCallerIdManager = new CequintCallerIdManager();
        }
        contactInfoCache.setCequintCallerIdManager(cequintCallerIdManager);
    }

    @Override
    public void onStop() {
        LogUtil.enterBlock("CallLogFragment.onStop");
        super.onStop();
        adapter.onStop();
        contactInfoCache.stop();
    }

    @Override
    public void onDestroy() {
        LogUtil.enterBlock("CallLogFragment.onDestroy");
        if (adapter != null) {
            adapter.changeCursor(null);
        }
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FILTER_TYPE, callTypeFilter);
        outState.putInt(KEY_LOG_LIMIT, logLimit);
        outState.putLong(KEY_DATE_LIMIT, dateLimit);
        outState.putBoolean(KEY_IS_CALL_LOG_ACTIVITY, isCallLogActivity);
        outState.putBoolean(KEY_HAS_READ_CALL_LOG_PERMISSION, hasReadCallLogPermission);
        outState.putBoolean(KEY_REFRESH_DATA_REQUIRED, refreshDataRequired);
        outState.putBoolean(KEY_SELECT_ALL_MODE, selectAllMode);
        if (adapter != null) {
            adapter.onSaveInstanceState(outState);
        }
    }

    @Override
    public void fetchCalls() {
        callLogQueryHandler.fetchCalls(callTypeFilter, dateLimit);
        if (!isCallLogActivity
                && getActivity() != null
                && !getActivity().isFinishing()
                && FragmentUtils.getParent(this, CallLogFragmentListener.class) != null) {
            FragmentUtils.getParentUnsafe(this, CallLogFragmentListener.class).updateTabUnreadCounts();
        }
    }

    private void updateEmptyMessage(int filterType) {
        final Context context = getActivity();
        if (context == null) {
            return;
        }

        if (!PermissionsUtil.hasPermission(context, READ_CALL_LOG)) {
            emptyListView.setDescription(R.string.permission_no_calllog);
            emptyListView.setActionLabel(R.string.permission_single_turn_on);
            return;
        }

        final int messageId;
        switch (filterType) {
            case Calls.MISSED_TYPE:
                messageId = R.string.call_log_missed_empty;
                break;
            case Calls.VOICEMAIL_TYPE:
                messageId = R.string.call_log_voicemail_empty;
                break;
            case CallLogQueryHandler.CALL_TYPE_ALL:
                messageId = R.string.call_log_all_empty;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unexpected filter type in CallLogFragment: " + filterType);
        }
        emptyListView.setDescription(messageId);
        if (isCallLogActivity) {
            emptyListView.setActionLabel(EmptyContentView.NO_LABEL);
        } else if (filterType == CallLogQueryHandler.CALL_TYPE_ALL) {
            emptyListView.setActionLabel(R.string.call_log_all_empty_action);
        } else {
            emptyListView.setActionLabel(EmptyContentView.NO_LABEL);
        }
    }

    public CallLogAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (this.menuVisible != menuVisible) {
            this.menuVisible = menuVisible;
            if (menuVisible && isResumed()) {
                refreshData();
            }
        }
    }

    /**
     * Requests updates to the data to be shown.
     */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (refreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so they will be looked up
            // again once being shown.
            contactInfoCache.invalidate();
            adapter.setLoading(true);

            fetchCalls();
            callLogQueryHandler.fetchMissedCallsUnreadCount();
            refreshDataRequired = false;
        } else {
            // Refresh the display of the existing data to update the timestamp text descriptions.
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (!isCallLogActivity) {
            LogUtil.i("CallLogFragment.onEmptyViewActionButtonClicked", "showing dialpad");
            // Show dialpad if we are not in the call log activity.
            FragmentUtils.getParentUnsafe(this, HostInterface.class).showDialpad();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PHONE_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                // Force a refresh of the data since we were missing the permission before this.
                refreshDataRequired = true;
            }
        }
    }

    /**
     * Schedules an update to the relative call times (X mins ago).
     */
    private void rescheduleDisplayUpdate() {
        if (!displayUpdateHandler.hasMessages(EVENT_UPDATE_DISPLAY)) {
            long time = System.currentTimeMillis();
            // This value allows us to change the display relatively close to when the time changes
            // from one minute to the next.
            long millisUtilNextMinute = MILLIS_IN_MINUTE - (time % MILLIS_IN_MINUTE);
            displayUpdateHandler.sendEmptyMessageDelayed(EVENT_UPDATE_DISPLAY, millisUtilNextMinute);
        }
    }

    /**
     * Cancels any pending update requests to update the relative call times (X mins ago).
     */
    private void cancelDisplayUpdate() {
        displayUpdateHandler.removeMessages(EVENT_UPDATE_DISPLAY);
    }

    /**
     * Mark all missed calls as read if Keyguard not locked and possible.
     */
    void markMissedCallsAsReadAndRemoveNotifications() {
        if (callLogQueryHandler != null
                && !requireContext().getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            callLogQueryHandler.markMissedCallsAsRead();
            CallLogNotificationsService.cancelAllMissedCalls(getContext());
        }
    }

    public void onVisible() {
        LogUtil.enterBlock("CallLogFragment.onPageSelected");
        if (getActivity() != null && FragmentUtils.getParent(this, HostInterface.class) != null) {
            FragmentUtils.getParentUnsafe(this, HostInterface.class)
                    .enableFloatingButton(!isModalAlertVisible());
        }
    }

    public boolean isModalAlertVisible() {
        return modalAlertManager != null && !modalAlertManager.isEmpty();
    }

    @CallSuper
    public void onNotVisible() {
        LogUtil.enterBlock("CallLogFragment.onPageUnselected");
    }

    @Override
    public void onShowModalAlert(boolean show) {
        LogUtil.d(
                "CallLogFragment.onShowModalAlert",
                "show: %b, fragment: %s, isVisible: %b",
                show,
                this,
                getUserVisibleHint());
        getAdapter().notifyDataSetChanged();
        HostInterface hostInterface = FragmentUtils.getParent(this, HostInterface.class);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            modalAlertView.setVisibility(View.VISIBLE);
            if (hostInterface != null && getUserVisibleHint()) {
                hostInterface.enableFloatingButton(false);
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            modalAlertView.setVisibility(View.GONE);
            if (hostInterface != null && getUserVisibleHint()) {
                hostInterface.enableFloatingButton(true);
            }
        }
    }

    @Override
    public void showMultiSelectRemoveView(boolean show) {
        multiSelectUnSelectAllViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
        multiSelectUnSelectAllViewContent.setAlpha(show ? 0 : 1);
        multiSelectUnSelectAllViewContent.animate().alpha(show ? 1 : 0).start();
        if (show) {
            FragmentUtils.getParentUnsafe(this, CallLogFragmentListener.class)
                    .showMultiSelectRemoveView(true);
        } else {
            // This method is called after onDestroy. In DialtactsActivity, ListsFragment implements this
            // interface and never goes away with configuration changes so this is safe. MainActivity
            // removes that extra layer though, so we need to check if the parent is still there.
            CallLogFragmentListener listener =
                    FragmentUtils.getParent(this, CallLogFragmentListener.class);
            if (listener != null) {
                listener.showMultiSelectRemoveView(false);
            }
        }
    }

    @Override
    public void setSelectAllModeToFalse() {
        selectAllMode = false;
        selectUnselectAllIcon.setImageDrawable(
                getContext().getDrawable(R.drawable.ic_empty_check_mark_white_24dp));
    }

    @Override
    public void tapSelectAll() {
        LogUtil.i("CallLogFragment.tapSelectAll", "imitating select all");
        selectAllMode = true;
        updateSelectAllIcon();
    }

    @Override
    public void onClick(View v) {
        selectAllMode = !selectAllMode;
        if (selectAllMode) {
            Logger.get(v.getContext()).logImpression(DialerImpression.Type.MULTISELECT_SELECT_ALL);
        } else {
            Logger.get(v.getContext()).logImpression(DialerImpression.Type.MULTISELECT_UNSELECT_ALL);
        }
        updateSelectAllIcon();
    }

    private void updateSelectAllIcon() {
        if (selectAllMode) {
            selectUnselectAllIcon.setImageDrawable(
                    getContext().getDrawable(R.drawable.ic_check_mark_blue_24dp));
            getAdapter().onAllSelected();
        } else {
            selectUnselectAllIcon.setImageDrawable(
                    getContext().getDrawable(R.drawable.ic_empty_check_mark_white_24dp));
            getAdapter().onAllDeselected();
        }
    }

    public interface HostInterface {

        void showDialpad();

        void enableFloatingButton(boolean enabled);
    }

    /**
     * Useful callback for ListsFragment children to use to call into ListsFragment.
     */
    public interface CallLogFragmentListener {

        /**
         * External method to update unread count because the unread count changes when the user expands
         * a voicemail in the call log or when the user expands an unread call in the call history tab.
         */
        void updateTabUnreadCounts();

        void showMultiSelectRemoveView(boolean show);
    }

    protected class CustomContentObserver extends ContentObserver {

        public CustomContentObserver() {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshDataRequired = true;
        }
    }




}
