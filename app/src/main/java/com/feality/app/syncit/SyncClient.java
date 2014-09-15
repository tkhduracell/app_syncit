package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/**
 * Created by Filip on 2014-09-12.
 */
public class SyncClient extends IntentService {

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

    private static final int ACTION_START = 10;
    private static final int ACTION_STOP = 100;
    private static final int ACTION_SEND = 1000;

    private static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    private static final String INTENT_EXTRA_ADDRESS = "INTENT_EXTRA_ADDRESS";
    private static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";
    private static final String INTENT_EXTRA_FILE = "INTENT_EXTRA_FILE";

    private static final String LOG_TAG = SyncClient.class.getSimpleName();

    private Thread mClientThread;
    private int mState = ACTION_STOP;

    public SyncClient() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());
        switch (action) {
            case ACTION_START:
                if (ACTION_START == mState) break;
                final String address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
                final int port = intent.getIntExtra(INTENT_EXTRA_PORT, -1);
                mClientThread = new Thread(new ClientEchoRunnable(this, address, port));
                mClientThread.setName("ClientThread-" + address + ":" + port);
                mClientThread.start();
                break;
            case ACTION_STOP:
                if (ACTION_STOP == mState) break;
                mClientThread.interrupt();
                mClientThread = null;
            case ACTION_SEND:
                if (ACTION_SEND == mState) break;
                final String fileUri = intent.getStringExtra(INTENT_EXTRA_FILE);
                Log.d(LOG_TAG, "About to send file: "+ fileUri);
        }
        mState = action;
    }

    public void sendMessage(String msg) {
        Log.d(LOG_TAG, "Sending message: "+msg);
        Intent intent = new Intent(LaunchActivity.ServiceIntentReceiver.ACTION_ON_CONNECTION);
        intent.putExtra(LaunchActivity.ServiceIntentReceiver.EXTRA_MESSAGE, msg);
        sendBroadcast(intent);
    }

    private static class ClientEchoRunnable implements Runnable {

        private SyncClient mSyncClient;
        private final String mAddress;
        private final int mPort;
        private Socket mSocket;

        public ClientEchoRunnable(final SyncClient syncClient, final String address, final int port) {
            mSyncClient = syncClient;
            mAddress = address;
            mPort = port;
        }

        @Override
        public void run() {
            try {
                mSocket = new Socket(InetAddress.getByName(mAddress), mPort);
            } catch (UnknownHostException e) {
                Log.w(LOG_TAG, "Unable to resolve address: " + getAddress(), e);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Unable to connect to address " + getAddress(), e);
            }

            if (mSocket.isClosed()) return;

            final SocketAddress socketAddress = mSocket.getRemoteSocketAddress();
            try {
                DataInputStream dis = new DataInputStream(mSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());

                Log.d(LOG_TAG, "Waiting to send packets to: " + socketAddress);
                while (!Thread.interrupted() && !mSocket.isClosed()) {
                    int samples = dis.readInt();
                    for (int i = 0; i < samples; i++) {
                        long time = dis.readLong();
                        dos.writeLong(time);
                        dos.flush();
                    }
                    mSyncClient.sendMessage("Echoed "+ samples +" samples to server");
                }

            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to setup streams : " + socketAddress, e);
            }

            try {
                mSocket.close();
                mSocket = null;
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to close socket: " + socketAddress, e);
            }
            mSyncClient = null;
        }

        private String getAddress() {
            return mAddress + ":" + mPort;
        }
    }
}
