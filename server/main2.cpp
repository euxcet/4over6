#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <netinet/in.h>
#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <unistd.h>
#include <netdb.h>
#include <net/if.h>
#include <string>
#include <assert.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <linux/ip.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <fcntl.h>

#define MSG_IP_REQUEST 100
#define MSG_IP_RESPONSE 101
#define MSG_NET_REQUEST 102
#define MSG_NET_RESPONSE 103
#define MSG_KEEPALIVE 104

#define SERVER_PORT 5678

#define MAX_FDS 128
#define MAX_LENGTH 4096
#define BUF_SIZE 1024
#define MAX_EVENT 128

#define MSG_HEADER_SIZE 5

struct Message {
	int length;
	char type;
	char data[MAX_LENGTH];
};

int server_fd;
int client_fd;
int listen_epoll_fd;
int recv_epoll_fd;
int tun_fd;

in_addr tun_addr;

int accepted = false;

int make_socket_nonblocking(int fd) {
	int flag;
	if ((flag = fcntl(fd, F_GETFL, 0)) < 0) {
		perror("fcntl");
		return -1;
	}
	flag |= O_NONBLOCK;
	if (fcntl(fd, F_SETFL, flag)) {
		perror("fcntl");
		return -1;
	}
	return 0;
}

int create_socket() {
	int fd;

	if ((fd = socket(AF_INET6, SOCK_STREAM, 0)) < 0) {
		printf("Error: open socket failed.\n");
		return -1;
	}

	int on = 1;
	if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (const void *)&on, sizeof(on)) < 0) {
		printf("Error: enable address reuse failed.\n");
		return -1;
	}

	struct sockaddr_in6 serveraddr;
	memset(&serveraddr, 0, sizeof(serveraddr));
	serveraddr.sin6_family = AF_INET6;
	serveraddr.sin6_port = htons(SERVER_PORT);
	serveraddr.sin6_addr = in6addr_any;

	if (bind(fd, (struct sockaddr *)&serveraddr, sizeof(serveraddr)) < 0) {
		printf("Error: bind server address failed.\n");
		close(fd);
		return -1;
	}

	if (listen(fd, MAX_FDS) < 0) {
		printf("Error: listen on socket failed.\n");
		close(fd);
		return -1;
	}
	make_socket_nonblocking(fd);
	return fd;

}

int create_epoll() {
	int fd = epoll_create(MAX_EVENT);
	if (fd < 0) {
		return -1;
	}
	return fd;
}

int epoll_add_fd(int epoll_fd, int fd) {
	struct epoll_event event;
	event.data.fd = fd;
	event.events = EPOLLIN | EPOLLHUP;
	if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, fd, &event) < 0) {
		printf("Error: failed to add fd %d to epoll\n", fd);
		return -1;
	}
	return 0;
}

int epoll_del_fd(int epoll_fd, int fd) {
	if (epoll_ctl(epoll_fd, EPOLL_CTL_DEL, fd, NULL) == -1) {
		perror("epoll ctl");
		return -1;
	}
	return 0;
}

int create_tun(const char* dev, const char *addr) {
	int fd, err;
	if ((fd = open("/dev/net/tun", O_RDWR)) < 0) {
		printf("Create tun failed\n");
		return fd;
	}

	struct ifreq ifr;
	memset(&ifr, 0, sizeof(struct ifreq));

	ifr.ifr_flags |= IFF_TUN | IFF_NO_PI;

	strncpy(ifr.ifr_name, dev, IFNAMSIZ);

	if ((err = ioctl(fd, TUNSETIFF, (void*)&ifr)) < 0) {
		printf("Set tun device name failed\n");
		close(fd);
		return err;
	}

	make_socket_nonblocking(fd);

	tun_addr = {0};
	inet_aton(addr, &tun_addr);

	char buffer[256];
	sprintf(buffer, "ip link set dev %s up", ifr.ifr_name);
	system(buffer);
	sprintf(buffer, "ip a add 10.0.0.1/24 dev %s", ifr.ifr_name);
	system(buffer);
	sprintf(buffer, "ip link set dev %s mtu %u", ifr.ifr_name, 1500 - MSG_HEADER_SIZE);
	system(buffer);
	return fd;
}

void process_server() {
	struct sockaddr_in6 addr;
	memset(&addr, 0, sizeof(addr));
	socklen_t addrlen = sizeof(addr);

	if ((client_fd = accept(server_fd, (struct sockaddr *)&addr, &addrlen)) < 0) {
		printf("Error: failed to accept connection\n");
		return;
	}

	char peer6[BUF_SIZE];
	getpeername(client_fd, (struct sockaddr *)&addr, &addrlen);
	inet_ntop(AF_INET6, &addr.sin6_addr, peer6, sizeof(peer6));
	printf("--- new client ---\n");
	printf(" fd : %d\n", client_fd);
	printf("addr: %s\n", peer6);
	printf("port: %d\n", ntohs(addr.sin6_port));
	printf("------------------\n");

	make_socket_nonblocking(client_fd);

	struct timeval timeout;
	timeout.tv_sec = 100;
	timeout.tv_usec = 0;
	setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
	int enable = 1;
	setsockopt(client_fd, SOL_TCP, TCP_NODELAY, &enable, sizeof(enable));

	if (epoll_add_fd(recv_epoll_fd, client_fd) < 0) {
		printf("Error: failed to add client fd %d to epoll\n", client_fd);
		return;
	}
	accepted = true;
}

