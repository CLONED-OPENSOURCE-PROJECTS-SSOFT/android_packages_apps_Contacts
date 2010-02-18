/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteFullException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Contacts.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.lang.ref.WeakReference;

//Wysie
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

//Wysie: Contact pictures
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract.QuickContact;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.QuickContactBadge;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.ref.SoftReference;
import java.util.HashSet;


/**
 * Displays a list of call log entries.
 */
public class RecentCallsListActivity extends ListActivity
        implements View.OnCreateContextMenuListener {
    private static final String TAG = "RecentCallsList";

    /** The projection to use when querying the call log table */
    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL
    };

    static final int ID_COLUMN_INDEX = 0;
    static final int NUMBER_COLUMN_INDEX = 1;
    static final int DATE_COLUMN_INDEX = 2;
    static final int DURATION_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    static final int CALLER_NAME_COLUMN_INDEX = 5;
    static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;

    /** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            //Wysie: Contact pictures
            PhoneLookup.PHOTO_ID,
            PhoneLookup.LOOKUP_KEY
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;    
    //Wysie: Contact pictures
    static final int PHOTO_ID_COLUMN_INDEX = 5;
    static final int LOOKUP_KEY_COLUMN_INDEX = 6;

    private static final int MENU_ITEM_DELETE = 1;
    private static final int MENU_ITEM_DELETE_ALL = 2;
    private static final int MENU_ITEM_VIEW_CONTACTS = 3;
    private static final int MENU_ITEM_DELETE_ALL_INCOMING = 4;
    private static final int MENU_ITEM_DELETE_ALL_OUTGOING = 5;
    private static final int MENU_ITEM_DELETE_ALL_MISSED = 6;

    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;

    RecentCallsAdapter mAdapter;
    private QueryHandler mQueryHandler;
    String mVoiceMailNumber;
    
    //Wysie
    private MenuItem mPreferences;    
    private SharedPreferences ePrefs;
    private static boolean exactTime;
    private static boolean is24hour;
    private static boolean showSeconds;
    private static final String format24HourSeconds = "MMM d, kk:mm:ss";
    private static final String format24Hour = "MMM d, kk:mm";
    private static final String format12HourSeconds = "MMM d, h:mm:ssaa";
    private static final String format12Hour = "MMM d, h:mmaa";
    
    private static final int MENU_PREFERENCES = 7;
    
    //Wysie: Contact pictures
    private static ExecutorService sImageFetchThreadPool;
    private int mScrollState;
    private static boolean mDisplayPhotos;
    private static boolean isQuickContact;
    private static boolean showDialButton;

    static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;        
        //Wysie: Contact pictures
        public long photoId;
        public String lookupKey;

        public static ContactInfo EMPTY = new ContactInfo();
    }

    public static final class RecentCallsListItemViews {
        //Wysie: Contact pictures
        QuickContactBadge photoView;
        ImageView nonQuickContactPhotoView;
        
        TextView line1View;
        TextView labelView;
        TextView numberView;
        TextView dateView;
        ImageView iconView;
        View callView;
        
        View dividerView;
    }

    static final class CallerInfoQuery {
        String number;
        int position;
        String name;
        int numberType;
        String numberLabel;
    }

    /**
     * Shared builder used by {@link #formatPhoneNumber(String)} to minimize
     * allocations when formatting phone numbers.
     */
    private static final SpannableStringBuilder sEditable = new SpannableStringBuilder();

    /**
     * Invalid formatting type constant for {@link #sFormattingType}.
     */
    private static final int FORMATTING_TYPE_INVALID = -1;

    /**
     * Cached formatting type for current {@link Locale}, as provided by
     * {@link PhoneNumberUtils#getFormatTypeForLocale(Locale)}.
     */
    private static int sFormattingType = FORMATTING_TYPE_INVALID;
    
    
    //Wysie: Contact pictures
    final static class PhotoInfo {
        public int position;
        public long photoId;
        public Uri contactUri;

        public PhotoInfo(int position, long photoId, Uri contactUri) {
            this.position = position;
            this.photoId = photoId;
            this.contactUri = contactUri;
        }
        public QuickContactBadge photoView;
    }

    /** Adapter class to fill in data for the Call Log */
    final class RecentCallsAdapter extends ResourceCursorAdapter
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener, OnScrollListener {
        HashMap<String,ContactInfo> mContactInfo;
        private final LinkedList<CallerInfoQuery> mRequests;
        private volatile boolean mDone;
        private boolean mLoading = true;
        ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        private CharSequence[] mLabelArray;

        private Drawable mDrawableIncoming;
        private Drawable mDrawableOutgoing;
        private Drawable mDrawableMissed;
        
        //Wysie
        private ImageFetchHandler mImageHandler;
        private ImageDbFetcher mImageFetcher;
        private static final int FETCH_IMAGE_MSG = 3;
        
        //Wysie: Contact pictures
        private HashMap<Long, SoftReference<Bitmap>> mBitmapCache = null;
        private HashSet<ImageView> mItemsMissingImages = null;
        

        public void onClick(View view) {
            if (view instanceof QuickContactBadge) {
                PhotoInfo info = (PhotoInfo)view.getTag();
                QuickContact.showQuickContact(mContext, view, info.contactUri, QuickContact.MODE_MEDIUM, null);
                isQuickContact = true;
            }
            else {
                String number = (String) view.getTag();
                if (!TextUtils.isEmpty(number)) {
                    Uri telUri = Uri.fromParts("tel", number, null);
                    startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, telUri));
                }
            }
        }

        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD, 1000);
                mFirst = false;
            }
            return true;
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REDRAW:
                        notifyDataSetChanged();
                        break;
                    case START_THREAD:
                        startRequestProcessing();
                        break;
                }
            }
        };

        public RecentCallsAdapter() {
            super(RecentCallsListActivity.this, R.layout.recent_calls_list_item, null);

            mContactInfo = new HashMap<String,ContactInfo>();
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;

            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            mLabelArray = getResources().getTextArray(com.android.internal.R.array.phoneTypes);
            
            //Wysie: Contact pictures
            mBitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
            mItemsMissingImages = new HashSet<ImageView>();
            mImageHandler = new ImageFetchHandler();
        }

        /**
         * Requery on background thread when {@link Cursor} changes.
         */
        @Override
        protected void onContentChanged() {
            // Start async requery
            startQuery();
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfo.get(number);
        }

        public void startRequestProcessing() {
            mDone = false;
            mCallerIdThread = new Thread(this);
            mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
            mCallerIdThread.start();
        }

        public void stopRequestProcessing() {
            mDone = true;
            if (mCallerIdThread != null) mCallerIdThread.interrupt();
        }

        public void clearCache() {
            synchronized (mContactInfo) {
                mContactInfo.clear();
            }
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
            if (TextUtils.equals(ciq.name, ci.name)
                    && TextUtils.equals(ciq.numberLabel, ci.label)
                    && ciq.numberType == ci.type) {
                return;
            }
            ContentValues values = new ContentValues(3);
            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);

            try {
                RecentCallsListActivity.this.getContentResolver().update(Calls.CONTENT_URI, values,
                        Calls.NUMBER + "='" + ciq.number + "'", null);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception while updating call info", e);
            }
        }

        private void enqueueRequest(String number, int position,
                String name, int numberType, String numberLabel) {
            CallerInfoQuery ciq = new CallerInfoQuery();
            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
        }

        private void queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfo.get(ciq.number);
            if (info != null && info != ContactInfo.EMPTY) {
                synchronized (mRequests) {
                    if (mRequests.isEmpty()) {
                        mHandler.sendEmptyMessage(REDRAW);
                    }
                }
            } else {
                Cursor phonesCursor =
                    RecentCallsListActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                    Uri.encode(ciq.number)),
                    PHONES_PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        info = new ContactInfo();
                        info.personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                        info.name = phonesCursor.getString(NAME_COLUMN_INDEX);
                        info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
                        info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                        
                        //Wysie: Contact pictures
                        info.photoId = phonesCursor.getLong(PHOTO_ID_COLUMN_INDEX);
                        info.lookupKey = phonesCursor.getString(LOOKUP_KEY_COLUMN_INDEX);

                        // New incoming phone number invalidates our formatted
                        // cache. Any cache fills happen only on the GUI thread.
                        info.formattedNumber = null;

                        mContactInfo.put(ciq.number, info);
                        // Inform list to update this item, if in view
                        synchronized (mRequests) {
                            if (mRequests.isEmpty()) {
                                mHandler.sendEmptyMessage(REDRAW);
                            }
                        }
                    }
                    phonesCursor.close();
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        public void run() {
            while (!mDone) {
                CallerInfoQuery ciq = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {
                            // Ignore and continue processing requests
                        }
                    }
                }
                if (ciq != null) {
                    queryContactInfo(ciq);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = super.newView(context, cursor, parent);

            // Get the views to bind to
            RecentCallsListItemViews views = new RecentCallsListItemViews();
            views.line1View = (TextView) view.findViewById(R.id.line1);
            views.labelView = (TextView) view.findViewById(R.id.label);
            views.numberView = (TextView) view.findViewById(R.id.number);
            views.dateView = (TextView) view.findViewById(R.id.date);
            views.iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            views.dividerView = view.findViewById(R.id.divider);
            views.callView = view.findViewById(R.id.call_icon);
            views.callView.setOnClickListener(this);
            
            //Wysie: Contact pictures
            views.photoView = (QuickContactBadge) view.findViewById(R.id.photo);
            views.photoView.setOnClickListener(this);
            
            views.nonQuickContactPhotoView = (ImageView) view.findViewById(R.id.noQuickContactPhoto);

            view.setTag(views);

            return view;
        }


        @Override
        public void bindView(View view, Context context, Cursor c) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = null;
            String callerName = c.getString(CALLER_NAME_COLUMN_INDEX);
            int callerNumberType = c.getInt(CALLER_NUMBERTYPE_COLUMN_INDEX);
            String callerNumberLabel = c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);
            
            //Wysie
            boolean noContactInfo = false;
            
            // Store away the number so we can call it directly if you click on the call icon
            views.callView.setTag(number);
            
            //Wysie: Use iconView to dial out if dial button is hidden            
            if (!showDialButton) {
                views.iconView.setTag(number);
                views.iconView.setOnClickListener(this);
                //views.iconView.setBackgroundResource(R.drawable.call_background);
            } else {
                views.iconView.setTag(null);
                views.iconView.setOnClickListener(null);
                //views.iconView.setBackgroundResource(0);
            }

            // Lookup contacts with this number
            ContactInfo info = mContactInfo.get(number);
            if (info == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                mContactInfo.put(number, info);
                enqueueRequest(number, c.getPosition(),
                        callerName, callerNumberType, callerNumberLabel);
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
                if (!TextUtils.equals(info.name, callerName)
                        || info.type != callerNumberType
                        || !TextUtils.equals(info.label, callerNumberLabel)) {
                    // Something is amiss, so sync up.
                    enqueueRequest(number, c.getPosition(),
                            callerName, callerNumberType, callerNumberLabel);
                }

                // Format and cache phone number for found contact
                if (info.formattedNumber == null) {
                    info.formattedNumber = formatPhoneNumber(info.number);
                }
                formattedNumber = info.formattedNumber;
            }

            String name = info.name;
            int ntype = info.type;
            String label = info.label;
            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;

                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number);
            }
            // Set the text lines and call icon.
            // Assumes the call back feature is on most of the
            // time. For private and unknown numbers: hide it.
            views.callView.setVisibility(View.VISIBLE);           


            if (!TextUtils.isEmpty(name)) {
                views.line1View.setText(name);
                views.labelView.setVisibility(View.VISIBLE);
                CharSequence numberLabel = Phone.getDisplayLabel(context, ntype, label,
                        mLabelArray);
                views.numberView.setVisibility(View.VISIBLE);
                views.numberView.setText(formattedNumber);
                if (!TextUtils.isEmpty(numberLabel)) {
                    views.labelView.setText(numberLabel);
                    views.labelView.setVisibility(View.VISIBLE);
                } else {
                    views.labelView.setVisibility(View.GONE);
                }
            } else {
                if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    number = getString(R.string.unknown);
                    views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                    number = getString(R.string.private_num);
                    views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    number = getString(R.string.payphone);
                } else if (number.equals(mVoiceMailNumber)) {
                    number = getString(R.string.voicemail);
                } else {
                    // Just a raw number, and no cache, so format it nicely
                    number = formatPhoneNumber(number);
                }
                
                //Wysie
                noContactInfo = true;

                views.line1View.setText(number);
                views.numberView.setVisibility(View.GONE);
                views.labelView.setVisibility(View.GONE);
            }          
            
            //Wysie: Contact pictures
            if (mDisplayPhotos) {
                long photoId = info.photoId;

                // Build soft lookup reference
                final long contactId = info.personId;
                final String lookupKey = info.lookupKey;
                Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
                ImageView viewToUse;                
                
                if (noContactInfo) {
                    viewToUse = views.nonQuickContactPhotoView;
                    views.photoView.setVisibility(View.INVISIBLE);
                    views.nonQuickContactPhotoView.setVisibility(View.VISIBLE);
                } else {
                    viewToUse = views.photoView;                    
                    //views.photoView.assignContactUri(contactUri); //Wysie: Commented out, we handle it explicityly in onClick()
                    views.photoView.setTag(contactUri);
                    views.photoView.setVisibility(View.VISIBLE);
                    views.nonQuickContactPhotoView.setVisibility(View.INVISIBLE);
                }
                
                final int position = c.getPosition();
                viewToUse.setTag(new PhotoInfo(position, photoId, contactUri));

                if (photoId == 0) {
                    viewToUse.setImageResource(R.drawable.ic_contact_list_picture);
                } else {

                    Bitmap photo = null;

                    // Look for the cached bitmap
                    SoftReference<Bitmap> ref = mBitmapCache.get(photoId);
                    if (ref != null) {
                        photo = ref.get();
                        if (photo == null) {
                            mBitmapCache.remove(photoId);
                        }
                    }

                    // Bind the photo, or use the fallback no photo resource
                    if (photo != null) {
                        viewToUse.setImageBitmap(photo);
                    } else {
                        // Cache miss
                        viewToUse.setImageResource(R.drawable.ic_contact_list_picture);

                        // Add it to a set of images that are populated asynchronously.
                        mItemsMissingImages.add(viewToUse);

                        if (mScrollState != OnScrollListener.SCROLL_STATE_FLING) {

                            // Scrolling is idle or slow, go get the image right now.
                            sendFetchImageMessage(viewToUse);
                        }
                    }
                }
            }
            else {
                views.photoView.setVisibility(View.GONE);
                views.nonQuickContactPhotoView.setVisibility(View.GONE);
            }

            int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);
            
            if (!exactTime) {
                // Set the date/time field by mixing relative and absolute times.
                int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

                views.dateView.setText(
                        DateUtils.getRelativeTimeSpanString(date,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        flags));
            } else {
                String format = null;

                if (is24hour) {
                    if (showSeconds) {
                        format = format24HourSeconds;
                    } else {
                        format = format24Hour;
                    }
                } else {
                    if (showSeconds) {
                        format = format12HourSeconds;
                    } else {
                        format = format12Hour;
                    }                  	
                }
                
                views.dateView.setText(DateFormat.format(format, date));                         
            }            

            if (showDialButton) {
                views.dividerView.setVisibility(View.VISIBLE);
                views.callView.setVisibility(View.VISIBLE);
            } else {
                views.dividerView.setVisibility(View.GONE);
                views.callView.setVisibility(View.GONE);
            }

            // Set the icon
            switch (type) {
                case Calls.INCOMING_TYPE:
                    views.iconView.setImageDrawable(mDrawableIncoming);
                    break;

                case Calls.OUTGOING_TYPE:
                    views.iconView.setImageDrawable(mDrawableOutgoing);
                    break;

                case Calls.MISSED_TYPE:
                    views.iconView.setImageDrawable(mDrawableMissed);
                    break;
            }

            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }
        
        
        //Wysie: Contact pictures
        private class ImageFetchHandler extends Handler {

            @Override
            public void handleMessage(Message message) {
                if (RecentCallsListActivity.this.isFinishing()) {
                    return;
                }
                switch(message.what) {
                    case FETCH_IMAGE_MSG: {
                        final ImageView imageView = (ImageView) message.obj;
                        if (imageView == null) {
                            break;
                        }

                        final PhotoInfo info = (PhotoInfo)imageView.getTag();
                        if (info == null) {
                            break;
                        }

                        final long photoId = info.photoId;
                        if (photoId == 0) {
                            break;
                        }

                        SoftReference<Bitmap> photoRef = mBitmapCache.get(photoId);
                        if (photoRef == null) {
                            break;
                        }
                        Bitmap photo = photoRef.get();
                        if (photo == null) {
                            mBitmapCache.remove(photoId);
                            break;
                        }

                        // Make sure the photoId on this image view has not changed
                        // while we were loading the image.
                        synchronized (imageView) {
                            final PhotoInfo updatedInfo = (PhotoInfo)imageView.getTag();
                            long currentPhotoId = updatedInfo.photoId;
                            if (currentPhotoId == photoId) {
                                imageView.setImageBitmap(photo);
                                mItemsMissingImages.remove(imageView);
                            }
                        }
                        break;
                    }
                }
            }

            public void clearImageFecthing() {
                removeMessages(FETCH_IMAGE_MSG);
            }
        }
        
        //Wysie: Contact pictures
        private class ImageDbFetcher implements Runnable {
            long mPhotoId;
            private ImageView mImageView;

            public ImageDbFetcher(long photoId, ImageView imageView) {
                this.mPhotoId = photoId;
                this.mImageView = imageView;
            }

            public void run() {
                if (RecentCallsListActivity.this.isFinishing()) {
                    return;
                }

                if (Thread.interrupted()) {
                    // shutdown has been called.
                    return;
                }
                Bitmap photo = null;
                try {
                    photo = ContactsUtils.loadContactPhoto(mContext, mPhotoId, null);
                } catch (OutOfMemoryError e) {
                    // Not enough memory for the photo, do nothing.
                }

                if (photo == null) {
                    return;
                }

                mBitmapCache.put(mPhotoId, new SoftReference<Bitmap>(photo));

                if (Thread.interrupted()) {
                    // shutdown has been called.
                    return;
                }

                // Update must happen on UI thread
                Message msg = new Message();
                msg.what = FETCH_IMAGE_MSG;
                msg.obj = mImageView;
                mImageHandler.sendMessage(msg);
            }
        }
        
        //Wysie: Contact pictures
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            // no op
        }
        
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mScrollState = scrollState;
            if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
                // If we are in a fling, stop loading images.
                clearImageFetching();
            } else if (mDisplayPhotos) {
                processMissingImageItems(view);
            }
        }

        private void processMissingImageItems(AbsListView view) {
            for (ImageView iv : mItemsMissingImages) {
                sendFetchImageMessage(iv);
            }
        }

        private void sendFetchImageMessage(ImageView view) {
            final PhotoInfo info = (PhotoInfo) view.getTag();
            if (info == null) {
                return;
            }
            final long photoId = info.photoId;
            if (photoId == 0) {
                return;
            }
            mImageFetcher = new ImageDbFetcher(photoId, view);
            synchronized (RecentCallsListActivity.this) {
                // can't sync on sImageFetchThreadPool.
                if (sImageFetchThreadPool == null) {
                    // Don't use more than 3 threads at a time to update. The thread pool will be
                    // shared by all contact items.
                    sImageFetchThreadPool = Executors.newFixedThreadPool(3);
                }
                sImageFetchThreadPool.execute(mImageFetcher);
            }
        }
        
        public void clearImageFetching() {
            synchronized (RecentCallsListActivity.this) {
                if (sImageFetchThreadPool != null) {
                    sImageFetchThreadPool.shutdownNow();
                    sImageFetchThreadPool = null;
                }
            }

            mImageHandler.clearImageFecthing();
        }
    }

    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<RecentCallsListActivity> mActivity;

        /**
         * Simple handler that wraps background calls to catch
         * {@link SQLiteException}, such as when the disk is full.
         */
        protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
            public CatchingWorkerHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    // Perform same query while catching any exceptions
                    super.handleMessage(msg);
                } catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteFullException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteDatabaseCorruptException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                }
            }
        }

        @Override
        protected Handler createHandler(Looper looper) {
            // Provide our special handler that catches exceptions
            return new CatchingWorkerHandler(looper);
        }

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<RecentCallsListActivity>(
                    (RecentCallsListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final RecentCallsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                final RecentCallsListActivity.RecentCallsAdapter callsAdapter = activity.mAdapter;
                callsAdapter.setLoading(false);
                callsAdapter.changeCursor(cursor);
            } else {
                cursor.close();
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        //Wysie
        ePrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        isQuickContact = false;
        
        setContentView(R.layout.recent_calls);

        // Typing here goes to the dialer
        setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mAdapter = new RecentCallsAdapter();
        getListView().setOnCreateContextMenuListener(this);
        setListAdapter(mAdapter);

        mVoiceMailNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                .getVoiceMailNumber();
        mQueryHandler = new QueryHandler(this);

        // Reset locale-based formatting cache
        sFormattingType = FORMATTING_TYPE_INVALID;
    }

    @Override
    protected void onResume() {
        if (isQuickContact) {
            isQuickContact = false;
            super.onResume();
        }
        else {
            // The adapter caches looked up numbers, clear it so they will get
            // looked up again.
            if (mAdapter != null) {
                mAdapter.clearCache();
            }
            
            // Force cache to reload so we don't show stale photos.
            if (mAdapter.mBitmapCache != null) {
                mAdapter.mBitmapCache.clear();
            }
            
            exactTime = ePrefs.getBoolean("cl_exact_time", true);
            is24hour = DateFormat.is24HourFormat(this);
            showSeconds = ePrefs.getBoolean("cl_show_seconds", true);
            mDisplayPhotos = ePrefs.getBoolean("cl_show_pic", true);
            showDialButton = ePrefs.getBoolean("cl_show_dial_button", false);
            
            super.onResume();

            startQuery();
            resetNewCallsFlag();
        
            mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

            mAdapter.mPreDrawListener = null; // Let it restart the thread after next draw
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        Cursor cursor = mAdapter.getCursor();
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Clear notifications only when window gains focus.  This activity won't
        // immediately receive focus if the keyguard screen is above it.
        if (hasFocus) {
            try {
                ITelephony iTelephony =
                        ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                if (iTelephony != null) {
                    iTelephony.cancelMissedCallsNotification();
                } else {
                    Log.w(TAG, "Telephony service is null, can't call " +
                            "cancelMissedCallsNotification");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
            }
        }
    }

    /**
     * Format the given phone number using
     * {@link PhoneNumberUtils#formatNumber(android.text.Editable, int)}. This
     * helper method uses {@link #sEditable} and {@link #sFormattingType} to
     * prevent allocations between multiple calls.
     * <p>
     * Because of the shared {@link #sEditable} builder, <b>this method is not
     * thread safe</b>, and should only be called from the GUI thread.
     * <p>
     * If the given String object is null or empty, return an empty String.
     */
    private String formatPhoneNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }

        // Cache formatting type if not already present
        if (sFormattingType == FORMATTING_TYPE_INVALID) {
            sFormattingType = PhoneNumberUtils.getFormatTypeForLocale(Locale.getDefault());
        }

        sEditable.clear();
        sEditable.append(number);

        PhoneNumberUtils.formatNumber(sEditable, sFormattingType);
        return sEditable.toString();
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI,
                values, where.toString(), null);
    }

    private void startQuery() {
        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI,
                CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_DELETE_ALL, 0, R.string.recentCalls_deleteAll)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_INCOMING, 0, R.string.recentCalls_deleteAllIncoming).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_OUTGOING, 0, R.string.recentCalls_deleteAllOutgoing).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_MISSED, 0, R.string.recentCalls_deleteAllMissed).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
                
	    mPreferences = menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(android.R.drawable.ic_menu_preferences);
        //Wysie_Soh: Preferences intent
        mPreferences.setIntent(new Intent(this, ContactsPreferences.class));
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(menuInfo.position);

        String number = cursor.getString(NUMBER_COLUMN_INDEX);
        Uri numberUri = null;
        boolean isVoicemail = false;
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = getString(R.string.payphone);
        } else if (number.equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
            numberUri = Uri.parse("voicemail:x");
            isVoicemail = true;
        } else {
            numberUri = Uri.fromParts("tel", number, null);
        }

        ContactInfo info = mAdapter.getContactInfo(number);
        boolean contactInfoPresent = (info != null && info != ContactInfo.EMPTY);
        if (contactInfoPresent) {
            menu.setHeaderTitle(info.name);
        } else {
            menu.setHeaderTitle(number);
        }

        if (numberUri != null) {
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberUri);
            menu.add(0, 0, 0, getResources().getString(R.string.recentCalls_callNumber, number))
                    .setIntent(intent);
        }

        if (contactInfoPresent) {
            menu.add(0, 0, 0, R.string.menu_viewContact)
                    .setIntent(new Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(Contacts.CONTENT_URI, info.personId)));
        }

        if (numberUri != null && !isVoicemail) {
            menu.add(0, 0, 0, R.string.recentCalls_editNumberBeforeCall)
                    .setIntent(new Intent(Intent.ACTION_DIAL, numberUri));
            menu.add(0, 0, 0, R.string.menu_sendTextMessage)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", number, null)));
        }
        if (!contactInfoPresent && numberUri != null && !isVoicemail) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Insert.PHONE, number);
            menu.add(0, 0, 0, R.string.recentCalls_addToContact)
                    .setIntent(intent);
        }
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE_ALL: {
                clearCallLog();
                return true;
            }
            case MENU_ITEM_DELETE_ALL_INCOMING: {
                clearCallLogType(Calls.INCOMING_TYPE);
                return true;
            }
            case MENU_ITEM_DELETE_ALL_OUTGOING: {
                clearCallLogType(Calls.OUTGOING_TYPE);
                return true;
            }
            case MENU_ITEM_DELETE_ALL_MISSED: {
                clearCallLogType(Calls.MISSED_TYPE);
                return true;
            }
            case MENU_ITEM_VIEW_CONTACTS: {
                Intent intent = new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // Convert the menu info to the proper type
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return false;
        }

        switch (item.getItemId()) {
            case MENU_ITEM_DELETE: {
                Cursor cursor = mAdapter.getCursor();
                if (cursor != null) {
                    cursor.moveToPosition(menuInfo.position);
                    cursor.deleteRow();
                }
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                long callPressDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Launch voice dialer
                    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                    }
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                try {
                    ITelephony phone = ITelephony.Stub.asInterface(
                            ServiceManager.checkService("phone"));
                    if (phone != null && !phone.isIdle()) {
                        // Let the super class handle it
                        break;
                    }
                } catch (RemoteException re) {
                    // Fall through and try to call the contact
                }

                callEntry(getListView().getSelectedItemPosition());
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    private String getBetterNumberFromContacts(String number) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        ContactInfo ci = mAdapter.mContactInfo.get(number);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor =
                    RecentCallsListActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                    number),
                    PHONES_PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    private void callEntry(int position) {
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = mAdapter.getCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            String number = cursor.getString(NUMBER_COLUMN_INDEX);
            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)
                    || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }

            int callType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
            if (!number.startsWith("+") &&
                    (callType == Calls.INCOMING_TYPE
                            || callType == Calls.MISSED_TYPE)) {
                // If the caller-id matches a contact with a better qualified number, use it
                number = getBetterNumberFromContacts(number);
            }
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    Uri.fromParts("tel", number, null));
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.setData(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id));
        startActivity(intent);
    }  
    
    // Wysie: Dialog to confirm if user wants to clear call log    
    private void clearCallLog() {
        if (ePrefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(R.string.alert_clear_call_log_message);
      
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    deleteCallLog(null, null);
                }
            });
        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });
        
            alert.show();
        } else {
            deleteCallLog(null, null);
        }
    }
    
    private void deleteCallLog(String where, String[] selArgs) {
        try {
            getContentResolver().delete(Calls.CONTENT_URI, where, selArgs);
            // TODO The change notification should do this automatically, but it isn't working
            // right now. Remove this when the change notification is working properly.
            startQuery();
        } catch (SQLiteException sqle) {// Nothing :P
        }
    }
    
    private void clearCallLogType(final int type) {
        int msg = 0;
        
        if (type == Calls.INCOMING_TYPE) {
            msg = R.string.alert_clear_cl_all_incoming;
        } else if (type == Calls.OUTGOING_TYPE) {
            msg = R.string.alert_clear_cl_all_outgoing;
        } else if (type == Calls.MISSED_TYPE) {
            msg = R.string.alert_clear_cl_all_missed;
        }
        
        if (ePrefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(msg);
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    deleteCallLog(Calls.TYPE + "=?", new String[] { Integer.toString(type) });
                }
            });        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });        
            alert.show();
            
        } else {
            deleteCallLog(Calls.TYPE + "=?", new String[] { Integer.toString(type) });
        }        
    }
}
