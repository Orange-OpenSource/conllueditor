#!/bin/bash


#LANG=en_US.UTF-8

BASENAME=$(dirname $(readlink -f $0))
TARGETDIR=$BASENAME/../target


if [[ "$1" =~  ^[4].[0-9]+$ ]]; then
    NEWESTJAR=$TARGETDIR/ConlluEditor-$1-jar-with-dependencies.jar
    shift
else
    NEWESTJAR=$(ls -tr $TARGETDIR/ConlluEditor-* | grep with-dep | tail -1)
fi


# echo 1>&2 "jar: $NEWESTJAR"




java -cp $NEWESTJAR com.orange.labs.conllparser.ConlluPlusConverter  "$@"

