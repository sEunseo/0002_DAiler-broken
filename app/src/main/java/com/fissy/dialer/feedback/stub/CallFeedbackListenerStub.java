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
 * limitations under the License
 */

package com.fissy.dialer.feedback.stub;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.inject.ApplicationContext;

import javax.inject.Inject;

/**
 *
 */
public class CallFeedbackListenerStub implements CallList.Listener {

    @Inject
    public CallFeedbackListenerStub(@ApplicationContext @NonNull Context context) {
        Context context1 = Assert.isNotNull(context);
    }

    @Override
    public void onIncomingCall(DialerCall call) {
    }

    @Override
    public void onUpgradeToVideo(DialerCall call) {
    }

    @Override
    public void onSessionModificationStateChange(DialerCall call) {
    }

    @Override
    public void onCallListChange(CallList callList) {
    }

    @Override
    public void onDisconnect(DialerCall call) {
    }

    @Override
    public void onWiFiToLteHandover(DialerCall call) {
    }

    @Override
    public void onHandoverToWifiFailed(DialerCall call) {
    }

    @Override
    public void onInternationalCallOnWifi(@NonNull DialerCall call) {
    }
}
