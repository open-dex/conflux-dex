#!/bin/bash

if [ $# -lt 1 ]; then
	echo "Parameters required: <access_token> [<dex_url>]"
	exit 1
fi
PORT=${SERVER_PORT:-8080}
DEX_HOST="http://localhost:${PORT}"
ALERT_URL=https://oapi.dingtalk.com/robot/send?access_token=$1
if [ ! -z $2 ]; then
    DEX_HOST="${2}"
fi
API_URL=${DEX_HOST}/system/suspend
echo "API URL is ${API_URL}"

function send() {
	curl -X POST -H "Content-Type: application/json" -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"$1\"},\"at\":{\"isAtAll\":true}}" $ALERT_URL
}

send "MatchFlow: start to monitor healthness ..."

retry=0
retry_max=3
state=normal
lastAuditCheckTime=`date +%s`

# check for the liveness of audit program every 10 minutes
function checkAuditProgram() {
	if [ $((`date +%s` - lastAuditCheckTime)) -gt 600 ]; then
		# boomflow
		auditCmd=`ps -ef | grep conflux-dex-audit | grep boomflow`
		if [ -z "$auditCmd" ]; then
			send "Warning: boomflow audit program not started!"
		fi
		
		# matchflow
		auditCmd=`ps -ef | grep conflux-dex-audit | grep matchflow | grep dbaddr`
		if [ -z "$auditCmd" ]; then
			send "Warning: matchflow audit program not started!"
		fi
		
		# shuttleflow
		auditCmd=`ps -ef | grep conflux-dex-audit | grep shuttleflow`
		if [ -z "$auditCmd" ]; then
			send "Warning: shuttleflow audit program not started!"
		fi
		
		lastAuditCheckTime=`date +%s`
	fi
}

while true
do
	success=false
	
	response=`curl -s $API_URL`
	if [ $? -eq 0 ]; then
		success=`echo $response | jq '.success'`
		if [ $? -ne 0 ]; then
			success=false
		fi
	fi
	
	if $success; then
		retry=0
		
		if `echo $response | jq '.data'`; then
			if [ "$state" = "normal" ]; then
				send "MatchFlow: system is paused!"
			elif [ "$state" = "error" ]; then
				send "MatchFlow: REST API access recovered, but system is paused!"
			fi
			
			state=paused
			sleep 5
		else
			if [ "$state" = "paused" ]; then
				send "MatchFlow: system is healthy now."
			elif [ "$state" = "error" ]; then
				send "MatchFlow: REST API access recovered."
			fi
			
			state=normal
			# only check for audit program when system is health
			checkAuditProgram
			sleep 3
		fi
	else
		if [ "$state" = "error" ]; then
			sleep 5
		elif [ $retry -lt $retry_max ]; then
			retry=$((retry+1))
			sleep 1
		else
			state=error
			send "MatchFlow: failed to access REST API for healthness check!"
			sleep 5
		fi
	fi
done
