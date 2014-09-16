package com.feality.app.syncit.fragments;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.feality.app.syncit.LaunchActivity;
import com.feality.app.syncit.R;
import com.todddavies.components.progressbar.ProgressWheel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by Filip on 2014-09-15.
 */
public class MediaFragment extends SmarterFragment {

    private static final String LOG_TAG = DiscoveryFragment.class.getSimpleName();

    private static final int RESULT_FILE_SELECT_CODE = 0;
    private static final int CHILD_FIRST = 0;
    private static final int CHILD_SECOND = 1;

    private TextView mTitleView;
    private TextView mSelectButton;
    private TextView mPlayButton;

    private ProgressWheel mProgressWheel;
    private LaunchActivity mActivity;
    private MediaPlayer mMediaPlayer;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_media, container, false);

        mProgressWheel = get(v, R.id.media_pw_spinner, ProgressWheel.class);
        mProgressWheel.setText("Select a file");
        if(!mProgressWheel.isSpinning()){
            mProgressWheel.spin();
        }

        mTitleView = get(v, R.id.media_title, TextView.class);

        mSelectButton = get(v, R.id.media_select_btn, Button.class);
        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                showFileChooser();
            }
        });

        mPlayButton = get(v, R.id. media_play_btn, Button.class);
        mPlayButton.setEnabled(false);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mMediaPlayer.isPlaying()){
                    mMediaPlayer.pause();
                    mMediaPlayer.seekTo(0);
                    mPlayButton.setText("Play");
                } else {
                    mMediaPlayer.start();
                    mPlayButton.setText("Stop");
                }
            }
        });

        mMediaPlayer = new MediaPlayer();
        return v;
    }

    @Override
    public void onAttach(final Activity activity) {
        mActivity = LaunchActivity.class.cast(activity);
        super.onAttach(activity);
    }

    private void onFileSelected(final Uri uri) throws IOException {
        mPlayButton.setText("Play");
        mSelectButton.setText(uri.getPath());

        if (mMediaPlayer.isPlaying()){
            mMediaPlayer.stop();
            mMediaPlayer.reset();
        }

        mMediaPlayer.setDataSource(getActivity(), uri);
        mMediaPlayer.prepare();

        mPlayButton.setEnabled(true);
        mActivity.distributeData(uri);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_FILE_SELECT_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.d(LOG_TAG, "Selected file: " + uri.toString());


            File f = new File(uri.getPath());
            long size = f.length();
            try {
                FileInputStream fis = new FileInputStream(f);
                size = Math.max(fis.available(), size);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unable to get file size", e);
            }

            mProgressWheel.clearAnimation();

            ObjectAnimator animation0 = ObjectAnimator.ofInt(mProgressWheel, "progress", 0, 360);
            animation0.setInterpolator(new AccelerateDecelerateInterpolator());
            animation0.setRepeatMode(ValueAnimator.REVERSE);
            animation0.setRepeatCount(ValueAnimator.INFINITE);
            animation0.setDuration(5000); // 5 seconds
            animation0.start();

            final int fileSize = (int) (size / 1024);
            ValueAnimator animation1 = ValueAnimator.ofInt(0, fileSize);
            animation1.setInterpolator(new AccelerateDecelerateInterpolator());
            animation1.setRepeatMode(ValueAnimator.REVERSE);
            animation1.setRepeatCount(ValueAnimator.INFINITE);
            animation1.setDuration(5000); // 5 seconds
            animation1.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(final ValueAnimator animation) {
                    int i = (Integer) animation.getAnimatedValue();
                    mProgressWheel.setText("Transferring file (" + i + " KB)");
                }
            });
            animation1.start();

            /*
            try {
                onFileSelected(uri);
            } catch (IOException e) {
                mProgressWheel.setText("Unable to play track");
            }*/
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    RESULT_FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {

            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(getActivity(), "Please install a File Manager", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        mMediaPlayer.release();
        mMediaPlayer = null;
        super.onDestroy();
    }

    public void showRoundTripTime(final float rtt) {
        mProgressWheel.setText("Measured Round Trip Time: "+ rtt +" ms");
    }
}
