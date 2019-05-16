package com.java.liuyingtian.ivi;

import android.content.Intent;
import android.net.VpnService;

import java.util.Timer;

public class IVIService extends VpnService {
    private Timer timer;
    private String serverIp;
    private String serverPort;
    private String localIp;

    static{
        System.loadLibrary("vpn-lib");
    }

    public native static int backend_thread(String serverIp, String serverPort, String localIp);

    @Override
    public void onCreate(){
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flag, int startId){
        if(intent.getStringExtra("action").equals("start")){
            serverIp = intent.getStringExtra("serverIp");
            serverPort = intent.getStringExtra("serverPort");
            localIp = intent.getStringExtra("localIp");
            startService();
        }
        else if(intent.getStringExtra("action").equals("stop")){
            stopService();
        }
        return START_STICKY;
    }

    private void startService(){
        int r = backend_thread(serverIp, serverPort, localIp);
        System.out.println("rrrrrrrrrrrr" + r);
    }

    private void stopService(){

    }


}
