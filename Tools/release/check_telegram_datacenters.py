#!/usr/bin/env python3
"""Telegram DC network reachability probe for CI.

This is *not* an MTProto authorization or account login test. Production and
test data centres are separate: a successful TCP handshake only confirms
reachability from the runner's current network. No Telegram messages, login
codes, API hashes or tokens are sent.
"""
import argparse
import json
import os
import re
import socket
import sys

# Initial bootstrap IPs for DC2. Telegram may rotate access points; the
# running Telegram client should use MTProto help.getConfig for actual DCs.
DATACENTERS = {
    "production": ("149.154.167.51", 443),
    "test": ("149.154.167.40", 443),
}


def credentials_status(env):
    api_id = env.get("TELEGRAM_API_ID", "")
    api_hash = env.get("TELEGRAM_API_HASH", "")
    valid_id = bool(re.fullmatch(r"[1-9][0-9]{0,9}", api_id)) and int(api_id) <= 2147483647
    valid_hash = bool(re.fullmatch(r"[0-9a-fA-F]{32}", api_hash))
    return {"api_id_present_and_valid": valid_id, "api_hash_present_and_valid": valid_hash}


def check_tcp(host, port, timeout):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True, None
    except (OSError, TimeoutError):
        # Do not print raw exception messages; proxies sometimes expose details.
        return False, "tcp_connection_failed"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=["production", "test", "both"], default="both")
    parser.add_argument("--timeout", type=float, default=4.0)
    parser.add_argument("--require-credentials", action="store_true")
    parser.add_argument("--strict-network", action="store_true")
    parser.add_argument("--output", help="Optional JSON report destination")
    args = parser.parse_args(argv)
    if not 0 < args.timeout <= 30:
        parser.error("timeout must be in (0, 30] seconds")

    creds = credentials_status(os.environ)
    names = list(DATACENTERS) if args.environment == "both" else [args.environment]
    results = {}
    for name in names:
        host, port = DATACENTERS[name]
        ok, error = check_tcp(host, port, args.timeout)
        results[name] = {"host": host, "port": port, "reachable_tcp": ok,
                         "error": error, "mtproto_authenticated": False}

    report = {
        "kind": "telegram_dc_tcp_probe",
        "credentials": creds,
        "datacenters": results,
        "notes": "TCP only; not proof of MTProto authentication, account login, or full service availability",
    }
    serialized = json.dumps(report, indent=2, sort_keys=True)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as stream:
            stream.write(serialized + "\n")
    print(serialized)
    if args.require_credentials and not all(creds.values()):
        return 2
    if args.strict_network and not all(v["reachable_tcp"] for v in results.values()):
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
