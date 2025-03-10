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
 * limitations under the License.
 */

package com.android.contacts.common.util;

import com.fissy.dialer.common.LogUtil;

import java.util.ArrayList;

/**
 * A {@link StopWatch} records start, laps and stop, and print them to logcat.
 */
public class StopWatch {

    private final String mLabel;

    private final ArrayList<Long> mTimes = new ArrayList<>();
    private final ArrayList<String> mLapLabels = new ArrayList<>();

    private StopWatch(String label) {
        mLabel = label;
        lap("");
    }

    /**
     * Create a new instance and start it.
     */
    public static StopWatch start(String label) {
        return new StopWatch(label);
    }

    /**
     * Record a lap.
     */
    public void lap(String lapLabel) {
        mTimes.add(System.currentTimeMillis());
        mLapLabels.add(lapLabel);
    }

    /**
     * Stop it and log the result, if the total time >= {@code timeThresholdToLog}.
     */
    public void stopAndLog(String TAG, int timeThresholdToLog) {

        lap("");

        final long start = mTimes.get(0);
        final long stop = mTimes.get(mTimes.size() - 1);

        final long total = stop - start;
        if (total < timeThresholdToLog) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(mLabel);
        sb.append(",");
        sb.append(total);
        sb.append(": ");

        long last = start;
        for (int i = 1; i < mTimes.size(); i++) {
            final long current = mTimes.get(i);
            sb.append(mLapLabels.get(i));
            sb.append(",");
            sb.append((current - last));
            sb.append(" ");
            last = current;
        }
        LogUtil.v(TAG, sb.toString());
    }
}
