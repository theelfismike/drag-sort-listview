/*
 * DragSortListView.
 *
 * A subclass of the Android ListView component that enables drag
 * and drop re-ordering of list items.
 *
 * Copyright 2012 Carl Bauer, Mike Kelley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobeta.android.dslv;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import java.util.ArrayList;

/**
 * ListView subclass that mediates drag and drop resorting of items.
 *
 * @author heycosmo, theelfismike
 */
public class DragSortListView extends ListView {

    private ImageView mFloatView; //The View that floats above the ListView and represents the dragged item.

    private int mFloatViewY;  //The middle (in the y-direction) of the floating View.
    private int mFloatBGColor;
    private float mFloatAlpha; //Transparency for the floating View (XML attribute).

    /**
     * While drag-sorting, the current position of the floating
     * View. If dropped, the dragged item will land in this position.
     */
    private int mFloatPos;

    private WindowManager mWindowManager; //Manages the floating View.
    private WindowManager.LayoutParams mWindowParams; //LayoutParams for the floating View.

    /**
     * The amount to scroll during the next layout pass. Used only
     * for drag-scrolling, not standard ListView scrolling.
     */
    private int mScrollY = 0;

    /**
     * The first expanded ListView position that helps represent
     * the drop slot tracking the floating View.
     */
    private int mFirstExpPos;

    /**
     * The second expanded ListView position that helps represent
     * the drop slot tracking the floating View. This can equal
     * mFirstExpPos if there is no slide shuffle occurring; otherwise
     * it is equal to mFirstExpPos + 1.
     */
    private int mSecondExpPos;

    private boolean mAnimate = false;  //Flag set if slide shuffling is enabled.

    private int mSrcPos; //The user dragged from this position.

    /**
     * Offset (in x) within the dragged item at which the user
     * picked it up (or first touched down with the digitalis).
     */
    private int mDragPointX;

    /**
     * Offset (in y) within the dragged item at which the user
     * picked it up (or first touched down with the digitalis).
     */
    private int mDragPointY;

    private int mXOffset; //The difference (in x) between screen coordinates and coordinates in this view.
    private int mYOffset; //The difference (in y) between screen coordinates and coordinates in this view.

    /**
     * A listener that receives a callback when the floating View
     * is dropped.
     */
    private DropListener mDropListener;

    /**
     * A listener that receives a callback when the floating View
     * (or more precisely the originally dragged item) is removed
     * by one of the provided gestures.
     */
    private RemoveListener mRemoveListener;

    private GestureDetector mGestureDetector; //Used to detect a remove gesture.

    private enum REMOVE_MODE {
        FLING,
        SLIDE,
        SLIDE_LEFT,
        TRASH
    }

    private REMOVE_MODE mRemoveMode = REMOVE_MODE.FLING;

    private Rect mTempRect = new Rect();
    private int[] mTempLoc = new int[2];
    private Bitmap mDragBitmap;

    /**
     * Height in pixels to which the originally dragged item
     * is collapsed during a drag-sort. Currently, this value
     * must be greater than zero.
     */
    private int mItemHeightCollapsed = 1;

    private int mFloatViewHeight; //Height of the floating View, used for providing the tracking drop slot.
    private int mFloatViewHeightHalf; //Convenience member. See above.

    /**
     * Sample Views ultimately used for calculating the height
     * of ListView items that are off-screen.
     */
    private View[] mSampleViewTypes = new View[1];

    private DragScroller mDragScroller;

    /**
     * Determines the start of the upward drag-scroll region
     * at the top of the ListView. Specified by a fraction
     * of the ListView height, thus screen resolution agnostic.
     */
    private float mDragUpScrollStartFrac = 1.0f / 3.0f;

    /**
     * Determines the start of the downward drag-scroll region
     * at the bottom of the ListView. Specified by a fraction
     * of the ListView height, thus screen resolution agnostic.
     */
    private float mDragDownScrollStartFrac = 1.0f / 3.0f;

    /**
     * The following are calculated from the above fracs.
     */
    private int mUpScrollStartY;
    private int mDownScrollStartY;
    private float mDownScrollStartYF;
    private float mUpScrollStartYF;

    /**
     * Calculated from above above and current ListView height.
     */
    private float mDragUpScrollHeight;

    /**
     * Calculated from above above and current ListView height.
     */
    private float mDragDownScrollHeight;

    /**
     * Maximum drag-scroll speed in pixels per ms. Only used with
     * default linear drag-scroll profile.
     */
    private float mMaxScrollSpeed = 0.3f;

