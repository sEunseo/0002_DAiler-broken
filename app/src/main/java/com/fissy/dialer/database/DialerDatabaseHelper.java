/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.fissy.dialer.database;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.contacts.common.util.StopWatch;
import com.fissy.dialer.R;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.common.concurrent.DefaultFutureCallback;
import com.fissy.dialer.common.concurrent.DialerExecutorComponent;
import com.fissy.dialer.common.concurrent.DialerFutureSerializer;
import com.fissy.dialer.common.database.Selection;
import com.fissy.dialer.configprovider.ConfigProviderComponent;
import com.fissy.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.fissy.dialer.smartdial.util.SmartDialNameMatcher;
import com.fissy.dialer.smartdial.util.SmartDialPrefix;
import com.fissy.dialer.util.PermissionsUtil;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Database helper for smart dial. Designed as a singleton to make sure there is only one access
 * point to the database. Provides methods to maintain, update, and query the database.
 */
public class DialerDatabaseHelper extends SQLiteOpenHelper {

    /**
     * SmartDial DB version ranges:
     *
     * <pre>
     *   0-98   KitKat
     * </pre>
     */
    public static final int DATABASE_VERSION = 10;

    public static final String DATABASE_NAME = "dialer.db";

    public static final String ACTION_SMART_DIAL_UPDATED =
            "com.fissy.dialer.database.ACTION_SMART_DIAL_UPDATED";
    @VisibleForTesting
    static final String DEFAULT_LAST_UPDATED_CONFIG_KEY = "smart_dial_default_last_update_millis";
    private static final String TAG = "DialerDatabaseHelper";
    private static final boolean DEBUG = false;
    /**
     * Saves the last update time of smart dial databases to shared preferences.
     */
    private static final String DATABASE_LAST_CREATED_SHARED_PREF = "com.fissy.dialer";
    private static final String LAST_UPDATED_MILLIS = "last_updated_millis";
    private static final String DATABASE_VERSION_PROPERTY = "database_version";
    private static final int MAX_ENTRIES = 20;

    private final Context context;
    private final DialerFutureSerializer dialerFutureSerializer = new DialerFutureSerializer();

    private boolean isTestInstance = false;

    protected DialerDatabaseHelper(Context context) {
        super(context, DialerDatabaseHelper.DATABASE_NAME, null, DialerDatabaseHelper.DATABASE_VERSION);
        this.context = Objects.requireNonNull(context, "Context must not be null");
    }

    public void setIsTestInstance(boolean isTestInstance) {
        this.isTestInstance = isTestInstance;
    }

