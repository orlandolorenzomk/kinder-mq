#!/usr/bin/env bash
# Usage: ./client.sh [host] [port]
HOST=${1:-localhost}
PORT=${2:-51092}

echo "Connecting to $HOST:$PORT ..."
nc -v "$HOST" "$PORT"
