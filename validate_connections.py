#!/usr/bin/env python3
"""
validate_connections.py

External smoke test for web-socket-gateway.  Reads a gateway XML config
and verifies endpoint connectivity and forwarding behaviour against a
running application instance.

Usage:
  python3 validate_connections.py [config.xml] [--timeout SECS]

Requires: Python 3.8+
Optional: pip install websockets   (needed for WebSocket endpoint tests)
"""

import argparse
import asyncio
import os
import secrets
import socket
import struct
import sys
import threading
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

try:
    import websockets

    HAS_WEBSOCKETS = True
except ImportError:
    HAS_WEBSOCKETS = False

_NS = "http://github.com/web-socket-gateway/config/v1"
_ENDPOINT_TAGS = {"websocket-server", "tcp-server", "tcp-client", "tcp-hub", "udp-multicast"}
TIMEOUT_DEFAULT = 5.0


# ── Result tracking ──────────────────────────────────────────────────────────


@dataclass
class Result:
    name: str
    status: str  # PASS | FAIL | SKIP
    detail: str = ""


_results: list[Result] = []


def _record(name: str, status: str, detail: str = "") -> None:
    _results.append(Result(name, status, detail))
    tag = {"PASS": "[PASS]", "FAIL": "[FAIL]", "SKIP": "[SKIP]"}[status]
    msg = f"{tag} {name}"
    if detail:
        msg += f" — {detail}"
    print(msg)


def _pass(name: str, detail: str = "") -> None:
    _record(name, "PASS", detail)


def _fail(name: str, detail: str = "") -> None:
    _record(name, "FAIL", detail)


def _skip(name: str, detail: str = "") -> None:
    _record(name, "SKIP", detail)


# ── Config parsing ────────────────────────────────────────────────────────────


def parse_config(path: str):
    """Return (endpoints_dict, rules_list).

    endpoints_dict  {label: {"type": str, **xml_attrs}}
    rules_list      [(from_label, to_label), ...]
    """
    root = ET.parse(path).getroot()
    endpoints: dict = {}
    rules: list = []
    for child in root:
        local = child.tag.split("}")[-1] if "}" in child.tag else child.tag
        attrs = dict(child.attrib)
        if local in _ENDPOINT_TAGS:
            endpoints[attrs["label"]] = {"type": local, **attrs}
        elif local == "forward":
            rules.append((attrs["from"], attrs["to"]))
    return endpoints, rules


# ── Network helpers ───────────────────────────────────────────────────────────


def _connectable_host(bind_address: str) -> str:
    """Convert a listen address to a connectable address."""
    return "127.0.0.1" if bind_address in ("0.0.0.0", "", None) else bind_address


def tcp_connect(host: str, port: int, timeout: float) -> socket.socket:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(timeout)
    s.connect((_connectable_host(host), port))
    return s


def tcp_recv_containing(sock: socket.socket, payload: bytes, timeout: float) -> bool:
    """Read from *sock* until *payload* appears in the stream, or *timeout* expires."""
    buf = b""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        remaining = deadline - time.monotonic()
        sock.settimeout(min(max(remaining, 0.05), 0.5))
        try:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buf += chunk
            if payload in buf:
                return True
        except socket.timeout:
            continue
        except OSError:
            break
    return False


def _ws_url(ep: dict) -> str:
    host = _connectable_host(ep.get("bind-address", "0.0.0.0"))
    return f"ws://{host}:{ep['port']}{ep.get('path', '/ws')}"


def _udp_multicast_loopback(group: str, port: int, payload: bytes, timeout: float) -> bool:
    """Send a datagram to the multicast group and check it arrives back via loopback."""
    recv_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    recv_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        recv_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    except AttributeError:
        pass  # Not available on all platforms
    recv_sock.bind(("", port))
    mreq = struct.pack("4sL", socket.inet_aton(group), socket.INADDR_ANY)
    recv_sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
    recv_sock.settimeout(timeout)

    send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    send_sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
    try:
        send_sock.sendto(payload, (group, port))
        data, _ = recv_sock.recvfrom(4096)
        return payload in data
    except socket.timeout:
        return False
    finally:
        recv_sock.close()
        send_sock.close()


# ── Endpoint connectivity tests ───────────────────────────────────────────────


