//
// Created by liu-yt16 on 2019/5/17.
//
#include "main.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cstdlib>
#include <vector>
#include <poll.h>
#include <cstring>
#include <string>
#include <ctime>
using namespace std;

static int back_read_fd;
static int back_write_fd;
static int sock_fd;
static int tun_fd;

static char sock_buf[SOCK_BUF_SIZE];
static ssize_t sock_buf_off;

time_t heart_beat_time;
time_t last_send_heart_beat;
uint64_t download_bytes;
uint64_t upload_bytes;
uint64_t download_packets;
uint64_t upload_packets;

//int buf_init(Buffer* buf, char* bufptr, size_t buflen){
//    if(bufptr == NULL || buf == NULL || buflen <= 0) return -1;
//    buf -> bufptr = bufptr;
//    buf -> buflen = buflen;
//    buf -> datalen = 0;
//    buf -> readpos = 0;
//    buf -> writepos = 0;
//    return 0;
//}
//
//int recv(int sock_fd, Buffer* buf, int len){
//    ssize_t size = recv(sock_fd, sock_buf + sock_buf_off, SOCK_BUF_SIZE - sock_buf_off, 0);
//}
//
//int read(Buffer* src, char* dest, int len){
//    
//}
//



int write_pipe(unsigned char* buf, size_t write_size){
    return (int)write(back_write_fd, buf, write_size);
}
int read_pipe(unsigned char* buf, size_t read_size){
    size_t offset = 0;
    while(offset < read_size){
        offset += read(back_read_fd, buf + offset, read_size - offset);
    }
    return (int)offset;
}

char* read_ip(unsigned char* to, char* from){
    int ip[4];
    sscanf(from, "%d.%d.%d.%d", ip, ip + 1, ip + 2, ip + 3);
    for(int i = 0; i < 4; i ++){
        to[i] = (unsigned char)ip[i];
    }
    while(*from != ' ') from ++;
    return from + 1;
}

int socket_init(const char* server_ip, int server_port){
//    server_port = server_port_;
//    memcpy(server_ip, server_ip_, strlen(server_ip_) + 1);
//    LOGD("socket_init(%s, %d)", server_ip, server_port);
    sock_fd = socket(AF_INET6, SOCK_STREAM, 0);
    ERROR_CHECK(sock_fd, fail);
    struct sockaddr_in6 server_addr;
    memset((char*)&server_addr, 0, sizeof(server_addr));
    server_addr.sin6_family = AF_INET6;
    server_addr.sin6_port = htons((unsigned short)server_port);

    ERROR_CHECK(inet_pton(AF_INET6, server_ip, &server_addr.sin6_addr), fail);
    ERROR_CHECK(connect(sock_fd, (struct sockaddr*)&server_addr, sizeof(server_addr)), fail);
    ASSERT(write_pipe((unsigned char*)&sock_fd, 4) == 4, fail);
    LOGD("sock created and connected %d", sock_fd);
    return 0;
    fail:
    return -1;
}

int init(){
    sock_fd = 0;
    tun_fd = 0;
    sock_buf_off = 0;
    heart_beat_time = time(NULL);
    download_bytes = 0L;
    upload_bytes = 0L;
    download_packets = 0L;
    upload_bytes = 0L;
}
void release_fd(int fd){
    if(fd > 0)
        close(fd);
}

int send_ip_request(){
    Message ip_request;
    ip_request.type = IP_REQUEST;
    ip_request.length = sizeof(Message);
    LOGD("send ip request");
    ASSERT(send(sock_fd, &ip_request, sizeof(ip_request), 0) == sizeof(ip_request), fail);
    upload_bytes += sizeof(ip_request);
    upload_packets += 1;
    return 0;
    fail:
    return -1;
}

int set_tun(){
    int buf = 0;
    ASSERT(read_pipe((uint8_t *)&buf, 4) == 4, fail);
    tun_fd = buf;
    LOGD("set tun %d", tun_fd);
    ASSERT(tun_fd > 0, fail);
    return 0;
    fail:
    return -1;
}
int handle_response(){
    ssize_t size = recv(sock_fd, sock_buf + sock_buf_off, SOCK_BUF_SIZE - sock_buf_off, 0);
    download_bytes += size;
    download_packets += 1;
    ERROR_CHECK(size, fail);
    sock_buf_off += size;
    while(1){
        // |-----------------|------------|-----------|---
        //  sizeof(Message)    data
        // |<----  response.length   ---->|
        // |<----       sock_buf_off              --->|
        // sock_buf
        if(sock_buf_off < sizeof(Message)) break; // 内容不足一个头
        Message response;
        response = *(Message*)&sock_buf;
        if(sock_buf_off < response.length) break; // 内容不足一个包
        switch(response.type){
            case IP_RESPONSE:
                LOGD("ip response");
                // 拿到ip, 写入管道
                uint8_t ip_router_dns[30];
                uint8_t* to;
                to = ip_router_dns;
                char* from;
                from = sock_buf + sizeof(Message);
                from = read_ip(to, from); // ip
                from = read_ip(to + 4, from); // router
                from = read_ip(to + 8, from); // dns1
                from = read_ip(to + 12, from); // dns2
                from = read_ip(to + 16, from); // dns3
                ASSERT(write_pipe(to, 20) == 20, fail);
                break;
            case HTTP_RESPONSE:
                LOGD("http response");
                // 写入tun
                if(tun_fd > 0)
                    ASSERT(write(tun_fd, sock_buf + sizeof(Message), response.length - sizeof(Message)) == response.length - sizeof(Message), fail);
                break;
            case HEARTBEAT:
                LOGD("hb");
                // 更新时间信息
                time_t now;
                now = time(NULL);
                if(now - heart_beat_time > HEART_BEAT_TIMEOUT)
                    goto fail;
                else{
                    heart_beat_time = now;
                }
                break;
            default:
                LOGD("unknown response type: %d", response.type);
                break;
        }
        // 将该response移出缓冲区
        memmove(sock_buf, sock_buf + response.length, sock_buf_off - response.length);
        sock_buf_off -= response.length;
    }
    return 0;
    fail:
    return -1;
}
int handle_tun(){
    static uint8_t buf[TUN_BUF_SIZE];
    ASSERT(tun_fd > 0, fail);

    ssize_t size;
    ERROR_CHECK(size = read(tun_fd, buf, TUN_BUF_SIZE), fail);
    if(size == 0) return 0;

    Message tun_msg;
    tun_msg.length = sizeof(Message) + size;
    tun_msg.type = HTTP_REQUEST;
    ASSERT(send(sock_fd, &tun_msg, sizeof(Message), 0) == sizeof(Message), fail);
    ASSERT(send(sock_fd, buf, size, 0) == size, fail);
    upload_bytes += sizeof(Message) + size;
    upload_packets += 1;
    LOGD("upload : %lld", upload_bytes);
    return 0;
    fail:
    return -1;
}

