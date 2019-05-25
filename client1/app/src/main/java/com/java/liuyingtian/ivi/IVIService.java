package com.java.liuyingtian.ivi;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class IVIService extends VpnService {
    private static int EXIT = 1;
    private static int IP_CONFIG = 2;
    private static int SET_TUN = 3;
    private static int FETCH_STATE = 4;
    public static String BROADCAST_STATE = "com.java.liuyingtian.ivi.BROADCAST_STATE";

    private Thread backendThread = null;
    private ParcelFileDescriptor vpnInterface = null;
    private Timer timer = null;
    private static IVIService instance = null;

    private ParcelFileDescriptor frontendWriteFd;
    private ParcelFileDescriptor backendReadFd;
    private ParcelFileDescriptor backendWriteFd;
    private ParcelFileDescriptor frontendReadFd;

    private DataInputStream readStream;
    private DataOutputStream writeStream;

    private String virtual_ipv4_addr;
    private long in_bytes;
    private long out_bytes;
    private long in_packets;
    private long out_packets;
    private long in_speed_bytes;
    private long out_speed_bytes;
    private long running_time;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.d("debug", "onStartCommand");

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (intent.getStringExtra("command").equals("start")) {
                    Log.d("onStartCommand", "starting");
                    startVPN(intent.getStringExtra("hostname"), intent.getIntExtra("port", 0));
                    IVIService.instance = IVIService.this;
                } else {
                    Log.d("onStartCommand", "stopping");
                    if (IVIService.instance != null) IVIService.instance.stopVPN();
                    IVIService.instance = null;
                }
                broadcastState();
            }
        }).start();

        return START_REDELIVER_INTENT;
    }

    private byte[] readResponse(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while(offset < length) {
            int read = readStream.read(data, offset, length-offset);
            if (read < 0) throw new IOException("read fail");
            offset += read;
        }
        return data;
    }

    private int readInt(DataInputStream s) throws IOException {
        int a = s.read();
        int b = s.read();
        int c = s.read();
        int d = s.read();
        return ((d*256+c)*256+b)*256+a;
    }
    private long readLong(DataInputStream s) throws IOException {
        long a = (long)readInt(s);
        long b = (long)readInt(s);
        return (b<<32)+a;
    }
    private void writeInt(DataOutputStream s, int data) throws IOException {
        int a = data%256; data /= 256;
        int b = data%256; data /= 256;
        int c = data%256; data /= 256;
        int d = data%256; data /= 256;
        s.write(a);s.write(b);s.write(c);s.write(d);
    }

    private void broadcastState() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.putExtra("running_state", (backendThread != null && backendThread.isAlive()) ? 1 : 0);
        intent.putExtra("virtual_ipv4_addr", virtual_ipv4_addr);
        intent.putExtra("in_bytes", in_bytes);
        intent.putExtra("out_bytes", out_bytes);
        intent.putExtra("in_packets", in_packets);
        intent.putExtra("out_packets", out_packets);
        intent.putExtra("in_speed_bytes", in_speed_bytes);
        intent.putExtra("out_speed_bytes", out_speed_bytes);
        intent.putExtra("running_time", running_time);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
