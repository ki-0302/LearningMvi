package com.maho_ya.learningmvi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

// アプリケーションレベルの依存関係として機能するコンテナの生成をトリガーする。
// Applicationオブジェクトのライフサイクルにアタッチされる
@HiltAndroidApp
class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
