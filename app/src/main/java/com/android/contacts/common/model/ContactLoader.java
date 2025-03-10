/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.common.model;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import androidx.loader.content.AsyncTaskLoader;

import com.android.contacts.common.GroupMetaData;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountTypeWithDataSet;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.model.dataitem.PhotoDataItem;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.ContactLoaderUtils;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.location.GeoUtil;
import com.fissy.dialer.util.PermissionsUtil;
import com.fissy.dialer.util.UriUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads a single Contact and all it constituent RawContacts.
 */
public class ContactLoader extends AsyncTaskLoader<Contact> {

    private static final String TAG = ContactLoader.class.getSimpleName();

    /**
     * A short-lived cache that can be set by cacheResult()
     */
    private static Contact sCachedResult = null;

    private final Uri mRequestedUri;
    private final Set<Long> mNotifiedRawContactIds = Sets.newHashSet();
    private final boolean mLoadGroupMetaData;
    private final boolean mLoadInvitableAccountTypes;
    private final boolean mPostViewNotification;
    private final boolean mComputeFormattedPhoneNumber;
    private Uri mLookupUri;
    private Contact mContact;
    private ForceLoadContentObserver mObserver;

    public ContactLoader(Context context, Uri lookupUri, boolean postViewNotification) {
        this(context, lookupUri, false, false, postViewNotification, false);
    }

    public ContactLoader(
            Context context,
            Uri lookupUri,
            boolean loadGroupMetaData,
            boolean loadInvitableAccountTypes,
            boolean postViewNotification,
            boolean computeFormattedPhoneNumber) {
        super(context);
        mLookupUri = lookupUri;
        mRequestedUri = lookupUri;
        mLoadGroupMetaData = loadGroupMetaData;
        mLoadInvitableAccountTypes = loadInvitableAccountTypes;
        mPostViewNotification = postViewNotification;
        mComputeFormattedPhoneNumber = computeFormattedPhoneNumber;
    }

    /**
     * Parses a {@link Contact} stored as a JSON string in a lookup URI.
     *
     * @param lookupUri The contact information to parse .
     * @return The parsed {@code Contact} information.
     */
    public static Contact parseEncodedContactEntity(Uri lookupUri) {
        try {
            return loadEncodedContactEntity(lookupUri, lookupUri);
        } catch (JSONException je) {
            return null;
        }
    }

    private static Contact loadEncodedContactEntity(Uri uri, Uri lookupUri) throws JSONException {
        final String jsonString = uri.getEncodedFragment();
        final JSONObject json = new JSONObject(jsonString);

        final long directoryId =
                Long.parseLong(uri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY));

        final String displayName = json.optString(Contacts.DISPLAY_NAME);
        final String altDisplayName = json.optString(Contacts.DISPLAY_NAME_ALTERNATIVE, displayName);
        final int displayNameSource = json.getInt(Contacts.DISPLAY_NAME_SOURCE);
        final String photoUri = json.optString(Contacts.PHOTO_URI, Contacts.PHOTO_URI);
        final Contact contact =
                new Contact(
                        uri,
                        uri,
                        lookupUri,
                        directoryId,
                        null /* lookupKey */,
                        -1 /* id */,
                        -1 /* nameRawContactId */,
                        displayNameSource,
                        0 /* photoId */,
                        photoUri,
                        displayName,
                        altDisplayName,
                        null /* phoneticName */,
                        false /* starred */,
                        null /* presence */,
                        false /* sendToVoicemail */,
                        null /* customRingtone */,
                        false /* isUserProfile */);

        final String accountName = json.optString(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_NAME);
        final String directoryName = uri.getQueryParameter(Directory.DISPLAY_NAME);
        final String accountType = json.getString(RawContacts.ACCOUNT_TYPE);
        contact.setDirectoryMetaData(
                directoryName,
                null,
                accountName,
                accountType,
                json.optInt(Directory.EXPORT_SUPPORT, Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY));

        final ContentValues values = new ContentValues();
        values.put(Data._ID, -1);
        values.put(Data.CONTACT_ID, -1);
        final RawContact rawContact = new RawContact(values);

