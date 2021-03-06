package com.kenny.openimgur.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.kenny.openimgur.R;
import com.kenny.openimgur.classes.OpengurApp;
import com.kenny.snackbar.SnackBar;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by kcampagna on 8/22/14.
 */
public class CommentPopupFragment extends DialogFragment implements DialogInterface.OnClickListener {
    private static final String KEY_GALLERY_ID = "gallery_id";

    private static final String KEY_PARENT_ID = "parent_id";

    @Bind(R.id.comment)
    EditText mComment;

    @Bind(R.id.remainingChars)
    TextView mRemainingCharacters;

    private String mGalleryId;

    private String mParentId;

    private CommentListener mListener;

    /**
     * Creates an instance of CommentPopupFragment
     *
     * @param galleryId The id of the image or gallery being commented on
     * @param parent    Optional parent comment id for a reply
     * @return
     */
    public static CommentPopupFragment createInstance(@NonNull String galleryId, @Nullable String parent) {
        CommentPopupFragment fragment = new CommentPopupFragment();
        Bundle args = new Bundle();
        args.putString(KEY_GALLERY_ID, galleryId);

        if (!TextUtils.isEmpty(parent)) {
            args.putString(KEY_PARENT_ID, parent);
        }

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onPause() {
        mComment.setCursorVisible(false);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof CommentListener) {
            mListener = (CommentListener) activity;
        } else {
            throw new IllegalArgumentException("Activity must be an instance of CommentListener");
        }
    }

    @Override
    public void onDetach() {
        mListener = null;
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_GALLERY_ID)) {
            dismiss();
            SnackBar.show(getActivity(), R.string.error_generic);
            return;
        }

        mGalleryId = args.getString(KEY_GALLERY_ID);
        mParentId = args.getString(KEY_PARENT_ID, null);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.comment_dialog, null);
        ButterKnife.bind(this, view);

        mComment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                // NOOP
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                mRemainingCharacters.setText(String.valueOf(140 - charSequence.length()));
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // NOOP
            }
        });

        return new AlertDialog.Builder(getActivity(), OpengurApp.getInstance(getActivity()).getImgurTheme().getAlertDialogTheme())
                .setTitle(R.string.comment)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay, this)
                .setView(view)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                String comment = mComment.getText().toString();

                if (!TextUtils.isEmpty(comment)) {
                    if (mListener != null) mListener.onPostComment(comment, mGalleryId, mParentId);
                }
                break;
        }
    }

    public interface CommentListener {
        void onPostComment(String comment, String galleryId, String parentId);
    }
}
