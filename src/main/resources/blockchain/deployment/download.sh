#!/bin/bash

set -e

DATA_MIG_DIR=/tmp/conflux-dex/dump/CRCL
[ ! -d "$DATA_MIG_DIR" ] && mkdir -p $DATA_MIG_DIR

echo "Download CRCL"

DEPLOYMENT_DIR=./src/main/resources/blockchain/deployment
[ ! -d "$DEPLOYMENT_DIR/data" ] && mkdir -p $DEPLOYMENT_DIR/data
node --harmony $DEPLOYMENT_DIR/migrateCRCL.js --use_db -r -u $CFX_URL -p $BOOMFLOW_ADMIN_PRIVATE_KEY
wait $!

cp $DEPLOYMENT_DIR/data/*.dat $DATA_MIG_DIR

echo "Download CRCL...Done"