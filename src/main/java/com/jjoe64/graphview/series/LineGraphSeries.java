/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64@gmail.com>.
 */
package com.jjoe64.graphview.series;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;

import com.jjoe64.graphview.GraphView;

import java.util.Iterator;

/**
 * Series to plot the data as line.
 * The line can be styled with many options.
 *
 * @author jjoe64
 */
public class LineGraphSeries<E extends DataPointInterface> extends BaseSeries<E> {
    private static final long ANIMATION_DURATION = 333;

    /**
     * wrapped styles regarding the line
     */
    private final class Styles {
        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
         */
        private int thickness = 5;

        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @see #backgroundColor
         */
        private boolean drawBackground = false;

        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @see #dataPointsRadius
         */
        private boolean drawDataPoints = false;

        /**
         * the radius for the data points.
         *
         * @see #drawDataPoints
         */
        private float dataPointsRadius = 10f;

        /**
         * the background color for the filling under
         * the line.
         *
         * @see #drawBackground
         */
        private int backgroundColor = Color.argb(100, 172, 218, 255);
    }

    /**
     * wrapped styles
     */
    private Styles mStyles;

    /**
     * internal paint object
     */
    private Paint mPaint;

    /**
     * paint for the background
     */
    private Paint mPaintBackground;

    /**
     * path for the background filling
     */
    private Path mPathBackground;

    /**
     * path to the line
     */
    private Path mPath;

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     */
    private Paint mCustomPaint;

    private boolean mAnimated;

    private long mAnimationStart;

    private AccelerateInterpolator mAnimationInterpolator;

    // TODO
    private boolean mDrawAsPath = true;

    /**
     * creates a series without data
     */
    public LineGraphSeries() {
        init();
    }

    /**
     * creates a series with data
     *
     * @param data  data points
     *              important: array has to be sorted from lowest x-value to the highest
     */
    public LineGraphSeries(E[] data) {
        super(data);
        init();
    }

    /**
     * do the initialization
     * creates internal objects
     */
    protected void init() {
        mStyles = new Styles();
        mPaint = new Paint();
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaintBackground = new Paint();

        mPathBackground = new Path();
        mPath = new Path();

        mAnimationInterpolator = new AccelerateInterpolator(2f);
    }

