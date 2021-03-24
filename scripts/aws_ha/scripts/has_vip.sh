#!/bin/bash

set -e

private_ip=`ip addr | grep "dynamic ens5" | awk -F" " '{print $2'} | awk -F"/" '{print $1}'`
info=`aws ec2 describe-instances --filter "Name=private-ip-address,Values=$private_ip" | grep $VIP_PRIVATE_IP || echo ""`

if [[ ! -z "$info" ]]; then
    exit 1
fi

exit 0

