package com.feality.app.syncit;

import android.net.wifi.p2p.WifiP2pInfo;

/**
 * Created by Filip on 2014-09-12.
 */
public interface WifiP2pActionListener {
    public void onGroupOwnerAvailable(WifiP2pInfo info);
}