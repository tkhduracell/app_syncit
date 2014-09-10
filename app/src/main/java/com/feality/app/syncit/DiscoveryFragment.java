package com.feality.app.syncit;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.todddavies.components.progressbar.ProgressWheel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Filip on 2014-09-10.
 */
public class DiscoveryFragment extends SmarterFragment implements LaunchActivity.WifiP2PListener {

    private ListView mListView;
    private ViewSwitcher mViewSwitcher;
    private ProgressWheel mProgressWheel;
    private PeerListAdapter mPeerListAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discover, container, false);

        mViewSwitcher = ViewSwitcher.class.cast(v);
        mProgressWheel = get(v, R.id.discovery_pw_spinner, ProgressWheel.class);
        mListView = get(v, R.id.discovery_list, ListView.class);

        mPeerListAdapter = new PeerListAdapter();
        mListView.setAdapter(mPeerListAdapter);

        return v;
    }

    @Override
    public void isWifiP2PEnabled(boolean enabled) {
        int colorResourceId = enabled ? R.color.light_green : R.color.light_grey;
        String text = enabled ? "Wifi enabled" : "Wifi disabled";
        mProgressWheel.setText(text);
        if (enabled) {
            if (mProgressWheel.isSpinning()) {
                mProgressWheel.spin();
            }
        } else {
            mProgressWheel.stopSpinning();
        }
    }

    @Override
    public void onDiscoveringPeers() {
        if (!mProgressWheel.isSpinning()) {
            mProgressWheel.spin();
        }
    }

    @Override
    public void updatePeerList(List<WifiP2pDevice> peers) {

    }

    public void showError(String err) {
        final ProgressWheel progressWheel = get(R.id.discovery_pw_spinner, ProgressWheel.class);
        progressWheel.stopSpinning();
        progressWheel.setProgress(0);
        progressWheel.setText(err);
    }

    private class PeerListAdapter extends BaseAdapter {
        private List<WifiP2pDevice> mDeviceList = new ArrayList<WifiP2pDevice>();

        public void swapPeers(List<WifiP2pDevice> devices) {
            mDeviceList.clear();
            mDeviceList.addAll(devices);
        }

        @Override
        public int getCount() {
            return mDeviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDeviceList.get(position).describeContents();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            final WifiP2pDevice device = mDeviceList.get(position);

            View v = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);

            get(v, android.R.id.text1, TextView.class).setTextKeepState();

            return null;
        }
    }
}