    /**
     * Creates tables in the database when database is created for the first time.
     *
     * @param db The database.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        setupTables(db);
    }

    private void setupTables(SQLiteDatabase db) {
        dropTables(db);
        db.execSQL(
                "CREATE TABLE "
                        + Tables.SMARTDIAL_TABLE
                        + " ("
                        + SmartDialDbColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + SmartDialDbColumns.DATA_ID
                        + " INTEGER, "
                        + SmartDialDbColumns.NUMBER
                        + " TEXT,"
                        + SmartDialDbColumns.CONTACT_ID
                        + " INTEGER,"
                        + SmartDialDbColumns.LOOKUP_KEY
                        + " TEXT,"
                        + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                        + " TEXT, "
                        + SmartDialDbColumns.PHOTO_ID
                        + " INTEGER, "
                        + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME
                        + " LONG, "
                        + SmartDialDbColumns.LAST_TIME_USED
                        + " LONG, "
                        + SmartDialDbColumns.TIMES_USED
                        + " INTEGER, "
                        + SmartDialDbColumns.STARRED
                        + " INTEGER, "
                        + SmartDialDbColumns.IS_SUPER_PRIMARY
                        + " INTEGER, "
                        + SmartDialDbColumns.IN_VISIBLE_GROUP
                        + " INTEGER, "
                        + SmartDialDbColumns.IS_PRIMARY
                        + " INTEGER, "
                        + SmartDialDbColumns.CARRIER_PRESENCE
                        + " INTEGER NOT NULL DEFAULT 0"
                        + ");");

        db.execSQL(
                "CREATE TABLE "
                        + Tables.PREFIX_TABLE
                        + " ("
                        + PrefixColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + PrefixColumns.PREFIX
                        + " TEXT COLLATE NOCASE, "
                        + PrefixColumns.CONTACT_ID
                        + " INTEGER"
                        + ");");

        db.execSQL(
                "CREATE TABLE "
                        + Tables.PROPERTIES
                        + " ("
                        + PropertiesColumns.PROPERTY_KEY
                        + " TEXT PRIMARY KEY, "
                        + PropertiesColumns.PROPERTY_VALUE
                        + " TEXT "
                        + ");");

        // This will need to also be updated in setupTablesForFilteredNumberTest and onUpgrade.
        // Hardcoded so we know on glance what columns are updated in setupTables,
        // and to be able to guarantee the state of the DB at each upgrade step.
        db.execSQL(
                "CREATE TABLE "
                        + Tables.FILTERED_NUMBER_TABLE
                        + " ("
                        + FilteredNumberColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + FilteredNumberColumns.NORMALIZED_NUMBER
                        + " TEXT UNIQUE,"
                        + FilteredNumberColumns.NUMBER
                        + " TEXT,"
                        + FilteredNumberColumns.COUNTRY_ISO
                        + " TEXT,"
                        + FilteredNumberColumns.TIMES_FILTERED
                        + " INTEGER,"
                        + FilteredNumberColumns.LAST_TIME_FILTERED
                        + " LONG,"
                        + FilteredNumberColumns.CREATION_TIME
                        + " LONG,"
                        + FilteredNumberColumns.TYPE
                        + " INTEGER,"
                        + FilteredNumberColumns.SOURCE
                        + " INTEGER"
                        + ");");

        setProperty(db, DATABASE_VERSION_PROPERTY, String.valueOf(DATABASE_VERSION));
        if (!isTestInstance) {
            resetSmartDialLastUpdatedTime();
        }
    }

    public void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PREFIX_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SMARTDIAL_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PROPERTIES);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.FILTERED_NUMBER_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.VOICEMAIL_ARCHIVE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldNumber, int newNumber) {
        // Disregard the old version and new versions provided by SQLiteOpenHelper, we will read
        // our own from the database.

        int oldVersion;

        oldVersion = getPropertyAsInt(db, DATABASE_VERSION_PROPERTY, 0);

        if (oldVersion == 0) {
            LogUtil.e(
                    "DialerDatabaseHelper.onUpgrade", "malformed database version..recreating database");
        }

        if (oldVersion < 4) {
            setupTables(db);
            return;
        }

        if (oldVersion < 7) {
            db.execSQL("DROP TABLE IF EXISTS " + Tables.FILTERED_NUMBER_TABLE);
            db.execSQL(
                    "CREATE TABLE "
                            + Tables.FILTERED_NUMBER_TABLE
                            + " ("
                            + FilteredNumberColumns._ID
                            + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + FilteredNumberColumns.NORMALIZED_NUMBER
                            + " TEXT UNIQUE,"
                            + FilteredNumberColumns.NUMBER
                            + " TEXT,"
                            + FilteredNumberColumns.COUNTRY_ISO
                            + " TEXT,"
                            + FilteredNumberColumns.TIMES_FILTERED
                            + " INTEGER,"
                            + FilteredNumberColumns.LAST_TIME_FILTERED
                            + " LONG,"
                            + FilteredNumberColumns.CREATION_TIME
                            + " LONG,"
                            + FilteredNumberColumns.TYPE
                            + " INTEGER,"
                            + FilteredNumberColumns.SOURCE
                            + " INTEGER"
                            + ");");
            oldVersion = 7;
        }

        if (oldVersion < 8) {
            upgradeToVersion8(db);
            oldVersion = 8;
        }

        if (oldVersion < 10) {
            db.execSQL("DROP TABLE IF EXISTS " + Tables.VOICEMAIL_ARCHIVE_TABLE);
            oldVersion = 10;
        }

        if (oldVersion != DATABASE_VERSION) {
            throw new IllegalStateException(
                    "error upgrading the database to version " + DATABASE_VERSION);
        }

        setProperty(db, DATABASE_VERSION_PROPERTY, String.valueOf(DATABASE_VERSION));
    }

    public void upgradeToVersion8(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE smartdial_table ADD carrier_presence INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Stores a key-value pair in the {@link Tables#PROPERTIES} table.
     */
    public void setProperty(String key, String value) {
        setProperty(getWritableDatabase(), key, value);
    }

    public void setProperty(SQLiteDatabase db, String key, String value) {
        final ContentValues values = new ContentValues();
        values.put(PropertiesColumns.PROPERTY_KEY, key);
        values.put(PropertiesColumns.PROPERTY_VALUE, value);
        db.replace(Tables.PROPERTIES, null, values);
    }

    /**
     * Returns the value from the {@link Tables#PROPERTIES} table.
     */
    public String getProperty(String key, String defaultValue) {
        return getProperty(getReadableDatabase(), key, defaultValue);
    }

