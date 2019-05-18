package com.java.liuyingtian.ivi;

import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

public class IVIService extends VpnService {
    private Timer timer;
    private TimerTask timerTask;
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


    private long runtime = 0;//运行时长
    private long in_bytes_per_second;//每秒下载字节数
    private long out_bytes_per_second;//每秒上传字节数
    private long in_packets;//下载包数
    private long out_packets;//上传包数
    private long in_bytes;//下载总字节数
    private long out_bytes;//上传总字节数


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

    private int getInt(DataInputStream dataInputStream) throws  IOException {
        int a = dataInputStream.read();
        int b = dataInputStream.read();
        int c = dataInputStream.read();
        int d = dataInputStream.read();
        return ((d*256+c)*256+b)*256+a;
    }

    private long getBytes(DataInputStream dataInputStream) throws IOException{
        long a = (long)getInt(dataInputStream);
        long b = (long)getInt(dataInputStream);
        return (b<<32)+a;
    }

    private int readInt() throws IOException{
        return readStream.readInt();
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
    public int onStartCommand(final Intent intent, int flag, int startId){

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(intent.getStringExtra("action").equals("start")){
                    serverIp = intent.getStringExtra("serverIp");
                    serverPort = intent.getStringExtra("serverPort");
                    localIp = intent.getStringExtra("localIp");
                    System.out.println("in IVIService");
                    startService();
                    broadcast();
                }
                else if(intent.getStringExtra("action").equals("stop")){
                    stopService();
                }
            }
        }).start();

        return START_STICKY;
    }


    private void broadcast() {
        Intent intent = new Intent("BROADCAST");
        intent.putExtra("runtime",runtime);
        intent.putExtra("in_bytes_per_second",in_bytes_per_second);
        intent.putExtra("out_bytes_per_second",out_bytes_per_second);
        intent.putExtra("in_packets",in_packets);
        intent.putExtra("out_packets",out_packets);
        intent.putExtra("in_bytes",in_bytes);
        intent.putExtra("out_bytes",out_bytes);
        intent.putExtra("address",localVirtualAddr.getHostAddress());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        System.out.println("begin ivi timer!");
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                runtime++;
                System.out.println("in ivi service : runtime:"+runtime);
                try {
                    long in_bytes_before = in_bytes;
                    long out_bytes_before = out_bytes;
                    in_bytes = getBytes(readStream);
                    out_bytes = getBytes(readStream);
                    in_packets = getBytes(readStream);
                    out_packets = getBytes(readStream);
                    in_bytes_per_second = in_bytes - in_bytes_before;
                    out_bytes_per_second = out_bytes - out_bytes_before;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                broadcast();
            }
        };
        timer.schedule(timerTask,1000,1000);

    }

    private void stopService(){
        if(timer != null) {
            timer.cancel();
            timer = null;
        }

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
