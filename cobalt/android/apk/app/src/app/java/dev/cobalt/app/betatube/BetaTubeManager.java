// Copyright 2024 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.app.betatube;

// TODO: Firebase Realtime Database is currently accessed without client-side authentication.
//       Implement Firebase Auth (anonymous or service-account scoped) and configure database
//       security rules to prevent unauthorized reads/writes on license, ads, and takeover paths.
// TODO: Add unit tests for license state transitions, offline fallback behavior, dimension
//       boundary conditions, takeover show/dismiss lifecycle, and activation code determinism.

import android.app.Activity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import dev.cobalt.coat.CobaltActivity;

import java.lang.ref.WeakReference;

/**
 * Main orchestrator for the BetaTube VIP system.
 * Manages Firebase listeners for licensing, dimensions, ads, and takeover messages.
 * Uses WeakReference for Activity to prevent memory leaks on H313 24/7 devices.
 */
public class BetaTubeManager {

    private WeakReference<Activity> mActivityRef;
    private BetaTubeCache mCache;
    private BetaTubeOverlayManager mOverlayManager;
    private BetaTubeLockScreen mLockScreen;
    private String mActivationCode;

    private FirebaseDatabase mDatabase;
    private DatabaseReference mLicenseRef;
    private DatabaseReference mDimensionsRef;
    private DatabaseReference mAdsRef;
    private DatabaseReference mTakeoverRef;

    private ValueEventListener mLicenseListener;
    private ValueEventListener mDimensionsListener;
    private ValueEventListener mAdsListener;
    private ValueEventListener mTakeoverListener;

    public BetaTubeManager() {
    }

    /**
     * Initializes the BetaTube system: activation code generation, Firebase listeners,
     * license checking, overlay management.
     *
     * @param activity The host CobaltActivity
     */
    public void initialize(Activity activity) {
        mActivityRef = new WeakReference<Activity>(activity);
        mCache = new BetaTubeCache(activity.getApplicationContext());
        mActivationCode = HardwareIdGenerator.generateActivationCode(activity.getApplicationContext());

        mDatabase = FirebaseDatabase.getInstance();

        setupLicenseListener(activity);
        setupDimensionsListener();
        setupAdsListener();
        setupTakeoverListener();
    }

