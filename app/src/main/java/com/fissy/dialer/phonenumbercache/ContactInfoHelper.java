/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.fissy.dialer.phonenumbercache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.util.Constants;
import com.fissy.dialer.common.Assert;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.logging.ContactSource;
import com.fissy.dialer.oem.CequintCallerIdManager;
import com.fissy.dialer.oem.CequintCallerIdManager.CequintCallerIdContact;
import com.fissy.dialer.phonenumbercache.CachedNumberLookupService.CachedContactInfo;
import com.fissy.dialer.phonenumberutil.PhoneNumberHelper;
import com.fissy.dialer.telecom.TelecomUtil;
import com.fissy.dialer.util.PermissionsUtil;
import com.fissy.dialer.util.UriUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {

    private static final String TAG = ContactInfoHelper.class.getSimpleName();

    private final Context context;
    private final String currentCountryIso;
    private final CachedNumberLookupService cachedNumberLookupService;

    public ContactInfoHelper(Context context, String currentCountryIso) {
        this.context = context;
        this.currentCountryIso = currentCountryIso;
        cachedNumberLookupService = PhoneNumberCache.get(this.context).getCachedNumberLookupService();
    }

    /**
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param number - Unknown phone number
     * @return JSON-encoded URI that can be used to perform a lookup when clicking on the quick
     * contact card.
     */
    private static Uri createTemporaryContactUri(String number) {
        try {
            final JSONObject contactRows =
                    new JSONObject()
                            .put(
                                    Phone.CONTENT_ITEM_TYPE,
                                    new JSONObject().put(Phone.NUMBER, number).put(Phone.TYPE, Phone.TYPE_CUSTOM));

            final String jsonString =
                    new JSONObject()
                            .put(Contacts.DISPLAY_NAME, number)
                            .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.PHONE)
                            .put(Contacts.CONTENT_ITEM_TYPE, contactRows)
                            .toString();

            return Contacts.CONTENT_LOOKUP_URI
                    .buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(
                            ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }

    public static String lookUpDisplayNameAlternative(
            Context context, String lookupKey, @UserType long userType, @Nullable Long directoryId) {
        // Query {@link Contacts#CONTENT_LOOKUP_URI} directly with work lookup key is not allowed.
        if (lookupKey == null || userType == ContactsUtils.USER_TYPE_WORK) {
            return null;
        }

        if (directoryId != null) {
            // Query {@link Contacts#CONTENT_LOOKUP_URI} with work lookup key is not allowed.
            if (Directory.isEnterpriseDirectoryId(directoryId)) {
                return null;
            }

            // Skip this to avoid an extra remote network call for alternative name
            if (Directory.isRemoteDirectoryId(directoryId)) {
                return null;
            }
        }

        final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        try (Cursor cursor = context
                .getContentResolver()
                .query(uri, PhoneQuery.DISPLAY_NAME_ALTERNATIVE_PROJECTION, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(PhoneQuery.NAME_ALTERNATIVE);
            }
        } catch (IllegalArgumentException e) {
            // Avoid dialer crash when lookup key is not valid
            LogUtil.e(TAG, "IllegalArgumentException in lookUpDisplayNameAlternative", e);
        }

        return null;
    }

    public static Uri getContactInfoLookupUri(String number) {
        return getContactInfoLookupUri(number, -1);
    }

    public static Uri getContactInfoLookupUri(String number, long directoryId) {
        // Get URI for the number in the PhoneLookup table, with a parameter to indicate whether
        // the number is a SIP number.
        Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI;
        Uri.Builder builder =
                uri.buildUpon()
                        .appendPath(number)
                        .appendQueryParameter(
                                PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                                String.valueOf(PhoneNumberHelper.isUriNumber(number)));
        if (directoryId != -1) {
            builder.appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));
        }
        return builder.build();
    }

    /**
     * Returns the contact information stored in an entry of the call log.
     *
     * @param c A cursor pointing to an entry in the call log.
     */
    public static ContactInfo getContactInfo(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        String postDialDigits = c.getString(CallLogQuery.POST_DIAL_DIGITS);
        info.number =
                (matchedNumber == null) ? c.getString(CallLogQuery.NUMBER) + postDialDigits : matchedNumber;

        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri =
                UriUtils.nullForNonContactsUri(
                        UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_PHOTO_URI)));
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);

        return info;
    }

    @Nullable
    public ContactInfo lookupNumber(String number, String countryIso) {
        return lookupNumber(number, countryIso, -1);
    }

    /**
     * Returns the contact information for the given number.
     *
     * <p>If the number does not match any contact, returns a contact info containing only the number
     * and the formatted number.
     *
     * <p>If an error occurs during the lookup, it returns null.
     *
     * @param number      the number to look up
     * @param countryIso  the country associated with this number
     * @param directoryId the id of the directory to lookup
     */
    @Nullable
    @SuppressWarnings("ReferenceEquality")
    public ContactInfo lookupNumber(String number, String countryIso, long directoryId) {
        if (TextUtils.isEmpty(number)) {
            LogUtil.d("ContactInfoHelper.lookupNumber", "number is empty");
            return null;
        }

        ContactInfo info;

        if (PhoneNumberHelper.isUriNumber(number)) {
            LogUtil.d("ContactInfoHelper.lookupNumber", "number is sip");
            // The number is a SIP address..
            info = lookupContactFromUri(getContactInfoLookupUri(number, directoryId));
            if (info == null || info == ContactInfo.EMPTY) {
                // If lookup failed, check if the "username" of the SIP address is a phone number.
                String username = PhoneNumberHelper.getUsernameFromUriNumber(number);
                if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                    info = queryContactInfoForPhoneNumber(username, countryIso, directoryId);
                }
            }
        } else {
            // Look for a contact that has the given phone number.
            info = queryContactInfoForPhoneNumber(number, countryIso, directoryId);
        }

        final ContactInfo updatedInfo;
        if (info == null) {
            // The lookup failed.
            LogUtil.d("ContactInfoHelper.lookupNumber", "lookup failed");
            updatedInfo = null;
        } else {
            // If we did not find a matching contact, generate an empty contact info for the number.
            if (info == ContactInfo.EMPTY) {
                // Did not find a matching contact.
                updatedInfo = createEmptyContactInfoForNumber(number, countryIso);
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
    }

    private ContactInfo createEmptyContactInfoForNumber(String number, String countryIso) {
        ContactInfo contactInfo = new ContactInfo();
        contactInfo.number = number;
        contactInfo.formattedNumber = formatPhoneNumber(number, countryIso);
        contactInfo.normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        contactInfo.lookupUri = createTemporaryContactUri(contactInfo.formattedNumber);
        return contactInfo;
    }

    /**
     * Return the contact info object if the remote directory lookup succeeds, otherwise return an
     * empty contact info for the number.
     */
    public ContactInfo lookupNumberInRemoteDirectory(String number, String countryIso) {
        if (cachedNumberLookupService != null) {
            List<Long> remoteDirectories = getRemoteDirectories(context);
            for (long directoryId : remoteDirectories) {
                ContactInfo contactInfo = lookupNumber(number, countryIso, directoryId);
                if (hasName(contactInfo)) {
                    return contactInfo;
                }
            }
        }
        return createEmptyContactInfoForNumber(number, countryIso);
    }

    public boolean hasName(ContactInfo contactInfo) {
        return contactInfo != null && !TextUtils.isEmpty(contactInfo.name);
    }

    private List<Long> getRemoteDirectories(Context context) {
        List<Long> remoteDirectories = new ArrayList<>();
        Uri uri = Directory.ENTERPRISE_CONTENT_URI;
        Cursor cursor =
                context.getContentResolver().query(uri, new String[]{Directory._ID}, null, null, null);
        if (cursor == null) {
            return remoteDirectories;
        }
        int idIndex = cursor.getColumnIndex(Directory._ID);
        try {
            while (cursor.moveToNext()) {
                long directoryId = cursor.getLong(idIndex);
                if (Directory.isRemoteDirectoryId(directoryId)) {
                    remoteDirectories.add(directoryId);
                }
            }
        } finally {
            cursor.close();
        }
        return remoteDirectories;
    }

    /**
     * Looks up a contact using the given URI.
     *
     * <p>It returns null if an error occurs, {@link ContactInfo#EMPTY} if no matching contact is
     * found, or the {@link ContactInfo} for the given contact.
     *
     * <p>The {@link ContactInfo#formattedNumber} field is always set to {@code null} in the returned
     * value.
     */
    ContactInfo lookupContactFromUri(Uri uri) {
        if (uri == null) {
            LogUtil.d("ContactInfoHelper.lookupContactFromUri", "uri is null");
            return null;
        }
        if (!PermissionsUtil.hasContactsReadPermissions(context)) {
            LogUtil.d("ContactInfoHelper.lookupContactFromUri", "no contact permission, return empty");
            return ContactInfo.EMPTY;
        }

        try (Cursor phoneLookupCursor =
                     context
                             .getContentResolver()
                             .query(
                                     uri,
                                     PhoneQuery.getPhoneLookupProjection(),
                                     null /* selection */,
                                     null /* selectionArgs */,
                                     null /* sortOrder */)) {
            if (phoneLookupCursor == null) {
                LogUtil.d("ContactInfoHelper.lookupContactFromUri", "phoneLookupCursor is null");
                return null;
            }

            if (!phoneLookupCursor.moveToFirst()) {
                return ContactInfo.EMPTY;
            }

            // The Contacts provider ignores special characters in phone numbers when searching for a
            // contact. For example, number "123" is considered a match with a contact with number "#123".
            // We need to check whether the result contains a number that truly matches the query and move
            // the cursor to that position before building a ContactInfo.
            boolean hasNumberMatch =
                    PhoneNumberHelper.updateCursorToMatchContactLookupUri(
                            phoneLookupCursor, PhoneQuery.MATCHED_NUMBER, uri);
            if (!hasNumberMatch) {
                return ContactInfo.EMPTY;
            }

            String lookupKey = phoneLookupCursor.getString(PhoneQuery.LOOKUP_KEY);
            ContactInfo contactInfo = createPhoneLookupContactInfo(phoneLookupCursor, lookupKey);
            fillAdditionalContactInfo(context, contactInfo);
            return contactInfo;
        }
    }

    private ContactInfo createPhoneLookupContactInfo(Cursor phoneLookupCursor, String lookupKey) {
        ContactInfo info = new ContactInfo();
        info.lookupKey = lookupKey;
        info.lookupUri =
                Contacts.getLookupUri(phoneLookupCursor.getLong(PhoneQuery.PERSON_ID), lookupKey);
        info.name = phoneLookupCursor.getString(PhoneQuery.NAME);
        info.type = phoneLookupCursor.getInt(PhoneQuery.PHONE_TYPE);
        info.label = phoneLookupCursor.getString(PhoneQuery.LABEL);
        info.number = phoneLookupCursor.getString(PhoneQuery.MATCHED_NUMBER);
        info.normalizedNumber = phoneLookupCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
        info.photoId = phoneLookupCursor.getLong(PhoneQuery.PHOTO_ID);
        info.photoUri = UriUtils.parseUriOrNull(phoneLookupCursor.getString(PhoneQuery.PHOTO_URI));
        info.formattedNumber = null;
        info.userType =
                ContactsUtils.determineUserType(null, phoneLookupCursor.getLong(PhoneQuery.PERSON_ID));
        info.contactExists = true;

        return info;
    }

    private void fillAdditionalContactInfo(Context context, ContactInfo contactInfo) {
        if (contactInfo.number == null) {
            return;
        }
        Uri uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(contactInfo.number));
        try (Cursor cursor =
                     context
                             .getContentResolver()
                             .query(uri, PhoneQuery.ADDITIONAL_CONTACT_INFO_PROJECTION, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            contactInfo.nameAlternative =
                    cursor.getString(PhoneQuery.ADDITIONAL_CONTACT_INFO_DISPLAY_NAME_ALTERNATIVE);
            contactInfo.carrierPresence =
                    cursor.getInt(PhoneQuery.ADDITIONAL_CONTACT_INFO_CARRIER_PRESENCE);
        }
    }

    /**
     * Determines the contact information for the given phone number.
     *
     * <p>It returns the contact info if found.
     *
     * <p>If no contact corresponds to the given phone number, returns {@link ContactInfo#EMPTY}.
     *
     * <p>If the lookup fails for some other reason, it returns null.
     */
    @SuppressWarnings("ReferenceEquality")
    private ContactInfo queryContactInfoForPhoneNumber(
            String number, String countryIso, long directoryId) {
        if (TextUtils.isEmpty(number)) {
            LogUtil.d("ContactInfoHelper.queryContactInfoForPhoneNumber", "number is empty");
            return null;
        }

        ContactInfo info = lookupContactFromUri(getContactInfoLookupUri(number, directoryId));
        if (info == null) {
            LogUtil.d("ContactInfoHelper.queryContactInfoForPhoneNumber", "info looked up is null");
        }
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = formatPhoneNumber(number, countryIso);
            if (directoryId == -1) {
                // Contact found in the default directory
                info.sourceType = ContactSource.Type.SOURCE_TYPE_DIRECTORY;
            } else {
                // Contact found in the extended directory specified by directoryId
                info.sourceType = ContactSource.Type.SOURCE_TYPE_EXTENDED;
            }
        } else if (cachedNumberLookupService != null) {
            CachedContactInfo cacheInfo =
                    cachedNumberLookupService.lookupCachedContactFromNumber(context, number);
            if (cacheInfo != null) {
                if (!cacheInfo.getContactInfo().isBadData) {
                    info = cacheInfo.getContactInfo();
                } else {
                    LogUtil.i("ContactInfoHelper.queryContactInfoForPhoneNumber", "info is bad data");
                }
            }
        }
        return info;
    }

    /**
     * Format the given phone number
     *
     * @param number     the number to be formatted.
     * @param countryIso the ISO 3166-1 two letters country code, the country's convention will be
     *                   used to format the number if the normalized phone is null.
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberHelper.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = currentCountryIso;
        }
        return PhoneNumberHelper.formatNumber(context, number, null, countryIso);
    }

    /**
     * Stores differences between the updated contact info and the current call log contact info.
     *
     * @param number      The number of the contact.
     * @param countryIso  The country associated with this number.
     * @param updatedInfo The updated contact info.
     * @param callLogInfo The call log entry's current contact info.
     */
    public void updateCallLogContactInfo(
            String number, String countryIso, ContactInfo updatedInfo, ContactInfo callLogInfo) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.WRITE_CALL_LOG)) {
            return;
        }

        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }

            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }

            // Only replace the normalized number if the new updated normalized number isn't empty.
            if (!TextUtils.isEmpty(updatedInfo.normalizedNumber)
                    && !TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }

            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }

            final Uri updatedPhotoUriContactsOnly = UriUtils.nullForNonContactsUri(updatedInfo.photoUri);
            if (!UriUtils.areEqual(updatedPhotoUriContactsOnly, callLogInfo.photoUri)) {
                values.put(Calls.CACHED_PHOTO_URI, UriUtils.uriToString(updatedPhotoUriContactsOnly));
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.geoDescription, callLogInfo.geoDescription)) {
                values.put(Calls.GEOCODED_LOCATION, updatedInfo.geoDescription);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(
                    Calls.CACHED_PHOTO_URI,
                    UriUtils.uriToString(UriUtils.nullForNonContactsUri(updatedInfo.photoUri)));
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            values.put(Calls.GEOCODED_LOCATION, updatedInfo.geoDescription);
            needsUpdate = true;
        }

        if (!needsUpdate) {
            return;
        }

        try {
            if (countryIso == null) {
                context
                        .getContentResolver()
                        .update(
                                TelecomUtil.getCallLogUri(context),
                                values,
                                Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                                new String[]{number});
            } else {
                context
                        .getContentResolver()
                        .update(
                                TelecomUtil.getCallLogUri(context),
                                values,
                                Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                                new String[]{number, countryIso});
            }
        } catch (SQLiteFullException e) {
            LogUtil.e(TAG, "Unable to update contact info in call log db", e);
        }
    }

    public void updateCachedNumberLookupService(ContactInfo updatedInfo) {
        if (cachedNumberLookupService != null) {
            if (hasName(updatedInfo)) {
                CachedContactInfo cachedContactInfo =
                        cachedNumberLookupService.buildCachedContactInfo(updatedInfo);
                cachedNumberLookupService.addContact(context, cachedContactInfo);
            }
        }
    }

    /**
     * Given a contact's sourceType, return true if the contact is a business
     *
     * @param sourceType sourceType of the contact. This is usually populated by {@link
     *                   #cachedNumberLookupService}.
     */
    public boolean isBusiness(ContactSource.Type sourceType) {
        return cachedNumberLookupService != null && cachedNumberLookupService.isBusiness(sourceType);
    }

    /**
     * This function looks at a contact's source and determines if the user can mark caller ids from
     * this source as invalid.
     *
     * @param sourceType The source type to be checked
     * @param objectId   The ID of the Contact object.
     * @return true if contacts from this source can be marked with an invalid caller id
     */
    public boolean canReportAsInvalid(ContactSource.Type sourceType, String objectId) {
        return cachedNumberLookupService != null
                && cachedNumberLookupService.canReportAsInvalid(sourceType, objectId);
    }

    /**
     * Update ContactInfo by querying to Cequint Caller ID. Only name, geoDescription and photo uri
     * will be updated if available.
     */
    @WorkerThread
    public void updateFromCequintCallerId(
            @Nullable CequintCallerIdManager cequintCallerIdManager, ContactInfo info, String number) {
        Assert.isWorkerThread();
        if (!CequintCallerIdManager.isCequintCallerIdEnabled(context)) {
            return;
        }
        if (cequintCallerIdManager == null) {
            return;
        }
        CequintCallerIdContact cequintCallerIdContact =
                cequintCallerIdManager.getCachedCequintCallerIdContact(context, number);
        if (cequintCallerIdContact == null) {
            return;
        }
        if (TextUtils.isEmpty(info.name) && !TextUtils.isEmpty(cequintCallerIdContact.name())) {
            info.name = cequintCallerIdContact.name();
        }
        if (!TextUtils.isEmpty(cequintCallerIdContact.geolocation())) {
            info.geoDescription = cequintCallerIdContact.geolocation();
            info.sourceType = ContactSource.Type.SOURCE_TYPE_CEQUINT_CALLER_ID;
        }
        // Only update photo if local lookup has no result.
        if (!info.contactExists && info.photoUri == null && cequintCallerIdContact.photoUri() != null) {
            info.photoUri = UriUtils.parseUriOrNull(cequintCallerIdContact.photoUri());
        }
    }
}
