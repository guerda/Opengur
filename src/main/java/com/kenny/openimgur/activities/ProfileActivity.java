package com.kenny.openimgur.activities;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.kenny.openimgur.R;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.responses.UserResponse;
import com.kenny.openimgur.classes.ImgurUser;
import com.kenny.openimgur.fragments.ProfileAlbumsFragment;
import com.kenny.openimgur.fragments.ProfileCommentsFragment;
import com.kenny.openimgur.fragments.ProfileFavoritesFragment;
import com.kenny.openimgur.fragments.ProfileInfoFragment;
import com.kenny.openimgur.fragments.ProfileMessagesFragment;
import com.kenny.openimgur.fragments.ProfileSubmissionsFragment;
import com.kenny.openimgur.fragments.ProfileUploadsFragment;
import com.kenny.openimgur.services.AlarmReceiver;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.ui.ViewPager;
import com.kenny.openimgur.util.LogUtil;

import butterknife.Bind;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by kcampagna on 12/14/14.
 */
public class ProfileActivity extends BaseActivity {
    private static final String LOGIN_URL = "https://api.imgur.com/oauth2/authorize?client_id=" + ApiClient.CLIENT_ID + "&response_type=token";

    private static final String REDIRECT_URL = "https://com.kenny.openimgur";

    public static final String KEY_LOGGED_IN = "logged_in";

    public static final String KEY_LOGGED_OUT = "logged_out";

    private static final String KEY_USERNAME = "username";

    private static final String KEY_USER = "user";

    @Bind(R.id.slidingTabs)
    TabLayout mSlidingTabs;

    @Bind(R.id.pager)
    ViewPager mPager;

    @Bind(R.id.multiView)
    MultiStateView mMultiView;

    @Bind(R.id.toolBar)
    Toolbar mToolBar;

    private ImgurUser mSelectedUser;

    private ProfilePager mAdapter;

