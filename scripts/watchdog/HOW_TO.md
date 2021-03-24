# How to use watchdog , and How it works

These script are executed under the project's root folder(conflux-dex).
## Start it
```
./scripts/watchdog/start_watchdog.sh
```
The log file of watchdog itself is named `watchdog.log`, placed under the project's root 
directory.

Use `tail -f ./watchdog.log` to monitor whether it is running.
## Stop it
```
./scripts/watchdog/stop_watchdog.sh
```
This command will send the `kill -9` signal to the watchdog process.

## How it works

It checks the port 8080 by `lsof`, the process id is founded if some process
is listening on the port.

Then, a `curl` request to URL /common/timestamp is executed, if it succeeded, 
current checking round is finished, watchdog will sleep 1 second, and then 
check the next round.

If `curl` failed, then `kill -9` signal will be sent to the process id (if `lsof` found it),
and then a restart script (depend on `DEPLOY_ENV`) will be executed.

|DEPLOY_ENV|script|
|---  |---|
|prod |${DEX_DIR}/scripts/restart_prod.sh| 
|stage|${DEX_DIR}/scripts/restart_stage.sh| 
|test |${DEX_DIR}/test/scripts/restart_dex.sh| 