package com.java.liuyingtian.ivi;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private String mLocalIpv6Address;
    private String mLocalIpv4Address;
    private String mLocalMacAddress;
    private String mServerIpv6Address;
    private String mServerPort;

    private EditText serverIpEdit;
    private EditText serverPortEdit;
    private Button confirmButton;
    private Button connectButton;
    private TextView informationText;

    private Context context;
    private Timer timer;
    private boolean configured = false;

    enum VpnState{DISCONNECTED, CONNECTING, CONNECTED}
    private VpnState vpnState = VpnState.DISCONNECTED;

    static {
        System.loadLibrary("native-lib");
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        layoutInit();
        init();
    }

    private void updateInformation(){
        String info = "Local IP: " + mLocalIpv6Address + "\nServer IP: " + mServerIpv6Address + "\nServer Port: " + mServerPort;
        informationText.setText(info);
    }

    public void updateVpnState(VpnState state){
        vpnState = state;
    }

    private void layoutInit(){
        serverIpEdit = findViewById(R.id.server_ip);
        serverPortEdit = findViewById(R.id.server_port);
        confirmButton = findViewById(R.id.confirm);
        informationText = findViewById(R.id.information);
        connectButton = findViewById(R.id.connect);

        connectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(vpnState == VpnState.DISCONNECTED){
                    prepareVpnService();
                    ((Button)v).setText("Disconnect");
                }
                else if(vpnState == VpnState.CONNECTING){
                    stopVpnService();
                    ((Button)v).setText("Connect");
                }
                else if(vpnState == VpnState.CONNECTED){
                    stopVpnService();
                    ((Button)v).setText("Connect");
                }
            }
        });

        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String port = serverPortEdit.getText().toString();
                String portPattern = "^\\d{3,5}$";
                String ip = serverIpEdit.getText().toString();
                boolean isValidPort = Pattern.matches(portPattern, port);
                String ipPattern = "^((([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4})|" +
                        "(([0-9A-Fa-f]{1,4}:){1,7}:)|(([0-9A-Fa-f]{1,4}:){6}:[0-9A-Fa-f]{1,4})|" +
                        "(([0-9A-Fa-f]{1,4}:){5}(:[0-9A-Fa-f]{1,4}){1,2})|(([0-9A-Fa-f]{1,4}:){4}(" +
                        ":[0-9A-Fa-f]{1,4}){1,3})|(([0-9A-Fa-f]{1,4}:){3}(:[0-9A-Fa-f]{1,4}){1,4})|" +
                        "(([0-9A-Fa-f]{1,4}:){2}(:[0-9A-Fa-f]{1,4}){1,5})|([0-9A-Fa-f]{1,4}:(:[0-9A-Fa-f]" +
                        "{1,4}){1,6})|(:(:[0-9A-Fa-f]{1,4}){1,7})|(([0-9A-Fa-f]{1,4}:){6}(\\d|[1-9]\\d|1\\d{2}" +
                        "|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})|(([0-9A-Fa-f]{1,4}:)" +
                        "{5}:(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})" +
                        "|(([0-9A-Fa-f]{1,4}:){4}(:[0-9A-Fa-f]{1,4}){0,1}:(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])" +
                        "(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})|(([0-9A-Fa-f]{1,4}:){3}(:[0-9A-Fa-f]{1,4}" +
                        "){0,2}:(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5]))" +
                        "{3})|(([0-9A-Fa-f]{1,4}:){2}(:[0-9A-Fa-f]{1,4}){0,3}:(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(" +
                        "\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})|([0-9A-Fa-f]{1,4}:(:[0-9A-Fa-f]{1,4}){0,4}:(\\" +
                        "d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})|(:(" +
                        ":[0-9A-Fa-f]{1,4}){0,5}:(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-" +
                        "4]\\d|25[0-5])){3}))$";
                boolean isValidIp = Pattern.matches(ipPattern, ip);
                if(isValidIp && isValidPort){
                    mServerIpv6Address = ip;
                    mServerPort = port;
                    configured = true;
                    updateInformation();
                    Toast.makeText(context, "IP and Port confirmed", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(context, "Invalid IP or Port", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void prepareVpnService(){
        Intent intent = VpnService.prepare(getApplicationContext());
        if(intent != null){
            startActivityForResult(intent, 0);
        }
        else{
            onActivityResult(0, RESULT_OK, null);
        }
    }

    protected void onActivityResult(int request, int result, Intent data){
        super.onActivityResult(request, result, data);
        if(result == RESULT_OK){
            if(configured)
                startVpnService();
            else{
                Toast.makeText(context, "Empty Config", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startVpnService(){
        Intent intent = new Intent(MainActivity.this, IVIService.class);
        intent.putExtra("action", "start");
        intent.putExtra("serverIp", mServerIpv6Address);
        intent.putExtra("serverPort", mServerPort);
        intent.putExtra("localIp", mLocalIpv6Address);
        startService(intent);
    }

    private void stopVpnService(){
        Intent intent = new Intent(MainActivity.this, IVIService.class);
        intent.putExtra("action", "stop");
        startService(intent);
    }

    private void init(){
        checkInternetStatus();
        updateInformation();
    }

    private void startBackend(){

    }

    private void initTimer(){

    }

    private void checkInternetStatus(){
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        mLocalMacAddress = wifiInfo.getMacAddress();
        System.out.println("mac address: " + mLocalMacAddress);
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                if(networkInterface.getDisplayName().equals("wlan0") || networkInterface.getDisplayName().equals("eth0")) {
                    for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()){
                            if(inetAddress instanceof  Inet6Address) {
                                Matcher ipv6_matcher = Pattern.compile("%").matcher(inetAddress.getHostAddress());
                                if(ipv6_matcher.find()) {
                                    mLocalIpv6Address = inetAddress.getHostAddress().substring(0, ipv6_matcher.start());
                                }
                            }
                            else if(inetAddress instanceof Inet4Address){
                                Matcher ipv4_matcher = Pattern.compile("[.]").matcher(inetAddress.getHostAddress());
                                if(ipv4_matcher.find()){
                                    mLocalIpv4Address = inetAddress.getHostAddress();
                                }
                            }
                        }
                    }
                }
            }
        } catch (SocketException ex){
            Log.e("Ipv6 Address", ex.toString());
        }
        System.out.println("ipv4 addr: " + mLocalIpv4Address);
        System.out.println("ipv6 addr: " + mLocalIpv6Address);
    }

    @Override
    protected void onResume(){
        super.onResume();
        checkInternetStatus();
        updateInformation();
    }

}
