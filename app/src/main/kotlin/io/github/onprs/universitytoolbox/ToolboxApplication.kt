package io.github.onprs.universitytoolbox

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import io.github.onprs.universitytoolbox.core.network.ImageCompatRequestInterceptor
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.OkHttpClient

/**
 * 宿主应用入口：初始化 Hilt 根容器及共享连接池的 Coil 3 图片加载器。
 */
@HiltAndroidApp
class ToolboxApplication :
    Application(),
    SingletonImageLoader.Factory {

    @Inject
    @field:RootHttpClient
    lateinit var rootHttpClientProvider: Provider<OkHttpClient>

    private val imageHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        rootHttpClientProvider.get().newBuilder()
            .addInterceptor(ImageCompatRequestInterceptor())
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
        }
        .build()
}
