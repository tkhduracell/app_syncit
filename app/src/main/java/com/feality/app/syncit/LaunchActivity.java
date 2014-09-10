package com.feality.app.syncit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


public class LaunchActivity extends Activity {

    private static final String LOG_TAG = LaunchActivity.class.getSimpleName();
    public static final String FRAGMENT_DISCOVERY = "fragment_discovery";

    private final IntentFilter mIntentFilter = new IntentFilter();

    private WiFiDirectBroadcastReceiver mReceiver;
    private WifiP2pManager mP2PManager;
    private WifiP2pManager.Channel mChannel;
    private FragmentManager mFragmentManager;

    public <T> T get(int id, Class<T> clz){
        return clz.cast(findViewById(id));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_discover);

        mFragmentManager = getFragmentManager();

        mFragmentManager
                .beginTransaction()
                .add(R.id.main_fragment_placeholder, Fragment.instantiate(this, DiscoveryFragment.class.getName()), FRAGMENT_DISCOVERY)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .commit();

        startWifiPeerDiscovery();
    }

    private void startWifiPeerDiscovery() {
        //  Indicates a change in the Wi-Fi P2P status.
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available mPeers.
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mP2PManager = WifiP2pManager.class.cast(getSystemService(WIFI_P2P_SERVICE));

        mChannel = mP2PManager.initialize(this, getMainLooper(), null);
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();

        final DiscoveryFragment fragment = DiscoveryFragment.class.cast(
                mFragmentManager.findFragmentByTag(FRAGMENT_DISCOVERY));

        mReceiver = new WiFiDirectBroadcastReceiver(fragment, mP2PManager, mChannel);
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public interface WifiP2PListener {
        public void isWifiP2PEnabled(boolean enabled);
        public void onDiscoveringPeers();
        public void updatePeerList(List<WifiP2pDevice> peers);
    }

    public static class WiFiDirectBroadcastReceiver extends BroadcastReceiver implements WifiP2pManager.PeerListListener {

        private final List<WifiP2pDevice> mPeers = new ArrayList<WifiP2pDevice>();
        private final WifiP2PListener mWifiP2PListener;
        private final WifiP2pManager mWifiP2pManager;
        private final WifiP2pManager.Channel mChannel;

        private WiFiDirectBroadcastReceiver(WifiP2PListener mWifiP2PListener, WifiP2pManager mWifiP2pManager, WifiP2pManager.Channel mChannel) {
            this.mWifiP2PListener = mWifiP2PListener;
            this.mWifiP2pManager = mWifiP2pManager;
            this.mChannel = mChannel;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    mWifiP2PListener.isWifiP2PEnabled(true);
                } else {
                    mWifiP2PListener.isWifiP2PEnabled(false);
                }

                Log.d(LOG_TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

                // Request available mPeers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPeers(mChannel, this);
                }
                Log.d(LOG_TAG, "WIFI_P2P_PEERS_CHANGED_ACTION - P2P peers changed");

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

                // Connection state changed!  We should probably do something about that
                Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");

            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

                Log.d(LOG_TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            }
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            mPeers.clear();
            mPeers.addAll(peers.getDeviceList());
            mWifiP2PListener.updatePeerList(mPeers);
        }
    }

}
