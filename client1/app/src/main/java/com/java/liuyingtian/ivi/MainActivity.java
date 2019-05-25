package com.java.liuyingtian.ivi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
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
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity{
    static {
        System.loadLibrary("native-lib");
    }

    private Context context;
    private String my_ipv6_addr = "";
    private String my_ipv4_addr = "";

    private String server_ipv6_addr = "2607:5501:3000:f36::2";
    private int server_port = 5678;

    private int running_state = 0;
    private String virtual_ipv4_addr = null;
    private long in_bytes = 0;
    private long out_bytes = 0;
    private long in_packets = 0;
    private long out_packets = 0;
    private long in_speed_bytes = 0;
    private long out_speed_bytes = 0;
    private long running_time = 0;

    private EditText serverIp, serverPort;
    private TextView uploadPackets, downloadPackets, connectTime, uploadSpeed, downloadSpeed, uploadFlow, downloadFlow;
    private TextView serverIpShow, serverPortShow, localIpv6AddrShow, localAllocatedIpShow;
    private Button confirm, connect, restart;
    private IntentFilter intentFilter;

    private  void layoutInit(){
        serverIp = findViewById(R.id.server_ip);
        serverPort = findViewById(R.id.server_port);
        serverIpShow = findViewById(R.id.server_ip_show);
        serverPortShow = findViewById(R.id.server_port_show);
        uploadPackets = findViewById(R.id.upload_packets);
        uploadFlow = findViewById(R.id.upload_flow);
        uploadSpeed = findViewById(R.id.upload_speed);
        downloadPackets = findViewById(R.id.download_packets);
        downloadFlow = findViewById(R.id.download_flow);
        downloadSpeed = findViewById(R.id.download_speed);
        connectTime = findViewById(R.id.connect_time);
        confirm = findViewById(R.id.confirm_button);
        connect = findViewById(R.id.connect_button);
        restart = findViewById(R.id.restart_button);
        localIpv6AddrShow = findViewById(R.id.local_ipv6_addr);
        localAllocatedIpShow = findViewById(R.id.allocated_ip);

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect.setEnabled(false);
                restart.setEnabled(false);
                confirm.setEnabled(false);

                if (running_state == 0) {
                    prepareVPNService();
                } else {
                    stopVPNService();
                }
            }
        });

        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect.setEnabled(false);
                restart.setEnabled(false);
                confirm.setEnabled(false);
                stopVPNService();
                prepareVPNService();
            }
        });

        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String port = serverPort.getText().toString();
                String portPattern = "^\\d{3,5}$";
                String ip = serverIp.getText().toString();
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
                    server_ipv6_addr = ip;
                    server_port = Integer.valueOf(port);
                    updateUI();
                    Toast.makeText(context, "IP and Port confirmed", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(context, "Invalid IP or Port", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private BroadcastReceiver vpnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(IVIService.BROADCAST_STATE)) {
                MainActivity.this.running_state = intent.getIntExtra("running_state", 0);
                MainActivity.this.virtual_ipv4_addr = intent.getStringExtra("virtual_ipv4_addr");
                MainActivity.this.in_bytes = intent.getLongExtra("in_bytes", 0);
                MainActivity.this.out_bytes = intent.getLongExtra("out_bytes", 0);
                MainActivity.this.in_packets = intent.getLongExtra("in_packets", 0);
                MainActivity.this.out_packets = intent.getLongExtra("out_packets", 0);
                MainActivity.this.in_speed_bytes = intent.getLongExtra("in_speed_bytes", 0);
                MainActivity.this.out_speed_bytes = intent.getLongExtra("out_speed_bytes", 0);
                MainActivity.this.running_time = intent.getLongExtra("running_time", 0);
                connect.setEnabled(true);
                restart.setEnabled(true);
            }
            updateUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        layoutInit();
        intentFilter = new IntentFilter();
        intentFilter.addAction(IVIService.BROADCAST_STATE);
    }

    @Override
    protected void onResume(){
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnReceiver, intentFilter);
    }
    @Override
    protected void onPostResume() {
        super.onPostResume();
        checkInternetStatus();
        updateUI();
    }

    @Override
    protected void onPause(){
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            startVPNService();
        }
    }

    private void prepareVPNService() {
        Intent intent = VpnService.prepare(MainActivity.this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    private void startVPNService() {
        Intent intent = new Intent(MainActivity.this, IVIService.class);
        intent.putExtra("command", "start");
        intent.putExtra("hostname", server_ipv6_addr);
        intent.putExtra("port", server_port);
        startService(intent);
    }

    private void stopVPNService() {
        Intent intent = new Intent(MainActivity.this, IVIService.class);
        intent.putExtra("command", "stop");
        startService(intent);
    }
    private void updateUI() {
        connect.setText(running_state == 0 ? "Connect" : "Disconnect");
        localIpv6AddrShow.setText(my_ipv6_addr);
        serverIpShow.setText(server_ipv6_addr);
        serverPortShow.setText("" + server_port);
        localAllocatedIpShow.setText(virtual_ipv4_addr);

        connectTime.setText(time(running_time));
        uploadFlow.setText(Formatter.formatShortFileSize(context, out_bytes));
        uploadSpeed.setText(Formatter.formatShortFileSize(context, out_speed_bytes));
        uploadPackets.setText(Formatter.formatShortFileSize(context, out_packets));

        downloadFlow.setText(Formatter.formatShortFileSize(context, in_bytes));
        downloadSpeed.setText(Formatter.formatShortFileSize(context, in_speed_bytes));
        downloadPackets.setText(Formatter.formatShortFileSize(context, in_packets));

        if (running_state == 0) {
            confirm.setEnabled(true);
            restart.setEnabled(false);
        } else {
            confirm.setEnabled(false);
            restart.setEnabled(true);
        }
    }

    private String time(long inttime) {
        long second = inttime % 60;
        inttime /= 60;
        long minute = inttime % 60;
        inttime /= 60;
        long hour = inttime;
        return String.format("%d:%02d:%02d", hour, minute, second);
    }

    public void checkInternetStatus() {
        my_ipv4_addr = "";
        my_ipv6_addr = "";
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                if(networkInterface.getDisplayName().equals("wlan0") || networkInterface.getDisplayName().equals("eth0")) {
                    for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()){
                            if(inetAddress instanceof Inet6Address) {
                                Matcher ipv6_matcher = Pattern.compile("%").matcher(inetAddress.getHostAddress());
                                if(ipv6_matcher.find()) {
                                    my_ipv6_addr = inetAddress.getHostAddress().substring(0, ipv6_matcher.start());
                                }
                            }
                            else if(inetAddress instanceof Inet4Address){
                                Matcher ipv4_matcher = Pattern.compile("[.]").matcher(inetAddress.getHostAddress());
                                if(ipv4_matcher.find()){
                                    my_ipv4_addr = inetAddress.getHostAddress();
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("ipv4 addr: " + my_ipv4_addr);
            System.out.println("ipv6 addr: " + my_ipv6_addr);
        } catch (SocketException ex){
            Log.e("Ipv6 Address", ex.toString());
        }
    }
}
