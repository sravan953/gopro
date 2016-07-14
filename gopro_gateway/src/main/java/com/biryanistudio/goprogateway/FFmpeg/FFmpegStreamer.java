package com.biryanistudio.goprogateway.FFmpeg;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.biryanistudio.goprogateway.UDPService;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

/**
 * Created by Sravan on 10-Jul-16.
 */
public class FFmpegStreamer {
    final private String TAG = getClass().getSimpleName();
    final private Context mContext;
    private FFmpeg mFFmpeg;
    private boolean mFFmpegLoaded = false;
    private Network mWifiNetwork;
    final private Intent mUDPIntent;

    final private String YOUTUBE_KEY = "";
    private String[] cmd = {"-i", "udp://10.5.5.9:8554", "-codec:v:0", "copy", "-codec:a:1", "copy",
            "-ar", "44100", "-preset", "veryfast", "-f", "flv", "rtmp://a.rtmp.youtube.com/live2/" + YOUTUBE_KEY};

    public FFmpegStreamer(Context context) {
        mContext = context;
        mUDPIntent = new Intent(mContext, UDPService.class);
    }

    public void start() {
        if(!mFFmpegLoaded)
            bindPortOnWifi();
        else
            new RequestStreamTask2().execute();
    }

    public void stop() {
        mFFmpeg.killRunningProcesses();
        mContext.stopService(mUDPIntent);
        Log.i(TAG, "ffmpeg killRunningProcesses");
    }

    private void bindPortOnWifi() {
        final ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest wifiNetworkReq = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        connectivityManager.requestNetwork(wifiNetworkReq, new ConnectivityManager.NetworkCallback() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
            @Override
            public void onAvailable (Network network) {
                try {
                    Log.i(TAG, "WIFI AVAILABLE");
                    mWifiNetwork = network;
                    DatagramSocket datagramSocket = new DatagramSocket();
                    network.bindSocket(datagramSocket);
                    datagramSocket.connect(InetAddress.getByName("10.5.5.9"), 8554);
                    Log.i(TAG, "PORT: "+ datagramSocket.getLocalPort());
                    setCellularAsDefault();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLost (Network network) {
                Log.i(TAG, "WIFI LOST");
            }
        });
    }

    private void setCellularAsDefault() {
        final ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest cellularNetworkReq = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build();
        connectivityManager.requestNetwork(cellularNetworkReq, new ConnectivityManager.NetworkCallback() {
            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onAvailable (Network network) {
                Log.i(TAG, "CELLULAR AVAILABLE");
                connectivityManager.bindProcessToNetwork(network);
                loadFFMPEG();
            }

            @Override
            public void onLost (Network network) {
                Log.i(TAG, "CELLULAR LOST");
            }
        });}

    public void loadFFMPEG() {
        mFFmpeg = FFmpeg.getInstance(mContext);
        try {
            mFFmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onStart() {
                    Log.i(TAG, "ffmpeg loadBinary onStart");
                }

                @Override
                public void onFailure() {
                    Log.i(TAG, "ffmpeg loadBinary onFailure");
                }

                @Override
                public void onSuccess() {
                    Log.i(TAG, "ffmpeg loadBinary onSuccess");
                }

                @Override
                public void onFinish() {
                    Log.i(TAG, "ffmpeg loadBinary onFinish");
                    mFFmpegLoaded = true;
                    new RequestStreamTask2().execute();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private void executeCmd() {
        try {
            mFFmpeg.execute(cmd, new FFmpegExecuteResponseHandler() {
                @Override
                public void onStart() {
                    Log.i(TAG, "ffmpeg execute onStart");
                }

                @Override
                public void onProgress(String message) {
                    Log.i(TAG, message);
                }

                @Override
                public void onFailure(String message) {
                    Log.i(TAG, message);
                }

                @Override
                public void onSuccess(String message) {
                    Log.i(TAG, message);
                }

                @Override
                public void onFinish() {
                    Log.i(TAG, "ffmpeg execute onFinish");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }
    }

    private class RequestStreamTask2 extends AsyncTask<Void, Void, String> {
        final private String GOPRO_STREAM_URL = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&a1=proto_v2&c1=restart";

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URLConnection urlConnection = mWifiNetwork.openConnection(new URL(GOPRO_STREAM_URL));
                InputStreamReader is = new InputStreamReader(urlConnection.getInputStream());
                BufferedReader br = new BufferedReader(is);
                return br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute (String result) {
            if(result == null)
                Log.i(TAG, "null");
            else {
                Log.i(TAG, result);
                mContext.startService(mUDPIntent);
                executeCmd();
            }
        }
    }
}
