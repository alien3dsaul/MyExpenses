package org.totschnig.myexpenses.di

import android.app.Activity
import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.ImageViewIntentProvider
import org.totschnig.myexpenses.activity.SystemImageViewIntentProvider
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.ads.AdHandlerFactory
import org.totschnig.myexpenses.util.ads.PlatformAdHandlerFactory
import org.totschnig.myexpenses.util.bundle.LocaleManager
import javax.inject.Named
import javax.inject.Singleton


@Module
class UiModule {
    @Provides
    @Singleton
    fun provideImageViewIntentProvider(): ImageViewIntentProvider = SystemImageViewIntentProvider()

    @Provides
    @Singleton
    fun provideAdHandlerFactory(application: MyApplication, prefHandler: PrefHandler, @Named(AppComponent.USER_COUNTRY) userCountry: String): AdHandlerFactory =
            PlatformAdHandlerFactory(application, prefHandler, userCountry)

    @Provides
    @Singleton
    fun provideLanguageManager(): LocaleManager = try {
        Class.forName("org.totschnig.myexpenses.util.bundle.PlatformLocaleManager").newInstance() as LocaleManager
    } catch (e: Exception) {
        object : LocaleManager {
            var callback: (() -> Unit)? = null
            override fun initApplication(application: Application) {
                //noop
            }

            override fun initActivity(activity: Activity) {
                //noop
            }

            override fun requestLocale(context: Context) {
                callback?.invoke()
            }

            override fun onResume(onAvailable: () -> Unit) {
               this.callback = onAvailable
            }

            override fun onPause() {
                this.callback = null
            }
        }
    }
}