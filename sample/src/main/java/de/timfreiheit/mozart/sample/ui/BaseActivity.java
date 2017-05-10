package de.timfreiheit.mozart.sample.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import de.timfreiheit.mozart.sample.R;

public class BaseActivity extends AppCompatActivity {

    private CastContext castContext;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int playServicesAvailable =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);

        if (playServicesAvailable == ConnectionResult.SUCCESS) {
            castContext = CastContext.getSharedInstance(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);

        if (castContext != null) {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                    menu, R.id.media_route_menu_item);
        }
        return true;
    }

}
