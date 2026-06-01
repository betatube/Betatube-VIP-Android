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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Manages advertisement overlay views on top of CobaltActivity content.
 * Handles logo display, fixed banners, scrolling tickers, and takeover messages.
 * Optimized for H313 hardware with proper memory management.
 */
public class BetaTubeOverlayManager {

    private final Activity mActivity;
    private FrameLayout mOverlayContainer;
    private WeakReference<ImageView> mLogoViewRef;
    private LinearLayout mBannerContainer;
    private TextView mTickerView;
    private FrameLayout mTakeoverView;
    private ObjectAnimator mTickerAnimator;
    private Bitmap mCurrentLogoBitmap;
    private final Handler mMainHandler;

    public BetaTubeOverlayManager(Activity activity) {
        mActivity = activity;
        mMainHandler = new Handler(Looper.getMainLooper());
        createOverlayContainer();
    }

    private void createOverlayContainer() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlayContainer = new FrameLayout(mActivity);
                mOverlayContainer.setClickable(false);
                mOverlayContainer.setFocusable(false);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                mActivity.addContentView(mOverlayContainer, params);
            }
        });
    }

    /**
     * Updates the logo image displayed in the top-right corner.
     * Loads the image from URL in a background thread.
     */
    public void updateLogo(final String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (!imageUrl.startsWith("https://")) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                InputStream inputStream = null;
                try {
                    URL url = new URL(imageUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setDoInput(true);
                    connection.connect();

                    inputStream = connection.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setLogoBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception e) {
                    // Silently fail - logo is non-critical
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (Exception ignored) {
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private void setLogoBitmap(Bitmap bitmap) {
        if (mOverlayContainer == null) {
            return;
        }

        // Recycle old bitmap
        if (mCurrentLogoBitmap != null && !mCurrentLogoBitmap.isRecycled()) {
            mCurrentLogoBitmap.recycle();
        }
        mCurrentLogoBitmap = bitmap;

        ImageView logoView = (mLogoViewRef != null) ? mLogoViewRef.get() : null;

        if (logoView == null) {
            logoView = new ImageView(mActivity);
            int size = dpToPx(64);
            int margin = dpToPx(16);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
            params.gravity = Gravity.TOP | Gravity.END;
            params.topMargin = margin;
            params.rightMargin = margin;
            mOverlayContainer.addView(logoView, params);
            mLogoViewRef = new WeakReference<ImageView>(logoView);
        }

        logoView.setImageBitmap(bitmap);
    }

    /**
     * Updates the fixed banner with glassmorphism styling.
     */
    public void updateFixedBanner(final String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mOverlayContainer == null) {
                    return;
                }

                if (mBannerContainer == null) {
                    mBannerContainer = new LinearLayout(mActivity);
                    mBannerContainer.setOrientation(LinearLayout.VERTICAL);
                    mBannerContainer.setGravity(Gravity.CENTER);
                    int padding = dpToPx(12);
                    mBannerContainer.setPadding(padding, padding, padding, padding);

                    // Glassmorphism background
                    GradientDrawable background = new GradientDrawable();
                    background.setColor(Color.argb(120, 20, 20, 40));
                    background.setCornerRadius(dpToPx(8));
                    background.setStroke(dpToPx(1), Color.argb(40, 255, 255, 255));
                    mBannerContainer.setBackground(background);

                    int margin = dpToPx(16);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    params.topMargin = margin;
                    params.leftMargin = margin;
                    mOverlayContainer.addView(mBannerContainer, params);
                }

                // Clear existing content and add new text
                mBannerContainer.removeAllViews();
                TextView textView = new TextView(mActivity);
                textView.setText(content);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                mBannerContainer.addView(textView);
            }
        });
    }

    /**
     * Creates or updates the scrolling ticker with hardware-accelerated animation.
     * Uses ObjectAnimator on TRANSLATION_X for smooth scrolling.
     */
    public void updateTicker(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mOverlayContainer == null) {
                    return;
                }

                // Cancel existing animation
                if (mTickerAnimator != null) {
                    mTickerAnimator.cancel();
                    mTickerAnimator = null;
                }

                if (mTickerView == null) {
                    mTickerView = new TextView(mActivity);
                    mTickerView.setSingleLine(true);
                    mTickerView.setTextColor(Color.WHITE);
                    mTickerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    mTickerView.setTypeface(Typeface.DEFAULT_BOLD);
                    mTickerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    // Glassmorphism background for ticker area
                    GradientDrawable tickerBg = new GradientDrawable();
                    tickerBg.setColor(Color.argb(100, 10, 10, 30));
                    mTickerView.setBackground(tickerBg);
                    int padding = dpToPx(8);
                    mTickerView.setPadding(padding, padding, padding, padding);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    params.gravity = Gravity.BOTTOM;
                    params.bottomMargin = dpToPx(16);
                    mOverlayContainer.addView(mTickerView, params);
                }

                mTickerView.setText(text);

                // Wait for layout to measure text width
                mTickerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mTickerView == null || mOverlayContainer == null) {
                            return;
                        }
                        int screenWidth = mOverlayContainer.getWidth();
                        int textWidth = mTickerView.getWidth();
                        if (screenWidth == 0) {
                            screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
                        }

                        long duration = text.length() * 80L;
                        if (duration < 5000) {
                            duration = 5000;
                        }

                        mTickerAnimator = ObjectAnimator.ofFloat(
                                mTickerView, View.TRANSLATION_X,
                                (float) screenWidth, (float) -textWidth);
                        mTickerAnimator.setDuration(duration);
                        mTickerAnimator.setInterpolator(new LinearInterpolator());
                        mTickerAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                        mTickerAnimator.setRepeatMode(ObjectAnimator.RESTART);
                        mTickerAnimator.start();
                    }
                });
            }
        });
    }

    /**
     * Shows a full-screen takeover message that blocks all interaction.
     */
    public void showTakeover(final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mOverlayContainer == null) {
                    return;
                }

                // Remove existing takeover if present
                dismissTakeoverInternal();

                mTakeoverView = new FrameLayout(mActivity);
                mTakeoverView.setBackgroundColor(Color.argb(210, 10, 10, 20));
                mTakeoverView.setClickable(true);
                mTakeoverView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });

                TextView textView = new TextView(mActivity);
                textView.setText(message);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                textView.setTypeface(Typeface.DEFAULT_BOLD);
                textView.setGravity(Gravity.CENTER);
                int padding = dpToPx(32);
                textView.setPadding(padding, padding, padding, padding);

                FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                textParams.gravity = Gravity.CENTER;
                mTakeoverView.addView(textView, textParams);

                FrameLayout.LayoutParams takeoverParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                mOverlayContainer.addView(mTakeoverView, takeoverParams);
            }
        });
    }

    /**
     * Dismisses the takeover overlay.
     */
    public void dismissTakeover() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                dismissTakeoverInternal();
            }
        });
    }

    private void dismissTakeoverInternal() {
        if (mTakeoverView != null && mTakeoverView.getParent() != null) {
            ((ViewGroup) mTakeoverView.getParent()).removeView(mTakeoverView);
        }
        mTakeoverView = null;
    }

    /**
     * Cleans up all resources. Critical for H313 memory management on 24/7 devices.
     */
    public void destroy() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                // Cancel ticker animation
                if (mTickerAnimator != null) {
                    mTickerAnimator.cancel();
                    mTickerAnimator = null;
                }

                // Recycle bitmap
                if (mCurrentLogoBitmap != null && !mCurrentLogoBitmap.isRecycled()) {
                    mCurrentLogoBitmap.recycle();
                }
                mCurrentLogoBitmap = null;

                // Clear view references
                mLogoViewRef = null;
                mTickerView = null;
                mBannerContainer = null;

                // Dismiss takeover
                dismissTakeoverInternal();

                // Remove overlay container from activity
                if (mOverlayContainer != null && mOverlayContainer.getParent() != null) {
                    ((ViewGroup) mOverlayContainer.getParent()).removeView(mOverlayContainer);
                }
                mOverlayContainer = null;
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * mActivity.getResources().getDisplayMetrics().density);
    }
}
