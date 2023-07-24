#!/bin/bash

# Upgrade DEX service:
# 1. Deploy contracts, e.g. Boomflow, CRCL.
# 2. Initialize database or update new contract addresses.
# 3. Restart DEX service.

set -e

# Check for input parameters
if [ $# -lt 2 ]; then
	echo "Parameters required: <profile:test|stage|prod> <mysql_root_password>"
	exit 1
fi

export PROFILE=$1
MYSQL_ROOT_PWD=$2

# Change directory to conflux-dex
DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)
RESOURCE_DIR=$DEX_DIR/src/main/resources

# Check required environment variables
if [ -z $DEX_ADMIN_ADDRESS ]; then
	echo -e "\e[1;31mEnvironment variable DEX_ADMIN_ADDRESS not set\e[0m"
	exit 1
fi

if [ -z $BOOMFLOW_ADMIN_ADDRESS ]; then
	echo -e "\e[1;31mEnvironment variable BOOMFLOW_ADMIN_ADDRESS not set\e[0m"
	exit 1
fi

if [ -z $BOOMFLOW_ADMIN_PRIVATE_KEY ]; then
	echo -e "\e[1;31mEnvironment variable BOOMFLOW_ADMIN_PRIVATE_KEY not set\e[0m"
	exit 1
fi

if [ -z $CFX_URL ]; then
	echo "CFX_URL not set"
	exit 2
fi

echo "CFX URL: $CFX_URL"

epochNum=1
function uniSed() {
  # Note, macOS sed, [ -i [extension] ] , ubuntu [ -i[extension], a little space difference.
  if [[ $(uname) = 'Darwin' ]]; then
      sed -i .bak "$1" "$2"
  else
      sed -i "$1" "$2"
  fi
}
function doBashWork(){
# Get the current epoch to poll event logs from
epochResponse=`curl -s -X POST --data '{"jsonrpc":"2.0","method":"cfx_epochNumber","params":[],"id":1}' -H "Content-Type: application/json" $CFX_URL`
epochHex=`echo $epochResponse | jq -r '.result'`
epochNum=$(($epochHex))
echo "begin to poll event from epoch: $epochNum"

# Install npm packages if any new added or updated
if [ -z "$SKIP_NPM_INSTALL" ]; then
  echo "******************** npm install start ********************"
  npm install
  echo "******************** npm install ended ********************"
fi
# Deploy contracts on Conflux
echo "******************** deploy boomflow start ********************"
# call node script to deploy contracts
# Deploy contracts: Boomflow, ERC777, FC, CRCL
node ./src/main/resources/blockchain/deployment/bf.deploy$EVM.js
if [ "$?" != "0" ]; then
  exit
fi
if [ "$PROFILE" != "prod" ]; then
  # mint for test
	node ./src/main/resources/blockchain/deployment/bf.mint$EVM.js $UI_ADMIN_ADDRESS
fi
echo "******************** deploy boomflow ended ********************"

# unpause contracts
echo "******************** unpause boomflow start ********************"
node $RESOURCE_DIR/blockchain/deployment/bf.unpause$EVM.js
echo "******************** unpause boomflow ended ********************"
}
######### doBashWork end ##############

# Reset database or update addresses of new deployed contracts
export MYSQL_PWD=$MYSQL_ROOT_PWD
MYSQL_HOST="-h $DEX_MYSQL_HOST -P ${DEX_MYSQL_PORT:-3306}"
DB_USER=${DEX_MYSQL_USER:-root}
DB=${DB_NAME:-conflux_dex}
if [ "${DROP_DB}" = "true" ]; then
  #initdb database
  filledSqlFile=${RESOURCE_DIR}/initdb.$(date '+%Y%m%d-%H%M%S').sql
  echo "prepare sql file: ${filledSqlFile}"
  # Replace data base name, backup file is initdb.sql.bak
	if [[ "${DROP_DB}" = "true" ]]; then
	    echo "DROP DATABASE IF EXISTS __DATABASE_NAME;" >> ${filledSqlFile}
	fi
	cat ${RESOURCE_DIR}/initdb.sql >> ${filledSqlFile}
  #uniSed is a function defined above.
  uniSed "s/__DATABASE_NAME/${DB}/g" ${filledSqlFile}
  echo "prepare database begin:"
  mysql $MYSQL_HOST -u $DB_USER -e "source ${filledSqlFile}"
  echo "prepare database done."
