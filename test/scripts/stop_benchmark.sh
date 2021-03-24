#!/bin/bash

set -e

PID=`ps ux | grep mocha | grep benchmark | awk -F" " '{print $2}'`

kill -9 $PID
