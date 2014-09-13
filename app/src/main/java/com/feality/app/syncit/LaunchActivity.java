package com.feality.app.syncit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LaunchActivity extends Activity implements WifiP2pActionListener {

    private static final String LOG_TAG = LaunchActivity.class.getSimpleName();
    public static final String FRAGMENT_DISCOVERY = "fragment_discovery";
    public static final int DEFAULT_PORT = 8081;

    private final IntentFilter mIntentFilter = new IntentFilter();

    private PeerToPeerHandler mPeerToPeerHandler;
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

        mPeerToPeerHandler = new PeerToPeerHandler(mP2PManager, mChannel, fragment, this);
        registerReceiver(mPeerToPeerHandler, mIntentFilter);

        mPeerToPeerHandler.discoverPeers();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mPeerToPeerHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent serverIntent = new Intent(this, SyncServer.class);
        serverIntent.putExtra(SyncServer.INTENT_EXTRA_ACTION, SyncServer.ACTION_STOP);
        startService(serverIntent);

        Intent clientIntent = new Intent(this, SyncClient.class);
        clientIntent.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_STOP);
        startService(clientIntent);
    }

    public void distributeData(final Uri uri) {
        Intent connectIntent = new Intent(this, SyncClient.class);
        connectIntent.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_SEND);
        connectIntent.putExtra(SyncClient.INTENT_EXTRA_FILE, uri);
        startService(connectIntent);
    }

    @Override
    public void onGroupAvailable(final WifiP2pGroup info) {
        Log.d(LOG_TAG, "onGroupAvailable() "+ info);

        if (info.getClientList().size() == 0){
            Log.d(LOG_TAG, "onGroupAvailable() IGNORED! 0 peers");
            return;
        }

        if (info.isGroupOwner()) {
            Intent serverIntent = new Intent(this, SyncServer.class);
            serverIntent.putExtra(SyncServer.INTENT_EXTRA_ACTION, SyncServer.ACTION_START);
            serverIntent.putExtra(SyncServer.INTENT_EXTRA_PORT, DEFAULT_PORT);
            startService(serverIntent);

            ProgressDialog.show(this, "Waiting", "Waiting for client connection", true, true);
        } else {
            Intent clientIntent = new Intent(this, SyncClient.class);
            clientIntent.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_START);
            clientIntent.putExtra(SyncClient.INTENT_EXTRA_ADDRESS, info.getOwner().deviceAddress);
            clientIntent.putExtra(SyncClient.INTENT_EXTRA_PORT, DEFAULT_PORT);
            startService(clientIntent);

            ProgressDialog.show(this, "Connecting", "Connecting to group owner", true, true);
        }

    }

    public void connectToDevice(final WifiP2pDevice p2pDevice) {
        Intent clientIntent = new Intent(this, SyncClient.class);
        clientIntent.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_START);
        clientIntent.putExtra(SyncClient.INTENT_EXTRA_ADDRESS, p2pDevice.deviceAddress);
        clientIntent.putExtra(SyncClient.INTENT_EXTRA_PORT, DEFAULT_PORT);
        startService(clientIntent);
    }

    @Override
    public void onConnectedToDevice(final WifiP2pDevice p2pDevice) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a File to Upload"), 123);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager", Toast.LENGTH_SHORT).show();
        }
    }

    public void onDeviceSelected(final WifiP2pDevice p2pDevice) {
        mPeerToPeerHandler.onDeviceSelected(p2pDevice);
    }

    public static class PeerToPeerHandler extends BroadcastReceiver implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

        private final List<WifiP2pDevice> mPeers = new ArrayList<WifiP2pDevice>();
        private final WifiP2pUiListener mWifiP2pUiListener;
        private final WifiP2pManager mWifiP2pManager;
        private final WifiP2pManager.Channel mChannel;
        private final WifiP2pActionListener mWifiP2pActionListener;
        private final WifiP2pConfig mConnectedConfig = new WifiP2pConfig();

        private PeerToPeerHandler(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, WifiP2pUiListener uiListener, WifiP2pActionListener actionListener) {
            this.mWifiP2pManager = wifiP2pManager;
            this.mChannel = channel;
            this.mWifiP2pUiListener = uiListener;
            this.mWifiP2pActionListener = actionListener;
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

                mWifiP2pUiListener.showWifiStateChange(state, wifiStates[state]);

                Log.d(LOG_TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

                // Request available mPeers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    mWifiP2pManager.requestPeers(mChannel, this);
                }

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

                    requestGroupAfterConnected();
                } else {
                    // Reset stuff
                }

                Log.d(LOG_TAG, "wifiInfo: " +wifiP2pInfo);
                Log.d(LOG_TAG, "networkInfo: "+networkInfo);

            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

                Log.d(LOG_TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            }
        }

        public void discoverPeers() {
            mWifiP2pUiListener.onDiscoveringPeers();
            mWifiP2pManager.discoverPeers(mChannel, new LoggingListener("discoverPeers"));
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
                mWifiP2pUiListener.updatePeerList(mPeers);
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
                //mWifiP2pActionListener.onGroupOwnerAvailable(info);
            } else if (info.groupFormed) {
                // The other device acts as the client. In this case,
                // you'll want to create a client thread that connects to the group
                // owner.
                //mWifiP2pActionListener.onGroupOwnerAvailable(info);
            } else {
                // No group yet
            }
        }

        public void onDeviceSelected(final WifiP2pDevice p2pDevice) {
            mConnectedConfig.deviceAddress = p2pDevice.deviceAddress;
            mConnectedConfig.wps.setup = WpsInfo.PBC;
            mConnectedConfig.groupOwnerIntent = 0;
            final String device = String.format("%s (%s)", p2pDevice.deviceAddress, p2pDevice.deviceName);

            mWifiP2pManager.connect(mChannel, mConnectedConfig, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.w(LOG_TAG, "Successfully connected to "+ device);
                    requestGroupAfterConnected();
                }

                @Override
                public void onFailure(final int reason) {
                    if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                        mWifiP2pUiListener.onP2PNotSupported();
                    } else if (reason == WifiP2pManager.BUSY) {
                        Log.d(LOG_TAG, "Failed to connect to "+device+ ", device busy");
                    } else {
                        Log.d(LOG_TAG, "Failed to connect to "+device + ", reason:" +  reason);
                    }
                }
            });
        }

        private void requestGroupAfterConnected() {
            mWifiP2pManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(final WifiP2pGroup group) {
                    mWifiP2pActionListener.onGroupAvailable(group);
                }
            });

            //mWifiP2pActionListener.onConnectedToDevice(p2pDevice);
        }

        private void startServiceRegistration() {
            //  Create a string map containing information about your service.
            Map record = new HashMap();
            record.put("listenport", String.valueOf(DEFAULT_PORT));
            record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
            record.put("available", "visible");

            // Service information.  Pass it an instance name, service type
            // _protocol._transportlayer , and the map containing
            // information other devices will want once they connect to this one.
            WifiP2pDnsSdServiceInfo serviceInfo =
                    WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

            // Add the local service, sending the service info, network channel,
            // and listener that will be used to indicate success or failure of
            // the request.
            mWifiP2pManager.addLocalService(mChannel, serviceInfo, new LoggingListener("addLocalService"));
        }

        final HashMap<String, String> buddies = new HashMap<String, String>();

        private void discoverService() {
            WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
                @Override
                /* Callback includes:
                 * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
                 * record: TXT record dta as a map of key/value pairs.
                 * device: The device running the advertised service.
                 */
                public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                    Log.d(LOG_TAG, "DnsSdTxtRecord available -" + record.toString());
                    buddies.put(device.deviceAddress, String.valueOf(record.get("buddyname")));
                }
            };
        }

        private static class LoggingListener implements WifiP2pManager.ActionListener {
            private String mMethodName;
            private static String[] ERRORS = {
                    "ERROR",
                    "P2P_UNSUPPORTED",
                    "BUSY"
            };
            public LoggingListener(final String methodName) {
                mMethodName = methodName;
            }

            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, mMethodName + ":onSuccess()");
            }

            @Override
            public void onFailure(final int reason) {
                Log.d(LOG_TAG, mMethodName + ":onFailure("+ ERRORS[reason] +")");
            }
        }
    }

}
