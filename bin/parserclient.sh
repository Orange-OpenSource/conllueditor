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


echo 1>&2 "jar: $NEWESTJAR"


if [[ "$1" =~ ^Xmx[0-9]g$ ]]; then
    HEAP="-$1"
    echo $HEAP
    shift
fi

ROOTDIR="--rootdir $BASENAME/../gui"
if [ "$1" == "--rootdir" ]; then
    ROOTDIR="--rootdir $2"
    shift
    shift
fi

if [ "$1" == "-r" ]; then
    ROOTDIR="--rootdir $BASENAME/../gui"
    shift
fi



PROXY="-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080"
#PROxY=-Djava.net.useSytemProxies=true

java -cp $NEWESTJAR $PROXY com.orange.labs.parserclient.ParserClient ${ROOTDIR} "$@"



#
