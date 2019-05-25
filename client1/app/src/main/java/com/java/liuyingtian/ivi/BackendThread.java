package com.java.liuyingtian.ivi;

public class BackendThread implements Runnable {
    private int backReadFd;
    private int backWriteFd;
    private String serverIp;
    private int serverPort;

    public BackendThread(String ip, int port, int readFd, int writeFd){
        serverIp = ip;
        serverPort = port;
        backReadFd = readFd;
        backWriteFd = writeFd;
    }

    static {
        System.loadLibrary("native-lib");
    }
    public native int backend_thread(String server_ip, int server_port, int back_read_fd, int back_write_fd);
    @Override
    public void run() {
        int ret = backend_thread(serverIp, serverPort, backReadFd, backWriteFd);
    }
}
