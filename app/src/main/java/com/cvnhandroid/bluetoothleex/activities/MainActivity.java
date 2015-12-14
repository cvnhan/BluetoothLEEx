package com.cvnhandroid.bluetoothleex.activities;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.cvnhandroid.bluetoothleex.R;
import com.cvnhandroid.bluetoothleex.adapters.LeDeviceListAdapter;
import com.cvnhandroid.bluetoothleex.containers.BluetoothLeDeviceStore;
import com.cvnhandroid.bluetoothleex.util.BluetoothLeScanner;
import com.cvnhandroid.bluetoothleex.util.BluetoothUtils;
import com.cvnhandroid.bluetoothlelibrary.device.BluetoothLeDevice;
import com.cvnhandroid.bluetoothlelibrary.device.beacon.ibeacon.IBeaconDevice;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;
import uk.co.alt236.easycursor.objectcursor.EasyObjectCursor;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = MainActivity.class.getName();
    @Bind(R.id.tvBluetoothLe)
    protected TextView mTvBluetoothLeStatus;
    @Bind(R.id.tvBluetoothStatus)
    protected TextView mTvBluetoothStatus;
    @Bind(R.id.tvItemCount)
    protected TextView mTvItemCount;
    @Bind(android.R.id.list)
    protected ListView mList;
    @Bind(android.R.id.empty)
    protected View mEmpty;
    Firebase myFirebaseRef;

    public static Bus bus = new Bus();
    private BluetoothUtils mBluetoothUtils;
    private BluetoothLeScanner mScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothLeDeviceStore mDeviceStore;

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            final BluetoothLeDevice deviceLe = new BluetoothLeDevice(device, rssi, scanRecord, System.currentTimeMillis());
            mDeviceStore.addDevice(deviceLe);
            final EasyObjectCursor<BluetoothLeDevice> c = mDeviceStore.getDeviceCursor();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.swapCursor(c);
                    updateItemCount(mLeDeviceListAdapter.getCount());
                }
            });
        }
    };

    private void displayAboutDialog() {
        // REALLY REALLY LAZY LINKIFIED DIALOG
        final int paddingSizeDp = 5;
        final float scale = getResources().getDisplayMetrics().density;
        final int dpAsPixels = (int) (paddingSizeDp * scale + 0.5f);

        final TextView textView = new TextView(this);
        final SpannableString text = new SpannableString(getString(R.string.about_dialog_text));

        textView.setText(text);
        textView.setAutoLinkMask(RESULT_OK);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(dpAsPixels, dpAsPixels, dpAsPixels, dpAsPixels);

        Linkify.addLinks(text, Linkify.ALL);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                    }
                })
                .setView(textView)
                .show();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);
        myFirebaseRef = new Firebase("https://crackling-inferno-3353.firebaseio.com/");
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mList.setEmptyView(mEmpty);
        mList.setOnItemClickListener(this);
        mDeviceStore = new BluetoothLeDeviceStore();
        mBluetoothUtils = new BluetoothUtils(this);
        mScanner = new BluetoothLeScanner(mLeScanCallback, mBluetoothUtils);
        updateItemCount(0);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanner.isScanning()) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_progress_indeterminate);
        }

        if (mList.getCount() > 0) {
            menu.findItem(R.id.menu_share).setVisible(true);
        } else {
            menu.findItem(R.id.menu_share).setVisible(false);
        }

        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        final BluetoothLeDevice device = mLeDeviceListAdapter.getItem(position);
        if (device == null) return;

        final Intent intent = new Intent(this, DeviceDetailsActivity.class);
        intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE, device);

        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                startScan();
                break;
            case R.id.menu_stop:
                stopScan();
                break;
            case R.id.menu_about:
                displayAboutDialog();
                break;
            case R.id.menu_share:
                mDeviceStore.shareDataAsEmail(this);
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mScanner.scanLeDevice(-1, false);
        bus.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        bus.register(this);
        final boolean mIsBluetoothOn = mBluetoothUtils.isBluetoothOn();
        final boolean mIsBluetoothLePresent = mBluetoothUtils.isBluetoothLeSupported();

        if (mIsBluetoothOn) {
            mTvBluetoothStatus.setText(R.string.on);
        } else {
            mTvBluetoothStatus.setText(R.string.off);
        }

        if (mIsBluetoothLePresent) {
            mTvBluetoothLeStatus.setText(R.string.supported);
        } else {
            mTvBluetoothLeStatus.setText(R.string.not_supported);
        }

        invalidateOptionsMenu();
    }

    private void startScan() {
        final boolean mIsBluetoothOn = mBluetoothUtils.isBluetoothOn();
        final boolean mIsBluetoothLePresent = mBluetoothUtils.isBluetoothLeSupported();
        mDeviceStore.clear();
        updateItemCount(0);

        mLeDeviceListAdapter = new LeDeviceListAdapter(this, mDeviceStore.getDeviceCursor());
        mList.setAdapter(mLeDeviceListAdapter);

        mBluetoothUtils.askUserToEnableBluetoothIfNeeded();
        if (mIsBluetoothOn && mIsBluetoothLePresent) {
            mScanner.scanLeDevice(-1, true);
            invalidateOptionsMenu();
        }

        myFirebaseRef.child(getUniquePsuedoID()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.e(TAG, "onDataChange: " + snapshot.getValue());
                if (snapshot.getValue() != null && snapshot.getValue().toString().equals("checked")==false) {
                    Toast.makeText(MainActivity.this, snapshot.getValue().toString(), Toast.LENGTH_SHORT).show();
                    myFirebaseRef.child(getUniquePsuedoID()).setValue("checked");
                }
            }

            @Override
            public void onCancelled(FirebaseError error) {
            }
        });
    }

    private void stopScan(){
        mScanner.scanLeDevice(-1, false);
        invalidateOptionsMenu();
    }

    private void updateItemCount(final int count) {
        mTvItemCount.setText(
                getString(
                        R.string.formatter_item_count,
                        String.valueOf(count)));
    }

    @Subscribe
    public void DetectIMMEDIATEBLE(IBeaconDevice iBeacon){
        Toast.makeText(MainActivity.this, iBeacon.getAddress(), Toast.LENGTH_SHORT).show();
        Log.e(TAG, "DetectIMMEDIATEBLE: MAC: " + iBeacon.getAddress());
        Log.e(TAG, "DetectIMMEDIATEBLE: UUID: " + iBeacon.getUUID());
        Log.e(TAG, "DetectIMMEDIATEBLE: Major: " + iBeacon.getMajor());
        Log.e(TAG, "DetectIMMEDIATEBLE: Minor: " + iBeacon.getMinor());

        myFirebaseRef.child(iBeacon.getAddress()).setValue(getUniquePsuedoID());
        stopScan();
    }

    public static String getUniquePsuedoID() {
        String m_szDevIDShort = "35" + Build.BOARD.length() % 10 + Build.BRAND.length() % 10 + Build.DEVICE.length() % 10 + Build.MANUFACTURER.length() % 10 + Build.MODEL.length() % 10 + Build.PRODUCT.length() % 10;
        String serial = null;

        try {
            serial = Build.class.getField("SERIAL").get((Object) null).toString();
            return (new UUID((long) m_szDevIDShort.hashCode(), (long) serial.hashCode())).toString();
        } catch (Exception var3) {
            serial = "tale";
            return (new UUID((long) m_szDevIDShort.hashCode(), (long) serial.hashCode())).toString();
        }
    }

}
