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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences-based offline cache for BetaTube configuration.
 * Provides fallback data when Firebase is unreachable.
 */
public class BetaTubeCache {

    private static final String PREFS_NAME = "betatube_cache";
    private static final String KEY_DIMENSION_X = "dimension_x";
    private static final String KEY_DIMENSION_Y = "dimension_y";
    private static final String KEY_LOGO_URL = "logo_url";
    private static final String KEY_BANNER_CONTENT = "banner_content";
    private static final String KEY_TICKER_TEXT = "ticker_text";
    private static final String KEY_TAKEOVER_MESSAGE = "takeover_message";
    private static final String KEY_LICENSE_STATUS = "license_status";

    private final SharedPreferences mPrefs;

    public BetaTubeCache(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void cacheDimensions(int x, int y) {
        mPrefs.edit()
                .putInt(KEY_DIMENSION_X, x)
                .putInt(KEY_DIMENSION_Y, y)
                .apply();
    }

    public int getCachedX() {
        return mPrefs.getInt(KEY_DIMENSION_X, 0);
    }

    public int getCachedY() {
        return mPrefs.getInt(KEY_DIMENSION_Y, 0);
    }

    public void cacheLogoUrl(String url) {
        mPrefs.edit().putString(KEY_LOGO_URL, url).apply();
    }

    public String getCachedLogoUrl() {
        return mPrefs.getString(KEY_LOGO_URL, null);
    }

    public void cacheBannerContent(String content) {
        mPrefs.edit().putString(KEY_BANNER_CONTENT, content).apply();
    }

    public String getCachedBannerContent() {
        return mPrefs.getString(KEY_BANNER_CONTENT, null);
    }

    public void cacheTickerText(String text) {
        mPrefs.edit().putString(KEY_TICKER_TEXT, text).apply();
    }

    public String getCachedTickerText() {
        return mPrefs.getString(KEY_TICKER_TEXT, null);
    }

    public void cacheTakeoverMessage(String msg) {
        mPrefs.edit().putString(KEY_TAKEOVER_MESSAGE, msg).apply();
    }

    public String getCachedTakeoverMessage() {
        return mPrefs.getString(KEY_TAKEOVER_MESSAGE, null);
    }

    public void cacheLicenseStatus(String status) {
        mPrefs.edit().putString(KEY_LICENSE_STATUS, status).apply();
    }

    public String getCachedLicenseStatus() {
        return mPrefs.getString(KEY_LICENSE_STATUS, "unknown");
    }

    /**
     * Cache all values at once for efficiency.
     */
    public void cacheAll(int x, int y, String logo, String banner, String ticker, String takeover) {
        mPrefs.edit()
                .putInt(KEY_DIMENSION_X, x)
                .putInt(KEY_DIMENSION_Y, y)
                .putString(KEY_LOGO_URL, logo)
                .putString(KEY_BANNER_CONTENT, banner)
                .putString(KEY_TICKER_TEXT, ticker)
                .putString(KEY_TAKEOVER_MESSAGE, takeover)
                .apply();
    }
}