    public String getProperty(SQLiteDatabase db, String key, String defaultValue) {
        try {
            String value = null;
            final Cursor cursor =
                    db.query(
                            Tables.PROPERTIES,
                            new String[]{PropertiesColumns.PROPERTY_VALUE},
                            PropertiesColumns.PROPERTY_KEY + "=?",
                            new String[]{key},
                            null,
                            null,
                            null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        value = cursor.getString(0);
                    }
                } finally {
                    cursor.close();
                }
            }
            return value != null ? value : defaultValue;
        } catch (SQLiteException e) {
            return defaultValue;
        }
    }

    public int getPropertyAsInt(SQLiteDatabase db, String key, int defaultValue) {
        final String stored = getProperty(db, key, "");
        try {
            return Integer.parseInt(stored);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void resetSmartDialLastUpdatedTime() {
        final SharedPreferences databaseLastUpdateSharedPref =
                context.getSharedPreferences(DATABASE_LAST_CREATED_SHARED_PREF, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = databaseLastUpdateSharedPref.edit();
        editor.putLong(LAST_UPDATED_MILLIS, 0);
        editor.apply();
    }

    /**
     * Starts the database upgrade process in the background.
     *
     * @see #updateSmartDialDatabase(boolean) for the usage of {@code forceUpdate}.
     */
    public void startSmartDialUpdateThread(boolean forceUpdate) {
        if (PermissionsUtil.hasContactsReadPermissions(context)) {
            Futures.addCallback(
                    // Serialize calls to updateSmartDialDatabase. Use FutureSerializer instead of
                    // synchronizing on the method to prevent deadlocking thread pool. FutureSerializer
                    // provides the guarantee that the next AsyncCallable won't even be submitted until the
                    // ListenableFuture returned by the previous one completes. See a bug.
                    dialerFutureSerializer.submit(
                            () -> {
                                updateSmartDialDatabase(forceUpdate);
                                return null;
                            },
                            DialerExecutorComponent.get(context).backgroundExecutor()),
                    new DefaultFutureCallback<>(),
                    MoreExecutors.directExecutor());
        }
    }

    /**
     * Removes rows in the smartdial database that matches the contacts that have been deleted by
     * other apps since last update.
     *
     * @param db                    Database to operate on.
     * @param lastUpdatedTimeMillis the last time at which an update to the smart dial database was
     *                              run.
     */
    private void removeDeletedContacts(SQLiteDatabase db, String lastUpdatedTimeMillis) {
        Cursor deletedContactCursor = getDeletedContactCursor(lastUpdatedTimeMillis);

        if (deletedContactCursor == null) {
            return;
        }

        db.beginTransaction();
        try {
            if (!deletedContactCursor.moveToFirst()) {
                return;
            }

            do {
                if (deletedContactCursor.isNull(DeleteContactQuery.DELETED_CONTACT_ID)) {
                    LogUtil.i(
                            "DialerDatabaseHelper.removeDeletedContacts",
                            "contact_id column null. Row was deleted during iteration, skipping");
                    continue;
                }

                long deleteContactId = deletedContactCursor.getLong(DeleteContactQuery.DELETED_CONTACT_ID);

                Selection smartDialSelection =
                        Selection.column(SmartDialDbColumns.CONTACT_ID).is("=", deleteContactId);
                db.delete(
                        Tables.SMARTDIAL_TABLE,
                        smartDialSelection.getSelection(),
                        smartDialSelection.getSelectionArgs());

                Selection prefixSelection =
                        Selection.column(PrefixColumns.CONTACT_ID).is("=", deleteContactId);
                db.delete(
                        Tables.PREFIX_TABLE,
                        prefixSelection.getSelection(),
                        prefixSelection.getSelectionArgs());
            } while (deletedContactCursor.moveToNext());

            db.setTransactionSuccessful();
        } finally {
            deletedContactCursor.close();
            db.endTransaction();
        }
    }

    private Cursor getDeletedContactCursor(String lastUpdateMillis) {
        return context
                .getContentResolver()
                .query(
                        DeleteContactQuery.URI,
                        DeleteContactQuery.PROJECTION,
                        DeleteContactQuery.SELECT_UPDATED_CLAUSE,
                        new String[]{lastUpdateMillis},
                        null);
    }

    /**
     * Removes potentially corrupted entries in the database. These contacts may be added before the
     * previous instance of the dialer was destroyed for some reason. For data integrity, we delete
     * all of them.
     *
     * @param db               Database pointer to the dialer database.
     * @param last_update_time Time stamp of last successful update of the dialer database.
     */
    private void removePotentiallyCorruptedContacts(SQLiteDatabase db, String last_update_time) {
        db.delete(
                Tables.PREFIX_TABLE,
                PrefixColumns.CONTACT_ID
                        + " IN "
                        + "(SELECT "
                        + SmartDialDbColumns.CONTACT_ID
                        + " FROM "
                        + Tables.SMARTDIAL_TABLE
                        + " WHERE "
                        + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME
                        + " > "
                        + last_update_time
                        + ")",
                null);
        db.delete(
                Tables.SMARTDIAL_TABLE,
                SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME + " > " + last_update_time,
                null);
    }

    /**
     * Removes rows in the smartdial database that matches updated contacts.
     *
     * @param db                   Database pointer to the smartdial database
     * @param updatedContactCursor Cursor pointing to the list of recently updated contacts.
     */
    @VisibleForTesting
    void removeUpdatedContacts(SQLiteDatabase db, Cursor updatedContactCursor) {
        db.beginTransaction();
        try {
            updatedContactCursor.moveToPosition(-1);
            while (updatedContactCursor.moveToNext()) {
                if (updatedContactCursor.isNull(UpdatedContactQuery.UPDATED_CONTACT_ID)) {
                    LogUtil.i(
                            "DialerDatabaseHelper.removeUpdatedContacts",
                            "contact_id column null. Row was deleted during iteration, skipping");
                    continue;
                }

                final long contactId = updatedContactCursor.getLong(UpdatedContactQuery.UPDATED_CONTACT_ID);

                db.delete(Tables.SMARTDIAL_TABLE, SmartDialDbColumns.CONTACT_ID + "=" + contactId, null);
                db.delete(Tables.PREFIX_TABLE, PrefixColumns.CONTACT_ID + "=" + contactId, null);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts updated contacts as rows to the smartdial table.
     *
     * @param db                   Database pointer to the smartdial database.
     * @param updatedContactCursor Cursor pointing to the list of recently updated contacts.
     * @param currentMillis        Current time to be recorded in the smartdial table as update timestamp.
     */
    @VisibleForTesting
    protected void insertUpdatedContactsAndNumberPrefix(
            SQLiteDatabase db, Cursor updatedContactCursor, Long currentMillis) {
        db.beginTransaction();
        try {
            final String sqlInsert =
                    "INSERT INTO "
                            + Tables.SMARTDIAL_TABLE
                            + " ("
                            + SmartDialDbColumns.DATA_ID
                            + ", "
                            + SmartDialDbColumns.NUMBER
                            + ", "
                            + SmartDialDbColumns.CONTACT_ID
                            + ", "
                            + SmartDialDbColumns.LOOKUP_KEY
                            + ", "
                            + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                            + ", "
                            + SmartDialDbColumns.PHOTO_ID
                            + ", "
                            + SmartDialDbColumns.LAST_TIME_USED
                            + ", "
                            + SmartDialDbColumns.TIMES_USED
                            + ", "
                            + SmartDialDbColumns.STARRED
                            + ", "
                            + SmartDialDbColumns.IS_SUPER_PRIMARY
                            + ", "
                            + SmartDialDbColumns.IN_VISIBLE_GROUP
                            + ", "
                            + SmartDialDbColumns.IS_PRIMARY
                            + ", "
                            + SmartDialDbColumns.CARRIER_PRESENCE
                            + ", "
                            + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME
                            + ") "
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            final SQLiteStatement insert = db.compileStatement(sqlInsert);

            final String numberSqlInsert =
                    "INSERT INTO "
                            + Tables.PREFIX_TABLE
                            + " ("
                            + PrefixColumns.CONTACT_ID
                            + ", "
                            + PrefixColumns.PREFIX
                            + ") "
                            + " VALUES (?, ?)";
            final SQLiteStatement numberInsert = db.compileStatement(numberSqlInsert);

            updatedContactCursor.moveToPosition(-1);
            while (updatedContactCursor.moveToNext()) {
                insert.clearBindings();

                if (updatedContactCursor.isNull(PhoneQuery.PHONE_ID)) {
                    LogUtil.i(
                            "DialerDatabaseHelper.insertUpdatedContactsAndNumberPrefix",
                            "_id column null. Row was deleted during iteration, skipping");
                    continue;
                }

                // Handle string columns which can possibly be null first. In the case of certain
                // null columns (due to malformed rows possibly inserted by third-party apps
                // or sync adapters), skip the phone number row.
                final String number = updatedContactCursor.getString(PhoneQuery.PHONE_NUMBER);
                if (TextUtils.isEmpty(number)) {
                    continue;
                } else {
                    insert.bindString(2, number);
                }

                final String lookupKey = updatedContactCursor.getString(PhoneQuery.PHONE_LOOKUP_KEY);
                if (TextUtils.isEmpty(lookupKey)) {
                    continue;
                } else {
                    insert.bindString(4, lookupKey);
                }

                final String displayName = updatedContactCursor.getString(PhoneQuery.PHONE_DISPLAY_NAME);
                if (displayName == null) {
                    insert.bindString(5, context.getResources().getString(R.string.missing_name));
                } else {
                    insert.bindString(5, displayName);
                }
                insert.bindLong(1, updatedContactCursor.getLong(PhoneQuery.PHONE_ID));
                insert.bindLong(3, updatedContactCursor.getLong(PhoneQuery.PHONE_CONTACT_ID));
                insert.bindLong(6, updatedContactCursor.getLong(PhoneQuery.PHONE_PHOTO_ID));
                insert.bindLong(7, updatedContactCursor.getLong(PhoneQuery.PHONE_LAST_TIME_USED));
                insert.bindLong(8, updatedContactCursor.getInt(PhoneQuery.PHONE_TIMES_USED));
                insert.bindLong(9, updatedContactCursor.getInt(PhoneQuery.PHONE_STARRED));
                insert.bindLong(10, updatedContactCursor.getInt(PhoneQuery.PHONE_IS_SUPER_PRIMARY));
                insert.bindLong(11, updatedContactCursor.getInt(PhoneQuery.PHONE_IN_VISIBLE_GROUP));
                insert.bindLong(12, updatedContactCursor.getInt(PhoneQuery.PHONE_IS_PRIMARY));
                insert.bindLong(13, updatedContactCursor.getInt(PhoneQuery.PHONE_CARRIER_PRESENCE));
                insert.bindLong(14, currentMillis);
                insert.executeInsert();
                final String contactPhoneNumber = updatedContactCursor.getString(PhoneQuery.PHONE_NUMBER);
                final ArrayList<String> numberPrefixes =
                        SmartDialPrefix.parseToNumberTokens(context, contactPhoneNumber);

                for (String numberPrefix : numberPrefixes) {
                    numberInsert.bindLong(1, updatedContactCursor.getLong(PhoneQuery.PHONE_CONTACT_ID));
                    numberInsert.bindString(2, numberPrefix);
                    numberInsert.executeInsert();
                    numberInsert.clearBindings();
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts prefixes of contact names to the prefix table.
     *
     * @param db         Database pointer to the smartdial database.
     * @param nameCursor Cursor pointing to the list of distinct updated contacts.
     */
    @VisibleForTesting
    void insertNamePrefixes(SQLiteDatabase db, Cursor nameCursor) {
        final int columnIndexName = nameCursor.getColumnIndex(SmartDialDbColumns.DISPLAY_NAME_PRIMARY);
        final int columnIndexContactId = nameCursor.getColumnIndex(SmartDialDbColumns.CONTACT_ID);

        db.beginTransaction();
        try {
            final String sqlInsert =
                    "INSERT INTO "
                            + Tables.PREFIX_TABLE
                            + " ("
                            + PrefixColumns.CONTACT_ID
                            + ", "
                            + PrefixColumns.PREFIX
                            + ") "
                            + " VALUES (?, ?)";
            final SQLiteStatement insert = db.compileStatement(sqlInsert);

            while (nameCursor.moveToNext()) {
                if (nameCursor.isNull(columnIndexContactId)) {
                    LogUtil.i(
                            "DialerDatabaseHelper.insertNamePrefixes",
                            "contact_id column null. Row was deleted during iteration, skipping");
                    continue;
                }

                final ArrayList<String> namePrefixes =
                        SmartDialPrefix.generateNamePrefixes(context, nameCursor.getString(columnIndexName));

                for (String namePrefix : namePrefixes) {
                    insert.bindLong(1, nameCursor.getLong(columnIndexContactId));
                    insert.bindString(2, namePrefix);
                    insert.executeInsert();
                    insert.clearBindings();
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Updates the smart dial and prefix database. This method queries the Delta API to get changed
     * contacts since last update, and updates the records in smartdial database and prefix database
     * accordingly. It also queries the deleted contact database to remove newly deleted contacts
     * since last update.
     *
     * @param forceUpdate If set to true, update the database by reloading all contacts.
     */
    @WorkerThread
    public void updateSmartDialDatabase(boolean forceUpdate) {
        LogUtil.enterBlock("DialerDatabaseHelper.updateSmartDialDatabase");

        final SQLiteDatabase db = getWritableDatabase();

        LogUtil.v("DialerDatabaseHelper.updateSmartDialDatabase", "starting to update database");
        final StopWatch stopWatch = DEBUG ? StopWatch.start("Updating databases") : null;

        final SharedPreferences databaseLastUpdateSharedPref =
                context.getSharedPreferences(DATABASE_LAST_CREATED_SHARED_PREF, Context.MODE_PRIVATE);

        long defaultLastUpdateMillis =
                ConfigProviderComponent.get(context)
                        .getConfigProvider()
                        .getLong(DEFAULT_LAST_UPDATED_CONFIG_KEY, 0);

        long sharedPrefLastUpdateMillis =
                databaseLastUpdateSharedPref.getLong(LAST_UPDATED_MILLIS, defaultLastUpdateMillis);

        final String lastUpdateMillis = String.valueOf(forceUpdate ? 0 : sharedPrefLastUpdateMillis);

        LogUtil.i(
                "DialerDatabaseHelper.updateSmartDialDatabase", "last updated at %s", lastUpdateMillis);

        final long currentMillis = System.currentTimeMillis();

        if (DEBUG) {
            stopWatch.lap("Queried the Contacts database");
        }

        removeDeletedContacts(db, lastUpdateMillis);
        removePotentiallyCorruptedContacts(db, lastUpdateMillis);

        if (DEBUG) {
            stopWatch.lap("Finished deleting deleted entries");
        }

        if (!lastUpdateMillis.equals("0")) {
            final Cursor updatedContactCursor =
                    context
                            .getContentResolver()
                            .query(
                                    UpdatedContactQuery.URI,
                                    UpdatedContactQuery.PROJECTION,
                                    UpdatedContactQuery.SELECT_UPDATED_CLAUSE,
                                    new String[]{lastUpdateMillis},
                                    null);
            if (updatedContactCursor == null) {
                LogUtil.e(
                        "DialerDatabaseHelper.updateSmartDialDatabase",
                        "smartDial query received null for cursor");
                return;
            }
            try {
                removeUpdatedContacts(db, updatedContactCursor);
            } finally {
                updatedContactCursor.close();
            }
            if (DEBUG) {
                stopWatch.lap("Finished deleting entries belonging to updated contacts");
            }
        }

        final Cursor updatedPhoneCursor =
                context
                        .getContentResolver()
                        .query(
                                PhoneQuery.URI,
                                PhoneQuery.PROJECTION,
                                PhoneQuery.SELECTION,
                                new String[]{lastUpdateMillis},
                                null);
        if (updatedPhoneCursor == null) {
            LogUtil.e(
                    "DialerDatabaseHelper.updateSmartDialDatabase",
                    "smartDial query received null for cursor");
            return;
        }

        try {
            insertUpdatedContactsAndNumberPrefix(db, updatedPhoneCursor, currentMillis);
            if (DEBUG) {
                stopWatch.lap("Finished building the smart dial table");
            }
        } finally {
            updatedPhoneCursor.close();
        }

        final Cursor nameCursor =
                db.rawQuery(
                        "SELECT DISTINCT "
                                + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                                + ", "
                                + SmartDialDbColumns.CONTACT_ID
                                + " FROM "
                                + Tables.SMARTDIAL_TABLE
                                + " WHERE "
                                + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME
                                + " = "
                                + currentMillis,
                        new String[]{});
        if (nameCursor != null) {
            try {
                if (DEBUG) {
                    stopWatch.lap("Queried the smart dial table for contact names");
                }

                insertNamePrefixes(db, nameCursor);
                if (DEBUG) {
                    stopWatch.lap("Finished building the name prefix table");
                }
            } finally {
                nameCursor.close();
            }
        }

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS smartdial_contact_id_index ON "
                        + Tables.SMARTDIAL_TABLE
                        + " ("
                        + SmartDialDbColumns.CONTACT_ID
                        + ");");
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS smartdial_last_update_index ON "
                        + Tables.SMARTDIAL_TABLE
                        + " ("
                        + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME
                        + ");");
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS smartdial_sort_index ON "
                        + Tables.SMARTDIAL_TABLE
                        + " ("
                        + SmartDialDbColumns.STARRED
                        + ", "
                        + SmartDialDbColumns.IS_SUPER_PRIMARY
                        + ", "
                        + SmartDialDbColumns.LAST_TIME_USED
                        + ", "
                        + SmartDialDbColumns.TIMES_USED
                        + ", "
                        + SmartDialDbColumns.IN_VISIBLE_GROUP
                        + ", "
                        + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                        + ", "
                        + SmartDialDbColumns.CONTACT_ID
                        + ", "
                        + SmartDialDbColumns.IS_PRIMARY
                        + ");");
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS nameprefix_index ON "
                        + Tables.PREFIX_TABLE
                        + " ("
                        + PrefixColumns.PREFIX
                        + ");");
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS nameprefix_contact_id_index ON "
                        + Tables.PREFIX_TABLE
                        + " ("
                        + PrefixColumns.CONTACT_ID
                        + ");");

        if (DEBUG) {
            stopWatch.lap(TAG + "Finished recreating index");
        }

        db.execSQL("ANALYZE " + Tables.SMARTDIAL_TABLE);
        db.execSQL("ANALYZE " + Tables.PREFIX_TABLE);
        db.execSQL("ANALYZE smartdial_contact_id_index");
        db.execSQL("ANALYZE smartdial_last_update_index");
        db.execSQL("ANALYZE nameprefix_index");
        db.execSQL("ANALYZE nameprefix_contact_id_index");
        if (DEBUG) {
            stopWatch.stopAndLog(TAG + "Finished updating index stats", 0);
        }

        final SharedPreferences.Editor editor = databaseLastUpdateSharedPref.edit();
        editor.putLong(LAST_UPDATED_MILLIS, currentMillis);
        editor.apply();

        LogUtil.i("DialerDatabaseHelper.updateSmartDialDatabase", "broadcasting smart dial update");

        // Notify content observers that smart dial database has been updated.
        Intent intent = new Intent(ACTION_SMART_DIAL_UPDATED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /**
     * Returns a list of candidate contacts where the query is a prefix of the dialpad index of the
     * contact's name or phone number.
     *
     * @param query The prefix of a contact's dialpad index.
     * @return A list of top candidate contacts that will be suggested to user to match their input.
     */
    @WorkerThread
    public synchronized ArrayList<ContactNumber> getLooseMatches(
            String query, SmartDialNameMatcher nameMatcher) {
        final SQLiteDatabase db = getReadableDatabase();

        final String looseQuery = query + "%";

        final ArrayList<ContactNumber> result = new ArrayList<>();

        final StopWatch stopWatch = DEBUG ? StopWatch.start(":Name Prefix query") : null;

        final String currentTimeStamp = Long.toString(System.currentTimeMillis());

        final Cursor cursor =
                db.rawQuery(
                        "SELECT "
                                + SmartDialDbColumns.DATA_ID
                                + ", "
                                + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                                + ", "
                                + SmartDialDbColumns.PHOTO_ID
                                + ", "
                                + SmartDialDbColumns.NUMBER
                                + ", "
                                + SmartDialDbColumns.CONTACT_ID
                                + ", "
                                + SmartDialDbColumns.LOOKUP_KEY
                                + ", "
                                + SmartDialDbColumns.CARRIER_PRESENCE
                                + " FROM "
                                + Tables.SMARTDIAL_TABLE
                                + " WHERE "
                                + SmartDialDbColumns.CONTACT_ID
                                + " IN "
                                + " (SELECT "
                                + PrefixColumns.CONTACT_ID
                                + " FROM "
                                + Tables.PREFIX_TABLE
                                + " WHERE "
                                + Tables.PREFIX_TABLE
                                + "."
                                + PrefixColumns.PREFIX
                                + " LIKE '"
                                + looseQuery
                                + "')"
                                + " ORDER BY "
                                + SmartDialSortingOrder.SORT_ORDER,
                        new String[]{currentTimeStamp});
        if (cursor == null) {
            return result;
        }
        try {
            if (DEBUG) {
                stopWatch.lap("Prefix query completed");
            }

            final int columnDataId = 0;
            final int columnDisplayNamePrimary = 1;
            final int columnPhotoId = 2;
            final int columnNumber = 3;
            final int columnId = 4;
            final int columnLookupKey = 5;
            final int columnCarrierPresence = 6;
            if (DEBUG) {
                stopWatch.lap("Found column IDs");
            }

            final Set<ContactMatch> duplicates = new HashSet<>();
            int counter = 0;
            if (DEBUG) {
                stopWatch.lap("Moved cursor to start");
            }
            while ((cursor.moveToNext()) && (counter < MAX_ENTRIES)) {
                if (cursor.isNull(columnDataId)) {
                    LogUtil.i(
                            "DialerDatabaseHelper.getLooseMatches",
                            "_id column null. Row was deleted during iteration, skipping");
                    continue;
                }
                final long dataID = cursor.getLong(columnDataId);
                final String displayName = cursor.getString(columnDisplayNamePrimary);
                final String phoneNumber = cursor.getString(columnNumber);
                final long id = cursor.getLong(columnId);
                final long photoId = cursor.getLong(columnPhotoId);
                final String lookupKey = cursor.getString(columnLookupKey);
                final int carrierPresence = cursor.getInt(columnCarrierPresence);

                final ContactMatch contactMatch = new ContactMatch(lookupKey, id);
                if (duplicates.contains(contactMatch)) {
                    continue;
                }

                final boolean nameMatches = nameMatcher.matches(context, displayName);
                final boolean numberMatches =
                        (nameMatcher.matchesNumber(context, phoneNumber, query) != null);
                if (nameMatches || numberMatches) {
                    duplicates.add(contactMatch);
                    result.add(
                            new ContactNumber(
                                    id, dataID, displayName, phoneNumber, lookupKey, photoId, carrierPresence));
                    counter++;
                    if (DEBUG) {
                        stopWatch.lap("Added one result: Name: " + displayName);
                    }
                }
            }

            if (DEBUG) {
                stopWatch.stopAndLog(TAG + "Finished loading cursor", 0);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public interface Tables {

        /**
         * Saves a list of numbers to be blocked.
         */
        String FILTERED_NUMBER_TABLE = "filtered_numbers_table";
        /**
         * Saves the necessary smart dial information of all contacts.
         */
        String SMARTDIAL_TABLE = "smartdial_table";
        /**
         * Saves all possible prefixes to refer to a contacts.
         */
        String PREFIX_TABLE = "prefix_table";
        /**
         * Saves all archived voicemail information.
         */
        String VOICEMAIL_ARCHIVE_TABLE = "voicemail_archive_table";
        /**
         * Database properties for internal use
         */
        String PROPERTIES = "properties";
    }

    public interface SmartDialDbColumns {

        String _ID = "id";
        String DATA_ID = "data_id";
        String NUMBER = "phone_number";
        String CONTACT_ID = "contact_id";
        String LOOKUP_KEY = "lookup_key";
        String DISPLAY_NAME_PRIMARY = "display_name";
        String PHOTO_ID = "photo_id";
        String LAST_TIME_USED = "last_time_used";
        String TIMES_USED = "times_used";
        String STARRED = "starred";
        String IS_SUPER_PRIMARY = "is_super_primary";
        String IN_VISIBLE_GROUP = "in_visible_group";
        String IS_PRIMARY = "is_primary";
        String CARRIER_PRESENCE = "carrier_presence";
        String LAST_SMARTDIAL_UPDATE_TIME = "last_smartdial_update_time";
    }

    public interface PrefixColumns extends BaseColumns {

        String PREFIX = "prefix";
        String CONTACT_ID = "contact_id";
    }

    public interface PropertiesColumns {

        String PROPERTY_KEY = "property_key";
        String PROPERTY_VALUE = "property_value";
    }

    /**
     * Query options for querying the contact database.
     */
    public interface PhoneQuery {

        Uri URI =
                Phone.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(
                                ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
                        .build();

        String[] PROJECTION =
                new String[]{
                        Phone._ID, // 0
                        Phone.TYPE, // 1
                        Phone.LABEL, // 2
                        Phone.NUMBER, // 3
                        Phone.CONTACT_ID, // 4
                        Phone.LOOKUP_KEY, // 5
                        Phone.DISPLAY_NAME_PRIMARY, // 6
                        Phone.PHOTO_ID, // 7
                        Data.LAST_TIME_USED, // 8
                        Data.TIMES_USED, // 9
                        Contacts.STARRED, // 10
                        Data.IS_SUPER_PRIMARY, // 11
                        Contacts.IN_VISIBLE_GROUP, // 12
                        Data.IS_PRIMARY, // 13
                        Data.CARRIER_PRESENCE, // 14
                };

        int PHONE_ID = 0;
        int PHONE_TYPE = 1;
        int PHONE_LABEL = 2;
        int PHONE_NUMBER = 3;
        int PHONE_CONTACT_ID = 4;
        int PHONE_LOOKUP_KEY = 5;
        int PHONE_DISPLAY_NAME = 6;
        int PHONE_PHOTO_ID = 7;
        int PHONE_LAST_TIME_USED = 8;
        int PHONE_TIMES_USED = 9;
        int PHONE_STARRED = 10;
        int PHONE_IS_SUPER_PRIMARY = 11;
        int PHONE_IN_VISIBLE_GROUP = 12;
        int PHONE_IS_PRIMARY = 13;
        int PHONE_CARRIER_PRESENCE = 14;

        /**
         * Selects only rows that have been updated after a certain time stamp.
         */
        String SELECT_UPDATED_CLAUSE = Phone.CONTACT_LAST_UPDATED_TIMESTAMP + " > ?";

        /**
         * Ignores contacts that have an unreasonably long lookup key. These are likely to be the result
         * of multiple (> 50) merged raw contacts, and are likely to cause OutOfMemoryExceptions within
         * SQLite, or cause memory allocation problems later on when iterating through the cursor set
         * (see a bug)
         */
        String SELECT_IGNORE_LOOKUP_KEY_TOO_LONG_CLAUSE = "length(" + Phone.LOOKUP_KEY + ") < 1000";

        String SELECTION = SELECT_UPDATED_CLAUSE + " AND " + SELECT_IGNORE_LOOKUP_KEY_TOO_LONG_CLAUSE;
    }

    /**
     * Query for all contacts that have been updated since the last time the smart dial database was
     * updated.
     */
    public interface UpdatedContactQuery {

        Uri URI = ContactsContract.Contacts.CONTENT_URI;

        String[] PROJECTION =
                new String[]{
                        ContactsContract.Contacts._ID // 0
                };

        int UPDATED_CONTACT_ID = 0;

        String SELECT_UPDATED_CLAUSE =
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " > ?";
    }

    /**
     * Query options for querying the deleted contact database.
     */
    public interface DeleteContactQuery {

        Uri URI = ContactsContract.DeletedContacts.CONTENT_URI;

        String[] PROJECTION =
                new String[]{
                        ContactsContract.DeletedContacts.CONTACT_ID, // 0
                        ContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP, // 1
                };

        int DELETED_CONTACT_ID = 0;
        int DELETED_TIMESTAMP = 1;

        /**
         * Selects only rows that have been deleted after a certain time stamp.
         */
        String SELECT_UPDATED_CLAUSE =
                ContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP + " > ?";
    }

    /**
     * Gets the sorting order for the smartdial table. This computes a SQL "ORDER BY" argument by
     * composing contact status and recent contact details together.
     */
    private interface SmartDialSortingOrder {

        /**
         * Current contacts - those contacted within the last 3 days (in milliseconds)
         */
        long LAST_TIME_USED_CURRENT_MS = 3L * 24 * 60 * 60 * 1000;
        /**
         * Recent contacts - those contacted within the last 30 days (in milliseconds)
         */
        long LAST_TIME_USED_RECENT_MS = 30L * 24 * 60 * 60 * 1000;

        /**
         * Time since last contact.
         */
        String TIME_SINCE_LAST_USED_MS =
                "( ?1 - " + Tables.SMARTDIAL_TABLE + "." + SmartDialDbColumns.LAST_TIME_USED + ")";

        /**
         * Contacts that have been used in the past 3 days rank higher than contacts that have been used
         * in the past 30 days, which rank higher than contacts that have not been used in recent 30
         * days.
         */
        String SORT_BY_DATA_USAGE =
                "(CASE WHEN "
                        + TIME_SINCE_LAST_USED_MS
                        + " < "
                        + LAST_TIME_USED_CURRENT_MS
                        + " THEN 0 "
                        + " WHEN "
                        + TIME_SINCE_LAST_USED_MS
                        + " < "
                        + LAST_TIME_USED_RECENT_MS
                        + " THEN 1 "
                        + " ELSE 2 END)";

        /**
         * This sort order is similar to that used by the ContactsProvider when returning a list of
         * frequently called contacts.
         */
        String SORT_ORDER =
                Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.STARRED
                        + " DESC, "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.IS_SUPER_PRIMARY
                        + " DESC, "
                        + SORT_BY_DATA_USAGE
                        + ", "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.TIMES_USED
                        + " DESC, "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.IN_VISIBLE_GROUP
                        + " DESC, "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.DISPLAY_NAME_PRIMARY
                        + ", "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.CONTACT_ID
                        + ", "
                        + Tables.SMARTDIAL_TABLE
                        + "."
                        + SmartDialDbColumns.IS_PRIMARY
                        + " DESC";
    }

    /**
     * Simple data format for a contact, containing only information needed for showing up in smart
     * dial interface.
     */
    public static class ContactNumber {

        public final long id;
        public final long dataId;
        public final String displayName;
        public final String phoneNumber;
        public final String lookupKey;
        public final long photoId;
        public final int carrierPresence;

        public ContactNumber(
                long id,
                long dataID,
                String displayName,
                String phoneNumber,
                String lookupKey,
                long photoId,
                int carrierPresence) {
            this.dataId = dataID;
            this.id = id;
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.lookupKey = lookupKey;
            this.photoId = photoId;
            this.carrierPresence = carrierPresence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    id, dataId, displayName, phoneNumber, lookupKey, photoId, carrierPresence);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof ContactNumber) {
                final ContactNumber that = (ContactNumber) object;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.dataId, that.dataId)
                        && Objects.equals(this.displayName, that.displayName)
                        && Objects.equals(this.phoneNumber, that.phoneNumber)
                        && Objects.equals(this.lookupKey, that.lookupKey)
                        && Objects.equals(this.photoId, that.photoId)
                        && Objects.equals(this.carrierPresence, that.carrierPresence);
            }
            return false;
        }
    }

    /**
     * Data format for finding duplicated contacts.
     */
    private static class ContactMatch {

        private final String lookupKey;
        private final long id;

        public ContactMatch(String lookupKey, long id) {
            this.lookupKey = lookupKey;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lookupKey, id);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof ContactMatch) {
                final ContactMatch that = (ContactMatch) object;
                return Objects.equals(this.lookupKey, that.lookupKey) && Objects.equals(this.id, that.id);
            }
            return false;
        }
    }
}
