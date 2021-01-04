#!/bin/bash

# this script is only needed to launche the server in an docker container
# outside docker please use bin/conlluedit.sh

filename=$1
shift

if [ "$1" != "" ]; then
    echo " ** cannot run validator within docker"
    echo " ** please run it on the edited file ($1) outside docker"
    #ivalidator="--validator $1"
fi
shift

if [ "$1" != "" ]; then
    iupos="--UPOS $1"
fi
shift

if [ "$1" != "" ]; then
    ixpos="--XPOS $1"
fi
shift

if [ "$1" != "" ]; then
    ideprels="--deprels $1"
fi
shift

if [ "$1" != "" ]; then
    ifeats="--features $1"
fi
shift

if [ "$1" != "" ]; then
    ilang="--language $1"
fi
shift

if [ "$1" != "" ]; then
    iunused="--include_unused"
fi
shift

if [ "$1" != "" ]; then
    ishortcuts="--shortcuts $1"
fi
shift

if [ "$1" != "" ]; then
    isaveafter="--saveAfter $1"
fi
shift

if [ "$1" != "" ]; then
    icompare="--compare $1"
fi
shift

echo "options given:"
echo "  validator      $ivalidator"
echo "  upos           $iupos"
echo "  xpos           $ixpos"
echo "  deprels        $ideprels"
echo "  features       $ifeats"
echo "  language       $ilang"
echo "  include unused $iunused"
echo "  shortcuts      $ishortcuts"
echo "  saveafter      $isaveafter"
echo "  compare        $icompare"
echo "  filename       $filename"

java -Xmx4g -cp /usr/src/ConlluEditor/ConlluEditor.jar \
	com.orange.labs.editor.ConlluEditor \
	--rootdir /usr/src/ConlluEditor \
        $ivalidator $iupos $ixpos $ideprels $ifeats $ilang $iunused $ishortcuts $isaveafter \
        $icompare \
	${filename}  5555
