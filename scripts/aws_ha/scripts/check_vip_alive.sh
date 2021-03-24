#!/bin/bash

set -e

source /home/ubuntu/.bash_profile

su - ubuntu -c "$DEX_HA_PATH/scripts/has_vip.sh"
RET=$?

if [ $RET -eq 0 ]; then
    exit 1
fi

exit 0
