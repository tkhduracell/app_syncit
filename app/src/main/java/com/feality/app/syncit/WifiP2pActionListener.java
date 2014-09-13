package com.feality.app.syncit;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;

/**
 * Created by Filip on 2014-09-12.
 */
public interface WifiP2pActionListener {
    public void onGroupAvailable(WifiP2pGroup info);
    public void onConnectedToDevice(WifiP2pDevice p2pDevice);
}