def test_connectivity(label: str, ep: dict, timeout: float) -> None:
    ep_type = ep["type"]
    name = f"Connectivity: {ep_type} '{label}'"

    try:
        if ep_type in ("tcp-server", "tcp-hub"):
            host = ep.get("bind-address", "0.0.0.0")
            port = int(ep["port"])
            s = tcp_connect(host, port, timeout)
            s.close()
            _pass(name, f"{_connectable_host(host)}:{port}")

        elif ep_type == "websocket-server":
            if not HAS_WEBSOCKETS:
                _skip(name, "install 'websockets' package to enable this test")
                return
            url = _ws_url(ep)

            async def _check():
                async with websockets.connect(url, open_timeout=timeout):
                    pass

            asyncio.run(_check())
            _pass(name, url)

        elif ep_type == "tcp-client":
            host = ep["host"]
            port = int(ep["port"])
            if host not in ("localhost", "127.0.0.1"):
                _skip(name, f"non-localhost target {host}:{port}")
                return
            s = tcp_connect(host, port, timeout)
            s.close()
            _pass(name, f"target server {host}:{port} reachable")

        elif ep_type == "udp-multicast":
            group = ep["group"]
            port = int(ep["port"])
            probe = secrets.token_bytes(8)
            if _udp_multicast_loopback(group, port, probe, timeout):
                _pass(name, f"loopback OK on {group}:{port}")
            else:
                _fail(name, f"no loopback datagram received from {group}:{port}")

    except Exception as exc:
        _fail(name, str(exc))


# ── TCP hub semantics test ────────────────────────────────────────────────────


def test_tcp_hub_semantics(label: str, ep: dict, timeout: float) -> None:
    """Verify hub peer-broadcast and sender-exclusion behaviour."""
    name = f"TCP hub semantics: '{label}'"
    host = ep.get("bind-address", "0.0.0.0")
    port = int(ep["port"])

    try:
        a = tcp_connect(host, port, timeout)
        b = tcp_connect(host, port, timeout)
        c = tcp_connect(host, port, timeout)
        try:
            # Wait for Netty to fire channelActive for all three connections.
            time.sleep(0.2)

            payload_a = secrets.token_bytes(16)
            a.sendall(payload_a)

            if not tcp_recv_containing(b, payload_a, timeout):
                _fail(name, "client B did not receive A's broadcast")
                return
            if not tcp_recv_containing(c, payload_a, timeout):
                _fail(name, "client C did not receive A's broadcast")
                return

            # Send from B so we can drain A's stream and confirm it carries only
            # B's data, not A's own earlier message.
            payload_b = secrets.token_bytes(8)
            b.sendall(payload_b)

            if not tcp_recv_containing(a, payload_b, timeout):
                _fail(name, "client A did not receive B's broadcast")
                return

            # A's stream should now be empty (it must not have received payload_a).
            a.settimeout(0.3)
            stray = b""
            try:
                stray = a.recv(256)
            except socket.timeout:
                pass

            if stray:
                _fail(name, f"client A received unexpected data (sender-exclusion broken): {stray!r}")
                return

            _pass(name, "peer broadcast OK, sender exclusion OK")
        finally:
            a.close()
            b.close()
            c.close()

    except Exception as exc:
        _fail(name, str(exc))


# ── Forwarding rule tests ─────────────────────────────────────────────────────


