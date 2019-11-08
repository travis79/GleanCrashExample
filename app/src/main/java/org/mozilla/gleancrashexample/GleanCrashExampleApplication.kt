package org.mozilla.gleancrashexample

import android.app.Application
import mozilla.components.service.glean.Glean
import org.mozilla.gleancrashexample.GleanMetrics.Pings

class GleanCrashExampleApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        // Register the sample application's custom pings.
        Glean.registerPings(Pings)

        // Set upload enabled
        Glean.setUploadEnabled(true)

        // Initialize the Glean library. Ideally, this is the first thing that
        // must be done right after enabling logging.
        Glean.initialize(applicationContext)
    }
}