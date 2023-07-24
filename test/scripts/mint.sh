#!/usr/bin/env bash
source ./scripts/env.sh
addr=${1}
if [[ -z ${addr} ]]; then
    echo "Usage: mint.sh addr"
    exit 1
fi
echo "Mint ${CFX_URL}"
node ./src/main/resources/blockchain/deployment/bf.mint$EVM.js $addr
wait $!
echo "Mint...Done"