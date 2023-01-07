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



if [ "$2" == "" ]; then
	echo "usage $0 grewmath|@grewmatch conllu-files"
	exit 1
fi

PATTERN=$1
shift
CONLLU=$*

java -Xmx4g -cp $NEWESTJAR com.orange.labs.conllparser.CheckGrewmatch --filter --join $PATTERN $CONLLU
 

