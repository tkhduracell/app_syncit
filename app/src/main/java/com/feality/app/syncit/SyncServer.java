package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

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

    public static final String INTENT_EXTRA_ACTION = "INTENT_EXTRA_ACTION";
    public static final String INTENT_EXTRA_PORT = "INTENT_EXTRA_PORT";

    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;

    private int mState = ACTION_STOP;
    private Thread mAcceptThread;

    public SyncServer() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int action = intent.getIntExtra(INTENT_EXTRA_ACTION, -1);
        Log.d(LOG_TAG, "onHandleIntent(): "+ intent.getExtras());

        switch (action) {
            case ACTION_START:
                if(ACTION_START == mState) break;
                final int port = intent.getIntExtra(INTENT_EXTRA_PORT, -1);
                mAcceptThread = new Thread(new AcceptService(port, this));
                mAcceptThread.setName("AcceptService-" + port);
                mAcceptThread.start();
                break;
            case ACTION_STOP:
                if(ACTION_STOP == mState) break;
                stopSelf();
                break;
            default:
                Log.wtf(LOG_TAG, "Unknown action: " + action);
                break;
        }
        mState = action;
    }

    @Override
    public void onDestroy() {
        mAcceptThread.interrupt();
        mAcceptThread = null;
        mState = ACTION_START;
        super.onDestroy();
    }

    public void sendMessage(String msg) {
        Intent intent = new Intent(LaunchActivity.ServiceIntentReceiver.ACTION_ON_CONNECTION);
        intent.putExtra(LaunchActivity.ServiceIntentReceiver.EXTRA_MESSAGE, msg);
        sendBroadcast(intent);
    }

    private static class EchoServiceRunnable implements Runnable {
        private final Socket mSocket;
        private final SocketAddress mRemoteSocketAddress;

        public EchoServiceRunnable(final Socket socket) {
            mSocket = socket;
            mRemoteSocketAddress = socket.getRemoteSocketAddress();
        }

        @Override
        public void run() {
            try {
                DataInputStream dis = new DataInputStream(mSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());

                try {
                    while (!Thread.interrupted() && !mSocket.isClosed()) {
                        long time = dis.readLong();
                        Log.w(LOG_TAG, "Received time: " + time);
                        dos.writeLong(System.currentTimeMillis());
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to echo time: " + mRemoteSocketAddress, e);
                }

            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to setup streams: " + mRemoteSocketAddress, e);
            }

            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to close socket: " + mRemoteSocketAddress, e);
            }

        }

    }

    private static class AcceptService implements Runnable {

        private final List<Thread> mThreadList = new ArrayList<Thread>();
        private final int mPort;
        private SyncServer mSyncServer;

        private ServerSocket mServerSocket;

        public AcceptService(final int port, final SyncServer syncServer) {
            mPort = port;
            mSyncServer = syncServer;
        }

        @Override
        public void run() {
            mSyncServer.sendMessage("AcceptService started!");
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to close running server on port: " + mPort, e);
                }
            }

            try {
                mServerSocket = new ServerSocket(mPort);
            } catch (BindException e) {
                Log.w(LOG_TAG, "Server already running on port: " + mPort);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to start server on port: " + mPort, e);
            }

            while(!Thread.interrupted() && mServerSocket != null && !mServerSocket.isClosed()) {
                try {
                    mSyncServer.sendMessage("Waiting for connections on " + mServerSocket.getLocalSocketAddress());
                    Log.d(LOG_TAG, "Waiting for connections on " + mServerSocket.getLocalSocketAddress());
                    final Socket socket = mServerSocket.accept();
                    mSyncServer.sendMessage("Got connections " + socket.getRemoteSocketAddress());
                    EchoServiceRunnable echoServiceRunnable = new EchoServiceRunnable(socket);
                    Thread thread = new Thread(echoServiceRunnable);
                    thread.setName("EchoService: " + socket.getRemoteSocketAddress());
                    thread.start();
                    mThreadList.add(thread);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "ServerSocket error accepting connection", e);
                }
            }

            for (Thread thread : mThreadList) {
                Log.d(LOG_TAG, "Stopping "+ thread.toString());
                thread.interrupt();
            }
            mThreadList.clear();
        }
    }
}
