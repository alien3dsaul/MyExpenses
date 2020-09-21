package org.totschnig.myexpenses.di

import android.app.Activity
import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.ImageViewIntentProvider
import org.totschnig.myexpenses.activity.SystemImageViewIntentProvider
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.ads.AdHandlerFactory
import org.totschnig.myexpenses.util.ads.PlatformAdHandlerFactory
import org.totschnig.myexpenses.util.locale.Callback
import org.totschnig.myexpenses.util.locale.LocaleManager
import org.totschnig.myexpenses.util.locale.UserLocaleProvider
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
    fun provideLanguageManager(localeProvider: UserLocaleProvider): LocaleManager = try {
        Class.forName("org.totschnig.myexpenses.util.locale.PlatformLocaleManager")
                .getConstructor(UserLocaleProvider::class.java)
                .newInstance(localeProvider) as LocaleManager
    } catch (e: Exception) {
        object : LocaleManager {
            var callback: Callback? = null
            override fun initApplication(application: Application) {
                //noop
            }

            override fun initActivity(activity: Activity) {
                //noop
            }

            override fun requestLocale(context: Context) {
                callback?.onAvailable()
            }

            override fun onResume(callback: Callback) {
                this.callback = callback
            }

            override fun onPause() {
                this.callback = null
            }
        }
    }

    @Provides
    @Singleton
    fun provideFeatureManager(): FeatureManager = try {
        Class.forName("org.totschnig.myexpenses.util.locale.PlatformFeatureManager")
                .newInstance() as FeatureManager
    } catch (e: Exception) {
        object : FeatureManager {
            override fun isFeatureInstalled(feature: FeatureManager.Feature) = false

            override fun requestFeature(feature: FeatureManager.Feature, callback: org.totschnig.myexpenses.feature.Callback) {
                callback.onError(NotImplementedError())
            }
        }
    }
}