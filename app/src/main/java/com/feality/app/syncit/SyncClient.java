package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Filip on 2014-09-12.
 */
public class SyncClient extends IntentService {

    private Client mClient;

    public static class IntentBuilder {
        public static Intent start(Context c, String address, int port){
            Intent i = new Intent(c, SyncClient.class);
            i.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_START);
            i.putExtra(SyncClient.INTENT_EXTRA_ADDRESS, address);
            i.putExtra(SyncClient.INTENT_EXTRA_PORT, port);
            return i;
        }
        public static Intent stop(Context c) {
            Intent i = new Intent(c, SyncClient.class);
            i.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_STOP);
            return i;
        }
        public static Intent send(Context c, Uri file) {
            Intent i = new Intent(c, SyncClient.class);
            i.putExtra(SyncClient.INTENT_EXTRA_ACTION, SyncClient.ACTION_SEND);
            i.putExtra(SyncClient.INTENT_EXTRA_FILE, file);
            return i;
        }
    }

    public static final int SAMPLES = 10;
    private static final int ACTION_START = SAMPLES;
    private static final int ACTION_STOP = 100;
    private static final int ACTION_SEND = 1000;

    private static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    private static final String INTENT_EXTRA_ADDRESS = "INTENT_EXTRA_ADDRESS";
    private static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";
    private static final String INTENT_EXTRA_FILE = "INTENT_EXTRA_FILE";

    private static final String LOG_TAG = SyncClient.class.getSimpleName();

    public SyncClient() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());
        switch (action) {
            case ACTION_START:
                final String address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
                mClient = new Client();
                mClient.getKryo().register(NetworkActions.RTTFrame.class);
                try {
                    mClient.connect(5000, address, 54885, 54887);
                } catch (IOException e) {
                    sendBroadcast(LaunchActivity.Message.onDisconnected(e.getMessage()));
                }
                break;
            case ACTION_STOP:
                mClient.close();
                break;
            case ACTION_SEND:
                final AtomicLong sum = new AtomicLong(0);
                final AtomicLong count = new AtomicLong(0);

                Listener listener = new Listener() {
                    public void received(Connection connection, Object object) {
                        if (object instanceof NetworkActions.RTTFrame) {
                            NetworkActions.RTTFrame frame = new NetworkActions.RTTFrame();
                            sum.addAndGet(System.currentTimeMillis() - frame.time);
                            count.incrementAndGet();
                        }
                    }
                };
                mClient.addListener(listener);

                NetworkActions.RTTFrame rttFrame = new NetworkActions.RTTFrame();
                for (int i = 0; i < SAMPLES; i++) {
                    rttFrame.time = System.currentTimeMillis();
                    mClient.sendTCP(rttFrame);
                }

                while(count.get() < SAMPLES - 1) try {
                    Thread.sleep(SAMPLES);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mClient.removeListener(listener);

                float avg = (sum.get() * 1.0f) / count.get();
                sendBroadcast(LaunchActivity.Message.onSynced("samples:" + count.get(), avg));
                break;
        }
    }
}
