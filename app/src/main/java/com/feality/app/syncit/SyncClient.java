package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by Filip on 2014-09-12.
 */
public class SyncClient extends IntentService {

    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_SEND = 2;

    public static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    public static final String INTENT_EXTRA_ADDRESS = "INTENT_EXTRA_ADDRESS";
    public static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";
    public static final String INTENT_EXTRA_FILE = "INTENT_EXTRA_FILE";

    public static final String LOG_TAG = SyncClient.class.getSimpleName();
    private Socket mSocket;

    public SyncClient() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());
        switch (action){
            case ACTION_START:
                String address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
                int port = intent.getIntExtra(INTENT_EXTRA_PORT, -1);
                try {
                    mSocket = new Socket(InetAddress.getByName(address), port);
                } catch (UnknownHostException e) {
                    Log.d(LOG_TAG, "Unable to resolve address: " + address, e);
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Unable to connect to address " + address + ":" + port, e);
                }

                try {
                    DataInputStream dis = new DataInputStream(mSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());

                    while (!mSocket.isClosed()) {
                        dos.writeLong(System.currentTimeMillis());
                        long time = dis.readLong();
                        Log.w(LOG_TAG, "Received time: " + time);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            case ACTION_STOP:
                try {
                    mSocket.close();
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Unable to close connection", e);
                }
                stopSelf();
            case ACTION_SEND:
                final String fileUri = intent.getStringExtra(INTENT_EXTRA_FILE);
                Log.d(LOG_TAG, "About to send file: "+ fileUri);

        }

    }
}
