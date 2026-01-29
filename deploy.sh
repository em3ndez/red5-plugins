#!/bin/bash

#JAVA_HOME=/home/mondain/workspace/tools/jdk1.7.0_79
#export JAVA_HOME

#MVN_OPTS="-Dgpg.passphrase=venom1 -Dmaven.test.skip=true -Dmaven.compiler.fork=true -Dmaven.compiler.executable=$JAVA_HOME/bin/javac"
MVN_OPTS="-Dgpg.passphrase=venom1 -Dmaven.test.skip=true"
export MVN_OPTS

PATH=$JAVA_HOME/bin:$PATH

java -version

# deploy plugins
echo "Build and deploy Red5 Plugins"
cd /home/mondain/workspace/github-red5/red5-plugins
mvn $MVN_OPTS clean deploy -P release
