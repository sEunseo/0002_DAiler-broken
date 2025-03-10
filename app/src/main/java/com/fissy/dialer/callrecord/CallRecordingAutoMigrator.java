/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2020 The LineageOS Project
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

package com.fissy.dialer.callrecord;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.DialerExecutor.Worker;
import com.fissy.dialer.common.concurrent.DialerExecutorFactory;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;

public class CallRecordingAutoMigrator {
    private static final String TAG = "CallRecordingAutoMigrator";

    @NonNull
    private final Context appContext;
    @NonNull
    private final DialerExecutorFactory dialerExecutorFactory;

    public CallRecordingAutoMigrator(
            @NonNull Context appContext,
            @NonNull DialerExecutorFactory dialerExecutorFactory) {
        this.appContext = Assert.isNotNull(appContext);
        this.dialerExecutorFactory = Assert.isNotNull(dialerExecutorFactory);
    }

    public void asyncAutoMigrate() {
        dialerExecutorFactory
                .createNonUiTaskBuilder(new ShouldAttemptAutoMigrate(appContext))
                .onSuccess(this::autoMigrate)
                .build()
                .executeParallel(null);
    }

    private void autoMigrate(boolean shouldAttemptAutoMigrate) {
        if (!shouldAttemptAutoMigrate) {
            return;
        }

        final com.fissy.dialer.callrecord.CallRecordingDataStore store = new com.fissy.dialer.callrecord.CallRecordingDataStore();
        store.open(appContext);

        final ContentResolver cr = appContext.getContentResolver();
        final SparseArray<CallRecording> oldRecordingData = store.getUnmigratedRecordingData();
        final File dir = Environment.getExternalStoragePublicDirectory("CallRecordings");
        for (File recording : Objects.requireNonNull(dir.listFiles())) {
            OutputStream os = null;
            try {
                // determine data store ID and call creation time of recording
                int id = -1;
                long creationTime = System.currentTimeMillis();
                for (int i = 0; i < oldRecordingData.size(); i++) {
                    if (TextUtils.equals(recording.getName(), oldRecordingData.valueAt(i).fileName)) {
                        creationTime = oldRecordingData.valueAt(i).creationTime;
                        id = oldRecordingData.keyAt(i);
                        break;
                    }
                }

                // create media store entry for recording
                Uri uri = cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        CallRecording.generateMediaInsertValues(recording.getName(), creationTime));
                os = cr.openOutputStream(uri);

                // copy file contents to media store stream
                Files.copy(recording.toPath(), os);

                // insert media store id to store
                if (id >= 0) {
                    store.updateMigratedRecording(id, Integer.parseInt(uri.getLastPathSegment()));
                }

                // mark recording as complete
                cr.update(uri, CallRecording.generateCompletedValues(), null, null);

                // delete file
                LogUtil.i(TAG, "Successfully migrated recording " + recording + " (ID " + id + ")");
                recording.delete();
            } catch (IOException e) {
                LogUtil.w(TAG, "Failed migrating call recording " + recording, e);
            } finally {
                if (os != null) {
                    IOUtils.closeQuietly(os);
                }
            }
        }

        if (Objects.requireNonNull(dir.listFiles()).length == 0) {
            dir.delete();
        }

        store.close();
    }

    private static class ShouldAttemptAutoMigrate implements Worker<Void, Boolean> {

        ShouldAttemptAutoMigrate(Context appContext) {
        }

        @Nullable
        @Override
        public Boolean doInBackground(@Nullable Void input) {

            final File dir = Environment.getExternalStoragePublicDirectory("CallRecordings");
            if (!dir.exists()) {
                LogUtil.i(TAG, "not attempting auto-migrate: no recordings present");
                return false;
            }

            return true;
        }
    }
}
