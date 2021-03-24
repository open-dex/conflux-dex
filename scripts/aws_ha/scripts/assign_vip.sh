#!/bin/bash

private_ip=`ip addr | grep "dynamic ens5" | awk -F" " '{print $2'} | awk -F"/" '{print $1}'`
ENI=`aws ec2 describe-instances --filter "Name=private-ip-address,Values=$private_ip" | grep NetworkInterfaceId | awk -F"\"" '{print $4}'`
aws ec2 assign-private-ip-addresses --network-interface-id $ENI --private-ip-address $VIP_PRIVATE_IP --allow-reassignment