    /**
     * plots the series
     * draws the line and the background
     *
     * @param graphView graphview
     * @param canvas canvas
     * @param isSecondScale flag if it is the second scale
     */
    @Override
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale) {
        resetDataPoints();

        // get data
        double maxX = graphView.getViewport().getMaxX(false);
        double minX = graphView.getViewport().getMinX(false);

        double maxY;
        double minY;
        if (isSecondScale) {
            maxY = graphView.getSecondScale().getMaxY(false);
            minY = graphView.getSecondScale().getMinY(false);
        } else {
            maxY = graphView.getViewport().getMaxY(false);
            minY = graphView.getViewport().getMinY(false);
        }

        Iterator<E> values = getValues(minX, maxX);

        // draw background
        double lastEndY = 0;
        double lastEndX = 0;

        // draw data
        mPaint.setStrokeWidth(mStyles.thickness);
        mPaint.setColor(getColor());
        mPaintBackground.setColor(mStyles.backgroundColor);

        Paint paint;
        if (mCustomPaint != null) {
            paint = mCustomPaint;
        } else {
            paint = mPaint;
        }

        mPath.reset();

        if (mStyles.drawBackground) {
            mPathBackground.reset();
        }

        double diffY = maxY - minY;
        double diffX = maxX - minX;

        float graphHeight = graphView.getGraphContentHeight();
        float graphWidth = graphView.getGraphContentWidth();
        float graphLeft = graphView.getGraphContentLeft();
        float graphTop = graphView.getGraphContentTop();

        lastEndY = 0;
        lastEndX = 0;
        double lastUsedEndX = 0;
        float firstX = 0;
        float lastRenderedX = 0;
        int i=0;
        while (values.hasNext()) {
            E value = values.next();

            double valY = value.getY() - minY;
            double ratY = valY / diffY;
            double y = graphHeight * ratY;

            double valX = value.getX() - minX;
            double ratX = valX / diffX;
            double x = graphWidth * ratX;

            double orgX = x;
            double orgY = y;

            if (i > 0) {
                // overdraw
                boolean isOverdraw = false;
                boolean isOverdrawEndPoint = false;
                boolean skipDraw = false;

                if (x > graphWidth) { // end right
                    double b = ((graphWidth - lastEndX) * (y - lastEndY)/(x - lastEndX));
                    y = lastEndY+b;
                    x = graphWidth;
                    isOverdraw = isOverdrawEndPoint = true;
                }
                if (y < 0) { // end bottom
                    // skip when previous and this point is out of bound
                    if (lastEndY < 0) {skipDraw=true;}
                    double b = ((0 - lastEndY) * (x - lastEndX)/(y - lastEndY));
                    x = lastEndX+b;
                    y = 0;
                    isOverdraw = isOverdrawEndPoint = true;
                }
                if (y > graphHeight) { // end top
                    // skip when previous and this point is out of bound
                    if (lastEndY > graphHeight) {skipDraw=true;}
                    double b = ((graphHeight - lastEndY) * (x - lastEndX)/(y - lastEndY));
                    x = lastEndX+b;
                    y = graphHeight;
                    isOverdraw = isOverdrawEndPoint = true;
                }
                if (lastEndY < 0) { // start bottom
                    double b = ((0 - y) * (x - lastEndX)/(lastEndY - y));
                    lastEndX = x-b;
                    lastEndY = 0;
                    isOverdraw = true;
                }
                if (lastEndX < 0) { // start left
                    double b = ((0 - x) * (y - lastEndY)/(lastEndX - x));
                    lastEndY = y-b;
                    lastEndX = 0;
                    isOverdraw = true;
                }
                if (lastEndY > graphHeight) { // start top
                    double b = ((graphHeight - y) * (x - lastEndX)/(lastEndY - y));
                    lastEndX = x-b;
                    lastEndY = graphHeight;
                    isOverdraw = true;
                }

                float startX = (float) lastEndX + (graphLeft + 1);
                float startY = (float) (graphTop - lastEndY) + graphHeight;
                float endX = (float) x + (graphLeft + 1);
                float endY = (float) (graphTop - y) + graphHeight;
                float startXAnimated = startX;
                float endXAnimated = endX;

                if (endX < startX) {
                    // dont draw from right to left
                    skipDraw = true;
                }

                // NaN can happen when previous and current value is out of y bounds
                if (!skipDraw && !Float.isNaN(startY) && !Float.isNaN(endY)) {
                    // animation
                    if (mAnimated) {
                        long currentTime = System.currentTimeMillis();
                        if (mAnimationStart == 0) {
                            mAnimationStart = currentTime;
                        }
                        float timeFactor = (float) (currentTime-mAnimationStart) / ANIMATION_DURATION;
                        float factor = mAnimationInterpolator.getInterpolation(timeFactor);
                        if (timeFactor <= 1.0) {
                            startXAnimated = (startX-graphLeft) * factor + graphLeft;
                            endXAnimated = (endX-graphLeft) * factor + graphLeft;
                            ViewCompat.postInvalidateOnAnimation(graphView);
                        }
                    }

                    // draw data point
                    if (!isOverdrawEndPoint) {
                        if (mStyles.drawDataPoints) {
                            // draw first datapoint
                            mPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(endXAnimated, endY, mStyles.dataPointsRadius, paint);
                            mPaint.setStyle(Paint.Style.STROKE);
                        }
                        registerDataPoint(endX, endY, value);
                    }

                    if (mDrawAsPath) {
                        mPath.moveTo(startXAnimated, startY);
                    }

                    // performance opt.
                    if (Math.abs(endX-lastRenderedX) > .3f) {
                        if (mDrawAsPath) {
                            mPath.lineTo(endXAnimated, endY);
                        } else {
                            renderLine(canvas, new float[] {startXAnimated, startY, endXAnimated, endY});
                        }
                        lastRenderedX = endX;
                    }
                }

                if (mStyles.drawBackground) {
                    if (i>=1) {
                        firstX = startX;
                        mPathBackground.moveTo(startXAnimated, startY);
                    }
                    mPathBackground.lineTo(endXAnimated, endY);
                }
                lastUsedEndX = endX;
            } else if (mStyles.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                float first_X = (float) x + (graphLeft + 1);
                float first_Y = (float) (graphTop - y) + graphHeight;

                if (first_X >= graphLeft && first_Y <= (graphTop+graphHeight)) {
                    mPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(first_X, first_Y, mStyles.dataPointsRadius, mPaint);
                    mPaint.setStyle(Paint.Style.STROKE);
                }
            }
            lastEndY = orgY;
            lastEndX = orgX;
            i++;
        }

        if (mDrawAsPath) {
            // draw at the end
            canvas.drawPath(mPath, paint);
        }

        if (mStyles.drawBackground) {
            // end / close path
            mPathBackground.lineTo((float) lastUsedEndX, graphHeight + graphTop);
            mPathBackground.lineTo(firstX, graphHeight + graphTop);
            mPathBackground.close();
            canvas.drawPath(mPathBackground, mPaintBackground);
        }

    }

    private void renderLine(Canvas canvas, float[] pts) {
        canvas.drawLines(pts, mPaint);
    }

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
     *
     * @return the thickness of the line
     */
    public int getThickness() {
        return mStyles.thickness;
    }

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
     *
     * @param thickness thickness of the line
     */
    public void setThickness(int thickness) {
        mStyles.thickness = thickness;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @return whether the background will be drawn
     * @see #getBackgroundColor()
     */
    public boolean isDrawBackground() {
        return mStyles.drawBackground;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @param drawBackground whether the background will be drawn
     * @see #setBackgroundColor(int)
     */
    public void setDrawBackground(boolean drawBackground) {
        mStyles.drawBackground = drawBackground;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @return flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public boolean isDrawDataPoints() {
        return mStyles.drawDataPoints;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @param drawDataPoints flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public void setDrawDataPoints(boolean drawDataPoints) {
        mStyles.drawDataPoints = drawDataPoints;
    }

    /**
     * @return the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public float getDataPointsRadius() {
        return mStyles.dataPointsRadius;
    }

    /**
     * @param dataPointsRadius the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public void setDataPointsRadius(float dataPointsRadius) {
        mStyles.dataPointsRadius = dataPointsRadius;
    }

    /**
     * @return  the background color for the filling under
     *          the line.
     * @see #setDrawBackground(boolean)
     */
    public int getBackgroundColor() {
        return mStyles.backgroundColor;
    }

    /**
     * @param backgroundColor  the background color for the filling under
     *                          the line.
     * @see #setDrawBackground(boolean)
     */
    public void setBackgroundColor(int backgroundColor) {
        mStyles.backgroundColor = backgroundColor;
    }

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     *
     * @param customPaint the custom paint to be used for rendering the line
     */
    public void setCustomPaint(Paint customPaint) {
        this.mCustomPaint = customPaint;
    }

    public void setAnimated(boolean animated) {
        this.mAnimated = animated;
    }

    public boolean isDrawAsPath() {
        return mDrawAsPath;
    }

    public void setDrawAsPath(boolean mDrawAsPath) {
        this.mDrawAsPath = mDrawAsPath;
    }
}
