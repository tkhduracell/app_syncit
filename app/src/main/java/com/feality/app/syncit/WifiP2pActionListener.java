package com.feality.app.syncit;

import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;

/**
 * Created by Filip on 2014-09-12.
 */
public interface WifiP2pActionListener {
    public void onDeviceSelected(final WifiP2pDevice p2pDevice);
    public void onConnectedToDevice(WifiP2pInfo wifiP2pInfo);
    public void onConnectedToDevice(WifiP2pDevice p2pDevice, final WifiP2pInfo info);
    public void onServiceDetected(NsdServiceInfo serviceP2pDevice);

}