package com.feality.app.syncit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;

import com.esotericsoftware.minlog.Log;
import com.feality.app.syncit.fragments.DiscoveryFragment;
import com.feality.app.syncit.fragments.MediaFragment;
import com.feality.app.syncit.net.NetworkSyncService;
import com.feality.app.syncit.net.PeerToPeerHandler;

import java.net.InetAddress;


public class LaunchActivity extends Activity implements WifiP2pActionListener {
    private static final String LOG_TAG = LaunchActivity.class.getSimpleName();

    private static final String FRAGMENT_DISCOVERY = "fragment_discovery";
    private static final String FRAGMENT_MEDIA = "fragment_media";

    private final IntentFilter mP2pIntentFilter = new IntentFilter();
    private final IntentFilter mServiceIntentFilter = new IntentFilter();

    private MessageReceiver mMessageReceiver;
    private PeerToPeerHandler mPeerToPeerHandler;

    private WifiP2pManager mP2PManager;
    private WifiP2pManager.Channel mChannel;
    private FragmentManager mFragmentManager;
    private ProgressDialog mProgressDialog;

    private DiscoveryFragment mDiscoveryFragment;
    private MediaFragment mMediaFragment;

    public <T> T get(int id, Class<T> clz){
        return clz.cast(findViewById(id));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFragmentManager = getFragmentManager();

        showDiscoveryFragment();

        initWifiP2PReceivers();

        Log.set(Log.LEVEL_TRACE);
        startService(NetworkSyncService.Message.startServer(this));
    }

    private void showDiscoveryFragment() {
        Fragment fragment = mFragmentManager.findFragmentByTag(FRAGMENT_DISCOVERY);
        if (fragment == null) {
            fragment = Fragment.instantiate(this, DiscoveryFragment.class.getName());
        }
        mFragmentManager
            .beginTransaction()
            .replace(R.id.main_fragment_placeholder, fragment, FRAGMENT_DISCOVERY)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .commit();
    }

    private void showMediaFragment() {
        Fragment fragment = mFragmentManager.findFragmentByTag(FRAGMENT_MEDIA);
        if (fragment == null) {
            fragment = MediaFragment.instantiate(this, MediaFragment.class.getName());
        }
        mFragmentManager
            .beginTransaction()
            .replace(R.id.main_fragment_placeholder, fragment, FRAGMENT_MEDIA)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .commit();
    }

    private void initWifiP2PReceivers() {
        mP2PManager = WifiP2pManager.class.cast(getSystemService(WIFI_P2P_SERVICE));

        //  Indicates a change in the Wi-Fi P2P status.
        mP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates a change in the list of available mPeers.
        mP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        mP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        mP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Indicates that a client have connected
        mServiceIntentFilter.addAction(MessageReceiver.ACTION_ON_CONNECTED);
        mServiceIntentFilter.addAction(MessageReceiver.ACTION_ON_DISCONNECTED);
        mServiceIntentFilter.addAction(MessageReceiver.ACTION_ON_RTT);

        mChannel = mP2PManager.initialize(this, getMainLooper(), null);

        mMessageReceiver = new MessageReceiver();
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();

        mDiscoveryFragment = DiscoveryFragment.class.cast(
            mFragmentManager.findFragmentByTag(FRAGMENT_DISCOVERY)
        );

        mMediaFragment = MediaFragment.class.cast(
            mFragmentManager.findFragmentByTag(FRAGMENT_MEDIA)
        );

        mPeerToPeerHandler = new PeerToPeerHandler(mP2PManager, mChannel, mDiscoveryFragment, this);
        mPeerToPeerHandler.discoverPeers();

        registerReceiver(mPeerToPeerHandler, mP2pIntentFilter);
        registerReceiver(mMessageReceiver, mServiceIntentFilter);
    }

    @Override
    public void onPause() {
        unregisterReceiver(mPeerToPeerHandler);
        unregisterReceiver(mMessageReceiver);
        mDiscoveryFragment = null;
        mMediaFragment = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mPeerToPeerHandler.tearDown();
        startService(NetworkSyncService.Message.stopServer(this));
        startService(NetworkSyncService.Message.disconnectClient(this));
        super.onDestroy();
    }

