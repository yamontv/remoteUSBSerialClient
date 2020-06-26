package uz.elius.remoteusb_serialclient;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TcpClient {
    private String TAG = getClass().getName();

    static final int SERVER_PORT = 65432;
    // sends message received notifications
    private OnMessageReceived mMessageListener = null;
    // while this is true, the server will continue running
    private boolean mRun = false;

    private Socket mSocket;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(OnMessageReceived listener) {
        mMessageListener = listener;
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public void sendMessage(byte[] data) {
        try {
            mSocket.getOutputStream().write(data);
            mSocket.getOutputStream().flush();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Close the connection and release the members
     */
    public void stopClient() {
        mRun = false;
    }

    public boolean connect(final String url) {
        try {
            mSocket = new Socket();
            try {
                mSocket.connect(new InetSocketAddress(url, SERVER_PORT), 5000);
            } catch (IOException ex) {
                Log.e(TAG, ex.toString());
                mSocket.close();
                return false;
            }

            mSocket.setSoTimeout(1000);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }

        return true;
    }

    public void run() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead = 0;
        byte[] data = new byte[16384];
        mRun = true;

        try {
            try {
                while (mRun) {
                    try {
                        while ((nRead = mSocket.getInputStream().read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                    } catch (SocketTimeoutException e) {
                        //all good
                    }
                    if (buffer.size() > 0) {
                        mMessageListener.messageReceived(buffer.toByteArray());
                        buffer.reset();
                    }
                    if (nRead == -1) {
                        Log.e(TAG, "remote closed");
                        mRun = false;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "S: Error", e);
            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                mSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "C: Error", e);
        }
    }

    public interface OnMessageReceived {
        public void messageReceived(byte[] data);
    }
}