fi
###
if [ ! -z "${SKIP_BASH_WORK}" ]; then
    echo "Skip bash [${SKIP_BASH_WORK}]"
else
    doBashWork
fi
# Update resources for the new deployed contracts
SETUP_ASSET_SQL_FILE=$RESOURCE_DIR/predefined_assets.sql
echo "Database is ${DB}"
echo -e "USE ${DB};" > $SETUP_ASSET_SQL_FILE
echo -e "SET FOREIGN_KEY_CHECKS=0;" >> $SETUP_ASSET_SQL_FILE
echo -e "" >> $SETUP_ASSET_SQL_FILE

echo -e "REPLACE INTO t_product (id, name, base_currency_id, quote_currency_id, price_precision, amount_precision, funds_precision, min_order_amount, max_order_amount, min_order_funds, instant_exchange, base_product_id, quote_product_id, base_is_base_side, quote_is_base_side) VALUES" >> $SETUP_ASSET_SQL_FILE
if [ "$PROFILE" == "prod" ]; then
  echo -e "\t(1, 'BTC-USDT', 1, 3, 2, 6, 8, 0.000001, 99999999.999999, 0.00000001, false, NULL, NULL, NULL, NULL)," >> $SETUP_ASSET_SQL_FILE
  echo -e "\t(2, 'ETH-USDT', 2, 3, 2, 4, 8, 0.0001, 99999999.9999, 0.00000001, false, NULL, NULL, NULL, NULL)," >> $SETUP_ASSET_SQL_FILE
  echo -e "\t(3, 'FC-USDT', 4, 3, 4, 4, 8, 0.0001, 99999999.9999, 0.00000001, false, NULL, NULL, NULL, NULL)," >> $SETUP_ASSET_SQL_FILE
  echo -e "\t(4, 'CFX-USDT', 9, 3, 4, 4, 8, 0.0001, 99999999.9999, 0.00000001, false, NULL, NULL, NULL, NULL)," >> $SETUP_ASSET_SQL_FILE
fi
echo -e "\t(5, 'EOS-KCoin', 10, 11, 4, 4, 8, 0.0001, 99999999.9999, 0.00000001, false, NULL, NULL, NULL, NULL);" >> $SETUP_ASSET_SQL_FILE

#echo -e "\t(5, 'BTC-FC', 1, 6, 2, 6, 2, 0.000001, 99999999.999999, 0.01, true, 1, 4, true, false);" >> $SETUP_ASSET_SQL_FILE
echo -e "" >> $SETUP_ASSET_SQL_FILE
echo -e "SET FOREIGN_KEY_CHECKS=1;" >> $SETUP_ASSET_SQL_FILE

echo "import SETUP_ASSET_SQL_FILE begin"
mysql $MYSQL_HOST -u $DB_USER -e "source ${SETUP_ASSET_SQL_FILE}"
echo "import SETUP_ASSET_SQL_FILE done"

# Backup log
LOG_BACK_DIR=$DEX_DIR/../conflux-dex-log-archive
[ ! -d "$LOG_BACK_DIR" ] && mkdir -p $LOG_BACK_DIR
[ ! -f console.log ] && touch console.log
echo "LOG_BACK_DIR $LOG_BACK_DIR"
zip $LOG_BACK_DIR/archive-$(date '+%Y%m%d-%H%M%S').zip *.log logs
rm -f *.log
rm -rf logs

# Start DEX service in background
PORT=${SERVER_PORT:-8080}
cd $DEX_DIR
pwd
#./gradlew bootRun --args="--spring.profiles.active=$PROFILE\
# --blockchain.poll.epoch=$epochNum\
# --server.port=${PORT}\
#"

echo "Start script finished. server port ${PORT}, database ${DB}"