    private void setupLicenseListener(final Activity activity) {
        mLicenseRef = mDatabase.getReference("licenses").child(mActivationCode).child("status");
        mLicenseListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Activity act = mActivityRef.get();
                if (act == null || act.isFinishing()) {
                    return;
                }

                String status = dataSnapshot.getValue(String.class);
                mCache.cacheLicenseStatus(status != null ? status : "unknown");

                if ("active".equals(status)) {
                    if (mLockScreen != null && mLockScreen.isShowing()) {
                        mLockScreen.dismiss();
                    }
                    initOverlays(act);
                } else {
                    showLockScreen(act);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Use cached license status as fallback
                Activity act = mActivityRef.get();
                if (act == null || act.isFinishing()) {
                    return;
                }

                String cachedStatus = mCache.getCachedLicenseStatus();
                if ("active".equals(cachedStatus)) {
                    initOverlays(act);
                } else {
                    showLockScreen(act);
                }
            }
        };
        mLicenseRef.addValueEventListener(mLicenseListener);
    }

    private void showLockScreen(Activity activity) {
        if (mLockScreen == null) {
            mLockScreen = new BetaTubeLockScreen(activity, mActivationCode);
        }
        if (!mLockScreen.isShowing()) {
            mLockScreen.show();
        }
    }

    private void initOverlays(Activity activity) {
        if (mOverlayManager == null) {
            mOverlayManager = new BetaTubeOverlayManager(activity);
        }
    }

    private void setupDimensionsListener() {
        mDimensionsRef = mDatabase.getReference("settings/dimensions");
        mDimensionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Activity act = mActivityRef.get();
                if (act == null || act.isFinishing()) {
                    return;
                }

                Integer x = dataSnapshot.child("X").getValue(Integer.class);
                Integer y = dataSnapshot.child("Y").getValue(Integer.class);
                int dimX = (x != null) ? x.intValue() : 0;
                int dimY = (y != null) ? y.intValue() : 0;

                int screenWidth = act.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = act.getResources().getDisplayMetrics().heightPixels;
                dimX = Math.max(0, Math.min(dimX, screenWidth / 2));
                dimY = Math.max(0, Math.min(dimY, screenHeight / 2));

                mCache.cacheDimensions(dimX, dimY);

                if (act instanceof CobaltActivity) {
                    ((CobaltActivity) act).setVideoSurfaceBounds(dimX, dimY,
                            screenWidth - dimX,
                            screenHeight - dimY);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Use cached dimensions as fallback
                Activity act = mActivityRef.get();
                if (act == null || act.isFinishing()) {
                    return;
                }

                int cachedX = mCache.getCachedX();
                int cachedY = mCache.getCachedY();
                if (act instanceof CobaltActivity && (cachedX != 0 || cachedY != 0)) {
                    ((CobaltActivity) act).setVideoSurfaceBounds(cachedX, cachedY,
                            act.getResources().getDisplayMetrics().widthPixels - cachedX,
                            act.getResources().getDisplayMetrics().heightPixels - cachedY);
                }
            }
        };
        mDimensionsRef.addValueEventListener(mDimensionsListener);
    }

    private void setupAdsListener() {
        mAdsRef = mDatabase.getReference("ads");
        mAdsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mOverlayManager == null) {
                    return;
                }

                String logoUrl = dataSnapshot.child("logo_url").getValue(String.class);
                String movingText = dataSnapshot.child("moving_text").getValue(String.class);
                String bannerContent = dataSnapshot.child("banner_content").getValue(String.class);

                if (logoUrl != null) {
                    mCache.cacheLogoUrl(logoUrl);
                    mOverlayManager.updateLogo(logoUrl);
                }
                if (bannerContent != null) {
                    mCache.cacheBannerContent(bannerContent);
                    mOverlayManager.updateFixedBanner(bannerContent);
                }
                if (movingText != null) {
                    mCache.cacheTickerText(movingText);
                    mOverlayManager.updateTicker(movingText);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Use cached ads values as fallback
                if (mOverlayManager == null) {
                    return;
                }

                String cachedLogo = mCache.getCachedLogoUrl();
                String cachedBanner = mCache.getCachedBannerContent();
                String cachedTicker = mCache.getCachedTickerText();

                if (cachedLogo != null) {
                    mOverlayManager.updateLogo(cachedLogo);
                }
                if (cachedBanner != null) {
                    mOverlayManager.updateFixedBanner(cachedBanner);
                }
                if (cachedTicker != null) {
                    mOverlayManager.updateTicker(cachedTicker);
                }
            }
        };
        mAdsRef.addValueEventListener(mAdsListener);
    }

    private void setupTakeoverListener() {
        mTakeoverRef = mDatabase.getReference("settings/takeover_message");
        mTakeoverListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mOverlayManager == null) {
                    return;
                }

                String message = dataSnapshot.getValue(String.class);
                if (message != null && !message.isEmpty()) {
                    mCache.cacheTakeoverMessage(message);
                    mOverlayManager.showTakeover(message);
                } else {
                    mOverlayManager.dismissTakeover();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Use cached takeover value as fallback
                if (mOverlayManager == null) {
                    return;
                }

                String cachedTakeover = mCache.getCachedTakeoverMessage();
                if (cachedTakeover != null && !cachedTakeover.isEmpty()) {
                    mOverlayManager.showTakeover(cachedTakeover);
                }
            }
        };
        mTakeoverRef.addValueEventListener(mTakeoverListener);
    }

    /**
     * Cleans up all Firebase listeners and overlay resources.
     * Must be called in Activity.onDestroy() to prevent memory leaks.
     */
    public void destroy() {
        // Remove Firebase listeners
        if (mLicenseRef != null && mLicenseListener != null) {
            mLicenseRef.removeEventListener(mLicenseListener);
        }
        if (mDimensionsRef != null && mDimensionsListener != null) {
            mDimensionsRef.removeEventListener(mDimensionsListener);
        }
        if (mAdsRef != null && mAdsListener != null) {
            mAdsRef.removeEventListener(mAdsListener);
        }
        if (mTakeoverRef != null && mTakeoverListener != null) {
            mTakeoverRef.removeEventListener(mTakeoverListener);
        }

        // Destroy overlay manager
        if (mOverlayManager != null) {
            mOverlayManager.destroy();
            mOverlayManager = null;
        }

        // Dismiss lock screen
        if (mLockScreen != null) {
            if (mLockScreen.isShowing()) {
                mLockScreen.dismiss();
            }
            mLockScreen = null;
        }

        // Null all references
        mLicenseRef = null;
        mDimensionsRef = null;
        mAdsRef = null;
        mTakeoverRef = null;
        mLicenseListener = null;
        mDimensionsListener = null;
        mAdsListener = null;
        mTakeoverListener = null;
        mDatabase = null;
        mCache = null;
        mActivityRef = null;
    }
}
