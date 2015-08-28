package com.kenny.openimgur.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.FullScreenPhotoActivity;
import com.kenny.openimgur.adapters.GalleryAdapter2;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.responses.BasicResponse;
import com.kenny.openimgur.classes.ImgurBaseObject;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.snackbar.SnackBar;

import java.util.ArrayList;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by kcampagna on 12/27/14.
 */
public class ProfileUploadsFragment extends BaseGridFragment2 implements View.OnLongClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (user == null) {
            throw new IllegalStateException("User is not logged in");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery2, container, false);
    }

    @Override
    protected void setAdapter(GalleryAdapter2 adapter) {
        super.setAdapter(adapter);
        adapter.setOnLongClickPressListener(this);
    }

    @Override
    protected void fetchGallery() {
        super.fetchGallery();
        ApiClient.getService().getProfileUploads(user.getUsername(), mCurrentPage, this);
    }

    @Override
    protected void onItemSelected(int position, ArrayList<ImgurBaseObject> items) {
        startActivity(FullScreenPhotoActivity.createIntent(getActivity(), items.get(position).getLink()));
    }

    @Override
    public boolean onLongClick(View v) {
        final ImgurBaseObject photo = getAdapter().getItem(mGrid.getChildAdapterPosition(v));

        new AlertDialog.Builder(getActivity(), theme.getAlertDialogTheme())
                .setItems(R.array.uploaded_photos_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 0. Share 1. Copy Link 2. Delete

                        switch (which) {
                            case 0:
                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share));
                                shareIntent.putExtra(Intent.EXTRA_TEXT, photo.getLink());

                                if (shareIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.send_feedback)));
                                } else {
                                    SnackBar.show(getActivity(), R.string.cant_launch_intent);
                                }
                                break;

                            case 1:
                                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(ClipData.newPlainText("link", photo.getLink()));
                                break;

                            case 2:
                                new AlertDialog.Builder(getActivity(), theme.getAlertDialogTheme())
                                        .setMessage(R.string.profile_delete_image)
                                        .setNegativeButton(R.string.cancel, null)
                                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                mMultiStateView.setViewState(MultiStateView.VIEW_STATE_LOADING);
                                                deletePhoto(photo);
                                            }
                                        }).show();
                                break;
                        }
                    }
                }).show();

        return true;
    }

    @Override
    protected void onEmptyResults() {
        mHasMore = false;
        mIsLoading = false;

        if (getAdapter() == null || getAdapter().isEmpty()) {
            String errorMessage = getString(R.string.profile_no_uploads);
            mMultiStateView.setErrorText(R.id.errorMessage, errorMessage);
            mMultiStateView.setViewState(MultiStateView.VIEW_STATE_ERROR);
        }
    }

    @Override
    protected boolean showPoints() {
        return false;
    }

    private void deletePhoto(final ImgurBaseObject photo) {
        mMultiStateView.setViewState(MultiStateView.VIEW_STATE_LOADING);

        ApiClient.getService().deletePhoto(photo.getDeleteHash(), new Callback<BasicResponse>() {
            @Override
            public void success(BasicResponse basicResponse, Response response) {
                if (!isAdded()) return;

                if (basicResponse != null && basicResponse.data) {
                    GalleryAdapter2 adapter = getAdapter();

                    if (adapter != null) {
                        adapter.removeItem(photo);
                    }

                    if (adapter == null || adapter.isEmpty()) {
                        onEmptyResults();
                    } else {
                        mMultiStateView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                    }

                    SnackBar.show(getActivity(), R.string.profile_delete_success_image);
                } else {
                    SnackBar.show(getActivity(), R.string.error_generic);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                if (!isAdded()) return;
                LogUtil.e(TAG, "Unable to delete photo", error);
                mMultiStateView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                SnackBar.show(getActivity(), R.string.error_generic);
            }
        });

    }
}
