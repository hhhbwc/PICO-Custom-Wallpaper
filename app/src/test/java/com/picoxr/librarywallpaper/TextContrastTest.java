package com.picoxr.librarywallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TextContrastTest {
    @Test
    public void detectsLightLuminance() {
        assertTrue(TextContrast.isLightLuminance(255));
        assertTrue(TextContrast.isLightLuminance(155));
    }

    @Test
    public void detectsDarkLuminance() {
        assertFalse(TextContrast.isLightLuminance(154));
        assertFalse(TextContrast.isLightLuminance(30));
    }
}
