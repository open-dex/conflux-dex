#!/usr/bin/env bash
DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)
nohup bash ${DEX_DIR}/scripts/watchdog/watchdog.sh > ${DEX_DIR}/watchdog.log &
echo "OK"
