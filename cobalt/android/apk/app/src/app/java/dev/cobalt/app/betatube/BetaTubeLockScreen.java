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

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen lock overlay that blocks all user interaction until the device
 * is activated. Displays the activation code and instructions.
 */
public class BetaTubeLockScreen {

    private final Activity mActivity;
    private final String mActivationCode;
    private FrameLayout mRootView;
    private boolean mIsShowing;

    public BetaTubeLockScreen(Activity activity, String activationCode) {
        mActivity = activity;
        mActivationCode = activationCode;
        mIsShowing = false;
    }

    /**
     * Shows the lock screen overlay on top of all content.
     */
    public void show() {
        if (mIsShowing) {
            return;
        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createAndShowOverlay();
            }
        });
    }

    private void createAndShowOverlay() {
        float density = mActivity.getResources().getDisplayMetrics().density;

        // Root FrameLayout - full screen dark background
        mRootView = new FrameLayout(mActivity);
        mRootView.setBackgroundColor(Color.argb(230, 15, 15, 26));
        mRootView.setElevation(100f);
        mRootView.setTranslationZ(100f);

        // Consume all touch events to block interaction
        mRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        // Centered card container
        LinearLayout card = new LinearLayout(mActivity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        int padding = (int) (48 * density);
        card.setPadding(padding, padding, padding, padding);

        // Card background with rounded corners
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.argb(180, 30, 30, 50));
        cardBackground.setCornerRadius(16 * density);
        cardBackground.setStroke((int) (1 * density), Color.argb(60, 255, 255, 255));
        card.setBackground(cardBackground);

        // Title - "SYSTEM LOCKED"
        TextView titleView = new TextView(mActivity);
        titleView.setText("SYSTEM LOCKED");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int) (24 * density);
        card.addView(titleView, titleParams);

        // Activation code display
        TextView codeView = new TextView(mActivity);
        codeView.setText(mActivationCode);
        codeView.setTextColor(Color.parseColor("#e94560"));
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        codeParams.bottomMargin = (int) (24 * density);
        card.addView(codeView, codeParams);

        // Message
        TextView messageView = new TextView(mActivity);
        messageView.setText("System Locked. Please send this code to the Technical Director "
                + "(Rajab) for activation.");
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        card.addView(messageView, messageParams);

        // Card layout params - centered in root
        FrameLayout.LayoutParams cardLayoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        cardLayoutParams.gravity = Gravity.CENTER;
        mRootView.addView(card, cardLayoutParams);

        // Add to activity with MATCH_PARENT
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mActivity.addContentView(mRootView, rootParams);

        mIsShowing = true;
    }

    /**
     * Dismisses the lock screen with a fade-out animation.
     */
    public void dismiss() {
        if (!mIsShowing || mRootView == null) {
            return;
        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(300);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (mRootView != null && mRootView.getParent() != null) {
                            ((ViewGroup) mRootView.getParent()).removeView(mRootView);
                        }
                        mRootView = null;
                        mIsShowing = false;
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                mRootView.startAnimation(fadeOut);
            }
        });
    }

    /**
     * Returns whether the lock screen is currently showing.
     */
    public boolean isShowing() {
        return mIsShowing;
    }
}
