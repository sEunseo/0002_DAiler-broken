/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.fissy.dialer.lookup;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.fissy.dialer.lookup.google.GoogleForwardLookup;
import com.fissy.dialer.phonenumbercache.ContactInfo;
import com.fissy.dialer.searchfragment.common.Projections;
import com.fissy.dialer.util.PermissionsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LookupProvider extends ContentProvider {
    public static final String AUTHORITY = "com.fissy.dialer.lookup";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri NEARBY_AND_PEOPLE_LOOKUP_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "nearby_and_people");
    public static final Uri IMAGE_CACHE_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "images");
    private static final String TAG = LookupProvider.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final UriMatcher uriMatcher = new UriMatcher(-1);
    private static final int NEARBY = 0;
    private static final int PEOPLE = 1;
    private static final int NEARBY_AND_PEOPLE = 2;
    private static final int IMAGE = 3;

    static {
        uriMatcher.addURI(AUTHORITY, "nearby/*", NEARBY);
        uriMatcher.addURI(AUTHORITY, "people/*", PEOPLE);
        uriMatcher.addURI(AUTHORITY, "nearby_and_people/*", NEARBY_AND_PEOPLE);
        uriMatcher.addURI(AUTHORITY, "images/*", IMAGE);
    }

    private final LinkedList<FutureTask> activeTasks = new LinkedList<>();
    /**
     * Get location from last location query.
     */
    private String PROVIDER = "network";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, final String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (DEBUG) Log.v(TAG, "query: " + uri);

        Location lastLocation = null;
        final int match = uriMatcher.match(uri);

        switch (match) {
            case NEARBY:
            case NEARBY_AND_PEOPLE:
                if (!PermissionsUtil.hasLocationPermissions(getContext())) {
                    Log.v(TAG, "Location permission is missing, can not determine location.");
                } else if (!isLocationEnabled()) {
                    Log.v(TAG, "Location settings is disabled, can no determine location.");
                } else {
                    Log.v(TAG, "Location granted performing lookup.");
                    lastLocation = getLastLocation();
                }
                if (match == NEARBY && lastLocation == null) {
                    Log.v(TAG, "No location available, ignoring query.");
                    return null;
                }
                // fall through to the actual query

            case PEOPLE:
                final String filter = Uri.encode(uri.getLastPathSegment());
                String limit = uri.getQueryParameter(ContactsContract.LIMIT_PARAM_KEY);

                int maxResults = -1;

                try {
                    if (limit != null) {
                        maxResults = Integer.parseInt(limit);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "query: invalid limit parameter: '" + limit + "'");
                }

                final Location finalLastLocation = lastLocation;
                final int finalMaxResults = maxResults;

                return execute(() -> handleFilter(match, projection, filter, finalMaxResults, finalLastLocation));
        }

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert() not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update() not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete() not supported");
    }

    @Override
    public String getType(Uri uri) {
        int match = uriMatcher.match(uri);

        switch (match) {
            case NEARBY:
            case PEOPLE:
            case NEARBY_AND_PEOPLE:
                return Contacts.CONTENT_ITEM_TYPE;

            default:
                return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uriMatcher.match(uri) == IMAGE) {
            String number = uri.getLastPathSegment();
            File image = LookupCache.getImagePath(getContext(), number);

            if (mode.equals("r")) {
                if (!image.exists() || !image.isFile()) {
                    throw new FileNotFoundException("Cached image does not exist");
                }

                return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                throw new FileNotFoundException("The URI is read only");
            }
        }
        throw new FileNotFoundException("Invalid URI: " + uri);
    }

    /**
     * Check if the location services is on.
     *
     * @return Whether location services are enabled
     */
    private boolean isLocationEnabled() {
        try {
            int mode = Settings.Secure.getInt(getContext().getContentResolver(),
                    Settings.Secure.LOCATION_MODE);

            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Failed to get location mode", e);
            return false;
        }
    }

    private Location getLastLocation() {
        LocationManager locationManager = getContext().getSystemService(LocationManager.class);

        try {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                return null;
            }
            locationManager.requestSingleUpdate(new Criteria(), new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                    PROVIDER = provider;
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    PROVIDER = provider;
                }
            }, Looper.getMainLooper());
            if (DEBUG) Log.v(TAG, "provider(" + PROVIDER + ")");
            return locationManager.getLastKnownLocation(PROVIDER);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Process filter/query and perform the lookup.
     *
     * @param projection   Columns to include in query
     * @param filter       String to lookup
     * @param maxResults   Maximum number of results
     * @param lastLocation Coordinates of last location query
     * @return Cursor for the results
     */
    private Cursor handleFilter(int type, String[] projection, String filter,
                                int maxResults, Location lastLocation) {
        if (DEBUG) Log.v(TAG, "handleFilter(" + filter + ")");

        if (filter == null) {
            return null;
        }

        if (lastLocation == null) {
            return null;
        }

        try {
            filter = URLDecoder.decode(filter, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
        }

        ArrayList<ContactInfo> results = new ArrayList<>();
        if (type == NEARBY || type == NEARBY_AND_PEOPLE) {
            List<ContactInfo> nearby = GoogleForwardLookup.lookup(getContext(), filter, lastLocation);
            if (nearby != null) {
                if (DEBUG) Log.v(TAG, "adding places");
                results.addAll(nearby);
            }
        }

        if (results.isEmpty()) {
            if (DEBUG) Log.v(TAG, "handleFilter(" + filter + "): No results");
            return null;
        }

        Cursor cursor = null;
        try {
            cursor = buildResultCursor(projection, results, maxResults);
            if (DEBUG) {
                Log.v(TAG, "handleFilter(" + filter + "): " + cursor.getCount() + " matches");
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON failure", e);
        }

        return cursor;
    }

    /**
     * Query results.
     *
     * @param projection Columns to include in query
     * @param results    Results for the forward lookup
     * @param maxResults Maximum number of rows/results to add to cursor
     * @return Cursor for forward lookup query results
     */
    private Cursor buildResultCursor(String[] projection, List<ContactInfo> results, int maxResults)
            throws JSONException {
        // Extended directories always use this projection
        MatrixCursor cursor = new MatrixCursor(Projections.DATA_PROJECTION);

        int id = 1;
        for (ContactInfo result : results) {
            Object[] row = new Object[Projections.DATA_PROJECTION.length];

            row[Projections.ID] = id;
            row[Projections.PHONE_TYPE] = result.type;
            row[Projections.PHONE_LABEL] = getAddress(result);
            row[Projections.PHONE_NUMBER] = result.number;
            row[Projections.DISPLAY_NAME] = result.name;
            row[Projections.PHOTO_ID] = 0;
            row[Projections.PHOTO_URI] = result.photoUri;
            row[Projections.LOOKUP_KEY] = result.lookupUri.getEncodedFragment();
            row[Projections.CONTACT_ID] = id;

            cursor.addRow(row);

            if (maxResults != -1 && cursor.getCount() >= maxResults) {
                break;
            }

            id++;
        }

        return cursor;
    }

    private String getAddress(ContactInfo info) {
        // Hack: Show city or address for phone label, so they appear in the results list

        String city = null;
        String address = null;

        try {
            String jsonString = info.lookupUri.getEncodedFragment();
            JSONObject json = new JSONObject(jsonString);
            JSONObject contact = json.getJSONObject(Contacts.CONTENT_ITEM_TYPE);

            if (!contact.has(StructuredPostal.CONTENT_ITEM_TYPE)) {
                return null;
            }

            JSONArray addresses = contact.getJSONArray(StructuredPostal.CONTENT_ITEM_TYPE);
            if (addresses.length() == 0) {
                return null;
            }

            JSONObject addressEntry = addresses.getJSONObject(0);
            if (addressEntry.has(StructuredPostal.CITY)) {
                city = addressEntry.getString(StructuredPostal.CITY);
            }
            if (addressEntry.has(StructuredPostal.FORMATTED_ADDRESS)) {
                address = addressEntry.getString(StructuredPostal.FORMATTED_ADDRESS);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get address", e);
        }

        if (city != null) {
            return city;
        } else return address;
    }

    /**
     * Execute thread that is killed after a specified amount of time.
     *
     * @param callable The thread
     * @return Instance of the thread
     */
    private <T> T execute(Callable<T> callable) {
        FutureCallable<T> futureCallable = new FutureCallable<>(callable);
        FutureTask<T> future = new FutureTask<>(futureCallable);
        futureCallable.setFuture(future);

        synchronized (activeTasks) {
            activeTasks.addLast(future);
            Log.v(TAG, "Currently running tasks: " + activeTasks.size());

            while (activeTasks.size() > 8) {
                Log.w(TAG, "Too many tasks, canceling one");
                activeTasks.removeFirst().cancel(true);
            }
        }

        Log.v(TAG, "Starting task " + "FilterThread");

        new Thread(future, "FilterThread").start();

        try {
            Log.v(TAG, "Getting future " + "FilterThread");
            return future.get(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Task was interrupted: " + "FilterThread");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.w(TAG, "Task threw an exception: " + "FilterThread", e);
        } catch (TimeoutException e) {
            Log.w(TAG, "Task timed out: " + "FilterThread");
            future.cancel(true);
        } catch (CancellationException e) {
            Log.w(TAG, "Task was cancelled: " + "FilterThread");
        }

        return null;
    }

    private class FutureCallable<T> implements Callable<T> {
        private final Callable<T> callable;
        private volatile FutureTask<T> future;

        public FutureCallable(Callable<T> callable) {
            future = null;
            this.callable = callable;
        }

        public T call() throws Exception {
            Log.v(TAG, "Future called for " + Thread.currentThread().getName());

            T result = callable.call();
            if (future == null) {
                return result;
            }

            synchronized (activeTasks) {
                activeTasks.remove(future);
            }

            future = null;
            return result;
        }

        public void setFuture(FutureTask<T> future) {
            this.future = future;
        }
    }
}
