package com.feality.app.syncit;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;

import java.util.List;

/**
 * Created by Filip on 2014-09-12.
 */
public interface WifiP2pUiListener {
    public void onDiscoveringPeers();
    public void onP2PNotSupported();
    public void showWifiStateChange(int status, String statusText);
    public void updatePeerList(List<WifiP2pDevice> peers);
    public void onGroupOwnerAvailable(WifiP2pInfo info);
}
