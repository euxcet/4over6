#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <netinet/ip.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <bits/ioctls.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <errno.h>

#define ADDRESS_PREFIX "13.8.0"

const int MAX_FDS = 1024;
const int SERVER_PORT = 7654;


int socket_init() {
	int fd;

	if ((fd = socket(AF_INET6, SOCK_STREAM, 0)) < 0) {
		printf("Error: open socket failed.\n");
		return -1;
	}

	// enable address reuse
	int on = 1;
	if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (const void *)&on, sizeof(on)) < 0) {
		printf("Error: enable address reuse failed.\n");
		return -1;
	}

	// bind server address
	struct sockaddr_in6 serveraddr;
	memset(&serveraddr, 0, sizeof(serveraddr));
	serveraddr.sin6_family = AF_INET6;
	serveraddr.sin6_port = htons(SERVER_PORT);
	serveraddr.sin6_addr = in6addr_any;

	if (bind(fd, (struct sockaddr *)&serveraddr, sizeof(serveraddr)) < 0) {
		printf("Error: bind server address failed.\n");
		return -1;
	}

	if (listen(fd, MAX_FDS) < 0) {
		printf("Error: listen on socket failed.\n");
		return -1;
	}


	return fd;
}

int tun_init() {
	int fd;

	if ((fd = open("/dev/net/tun", O_RDWR)) < 0) {
		printf("Error: can't open /dev/net/tun\n");
		return -1;
	}

	struct ifreq ifr;
	memset(&ifr, 0, sizeof(ifr));
	ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
	strncpy(ifr.ifr_name, "over6", IFNAMSIZ);

	if (ioctl(fd, TUNSETIFF, (void *)&ifr) == -1) {
		printf("Error: set tun interface name failed.\n");
		return -1;
	}


	// enable interface and net
	system("ifconfig over6 " ADDRESS_PREFIX ".1 netmask 255.255.255.0 up");
	system("iptables -t nat -A POSTROUTING -s " ADDRESS_PREFIX ".0/24 -j MASQUERADE");

}

int main() {
	int fd;
	if ((fd = socket_init()) < 0) {
		exit(1);
	}
	printf("server fd = %d\n", fd);

	tun_init();
}
