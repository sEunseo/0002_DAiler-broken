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

/**
 * A classifier which looks at the general direction of a stroke and evaluates it depending on the
 * type of action that takes place.
 */
public class DirectionClassifier extends StrokeClassifier {
    public DirectionClassifier(ClassifierData classifierData) {
    }

    @Override
    public String getTag() {
        return "DIR";
    }

    @Override
    public float getFalseTouchEvaluation(Stroke stroke) {
        Point firstPoint = stroke.getPoints().get(0);
        Point lastPoint = stroke.getPoints().get(stroke.getPoints().size() - 1);
        return DirectionEvaluator.evaluate(lastPoint.x - firstPoint.x, lastPoint.y - firstPoint.y);
    }
}
