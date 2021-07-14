#!/bin/bash


case "$OSTYPE" in
    Darwin*)
        # Mac
        BASENAME=$(dirname $(greadlink -f $0))
        ;;
    Linux*)
        # Linux, including WSL
        BASENAME=$(dirname $(readlink -f $0))
        ;;
    *)
        # all the rest
        BASENAME=$(dirname $(readlink -f $0))
        ;;
esac

TARGETDIR=$BASENAME/../target


if [[ "$1" =~  ^[4].[0-9]+$ ]]; then
    NEWESTJAR=$TARGETDIR/ConlluEditor-$1-jar-with-dependencies.jar
    shift
else
    NEWESTJAR=$(ls -tr $TARGETDIR/ConlluEditor-* | grep with-dep | tail -1)
fi

if [ "$2" == "" ]; then
	echo "usage $0 conditions conllufile"
	exit 1
fi

RULES=$1
CONLLU=$2

java -Xmx4g -cp $NEWESTJAR com.orange.labs.conllparser.ConllFile --conll --cedit ${RULES} ${CONLLU}


