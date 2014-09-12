package com.feality.app.syncit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.todddavies.components.progressbar.ProgressWheel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Filip on 2014-09-10.
 */
public class DiscoveryFragment extends SmarterFragment implements WifiP2pUiListener, AdapterView.OnItemClickListener {

    private static final String LOG_TAG = DiscoveryFragment.class.getSimpleName();

    public static final int CHILD_FIRST = 0;
    public static final int CHILD_SECOND = 1;

    private ListView mListView;
    private ViewSwitcher mViewSwitcher;
    private ProgressWheel mProgressWheel;
    private PeerListAdapter mPeerListAdapter;
    private Button mSelectButton;
    private Button mPlayButton;
    private MediaPlayer mMediaPlayer;
    private LaunchActivity mActivity;
    private TextView mTitleView;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discover, container, false);

        mViewSwitcher = ViewSwitcher.class.cast(v);
        mViewSwitcher.setInAnimation(inflater.getContext(), android.R.anim.fade_in);
        mViewSwitcher.setOutAnimation(inflater.getContext(), android.R.anim.fade_out);

        mProgressWheel = get(v, R.id.discovery_pw_spinner, ProgressWheel.class);
        mProgressWheel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                mViewSwitcher.setDisplayedChild(CHILD_SECOND);
                return true;
            }
        });

        mPeerListAdapter = new PeerListAdapter(inflater.getContext(), -1);

        mListView = get(v, R.id.discovery_list, ListView.class);
        mListView.setAdapter(mPeerListAdapter);
        mListView.setOnItemClickListener(this);

        mSelectButton = get(v, R.id.discovery_select_btn, Button.class);
        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                showFileChooser();
            }
        });

        mMediaPlayer = new MediaPlayer();

        mPlayButton = get(v, R.id.discovery_play_btn, Button.class);
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

        mTitleView = get(v, R.id.discovery_title, TextView.class);

        return v;
    }

    @Override
    public void onAttach(final Activity activity) {
        mActivity = LaunchActivity.class.cast(activity);
        super.onAttach(activity);
    }

    private static final int FILE_SELECT_CODE = 0;

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(getActivity(), "Please install a File Manager", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.d(LOG_TAG, "File Uri: " + uri.toString());
                    // Get the path
                    try {
                        onFileSelected(uri);
                    } catch (IOException e) {
                        Log.wtf(LOG_TAG, "Unable to read file: " + uri, e);
                    }

                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        mMediaPlayer.release();
        mMediaPlayer = null;
        super.onDestroy();
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
    public void showWifiStateChange(int state, String stateText) {
        if (state == 2 || state == 3) {
            // We are cool
        } else {
            mProgressWheel.setText(stateText);
            mViewSwitcher.setDisplayedChild(CHILD_FIRST);
            mProgressWheel.stopSpinning();
        }
    }

    @Override
    public void onDiscoveringPeers() {
        mProgressWheel.setText(getString(R.string.waiting_for_peers));
        if (!mProgressWheel.isSpinning()) {
            mProgressWheel.spin();
        }
    }

    @Override
    public void updatePeerList(List<WifiP2pDevice> peers) {
        mPeerListAdapter.swapPeers(peers);
        mPeerListAdapter.notifyDataSetChanged();
        showPeerList();
    }

    private void showPeerList() {
        if ( mViewSwitcher.getDisplayedChild() != CHILD_SECOND){
            mViewSwitcher.setDisplayedChild(CHILD_SECOND);
        }
    }

    @Override
    public void onP2PNotSupported() {
        showLoadingSpinner();
    }

    @Override
    public void onGroupOwnerAvailable(final WifiP2pInfo info) {
        // InetAddress from WifiP2pInfo struct.
        Log.d(LOG_TAG, "groupOwnerAddress:" + info);

        if (info.groupFormed) {
            final boolean isGo = info.isGroupOwner;
            String mode = getString(isGo ?
                    R.string.discovery_title_master : R.string.discovery_title_slave);
            mTitleView.setText(getString(R.string.discovery_title).concat(" ").concat(mode));
        } else {
            mTitleView.setText(getString(R.string.discovery_title).concat(" ").concat("(no owner yet)"));
        }

        mPlayButton.setEnabled(info.groupFormed);
        mSelectButton.setEnabled(info.groupFormed);
    }

    private void showLoadingSpinner() {
        mViewSwitcher.setDisplayedChild(CHILD_FIRST);
    }

    public void showError(String err) {
        final ProgressWheel progressWheel = get(R.id.discovery_pw_spinner, ProgressWheel.class);
        progressWheel.stopSpinning();
        progressWheel.setProgress(0);
        progressWheel.setText(err);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final WifiP2pDevice p2pDevice = mPeerListAdapter.getItem(position);
        parent.setSelection(position);

        get(view, R.id.inflate_peer_item_switcher, ViewSwitcher.class).showNext();
        get(view, R.id.inflate_peer_item_progress, ProgressBar.class).setIndeterminate(true);

        //mActivity.connectToDevice(p2pDevice);

        /*
        progressBar.clearAnimation();
        ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", 0, progressBar.getMax());
        animation.setDuration(1000); // 1 second
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.start();
        */

    }



    private class PeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        public PeerListAdapter(final Context context, final int resource) {
            super(context, resource, new ArrayList<WifiP2pDevice>());
        }

        public void swapPeers(List<WifiP2pDevice> devices) {
            clear();
            addAll(devices);
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            final WifiP2pDevice device = getItem(position);

            if (v == null) {
                final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                v = inflater.inflate(R.layout.inflate_peer_item, parent, false);
            }

            final String text1 = String.format("%s", device.deviceName);
            get(v, R.id.inflate_peer_item_text1, TextView.class).setTextKeepState(text1);

            final String hostOwnerPostfix = device.isGroupOwner() ? " (host)" : "";
            final String text2 = String.format("%s%s", device.deviceAddress, hostOwnerPostfix);
            get(v, R.id.inflate_peer_item_text2, TextView.class).setTextKeepState(text2);

            return v;
        }
    }
}
