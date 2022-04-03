#! /bin/bash

## $1 = the dst path of jar file
## $2 = the src path of jar file

toPath="$1"
fromPath="$2"
ANDROID_CLASS_DIR="/home/zhouhao/CrossFrameworkAnalysis/JavaLayer/output/classes/"

if [ ! -d "$ANDROID_CLASS_DIR" ]; then
	mkdir "$ANDROID_CLASS_DIR" 
fi

cp $fromPath $toPath
