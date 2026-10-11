package com.xploitmusic.app.core.util

import java.util.Locale

object RegionUtils {

    const val GLOBAL_REGION = "US"
    val FALLBACK_REGIONS = listOf("US", "GB", "IN")

    /**
     * Normaliza un código de región a un formato válido (2 letras mayúsculas).
     * Si es inválido, vacío, nulo o tiene un formato incorrecto, usa "US" como fallback básico.
     */
    fun normalizeRegion(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return GLOBAL_REGION
        
        val code = countryCode.trim().uppercase(Locale.ROOT)
        if (code.length != 2) return GLOBAL_REGION
        
        // Verifica si contiene solo letras de la A a la Z
        if (!code.matches(Regex("^[A-Z]{2}\$"))) return GLOBAL_REGION
        
        return code
    }

    /**
     * Devuelve una escala de fallbacks priorizados si una región falla.
     * Ejemplo: Si la original era "PE" y falla, retornará la cadena ["US", "GB", "IN"].
     */
    fun getFallbackRegions(currentRegion: String): List<String> {
        return FALLBACK_REGIONS.filter { it != currentRegion }
    }

    /**
     * Devuelve el emoji de la bandera correspondiente a un código de país de 2 letras.
     */
    fun getRegionFlag(regionCode: String?): String {
        val normalized = normalizeRegion(regionCode)
        if (normalized.length != 2) return "🌎"
        
        val firstLetter = Character.codePointAt(normalized, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(normalized, 1) - 0x41 + 0x1F1E6
        
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
