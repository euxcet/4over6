//
// Created by liu-yt16 on 2019/5/17.
//

#ifndef CLIENT1_MAIN_H
#define CLIENT1_MAIN_H
#include "all.h"
#include <string>

#define IP_REQUEST (100)
#define IP_RESPONSE (101)
#define HTTP_REQUEST (102)
#define HTTP_RESPONSE (103)
#define HEARTBEAT (104)

#define WAIT_TIME (1000)
#define TUN_BUF_SIZE (4 * 1024 * 1024)
#define SOCK_BUF_SIZE (4 * 1024 * 1024)
#define HEART_BEAT_TIMEOUT (60)
#define HEART_BEAT_INTERVAL (20)

#define IP_CONFIG (2)
#define SET_TUN (3)
#define EXIT (1)
#define FETCH_STATE (4)

struct Message{
    int length;
    char type;
}__attribute__((packed));

//struct Buffer{
//    char* bufptr;
//    size_t buflen;
//    size_t datalen;
//    size_t readpos;
//    size_t writepos;
//};

//int buf_init(Buffer* buf, char* bufptr, size_t buflen);
//int read(int fd, Buffer* buf, int len);

int backend_main(const char* server_ip, int server_port, int backend_read_fd, int backend_write_fd);
int socket_init(const char* server_ip, int server_port);

int write_pipe(unsigned char* buf, size_t write_size);
int read_pipe(unsigned char* buf, size_t read_size);
char* read_ip(unsigned char* to, char* from);

int handle_response();
int handle_pipe();
int handle_tun();
int send_ip_request();
int set_tun();
int init();
#endif //CLIENT1_MAIN_H
