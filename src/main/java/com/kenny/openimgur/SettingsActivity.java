package com.kenny.openimgur;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ImageView;

import com.kenny.openimgur.classes.OpenImgurApp;
import com.kenny.openimgur.classes.VideoCache;
import com.kenny.openimgur.fragments.LoadingDialogFragment;
import com.kenny.openimgur.fragments.PopupDialogViewBuilder;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.openimgur.util.SqlHelper;
import com.kenny.snackbar.SnackBar;

import java.lang.ref.WeakReference;

/**
 * Created by kcampagna on 6/30/14.
 */
public class SettingsActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    public static final String REDDIT_SEARCH_KEY = "subreddit";

    public static final String NSFW_KEY = "allowNSFW";

    public static final String CURRENT_CACHE_SIZE_KEY = "currentCacheSize";

    public static final String THUMBNAIL_QUALITY_KEY = "thumbnailQuality";

    public static final String THUMBNAIL_QUALITY_LOW = "low";

    public static final String THUMBNAIL_QUALITY_MEDIUM = "medium";

    public static final String THUMBNAIL_QUALITY_HIGH = "high";

    private OpenImgurApp mApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ImageView view = (ImageView) findViewById(android.R.id.home);
        if (view != null) {
            try {
                ViewGroup viewGroup = (ViewGroup) view.getParent();
                View upView = viewGroup.getChildAt(0);
                int padding = getResources().getDimensionPixelSize(R.dimen.up_arrow_padding);
                upView.setPadding(padding, padding, padding, padding);
            } catch (Exception e) {
                LogUtil.e("SettingsActivity", "Unable to set upAsHomeIndicator padding", e);
            }
        }

        mApp = ((OpenImgurApp) getApplication());
        addPreferencesFromResource(R.xml.settings);
        bindPreference(findPreference(THUMBNAIL_QUALITY_KEY));
        findPreference(REDDIT_SEARCH_KEY).setOnPreferenceClickListener(this);
        findPreference(CURRENT_CACHE_SIZE_KEY).setOnPreferenceClickListener(this);
        findPreference("licenses").setOnPreferenceClickListener(this);
        findPreference("openSource").setOnPreferenceClickListener(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        ActionBar ab = getActionBar();
        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setIcon(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        SnackBar.cancelSnackBars(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long cacheSize = FileUtil.getDirectorySize(mApp.getImageLoader().getDiskCache().getDirectory());
        cacheSize += VideoCache.getInstance().getCacheSize();
        findPreference(CURRENT_CACHE_SIZE_KEY).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            findPreference("version").setSummary(version);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("Settings Activity", "Unable to get version summary", e);
        }
    }

    private void bindPreference(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        onPreferenceChange(preference, mApp.getPreferences().getString(preference.getKey(), ""));
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(o.toString());
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }

            return true;
        } else {
            // Only have list preferences so far
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        if (preference.getKey().equals(CURRENT_CACHE_SIZE_KEY)) {
            new PopupDialogViewBuilder(SettingsActivity.this)
                    .setTitle(R.string.clear_cache)
                    .setMessage(R.string.clear_cache_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.yes, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            new DeleteCacheTask(SettingsActivity.this).execute();
                        }
                    }).show();
            return true;
        } else if (preference.getKey().equals(REDDIT_SEARCH_KEY)) {
            new SqlHelper(getApplicationContext()).deleteAllSubRedditSearches();
            SnackBar.show(SettingsActivity.this, R.string.reddit_search_cleared);
            return true;
        } else if (preference.getKey().equals("licenses")) {
            AlertDialog dialog = new AlertDialog.Builder(SettingsActivity.this)
                    .setNegativeButton(R.string.dismiss, null).create();
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            WebView webView = new WebView(getApplicationContext());
            webView.loadUrl("file:///android_asset/licenses.html");
            dialog.setView(webView);
            dialog.show();
            return true;
        } else if (preference.getKey().equals("openSource")) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kennyc1012/OpenImgur"));
            if (browserIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(browserIntent);
            } else {
                SnackBar.show(SettingsActivity.this, R.string.cant_launch_intent);
            }
        }

        return false;
    }

    private static class DeleteCacheTask extends AsyncTask<Void, Void, Long> {
        private WeakReference<SettingsActivity> mActivity;

        public DeleteCacheTask(SettingsActivity activity) {
            mActivity = new WeakReference<SettingsActivity>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mActivity.get().getFragmentManager().beginTransaction().add(LoadingDialogFragment.createInstance(R.string.one_moment, false), "loading").commit();
        }

        @Override
        protected Long doInBackground(Void... voids) {
            SettingsActivity activity = mActivity.get();
            activity.mApp.getImageLoader().clearDiskCache();
            VideoCache.getInstance().deleteCache();
            long cacheSize = FileUtil.getDirectorySize(activity.mApp.getCacheDir());
            cacheSize += VideoCache.getInstance().getCacheSize();
            return cacheSize;
        }

        @Override
        protected void onPostExecute(Long cacheSize) {
            SettingsActivity activity = mActivity.get();

            if (activity != null) {
                activity.findPreference(CURRENT_CACHE_SIZE_KEY).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));
                Fragment fragment = activity.getFragmentManager().findFragmentByTag("loading");

                if (fragment != null) {
                    ((DialogFragment) fragment).dismiss();
                }

                mActivity.clear();
            }
        }
    }
}
