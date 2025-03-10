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
 * limitations under the License.
 */

package com.android.incallui.async;

import java.util.concurrent.Executor;

/**
 * Executor that can be used to easily synchronize testing and production code. Production code
 * should call {@link #milestone()} at points in the code where the state of the system is worthy of
 * testing. In a test scenario, this method will pause execution until the test acknowledges the
 * milestone through the use of {@link #ackMilestoneForTesting()}.
 */
public interface PausableExecutor extends Executor {

    /**
     * Method called from asynchronous production code to inform this executor that it has reached a
     * point that puts the system into a state worth testing. TestableExecutors intended for use in a
     * testing environment should cause the calling thread to block. In the production environment
     * this should be a no-op.
     */
    void milestone();

    /**
     * Method called from the test code to inform this executor that the state of the production
     * system at the current milestone has been sufficiently tested. Every milestone must be
     * acknowledged.
     */
    void ackMilestoneForTesting();

    /**
     * Method called from the test code to inform this executor that the tests are finished with all
     * milestones. Future calls to {@link #milestone()} or {@link #awaitMilestoneForTesting()} should
     * return immediately.
     */
    void ackAllMilestonesForTesting();

    /**
     * Method called from the test code to block until a milestone has been reached in the production
     * code.
     */
    void awaitMilestoneForTesting() throws InterruptedException;
}
