#!/usr/bin/env python3
"""
Unix socket proxy that rewrites Docker API version < 1.44 to 1.44
so that docker-java (which defaults to /v1.32/ URL paths) works with Docker 29.x
which requires minimum API 1.44.

Usage: python3 docker-version-proxy.py [proxy_socket] [target_socket]
  proxy_socket  defaults to /tmp/docker-proxy.sock
  target_socket defaults to /var/run/docker.sock
"""
import socket
import os
import threading
import re
import sys

PROXY_PATH  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/docker-proxy.sock'
TARGET_PATH = sys.argv[2] if len(sys.argv) > 2 else '/var/run/docker.sock'


def rewrite_request_line(data):
    try:
        pos = data.find(b'\r\n')
        if pos < 0:
            return data
        request_line = data[:pos].decode('utf-8', errors='replace')

        def replace_version(m):
            major, minor = int(m.group(1)), int(m.group(2))
            if major == 1 and minor < 44:
                return '/v1.44/'
            return m.group(0)

        new_line = re.sub(r'/v(\d+)\.(\d+)/', replace_version, request_line)
        if new_line != request_line:
            return new_line.encode('utf-8') + data[pos:]
        return data
    except Exception:
        return data


def forward_stream(src, dst, rewrite=False, buf_size=65536):
    try:
        while True:
            data = src.recv(buf_size)
            if not data:
                break
            if rewrite:
                data = rewrite_request_line(data)
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except Exception:
            pass


def handle_client(client_sock):
    try:
        target_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        target_sock.connect(TARGET_PATH)

        t1 = threading.Thread(target=forward_stream, args=(client_sock, target_sock, True), daemon=True)
        t2 = threading.Thread(target=forward_stream, args=(target_sock, client_sock, False), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
    except Exception:
        pass
    finally:
        try:
            client_sock.close()
        except Exception:
            pass


if os.path.exists(PROXY_PATH):
    os.remove(PROXY_PATH)

server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
server.bind(PROXY_PATH)
server.listen(100)
os.chmod(PROXY_PATH, 0o666)

print(f"Docker API version proxy: {PROXY_PATH} -> {TARGET_PATH}", flush=True)
try:
    while True:
        client, _ = server.accept()
        t = threading.Thread(target=handle_client, args=(client,), daemon=True)
        t.start()
except KeyboardInterrupt:
    print("Proxy stopped")
finally:
    server.close()
    if os.path.exists(PROXY_PATH):
        os.remove(PROXY_PATH)
