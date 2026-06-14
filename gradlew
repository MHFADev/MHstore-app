#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine Java executable
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then JAVACMD="$JAVA_HOME/jre/sh/java"
  else JAVACMD="$JAVA_HOME/bin/java"; fi
else JAVACMD="java"; fi

exec "$JAVACMD" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
