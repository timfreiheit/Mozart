package de.timfreiheit.mozart.sample.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu

import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

import de.timfreiheit.mozart.sample.R

open class BaseActivity : AppCompatActivity() {

    private var castContext: CastContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)

        if (playServicesAvailable == ConnectionResult.SUCCESS) {
            castContext = CastContext.getSharedInstance(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu, menu)

        if (castContext != null) {
            CastButtonFactory.setUpMediaRouteButton(applicationContext,
                    menu, R.id.media_route_menu_item)
        }
        return true
    }

}
