#!/bin/bash

set -e

DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)

nohup $DEX_DIR/node_modules/.bin/mocha $DEX_DIR/test/benchmark/ > $DEX_DIR/benchmark.log 2>&1 &
