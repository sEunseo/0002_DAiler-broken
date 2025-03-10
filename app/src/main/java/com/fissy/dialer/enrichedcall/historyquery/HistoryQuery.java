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
package com.fissy.dialer.enrichedcall.historyquery;

import androidx.annotation.NonNull;

import com.fissy.dialer.common.LogUtil;
import com.google.auto.value.AutoValue;

import java.util.Locale;

/**
 * Data object representing the pieces of information required to query for historical enriched call
 * data.
 */
@AutoValue
public abstract class HistoryQuery {

    @NonNull
    public static HistoryQuery create(@NonNull String number, long callStartTime, long callEndTime) {
        return new AutoValue_HistoryQuery(number, callStartTime, callEndTime);
    }

    public abstract String getNumber();

    public abstract long getCallStartTimestamp();

    public abstract long getCallEndTimestamp();

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "HistoryQuery{number: %s, callStartTimestamp: %d, callEndTimestamp: %d}",
                LogUtil.sanitizePhoneNumber(getNumber()), getCallStartTimestamp(), getCallEndTimestamp());
    }
}