int receive(int fd, char *buf, int len) {
	int size = 0;
	for(int offset = 0; offset < len; offset += size) {
		size = read(fd, buf + offset, len - offset);
		if (size == -1) {
			usleep(100);
			size = 0;
			continue;
		}
		else if (size == 0) {
			return 0;
		}
		else if (size < 0 ) {
			perror("receive error");
			return -1;
		}
	}
	return len;
}

void process_tun() {
	Message msg;
	int n = read(tun_fd, msg.data, MAX_LENGTH);
	n += MSG_HEADER_SIZE;
	printf("tun [length = %d]\n", n);
	iphdr *hdr = (struct iphdr *)msg.data;
	printf("version %d\n", hdr -> version);
	if (hdr -> version == 4) {
		if (hdr -> protocol == 1) { // ICMP
			hdr -> saddr = tun_addr.s_addr;
			write(tun_fd, &msg.data, n);
			return;
		}
		else if (hdr -> daddr == tun_addr.s_addr) {
			msg.type = MSG_NET_RESPONSE;
			msg.length = n;
			write(client_fd, &msg, msg.length);
			return;
		}
	}
}

int process_client(struct epoll_event event) {
	printf("message from client\n");
	if (event.events & EPOLLHUP)
		return -1;
	Message msg;
	int n = receive(client_fd, (char*)&msg, MSG_HEADER_SIZE);
	if (n < 0) {
		return -1;
	}



	if (msg.type == MSG_IP_REQUEST) {
		printf("IP REQUEST\n");
		msg.type = MSG_IP_RESPONSE;
		snprintf(msg.data, MAX_LENGTH, "%s 0.0.0.0 202.38.120.242 8.8.8.8 202.106.0.20 ", inet_ntoa(tun_addr));
		msg.length = strlen(msg.data) + MSG_HEADER_SIZE;
		write(client_fd, &msg, msg.length);
	}
	else if (msg.type == MSG_NET_REQUEST) {
		printf("NET REQUEST  [length = %d]\n", msg.length);
		int len = receive(client_fd, (char*)msg.data, msg.length - MSG_HEADER_SIZE);
		if (len == msg.length - MSG_HEADER_SIZE) {
			iphdr *hdr = (struct iphdr *)msg.data;
			printf("	version = %d\n", hdr -> version);
			if (hdr -> version == 4) {
				hdr -> saddr = tun_addr.s_addr;
			}
			if (hdr -> saddr == tun_addr.s_addr) {
				write(tun_fd, msg.data, msg.length - MSG_HEADER_SIZE);
			}
		}
		else {
			return -1;
		}
	}
	else if (msg.type == MSG_KEEPALIVE) {
		printf("KEEP ALIVE\n");
	}
	return 0;
}

void process_epoll(int fd, int timeout) {
	epoll_event events[MAX_EVENT];
	int count = epoll_wait(fd, events, MAX_EVENT, timeout);
	for(int i = 0; i < count; i++) {
		if (events[i].data.fd == server_fd) {
			process_server();
		}
		else if (events[i].data.fd == tun_fd) {
			process_tun();
		}
		else {
			if (process_client(events[i]) < 0) {
				printf("disconnected [fd = %d]\n ", events[i].data.fd);
				epoll_del_fd(recv_epoll_fd, events[i].data.fd);
				close(events[i].data.fd);
				accepted = 0;
				return;
			}
		}
	}
}

void clear() {
	if (listen_epoll_fd > 0) {
		close(listen_epoll_fd);
	}
	if (server_fd > 0) {
		close(server_fd);
	}
	if (recv_epoll_fd > 0) {
		close(recv_epoll_fd);
	}
	if (tun_fd > 0) {
		close(tun_fd);
	}
}


time_t heartbeat_ts;
int main() {
	listen_epoll_fd = create_epoll();
	server_fd = create_socket();
	epoll_add_fd(listen_epoll_fd, server_fd);

	system("iptables -F");
	system("iptables -t nat -F");
	system("echo \"1\" > /proc/sys/net/ipv4/ip_forward");
	system("iptables -A FORWARD -j ACCEPT");
	system("iptables -t nat -A POSTROUTING -s 10.0.0.0/8 -j MASQUERADE");

	recv_epoll_fd = create_epoll();
	tun_fd = create_tun("4over6", "10.0.0.3");
	epoll_add_fd(recv_epoll_fd, tun_fd);

	printf("listen epoll fd = %d\n", listen_epoll_fd);
	printf("server fd = %d\n", server_fd);
	printf("recv epoll fd = %d\n", recv_epoll_fd);
	printf("tun fd = %d\n", tun_fd);

	while (true) {
		if (!accepted) {
			process_epoll(listen_epoll_fd, 5);
		}
		else {
			process_epoll(recv_epoll_fd, 1);
		}
		if (accepted && time(NULL) - heartbeat_ts > 20) {
			printf("send heartbeat %u\n", (unsigned int)heartbeat_ts);
			heartbeat_ts = time(NULL);
			Message msg;
			msg.type = MSG_KEEPALIVE;
			msg.length = MSG_HEADER_SIZE;
			write(client_fd, &msg, msg.length);
		}
	}

	clear();
}
