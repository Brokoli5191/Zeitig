package app.brokoli5191.zeitig.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One OkHttp client for the whole app; every request identifies the app, nothing else. */
public object HttpClients {
    public fun create(appVersion: String): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", userAgent(appVersion))
                        .build(),
                )
            }.build()

    public fun userAgent(appVersion: String): String = "Zeitig/$appVersion (Android; +https://github.com/Brokoli5191/Zeitig)"
}
