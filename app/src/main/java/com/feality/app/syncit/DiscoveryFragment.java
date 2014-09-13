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

        mTitleView = get(v, R.id.discovery_title, TextView.class);

        return v;
    }

    @Override
    public void onAttach(final Activity activity) {
        mActivity = LaunchActivity.class.cast(activity);
        super.onAttach(activity);
    }

    @Override
    public void showWifiStateChange(int state, String stateText) {
        if (state == 2 || state == 3) {
            // We are cool
        } else {
            mViewSwitcher.setDisplayedChild(CHILD_FIRST);
            mProgressWheel.setText(stateText);
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
        if (peers.isEmpty()) {
            showLoadingSpinner();
        } else {
            showPeerList();
        }
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
    public void onGroupOwnerAvailable(final WifiP2pInfo info) {}

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

        get(view, R.id.inflate_peer_item_switcher, ViewSwitcher.class).setDisplayedChild(CHILD_SECOND);
        get(view, R.id.inflate_peer_item_progress, ProgressBar.class).setIndeterminate(true);

        mActivity.onDeviceSelected(p2pDevice);

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

            final String text2 = String.format("%s", device.deviceAddress);
            get(v, R.id.inflate_peer_item_text2, TextView.class).setTextKeepState(text2);

            return v;
        }
    }
}
