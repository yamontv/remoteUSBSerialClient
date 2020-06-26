package uz.elius.remoteusb_serialclient;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ClientActivity extends AppCompatActivity implements UsbSerialInterface.UsbReadCallback {
    private final String SERVER_URL = "ec2-52-221-181-92.ap-southeast-1.compute.amazonaws.com";
    private final byte[] privateKey = {0x05, 0x27, 0x06, (byte) 0x9e, (byte) 0x9e, 0x44, (byte) 0xeb, 0x44, (byte) 0xa7, 0x48, 0x47, (byte) 0xbd,
            (byte) 0xca, (byte) 0xc9, (byte) 0xae, 0x2d, (byte) 0xf9, (byte) 0xf2, 0x75, (byte) 0x87, (byte) 0xc7, 0x08, 0x1e, 0x1e};
    private final int NONCE_LEN = 4;
    private final int AUTH_LEN = 512;
    private String TAG = getClass().getName();
    private static final String ACTION_USB_PERMISSION = "uz.elius.remoteusb_serialclient.USB_PERMISSION";

    private UsbManager mUsbManager;
    private UsbSerialDevice mSerial;
    private TextView mDeviceStatusText;
    private TextView mDeviceSpeedText;
    private TextView mClientNumberText;
    private TextView mNetworkStatus;

    private Button mStartButton;
    private Button mStopButton;

    private PendingIntent mPermissionIntent;

    private TcpClient mTcpClient;
    private boolean mIsConnected = false;

    private RC4 mRxCipher;
    private RC4 mTxCipher;
    private boolean mConnectionEstablished;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    connect(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                stop(null);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register callback to connect to device after asking for permission
        mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        setContentView(R.layout.activity_client);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mDeviceStatusText = findViewById(R.id.deviceStatus);
        mDeviceSpeedText = findViewById(R.id.deviceSpeed);
        mClientNumberText = findViewById(R.id.clientNumber);
        mNetworkStatus = findViewById(R.id.networkStatus);
        mStartButton = findViewById(R.id.buttonStart);
        mStopButton = findViewById(R.id.buttonStop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        stop(null);
    }

    public void start(View view) {
        UsbDevice device = findUsbDevice();
        if (device != null) {
            if (!mUsbManager.hasPermission(device)) {
                mUsbManager.requestPermission(device, mPermissionIntent);
            } else {
                connect(device);
            }
        } else {
            mDeviceStatusText.setText(R.string.no_device_found);
        }
        //For TCP tests only
//        mSerial = null;
//        startRead();
//        new ConnectTask().execute();
    }

    public void stop(View view) {
        stopRead();
        mSerial = null;
        mDeviceStatusText.setText(R.string.stopped);
        if (mTcpClient != null) {
            // disconnect
            mTcpClient.stopClient();
            mTcpClient = null;
        }
        mDeviceSpeedText.setText("");
        mClientNumberText.setText("");
    }

    private void connect(UsbDevice device) {
        UsbDeviceConnection usbConnection = mUsbManager.openDevice(device);
        mSerial = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);
        startRead();
        mDeviceStatusText.setText(getString(R.string.connected_to, device.getProductName()));
        mDeviceSpeedText.setText("BaudRate: 115200");
        new ConnectTask().execute();
    }

    private void startRead() {
        if (mSerial != null) {
            mSerial.open();
            mSerial.setBaudRate(115200);
            mSerial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            mSerial.setParity(UsbSerialInterface.PARITY_NONE);
            mSerial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            mSerial.read(this);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //buttons mod
        mStopButton.setEnabled(true);
        mStopButton.setBackgroundResource(R.color.colorPrimary);
        mStartButton.setEnabled(false);
        mStartButton.setBackgroundResource(R.color.colorDisabled);
    }

    private void stopRead() {
        if (mSerial != null) {
            mSerial.syncClose();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //buttons mod
        mStopButton.setEnabled(false);
        mStopButton.setBackgroundResource(R.color.colorDisabled);
        mStartButton.setEnabled(true);
        mStartButton.setBackgroundResource(R.color.colorPrimary);
    }

    private UsbDevice findUsbDevice() {
        UsbDevice device;
        HashMap<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();

        // Try and find a known device
        if (!usbDevices.isEmpty()) {
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();

                if (UsbSerialDevice.isSupported(device)) {
                    return device;
                }
            }
        }
        //no device
        return null;
    }

    @Override
    public void onReceivedData(byte[] data) {
        if (mIsConnected)
            mTcpClient.sendMessage(mTxCipher.crypt(data));
    }

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mConnectionEstablished = false;
        }

        @Override
        protected TcpClient doInBackground(String... message) {
            //we create a TCPClient object and
            mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(byte[] data) {
                    if (mConnectionEstablished == false) {
                        if (data.length != NONCE_LEN + AUTH_LEN) {
                            Log.e(TAG, "Wrong length of auth = " + data.length);
                            mTcpClient.stopClient();
                            return;
                        }
                        byte[] nonce = new byte[privateKey.length + NONCE_LEN];
                        System.arraycopy(privateKey, 0, nonce, 0, privateKey.length);
                        System.arraycopy(data, 0, nonce, privateKey.length, NONCE_LEN);
                        try {
                            mRxCipher = new RC4(nonce);

                            for (int i = 0; i < NONCE_LEN; i++) {
                                nonce[i + privateKey.length] = (byte) ((~nonce[i + privateKey.length]) & 0xFF);
                            }
                            mTxCipher = new RC4(nonce);
                        } catch (NoSuchAlgorithmException e) {
                            Log.e(TAG, e.toString());
                            mTcpClient.stopClient();
                            return;
                        }

                        byte[] auth = new byte[AUTH_LEN];
                        System.arraycopy(data, NONCE_LEN, auth, 0, AUTH_LEN);

                        onReceivedData(mRxCipher.crypt(auth));

                        mConnectionEstablished = true;
                        return;
                    }

                    data = mRxCipher.crypt(data);

                    switch (data[0]) {
                        case (byte) 0xFF:
                            publishProgress("number", String.format("%03d", data[1]));
                            return;
                        case (byte) 0xFE:
                            int baud_rate = (data[3] & 0xFF) | ((data[2] & 0xFF) << 8) | ((data[1] & 0xFF) << 16);
                            if (mSerial != null)
                                mSerial.setBaudRate(baud_rate);
                            publishProgress("baud_rate", String.valueOf(baud_rate));
                            return;
                    }
                    if (mSerial != null)
                        mSerial.write(data);
                }
            });
            publishProgress("connecting");
            if (mTcpClient.connect(SERVER_URL) == false) {
                publishProgress("fail");
                return null;
            }
            publishProgress("connected");
            mTcpClient.run();
            publishProgress("disconnected");
            mTcpClient = null;
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            switch (values[0]) {
                case "fail":
                    stop(null);
                    mNetworkStatus.setText(R.string.connect_fail);
                    mIsConnected = false;
                    break;
                case "connecting":
                    mNetworkStatus.setText(R.string.connecting);
                    break;
                case "connected":
                    mNetworkStatus.setText(R.string.connected);
                    mIsConnected = true;
                    break;
                case "disconnected":
                    mNetworkStatus.setText(R.string.disconnected);
                    mIsConnected = false;
                    stop(null);
                    break;
                case "number":
                    mClientNumberText.setText(values[1]);
                    break;
                case "baud_rate":
                    mDeviceSpeedText.setText("BaudRate: " + values[1]);
                    break;
            }
        }
    }
}
