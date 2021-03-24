#!/bin/bash

set -e

# exit 1 if machine holds vip but dex failed
PORT=${SERVER_PORT:-8080}
source /home/ubuntu/.bash_profile

su - ubuntu -c "$DEX_HA_PATH/scripts/has_vip.sh"
RET=$?

if [ $RET -eq 0 ]; then
    exit 0
fi

DEX_PID=`lsof -t -i:${PORT} || echo ""`
if [ -z "$DEX_PID" ]; then
    exit 1
fi

exit 0
