/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.itamadersomajinc.banglatype.keyboard.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.core.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.itamadersomajinc.banglatype.accessibility.AccessibilityUtils;
import com.itamadersomajinc.banglatype.keyboard.Keyboard;
import com.itamadersomajinc.banglatype.keyboard.MainKeyboardView;
import com.itamadersomajinc.banglatype.keyboard.MoreKeysPanel;
import com.itamadersomajinc.banglatype.keyboard.AudioAndHapticFeedbackManager;
import com.itamadersomajinc.banglatype.keyboard.R;
import com.itamadersomajinc.banglatype.keyboard.SuggestedWords;
import com.itamadersomajinc.banglatype.keyboard.SuggestedWords.SuggestedWordInfo;
import com.itamadersomajinc.banglatype.keyboard.common.Constants;
import com.itamadersomajinc.banglatype.keyboard.define.DebugFlags;
import com.itamadersomajinc.banglatype.keyboard.settings.Settings;
import com.itamadersomajinc.banglatype.keyboard.settings.SettingsValues;
import com.itamadersomajinc.banglatype.keyboard.suggestions.MoreSuggestionsView.MoreSuggestionsListener;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;

import java.util.ArrayList;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public void showImportantNoticeContents();
        public void pickSuggestionManually(SuggestedWordInfo word);
        public void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
    }

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final View mToolbarView;
    private final ImageButton mVoiceKey;
    private final View mImportantNoticeStrip;
    MainKeyboardView mMainKeyboardView;
    private boolean mToolbarExpanded = false;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;
        private final View mImportantNoticeStrip;
        private final View mToolbarView;

        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip, final View importantNoticeStrip,
                final View toolbarView) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            mImportantNoticeStrip = importantNoticeStrip;
            mToolbarView = toolbarView;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mImportantNoticeStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mToolbarView, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
            // Note: mToolbarView's visibility is managed by setSuggestions.
        }

        public void showImportantNoticeStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(VISIBLE);
            mToolbarView.setVisibility(INVISIBLE);
        }

        public boolean isShowingImportantNoticeStrip() {
            return mImportantNoticeStrip.getVisibility() == VISIBLE;
        }
    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        mToolbarView = findViewById(R.id.toolbar_view);
        setupToolbarButtons(mToolbarView);
        mVoiceKey = (ImageButton)findViewById(R.id.suggestions_strip_voice_key);

        final ImageButton toggleButton = (ImageButton) findViewById(R.id.toolbar_toggle_button);
        if (toggleButton != null) {
            toggleButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mToolbarExpanded = !mToolbarExpanded;
                    updateToolbarToggleState();
                }
            });
        }

        mImportantNoticeStrip = findViewById(R.id.important_notice_strip);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip,
                mImportantNoticeStrip, mToolbarView);

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey);
        keyboardAttr.recycle();
        mVoiceKey.setImageDrawable(iconVoice);
        mVoiceKey.setOnClickListener(this);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = (MainKeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        mVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : INVISIBLE);
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
        mStripVisibilityGroup.showSuggestionsStrip();

        if (mSuggestedWords.isEmpty() || mSuggestedWords.isPunctuationSuggestions()) {
            mToolbarView.setVisibility(VISIBLE);
            mSuggestionsStrip.setVisibility(GONE);
            final ImageButton toggleButton = (ImageButton) findViewById(R.id.toolbar_toggle_button);
            if (toggleButton != null) {
                toggleButton.setVisibility(GONE);
            }
        } else {
            final ImageButton toggleButton = (ImageButton) findViewById(R.id.toolbar_toggle_button);
            if (toggleButton != null) {
                toggleButton.setVisibility(VISIBLE);
            }
            mToolbarExpanded = false;
            updateToolbarToggleState();
        }
    }

    public void updateToolbarToggleState() {
        final ImageButton toggleButton = (ImageButton) findViewById(R.id.toolbar_toggle_button);
        if (mToolbarExpanded) {
            mToolbarView.setVisibility(VISIBLE);
            mSuggestionsStrip.setVisibility(GONE);
            if (toggleButton != null) {
                toggleButton.setImageResource(R.drawable.ic_chevron_left);
            }
        } else {
            mToolbarView.setVisibility(GONE);
            mSuggestionsStrip.setVisibility(VISIBLE);
            if (toggleButton != null) {
                toggleButton.setImageResource(R.drawable.ic_chevron_right);
            }
        }
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    // This method checks if we should show the important notice (checks on permanent storage if
    // it has been shown once already or not, and if in the setup wizard). If applicable, it shows
    // the notice. In all cases, it returns true if it was shown, false otherwise.
    public boolean maybeShowImportantNoticeTitle() {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        if (!ImportantNoticeUtils.shouldShowImportantNotice(getContext(), currentSettingsValues)) {
            return false;
        }
        if (getWidth() <= 0) {
            return false;
        }
        final String importantNoticeTitle = ImportantNoticeUtils.getSuggestContactsNoticeTitle(
                getContext());
        if (TextUtils.isEmpty(importantNoticeTitle)) {
            return false;
        }
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        mLayoutHelper.layoutImportantNotice(mImportantNoticeStrip, importantNoticeTitle);
        mStripVisibilityGroup.showImportantNoticeStrip();
        mImportantNoticeStrip.setOnClickListener(this);
        return true;
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        if (mStripVisibilityGroup.isShowingImportantNoticeStrip()) {
            return false;
        }
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int)me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int)me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    private View mToolbarScroll;
    private View mDotPage1, mDotPage2, mDotPage3;
    private int mCurrentToolbarPage = 0;
    private boolean mIsFloatingMode = false;
    private int mOneHandedMode = 0; // 0=full, 1=left, 2=right

    private void setupToolbarButtons(View toolbar) {
        if (toolbar == null) return;

        // Find scroll view and page indicators
        mToolbarScroll = toolbar.findViewById(R.id.toolbar_scroll);
        mDotPage1 = toolbar.findViewById(R.id.dot_page1);
        mDotPage2 = toolbar.findViewById(R.id.dot_page2);
        mDotPage3 = toolbar.findViewById(R.id.dot_page3);

        // Page 1 buttons
        int[] page1Ids = {R.id.button_clipboard, R.id.button_emoji, R.id.button_number,
                R.id.button_translate, R.id.button_theme};
        for (int id : page1Ids) {
            View v = toolbar.findViewById(id);
            if (v != null) v.setOnClickListener(this);
        }

        // Page 2 buttons (text editing)
        int[] page2Ids = {R.id.button_undo, R.id.button_redo, R.id.button_select_all,
                R.id.button_cut, R.id.button_copy, R.id.button_paste};
        for (int id : page2Ids) {
            View v = toolbar.findViewById(id);
            if (v != null) v.setOnClickListener(this);
        }

        // Page 3 buttons (layout modes)
        int[] page3Ids = {R.id.button_floating, R.id.button_one_handed_left,
                R.id.button_one_handed_right, R.id.button_full, R.id.button_layout_picker};
        for (int id : page3Ids) {
            View v = toolbar.findViewById(id);
            if (v != null) v.setOnClickListener(this);
        }

        // Setup swipe detection on toolbar scroll view
        if (mToolbarScroll instanceof HorizontalScrollView) {
            final HorizontalScrollView scrollView = (HorizontalScrollView) mToolbarScroll;
            scrollView.setOnTouchListener(new OnTouchListener() {
                private float startX;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startX = event.getX();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        float deltaX = event.getX() - startX;
                        int pageWidth = getWidth();
                        if (pageWidth > 0) {
                            if (deltaX < -50 && mCurrentToolbarPage < 2) {
                                scrollToPage(mCurrentToolbarPage + 1, pageWidth);
                            } else if (deltaX > 50 && mCurrentToolbarPage > 0) {
                                scrollToPage(mCurrentToolbarPage - 1, pageWidth);
                            } else {
                                // Snap to nearest page
                                int scrollX = scrollView.getScrollX();
                                int nearestPage = Math.round((float) scrollX / pageWidth);
                                nearestPage = Math.max(0, Math.min(2, nearestPage));
                                scrollToPage(nearestPage, pageWidth);
                            }
                        }
                    }
                    return false;
                }
            });
        }
    }

    private void scrollToPage(int page, int pageWidth) {
        mCurrentToolbarPage = page;
        if (mToolbarScroll instanceof HorizontalScrollView) {
            ((HorizontalScrollView) mToolbarScroll).smoothScrollTo(page * pageWidth, 0);
        }
        updatePageIndicator();
    }

    private void updatePageIndicator() {
        if (mDotPage1 != null) mDotPage1.setBackgroundResource(
                mCurrentToolbarPage == 0 ? R.drawable.toolbar_dot_active : R.drawable.toolbar_dot_inactive);
        if (mDotPage2 != null) mDotPage2.setBackgroundResource(
                mCurrentToolbarPage == 1 ? R.drawable.toolbar_dot_active : R.drawable.toolbar_dot_inactive);
        if (mDotPage3 != null) mDotPage3.setBackgroundResource(
                mCurrentToolbarPage == 2 ? R.drawable.toolbar_dot_active : R.drawable.toolbar_dot_inactive);
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        if (view == mImportantNoticeStrip) {
            mListener.showImportantNoticeContents();
            return;
        }
        if (view == mVoiceKey) {
            mListener.onCodeInput(Constants.CODE_SHORTCUT,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }

        final int id = view.getId();
        if (id == R.id.button_emoji) {
            mListener.onCodeInput(Constants.CODE_EMOJI,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false);
            return;
        } else if (id == R.id.button_number) {
            mListener.onCodeInput(Constants.CODE_SYMBOL_SHIFT,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false);
            return;
        } else if (id == R.id.button_clipboard) {
            handleClipboard();
            return;
        } else if (id == R.id.button_theme) {
            handleTheme();
            return;
        } else if (id == R.id.button_translate) {
            handleTranslate();
            return;
        } else if (id == R.id.button_undo) {
            handleTextEdit(Constants.CODE_UNDO);
            return;
        } else if (id == R.id.button_redo) {
            handleTextEdit(Constants.CODE_REDO);
            return;
        } else if (id == R.id.button_select_all) {
            handleTextEdit(Constants.CODE_SELECT_ALL);
            return;
        } else if (id == R.id.button_cut) {
            handleTextEdit(Constants.CODE_CUT);
            return;
        } else if (id == R.id.button_copy) {
            handleTextEdit(Constants.CODE_COPY);
            return;
        } else if (id == R.id.button_paste) {
            handleTextEdit(Constants.CODE_PASTE);
            return;
        } else if (id == R.id.button_floating) {
            toggleFloatingMode();
            return;
        } else if (id == R.id.button_one_handed_left) {
            setOneHandedMode(1);
            return;
        } else if (id == R.id.button_one_handed_right) {
            setOneHandedMode(2);
            return;
        } else if (id == R.id.button_full) {
            setOneHandedMode(0);
            return;
        } else if (id == R.id.button_layout_picker) {
            // Open layout picker via the listener
            mListener.onCodeInput(Constants.CODE_LAYOUT_PICKER,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false);
            return;
        }

        final Object tag = view.getTag();
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    private void handleClipboard() {
        // Trigger clipboard action through code input
        mListener.onCodeInput(Constants.CODE_CLIPBOARD,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    private void handleTheme() {
        mListener.onCodeInput(Constants.CODE_THEME,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    private void handleTranslate() {
        mListener.onCodeInput(Constants.CODE_TRANSLATE,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    private void handleTextEdit(int code) {
        mListener.onCodeInput(code,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    private void toggleFloatingMode() {
        mIsFloatingMode = !mIsFloatingMode;
        mOneHandedMode = 0;
        mListener.onCodeInput(mIsFloatingMode ? Constants.CODE_FLOATING_ON : Constants.CODE_FLOATING_OFF,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    private void setOneHandedMode(int mode) {
        mOneHandedMode = mode;
        mIsFloatingMode = false;
        mListener.onCodeInput(
                mode == 1 ? Constants.CODE_ONE_HANDED_LEFT :
                mode == 2 ? Constants.CODE_ONE_HANDED_RIGHT :
                Constants.CODE_FULL_MODE,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                false);
    }

    public boolean isFloatingMode() { return mIsFloatingMode; }
    public int getOneHandedMode() { return mOneHandedMode; }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
        if (oldw <= 0 && w > 0) {
            maybeShowImportantNoticeTitle();
        }
    }
}