def test_forward_rule(
    from_label: str,
    to_label: str,
    endpoints: dict,
    timeout: float,
) -> None:
    """Inject a unique payload into the source endpoint and verify it arrives at the destination."""
    name = f"Forward rule: '{from_label}' → '{to_label}'"
    src = endpoints[from_label]
    dst = endpoints[to_label]
    src_type = src["type"]
    dst_type = dst["type"]

    # Declare unsupported combinations up front.
    if src_type == "tcp-client" or dst_type == "tcp-client":
        _skip(
            name,
            "tcp-client forwarding requires acting as the remote server — not supported",
        )
        return
    if src_type == "udp-multicast" or dst_type == "udp-multicast":
        _skip(name, "UDP multicast forwarding tests are not supported")
        return
    if (src_type == "websocket-server" or dst_type == "websocket-server") and not HAS_WEBSOCKETS:
        _skip(name, "install 'websockets' package to enable WebSocket forwarding tests")
        return

    payload = secrets.token_bytes(16)

    # Observer runs in a background thread so it is ready before injection.
    received_flag = threading.Event()
    observe_ready = threading.Event()
    observe_errors: list[str] = []

    # -- Observer implementations -------------------------------------------

    def _observe_tcp(ep: dict) -> None:
        host = ep.get("bind-address", "0.0.0.0")
        port = int(ep["port"])
        try:
            s = tcp_connect(host, port, timeout)
            observe_ready.set()
            try:
                if tcp_recv_containing(s, payload, timeout):
                    received_flag.set()
                else:
                    observe_errors.append("payload not seen within timeout")
            finally:
                s.close()
        except Exception as exc:
            observe_errors.append(str(exc))
            observe_ready.set()

    async def _observe_ws_coro(ep: dict) -> None:
        url = _ws_url(ep)
        try:
            async with websockets.connect(url, open_timeout=timeout) as ws:
                observe_ready.set()
                msg = await asyncio.wait_for(ws.recv(), timeout=timeout)
                data = msg if isinstance(msg, bytes) else msg.encode()
                if payload in data:
                    received_flag.set()
                else:
                    observe_errors.append(f"payload mismatch at destination: {data!r}")
        except Exception as exc:
            observe_errors.append(str(exc))
            observe_ready.set()

    def _observe_ws(ep: dict) -> None:
        asyncio.run(_observe_ws_coro(ep))

    # -- Build observer thread -----------------------------------------------

    if dst_type in ("tcp-server", "tcp-hub"):
        obs = threading.Thread(target=_observe_tcp, args=(dst,), daemon=True)
    elif dst_type == "websocket-server":
        obs = threading.Thread(target=_observe_ws, args=(dst,), daemon=True)
    else:
        _skip(name, f"unsupported destination type: {dst_type}")
        return

    obs.start()
    if not observe_ready.wait(timeout):
        _fail(name, "observer could not connect to destination endpoint")
        return

    # Brief pause so the gateway has registered the observer's inbound channel.
    time.sleep(0.05)

    # -- Inject data into source endpoint ------------------------------------

    try:
        if src_type == "tcp-server":
            host = src.get("bind-address", "0.0.0.0")
            port = int(src["port"])
            s = tcp_connect(host, port, timeout)
            s.sendall(payload)
            time.sleep(0.05)
            s.close()

        elif src_type == "tcp-hub":
            host = src.get("bind-address", "0.0.0.0")
            port = int(src["port"])
            s = tcp_connect(host, port, timeout)
            # Let Netty register the new hub client before sending.
            time.sleep(0.15)
            s.sendall(payload)
            time.sleep(0.05)
            s.close()

        elif src_type == "websocket-server":

            async def _inject_ws() -> None:
                url = _ws_url(src)
                async with websockets.connect(url, open_timeout=timeout) as ws:
                    await ws.send(payload)
                    await asyncio.sleep(0.1)

            asyncio.run(_inject_ws())

    except Exception as exc:
        _fail(name, f"injection failed: {exc}")
        return

    # -- Collect result ------------------------------------------------------

    if received_flag.wait(timeout + 0.5):
        _pass(name, "payload forwarded correctly")
    elif observe_errors:
        _fail(name, observe_errors[0])
    else:
        _fail(name, "payload not received at destination within timeout")


# ── Orchestration ─────────────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Validate a running web-socket-gateway instance.\n"
            "Reads the gateway config XML and exercises each endpoint and\n"
            "forwarding rule against the live application."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "config",
        nargs="?",
        default="example-config.xml",
        help="Path to gateway XML config file (default: example-config.xml)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=TIMEOUT_DEFAULT,
        metavar="SECS",
        help=f"Per-operation timeout in seconds (default: {TIMEOUT_DEFAULT})",
    )
    args = parser.parse_args()

    if not os.path.exists(args.config):
        print(f"error: config file not found: {args.config}", file=sys.stderr)
        sys.exit(2)

    endpoints, rules = parse_config(args.config)

    print(f"Config    : {args.config}")
    print(f"Endpoints : {len(endpoints)},  Forward rules: {len(rules)}")
    print()

    # 1. Connectivity — one test per endpoint
    for label, ep in endpoints.items():
        test_connectivity(label, ep, args.timeout)
    print()

    # 2. TCP hub semantics — sender-exclusion and peer-broadcast
    hub_labels = [lbl for lbl, ep in endpoints.items() if ep["type"] == "tcp-hub"]
    for label in hub_labels:
        test_tcp_hub_semantics(label, endpoints[label], args.timeout)
    if hub_labels:
        print()

    # 3. Forwarding rules — inject at source, verify at destination
    for from_label, to_label in rules:
        test_forward_rule(from_label, to_label, endpoints, args.timeout)

    # Summary
    n_pass = sum(1 for r in _results if r.status == "PASS")
    n_fail = sum(1 for r in _results if r.status == "FAIL")
    n_skip = sum(1 for r in _results if r.status == "SKIP")
    print()
    print(f"Results: {n_pass} passed, {n_fail} failed, {n_skip} skipped")

    sys.exit(0 if n_fail == 0 else 1)


if __name__ == "__main__":
    main()
