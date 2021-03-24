#!/bin/bash

set -e
PORT=${SERVER_PORT:-8080}
DEX_PID=`lsof -t -i:${PORT} || echo ""`

if [ -z "$DEX_PID" ]; then
    DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../../.. && pwd)
    RESOURCE_DIR=$DEX_DIR/src/main/resources
    PROFILE=stage

    # Backup log
    LOG_BACK_DIR=$DEX_DIR/../conflux-dex-log-archive
    [ ! -d "$LOG_BACK_DIR" ] && mkdir -p $LOG_BACK_DIR
    [ ! -f console.log ] && touch console.log
    zip $LOG_BACK_DIR/archive-$(date '+%Y%m%d-%H%M%S').zip *.log logs
    rm -f *.log
    rm -rf logs

    # Start DEX service in background
    cd $DEX_DIR
    nohup gradle bootRun --args="--spring.profiles.active=$PROFILE" > console.log 2>&1 &
fi
