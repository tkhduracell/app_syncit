package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.util.InputStreamSender;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Filip on 2014-09-12.
 */
public class SyncServer extends IntentService {

    private static final String LOG_TAG = SyncServer.class.getSimpleName();

    private Server mServer;
    private Client mClient;

    public static class IntentBuilder {
        public static Intent start(Context c, int port){
            Intent i = new Intent(c, SyncServer.class);
            i.putExtra(SyncServer.INTENT_EXTRA_ACTION, SyncServer.ACTION_START);
            i.putExtra(SyncServer.INTENT_EXTRA_PORT, port);
            return i;
        }
        public static Intent stop(Context c) {
            Intent i = new Intent(c, SyncServer.class);
            i.putExtra(SyncServer.INTENT_EXTRA_ACTION, SyncServer.ACTION_STOP);
            return i;
        }
    }


    private static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    private static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";

    private static final int ACTION_START = 0;
    private static final int ACTION_STOP = 1;

    public SyncServer() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());

        switch (action) {
            case ACTION_START:
                mServer = new Server(54885, 54887);
                mServer.getKryo().register(NetworkActions.RTTFrame.class);
                mServer.start();
                mServer.addListener(new Listener() {
                    public void received (Connection connection, Object object) {
                        if (object instanceof NetworkActions.RTTFrame) {
                            connection.sendTCP(object);
                        }
                    }
                });
                break;
            case ACTION_STOP:
                mServer.close();
                break;
            default:
                Log.wtf(LOG_TAG, "Unknown action: " + action);
                break;
        }
    }

    private static class MediaPlay {

    }
}
