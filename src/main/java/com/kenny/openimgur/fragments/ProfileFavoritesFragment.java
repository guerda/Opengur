package com.kenny.openimgur.fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.FullScreenPhotoActivity;
import com.kenny.openimgur.activities.ViewActivity;
import com.kenny.openimgur.adapters.GalleryAdapter2;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.ImgurService;
import com.kenny.openimgur.api.responses.BasicResponse;
import com.kenny.openimgur.api.responses.GalleryResponse;
import com.kenny.openimgur.classes.ImgurAlbum;
import com.kenny.openimgur.classes.ImgurBaseObject;
import com.kenny.openimgur.classes.ImgurPhoto;
import com.kenny.openimgur.classes.ImgurUser;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.snackbar.SnackBar;

import java.util.ArrayList;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by kcampagna on 12/20/14.
 */
public class ProfileFavoritesFragment extends BaseGridFragment2 implements View.OnLongClickListener {
    private static final String KEY_USER = "user";

    private ImgurUser mSelectedUser;

    public static ProfileFavoritesFragment newInstance(@NonNull ImgurUser user) {
        ProfileFavoritesFragment fragment = new ProfileFavoritesFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(KEY_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery2, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public boolean onLongClick(View v) {
        final ImgurBaseObject obj = getAdapter().getItem(mGrid.getChildAdapterPosition(v));
        new AlertDialog.Builder(getActivity(), theme.getAlertDialogTheme())
                .setTitle(R.string.profile_unfavorite_title)
                .setMessage(R.string.profile_unfavorite_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMultiStateView.setViewState(MultiStateView.VIEW_STATE_LOADING);
                        removeFavorite(obj);
                    }
                })
                .show();

        return true;
    }

    @Override
    protected void fetchGallery() {
        super.fetchGallery();
        boolean isSelf = mSelectedUser.isSelf(app);
        ImgurService apiService = ApiClient.getService();

        if (isSelf) {
            apiService.getProfileFavorites(mSelectedUser.getUsername(), this);
        } else {
            apiService.getProfileGalleryFavorites(mSelectedUser.getUsername(), mCurrentPage, this);
        }
    }

    @Override
    protected void setAdapter(GalleryAdapter2 adapter) {
        super.setAdapter(adapter);
        if (mSelectedUser != null && mSelectedUser.isSelf(app))
            adapter.setOnLongClickPressListener(this);
    }

    @Override
    protected void onItemSelected(int position, ArrayList<ImgurBaseObject> items) {
        super.onItemSelected(position, items);
    }

    @Override
    public void onClick(View v) {
        int position = mGrid.getChildAdapterPosition(v);

        if (position >= 0) {
            ImgurBaseObject obj = getAdapter().getItem(position);
            Intent intent;

            if (obj instanceof ImgurAlbum || obj.getUpVotes() > Integer.MIN_VALUE) {
                ArrayList<ImgurBaseObject> items = new ArrayList<>(1);
                items.add(obj);
                intent = ViewActivity.createIntent(getActivity(), items, 0);
            } else {
                intent = FullScreenPhotoActivity.createIntent(getActivity(), obj.getLink());
            }

            startActivity(intent);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_USER, mSelectedUser);
    }

    @Override
    protected void onRestoreSavedInstance(Bundle savedInstanceState) {
        super.onRestoreSavedInstance(savedInstanceState);
        if (savedInstanceState != null) {
            mSelectedUser = savedInstanceState.getParcelable(KEY_USER);
        } else {
            mSelectedUser = getArguments().getParcelable(KEY_USER);
        }

        if (mSelectedUser == null) {
            throw new IllegalArgumentException("Profile must be supplied to fragment");
        }

        if (getAdapter() != null && mSelectedUser.isSelf(app)) {
            getAdapter().setOnLongClickPressListener(this);
        }
    }

    @Override
    public void success(GalleryResponse galleryResponse, Response response) {
        super.success(galleryResponse, response);
        if (mSelectedUser.isSelf(app)) mHasMore = false;
    }

    @Override
    protected void onEmptyResults() {
        mIsLoading = false;
        mHasMore = false;

        if (getAdapter() == null || getAdapter().isEmpty()) {
            String errorMessage = getString(R.string.profile_no_favorites, mSelectedUser.getUsername());
            mMultiStateView.setErrorText(R.id.errorMessage, errorMessage);
            mMultiStateView.setViewState(MultiStateView.VIEW_STATE_ERROR);
        }
    }

    private void removeFavorite(final ImgurBaseObject object) {
        String id = object.getId();
        Callback<BasicResponse> cb = new Callback<BasicResponse>() {
            @Override
            public void success(BasicResponse basicResponse, Response response) {
                if (!isAdded()) return;

                if (basicResponse != null && basicResponse.success) {
                    GalleryAdapter2 adapter = getAdapter();
                    if (adapter != null) adapter.removeItem(object);
                    mMultiStateView.setViewState(adapter != null && adapter.isEmpty() ? MultiStateView.VIEW_STATE_EMPTY : MultiStateView.VIEW_STATE_CONTENT);
                } else {
                    SnackBar.show(getActivity(), R.string.error_generic);
                    mMultiStateView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                if (!isAdded()) return;
                LogUtil.e(TAG, "Unable to favorite item", error);
                SnackBar.show(getActivity(), R.string.error_generic);
                mMultiStateView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
            }
        };

        if (object instanceof ImgurPhoto) {
            ApiClient.getService().favoriteImage(id, id, cb);
        } else {
            ApiClient.getService().favoriteAlbum(id, id, cb);
        }
    }
}
