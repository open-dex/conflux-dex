# Work in progress
Please note that all test cases under this folder relates to cross chain operation more or less,
it's not suitable for testing DEX.

`OrderBot` the java class contains deposit and trade test.
# Intergration test

## step 1

set env var `MYSQL_PWD`, `DEX_ADMIN_ADDRESS`, `DEX_ADMIN_PRIVATE_KEY`, `CFX_URL`

```
./scripts/tool/start.sh test db_pwd
```

```
npm test
```

to test all cases in default folder (not contain subfolders)

## step 3, crosschain test

set env var `BTC_TESTNET_ADDRESS` `BTC_TESTNET_WIF` `ETH_TESTNET_ADDRESS` `ETH_TESTNET_PRIVATEKEY`

```
npm test test/deposit
npm test test/withdraw
```

to test cases in specific folder

## step 4, stress test

set env var `BENCHMARK_TPS` `BENCHMARK_LIMIT (total orders to place, <=0 means unlimited)`

```
npm test test/benchmark
```

or

```
./test/scripts/start_benchmark.sh
./test/scripts/stop_benchmark.sh
```

to run stress test on background
