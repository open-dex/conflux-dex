#!/bin/bash

set -e

# AWS config
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_key
AWS_DEFAULT_REGION=ap-northeast-1
AWS_OUTPUT_FORMAT=json

# Local config
cd `dirname "${BASH_SOURCE[0]}"`/../..
DEX_HA_PATH=`pwd`/scripts/aws_ha
CLEAR_BASH_PROFILE_Y_OR_N=N

# HA config
IS_MASTER_Y_OR_N=Y
VIP_PRIVATE_IP=VIP_IP
MASTER_PRIVATE_IP=MASTER_IP
SLAVE_PRIVATE_IP=BACKUP_IP

# Set env var
if [ $CLEAR_BASH_PROFILE_Y_OR_N == Y ]; then
    echo "">~/.bash_profile
fi
echo "export PATH=~/.local/bin:\$PATH">>~/.bash_profile
echo "export DEX_HA_PATH=$DEX_HA_PATH">>~/.bash_profile
echo "export VIP_PRIVATE_IP=$VIP_PRIVATE_IP">>~/.bash_profile
if [ $IS_MASTER_Y_OR_N == Y ]; then
    MY_PRIVATE_IP=$MASTER_PRIVATE_IP
    OTHER_PRIVATE_IP=$SLAVE_PRIVATE_IP
else
    OTHER_PRIVATE_IP=$MASTER_PRIVATE_IP
    MY_PRIVATE_IP=$SLAVE_PRIVATE_IP
fi

# Install python and aws-cli
curl -O https://bootstrap.pypa.io/get-pip.py
sudo apt update
sudo apt install python -y
python get-pip.py --user
pip install awscli --upgrade --user
aws configure<<EOF
$AWS_ACCESS_KEY_ID
$AWS_SECRET_ACCESS_KEY
$AWS_DEFAULT_REGION
$AWS_OUTPUT_FORMAT
EOF
rm get-pip.py

# Install keepalived and setup config
sudo apt install keepalived -y
KEEPALIVED_CONF=/etc/keepalived/keepalived.conf
sudo cp $DEX_HA_PATH/config/keepalived.conf $KEEPALIVED_CONF
sudo sed -i "s/MY_PRIVATE_IP/$MY_PRIVATE_IP/g" $KEEPALIVED_CONF
sudo sed -i "s/OTHER_PRIVATE_IP/$OTHER_PRIVATE_IP/g" $KEEPALIVED_CONF
sudo sed -i "s@DEX_HA_PATH@$DEX_HA_PATH@g" $KEEPALIVED_CONF
if [ $IS_MASTER_Y_OR_N == N ]; then
    sudo sed -i "s/priority 100/priority 90/g" $KEEPALIVED_CONF
fi
