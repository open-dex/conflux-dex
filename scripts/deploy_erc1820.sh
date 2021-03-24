#!/bin/bash

set -e

if [ $# -lt 2 ]; then
	echo "Parameters required: <cfx_url> <private_key> [<address>]"
	exit 1
fi

export CFX_URL=$1
USER_ERC1820_PRIVATE_KEY=$2
USER_ERC1820=${3:-0x1789c4cd7c3cf1f1060d74ccb8e059519a17725d}

DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/.. && pwd)

# Ensure balance is enough to deploy ERC1820 contract.
balanceResponse=`curl -X POST --data "{\"jsonrpc\":\"2.0\",\"method\":\"cfx_getBalance\",\"params\":[\"$USER_ERC1820\"],\"id\":1}" -H "Content-Type: application/json" $CFX_URL`
balanceHex=`echo $balanceResponse | jq -r '.result'`
if [ "$balanceHex" = "0x0" ]; then
	echo -e "\e[1;31mBalance of ERC1820 account is not enough: $USER_ERC1820\e[0m"
	exit 1
fi

echo "Deploy ERC1820"
node $DEX_DIR/src/main/resources/blockchain/deployment/bf.ERC1820.js $USER_ERC1820_PRIVATE_KEY $CFX_URL
echo "Deploy ERC1820...Done"