    public static Intent createIntent(Context context, @Nullable String userName) {
        Intent intent = new Intent(context, ProfileActivity.class);
        if (!TextUtils.isEmpty(userName)) intent.putExtra(KEY_USERNAME, userName);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        setStatusBarColorResource(theme.darkColor);
        setupToolBar();
        handleData(savedInstanceState, getIntent());

        // TODO remove when bug has been fixed in support library
        if (ViewCompat.isLaidOut(mSlidingTabs)) {
            mSlidingTabs.setupWithViewPager(mPager);
        } else {
            mSlidingTabs.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    mSlidingTabs.setupWithViewPager(mPager);
                    mSlidingTabs.removeOnLayoutChangeListener(this);
                }
            });
        }
    }

    /**
     * Sets up the tool bar to take the place of the action bar
     */
    private void setupToolBar() {
        setSupportActionBar(mToolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void handleData(Bundle savedInstanceState, Intent args) {
        if (savedInstanceState == null) {
            if (args.hasExtra(KEY_USERNAME)) {
                LogUtil.v(TAG, "User present in Bundle extras");
                String username = args.getStringExtra(KEY_USERNAME);
                mSelectedUser = app.getSql().getUser(username);
                configUser(username);
            } else if (app.getUser() != null) {
                LogUtil.v(TAG, "User already logged in");
                mSelectedUser = app.getUser();
                configUser(null);
            } else {
                LogUtil.v(TAG, "No user present. Showing Login screen");
                configWebView();
            }
        } else if (savedInstanceState.containsKey(KEY_USER)) {
            mSelectedUser = savedInstanceState.getParcelable(KEY_USER);
            configUser(null);
        }
    }

    private void configUser(String username) {
        mMultiView.setViewState(MultiStateView.VIEW_STATE_LOADING);
        // Load the new user data if we haven't viewed the user within 24 hours
        if (mSelectedUser == null || System.currentTimeMillis() - mSelectedUser.getLastSeen() >= DateUtils.DAY_IN_MILLIS) {
            LogUtil.v(TAG, "Selected user is null or data is too old, fetching new data");
            String userName = mSelectedUser == null ? username : mSelectedUser.getUsername();
            getSupportActionBar().setTitle(userName);
            fetchProfile(userName);
        } else {
            LogUtil.v(TAG, "Selected user present in database and has valid data");
            mAdapter = new ProfilePager(getApplicationContext(), getFragmentManager(), mSelectedUser);
            mPager.setAdapter(mAdapter);
            mSlidingTabs.setupWithViewPager(mPager);
            mMultiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
            getSupportActionBar().setTitle(mSelectedUser.getUsername());
            supportInvalidateOptionsMenu();
        }
    }

    public void onUserLogout() {
        mSelectedUser = null;
        app.onLogout();
        configWebView();
        setResult(Activity.RESULT_OK, new Intent().putExtra(KEY_LOGGED_OUT, true));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.logout).setVisible(mSelectedUser != null && mSelectedUser.isSelf(app));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                new AlertDialog.Builder(ProfileActivity.this, theme.getAlertDialogTheme())
                        .setTitle(R.string.logout)
                        .setMessage(R.string.logout_confirm)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                onUserLogout();
                            }
                        }).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Configures the webview to handle a user logging in
     */
    private void configWebView() {
        getSupportActionBar().hide();
        mMultiView.setViewState(MultiStateView.VIEW_STATE_EMPTY);
        WebView webView = (WebView) mMultiView.getView(MultiStateView.VIEW_STATE_EMPTY).findViewById(R.id.loginWebView);
        webView.loadUrl(LOGIN_URL);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(REDIRECT_URL)) {

                    if (url.contains("/?error=")) {
                        LogUtil.v(TAG, "Error received from URL " + url);
                        view.loadUrl(LOGIN_URL);
                        return true;
                    }

                    // We will extract the info from the callback url
                    mMultiView.setViewState(MultiStateView.VIEW_STATE_LOADING);
                    String[] outerSplit = url.split("\\#")[1].split("\\&");
                    String username = null;
                    String accessToken = null;
                    String refreshToken = null;
                    long accessTokenExpiration = 0;
                    int index = 0;

                    for (String s : outerSplit) {
                        String[] innerSplit = s.split("\\=");

                        switch (index) {
                            // Access Token
                            case 0:
                                accessToken = innerSplit[1];
                                break;

                            // Access Token Expiration
                            case 1:
                                long expiresIn = Long.parseLong(innerSplit[1]);
                                accessTokenExpiration = System.currentTimeMillis() + (expiresIn * DateUtils.SECOND_IN_MILLIS);
                                break;

                            // Token Type, not using
                            case 2:
                                //NO OP
                                break;

                            // Refresh Token
                            case 3:
                                refreshToken = innerSplit[1];
                                break;

                            // Username
                            case 4:
                                username = innerSplit[1];
                                break;
                        }

                        index++;
                    }

                    // Make sure that everything was set
                    if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(accessToken) &&
                            !TextUtils.isEmpty(refreshToken) && accessTokenExpiration > 0) {
                        ImgurUser newUser = new ImgurUser(username, accessToken, refreshToken, accessTokenExpiration);
                        app.setUser(newUser);
                        user = newUser;
                        mSelectedUser = newUser;
                        LogUtil.v(TAG, "User " + newUser.getUsername() + " logged in");
                        fetchProfile(mSelectedUser.getUsername());
                        CookieManager.getInstance().removeAllCookie();
                        view.clearHistory();
                        view.clearCache(true);
                        view.clearFormData();
                        getSupportActionBar().show();
                        getSupportActionBar().setTitle(user.getUsername());
                        setResult(Activity.RESULT_OK, new Intent().putExtra(KEY_LOGGED_IN, true));
                        AlarmReceiver.createNotificationAlarm(getApplicationContext());
                    } else {
                        mMultiView.setErrorText(R.id.errorMessage, R.string.error_generic);
                        mMultiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                    }
                } else {
                    view.loadUrl(url);
                }

                return true;
            }
        });
    }

    private void fetchProfile(String username) {
        ApiClient.getService().getProfile(username, new Callback<UserResponse>() {
            @Override
            public void success(UserResponse userResponse, Response response) {
                if (isDestroyed() || isFinishing()) return;

                if (userResponse != null && userResponse.data != null) {
                    if (mSelectedUser != null) {
                        mSelectedUser.copy(userResponse.data);
                    } else {
                        mSelectedUser = userResponse.data;
                    }

                    if (mSelectedUser.isSelf(app) && !TextUtils.isEmpty(mSelectedUser.getAccessToken())) {
                        app.getSql().updateUserInfo(mSelectedUser);
                    } else {
                        app.getSql().insertProfile(mSelectedUser);
                    }

                    mAdapter = new ProfilePager(getApplicationContext(), getFragmentManager(), mSelectedUser);
                    mPager.setAdapter(mAdapter);
                    mSlidingTabs.setupWithViewPager(mPager);
                    mMultiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                    supportInvalidateOptionsMenu();
                } else {
                    mMultiView.setErrorText(R.id.errorMessage, R.string.error_generic);
                    mMultiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                mMultiView.setErrorText(R.id.errorMessage, ApiClient.getErrorCode(error));
                mMultiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedUser != null) {
            outState.putParcelable(KEY_USER, mSelectedUser);
        }
    }

    @Override
    protected int getStyleRes() {
        return theme.isDarkTheme ? R.style.Theme_Translucent_Main_Dark : R.style.Theme_Translucent_Main_Light;
    }

    private static class ProfilePager extends FragmentStatePagerAdapter {
        private static final int NUM_PAGES_SELF = 7;

        private static final int NUM_PAGES_FAR = 5;

        private String[] mTitles;

        private ImgurUser mUser;

        private int mNumPages;

        public ProfilePager(Context context, FragmentManager fm, ImgurUser user) {
            super(fm);
            mTitles = context.getResources().getStringArray(R.array.profile_tabs);
            mUser = user;
            mNumPages = mUser.isSelf() ? NUM_PAGES_SELF : NUM_PAGES_FAR;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                // Info
                case 0:
                    return ProfileInfoFragment.createInstance(mUser);

                // Submissions
                case 1:
                    return ProfileSubmissionsFragment.newInstance(mUser);

                // Favorites
                case 2:
                    return ProfileFavoritesFragment.newInstance(mUser);

                // Albums
                case 3:
                    return ProfileAlbumsFragment.createInstance(mUser);

                // Comments
                case 4:
                    return ProfileCommentsFragment.createInstance(mUser);

                // Uploads
                case 5:
                    return new ProfileUploadsFragment();

                // Messages
                case 6:
                    return new ProfileMessagesFragment();

                default:
                    throw new IndexOutOfBoundsException("How did this happen?");
            }
        }

        @Override
        public int getCount() {
            return mNumPages;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTitles[position];
        }
    }
}
