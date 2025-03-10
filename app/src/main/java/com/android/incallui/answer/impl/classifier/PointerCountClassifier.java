/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.incallui.answer.impl.classifier;

import android.view.MotionEvent;

/**
 * A classifier which looks at the total number of traces in the whole gesture.
 */
class PointerCountClassifier extends GestureClassifier {
    private int count;

    public PointerCountClassifier(ClassifierData classifierData) {
        count = 0;
    }

    @Override
    public String getTag() {
        return "PTR_CNT";
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            count = 1;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            ++count;
        }
    }

    @Override
    public float getFalseTouchEvaluation() {
        return PointerCountEvaluator.evaluate(count);
    }
}
