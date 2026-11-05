package com.xploitmusic.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackLogManagerTest {

    @Test
    fun `sanitizeMessage strips tokens and query params from HTTP and HTTPS URLs`() {
        val message = "Failed to fetch stream from https://rr1---sn-x.googlevideo.com/videoplayback?expire=12345678&sparams=ip&sig=ABC123DEF456 with error 403"
        val sanitized = PlaybackLogManager.sanitizeMessage(message)

        assertThat(sanitized).doesNotContain("expire=12345678")
        assertThat(sanitized).doesNotContain("sig=ABC123DEF456")
        assertThat(sanitized).doesNotContain("/videoplayback")
        assertThat(sanitized).contains("rr1---sn-x.googlevideo.com")
    }

    @Test
    fun `sanitizeMessage handles plain text messages without URLs untouched`() {
        val message = "Audio output device connected: Bluetooth A2DP (Buds3 Pro)"
        val sanitized = PlaybackLogManager.sanitizeMessage(message)

        assertThat(sanitized).isEqualTo(message)
    }

    @Test
    fun `resolveNetworkType returns Sin red when activeNetwork or capabilities are null`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)

        val mockNetwork: android.net.Network = io.mockk.mockk()
        val mockCaps: android.net.NetworkCapabilities = io.mockk.mockk()

        assertThat(manager.resolveNetworkType(null, null)).isEqualTo("Sin red")
        assertThat(manager.resolveNetworkType(mockCaps, null)).isEqualTo("Sin red")
        assertThat(manager.resolveNetworkType(null, mockNetwork)).isEqualTo("Sin red")
    }

    @Test
    fun `resolveNetworkType resolves cellular when wifi lost but active mobile data present`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)

        val mockNetwork: android.net.Network = io.mockk.mockk()
        val mockCaps: android.net.NetworkCapabilities = io.mockk.mockk()

        io.mockk.every { mockCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } returns false
        io.mockk.every { mockCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) } returns true

        val resolved = manager.resolveNetworkType(mockCaps, mockNetwork)
        assertThat(resolved).isEqualTo("Datos móviles")
    }

    @Test
    fun `resolveNetworkType resolves network type regardless of VALIDATED capability`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)

        val mockNetwork: android.net.Network = io.mockk.mockk()
        val mockCaps: android.net.NetworkCapabilities = io.mockk.mockk()

        io.mockk.every { mockCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } returns true
        io.mockk.every { mockCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        io.mockk.every { mockCaps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        val resolved = manager.resolveNetworkType(mockCaps, mockNetwork)
        assertThat(resolved).isEqualTo("Wi-Fi")
    }

    @Test
    fun `getBestValidatedNetwork returns null when empty`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)
        val mockCm: android.net.ConnectivityManager = io.mockk.mockk(relaxed = true)
        assertThat(manager.getBestValidatedNetwork(mockCm)).isNull()
    }

    @Test
    fun `getBestValidatedNetwork returns cellular when one validated cellular network`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)
        val mockCm: android.net.ConnectivityManager = io.mockk.mockk(relaxed = true)
        val cellNet: android.net.Network = io.mockk.mockk()
        val cellCaps: android.net.NetworkCapabilities = io.mockk.mockk(relaxed = true)

        io.mockk.every { cellCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        io.mockk.every { mockCm.getNetworkCapabilities(cellNet) } returns cellCaps

        val field = PlaybackLogManager::class.java.getDeclaredField("validatedNetworks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val validatedNets = field.get(manager) as MutableSet<android.net.Network>
        validatedNets.add(cellNet)

        assertThat(manager.getBestValidatedNetwork(mockCm)).isEqualTo(cellNet)
    }

    @Test
    fun `getBestValidatedNetwork prefers Wifi over cellular even if active is cellular`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)
        val mockCm: android.net.ConnectivityManager = io.mockk.mockk(relaxed = true)
        val wifiNet: android.net.Network = io.mockk.mockk()
        val cellNet: android.net.Network = io.mockk.mockk()
        val wifiCaps: android.net.NetworkCapabilities = io.mockk.mockk(relaxed = true)
        val cellCaps: android.net.NetworkCapabilities = io.mockk.mockk(relaxed = true)

        io.mockk.every { wifiCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } returns true
        io.mockk.every { cellCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        io.mockk.every { mockCm.getNetworkCapabilities(wifiNet) } returns wifiCaps
        io.mockk.every { mockCm.getNetworkCapabilities(cellNet) } returns cellCaps
        io.mockk.every { mockCm.activeNetwork } returns cellNet

        val field = PlaybackLogManager::class.java.getDeclaredField("validatedNetworks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val validatedNets = field.get(manager) as MutableSet<android.net.Network>
        validatedNets.add(wifiNet)
        validatedNets.add(cellNet)

        assertThat(manager.getBestValidatedNetwork(mockCm)).isEqualTo(wifiNet)
    }

    @Test
    fun `getBestValidatedNetwork falls back to cellular when Wifi lost`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)
        val mockCm: android.net.ConnectivityManager = io.mockk.mockk(relaxed = true)
        val wifiNet: android.net.Network = io.mockk.mockk()
        val cellNet: android.net.Network = io.mockk.mockk()
        val wifiCaps: android.net.NetworkCapabilities = io.mockk.mockk(relaxed = true)
        val cellCaps: android.net.NetworkCapabilities = io.mockk.mockk(relaxed = true)

        io.mockk.every { wifiCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } returns true
        io.mockk.every { cellCaps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        io.mockk.every { mockCm.getNetworkCapabilities(wifiNet) } returns wifiCaps
        io.mockk.every { mockCm.getNetworkCapabilities(cellNet) } returns cellCaps

        val field = PlaybackLogManager::class.java.getDeclaredField("validatedNetworks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val validatedNets = field.get(manager) as MutableSet<android.net.Network>
        validatedNets.add(wifiNet)
        validatedNets.add(cellNet)
        validatedNets.remove(wifiNet)

        assertThat(manager.getBestValidatedNetwork(mockCm)).isEqualTo(cellNet)
    }

    @Test
    fun `onNetworkValidated adds and onNetworkLost removes network and recalculates state`() {
        val mockContext: android.content.Context = io.mockk.mockk(relaxed = true)
        val manager = PlaybackLogManager(mockContext)
        val net: android.net.Network = io.mockk.mockk()

        val field = PlaybackLogManager::class.java.getDeclaredField("validatedNetworks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val validatedNets = field.get(manager) as MutableSet<android.net.Network>

        validatedNets.add(net)
        assertThat(validatedNets).contains(net)
        validatedNets.remove(net)
        assertThat(validatedNets).doesNotContain(net)
    }
}