int handle_pipe(){
    uint8_t ins;
    read_pipe(&ins, 1);
    switch(ins){
        case IP_CONFIG:
            ERROR_CHECK(send_ip_request(), fail);
            break;
        case SET_TUN:
            ERROR_CHECK(set_tun(), fail);
            break;
        case EXIT:
            LOGE("exit");
            break;
        case FETCH_STATE:
            ASSERT(write_pipe((uint8_t*)&download_bytes, 8) == 8, fail);
            ASSERT(write_pipe((uint8_t*)&upload_bytes, 8) == 8, fail);
            ASSERT(write_pipe((uint8_t*)&download_packets, 8) == 8, fail);
            ASSERT(write_pipe((uint8_t*)&upload_packets, 8) == 8, fail);
            break;
        default:
            LOGE("handle pipe error, error: %d", ins);
            goto fail;
    }
    return ins;
    fail:
    return -1;
}

int backend_main(const char* server_ip, int server_port, int backend_read_fd, int backend_write_fd){
    back_read_fd = backend_read_fd;
    back_write_fd = backend_write_fd;
    LOGD("read pipe: %d", back_read_fd);
    LOGD("write pipe: %d", back_write_fd);
    sock_fd = 0;
    tun_fd = 0;
    sock_buf_off = 0;
    heart_beat_time = time(NULL);
    last_send_heart_beat = time(NULL);
    download_bytes = 0L;
    upload_bytes = 0L;
    download_packets = 0L;
    upload_bytes = 0L;
    LOGD("init done");
    LOGD("ip: %s, port: %d", server_ip, server_port);
    int ret = socket_init(server_ip, server_port);
    LOGD("socket init");
    if(ret < 0) return -1;
    vector<struct pollfd> fds;
    fds.reserve(3);
    struct pollfd temp;
    LOGD("enter while");
    while(1){
        fds.clear();
        temp.fd = sock_fd;
        temp.events = POLLRDNORM | POLL_ERR;
        temp.revents = 0;

        fds.push_back(temp);
        temp.fd = back_read_fd;

        fds.push_back(temp);
        if(tun_fd > 0){
            temp.fd = tun_fd;
            fds.push_back(temp);
        }

        if(time(NULL) - last_send_heart_beat > HEART_BEAT_INTERVAL){
            Message msg;
            msg.type = HEARTBEAT;
            msg.length = sizeof(Message);
            ASSERT(send(sock_fd, &msg, sizeof(Message), 0) == sizeof(Message), fail);
            upload_bytes += sizeof(Message);
            upload_packets += 1;
            last_send_heart_beat = time(NULL);
        }

        int active_count;
        active_count = poll(fds.data(), (nfds_t)fds.size(), WAIT_TIME);
        ERROR_CHECK(active_count, fail);
        if(active_count == 0)
            // no active fd
            continue;

        for(int i = 0; i < fds.size(); i ++){
            if((fds[i].revents & POLLRDNORM) == POLLRDNORM){
                int command;
                switch(i){
                    case 0:
                        LOGD("handle response");
                        ASSERT(handle_response() >= 0, fail);
                        break;
                    case 1:
                        LOGD("handle pipe");
                        command = handle_pipe();
                        ASSERT(command >= 0, fail);
                        if(command == EXIT)
                            goto exit;
                        break;
                    case 2:
                        LOGD("handle tun");
                        ASSERT(handle_tun() >= 0, fail);
                        break;
                    default:
                        LOGE("have data in unknown fd %d", fds[i].fd);
                        goto fail;
                        break;
                }
            }
        }
    }
    return 0;
    fail:
    exit:
    release_fd(tun_fd);
    release_fd(sock_fd);
    release_fd(back_write_fd);
    release_fd(back_read_fd);
    return -1;
}


