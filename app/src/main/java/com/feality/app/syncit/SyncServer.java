package com.feality.app.syncit;

import android.app.IntentService;
import android.content.Context;
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
                mAcceptThread = new Thread(new AcceptServer(port, this));
                mAcceptThread.setName("AcceptServer-" + port);
                mAcceptThread.start();
                break;
            case ACTION_STOP:
                if(ACTION_STOP == mState) break;
                mAcceptThread.interrupt();
                mAcceptThread = null;
                break;
            default:
                Log.wtf(LOG_TAG, "Unknown action: " + action);
                break;
        }
        mState = action;
    }

    private static class EchoResponder implements Runnable {
        private final Socket mSocket;
        private SyncServer mSyncServer;
        private final SocketAddress mRemoteSocketAddress;

        public EchoResponder(final Socket socket, final SyncServer syncServer) {
            mSocket = socket;
            mSyncServer = syncServer;
            mRemoteSocketAddress = socket.getRemoteSocketAddress();
        }

        @Override
        public void run() {
            mSyncServer.sendBroadcast(
                LaunchActivity.Message.onConnected(mRemoteSocketAddress.toString())
            );
            try {
                DataInputStream dis = new DataInputStream(mSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());

                try {
                    while (!Thread.interrupted() && !mSocket.isClosed()) {
                        Log.w(LOG_TAG, "Calculating RTT");
                        int samples = 30;
                        long sum = 0;
                        dos.writeInt(samples);
                        for (int i = 0; i < samples; i++) {
                            dos.writeLong(System.currentTimeMillis());
                            dos.flush();
                            long time = dis.readLong();
                            long now = System.currentTimeMillis();
                            sum += (now - time);
                        }
                        float avg = (sum * 1.0f) / samples;
                        String msg = "RTT: avg(" + avg + " ms) across " + samples + " samples";
                        mSyncServer.sendBroadcast(LaunchActivity.Message.onSynced(msg, avg));
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
            mSyncServer.sendBroadcast(
                LaunchActivity.Message.onDisconnected(mRemoteSocketAddress.toString())
            );
            mSyncServer = null;
        }

    }

    private static class AcceptServer implements Runnable {

        private final List<Thread> mThreadList = new ArrayList<Thread>();
        private final int mPort;
        private SyncServer mSyncServer;

        private ServerSocket mServerSocket;

        public AcceptServer(final int port, final SyncServer syncServer) {
            mPort = port;
            mSyncServer = syncServer;
        }

        @Override
        public void run() {
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
                    Log.d(LOG_TAG, "Waiting for connections: " + mServerSocket.getLocalSocketAddress());
                    final Socket socket = mServerSocket.accept();
                    Log.d(LOG_TAG, socket.getRemoteSocketAddress() + " connected");

                    EchoResponder echoResponder = new EchoResponder(socket, mSyncServer);
                    Thread thread = new Thread(echoResponder);
                    thread.setName("EchoResponder: " + socket.getRemoteSocketAddress());
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
            mSyncServer = null;
        }
    }
}
