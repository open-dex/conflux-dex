#!/bin/bash

set -e
PORT=${SERVER_PORT:-8080}
DEX_PID=`lsof -t -i:${PORT} || echo""`

if [[ ! -z "$DEX_PID" ]]; then
    kill -9 $DEX_PID
fi
