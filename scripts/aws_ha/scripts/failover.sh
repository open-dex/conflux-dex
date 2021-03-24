#!/bin/bash

set -e

TYPE=$1
NAME=$2
STATE=$3

source /home/ubuntu/.bash_profile
echo "$(date '+%Y%m%d-%H%M%S') become $STATE" >> $DEX_HA_PATH/../../failover.log

case $STATE in
    "MASTER")
        su - ubuntu -c "$DEX_HA_PATH/scripts/assign_vip.sh"
        ip address add $VIP_PRIVATE_IP/20 dev ens5
        su - ubuntu -c "$DEX_HA_PATH/scripts/start_server.sh"
        ;;
    "BACKUP"|"FAULT")
        #su - ubuntu -c "$DEX_HA_PATH/scripts/kill_server.sh"
        ip address del $VIP_PRIVATE_IP/20 dev ens5
        ;;
    *)
        exit 1
        ;;
esac
