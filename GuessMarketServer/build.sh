#!/bin/bash
set -e
cd "$(dirname "$0")"

TOMCAT_HOME=/Users/ido/Desktop/java/tomcat/apache-tomcat-10.1.61
SERVLET_JAR=$TOMCAT_HOME/lib/servlet-api.jar
LIB_JARS=$(ls web/WEB-INF/lib/*.jar | tr '\n' ':')

rm -rf web/WEB-INF/classes
mkdir -p web/WEB-INF/classes

find src -name "*.java" > sources.txt
javac -cp "$SERVLET_JAR:$LIB_JARS" -d web/WEB-INF/classes @sources.txt
rm sources.txt

rm -f guessmarket.war
cd web
jar -cf ../guessmarket.war .
cd ..
echo "Built guessmarket.war"
ls -lh guessmarket.war
ls -lh web/WEB-INF/lib/