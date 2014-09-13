package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Intent;
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

    public static final int ACTION_START = 10;
    public static final int ACTION_STOP = 100;
    public static final int ACTION_SEND = 1000;

    public static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    public static final String INTENT_EXTRA_ADDRESS = "INTENT_EXTRA_ADDRESS";
    public static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";
    public static final String INTENT_EXTRA_FILE = "INTENT_EXTRA_FILE";

    public static final String LOG_TAG = SyncClient.class.getSimpleName();
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
                mClientThread = new Thread(new ClientEchoRunnable(address, port));
                mClientThread.setName("ClientThread-" + address + ":" + port);
                mClientThread.start();
                break;
            case ACTION_STOP:
                if (ACTION_STOP == mState) break;
                mClientThread.interrupt();
                mClientThread = null;
                stopSelf();
            case ACTION_SEND:
                if (ACTION_SEND == mState) break;
                final String fileUri = intent.getStringExtra(INTENT_EXTRA_FILE);
                Log.d(LOG_TAG, "About to send file: "+ fileUri);
        }
        mState = action;
    }

    private static class ClientEchoRunnable implements Runnable {

        private final String mAddress;
        private final int mPort;
        private Socket mSocket;

        public ClientEchoRunnable(final String address, final int port) {
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
                    dos.writeLong(System.currentTimeMillis());
                    long time = dis.readLong();
                    Log.d(LOG_TAG, "Received time: " + time);
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

        }

        private String getAddress() {
            return mAddress + ":" + mPort;
        }
    }
}
