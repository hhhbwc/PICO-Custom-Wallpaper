package com.picoxr.librarywallpaper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CornerRadiusTest {
    private static final float DELTA = 0.001f;

    @Test
    public void leftRoundedMatchesPicoNavigationRail() {
        CornerRadius corner = CornerRadius.leftRounded(28f);
        assertEquals(28f, corner.topLeft, DELTA);
        assertEquals(0f, corner.topRight, DELTA);
        assertEquals(0f, corner.bottomRight, DELTA);
        assertEquals(28f, corner.bottomLeft, DELTA);
        assertFalse(corner.isSquare());
    }

    @Test
    public void rightRoundedMatchesPicoContentPane() {
        CornerRadius corner = CornerRadius.rightRounded(28f);
        assertEquals(0f, corner.topLeft, DELTA);
        assertEquals(28f, corner.topRight, DELTA);
        assertEquals(28f, corner.bottomRight, DELTA);
        assertEquals(0f, corner.bottomLeft, DELTA);
    }

    @Test
    public void gradientDrawableRadiiOrderRoundTrips() {
        // GradientDrawable radii order: TL, TR, BR, BL (each corner as pair)
        float[] radii = {28, 28, 0, 0, 0, 0, 28, 28};
        CornerRadius corner = CornerRadius.fromRadii(radii);
        assertEquals(CornerRadius.leftRounded(28f), corner);
    }

    @Test
    public void malformedRadiiFallBackToSquare() {
        assertTrue(CornerRadius.fromRadii(null).isSquare());
        assertTrue(CornerRadius.fromRadii(new float[3]).isSquare());
    }

    @Test
    public void negativeRadiiAreClamped() {
        assertEquals(0f, new CornerRadius(-5f, -5f, -5f, -5f).topLeft, DELTA);
    }
}
