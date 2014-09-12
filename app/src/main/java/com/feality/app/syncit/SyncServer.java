package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Filip on 2014-09-12.
 */
public class SyncServer extends IntentService {

    private static final String LOG_TAG = SyncServer.class.getSimpleName();

    public static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    public static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";
    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;

    private int mPort;
    private ServerSocket mServerSocket;

    public SyncServer() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());

        switch (action) {
            case ACTION_START:
                mPort = intent.getIntExtra(INTENT_EXTRA_PORT, -1);
                try {
                    mServerSocket = new ServerSocket(mPort);
                } catch (BindException e) {
                    Log.w(LOG_TAG, "Server already running on port: " + mPort);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to start server on port: " + mPort, e);
                }
                while(!mServerSocket.isClosed()) {
                    try {
                        new Thread(new EchoServiceRunnable(mServerSocket.accept())).start();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "ServerSocket accept error", e);
                    }
                }

                break;
            case ACTION_STOP:
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to close server on port: " + mPort, e);
                }
                stopSelf();
                break;
            default:
                Log.wtf(LOG_TAG, "Unknown action: " + action);
                break;
        }

    }

    private static class EchoServiceRunnable implements Runnable {

        private Socket mSocket;

        public EchoServiceRunnable(final Socket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                DataInputStream dis = new DataInputStream(mSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());

                while (!mSocket.isClosed()) {
                    long time = dis.readLong();
                    Log.w(LOG_TAG, "Received time: " + time);
                    dos.writeLong(System.currentTimeMillis());
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }
}