        final JSONObject items = json.getJSONObject(Contacts.CONTENT_ITEM_TYPE);
        final Iterator<String> keys = items.keys();
        while (keys.hasNext()) {
            final String mimetype = (String) keys.next();

            // Could be single object or array.
            final JSONObject obj = items.optJSONObject(mimetype);
            if (obj == null) {
                final JSONArray array = items.getJSONArray(mimetype);
                for (int i = 0; i < array.length(); i++) {
                    final JSONObject item = array.getJSONObject(i);
                    processOneRecord(rawContact, item, mimetype);
                }
            } else {
                processOneRecord(rawContact, obj, mimetype);
            }
        }

        contact.setRawContacts(new ImmutableList.Builder<RawContact>().add(rawContact).build());
        return contact;
    }

    private static void processOneRecord(RawContact rawContact, JSONObject item, String mimetype)
            throws JSONException {
        final ContentValues itemValues = new ContentValues();
        itemValues.put(Data.MIMETYPE, mimetype);
        itemValues.put(Data._ID, -1);

        final Iterator<String> iterator = item.keys();
        while (iterator.hasNext()) {
            String name = (String) iterator.next();
            final Object o = item.get(name);
            if (o instanceof String) {
                itemValues.put(name, (String) o);
            } else if (o instanceof Integer) {
                itemValues.put(name, (Integer) o);
            }
        }
        rawContact.addDataItemValues(itemValues);
    }

    @Override
    public Contact loadInBackground() {
        LogUtil.v(TAG, "loadInBackground=" + mLookupUri);
        try {
            final ContentResolver resolver = getContext().getContentResolver();
            final Uri uriCurrentFormat = ContactLoaderUtils.ensureIsContactUri(resolver, mLookupUri);
            final Contact cachedResult = sCachedResult;
            sCachedResult = null;
            // Is this the same Uri as what we had before already? In that case, reuse that result
            final Contact result;
            final boolean resultIsCached;
            if (cachedResult != null && UriUtils.areEqual(cachedResult.getLookupUri(), mLookupUri)) {
                // We are using a cached result from earlier. Below, we should make sure
                // we are not doing any more network or disc accesses
                result = new Contact(mRequestedUri, cachedResult);
                resultIsCached = true;
            } else {
                if (uriCurrentFormat.getLastPathSegment().equals(Constants.LOOKUP_URI_ENCODED)) {
                    result = loadEncodedContactEntity(uriCurrentFormat, mLookupUri);
                } else {
                    result = loadContactEntity(resolver, uriCurrentFormat);
                }
                resultIsCached = false;
            }
            if (result.isLoaded()) {
                if (result.isDirectoryEntry()) {
                    if (!resultIsCached) {
                        loadDirectoryMetaData(result);
                    }
                } else if (mLoadGroupMetaData) {
                    if (result.getGroupMetaData() == null) {
                        loadGroupMetaData(result);
                    }
                }
                if (mComputeFormattedPhoneNumber) {
                    computeFormattedPhoneNumbers(result);
                }
                if (!resultIsCached) {
                    loadPhotoBinaryData(result);
                }

                // Note ME profile should never have "Add connection"
                if (mLoadInvitableAccountTypes && result.getInvitableAccountTypes() == null) {
                    loadInvitableAccountTypes(result);
                }
            }
            return result;
        } catch (Exception e) {
            LogUtil.e(TAG, "Error loading the contact: " + mLookupUri, e);
            return Contact.forError(mRequestedUri, e);
        }
    }

    private Contact loadContactEntity(ContentResolver resolver, Uri contactUri) {
        Uri entityUri = Uri.withAppendedPath(contactUri, Contacts.Entity.CONTENT_DIRECTORY);
        Cursor cursor =
                resolver.query(entityUri, ContactQuery.COLUMNS, null, null, Contacts.Entity.RAW_CONTACT_ID);
        if (cursor == null) {
            LogUtil.e(TAG, "No cursor returned in loadContactEntity");
            return Contact.forNotFound(mRequestedUri);
        }

        try {
            if (!cursor.moveToFirst()) {
                cursor.close();
                return Contact.forNotFound(mRequestedUri);
            }

            // Create the loaded contact starting with the header data.
            Contact contact = loadContactHeaderData(cursor, contactUri);

            // Fill in the raw contacts, which is wrapped in an Entity and any
            // status data.  Initially, result has empty entities and statuses.
            long currentRawContactId = -1;
            RawContact rawContact = null;
            ImmutableList.Builder<RawContact> rawContactsBuilder =
                    new ImmutableList.Builder<>();
            do {
                long rawContactId = cursor.getLong(ContactQuery.RAW_CONTACT_ID);
                if (rawContactId != currentRawContactId) {
                    // First time to see this raw contact id, so create a new entity, and
                    // add it to the result's entities.
                    currentRawContactId = rawContactId;
                    rawContact = new RawContact(loadRawContactValues(cursor));
                    rawContactsBuilder.add(rawContact);
                }
                if (!cursor.isNull(ContactQuery.DATA_ID)) {
                    ContentValues data = loadDataValues(cursor);
                    assert rawContact != null;
                    rawContact.addDataItemValues(data);
                }
            } while (cursor.moveToNext());

            contact.setRawContacts(rawContactsBuilder.build());

            return contact;
        } finally {
            cursor.close();
        }
    }

    /**
     * Looks for the photo data item in entities. If found, a thumbnail will be stored. A larger photo
     * will also be stored if available.
     */
    private void loadPhotoBinaryData(Contact contactData) {
        loadThumbnailBinaryData(contactData);

        // Try to load the large photo from a file using the photo URI.
        String photoUri = contactData.getPhotoUri();
        if (photoUri != null) {
            try {
                final InputStream inputStream;
                final AssetFileDescriptor fd;
                final Uri uri = Uri.parse(photoUri);
                final String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // Support HTTP urls that might come from extended directories
                    inputStream = new URL(photoUri).openStream();
                    fd = null;
                } else {
                    fd = getContext().getContentResolver().openAssetFileDescriptor(uri, "r");
                    inputStream = fd.createInputStream();
                }
                byte[] buffer = new byte[16 * 1024];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    int size;
                    while ((size = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, size);
                    }
                    contactData.setPhotoBinaryData(baos.toByteArray());
                } finally {
                    inputStream.close();
                    if (fd != null) {
                        fd.close();
                    }
                }
                return;
            } catch (IOException ioe) {
                // Just fall back to the case below.
            }
        }

        // If we couldn't load from a file, fall back to the data blob.
        contactData.setPhotoBinaryData(contactData.getThumbnailPhotoBinaryData());
    }

    private void loadThumbnailBinaryData(Contact contactData) {
        final long photoId = contactData.getPhotoId();
        if (photoId <= 0) {
            // No photo ID
            return;
        }

        for (RawContact rawContact : contactData.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                if (dataItem.getId() == photoId) {
                    if (!(dataItem instanceof PhotoDataItem)) {
                        break;
                    }

                    final PhotoDataItem photo = (PhotoDataItem) dataItem;
                    contactData.setThumbnailPhotoBinaryData(photo.getPhoto());
                    break;
                }
            }
        }
    }

    /**
     * Sets the "invitable" account types to {mInvitableAccountTypes}.
     */
    private void loadInvitableAccountTypes(Contact contactData) {
        final ImmutableList.Builder<AccountType> resultListBuilder =
                new ImmutableList.Builder<>();
        if (!contactData.isUserProfile()) {
            Map<AccountTypeWithDataSet, AccountType> invitables =
                    AccountTypeManager.getInstance(getContext()).getUsableInvitableAccountTypes();
            if (!invitables.isEmpty()) {
                final Map<AccountTypeWithDataSet, AccountType> resultMap = Maps.newHashMap(invitables);

                // Remove the ones that already have a raw contact in the current contact
                for (RawContact rawContact : contactData.getRawContacts()) {
                    final AccountTypeWithDataSet type =
                            AccountTypeWithDataSet.get(
                                    rawContact.getAccountTypeString(), rawContact.getDataSet());
                    resultMap.remove(type);
                }

                resultListBuilder.addAll(resultMap.values());
            }
        }

        // Set to mInvitableAccountTypes
        contactData.setInvitableAccountTypes(resultListBuilder.build());
    }

    /**
     * Extracts Contact level columns from the cursor.
     */
    private Contact loadContactHeaderData(final Cursor cursor, Uri contactUri) {
        final String directoryParameter =
                contactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
        final long directoryId =
                directoryParameter == null ? Directory.DEFAULT : Long.parseLong(directoryParameter);
        final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
        final String lookupKey = cursor.getString(ContactQuery.LOOKUP_KEY);
        final long nameRawContactId = cursor.getLong(ContactQuery.NAME_RAW_CONTACT_ID);
        final int displayNameSource = cursor.getInt(ContactQuery.DISPLAY_NAME_SOURCE);
        final String displayName = cursor.getString(ContactQuery.DISPLAY_NAME);
        final String altDisplayName = cursor.getString(ContactQuery.ALT_DISPLAY_NAME);
        final String phoneticName = cursor.getString(ContactQuery.PHONETIC_NAME);
        final long photoId = cursor.getLong(ContactQuery.PHOTO_ID);
        final String photoUri = cursor.getString(ContactQuery.PHOTO_URI);
        final boolean starred = cursor.getInt(ContactQuery.STARRED) != 0;
        final Integer presence =
                cursor.isNull(ContactQuery.CONTACT_PRESENCE)
                        ? null
                        : cursor.getInt(ContactQuery.CONTACT_PRESENCE);
        final boolean sendToVoicemail = cursor.getInt(ContactQuery.SEND_TO_VOICEMAIL) == 1;
        final String customRingtone = cursor.getString(ContactQuery.CUSTOM_RINGTONE);
        final boolean isUserProfile = cursor.getInt(ContactQuery.IS_USER_PROFILE) == 1;

        Uri lookupUri;
        if (directoryId == Directory.DEFAULT || directoryId == Directory.LOCAL_INVISIBLE) {
            lookupUri =
                    ContentUris.withAppendedId(
                            Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), contactId);
        } else {
            lookupUri = contactUri;
        }

        return new Contact(
                mRequestedUri,
                contactUri,
                lookupUri,
                directoryId,
                lookupKey,
                contactId,
                nameRawContactId,
                displayNameSource,
                photoId,
                photoUri,
                displayName,
                altDisplayName,
                phoneticName,
                starred,
                presence,
                sendToVoicemail,
                customRingtone,
                isUserProfile);
    }

    /**
     * Extracts RawContact level columns from the cursor.
     */
    private ContentValues loadRawContactValues(Cursor cursor) {
        ContentValues cv = new ContentValues();

        cv.put(RawContacts._ID, cursor.getLong(ContactQuery.RAW_CONTACT_ID));

        cursorColumnToContentValues(cursor, cv, ContactQuery.ACCOUNT_NAME);
        cursorColumnToContentValues(cursor, cv, ContactQuery.ACCOUNT_TYPE);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SET);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DIRTY);
        cursorColumnToContentValues(cursor, cv, ContactQuery.VERSION);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SOURCE_ID);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC1);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC2);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC3);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC4);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DELETED);
        cursorColumnToContentValues(cursor, cv, ContactQuery.CONTACT_ID);
        cursorColumnToContentValues(cursor, cv, ContactQuery.STARRED);

        return cv;
    }

    /**
     * Extracts Data level columns from the cursor.
     */
    private ContentValues loadDataValues(Cursor cursor) {
        ContentValues cv = new ContentValues();

        cv.put(Data._ID, cursor.getLong(ContactQuery.DATA_ID));

        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA1);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA2);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA3);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA4);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA5);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA6);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA7);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA8);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA9);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA10);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA11);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA12);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA13);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA14);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA15);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC1);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC2);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC3);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC4);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_VERSION);
        cursorColumnToContentValues(cursor, cv, ContactQuery.IS_PRIMARY);
        cursorColumnToContentValues(cursor, cv, ContactQuery.IS_SUPERPRIMARY);
        cursorColumnToContentValues(cursor, cv, ContactQuery.MIMETYPE);
        cursorColumnToContentValues(cursor, cv, ContactQuery.GROUP_SOURCE_ID);
        cursorColumnToContentValues(cursor, cv, ContactQuery.CHAT_CAPABILITY);
        cursorColumnToContentValues(cursor, cv, ContactQuery.TIMES_USED);
        cursorColumnToContentValues(cursor, cv, ContactQuery.LAST_TIME_USED);
        cursorColumnToContentValues(cursor, cv, ContactQuery.CARRIER_PRESENCE);

        return cv;
    }

    private void cursorColumnToContentValues(Cursor cursor, ContentValues values, int index) {
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_NULL:
                // don't put anything in the content values
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                values.put(ContactQuery.COLUMNS[index], cursor.getLong(index));
                break;
            case Cursor.FIELD_TYPE_STRING:
                values.put(ContactQuery.COLUMNS[index], cursor.getString(index));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                values.put(ContactQuery.COLUMNS[index], cursor.getBlob(index));
                break;
            default:
                throw new IllegalStateException("Invalid or unhandled data type");
            case Cursor.FIELD_TYPE_FLOAT:
                break;
        }
    }

    private void loadDirectoryMetaData(Contact result) {
        long directoryId = result.getDirectoryId();

        Cursor cursor =
                getContext()
                        .getContentResolver()
                        .query(
                                ContentUris.withAppendedId(Directory.CONTENT_URI, directoryId),
                                DirectoryQuery.COLUMNS,
                                null,
                                null,
                                null);
        if (cursor == null) {
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                final String displayName = cursor.getString(DirectoryQuery.DISPLAY_NAME);
                final String packageName = cursor.getString(DirectoryQuery.PACKAGE_NAME);
                final int typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
                final String accountType = cursor.getString(DirectoryQuery.ACCOUNT_TYPE);
                final String accountName = cursor.getString(DirectoryQuery.ACCOUNT_NAME);
                final int exportSupport = cursor.getInt(DirectoryQuery.EXPORT_SUPPORT);
                String directoryType = null;
                if (!TextUtils.isEmpty(packageName)) {
                    PackageManager pm = getContext().getPackageManager();
                    try {
                        Resources resources = pm.getResourcesForApplication(packageName);
                        directoryType = resources.getString(typeResourceId);
                    } catch (NameNotFoundException e) {
                        LogUtil.w(
                                TAG, "Contact directory resource not found: " + packageName + "." + typeResourceId);
                    }
                }

                result.setDirectoryMetaData(
                        displayName, directoryType, accountType, accountName, exportSupport);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Loads groups meta-data for all groups associated with all constituent raw contacts' accounts.
     */
    private void loadGroupMetaData(Contact result) {
        StringBuilder selection = new StringBuilder();
        ArrayList<String> selectionArgs = new ArrayList<>();
        final HashSet<AccountKey> accountsSeen = new HashSet<>();
        for (RawContact rawContact : result.getRawContacts()) {
            final String accountName = rawContact.getAccountName();
            final String accountType = rawContact.getAccountTypeString();
            final String dataSet = rawContact.getDataSet();
            final AccountKey accountKey = new AccountKey(accountName, accountType, dataSet);
            if (accountName != null && accountType != null && !accountsSeen.contains(accountKey)) {
                accountsSeen.add(accountKey);
                if (selection.length() != 0) {
                    selection.append(" OR ");
                }
                selection.append("(" + Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?");
                selectionArgs.add(accountName);
                selectionArgs.add(accountType);

                if (dataSet != null) {
                    selection.append(" AND " + Groups.DATA_SET + "=?");
                    selectionArgs.add(dataSet);
                } else {
                    selection.append(" AND " + Groups.DATA_SET + " IS NULL");
                }
                selection.append(")");
            }
        }
        final ImmutableList.Builder<GroupMetaData> groupListBuilder =
                new ImmutableList.Builder<>();
        final Cursor cursor =
                getContext()
                        .getContentResolver()
                        .query(
                                Groups.CONTENT_URI,
                                GroupQuery.COLUMNS,
                                selection.toString(),
                                selectionArgs.toArray(new String[0]),
                                null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    final String accountName = cursor.getString(GroupQuery.ACCOUNT_NAME);
                    final String accountType = cursor.getString(GroupQuery.ACCOUNT_TYPE);
                    final String dataSet = cursor.getString(GroupQuery.DATA_SET);
                    final long groupId = cursor.getLong(GroupQuery.ID);
                    final String title = cursor.getString(GroupQuery.TITLE);
                    final boolean defaultGroup =
                            !cursor.isNull(GroupQuery.AUTO_ADD) && cursor.getInt(GroupQuery.AUTO_ADD) != 0;
                    final boolean favorites =
                            !cursor.isNull(GroupQuery.FAVORITES) && cursor.getInt(GroupQuery.FAVORITES) != 0;

                    groupListBuilder.add(
                            new GroupMetaData(
                                    accountName, accountType, dataSet, groupId, title, defaultGroup, favorites));
                }
            } finally {
                cursor.close();
            }
        }
        result.setGroupMetaData(groupListBuilder.build());
    }

    /**
     * Iterates over all data items that represent phone numbers are tries to calculate a formatted
     * number. This function can safely be called several times as no unformatted data is overwritten
     */
    private void computeFormattedPhoneNumbers(Contact contactData) {
        final String countryIso = GeoUtil.getCurrentCountryIso(getContext());
        final ImmutableList<RawContact> rawContacts = contactData.getRawContacts();
        final int rawContactCount = rawContacts.size();
        for (int rawContactIndex = 0; rawContactIndex < rawContactCount; rawContactIndex++) {
            final RawContact rawContact = rawContacts.get(rawContactIndex);
            final List<DataItem> dataItems = rawContact.getDataItems();
            final int dataCount = dataItems.size();
            for (int dataIndex = 0; dataIndex < dataCount; dataIndex++) {
                final DataItem dataItem = dataItems.get(dataIndex);
                if (dataItem instanceof PhoneDataItem) {
                    final PhoneDataItem phoneDataItem = (PhoneDataItem) dataItem;
                    phoneDataItem.computeFormattedPhoneNumber(getContext(), countryIso);
                }
            }
        }
    }

    @Override
    public void deliverResult(Contact result) {
        unregisterObserver();

        // The creator isn't interested in any further updates
        if (isReset() || result == null) {
            return;
        }

        mContact = result;

        if (result.isLoaded()) {
            mLookupUri = result.getLookupUri();

            if (!result.isDirectoryEntry()) {
                if (mObserver == null) {
                    mObserver = new ForceLoadContentObserver();
                }

                if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
                    getContext().getContentResolver().registerContentObserver(mLookupUri, true, mObserver);
                } else {
                    LogUtil.w("ContactLoader.deliverResult", "contacts permission not available");
                }
            }

            if (mPostViewNotification) {
                // inform the source of the data that this contact is being looked at
                postViewNotificationToSyncAdapter();
            }
        }

        super.deliverResult(mContact);
    }

    /**
     * Posts a message to the contributing sync adapters that have opted-in, notifying them that the
     * contact has just been loaded
     */
    private void postViewNotificationToSyncAdapter() {
        Context context = getContext();
        for (RawContact rawContact : mContact.getRawContacts()) {
            final long rawContactId = rawContact.getId();
            if (mNotifiedRawContactIds.contains(rawContactId)) {
                continue; // Already notified for this raw contact.
            }
            mNotifiedRawContactIds.add(rawContactId);
            final AccountType accountType = rawContact.getAccountType(context);
            final String serviceName = accountType.getViewContactNotifyServiceClassName();
            final String servicePackageName = accountType.getViewContactNotifyServicePackageName();
            if (!TextUtils.isEmpty(serviceName) && !TextUtils.isEmpty(servicePackageName)) {
                final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
                final Intent intent = new Intent();
                intent.setClassName(servicePackageName, serviceName);
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, RawContacts.CONTENT_ITEM_TYPE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startService(intent);
                } catch (Exception e) {
                    LogUtil.e(TAG, "Error sending message to source-app", e);
                }
            }
        }
    }

    private void unregisterObserver() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }

    public void setLookupUri(Uri lookupUri) {
        mLookupUri = lookupUri;
    }

    @Override
    protected void onStartLoading() {
        if (mContact != null) {
            deliverResult(mContact);
        }

        if (takeContentChanged() || mContact == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        cancelLoad();
        unregisterObserver();
        mContact = null;
    }

    /**
     * Projection used for the query that loads all data for the entire contact (except for social
     * stream items).
     */
    private static class ContactQuery {

        public static final int NAME_RAW_CONTACT_ID = 0;
        public static final int DISPLAY_NAME_SOURCE = 1;
        public static final int LOOKUP_KEY = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int ALT_DISPLAY_NAME = 4;
        public static final int PHONETIC_NAME = 5;
        public static final int PHOTO_ID = 6;
        public static final int STARRED = 7;
        public static final int CONTACT_PRESENCE = 8;
        public static final int CONTACT_ID = 13;
        public static final int RAW_CONTACT_ID = 14;
        public static final int ACCOUNT_NAME = 15;
        public static final int ACCOUNT_TYPE = 16;
        public static final int DATA_SET = 17;
        public static final int DIRTY = 18;
        public static final int VERSION = 19;
        public static final int SOURCE_ID = 20;
        public static final int SYNC1 = 21;
        public static final int SYNC2 = 22;
        public static final int SYNC3 = 23;
        public static final int SYNC4 = 24;
        public static final int DELETED = 25;
        public static final int DATA_ID = 26;
        public static final int DATA1 = 27;
        public static final int DATA2 = 28;
        public static final int DATA3 = 29;
        public static final int DATA4 = 30;
        public static final int DATA5 = 31;
        public static final int DATA6 = 32;
        public static final int DATA7 = 33;
        public static final int DATA8 = 34;
        public static final int DATA9 = 35;
        public static final int DATA10 = 36;
        public static final int DATA11 = 37;
        public static final int DATA12 = 38;
        public static final int DATA13 = 39;
        public static final int DATA14 = 40;
        public static final int DATA15 = 41;
        public static final int DATA_SYNC1 = 42;
        public static final int DATA_SYNC2 = 43;
        public static final int DATA_SYNC3 = 44;
        public static final int DATA_SYNC4 = 45;
        public static final int DATA_VERSION = 46;
        public static final int IS_PRIMARY = 47;
        public static final int IS_SUPERPRIMARY = 48;
        public static final int MIMETYPE = 49;
        public static final int GROUP_SOURCE_ID = 50;
        public static final int PRESENCE = 51;
        public static final int CHAT_CAPABILITY = 52;
        public static final int STATUS = 53;
        public static final int PHOTO_URI = 58;
        public static final int SEND_TO_VOICEMAIL = 59;
        public static final int CUSTOM_RINGTONE = 60;
        public static final int IS_USER_PROFILE = 61;
        public static final int TIMES_USED = 62;
        public static final int LAST_TIME_USED = 63;
        public static final int CARRIER_PRESENCE = 64;
        static final String[] COLUMNS_INTERNAL =
                new String[]{
                        Contacts.NAME_RAW_CONTACT_ID,
                        Contacts.DISPLAY_NAME_SOURCE,
                        Contacts.LOOKUP_KEY,
                        Contacts.DISPLAY_NAME,
                        Contacts.DISPLAY_NAME_ALTERNATIVE,
                        Contacts.PHONETIC_NAME,
                        Contacts.PHOTO_ID,
                        Contacts.STARRED,
                        Contacts.CONTACT_PRESENCE,
                        Contacts.CONTACT_STATUS,
                        Contacts.CONTACT_STATUS_TIMESTAMP,
                        Contacts.CONTACT_STATUS_RES_PACKAGE,
                        Contacts.CONTACT_STATUS_LABEL,
                        Contacts.Entity.CONTACT_ID,
                        Contacts.Entity.RAW_CONTACT_ID,
                        RawContacts.ACCOUNT_NAME,
                        RawContacts.ACCOUNT_TYPE,
                        RawContacts.DATA_SET,
                        RawContacts.DIRTY,
                        RawContacts.VERSION,
                        RawContacts.SOURCE_ID,
                        RawContacts.SYNC1,
                        RawContacts.SYNC2,
                        RawContacts.SYNC3,
                        RawContacts.SYNC4,
                        RawContacts.DELETED,
                        Contacts.Entity.DATA_ID,
                        Data.DATA1,
                        Data.DATA2,
                        Data.DATA3,
                        Data.DATA4,
                        Data.DATA5,
                        Data.DATA6,
                        Data.DATA7,
                        Data.DATA8,
                        Data.DATA9,
                        Data.DATA10,
                        Data.DATA11,
                        Data.DATA12,
                        Data.DATA13,
                        Data.DATA14,
                        Data.DATA15,
                        Data.SYNC1,
                        Data.SYNC2,
                        Data.SYNC3,
                        Data.SYNC4,
                        Data.DATA_VERSION,
                        Data.IS_PRIMARY,
                        Data.IS_SUPER_PRIMARY,
                        Data.MIMETYPE,
                        GroupMembership.GROUP_SOURCE_ID,
                        Data.PRESENCE,
                        Data.CHAT_CAPABILITY,
                        Data.STATUS,
                        Data.STATUS_RES_PACKAGE,
                        Data.STATUS_ICON,
                        Data.STATUS_LABEL,
                        Data.STATUS_TIMESTAMP,
                        Contacts.PHOTO_URI,
                        Contacts.SEND_TO_VOICEMAIL,
                        Contacts.CUSTOM_RINGTONE,
                        Contacts.IS_USER_PROFILE,
                        Data.TIMES_USED,
                        Data.LAST_TIME_USED
                };
        static final String[] COLUMNS;

        static {
            List<String> projectionList = Lists.newArrayList(COLUMNS_INTERNAL);
            projectionList.add(Data.CARRIER_PRESENCE);
            COLUMNS = projectionList.toArray(new String[0]);
        }
    }

    /**
     * Projection used for the query that loads all data for the entire contact.
     */
    private static class DirectoryQuery {

        public static final int DISPLAY_NAME = 0;
        public static final int PACKAGE_NAME = 1;
        public static final int TYPE_RESOURCE_ID = 2;
        public static final int ACCOUNT_TYPE = 3;
        public static final int ACCOUNT_NAME = 4;
        public static final int EXPORT_SUPPORT = 5;
        static final String[] COLUMNS =
                new String[]{
                        Directory.DISPLAY_NAME,
                        Directory.PACKAGE_NAME,
                        Directory.TYPE_RESOURCE_ID,
                        Directory.ACCOUNT_TYPE,
                        Directory.ACCOUNT_NAME,
                        Directory.EXPORT_SUPPORT,
                };
    }

    private static class GroupQuery {

        public static final int ACCOUNT_NAME = 0;
        public static final int ACCOUNT_TYPE = 1;
        public static final int DATA_SET = 2;
        public static final int ID = 3;
        public static final int TITLE = 4;
        public static final int AUTO_ADD = 5;
        public static final int FAVORITES = 6;
        static final String[] COLUMNS =
                new String[]{
                        Groups.ACCOUNT_NAME,
                        Groups.ACCOUNT_TYPE,
                        Groups.DATA_SET,
                        Groups._ID,
                        Groups.TITLE,
                        Groups.AUTO_ADD,
                        Groups.FAVORITES,
                };
    }

    private static class AccountKey {

        private final String mAccountName;
        private final String mAccountType;
        private final String mDataSet;

        public AccountKey(String accountName, String accountType, String dataSet) {
            mAccountName = accountName;
            mAccountType = accountType;
            mDataSet = dataSet;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAccountName, mAccountType, mDataSet);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AccountKey)) {
                return false;
            }
            final AccountKey other = (AccountKey) obj;
            return Objects.equals(mAccountName, other.mAccountName)
                    && Objects.equals(mAccountType, other.mAccountType)
                    && Objects.equals(mDataSet, other.mDataSet);
        }
    }
}
