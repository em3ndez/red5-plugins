#!/bin/bash

#JAVA_HOME=/home/mondain/workspace/tools/jdk1.7.0_79
#export JAVA_HOME

#MVN_OPTS="-Dmaven.test.skip=true -Dmaven.compiler.fork=true -Dmaven.compiler.executable=$JAVA_HOME/bin/javac"
MVN_OPTS="-Dmaven.test.skip=true"
export MVN_OPTS

mvn $MVN_OPTS clean dependency:copy-dependencies package

