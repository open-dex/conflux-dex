#!/bin/bash

set -e

# Usage: restart.sh [PATCH_BRANCH]

PATCH_BRANCH=$1

DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/.. && pwd)
echo "dex dir: $DEX_DIR"
if [[ "$CALL_FROM" != "WATCH_DOG" ]]; then
  $DEX_DIR/scripts/watchdog/stop_watchdog.sh
fi
source "$DEX_DIR/scripts/tool/check_env_file.sh"
PORT=${SERVER_PORT:-8080}


# Kill the running program
DEX_PID=`lsof -t -i:${PORT} || echo ""`
if [[ ! -z "$DEX_PID" ]]; then
	kill -9 $DEX_PID
fi

# Apply patch if specified
if [ ! -z $PATCH_BRANCH ]; then
	git fetch
	git checkout $PATCH_BRANCH
fi

nohup gradle bootRun --args="--spring.profiles.active=$DEPLOY_ENV\
 --server.port=${PORT}\
" > console.log 2>&1 &
echo "restart $DEPLOY_ENV port $PORT done"
