/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.answer.impl.answermethod;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.incallui.util.AccessibilityUtil;
import com.fissy.dialer.R;
import com.fissy.dialer.common.LogUtil;

import java.util.Objects;

/**
 * Creates the appropriate {@link AnswerMethod} for the circumstances.
 */
public class AnswerMethodFactory {

    @NonNull
    public static AnswerMethod createAnswerMethod(@NonNull Activity activity) {

        if (needTwoButton(activity)) {
            return new TwoButtonMethod();
        } else {
            return new FlingUpDownMethod();
        }
    }

    public static boolean needsReplacement(@Nullable Fragment answerMethod) {
        //noinspection SimplifiableIfStatement
        if (answerMethod == null) {
            return true;
        }
        // If we have already started showing TwoButtonMethod, we should keep showing TwoButtonMethod.
        // Otherwise check if we need to change to TwoButtonMethod
        return !(answerMethod instanceof TwoButtonMethod) && needTwoButton(Objects.requireNonNull(answerMethod.getActivity()));
    }


    private static boolean needTwoButton(@NonNull Activity activity) {
        if (activity.getResources().getBoolean(R.bool.two_button_show)) {
            LogUtil.i("AnswerMethodFactory.needTwoButton", "enabled for testing");
            return true;
        }

        return AccessibilityUtil.isTouchExplorationEnabled(activity) || activity.isInMultiWindowMode();
    }
}
