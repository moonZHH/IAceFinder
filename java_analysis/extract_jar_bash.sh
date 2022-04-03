#! /bin/bash

## $1 = the src path of jar file
## $2 = the API level of the analyzed AOSP version

ANDROID_CLASS_DIR="/home/zhouhao/CrossFrameworkAnalysis/JavaLayer/output/classes/"

if [ $2 -gt 8 ] && [ $2 -lt 21 ]; then
	export JAVA_HOME=/home/zhouhao/Jdk_Version/jdk1.6.0_45
	export PATH=$JAVA_HOME/bin:$PATH
elif [ $2 -gt 20 ] && [ $2 -lt 24 ]; then
	export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
	export PATH=$JAVA_HOME/bin:$PATH
else
	export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre
	export PATH=$JAVA_HOME/bin:$PATH
fi

cd "$ANDROID_CLASS_DIR"
jar xf $1
rm -f $1
