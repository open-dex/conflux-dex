#!/usr/bin/env bash
str=`ps ax`
pid=`echo "$str" | grep "/watchdog.sh" | awk '{print $1}'`

if [[ -z $pid ]]; then
    echo "watchdog not found"
else
    ps $pid | cat
    kill -9 $pid
    echo "kill sent to $pid"
fi