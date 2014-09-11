package com.feality.app.syncit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
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
        setContentView(R.layout.activity_main);

        mFragmentManager = getFragmentManager();

        mFragmentManager
                .beginTransaction()
                .replace(R.id.main_fragment_placeholder, Fragment.instantiate(this, DiscoveryFragment.class.getName()), FRAGMENT_DISCOVERY)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .commit();

        initWifiP2PReceivers();
    }

    private void initWifiP2PReceivers() {
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

        mReceiver.discoverPeers();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public void distributeData(final Uri uri) {

    }

    public void connectToDevice(final WifiP2pDevice p2pDevice) {
        mReceiver.connectToDevice(p2pDevice);
    }

    public interface WifiP2PListener {
        public void onDiscoveringPeers();
        public void onP2PNotSupported();
        public void showWifiStateChange(int status, String statusText);
        public void updatePeerList(List<WifiP2pDevice> peers);
        public void onGroupOwnerAvailable(WifiP2pInfo info);
    }

    public static class WiFiDirectBroadcastReceiver extends BroadcastReceiver implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

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

                final String[] wifiStates = {
                    "WIFI_STATE_DISABLING",
                    "WIFI_STATE_DISABLED",
                    "WIFI_STATE_ENABLING",
                    "WIFI_STATE_ENABLED",
                    "WIFI_STATE_UNKNOWN"
                };

                mWifiP2PListener.showWifiStateChange(state, wifiStates[state]);

                Log.d(LOG_TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

                // Request available mPeers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                mWifiP2pManager.requestPeers(mChannel, this);

                Log.d(LOG_TAG, "WIFI_P2P_PEERS_CHANGED_ACTION - P2P peers changed, requesting peers");

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

                // Connection state changed!  We should probably do something about that
                Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");
                WifiP2pInfo wifiP2pInfo = WifiP2pInfo.class.cast(
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                );

                NetworkInfo networkInfo = NetworkInfo.class.cast(
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                );

                if (networkInfo.isConnected()) {
                    // We are connected with the other device, request connection
                    // info to find group owner IP

                    mWifiP2pManager.requestConnectionInfo(mChannel, this);
                }
                Log.d(LOG_TAG, "wifiInfo: " +wifiP2pInfo);
                Log.d(LOG_TAG, "networkInfo: "+networkInfo);
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

                Log.d(LOG_TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            }
        }

        public void discoverPeers() {
            mWifiP2PListener.onDiscoveringPeers();
            mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(LOG_TAG, "Discovering peers: onSuccess()");
                    mWifiP2pManager.requestConnectionInfo(mChannel, WiFiDirectBroadcastReceiver.this);
                }

                @Override
                public void onFailure(final int reason) {
                    if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                        mWifiP2PListener.onP2PNotSupported();
                    } else if (reason == WifiP2pManager.BUSY) {
                        Log.d(LOG_TAG, "Discovering peers: Busy busy doing nothing at all");
                    } else {
                        Log.d(LOG_TAG, "Discovering peers: onFailure(" + reason + ")");
                    }
                }
            });
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            final Collection<WifiP2pDevice> deviceList = peers.getDeviceList();
            final int nbrPeers = deviceList.size();

            boolean isEqual = mPeers.containsAll(deviceList) && deviceList.containsAll(mPeers);

            Log.d(LOG_TAG, "Wifi-Direct: found " + nbrPeers + " peers, updating: " + !isEqual);

            if (!isEqual) {
                mPeers.clear();
                mPeers.addAll(deviceList);
                mWifiP2PListener.updatePeerList(mPeers);

                if (nbrPeers == 0) {
                    discoverPeers();
                }
            }
        }

        @Override
        public void onConnectionInfoAvailable(final WifiP2pInfo info) {
            Log.d(LOG_TAG, "onConnectionInfoAvailable: " + info.toString());
            // After the group negotiation, we can determine the group owner.
            if (info.groupFormed && info.isGroupOwner) {
                // Do whatever tasks are specific to the group owner.
                // One common case is creating a server thread and accepting
                // incoming connections.
                mWifiP2PListener.onGroupOwnerAvailable(info);
            } else if (info.groupFormed) {
                // The other device acts as the client. In this case,
                // you'll want to create a client thread that connects to the group
                // owner.
                mWifiP2PListener.onGroupOwnerAvailable(info);
                Socket s = new Socket();
                try {
                    s.connect(new InetSocketAddress(info.groupOwnerAddress, 9090));
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to connect to host using post 9090", e);
                }

            } else {
                // Group not created so do create
                mWifiP2pManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(LOG_TAG, "Successfully created group");
                    }

                    @Override
                    public void onFailure(final int reason) {
                        Log.d(LOG_TAG, "Error created group: " + reason);
                    }
                });
            }
        }

        public void connectToDevice(final WifiP2pDevice p2pDevice) {

        }
    }

}
