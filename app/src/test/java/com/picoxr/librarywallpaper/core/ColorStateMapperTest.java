package com.picoxr.librarywallpaper.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ColorStateMapperTest {
    private static final float FACTOR = 0.24f;

    @Test
    public void darkensRgbWhileKeepingAlpha() {
        // white opaque -> dark gray opaque
        assertEquals(0xFF3D3D3D, ColorStateMapper.darken(0xFFFFFFFF, FACTOR));
        // 40% white -> same alpha, darkened rgb
        assertEquals(0x663D3D3D, ColorStateMapper.darken(0x66FFFFFF, FACTOR));
    }

    @Test
    public void darkensWholeSelectorPreservingEntryCount() {
        int[] colors = {0xCCFFFFFF, 0x66FFFFFF, 0x4DFFFFFF};
        int[] darkened = ColorStateMapper.darkenAll(colors, FACTOR);
        assertEquals(colors.length, darkened.length);
        assertEquals(0xCC3D3D3D, darkened[0]);
        assertEquals(0x663D3D3D, darkened[1]);
        assertEquals(0x4D3D3D3D, darkened[2]);
    }

    @Test
    public void nullAndEmptyPassThrough() {
        assertEquals(null, ColorStateMapper.darkenAll(null, FACTOR));
        assertEquals(0, ColorStateMapper.darkenAll(new int[0], FACTOR).length);
    }

    @Test
    public void blackStaysBlack() {
        assertEquals(0xFF000000, ColorStateMapper.darken(0xFF000000, FACTOR));
    }
}
