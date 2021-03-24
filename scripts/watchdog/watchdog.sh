#!/usr/bin/env bash
# nohup sh watchdog.sh > w.log &
set -e
export DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)
echo "dex dir: $DEX_DIR"
source "$DEX_DIR/scripts/tool/check_env_file.sh"

PORT=${SERVER_PORT:-8080}

TIMESTAMP(){
    echo $(date "+%Y-%m-%d %H:%M:%S")
}

function start(){
    #echo "plz fill start function"
    #sh $DEX_DIR/scripts/restart_$env.sh
    # for test environment
    cd $DEX_DIR
    export CALL_FROM="WATCH_DOG"
    if [[ ${DEPLOY_ENV} = "prod" ]]; then
        echo "restart prod"
        source $DEX_DIR/scripts/restart.sh
    elif [[ ${DEPLOY_ENV} = "stage" ]]; then
        echo "restart stage"
        source ${DEX_DIR}/scripts/restart.sh
    else
        echo "restart test"
        source ${DEX_DIR}/scripts/restart.sh
    fi
}
function alert(){
    echo "plz fill alert function"
}
# if restart, wait until rest api returns.
wait_started=0
function check() {
    echo ""
    echo ""
    echo "$(TIMESTAMP) do checking dex alive"
    lsof -i:${PORT} || echo "lsof got nothing"
    DEX_PID=`lsof -t -i:${PORT} -sTCP:LISTEN || echo ""`
    echo "Dex pid is $DEX_PID"
    #ps $DEX_PID | cat

    DEX_URL="localhost:${PORT}"
    FAIL_BODY='request to local fail'
    resp=`curl -s $DEX_URL/common/timestamp || echo $FAIL_BODY`
    echo "request timestamp from dex: $resp"
    success=true
    if [[ "$resp" = "$FAIL_BODY" ]]; then
        if [[ "$wait_started" -gt "0" ]]; then
          echo "wait_started... $wait_started"
          # countdown, in order to enter checking logic again.
          wait_started=$(($wait_started-1))
          sleep 3
          return 0
        fi
        success=false
    fi
    echo "success is $success"

    if [[ "$success" = true ]]; then
        echo "Dex is ok."
        wait_started=0
        return 0
    fi
    if [ ! -z $DEX_PID ]; then
        echo "kill dex: $DEX_PID"
        kill -9 $DEX_PID || echo "OK"
    else
        echo "dex pid is empty"
    fi

    alert
    echo "try starting"
    start
    # wait for N round. sleep 3 seconds each round, 100 rounds eq to 5 minutes.
    # to prevent duplicate restart.
    wait_started=100
}
while true;
do
    check
    sleep 1
done