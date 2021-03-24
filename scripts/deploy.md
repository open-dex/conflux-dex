# Conflux DEX Deployment Instructions
## Overview
This document guides maintainers to deploy Conflux DEX step by step. 

After the deploying, we will get a java based (spring) backend web application runs.
A related frontend(UI) is available from [document]().

Note:
- Execute all the commands below under the conflux-dex folder.
- All the commands are only tested on Ubuntu 18.
- Tested MySQL version: 5.7.30-log MySQL Community Server (GPL)
## Pre-requirement
- Clone this repository to local machine or a server.
- One conflux address with at least 1000 CFX balance, used as DEX_ADMIN, which send transaction to block chain.
'blockchain.admin.balance.min.cfx' configures the minimum CFX.
- Another conflux address with 200 CFX balance, used as BOOMFLOW_ADMIN, which deploy contracts. 
Deploy one time costs approximately 100 cfx.
- Better to have a private RPC node, the default public RPC service is not available sometimes. 
[how to run a node](https://developer.conflux-chain.org/docs/conflux-doc/docs/installation#download-compiled-node-binary).
## Terminology
- CRCL: CRC stands for Conflux Request for Comments (Against Ethereum Request for Comments, ERC); L(Lock) means lock
the erc777 tokens for trading.
- Boomflow: Boomflow contract handle order matching on the block chain.
- Matchflow: Matching orders in memory (persistent in Database, off the chain)
- Shuttleflow: Conflux cross chain solution to integrate user assets from bitcoin and ethereum.
- Address format: Conflux network had updated its address format from HEX to base32. 
Deploying and database use hex format, interaction with block chain uses base32 format. See the caller of 
`AddressTool`.
## Setup on a Clean Environment
Install necessary SDKs, libraries and tools:
```
./scripts/tool/install.sh MYSQL_PWD
```

## Deploy ERC1820 (optional)
ERC1820 contract must be deployed at first, it's only needed if you run on a brand-new chain,
since it had already been deployed on both test-net and main-net on Conflux:
```
npm install
./scripts/deploy_erc1820.sh CFX_URL PRIVATE_KEY_HEX
```

## Deploy or Restart

### Environment Variables
Generate template `env.sh` under `scripts` directory.
```
 ./scripts/tool/check_env_file.sh 
```
|Name|Description|Required|
|----|-----------|--------|
|SERVER_PORT|Spring web application's port|NO, default 8080|
|DB_NAME|Database name|NO, default conflux_dex|
|DROP_DB|If set to true, the database will be dropped and recreated when deploy|YES, default false|
|BOOMFLOW_ADMIN_ADDRESS|Boomflow admin address to check balance|YES|
|BOOMFLOW_ADMIN_PRIVATE_KEY|Boomflow admin private key to deploy contracts|YES|
|UI_ADMIN_ADDRESS|DEX admin address to access the frontend UI after deploying, could be the same with BOOMFLOW_ADMIN_ADDRESS|YES|
|DEX_ADMIN_ADDRESS|DEX admin address to send transactions|YES|
|DEX_ADMIN_PRIVATE_KEY|DEX admin private key to sign transactions|YES|
|MYSQL_PWD|Database password|Yes|
|CFX_URL|Conflux block chain's RPC server|Yes|
|DEPLOY_ENV|Deploy mod: test/stage/prod|Yes|
|DEX_MYSQL_USER|mysql user|NO, default root|
|DEX_MYSQL_PORT|mysql port|NO, default 3306|
|DEX_MYSQL_HOST|mysql host|NO, default localhost|
|INFLUXDB_URL|InfluxDB URL to export metrics|NO|
|INFLUXDB_DATABASE|InfluxDB database to export metrics|NO|

### PROD/TEST/STAGE Environment
The difference between environments is that CRCL contracts reference to different erc777 token contracts.
We will deploy new erc777 contracts, or refers to existing ones, see 
```./src/main/resources/blockchain/deployment/bf.erc20-erc777.js```  

Note that BTC/ETH always refer to existing contracts.

Deploy everything:
```
# Default argument, GITHUB_TAG_OR_BRANCH master
nohup ./scripts/deploy.sh [GITHUB_TAG_OR_BRANCH] 2>&1 | tee deploy.out &
```
The application will start automatically after deploying.
### Mint
When deploying it will mint some token to the BOOMFLOW_ADMIN_ADDRESS.

Use script below to mint standalone. The parameter address should be in hex format.
```
./test/scripts/mint.sh 0x1234~
``` 
### Trouble shooting
bf.deploy.js contains the main process about deploying contract. Please check is there any
nodejs exception on the console(or in the file 'deploy.out'). 

The log of the java program is either conflux-dex.log or logs/all-***.log.

The file console.log contains output of gradle compiling/starting process.  
### Monitor
Please see the frontend UI repository which provide a web page to interact with this application.
### Tools
Restart service:
```
./scripts/restart.sh [GITHUB_TAG_OR_BRANCH]
```
Only deploy contract:
```
source ./scripts/env.sh
node ./src/main/resources/blockchain/deployment/bf.deploy.js
```
### DEX improvement proposal
[Read more](../DIP.md)
### Links
- [github docs](https://open-dex.github.io/conflux-dex-docs/)
- [shuttleflow](https://shuttleflow.io/eth/shuttle/in)
- [rpc endpoints](https://github.com/conflux-fans/conflux-rpc-endpoints)
- [conflux developer docs](https://developer.conflux-chain.org/)
- [Conflux portal (wallet)](https://portal.conflux-chain.org/)
- [Testnet scan](https://testnet.confluxscan.io/) 
- [Mainnet scan](https://confluxscan.io/) 