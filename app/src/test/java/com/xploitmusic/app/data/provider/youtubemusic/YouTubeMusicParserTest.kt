package com.xploitmusic.app.data.provider.youtubemusic

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import org.junit.Test
import com.xploitmusic.app.domain.model.LatamCountry

class YouTubeMusicParserTest {
    private val parser = YouTubeMusicParser()

    @Test
    fun `search keeps official audio and rejects user uploads`() {
        val response = json(
            """
            {
              "contents": [
                ${responsiveItem("official-id", "Wonderwall", "Oasis", "MUSIC_VIDEO_TYPE_ATV")},
                ${responsiveItem("ugc-id", "Wonderwall mix", "Random user", "MUSIC_VIDEO_TYPE_UGC")}
              ]
            }
            """
        )

        val songs = parser.parseSearchSongs(response)

        assertThat(songs).hasSize(1)
        assertThat(songs.single().id).isEqualTo("official-id")
        assertThat(songs.single().artist).isEqualTo("Oasis")
        assertThat(songs.single().durationSeconds).isEqualTo(259L)
        assertThat(songs.single().thumbnailUrl).isEqualTo("https://img/large")
    }

    @Test
    fun `search deduplicates equivalent official recordings`() {
        val response = json(
            """
            {
              "contents": [
                ${responsiveItem("audio-id", "Wonderwall (Official Audio)", "Oasis", "MUSIC_VIDEO_TYPE_ATV")},
                ${responsiveItem("duplicate-id", "Wonderwall", "Oasis", "MUSIC_VIDEO_TYPE_ATV")}
              ]
            }
            """
        )

        val songs = parser.parseSearchSongs(response)

        assertThat(songs).hasSize(1)
        assertThat(songs.single().id).isEqualTo("audio-id")
    }

    @Test
    fun `watch queue only returns playable official audio`() {
        val response = json(
            """
            {
              "queue": [
                ${playlistItem("seed", "Seed", "Artist", "MUSIC_VIDEO_TYPE_ATV")},
                ${playlistItem("next", "Next", "Artist", "MUSIC_VIDEO_TYPE_ATV")},
                ${playlistItem("video", "Video", "Uploader", "MUSIC_VIDEO_TYPE_OMV")}
              ]
            }
            """
        )

        val songs = parser.parseWatchQueue(response)

        assertThat(songs.map { it.id }).containsExactly("seed", "next").inOrder()
    }

    @Test
    fun `chart selector prefers daily playlist`() {
        val response = json(
            """
            {
              "items": [
                {"musicTwoRowItemRenderer": {
                  "title": {"runs": [{"text": "Top semanal"}]},
                  "navigationEndpoint": {"browseEndpoint": {"browseId": "VLweekly"}}
                }},
                {"musicTwoRowItemRenderer": {
                  "title": {"runs": [{"text": "Top diario Perú"}]},
                  "navigationEndpoint": {"browseEndpoint": {"browseId": "VLdaily"}}
                }}
              ]
            }
            """
        )

        assertThat(parser.findChartPlaylistBrowseId(response)).isEqualTo("VLdaily")
    }

    @Test
    fun `country selector uses requested daily video chart not songs shorts or global`() {
        val response = json("""{"items": [
            ${chartCard("Top diario de vídeos musicales en todo el mundo", "VLvideos")},
            ${chartCard("Top semanal de canciones en México", "VLsongs")},
            ${chartCard("Top diario de vídeos musicales en Shorts en México", "VLshorts")},
            ${chartCard("Top diario de vídeos musicales en México", "VLmexico")}
        ]}""")
        assertThat(parser.findCountryChartPlaylist(response, LatamCountry.MX)?.browseId).isEqualTo("VLmexico")
    }

    @Test
    fun `country selector handles Brazil and does not mix weekly and daily rankings`() {
        val response = json("""{"items": [
            ${chartCard("Top semanal de vídeos musicales en Brasil", "VLweekly")},
            ${chartCard("Top diario de vídeos musicales en Brasil", "VLdaily")}
        ]}""")
        assertThat(parser.findCountryChartPlaylist(response, LatamCountry.BR)?.browseId).isEqualTo("VLdaily")
    }

    @Test
    fun `country selector rejects other countries and does not fall back to global`() {
        val response = json("""{"items": [
            ${chartCard("Mis canciones favoritas", "VLmix")},
            ${chartCard("Top diario de vídeos musicales en Perú", "VLperu")},
            ${chartCard("Top diario de canciones en Shorts en todo el mundo", "VLshorts")}
        ]}""")
        assertThat(parser.findCountryChartPlaylist(response, LatamCountry.CO)).isNull()
    }

