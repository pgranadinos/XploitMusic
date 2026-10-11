package com.xploitmusic.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotifyPkceTest {

    @Test
    fun `generates RFC 7636 S256 challenge`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val challenge = SpotifyRepositoryImpl.generateCodeChallenge(verifier)

        assertThat(challenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test
    fun `generated verifier has PKCE compliant length`() {
        val verifier = SpotifyRepositoryImpl.generateCodeVerifier()

        assertThat(verifier.length).isAtLeast(43)
        assertThat(verifier.length).isAtMost(128)
    }
}