    public void distributeData(final Uri uri) {
        startService(NetworkSyncService.Message.sendFile(this, uri));
    }

    @Override
    public void onServiceDetected(final NsdServiceInfo serviceP2pDevice) {
        mProgressDialog.setMessage("Service detected" +
                "\nname: " + serviceP2pDevice.getServiceName() +
                "\ntype: " + serviceP2pDevice.getServiceType() +
                "\nhost: " + serviceP2pDevice.getHost() + ":" + serviceP2pDevice.getPort());
        mProgressDialog.dismiss();
    }

    @Override
    public void onConnectedToDevice(final WifiP2pInfo info) {
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog.show(this, "Initializing",
                    "Initializing network components", true, false);
        }

        if (info.groupFormed) {
            if (info.isGroupOwner){
                mProgressDialog.setTitle("Hosting Wifi-Direct group");
                mProgressDialog.setMessage(
                        "Waiting for incoming connection..." +
                        "\nip: " + info.groupOwnerAddress);
                // Wait for incoming connection

            } else {
                mProgressDialog.setTitle("Joining Wifi-Direct group");
                mProgressDialog.setMessage(
                        "Connecting to group owner..." +
                                "\nip: " + info.groupOwnerAddress);
                final String address = info.groupOwnerAddress.getHostAddress();
                startService(NetworkSyncService.Message.connectClient(this, address));
            }
        } else {
            mProgressDialog.setTitle("Waiting for group");
            mProgressDialog.setMessage("Waiting for group to be formed...");
        }
    }

    @Override
    public void onDisconnected() {
        dismissProgressDialog();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    public void onDeviceSelected(final WifiP2pDevice p2pDevice) {
        mPeerToPeerHandler.connectToP2pDevice(p2pDevice);
        String name = String.format("%s (%s)", p2pDevice.deviceName, p2pDevice.deviceAddress);
        mProgressDialog = ProgressDialog.show(this, "Connecting",
                "Connecting to device " + name, true, false);
    }

    public static class Message {

        public static Intent onDisconnected() {
            return new Intent(MessageReceiver.ACTION_ON_DISCONNECTED);
        }

        public static Intent onConnected() {
            return new Intent(MessageReceiver.ACTION_ON_CONNECTED);
        }

        public static Intent onRttCalculated(long rttMs) {
            Intent i = new Intent(MessageReceiver.ACTION_ON_RTT);
            i.putExtra(MessageReceiver.EXTRA_AVG_RTT, rttMs);
            return i;
        }
    }

    private class MessageReceiver extends BroadcastReceiver {

        private final static String ACTION_ON_CONNECTED = "MessageReceiver.ACTION_ON_CONNECTED";
        private final static String ACTION_ON_DISCONNECTED = "MessageReceiver.ACTION_ON_DISCONNECTED";
        private final static String ACTION_ON_RTT = "MessageReceiver.ACTION_ON_RTT";

        private final static String EXTRA_MESSAGE = "MessageReceiver.EXTRA_MESSAGE";
        private final static String EXTRA_AVG_RTT = "MessageReceiver.EXTRA_AVG_RTT";

        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();

            float rtt = intent.getFloatExtra(EXTRA_AVG_RTT, 0);
            if (ACTION_ON_CONNECTED.equals(action)) {
                android.util.Log.w(LOG_TAG, "ACTION_ON_CONNECTED");
                showMediaFragment();
                dismissProgressDialog();
            }

            if (ACTION_ON_DISCONNECTED.equals(action)) {
                android.util.Log.w(LOG_TAG, "ACTION_ON_DISCONNECTED");
                showDiscoveryFragment();
                dismissProgressDialog();
            }

            if (ACTION_ON_RTT.equals(action) && mMediaFragment != null) {
                android.util.Log.w(LOG_TAG, "ACTION_ON_RTT");
                mMediaFragment.showRoundTripTime(rtt);
            }
        }
    }

}