    /**
     * Defines the scroll speed during a drag-scroll. User can
     * provide their own; this default is a simple linear profile
     * where scroll speed increases linearly as the floating View
     * nears the top/bottom of the ListView.
     */
    private DragScrollProfile mScrollProfile = new DragScrollProfile() {
        @Override
        public float getSpeed(float w, long t) {
            return mMaxScrollSpeed * w;
        }
    };

    private int mLastY;
    private int mDownY;

    /**
     * Determines when a slide shuffle animation starts. That is,
     * defines how close to the edge of the drop slot the floating
     * View must be to initiate the slide.
     */
    private float mSlideRegionFrac = 0.25f;

    /**
     * Number between 0 and 1 indicating the location of
     * an item during a slide (only used if drag-sort animations
     * are turned on). Nearly 1 means the item is
     * at the top of the slide region (nearly full blank item
     * is directly below).
     */
    private float mSlideFrac = 0.0f;

    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if(attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.DragSortListView, 0, 0);

            mItemHeightCollapsed = Math.max(1, a.getDimensionPixelSize(R.styleable.DragSortListView_collapsed_height,
                    1));

            mFloatBGColor = a.getColor(R.styleable.DragSortListView_float_background_color, 0x00000000);

            // alpha between 0 and 255, 0=transparent, 255=opaque
            mFloatAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, 1.0f);

            mSlideRegionFrac = Math.max(0.0f, Math.min(1.0f, 1.0f - a.getFloat(R.styleable
                    .DragSortListView_slide_shuffle_speed, 0.75f)));
            mAnimate = mSlideRegionFrac > 0.0f;

            mRemoveMode = REMOVE_MODE.values()[a.getInt(R.styleable.DragSortListView_remove_mode, -1)];

            float frac = a.getFloat(R.styleable.DragSortListView_drag_scroll_start, mDragUpScrollStartFrac);
            setDragScrollStart(frac);

            mMaxScrollSpeed = a.getFloat(R.styleable.DragSortListView_max_drag_scroll_speed, mMaxScrollSpeed);

            a.recycle();
        }

        mDragScroller = new DragScroller();
        setOnScrollListener(mDragScroller);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(new AdapterWrapper(null, null, adapter));
    }

    private class AdapterWrapper extends HeaderViewListAdapter {
        private ListAdapter mAdapter;

        public AdapterWrapper(ArrayList<FixedViewInfo> headerViewInfos, ArrayList<FixedViewInfo> footerViewInfos,
                              ListAdapter adapter) {
            super(headerViewInfos, footerViewInfos, adapter);
            mAdapter = adapter;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            RelativeLayout v;
            View child;

            if(convertView != null) {

                v = (RelativeLayout) convertView;
                View oldChild = v.getChildAt(0);

                child = mAdapter.getView(position, oldChild, v);
                if(child != oldChild) {
                    // shouldn't get here if user is reusing convertViews properly
                    v.removeViewAt(0);
                    v.addView(child);
                    // check that tags are equal too?
                    v.setTag(child.findViewById(R.id.drag));
                }
            } else {
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                v = new RelativeLayout(getContext());
                v.setLayoutParams(params);
                child = mAdapter.getView(position, null, v);
                v.addView(child);

                v.setTag(child.findViewById(R.id.drag));
            }

            // Set the correct item height given drag state; passed
            // View needs to be measured if measurement is required.
            adjustItem(position + getHeaderViewsCount(), v, true);

            return v;
        }
    }

    private void drawDivider(int expPosition, Canvas canvas) {

        final Drawable divider = getDivider();
        final int dividerHeight = getDividerHeight();

        if(divider != null && dividerHeight != 0) {
            final ViewGroup expItem = (ViewGroup) getChildAt(expPosition - getFirstVisiblePosition());
            if(expItem != null) {
                final int l = getPaddingLeft();
                final int r = getWidth() - getPaddingRight();
                final int t;
                final int b;

                final int childHeight = expItem.getChildAt(0).getHeight();

                if(expPosition > mSrcPos) {
                    t = expItem.getTop() + childHeight;
                    b = t + dividerHeight;
                } else {
                    b = expItem.getBottom() - childHeight;
                    t = b - dividerHeight;
                }

                divider.setBounds(l, t, r, b);
                divider.draw(canvas);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if(mFloatView != null) {
            // draw the divider over the expanded item
            if(mFirstExpPos != mSrcPos) {
                drawDivider(mFirstExpPos, canvas);
            }
            if(mSecondExpPos != mFirstExpPos && mSecondExpPos != mSrcPos) {
                drawDivider(mSecondExpPos, canvas);
            }
        }
    }

    private int measureItemAndGetHeight(View item, boolean ofChild) {

        ViewGroup.LayoutParams lp;
        if(ofChild) {
            item = ((ViewGroup) item).getChildAt(0);
            lp = item.getLayoutParams();
        } else {
            lp = item.getLayoutParams();
        }

        final int height = lp == null ? 0 : lp.height;
        if(height > 0) {
            return height;
        } else {
            int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            item.measure(spec, spec);
            return item.getMeasuredHeight();
        }
    }

    private int getItemHeight(int position) {
        return getItemHeight(position, false);
    }

    private int getItemHeight(int position, boolean ofChild) {

        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        if(position >= first && position <= last) {
            if(ofChild) {
                ViewGroup item = (ViewGroup) getChildAt(position - first);
                return item.getChildAt(0).getHeight();
            } else {
                return getChildAt(position - first).getHeight();
            }
        } else {
            final ListAdapter adapter = getAdapter();
            int type = adapter.getItemViewType(position);

            // There might be a better place for checking for the following
            final int typeCount = adapter.getViewTypeCount();
            if(typeCount != mSampleViewTypes.length) {
                mSampleViewTypes = new View[typeCount];
            }

            View v;
            if(type >= 0) {
                if(mSampleViewTypes[type] == null) {
                    v = adapter.getView(position, null, this);
                    mSampleViewTypes[type] = v;
                } else {
                    v = adapter.getView(position, mSampleViewTypes[type], this);
                }
            } else {
                // type is HEADER_OR_FOOTER or IGNORE
                v = adapter.getView(position, null, this);
            }

            return measureItemAndGetHeight(v, ofChild);
        }

    }

    /**
     * Get the shuffle edge for item at position when top of
     * item is at y-coord top
     *
     * @param position
     * @param top
     * @return Shuffle line between position-1 and position (for
     *         the given view of the list; that is, for when top of item at
     *         position has y-coord of given `top`). If
     *         floating View (treated as horizontal line) is dropped
     *         immediately above this line, it lands in position-1. If
     *         dropped immediately below this line, it lands in position.
     */
    private int getShuffleEdge(int position, int top) {

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        // shuffle edges are defined between items that can be
        // dragged; there are N-1 of them if there are N draggable
        // items.

        if(position <= numHeaders || (position >= getCount() - numFooters)) {
            return top;
        }

        int divHeight = getDividerHeight();

        int edge;

        if(mSecondExpPos <= mSrcPos) {
            // items are expanded on and/or above the source position

            if(position <= mFirstExpPos) {
                edge = top + (mFloatViewHeight - divHeight - getItemHeight(position - 1)) / 2;
            } else if(position == mSecondExpPos) {
                if(position == mSrcPos) {
                    edge = top + getItemHeight(position) - (2 * divHeight + getItemHeight(position - 1,
                            true) + mFloatViewHeight) / 2;
                } else {
                    int blankHeight = getItemHeight(position) - getItemHeight(position, true) - divHeight;
                    edge = top + (blankHeight - getItemHeight(position - 1)) / 2;
                }
            } else if(position < mSrcPos) {
                int childHeight = getItemHeight(position - 1, true);
                edge = top - (childHeight + 2 * divHeight + mFloatViewHeight) / 2;
            } else if(position == mSrcPos) {
                int childHeight = getItemHeight(position - 1, true);
                edge = top + mItemHeightCollapsed - (2 * divHeight + childHeight + mFloatViewHeight) / 2;
            } else {
                edge = top + (getItemHeight(position) - mFloatViewHeight) / 2;
            }
        } else {
            // items are expanded on and/or below the source position

            if(position <= mSrcPos) {
                edge = top + (mFloatViewHeight - getItemHeight(position - 1) - divHeight) / 2;
            } else if(position <= mFirstExpPos) {
                edge = top + (getItemHeight(position, true) + divHeight + mFloatViewHeight) / 2;
                if(position - 1 == mSrcPos) {
                    edge -= mItemHeightCollapsed + divHeight;
                }
            } else if(position == mSecondExpPos) {
                int blankAbove;
                if(position - 1 == mSrcPos) {
                    blankAbove = getItemHeight(position - 1);
                } else {
                    blankAbove = getItemHeight(position - 1) - getItemHeight(position - 1, true) - divHeight;
                }
                edge = top - blankAbove - divHeight + (getItemHeight(position, true) + divHeight + mFloatViewHeight)
                        / 2;
                //int height = getItemHeight(position);
                //int blankHeight = height - getItemHeight(position, true) - divHeight;
                //edge = top + (height - (mFloatViewHeight - blankHeight)) / 2;
            } else {
                edge = top + (getItemHeight(position) - mFloatViewHeight) / 2;
            }
        }

        return edge;
    }

    private boolean updatePositions() {

        final int first = getFirstVisiblePosition();
        int startPos = mFirstExpPos;
        View startView = getChildAt(startPos - first);

        if(startView == null) {
            startPos = first + getChildCount() / 2;
            startView = getChildAt(startPos - first);
        }
        int startTop = startView.getTop() + mScrollY;

        int edge = getShuffleEdge(startPos, startTop);
        int lastEdge = edge;

        int itemPos = startPos;
        int itemTop = startTop;
        if(mFloatViewY < edge) {
            // scanning up for float position
            while(itemPos >= 0) {
                itemPos--;

                if(itemPos == 0) {
                    edge = itemTop - getItemHeight(itemPos);
                    break;
                }

                itemTop -= getItemHeight(itemPos);
                edge = getShuffleEdge(itemPos, itemTop);

                if(mFloatViewY >= edge) {
                    break;
                }

                lastEdge = edge;
            }
        } else {
            // scanning down for float position
            final int count = getCount();
            while(itemPos < count) {
                if(itemPos == count - 1) {
                    edge = itemTop + getItemHeight(itemPos);
                    break;
                }

                itemTop += getItemHeight(itemPos);
                edge = getShuffleEdge(itemPos + 1, itemTop);

                // test for hit
                if(mFloatViewY < edge) {
                    break;
                }

                lastEdge = edge;
                itemPos++;
            }
        }

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        boolean updated = false;

        int oldFirstExpPos = mFirstExpPos;
        int oldSecondExpPos = mSecondExpPos;
        float oldSlideFrac = mSlideFrac;

        if(mAnimate) {
            int edgeToEdge = Math.abs(edge - lastEdge);

            int edgeTop, edgeBottom;
            if(mFloatViewY < edge) {
                edgeBottom = edge;
                edgeTop = lastEdge;
            } else {
                edgeTop = edge;
                edgeBottom = lastEdge;
            }

            int slideRgnHeight = (int) (0.5f * mSlideRegionFrac * edgeToEdge);
            float slideRgnHeightF = (float) slideRgnHeight;
            int slideEdgeTop = edgeTop + slideRgnHeight;
            int slideEdgeBottom = edgeBottom - slideRgnHeight;

            // Three regions
            if(mFloatViewY < slideEdgeTop) {
                mFirstExpPos = itemPos - 1;
                mSecondExpPos = itemPos;
                mSlideFrac = 0.5f * ((float) (slideEdgeTop - mFloatViewY)) / slideRgnHeightF;
            } else if(mFloatViewY < slideEdgeBottom) {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos;
            } else {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos + 1;
                mSlideFrac = 0.5f * (1.0f + ((float) (edgeBottom - mFloatViewY)) / slideRgnHeightF);
            }

        } else {
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        }

        // correct for headers and footers
        if(mFirstExpPos < numHeaders) {
            itemPos = numHeaders;
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        } else if(mSecondExpPos >= getCount() - numFooters) {
            itemPos = getCount() - numFooters - 1;
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        }

        if(mFirstExpPos != oldFirstExpPos || mSecondExpPos != oldSecondExpPos || mSlideFrac != oldSlideFrac) {
            updated = true;
        }

        if(itemPos != mFloatPos) {
            mFloatPos = itemPos;
            updated = true;
        }

        return updated;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if(mRemoveListener != null && mGestureDetector == null) {
            if(mRemoveMode == REMOVE_MODE.FLING) {
                mGestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if(mFloatView != null) {
                            if(velocityX > 1000) {
                                Rect r = mTempRect;
                                mFloatView.getDrawingRect(r);
                                if(e2.getX() > r.right * 2 / 3) {
                                    // fast fling right with release near the right edge of the screen
                                    dropFloatView(true);
                                }
                            }
                            // flinging while dragging should have no effect
                            // i.e. the gesture should not pass on to other
                            // onTouch handlers. Gobble...
                            return true;
                        }
                        return false;
                    }
                });
            }
        }

        if(mDropListener != null) {
            switch(ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();
                    mLastY = y;
                    mDownY = y;
                    int itemnum = pointToPosition(x, y); //includes headers/footers

                    final int numHeaders = getHeaderViewsCount();
                    final int numFooters = getFooterViewsCount();

                    if(itemnum == AdapterView.INVALID_POSITION || itemnum < numHeaders || itemnum >= getCount() -
                            numFooters) {
                        break;
                    }
                    ViewGroup item = (ViewGroup) getChildAt(itemnum - getFirstVisiblePosition());

                    mDragPointX = x - item.getLeft();
                    mDragPointY = y - item.getTop();
                    final int rawX = (int) ev.getRawX();
                    final int rawY = (int) ev.getRawY();
                    mXOffset = rawX - x;
                    mYOffset = rawY - y;

                    View dragBox = (View) item.getTag();
                    boolean dragHit = false;
                    if(dragBox != null) {
                        dragBox.getLocationOnScreen(mTempLoc);

                        dragHit = rawX > mTempLoc[0] && rawY > mTempLoc[1] &&
                                rawX < mTempLoc[0] + dragBox.getWidth() &&
                                rawY < mTempLoc[1] + dragBox.getHeight();
                    }

                    if(dragHit) {
                        item.setDrawingCacheEnabled(true);
                        // Create a copy of the drawing cache so that it does not get recycled
                        // by the framework when the list tries to clean up memory
                        Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
                        item.setDrawingCacheEnabled(false);

                        mFloatViewHeight = item.getHeight();
                        mFloatViewHeightHalf = mFloatViewHeight / 2;

                        mFirstExpPos = itemnum;
                        mSecondExpPos = itemnum;
                        mSrcPos = itemnum;
                        mFloatPos = itemnum;

                        startDragging(bitmap, x, y);

                        // cancel ListView fling
                        MotionEvent ev2 = MotionEvent.obtain(ev);
                        ev2.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(ev2);

                        return true;
                    }
                    removeFloatView();
                    break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param heightFraction Fraction of ListView height. Capped at
     *                       0.5f.
     */
    public void setDragScrollStart(float heightFraction) {
        setDragScrollStarts(heightFraction, heightFraction);
    }

    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param upperFrac Fraction of ListView height for up-scroll bound.
     *                  Capped at 0.5f.
     * @param lowerFrac Fraction of ListView height for down-scroll bound.
     *                  Capped at 0.5f.
     */
    public void setDragScrollStarts(float upperFrac, float lowerFrac) {
        if(lowerFrac > 0.5f) {
            mDragDownScrollStartFrac = 0.5f;
        } else {
            mDragDownScrollStartFrac = lowerFrac;
        }

        if(upperFrac > 0.5f) {
            mDragUpScrollStartFrac = 0.5f;
        } else {
            mDragUpScrollStartFrac = upperFrac;
        }

        if(getHeight() != 0) {
            updateScrollStarts();
        }
    }

    private void updateScrollStarts() {
        final int padTop = getPaddingTop();
        final int listHeight = getHeight() - padTop - getPaddingBottom();
        float heightF = (float) listHeight;

        mUpScrollStartYF = padTop + mDragUpScrollStartFrac * heightF;
        mDownScrollStartYF = padTop + (1.0f - mDragDownScrollStartFrac) * heightF;

        mUpScrollStartY = (int) mUpScrollStartYF;
        mDownScrollStartY = (int) mDownScrollStartYF;
        Log.d("mobeta", "up start=" + mUpScrollStartY);
        Log.d("mobeta", "down start=" + mDownScrollStartY);

        mDragUpScrollHeight = mUpScrollStartYF - padTop;
        mDragDownScrollHeight = padTop + listHeight - mDownScrollStartYF;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScrollStarts();
    }

    private void dropFloatView(boolean removeSrcItem) {
        mDragScroller.stopScrolling(true);

        if(removeSrcItem) {
            if(mRemoveListener != null) {
                mRemoveListener.remove(mSrcPos - getHeaderViewsCount());
            }
        } else {
            if(mDropListener != null && mFloatPos >= 0 && mFloatPos < getCount()) {
                final int numHeaders = getHeaderViewsCount();
                mDropListener.drop(mSrcPos - numHeaders, mFloatPos - numHeaders);
            }

            int oldSrcPos = mSrcPos;

            mSrcPos = -1;
            mFirstExpPos = -1;
            mSecondExpPos = -1;
            mFloatPos = -1;

            int firstPos = getFirstVisiblePosition();
            if(oldSrcPos < firstPos) {
                // collapsed src item is off screen;
                // adjust the scroll after item heights have been fixed
                View v = getChildAt(0);
                int top = 0;
                if(v != null) {
                    top = v.getTop();
                }
                //Log.d("mobeta", "top="+top+" fvh="+mFloatViewHeight);
                setSelectionFromTop(firstPos - 1, top - getPaddingTop());

            }

            removeFloatView();
        }
    }

    private void adjustAllItems() {
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        int begin = Math.max(0, getHeaderViewsCount() - first);
        int end = Math.min(last - first, getCount() - 1 - getFooterViewsCount() - first);

        for(int i = begin; i <= end; ++i) {
            View v = getChildAt(i);
            if(v != null) {
                adjustItem(first + i, v, false);
            }
        }
    }

    private void adjustItem(int position, View v, boolean needsMeasure) {

        // Adjust item height
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int oldHeight = lp.height;
        int height = oldHeight;

        int divHeight = getDividerHeight();

        boolean isSliding = mAnimate && mFirstExpPos != mSecondExpPos;

        if(position == mSrcPos) {
            if(mSrcPos == mFirstExpPos) {
                if(isSliding) {
                    height = Math.max((int) (mSlideFrac * mFloatViewHeight), mItemHeightCollapsed);
                } else {
                    height = mFloatViewHeight;
                }
            } else if(mSrcPos == mSecondExpPos) {
                // if gets here, we know an item is sliding
                height = Math.max(mFloatViewHeight - (int) (mSlideFrac * mFloatViewHeight), mItemHeightCollapsed);
            } else {
                height = mItemHeightCollapsed;
            }
        } else if(position == mFirstExpPos || position == mSecondExpPos) {

            int childHeight;
            if(needsMeasure) {
                childHeight = measureItemAndGetHeight(v, true);
            } else {
                childHeight = ((ViewGroup) v).getChildAt(0).getHeight();
            }

            if(position == mFirstExpPos) {
                if(isSliding) {
                    int blankHeight = (int) (mSlideFrac * mFloatViewHeight);
                    height = childHeight + divHeight + blankHeight;
                } else {
                    height = childHeight + divHeight + mFloatViewHeight;
                }
            } else { //position=mSecondExpPos
                // we know an item is sliding (b/c 2ndPos != 1stPos)
                int blankHeight = mFloatViewHeight - (int) (mSlideFrac * mFloatViewHeight);
                height = childHeight + divHeight + blankHeight;
            }
        } else {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        if(height != oldHeight) {
            lp.height = height;

            v.setLayoutParams(lp);
        }

        // Adjust item gravity
        if(position == mFirstExpPos || position == mSecondExpPos) {
            if(position < mSrcPos) {
                ((RelativeLayout) v).setGravity(Gravity.BOTTOM);
            } else if(position > mSrcPos) {
                ((RelativeLayout) v).setGravity(Gravity.TOP);
            }
        }

        // Finally adjust item visibility
        int oldVis = v.getVisibility();
        int vis = View.VISIBLE;

        if(position == mSrcPos && mFloatView != null) {
            vis = View.INVISIBLE;
        }

        if(vis != oldVis) {
            v.setVisibility(vis);
        }
    }

    private boolean mBlockLayoutRequests = false;

    @Override
    public void requestLayout() {
        if(!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    private void doDragScroll(int oldFirstExpPos) {
        if(mScrollY == 0) {
            return;
        }

        final int padTop = getPaddingTop();
        final int listHeight = getHeight() - padTop - getPaddingBottom();
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        int movePos;

        if(mScrollY >= 0) {
            mScrollY = Math.min(listHeight, mScrollY);
            movePos = first;
        } else {
            mScrollY = Math.max(-listHeight, mScrollY);
            movePos = last;
        }

        final View moveItem = getChildAt(movePos - first);
        int top = moveItem.getTop() + mScrollY;

        if(movePos == 0 && top > padTop) {
            top = padTop;
        }

        int moveHeightBefore = moveItem.getHeight();
        int moveHeightAfter = measureItemAndGetHeight(moveItem, false);

        if(moveHeightBefore != moveHeightAfter) {
            // some item height must change above move position
            // for adjustment to be required
            if(movePos > oldFirstExpPos || movePos > mFirstExpPos) {
                top += moveHeightBefore - moveHeightAfter;
            }
        }

        setSelectionFromTop(movePos, top - padTop);

        mScrollY = 0;
    }

    @Override
    protected void layoutChildren() {
        if(mFloatView != null) {
            int oldFirstExpPos = mFirstExpPos;

            mBlockLayoutRequests = true;

            if(updatePositions()) {
                adjustAllItems();
            }

            if(mScrollY != 0) {
                doDragScroll(oldFirstExpPos);
            }

            mBlockLayoutRequests = false;
        }

        super.layoutChildren();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if(mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        if(mDropListener != null && mFloatView != null) {
            int action = ev.getAction();

            final int x = (int) ev.getX();
            final int y = (int) ev.getY();

            switch(action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect r = mTempRect;
                    mFloatView.getDrawingRect(r);
                    if(mRemoveMode == REMOVE_MODE.SLIDE && ev.getX() > r.right * 3 / 4) {
                        dropFloatView(true);
                    } else if(mRemoveMode == REMOVE_MODE.SLIDE_LEFT && ev.getX() < r.right * 1 / 4) {
                        dropFloatView(true);
                    } else {
                        dropFloatView(false);
                    }

                    break;

                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:

                    // make src item invisible on first move away from pickup
                    // point. Reduces flicker.
                    if(mLastY == mDownY) {
                        // should we be this careful?
                        final View item = getChildAt(mSrcPos - getFirstVisiblePosition());
                        if(item != null) {
                            item.setVisibility(INVISIBLE);
                        }
                    }

                    dragView(x, y);
                    requestLayout();

                    // get the current scroll direction
                    int currentScrollDir = mDragScroller.getScrollDir();

                    if(y > mLastY && y > mDownScrollStartY && currentScrollDir != DragScroller.DOWN) {
                        // dragged down, it is below the down scroll start and it is not scrolling up

                        if(currentScrollDir != DragScroller.STOP) {
                            // moved directly from up scroll to down scroll
                            mDragScroller.stopScrolling(true);
                        }

                        // start scrolling down
                        mDragScroller.startScrolling(DragScroller.DOWN);
                    } else if(y < mLastY && y < mUpScrollStartY && currentScrollDir != DragScroller.UP) {
                        // dragged up, it is above the up scroll start and it is not scrolling up

                        if(currentScrollDir != DragScroller.STOP) {
                            // moved directly from down scroll to up scroll
                            mDragScroller.stopScrolling(true);
                        }

                        // start scrolling up
                        mDragScroller.startScrolling(DragScroller.UP);
                    } else if(y >= mUpScrollStartY && y <= mDownScrollStartY && mDragScroller.isScrolling()) {
                        // not in the upper nor in the lower drag-scroll regions but it is still scrolling

                        mDragScroller.stopScrolling(true);
                    }
                    break;
            }

            mLastY = y;
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private void startDragging(Bitmap bm, int x, int y) {
        if(getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowParams.x = x - mDragPointX + mXOffset;
        mWindowParams.y = y - mDragPointY + mYOffset;

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams
                .FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;
        mWindowParams.alpha = mFloatAlpha;

        Context context = getContext();
        ImageView v = new ImageView(context);
        v.setBackgroundColor(mFloatBGColor);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(bm);
        mDragBitmap = bm;

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(v, mWindowParams);
        mFloatView = v;
    }

    private void dragView(int x, int y) {
        if(mRemoveMode == REMOVE_MODE.SLIDE) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if(x > width / 2) {
                alpha = mFloatAlpha * (((float) (width - x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }

        if(mRemoveMode == REMOVE_MODE.SLIDE_LEFT) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if(x < width / 2) {
                alpha = mFloatAlpha * (((float) (x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }

        if(mRemoveMode == REMOVE_MODE.FLING || mRemoveMode == REMOVE_MODE.TRASH) {
            mWindowParams.x = x - mDragPointX + mXOffset;
        } else {
            mWindowParams.x = mXOffset + getPaddingLeft();
        }

        // keep floating view from going past bottom of last header view
        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();
        final int firstPos = getFirstVisiblePosition();
        final int lastPos = getLastVisiblePosition();

        int topLimit = getPaddingTop();
        if(firstPos < numHeaders) {
            topLimit = getChildAt(numHeaders - firstPos - 1).getBottom();
        }
        // bottom limit is top of first footer View or
        // bottom of last item in list
        int bottomLimit = getHeight() - getPaddingBottom();
        if(lastPos >= getCount() - numFooters - 1) {
            bottomLimit = getChildAt(getCount() - numFooters - 1 - firstPos).getBottom();
        }

        if(y - mDragPointY < topLimit) {
            mWindowParams.y = mYOffset + topLimit;
        } else if(y - mDragPointY + mFloatViewHeight > bottomLimit) {
            mWindowParams.y = mYOffset + bottomLimit - mFloatViewHeight;
        } else {
            mWindowParams.y = y - mDragPointY + mYOffset;
        }
        // get midpoint of floating view (constrained to ListView bounds)
        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
        //Log.d("mobeta", "float view taint x=" + mWindowParams.x + " y=" + mWindowParams.y);
        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
    }

    private void removeFloatView() {
        if(mFloatView != null) {
            mFloatView.setVisibility(GONE);
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mFloatView);
            mFloatView.setImageDrawable(null);
            mFloatView = null;
        }
        if(mDragBitmap != null) {
            mDragBitmap.recycle();
            mDragBitmap = null;
        }
    }

    /**
     * This better reorder your ListAdapter! DragSortListView does not do this
     * for you; doesn't make sense to. Make sure
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it is
     * called in your implementation.
     *
     * @param l
     */
    public void setDropListener(DropListener l) {
        mDropListener = l;
    }

    /**
     * Probably a no-brainer, but make sure that your remove listener
     * calls {@link BaseAdapter#notifyDataSetChanged()} or something like it.
     * When an item removal occurs, DragSortListView
     * relies on a redraw of all the items to recover invisible views
     * and such. Strictly speaking, if you remove something, your dataset
     * has changed...
     *
     * @param l
     */
    public void setRemoveListener(RemoveListener l) {
        mRemoveListener = l;
    }

    /**
     * Your implementation of this has to reorder your ListAdapter!
     * Make sure to call
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     *
     * @author heycosmo
     */
    public interface DropListener {
        public void drop(int from, int to);
    }

    /**
     * Make sure to call
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     *
     * @author heycosmo
     */
    public interface RemoveListener {
        public void remove(int which);
    }

    /**
     * Completely custom scroll speed profile. Default increases linearly
     * with position and is constant in time. Create your own by implementing
     * {@link DragSortListView.DragScrollProfile}.
     *
     * @param ssp
     */
    public void setDragScrollProfile(DragScrollProfile ssp) {
        if(ssp != null) {
            mScrollProfile = ssp;
        }
    }

    /**
     * Interface for controlling
     * scroll speed as a function of touch position and time. Use
     * {@link DragSortListView#setDragScrollProfile(DragScrollProfile)} to
     * set custom profile.
     *
     * @author heycosmo
     */
    public interface DragScrollProfile {
        /**
         * Return a scroll speed in pixels/millisecond. Always return a
         * positive number.
         *
         * @param w Normalized position in scroll region (i.e. w \in [0,1]).
         *          Small w typically means slow scrolling.
         * @param t Time (in milliseconds) since start of scroll (handy if you
         *          want scroll acceleration).
         * @return Scroll speed at position w and time t in pixels/ms.
         */
        float getSpeed(float w, long t);
    }

    private class DragScroller implements Runnable, AbsListView.OnScrollListener {

        private boolean mAbort;

        private long mPrevTime;

        private int dy;
        private float dt;
        private long tStart;
        private int scrollDir;

        public final static int STOP = -1;
        public final static int UP = 0;
        public final static int DOWN = 1;

        private float mScrollSpeed; // pixels per ms

        private boolean mScrolling = false;

        public boolean isScrolling() {
            return mScrolling;
        }

        public int getScrollDir() {
            return mScrolling ? scrollDir : STOP;
        }

        public DragScroller() {}

        public void startScrolling(int dir) {
            if(!mScrolling) {
                mAbort = false;
                mScrolling = true;
                tStart = SystemClock.uptimeMillis();
                mPrevTime = tStart;
                scrollDir = dir;
                post(this);
            }
        }

        public void stopScrolling(boolean now) {
            if(now) {
                DragSortListView.this.removeCallbacks(this);
                mScrolling = false;
            } else {
                mAbort = true;
            }
        }

        @Override
        public void run() {
            if(mAbort) {
                mScrolling = false;
                return;
            }

            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();
            final int count = getCount();
            final int padTop = getPaddingTop();
            final int listHeight = getHeight() - padTop - getPaddingBottom();

            if(scrollDir == UP) {
                View v = getChildAt(0);
                if(v == null) {
                    mScrolling = false;
                    return;
                } else {
                    if(first == 0 && v.getTop() == padTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = mScrollProfile.getSpeed((mUpScrollStartYF - mLastY) / mDragUpScrollHeight, mPrevTime);
            } else {
                View v = getChildAt(last - first);
                if(v == null) {
                    mScrolling = false;
                    return;
                } else {
                    if(last == count - 1 && v.getBottom() <= listHeight + padTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = -mScrollProfile.getSpeed((mLastY - mDownScrollStartYF) / mDragDownScrollHeight,
                        mPrevTime);
            }

            dt = SystemClock.uptimeMillis() - mPrevTime;
            // dy is change in View position of a list item; i.e. positive dy
            // means user is scrolling up (list item moves down the screen, remember
            // y=0 is at top of View).
            dy = (int) Math.round(mScrollSpeed * dt);
            mScrollY += dy;

            requestLayout();

            mPrevTime += dt;

            post(this);
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if(mScrolling && visibleItemCount != 0) {
                // Keep floating view from overlapping header and footer
                // items during scrolling
                int firstFooter = getCount() - getFooterViewsCount();
                int lastHeader = getHeaderViewsCount() - 1;

                if(firstVisibleItem <= lastHeader) {
                    int floatViewTop = mFloatViewY - mFloatViewHeightHalf;
                    int lastHeaderBottom = getChildAt(lastHeader - firstVisibleItem).getBottom();
                    if(floatViewTop < lastHeaderBottom) {
                        mWindowParams.y = mYOffset + lastHeaderBottom;
                        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                } else if(firstVisibleItem + visibleItemCount > firstFooter) {
                    int floatViewBottom = mFloatViewY + mFloatViewHeightHalf;
                    int firstFooterTop = getChildAt(firstFooter - firstVisibleItem).getTop();
                    if(floatViewBottom > firstFooterTop) {
                        mWindowParams.y = mYOffset + firstFooterTop - mFloatViewHeight;
                        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}
    }
}
