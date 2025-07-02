#!/bin/sh
##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to locate JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
  JAVA_PATH=$(which java)
  if [ -n "$JAVA_PATH" ]; then
    JAVA_HOME=$(dirname $(dirname "$JAVA_PATH"))
  fi
fi

exec "$JAVA_HOME/bin/java" \
  -Dorg.gradle.appname=gradlew \
  -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
