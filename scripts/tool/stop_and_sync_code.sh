#!/bin/bash

# Prepare to upgrade DEX service:
# 1. Stop the current running DEX service.
# 2. Optionally dump contract data for migration.
# 3. Sync up with the latest code (master or specified branch/tag).

set -e

if [ $# -lt 1 ]; then
	echo "Parameters required: [<branch_or_tag>]"
	exit 1
fi

BRANCH_OR_TAG=${1:-master}

cd `dirname "${BASH_SOURCE[0]}"`/../..
DEX_DIR=`pwd`
RESOURCE_DIR=$DEX_DIR/src/main/resources
APP_PROP_FILE=$RESOURCE_DIR/application.properties

PORT=${SERVER_PORT:-8080}
# Kill the running program
DEX_PID=`lsof -t -i:${PORT} || echo ""`
echo "DEX port is [${PORT}], pid is [${DEX_PID}]"
if [[ ! -z "$DEX_PID" ]]; then
	kill -9 $DEX_PID
	echo "Conflux DEX process killed: $DEX_PID"
fi

# Revert local change
function doGit() {
echo "begin git sync"

# Sync up with the latest code (master or branch)
if [ "$BRANCH_OR_TAG" = "master" ]; then
  git checkout master
	git pull
else
	git fetch
	git checkout $BRANCH_OR_TAG
fi
}

if [[ ! -z "$SKIP_GIT" ]]; then
    echo "Skip git [${SKIP_GIT}]"
else
    echo "Do git work  [${SKIP_GIT}]"
    # comment next line to avoid git operation, in order to speed up dev/test.
    doGit
fi

echo "stop and sync code done"