    @Test
    fun `ranked entries preserve source ranks after skipping unavailable and user uploads`() {
        val first = rankedItem("first", 1)
        val unavailable = rankedItem("unavailable", 2).apply {
            getAsJsonObject("musicResponsiveListItemRenderer").addProperty(
                "musicItemRendererDisplayPolicy", "MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT"
            )
        }
        val third = rankedItem("third", 3)
        val userUpload = rankedItem("ugc", 4, "MUSIC_VIDEO_TYPE_UGC")
        val response = json("""{"musicPlaylistShelfRenderer": {
            "contents": [$first, $unavailable, $third, $userUpload]
        }}""")
        val entries = parser.parseRankedChartEntries(response)
        assertThat(entries.map { it.rank }).containsExactly(1, 3).inOrder()
        assertThat(entries.map { it.song.id }).containsExactly("first", "third").inOrder()
    }

    @Test
    fun `ranked entries ignore unranked songs and recommendation shelves`() {
        val response = json("""{
            "musicPlaylistShelfRenderer": {"contents": [
                ${rankedItem("chart", 7)},
                ${responsiveItem("no-rank", "Song", "Artist", "MUSIC_VIDEO_TYPE_ATV")},
                ${rankedItem("chart", 7)}
            ]},
            "musicShelfRenderer": {"contents": [${rankedItem("recommendation", 1)}]}
        }""")
        val entries = parser.parseRankedChartEntries(response)
        assertThat(entries).hasSize(1)
        assertThat(entries.single().rank).isEqualTo(7)
        assertThat(entries.single().song.id).isEqualTo("chart")
    }

    private fun chartCard(title: String, browseId: String) = """
        {"musicTwoRowItemRenderer": {
            "title": {"runs": [{"text": "$title"}]},
            "navigationEndpoint": {"browseEndpoint": {"browseId": "$browseId"}}
        }}
    """

    private fun rankedItem(id: String, rank: Int, type: String = "MUSIC_VIDEO_TYPE_OMV") =
        json(responsiveItem(id, "Song $id", "Artist", type)).apply {
            getAsJsonObject("musicResponsiveListItemRenderer").add("customIndexColumn", json("""
                {"musicCustomIndexColumnRenderer": {"text": {"runs": [{"text": "$rank"}]}}}
            """))
        }

    private fun responsiveItem(
        id: String,
        title: String,
        artist: String,
        videoType: String,
    ): String =
        """
        {"musicResponsiveListItemRenderer": {
          "flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
              {"text": "$title", "navigationEndpoint": {"watchEndpoint": {"videoId": "$id"}}}
            ]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
              {"text": "$artist", "navigationEndpoint": {"browseEndpoint": {
                "browseId": "UCartist",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                }}
              }}},
              {"text": " • "}, {"text": "4:19"}
            ]}}}
          ],
          "fixedColumns": [{"musicResponsiveListItemFixedColumnRenderer": {
            "text": {"runs": [{"text": "4:19"}]}
          }}],
          "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
            {"url": "https://img/small", "width": 60, "height": 60},
            {"url": "https://img/large", "width": 544, "height": 544}
          ]}}},
          "overlay": {"musicItemThumbnailOverlayRenderer": {"content": {
            "musicPlayButtonRenderer": {"playNavigationEndpoint": {"watchEndpoint": {
              "videoId": "$id",
              "watchEndpointMusicSupportedConfigs": {"watchEndpointMusicConfig": {
                "musicVideoType": "$videoType"
              }}
            }}}
          }}}
        }}
        """.trimIndent()

    private fun playlistItem(
        id: String,
        title: String,
        artist: String,
        videoType: String,
    ): String =
        """
        {"playlistPanelVideoRenderer": {
          "videoId": "$id",
          "title": {"runs": [{"text": "$title"}]},
          "longBylineText": {"runs": [{"text": "$artist", "navigationEndpoint": {
            "browseEndpoint": {"browseId": "UCartist", "browseEndpointContextSupportedConfigs": {
              "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}
            }}
          }}]},
          "lengthText": {"runs": [{"text": "3:20"}]},
          "thumbnail": {"thumbnails": [{"url": "https://img/track", "width": 120, "height": 120}]},
          "navigationEndpoint": {"watchEndpoint": {
            "videoId": "$id", "watchEndpointMusicSupportedConfigs": {"watchEndpointMusicConfig": {
              "musicVideoType": "$videoType"
            }}
          }}
        }}
        """.trimIndent()

    private fun json(value: String) = JsonParser().parse(value).asJsonObject
}
