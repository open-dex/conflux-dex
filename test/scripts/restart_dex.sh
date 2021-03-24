#!/bin/bash

set -e
DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)
echo "restart test dex, dex dir: $DEX_DIR"
source "$DEX_DIR/scripts/tool/check_env_file.sh"

# Kill the running program
PORT=${SERVER_PORT:-8080}
DEX_PID=`lsof -t -i:${PORT} || echo ""`
echo "dex pid is [$DEX_PID]"
if [[ ! -z "$DEX_PID" ]]; then
	kill -9 $DEX_PID
fi

# Reset database
mysql -u root -e "source $DEX_DIR/src/main/resources/initdb.sql"
mysql -u root -e "source $DEX_DIR/src/test/resources/test_data.sql"

# Poll event logs from latest epoch
epochResponse=`curl -s -X POST --data '{"jsonrpc":"2.0","method":"cfx_epochNumber","params":[],"id":1}' -H "Content-Type: application/json" $CFX_URL`
epochHex=`echo $epochResponse | jq -r '.result'`
epochNum=$(($epochHex))

#java -jar build/libs/conflux-dex.jar --spring.profiles.active=test
nohup gradle bootRun --args="--spring.profiles.active=test --blockchain.poll.epoch=$epochNum" > console.log 2>&1 &
echo $! > pid.txt
echo "done pid $! code $?"
