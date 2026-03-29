package com.example.pace.data.auth

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pace.ui.splash.SplashActivity
import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.ReissueRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val authDataStore: AuthDataStore,
    @ApplicationContext private val context: Context
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            Log.w(TAG, "Skip reissue: too many retries for ${response.request.url}")
            return null
        }
        if (response.request.url.encodedPath.contains("/api/v1/auth/reissue")) {
            Log.w(TAG, "Skip reissue: authenticator invoked for reissue endpoint itself")
            return null
        }

        synchronized(this) {
            val currentAccessToken = authDataStore.getAccessToken()
            val requestAuthHeader = response.request.header("Authorization")
            Log.d(TAG, "401 detected for ${response.request.method} ${response.request.url}")
            Log.d(TAG, "Request auth header exists=${!requestAuthHeader.isNullOrBlank()}")
            Log.d(TAG, "Stored access token exists=${!currentAccessToken.isNullOrBlank()}")

            if (!requestAuthHeader.isNullOrBlank() && currentAccessToken != null) {
                val normalizedCurrent = normalizeTokenForComparison(currentAccessToken)
                val normalizedRequested = normalizeTokenForComparison(requestAuthHeader)

                if (normalizedCurrent.isNotBlank() && normalizedCurrent != normalizedRequested) {
                    Log.d(TAG, "Stored access token changed while request was in flight. Retrying with latest token.")
                    return response.request.newBuilder()
                        .header("Authorization", rebuildAuthorizationHeader(requestAuthHeader, currentAccessToken))
                        .build()
                }
            }

            val refreshToken = authDataStore.getRefreshToken() ?: return null
            Log.d(TAG, "Trying token reissue. Refresh token exists=true")
            val refreshService = createRefreshService()
            val refreshResponse = try {
                runBlocking {
                    refreshService.reissueToken(refreshToken, ReissueRequest(refreshToken))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token reissue request failed: ${e.message}", e)
                handleRefreshFailure()
                return null
            }

            Log.d(
                TAG,
                "Token reissue response: isSuccess=${refreshResponse.isSuccess}, code=${refreshResponse.code}, message=${refreshResponse.message}"
            )

            if (!refreshResponse.isSuccess || refreshResponse.result == null) {
                Log.w(TAG, "Token reissue failed. Clearing stored tokens.")
                handleRefreshFailure()
                return null
            }

            val newTokens = refreshResponse.result
            authDataStore.saveTokens(newTokens.accessToken, newTokens.refreshToken)
            Log.d(TAG, "Token reissue succeeded. Saved new access/refresh tokens.")

            val newAccessToken = newTokens.accessToken
            return response.request.newBuilder()
                .header(
                    "Authorization",
                    rebuildAuthorizationHeader(requestAuthHeader, newAccessToken)
                )
                .build()
        }
    }

    private fun createRefreshService(): AuthControllerService {
        val client = OkHttpClient.Builder().build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthControllerService::class.java)
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private fun normalizeTokenForComparison(token: String): String {
        return token.removePrefix(BEARER_PREFIX).trim()
    }

    private fun rebuildAuthorizationHeader(originalHeader: String?, accessToken: String): String {
        return if (originalHeader?.startsWith(BEARER_PREFIX, ignoreCase = true) == true) {
            "$BEARER_PREFIX$accessToken"
        } else {
            accessToken
        }
    }

    private fun handleRefreshFailure() {
        authDataStore.clearTokens()
        if (hasRedirectedToSplash) return

        hasRedirectedToSplash = true
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val BASE_URL = "https://pace-server.kro.kr/"
        private const val BEARER_PREFIX = "Bearer "
        @Volatile private var hasRedirectedToSplash: Boolean = false
    }
}
