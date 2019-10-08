package org.mozilla.gleancrashexample

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import mozilla.components.service.glean.Glean
import org.mozilla.gleancrashexample.GleanMetrics.Crash
import org.mozilla.gleancrashexample.GleanMetrics.Pings
import java.lang.NullPointerException

class MainActivity : AppCompatActivity(), Thread.UncaughtExceptionHandler {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

            // Go boom!
            throw NullPointerException()
        }

        Glean.setUploadEnabled(true)
        Glean.initialize(this)
        Glean.registerPings(Pings)

        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        Crash.exception.record(mapOf(Crash.exceptionKeys.cause to p1.cause!!.toString(),
            Crash.exceptionKeys.message to p1.message!!))
        //Pings.crash.send()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
