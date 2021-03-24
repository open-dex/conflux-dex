#!/bin/bash

# utility to reset database [conflux_dex]

set -e

if [ -z $MYSQL_PWD ]; then
	echo "environment variable MYSQL_PWD not configured"
	exit 1
fi

DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../../.. && pwd)

tmpSqlFile=initdb.tmp.sql

echo "DROP DATABASE IF EXISTS conflux_dex;" > $tmpSqlFile
cat $DEX_DIR/src/main/resources/initdb.sql >> $tmpSqlFile
cat $DEX_DIR/src/test/resources/test_data.sql >> $tmpSqlFile

boomflow=`cat $DEX_DIR/src/main/resources/blockchain/contract_address.json | jq -r ".boomflow_addr"`
echo "INSERT INTO t_config VALUES ('contract.boomflow.address', '$boomflow');" >> $tmpSqlFile

sed -i "s/__DATABASE_NAME/conflux_dex/g" $tmpSqlFile

mysql -u root -e "source $tmpSqlFile"

rm $tmpSqlFile