//        sendBroadcast(intent);
    }

    private int startVPN(String hostname, int port) {
        stopVPN();

        virtual_ipv4_addr = null;
        in_bytes = 0;
        out_bytes = 0;
        in_packets = 0;
        out_packets = 0;
        in_speed_bytes = 0;
        out_speed_bytes = 0;
        running_time = 0;

        try {
            ParcelFileDescriptor[] pipeFds = ParcelFileDescriptor.createPipe();
            frontendWriteFd = pipeFds[1];
            backendReadFd = pipeFds[0];
            pipeFds = ParcelFileDescriptor.createPipe();
            backendWriteFd = pipeFds[1];
            frontendReadFd = pipeFds[0];
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        System.out.println("set pipes:" + backendReadFd.getFd() + ", " + backendWriteFd.getFd());

        backendThread = new Thread(new BackendThread(hostname, port, backendReadFd.getFd(), backendWriteFd.getFd()));
        backendThread.start();
        System.out.println(1111);

        writeStream = new DataOutputStream(new FileOutputStream(frontendWriteFd.getFileDescriptor()));
        readStream = new DataInputStream(new FileInputStream(frontendReadFd.getFileDescriptor()));

        // 初始化配置
        try {
            System.out.println("read socket");
            int socketFd = readInt(readStream);
            System.out.println("protect socket: " + socketFd);

            System.out.println("front fetch config");
            writeStream.writeByte(IP_CONFIG);

            byte[] data = readResponse(20);
            System.out.println("reading");
            InetAddress address = InetAddress.getByAddress(Arrays.copyOfRange(data, 0, 4));
            InetAddress mask = InetAddress.getByAddress(Arrays.copyOfRange(data, 4, 8));
            InetAddress dns1 = InetAddress.getByAddress(Arrays.copyOfRange(data, 8, 12));
            InetAddress dns2 = InetAddress.getByAddress(Arrays.copyOfRange(data, 12, 16));
            InetAddress dns3 = InetAddress.getByAddress(Arrays.copyOfRange(data, 16, 20));

            vpnInterface = new Builder()
                    .addAddress(address, 24)
                    .addDnsServer(dns1)
                    .addDnsServer(dns2)
                    .addDnsServer(dns3)
                    .addRoute("0.0.0.0", 0)
                    .setSession("overover")
                    .establish();
            writeStream.writeByte(SET_TUN);
            writeInt(writeStream, vpnInterface.getFd());
            protect(socketFd);
            virtual_ipv4_addr = address.getHostAddress();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        TimerTask timer_task = new TimerTask() {
            public void run() {
                running_time ++;

                if (!backendThread.isAlive()) {
                    stopVPN();
                } else {
                    try {
                        writeStream.writeByte(FETCH_STATE);
                        long old_in_bytes = in_bytes;
                        long old_out_bytes = out_bytes;
                        in_bytes = readLong(readStream);
                        out_bytes = readLong(readStream);
                        in_packets = readLong(readStream);
                        out_packets = readLong(readStream);
                        in_speed_bytes = in_bytes - old_in_bytes;
                        out_speed_bytes = out_bytes - old_out_bytes;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                broadcastState();
            }
        };

        timer = new Timer();
        timer.scheduleAtFixedRate(timer_task, 1000, 1000);

        return 0;
    }

    private void stopVPN() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;

        if (backendThread != null && backendThread.isAlive()) {
            try {
                writeStream.writeByte(EXIT);
                backendThread.join(10000);
                writeStream.close();
                readStream.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        backendThread = null;

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        vpnInterface = null;

        stopSelf();
    }
}
//import android.content.Intent;
//import android.net.VpnService;
//import android.os.ParcelFileDescriptor;
//
//import java.io.DataInputStream;
//import java.io.DataOutputStream;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.net.Inet4Address;
//import java.net.InetAddress;
//import java.util.Timer;
//
//public class IVIService extends VpnService {
//    private Timer timer;
//    private String serverIp;
//    private String serverPort;
//    private String localIp;
//    private InetAddress localVirtualAddr;
//    private ParcelFileDescriptor vpnInterface;
//
//    private ParcelFileDescriptor frontWrite;
//    private ParcelFileDescriptor backRead;
//    private ParcelFileDescriptor frontRead;
//    private ParcelFileDescriptor backWrite;
//    private DataOutputStream writeStream;
//    private DataInputStream readStream;
//    private long downloadBytes;
//    private long uploadBytes;
//
//    private static int IP_CONFIG = 1;
//    private static int SET_TUN = 2;
//    private static int EXIT = 3;
//
//    private byte[] readBytes(int length) throws IOException{
//        byte[] bytes = new byte[length];
//        int offset = 0;
//        while(offset < length){
//            int size = readStream.read(bytes, offset, length - offset);
//            System.out.println("read size: " + size);
//            if(size < 0) throw new IOException();
//            offset += size;
//        }
//        return bytes;
//    }
//
//    private int readInt() throws IOException{
//        // 字节序
//        int i1 = readStream.read();
//        int i2 = readStream.read();
//        int i3 = readStream.read();
//        int i4 = readStream.read();
//        return ((i4 * 256 + i3) * 256 + i2) * 256 + i1;
//    }
//    private long readLong() throws IOException{
//        long l1 = (long)readInt();
//        long l2 = (long)readInt();
//        return l1 + l2 * (1L << 32);
//    }
//
//    private void writeInt(int data) throws  IOException{
//        int i1 = data%256; data /= 256;
//        int i2 = data%256; data /= 256;
//        int i3 = data%256; data /= 256;
//        int i4 = data%256;
//        writeStream.write(i1);
//        writeStream.write(i2);
//        writeStream.write(i3);
//        writeStream.write(i4);
//    }
//
//    private void writeBytes(byte[] data) throws IOException {
//        writeStream.write(data);
//    }
//
//
//    static {
//        System.loadLibrary("native-lib");
//    }
//    public native int backend_thread(String serverIp, int serverPort, String localIp);
//    public native int set_backend_pipes(int backRead, int backWrite);
//
//    @Override
//    public void onCreate(){
//        super.onCreate();
//    }
//
//    @Override
//    public int onStartCommand(final Intent intent, int flag, int startId){
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                if(intent.getStringExtra("action").equals("start")){
//                    serverIp = intent.getStringExtra("serverIp");
//                    serverPort = intent.getStringExtra("serverPort");
//                    localIp = intent.getStringExtra("localIp");
//                    startService();
//                }
//                else if(intent.getStringExtra("action").equals("stop")){
//                    stopService();
//                }
//            }
//        }).start();
//
//        return START_REDELIVER_INTENT;
////        return START_STICKY;
//    }
//
//    private void startService(){
//        stopService();
//        localVirtualAddr = null;
//        uploadBytes = 0;
//        downloadBytes = 0;
//
//        try {
//            ParcelFileDescriptor[] pipeFds = ParcelFileDescriptor.createPipe();
//            backRead = pipeFds[0];
//            frontWrite = pipeFds[1];
//            pipeFds = ParcelFileDescriptor.createPipe();
//            frontRead = pipeFds[0];
//            backWrite = pipeFds[1];
//        }
//        catch(IOException e){
//            e.printStackTrace();
//            return;
//        }
//        // set pipe interface
//        System.out.println("set pipes:" + backRead.getFd() + ", " + backWrite.getFd());
//        set_backend_pipes(backRead.getFd(), backWrite.getFd());
//        // backend get ipv4 addr from server
//
//        readStream = new DataInputStream(new FileInputStream(frontRead.getFileDescriptor()));
//        writeStream = new DataOutputStream(new FileOutputStream(frontWrite.getFileDescriptor()));
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("start backend");
//                backend_thread(serverIp, Integer.parseInt(serverPort), localIp);
//            }
//        }).start();
//
//        System.out.println(111111111);
//        try {
//            int protectSocket = readInt();
//            System.out.println("protected socket: " + protectSocket);
//
//            writeBytes(new byte[]{(byte)IP_CONFIG});
//
//            // ip route dns1 dns2 dns3
//            byte[] addr = readBytes(4);
//            localVirtualAddr = InetAddress.getByAddress(addr);
//            System.out.println(localVirtualAddr.getHostAddress());
//            addr = readBytes(4);
//            InetAddress router = InetAddress.getByAddress(addr);
//            System.out.println(router.getHostAddress());
//            addr = readBytes(4);
//            InetAddress dns0 = InetAddress.getByAddress(addr);
//            System.out.println(dns0.getHostAddress());
//            addr = readBytes(4);
//            InetAddress dns1 = InetAddress.getByAddress(addr);
//            System.out.println(dns1.getHostAddress());
//            addr = readBytes(4);
//            InetAddress dns2 = InetAddress.getByAddress(addr);
//            System.out.println(dns2.getHostAddress());
//
//            vpnInterface = new Builder()
//                    .addAddress(localVirtualAddr, 24)
//                    .addDnsServer(dns0)
//                    .addDnsServer(dns1)
//                    .addDnsServer(dns2)
//                    .addRoute("0.0.0.0", 0)
//                    .setSession("ivi")
//                    .establish();
//            writeBytes(new byte[]{(byte)SET_TUN});
//            System.out.println("tun fd " + vpnInterface.getFd());
//            writeInt(vpnInterface.getFd());
//            protect(protectSocket);
//
////            new Thread(new Runnable() {
////                @Override
////                public void run() {
////                    try {
////                        downloadBytes = readLong();
////                        uploadBytes = readLong();
////                    }
////                    catch(IOException e){
////                        e.printStackTrace();
////                    }
////                }
////
////            }).start();
//
//        }
//        catch(IOException e){
//            e.printStackTrace();
//            return;
//        }
//
//    }
//
//    private void stopService(){
//        try {
//            if (vpnInterface != null)
//                vpnInterface.close();
//            if(writeStream != null)
//                writeBytes(new byte[]{(byte)EXIT});
//        }
//        catch(IOException e){
//            e.printStackTrace();
//        }
//    }
//}
