package com.feality.app.syncit.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.util.Log;

import com.feality.app.syncit.LaunchActivity;
import com.feality.app.syncit.WifiP2pActionListener;
import com.feality.app.syncit.WifiP2pUiListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Filip on 2014-09-14.
 */
public class PeerToPeerHandler extends BroadcastReceiver implements WifiP2pManager.PeerListListener {
    private static final String LOG_TAG = PeerToPeerHandler.class.getSimpleName();
    public static final int PEER_UPDATE_PERIOD = 30000;

    private final List<WifiP2pDevice> mPeers = new ArrayList<WifiP2pDevice>();

    private boolean mDiscoveryRunning = false;
    private boolean mPauseDiscovery = false;

    private final WifiP2pUiListener mWifiP2pUiListener;
    private final WifiP2pManager mWifiP2pManager;
    private final WifiP2pManager.Channel mChannel;
    private final WifiP2pActionListener mWifiP2pActionListener;
    private final WifiP2pConfig mConnectedConfig = new WifiP2pConfig();

    private final Timer mTimer;
    private WifiP2pDnsSdServiceRequest mServiceRequest;
    private WifiP2pDnsSdServiceInfo mServiceInfo;

    public PeerToPeerHandler(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, WifiP2pUiListener uiListener, WifiP2pActionListener actionListener) {
        this.mWifiP2pManager = wifiP2pManager;
        this.mChannel = channel;
        this.mWifiP2pUiListener = uiListener;
        this.mWifiP2pActionListener = actionListener;
        this.mTimer = new Timer();
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
            if (mWifiP2pUiListener != null) {
                mWifiP2pUiListener.showWifiStateChange(state, wifiStates[state]);
            }

            Log.d(LOG_TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // Request available mPeers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                WifiP2pDeviceList p2pDeviceList = WifiP2pDeviceList.class.cast(
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)
                );
                Log.d(LOG_TAG, "WIFI_P2P_PEERS_CHANGED_ACTION - New peer list");
                onPeersAvailable(p2pDeviceList);
            } else {
                Log.d(LOG_TAG, "WIFI_P2P_PEERS_CHANGED_ACTION - Legacy device -> requesting peers");
                mWifiP2pManager.requestPeers(mChannel, this);
            }

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            // Connection state changed!  We should probably do something about that
            Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");

            WifiP2pInfo wifiP2pInfo = WifiP2pInfo.class.cast(
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
            );

            NetworkInfo networkInfo = NetworkInfo.class.cast(
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
            );

            if (wifiP2pInfo.groupFormed && networkInfo.isConnected()) {
                // We are connected with the other device, request connection
                // info to find group owner IP
                Log.w(LOG_TAG, "NetworkInfo we are connected!");
                mPauseDiscovery = true;
                mWifiP2pActionListener.onConnectedToDevice(wifiP2pInfo);
            } else {
                // Reset stuff
                Log.w(LOG_TAG, "NetworkInfo we are NOT connected!");
                mPauseDiscovery = false;
                mWifiP2pActionListener.onDisconnected();
            }

            // Log.d(LOG_TAG, String.valueOf(wifiP2pInfo));
            // Log.d(LOG_TAG, String.valueOf(networkInfo));

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            Log.d(LOG_TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
        }
    }

    public void discoverPeers() {
        mDiscoveryRunning = true;
        if (mWifiP2pUiListener != null) {
            mWifiP2pUiListener.onDiscoveringPeers();
        }
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (mPauseDiscovery) return;
                mWifiP2pManager.discoverPeers(mChannel, new DebugListener("discoverPeers(p=10sec)"));
            }
        }, 500, PEER_UPDATE_PERIOD);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        final Collection<WifiP2pDevice> deviceList = peers.getDeviceList();
        final int nbrPeers = deviceList.size();

        boolean isEqual = mPeers.containsAll(deviceList) && deviceList.containsAll(mPeers);

        Log.d(LOG_TAG, "PeerList: size=" + nbrPeers + ", wasUpdated?=" + !isEqual);

        if (!isEqual) {
            mPeers.clear();
            mPeers.addAll(deviceList);
            mWifiP2pUiListener.updatePeerList(mPeers);
        }
    }

    public void connectToP2pDevice(final WifiP2pDevice p2pDevice) {
        mConnectedConfig.deviceAddress = p2pDevice.deviceAddress;
        mConnectedConfig.wps.setup = WpsInfo.PBC;
        final String device = String.format("%s (%s)", p2pDevice.deviceAddress, p2pDevice.deviceName);

        mWifiP2pManager.connect(mChannel, mConnectedConfig, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.w(LOG_TAG, "Successfully connected to " + device);
                onConnected(p2pDevice);
            }

            @Override
            public void onFailure(final int reason) {
                if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                    mWifiP2pUiListener.onP2PNotSupported();
                } else if (reason == WifiP2pManager.BUSY) {
                    Log.d(LOG_TAG, "Failed to connect to "+device+ ", device busy");
                } else {
                    Log.d(LOG_TAG, "Failed to connect to "+device + ", reason:" +  reason);
                    retryConnection(p2pDevice);
                }
            }
        });
    }

    private void retryConnection(final WifiP2pDevice p2pDevice) {
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mWifiP2pManager.cancelConnect(mChannel, new DebugListener("cancelConnect"));
                connectToP2pDevice(p2pDevice);
            }
        }, 5000);
    }

    private void onConnected(final WifiP2pDevice p2pDevice) {
        mPauseDiscovery = true;
        mWifiP2pManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(final WifiP2pInfo info) {
                Log.d(LOG_TAG, "Requested ConnectionInfo=" + info);
                if (info.groupFormed) {
                    mWifiP2pActionListener.onConnectedToDevice(info);
                } else {
                    final WifiP2pManager.ConnectionInfoListener listener = this;
                    retryConnection(p2pDevice);
                }
            }
        });
    }

    public void registerService() {
        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("listenport", String.valueOf(8081));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance("_syncit_dnssd", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mWifiP2pManager.addLocalService(mChannel, mServiceInfo, new DebugListener("addLocalService"));
    }

    final HashMap<String, String> buddies = new HashMap<String, String>();

    public void startServiceDiscovery() {
        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
                /* Callback includes:
                 * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
                 * record: TXT record dta as a map of key/value pairs.
                 * device: The device running the advertised service.
                 */
            public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(LOG_TAG, "DnsSdTxtRecord:" + fullDomain + ", " + record.toString());
                buddies.put(device.deviceAddress, fullDomain);
            }
        };

        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                WifiP2pDevice resourceType) {

                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                resourceType.deviceName =
                        buddies.containsKey(resourceType.deviceAddress) ?
                                buddies.get(resourceType.deviceAddress) :
                                resourceType.deviceName;


                //mWifiP2pActionListener.onServiceDetected(resourceType);

                Log.d(LOG_TAG, "onBonjourServiceAvailable: " + instanceName);
            }
        };

        mWifiP2pManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);

        mServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mWifiP2pManager.addServiceRequest(mChannel, mServiceRequest, new DebugListener("addServiceRequest"));
        mWifiP2pManager.discoverServices(mChannel, new DebugListener("discoverServices"));
    }

    public void tearDown() {
        if (mWifiP2pManager == null) return;

        mPauseDiscovery = true;
        mTimer.cancel();

        try {
            mWifiP2pManager.cancelConnect(mChannel, new DebugListener("cancelConnect"));
        } catch (IllegalArgumentException ignored) {} //No connection running

        try {
            mWifiP2pManager.removeGroup(mChannel, new DebugListener("removeGroup"));
        } catch (IllegalArgumentException ignored) {} //No group running

        try {
            mWifiP2pManager.stopPeerDiscovery(mChannel, new DebugListener("stopPeerDiscovery"));
        } catch (IllegalArgumentException ignored) {} //No discovery running

        try {
            mWifiP2pManager.removeServiceRequest(mChannel, mServiceRequest, new DebugListener("removeServiceRequest"));
        } catch (IllegalArgumentException ignored) {} //No service request running

        try {
            mWifiP2pManager.removeLocalService(mChannel, mServiceInfo, new DebugListener("removeLocalService"));
        } catch (IllegalArgumentException ignored) {} //No service running
    }

    private static class DebugListener implements WifiP2pManager.ActionListener {
        private String mMethodName;
        private static String[] ERRORS = {
                "ERROR",
                "P2P_UNSUPPORTED",
                "BUSY",
                "NO_SERVICE_REQUESTS"
        };
        public DebugListener(final String methodName) {
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
