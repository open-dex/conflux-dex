#!/bin/bash

set -e

DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/.. && pwd)
echo "Dex dir: $DEX_DIR"
source "$DEX_DIR/scripts/tool/check_env_file.sh"

BRANCH_OR_TAG=${1:-master}

if [[ $# -lt 1 ]]; then
    echo "Usage :[branch_or_tag]"
	echo "Argument not set, use default"
fi

echo "Argument: branch ${BRANCH_OR_TAG}"

#<data_migrate_bool> [<branch_or_tag>]
$DEX_DIR/scripts/tool/stop_and_sync_code.sh $BRANCH_OR_TAG

#<profile:test|stage|prod> <mysql_root_password>
$DEX_DIR/scripts/tool/start.sh $DEPLOY_ENV $MYSQL_PWD
