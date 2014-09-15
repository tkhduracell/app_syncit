package com.feality.app.syncit;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

/**
 * Created by Filip on 2014-09-14.
 */
public class DiscoveryHandler {

    private static final String LOG_TAG = DiscoveryHandler.class.getSimpleName();
    public static final String SERVICE_TYPE = "_syncit._udp.";
    public static final String SERVICE_NAME = "_syncit_";

    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;

    private String mLocalServiceName;

    public DiscoveryHandler(final NsdManager nsdManager) {
        mNsdManager = nsdManager;
    }

    public void registerService(final String deviceName) {
        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                mLocalServiceName = NsdServiceInfo.getServiceName();
                Log.d(LOG_TAG, "Service registered "+ mLocalServiceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                Log.e(LOG_TAG, serviceInfo + "\nErrorCode="+errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.d(LOG_TAG, "Service un-registered "+ mLocalServiceName);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.
                Log.e(LOG_TAG, serviceInfo + "\nErrorCode="+errorCode);
            }
        };

        NsdServiceInfo serviceInfo  = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME + deviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(LaunchActivity.DEFAULT_PORT);
        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void discoverServices(final LaunchActivity serviceListener) {
        Log.d(LOG_TAG, "discoverServices(entered)");

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(LOG_TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(LOG_TAG, "Service discovery: " + service.getHost());
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(LOG_TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(mLocalServiceName)) {
                    // The name of the service tells the user what they'd be
                    // connecting to. It could be "Bob's Chat App".
                    Log.d(LOG_TAG, "Local machine: " + mLocalServiceName);
                } else if (service.getServiceName().contains(SERVICE_NAME)){
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(LOG_TAG, "Service lost" + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(LOG_TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(LOG_TAG, "Discovery failed: Error code=" + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(LOG_TAG, "Discovery failed: Error code=" + errorCode);
            }
        };

        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(LOG_TAG, "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(LOG_TAG, "Resolve Succeeded: " + serviceInfo);
                serviceListener.onServiceDetected(serviceInfo);
            }
        };

        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        Log.d(LOG_TAG, "discoverServices(initiated)");
    }

    public void tearDown() {
        try{
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        } catch (IllegalArgumentException ignored) {} // Discovery not running
        try {
            mNsdManager.unregisterService(mRegistrationListener);
        } catch (IllegalArgumentException ignored) {} // Service not registered
    }
}
