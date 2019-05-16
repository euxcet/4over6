package com.java.liuyingtian.ivi;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Timer;

public class IVIService extends VpnService {
    private Timer timer;
    private String serverIp;
    private String serverPort;
    private String localIp;
    private InetAddress localVirtualAddr;
    private ParcelFileDescriptor vpnInterface;

    private ParcelFileDescriptor frontWrite;
    private ParcelFileDescriptor backRead;
    private ParcelFileDescriptor frontRead;
    private ParcelFileDescriptor backWrite;
    private DataOutputStream writeStream;
    private DataInputStream readStream;


    private byte[] readBytes(int length) throws IOException{
        byte[] bytes = new byte[length];
        int offset = 0;
        while(offset < length){
            int size = readStream.read(bytes, offset, length - offset);
            if(size < 0) throw new IOException();
            offset += size;
        }
        return bytes;
    }

    private int readInt() throws IOException{
        return readStream.readInt()
    }

    private void writeInt(int data) throws  IOException{
        writeStream.writeInt(data);
    }

    private void writeBytes(byte[] data) throws IOException {
        writeStream.write(data);
    }


    static {
        System.loadLibrary("native-lib");
    }
    public native int backend_thread(String serverIp, String serverPort, String localIp);
    public native int get_ipv4_addr(String serverIp, String serverPort, String localIp);
    public native int set_backend_pipes(int backRead, int backWrite);

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
        stopService();
        localVirtualAddr = null;

        try {
            ParcelFileDescriptor[] pipeFds = ParcelFileDescriptor.createPipe();
            backRead = pipeFds[0];
            frontWrite = pipeFds[1];
            pipeFds = ParcelFileDescriptor.createPipe();
            frontRead = pipeFds[0];
            backWrite = pipeFds[1];
        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }
        set_backend_pipes(backRead.getFd(), backWrite.getFd());
        get_ipv4_addr(serverIp, serverPort, localIp);
        readStream = new DataInputStream(new FileInputStream(frontRead.getFileDescriptor()));
        writeStream = new DataOutputStream(new FileOutputStream(frontWrite.getFileDescriptor()));

        try {
            // ip route dns1 dns2 dns3
            byte[] addr = readBytes(4);
            localVirtualAddr = InetAddress.getByAddress(addr);
            addr = readBytes(4);
            InetAddress router = InetAddress.getByAddress(addr);
            addr = readBytes(4);
            InetAddress dns0 = InetAddress.getByAddress(addr);
            addr = readBytes(4);
            InetAddress dns1 = InetAddress.getByAddress(addr);
            addr = readBytes(4);
            InetAddress dns2 = InetAddress.getByAddress(addr);

            int protectSocket = readInt();
            protect(protectSocket);
            vpnInterface = new Builder()
                    .addAddress(localVirtualAddr, 24)
                    .addDnsServer(dns0)
                    .addDnsServer(dns1)
                    .addDnsServer(dns2)
                    .addRoute("0.0.0.0", 0)
                    .setSession("ivi")
                    .establish();
            writeInt(vpnInterface.getFd());

        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }

    }

    private void stopService(){
        try {
            if (vpnInterface != null)
                vpnInterface.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }

    }

    private void getIp(){

    }


}
