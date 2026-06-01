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
import android.provider.Settings;

import java.security.MessageDigest;

/**
 * Generates a unique activation code derived from the device's hardware identifiers.
 * The code is deterministic and cached in SharedPreferences for persistence.
 */
public class HardwareIdGenerator {

    private static final String PREFS_NAME = "betatube_prefs";
    private static final String KEY_ACTIVATION_CODE = "activation_code";
    private static final String FALLBACK_CODE = "BETA00";

    /**
     * Generates or retrieves a cached activation code for this device.
     *
     * @param context Application context
     * @return A 6-character uppercase alphanumeric activation code
     */
    public static String generateActivationCode(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String cached = prefs.getString(KEY_ACTIVATION_CODE, null);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }

            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);

            if (androidId == null || androidId.isEmpty()) {
                androidId = "unknown_device";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(androidId.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String code = hexString.substring(0, 6).toUpperCase();

            prefs.edit().putString(KEY_ACTIVATION_CODE, code).apply();
            return code;
        } catch (Exception e) {
            return FALLBACK_CODE;
        }
    }
}
