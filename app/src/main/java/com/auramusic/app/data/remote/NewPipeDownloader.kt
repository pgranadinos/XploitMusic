package com.auramusic.app.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    companion object {
        private const val TAG = "AuraDebug-Downloader"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:102.0) Gecko/20100101 Firefox/102.0"
    }

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val okHttpRequestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .apply {
                headers.forEach { (key, values) ->
                    values.forEach { value ->
                        addHeader(key, value)
                    }
                }
            }

        when (method) {
            "POST" -> {
                val body = dataToSend?.toRequestBody() ?: byteArrayOf().toRequestBody()
                okHttpRequestBuilder.post(body)
            }
            "HEAD" -> okHttpRequestBuilder.head()
            "PUT" -> {
                val body = dataToSend?.toRequestBody() ?: byteArrayOf().toRequestBody()
                okHttpRequestBuilder.put(body)
            }
            else -> okHttpRequestBuilder.get()
        }

        val okHttpResponse = client.newCall(okHttpRequestBuilder.build()).execute()
        val code = okHttpResponse.code

        if (!okHttpResponse.isSuccessful) {
            Log.w(TAG, "HTTP $code for $method $url")
            okHttpResponse.close()
            throw java.io.IOException("HTTP Error: $code for $url")
        }

        val body = okHttpResponse.body?.string()
        val responseHeaders = okHttpResponse.headers.toMultimap()

        return Response(code, okHttpResponse.message, responseHeaders, body, url)
    }
}
