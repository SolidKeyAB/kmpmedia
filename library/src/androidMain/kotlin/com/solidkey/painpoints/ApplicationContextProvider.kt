package com.solidkey.painpoints

import android.content.Context

object ApplicationContextProvider {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (appContext == null) { // Only set once to prevent unintended overwrites
                appContext = context.applicationContext
            }
        }
    }

    fun getContext(): Context {
        return appContext ?: throw IllegalStateException("ApplicationContextProvider is not initialized")
    }

    fun isInitialized(): Boolean = appContext != null
}
