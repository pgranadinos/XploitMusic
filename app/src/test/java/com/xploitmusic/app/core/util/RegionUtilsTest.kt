package com.xploitmusic.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionUtilsTest {

    @Test
    fun `normalizeRegion returns GLOBAL_REGION when input is null or blank`() {
        assertEquals("US", RegionUtils.normalizeRegion(null))
        assertEquals("US", RegionUtils.normalizeRegion(""))
        assertEquals("US", RegionUtils.normalizeRegion("   "))
    }

    @Test
    fun `normalizeRegion returns GLOBAL_REGION when input format is invalid`() {
        assertEquals("US", RegionUtils.normalizeRegion("P"))     // Demasiado corto
        assertEquals("US", RegionUtils.normalizeRegion("PER"))   // Demasiado largo
        assertEquals("US", RegionUtils.normalizeRegion("P1"))    // Contiene números
    }

    @Test
    fun `normalizeRegion returns uppercase 2-letter code when input is valid`() {
        assertEquals("PE", RegionUtils.normalizeRegion("pe"))
        assertEquals("PE", RegionUtils.normalizeRegion("PE"))
        assertEquals("MX", RegionUtils.normalizeRegion(" mx "))
    }

    @Test
    fun `getFallbackRegions excludes current region`() {
        val fallbacksFromUS = RegionUtils.getFallbackRegions("US")
        assertEquals(listOf("GB", "IN"), fallbacksFromUS)
        
        val fallbacksFromPE = RegionUtils.getFallbackRegions("PE")
        assertEquals(listOf("US", "GB", "IN"), fallbacksFromPE)
    }

    @Test
    fun `getRegionFlag returns correct flag or default`() {
        // 'P' + 'E' (Para Perú)
        assertEquals("🇵🇪", RegionUtils.getRegionFlag("PE"))
        
        // Inválido (retorna fallback US flag)
        assertEquals("🇺🇸", RegionUtils.getRegionFlag("12"))
    }
}
