package org.wordpress.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.ui.main.WPMainActivity;
import org.wordpress.android.util.ProfilingUtils;
import org.wordpress.android.util.ToastUtils;

public class WPLaunchActivity extends LocaleAwareActivity {
    /*
     * this the main (default) activity, which does nothing more than launch the
     * previously active activity on startup - note that it's defined in the
     * manifest to have no UI
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(WPLaunchActivity.class.getSimpleName(), "***=> onCreate");
        ProfilingUtils.split("WPLaunchActivity.onCreate");
        launchWPMainActivity();
    }

    private void launchWPMainActivity() {
        if (WordPress.wpDB == null) {
            ToastUtils.showToast(this, R.string.fatal_db_error, ToastUtils.Duration.LONG);
            finish();
            return;
        }

        if (launchJetpackDeepLinkIfNeeded(getIntent())) return;

        Intent intent = new Intent(this, WPMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(getIntent().getAction());
        intent.setData(getIntent().getData());
        if (intent.getAction() != null) {
            Log.i(WPLaunchActivity.class.getSimpleName(), "***=> action: " + intent.getAction());
        }
        if (intent.getData() != null) {
            Log.i(WPLaunchActivity.class.getSimpleName(), "***=> intent: " + intent.getData().toString());
        }
        startActivity(intent);
        finish();
    }

    private boolean launchJetpackDeepLinkIfNeeded(Intent intent) {
        if (getIntent() == null || getIntent().getData() == null || getIntent().getAction() == null) {
            return false;
        }

        Log.i(WPLaunchActivity.class.getSimpleName(), "***=> launch action: " + intent.getAction());
        Log.i(WPLaunchActivity.class.getSimpleName(), "***=> launch intent: " + intent.getData().toString());

        if (getIntent().getDataString().contains("jetpack://")) {
            Intent newIntent = new Intent(Intent.ACTION_VIEW);
            newIntent.setData(Uri.parse(intent.getDataString()));
            startActivity(newIntent);
            finish();
            return true;
        } else {
            return false;
        }
    }
}
