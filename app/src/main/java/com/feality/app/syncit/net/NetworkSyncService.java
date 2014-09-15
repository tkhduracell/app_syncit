package com.feality.app.syncit.net;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.feality.app.syncit.LaunchActivity;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Filip on 2014-09-12.
 */
public class NetworkSyncService extends IntentService {

    private static final String LOG_TAG = NetworkSyncService.class.getSimpleName();
    public static final int DEFAULT_TCP = 54885;
    public static final int DEFAULT_UDP = 54887;

    private Server mServer;
    private Client mClient;

    public static class Message {
        public static Intent startServer(Context c) {
            Intent i = new Intent(c, NetworkSyncService.class);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ACTION, NetworkSyncService.ACTION_SERVER_START);
            return i;
        }
        public static Intent stopServer(Context c) {
            Intent i = new Intent(c, NetworkSyncService.class);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ACTION, NetworkSyncService.ACTION_SERVER_STOP);
            return i;
        }
        public static Intent sendFile(Context c, Uri uri) {
            Intent i = new Intent(c, NetworkSyncService.class);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ACTION, NetworkSyncService.ACTION_CLIENT_CHECK_DELAY);
            return i;
        }

        public static Intent connectClient(Context c, String hostAddress) {
            Intent i = new Intent(c, NetworkSyncService.class);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ACTION, NetworkSyncService.ACTION_CLIENT_CONNECT);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ADDRESS, hostAddress);
            return i;
        }
        public static Intent disconnectClient(Context c) {
            Intent i = new Intent(c, NetworkSyncService.class);
            i.putExtra(NetworkSyncService.INTENT_EXTRA_ACTION, NetworkSyncService.ACTION_CLIENT_DISCONNECT);
            return i;
        }
    }

    private static final String INTENT_EXTRA_ADDRESS = "INTENT_EXTRA_ADDRESS";
    private static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";

    private static final int ACTION_CLIENT_CHECK_DELAY = 8;
    private static final int ACTION_CLIENT_DISCONNECT = 16;
    private static final int ACTION_CLIENT_CONNECT = 4;
    private static final int ACTION_SERVER_START = 1;
    private static final int ACTION_SERVER_STOP = 2;

    public NetworkSyncService() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());

        switch (action) {
            case ACTION_SERVER_START:
                mServer = new Server();
                mServer.getKryo().register(NetworkActions.RTTFrame.class);
                mServer.start();
                try {
                    mServer.bind(DEFAULT_TCP, DEFAULT_UDP);
                } catch (IOException e) {
                    Log.wtf(LOG_TAG, "Unable to bind to ports", e);
                }
                setupServer();
                Log.d(LOG_TAG, "Server started!");
                break;

            case ACTION_SERVER_STOP:
                mServer.close();
                Log.w(LOG_TAG, "Server stopped!");
                break;

            case ACTION_CLIENT_CHECK_DELAY:
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
                for (int i = 0; i < 10; i++) {
                    rttFrame.time = System.currentTimeMillis();
                    mServer.sendToAllTCP(rttFrame);
                }
                long avgMs = sum.get() / count.get();
                sendBroadcast(LaunchActivity.Message.onRttCalculated(avgMs));

                break;
            case ACTION_CLIENT_CONNECT:
                String address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
                mClient = new Client();
                mClient.start();
                Log.d(LOG_TAG, "Client started!");
                try {
                    mClient.addListener(new Listener(){
                        @Override
                        public void connected(final Connection connection) {
                            super.connected(connection);
                            Log.d(LOG_TAG, "Client:connected " + connection.getRemoteAddressTCP());
                            sendBroadcast(LaunchActivity.Message.onConnected());
                        }

                        @Override
                        public void disconnected(final Connection connection) {
                            super.disconnected(connection);
                            Log.d(LOG_TAG, "Client:disconnected " + connection.getRemoteAddressTCP());
                            sendBroadcast(LaunchActivity.Message.onDisconnected());
                        }
                    });
                    mClient.connect(10000, address, DEFAULT_TCP, DEFAULT_UDP);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to connect to " + address, e);
                    sendBroadcast(LaunchActivity.Message.onDisconnected());
                }
                break;
            default:
                Log.wtf(LOG_TAG, "Unknown action: " + action);
                break;
        }
    }

    private void setupServer() {
        mServer.addListener(new Listener() {
            public void received (Connection connection, Object object) {
                if (object instanceof NetworkActions.RTTFrame) {
                    connection.sendTCP(object);
                }
            }

            @Override
            public void connected(final Connection connection) {
                super.connected(connection);
                Log.d(LOG_TAG, "Server:client connected " + connection.getRemoteAddressTCP());
                sendBroadcast(LaunchActivity.Message.onConnected());
            }

            @Override
            public void disconnected(final Connection connection) {
                super.disconnected(connection);
                Log.d(LOG_TAG, "Server:client disconnected " + connection.getRemoteAddressTCP());
                sendBroadcast(LaunchActivity.Message.onDisconnected());
            }
        });
    }

    private static class MediaPlay {

    }
}
