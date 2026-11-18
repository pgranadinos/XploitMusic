package com.xploitmusic.app.data.provider.youtubemusic

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class YouTubeMusicClientTest {

    private fun mockOkHttpClient(handler: (requestUrl: String, bodyJson: String) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val bodyString = req.body?.let { body ->
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                } ?: ""
                handler(req.url.toString(), bodyString)
            }
            .build()
    }

    private fun jsonResponse(jsonString: String): Response {
        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://music.youtube.com/youtubei/v1/next?alt=json").build())
            .body(jsonString.trimIndent().toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun playlistItem(id: String, title: String, artist: String): String =
        """
        {
          "playlistPanelVideoRenderer": {
            "videoId": "$id",
            "title": {"runs": [{"text": "$title"}]},
            "longBylineText": {"runs": [{"text": "$artist"}]},
            "lengthText": {"runs": [{"text": "3:00"}]},
            "navigationEndpoint": {
              "watchEndpoint": {
                "watchEndpointMusicSupportedConfigs": {
                  "watchEndpointMusicConfig": {
                    "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                  }
                }
              }
            }
          }
        }
        """.trimIndent()

    @Test
    fun `a) watchQueue uses playlistId RDAMVM with videoId`() = kotlinx.coroutines.runBlocking {
        var capturedPlaylistId: String? = null
        var capturedVideoId: String? = null

        val client = YouTubeMusicClient(mockOkHttpClient { url, bodyJson ->
            if (url.contains("next")) {
                val jsonObj = JsonParser().parse(bodyJson).asJsonObject
                capturedPlaylistId = jsonObj.get("playlistId")?.asString
                capturedVideoId = jsonObj.get("videoId")?.asString
            }
            jsonResponse(
                """
                {
                  "contents": {
                    "singleColumnMusicWatchNextResultsRenderer": {
                      "tabbedRenderer": {
                        "watchNextTabbedResultsRenderer": {
                          "tabs": [
                            {
                              "tabRenderer": {
                                "content": {
                                  "musicQueueRenderer": {
                                    "content": {
                                      "playlistPanelRenderer": {
                                        "contents": [
                                          ${playlistItem("seed_1", "Seed", "Artist")}
                                        ]
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """
            )
        })

        client.watchQueue("song_test_123")

        assertThat(capturedPlaylistId).isEqualTo("RDAMVMsong_test_123")
        assertThat(capturedVideoId).isEqualTo("song_test_123")
    }

    @Test
    fun `b) json fixture with 12 songs returns 12 songs in exact response order`() = kotlinx.coroutines.runBlocking {
        val itemsJson = (1..12).joinToString(",\n") { i ->
            playlistItem("song_$i", "Song $i", "Artist")
        }

        val client = YouTubeMusicClient(mockOkHttpClient { _, _ ->
            jsonResponse(
                """
                {
                  "contents": {
                    "singleColumnMusicWatchNextResultsRenderer": {
                      "tabbedRenderer": {
                        "watchNextTabbedResultsRenderer": {
                          "tabs": [
                            {
                              "tabRenderer": {
                                "content": {
                                  "musicQueueRenderer": {
                                    "content": {
                                      "playlistPanelRenderer": {
                                        "contents": [
                                          ${playlistItem("seed_track", "Seed Track", "Seed Artist")},
                                          $itemsJson
                                        ]
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """
            )
        })

        val related = client.getRelated("seed_track")

        assertThat(related).hasSize(12)
        assertThat(related.map { it.id }).containsExactlyElementsIn((1..12).map { "song_$it" }).inOrder()
    }

    @Test
    fun `c) response with fewer than 10 songs returns whatever exists without error`() = kotlinx.coroutines.runBlocking {
        val itemsJson = (1..5).joinToString(",\n") { i ->
            playlistItem("song_$i", "Song $i", "Artist")
        }

        val client = YouTubeMusicClient(mockOkHttpClient { _, _ ->
            jsonResponse(
                """
                {
                  "contents": {
                    "singleColumnMusicWatchNextResultsRenderer": {
                      "tabbedRenderer": {
                        "watchNextTabbedResultsRenderer": {
                          "tabs": [
                            {
                              "tabRenderer": {
                                "content": {
                                  "musicQueueRenderer": {
                                    "content": {
                                      "playlistPanelRenderer": {
                                        "contents": [
                                          ${playlistItem("seed_track", "Seed Track", "Seed Artist")},
                                          $itemsJson
                                        ]
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """
            )
        })

        val related = client.getRelated("seed_track")

        assertThat(related).hasSize(5)
        assertThat(related.map { it.id }).containsExactly("song_1", "song_2", "song_3", "song_4", "song_5").inOrder()
    }

    @Test
    fun `getSearchSuggestions calls get_search_suggestions endpoint and returns parsed suggestions`() {
        kotlinx.coroutines.runBlocking {
            var capturedEndpoint: String? = null
            var capturedInput: String? = null

            val client = YouTubeMusicClient(mockOkHttpClient { url, bodyJson ->
                capturedEndpoint = url
                if (bodyJson.isNotBlank()) {
                    val json = JsonParser().parse(bodyJson).asJsonObject
                    capturedInput = json.get("input")?.asString
                }
                jsonResponse("""
                    {
                      "contents": [
                        {
                          "searchSuggestionsSectionRenderer": {
                            "contents": [
                              {
                                "searchSuggestionRenderer": {
                                  "suggestion": {
                                    "runs": [{"text": "cumbia"}, {"text": " sanjuanera"}]
                                  }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                """)
            })

            val suggestions = client.getSearchSuggestions("cumbia", "PE")

            assertThat(capturedEndpoint).contains("music/get_search_suggestions")
            assertThat(capturedInput).isEqualTo("cumbia")
            assertThat(suggestions).containsExactly("cumbia sanjuanera")
        }
    }

    @Test
    fun `getSearchSuggestions returns empty list on network error`() {
        kotlinx.coroutines.runBlocking {
            val client = YouTubeMusicClient(mockOkHttpClient { _, _ ->
                Response.Builder()
                    .code(500)
                    .message("Server Error")
                    .protocol(Protocol.HTTP_1_1)
                    .request(okhttp3.Request.Builder().url("https://music.youtube.com/youtubei/v1/music/get_search_suggestions?alt=json").build())
                    .body("Internal Server Error".toResponseBody("text/plain".toMediaType()))
                    .build()
            })

            val suggestions = client.getSearchSuggestions("error query")

            assertThat(suggestions).isEmpty()
        }
    }

    @Test
    fun `getSearchSuggestions rethrows CancellationException`() = kotlinx.coroutines.runBlocking {
        val client = YouTubeMusicClient(mockOkHttpClient { _, _ ->
            throw kotlinx.coroutines.CancellationException("Test cancellation")
        })

        try {
            client.getSearchSuggestions("cancelled query")
            org.junit.Assert.fail("Expected CancellationException to be thrown")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertThat(e.message).isEqualTo("Test cancellation")
        }
    }
}
