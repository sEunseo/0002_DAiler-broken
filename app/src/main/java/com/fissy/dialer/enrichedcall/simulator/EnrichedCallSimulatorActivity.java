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

package com.fissy.dialer.enrichedcall.simulator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fissy.dialer.R;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.enrichedcall.EnrichedCallComponent;
import com.fissy.dialer.enrichedcall.EnrichedCallManager;
import com.fissy.dialer.enrichedcall.EnrichedCallManager.StateChangedListener;

/**
 * Activity used to display Enriched call sessions that are currently in memory, and create new
 * outgoing sessions with various bits of data.
 *
 * <p>This activity will dynamically refresh as new sessions are added or updated, but there's no
 * update when sessions are deleted from memory. Use the refresh button to update the view.
 */
public class EnrichedCallSimulatorActivity extends AppCompatActivity
        implements StateChangedListener, OnClickListener {

    private Button refreshButton;
    private SessionsAdapter sessionsAdapter;

    public static Intent newIntent(@NonNull Context context) {
        return new Intent(Assert.isNotNull(context), EnrichedCallSimulatorActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        LogUtil.enterBlock("EnrichedCallSimulatorActivity.onCreate");
        super.onCreate(bundle);
        setContentView(R.layout.enriched_call_simulator_activity);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.enriched_call_simulator_activity);

        refreshButton = findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);

        RecyclerView recyclerView = findViewById(R.id.sessions_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        sessionsAdapter = new SessionsAdapter();
        sessionsAdapter.setSessionStrings(getEnrichedCallManager().getAllSessionsForDisplay());
        recyclerView.setAdapter(sessionsAdapter);
    }

    @Override
    protected void onResume() {
        LogUtil.enterBlock("EnrichedCallSimulatorActivity.onResume");
        super.onResume();
        getEnrichedCallManager().registerStateChangedListener(this);
    }

    @Override
    protected void onPause() {
        LogUtil.enterBlock("EnrichedCallSimulatorActivity.onPause");
        super.onPause();
        getEnrichedCallManager().unregisterStateChangedListener(this);
    }

    @Override
    public void onEnrichedCallStateChanged() {
        LogUtil.enterBlock("EnrichedCallSimulatorActivity.onEnrichedCallStateChanged");
        refreshSessions();
    }

    @Override
    public void onClick(View v) {
        if (v == refreshButton) {
            LogUtil.i("EnrichedCallSimulatorActivity.onClick", "refreshing sessions");
            refreshSessions();
        }
    }

    private void refreshSessions() {
        sessionsAdapter.setSessionStrings(getEnrichedCallManager().getAllSessionsForDisplay());
        sessionsAdapter.notifyDataSetChanged();
    }

    private EnrichedCallManager getEnrichedCallManager() {
        return EnrichedCallComponent.get(this).getEnrichedCallManager();
    }
}
