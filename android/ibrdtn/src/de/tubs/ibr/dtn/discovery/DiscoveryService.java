package de.tubs.ibr.dtn.discovery;

import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import android.util.SparseArray;
import de.tubs.ibr.dtn.daemon.Preferences;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class DiscoveryService extends Service {

    public static final String TAG = DiscoveryService.class.getSimpleName();

    /**
     * Prefix used in the name of a Bluetooth device that announces a DTN node
     */
    public static final String DTN_DISCOVERY_PREFIX = "dtn:";
    
    /**
     * Time in ms before a previously found node is announced again
     */
    public static final int REDISCOVER_TIMEOUT = 5 * 60 * 1000;
    
    public static final String ACTION_BLE_NODE_DISCOVERED = "de.tubs.ibr.dtn.Intent.BLE_NODE_DISCOVERED";
    public static final String EXTRA_BLE_NODE = "de.tubs.ibr.dtn.Intent.BLE_NODE";

    private Handler mHandler = new Handler();
    private WifiManager mWifiManager;
    private AlarmManager mAlarmManager;
    private Intent mWakefulIntent;
    private SparseArray<Long> mDiscoveredDtnNodes;
    
    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO unregister receiver?
			unregisterReceiver(mWifiReceiver);
			
			List<ScanResult> results = mWifiManager.getScanResults();
			for (ScanResult s : results) {
				Log.i(TAG, "onWifiScan: " + s.SSID + " (" + s.level + ")");
				if (s.SSID.toLowerCase(Locale.US).startsWith(DTN_DISCOVERY_PREFIX)) {
					onDtnNodeDiscovered(s.SSID);
				}
				
			}
		}
    };

    private void onDtnNodeDiscovered(String address) {

    	// transform address to proper format
    	String nodeAddress = address.toLowerCase(Locale.US);

        // throttle announcements
        int nodeHash = nodeAddress.hashCode();
        Long lastDiscoveryTime = mDiscoveredDtnNodes.get(nodeHash);
        if (lastDiscoveryTime != null && System.currentTimeMillis() < lastDiscoveryTime + REDISCOVER_TIMEOUT) {
            return;
        }
        mDiscoveredDtnNodes.put(nodeHash, System.currentTimeMillis());

        Log.d(TAG, "found DTN node with address: " + nodeAddress);
        String ssid = nodeAddress;
        
		// connect to network with matching SSID if not already connected
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
		if (!wifiInfo.getSSID().equals(String.format("\"%s\"", ssid))) {
			Log.d(TAG, "connecting to SSID " + ssid);
			WifiConfiguration wifiConfig = new WifiConfiguration();
			wifiConfig.SSID = String.format("\"%s\"", ssid);
			wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

			int networkId = mWifiManager.addNetwork(wifiConfig);
			mWifiManager.disconnect();
			mWifiManager.enableNetwork(networkId, true);
			mWifiManager.reconnect();
		}

        // announce endpoint to IBR-DTN daemon
        Intent discoveredIntent = new Intent(ACTION_BLE_NODE_DISCOVERED);
        discoveredIntent.putExtra(EXTRA_BLE_NODE, ssid + ".dtn");
        sendBroadcast(discoveredIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initialize();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mWakefulIntent = intent;
        startScan();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    @TargetApi(18)
    public boolean initialize() {

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        mDiscoveredDtnNodes = new SparseArray<Long>();
        
        return true;
    }

    @SuppressLint("NewApi")
	private void startScan() {
      // TODO set proper values for scan duration and delay
        final int delay = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Preferences.KEY_SCAN_DELAY, "30000"));

        Log.i(TAG, "scanning for WiFi");
        
        registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
        
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
         
                if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Preferences.KEY_BLE_ENABLED, false))
                    return;
                
                // schedule new scan with AlarmManager
                PendingIntent intent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(WakefulDiscoveryReceiver.ACTION_BLE_DISCOVERY), 0);
                if (Build.VERSION.SDK_INT >= 19) {
                    mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, intent);
                } else {
                    mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, intent);
                }
                
                // release wakelock after scan has finished
                if (mWakefulIntent != null) {
                	WakefulBroadcastReceiver.completeWakefulIntent(mWakefulIntent);
                } else {
                	Log.e(TAG, "WakefulIntent is null!");
                }
            }
        }, delay);
    }

}
