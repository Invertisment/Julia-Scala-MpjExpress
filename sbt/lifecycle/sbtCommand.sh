#!/bin/bash -e

lastDir=`pwd`
cd "$(dirname $0)/../.."

`pwd`/sbt/runSbt.sh $@

cd ${lastDir}