#!/bin/bash

# helper script to download and install jquery, jquery-ui, boostrap and popper

MYDIR=$(dirname $0)
DEST=$MYDIR/../gui/lib

if [ -d $DEST ]; then
	echo "destination directory $DEST exists already. aborted"
	exit 1
fi

which wget

if [ $? -ne 0 ]; then
	echo "need wget to download libs. aborted"
	exit 2
fi


function getfile() {
	wget -q $1
	if [ $? -ne 0 ]; then
		echo "cannot download $1. aborted"
		exit 11
	fi
}


mkdir $DEST
pushd $DEST



getfile https://code.jquery.com/jquery-3.3.1.min.js

getfile https://jqueryui.com/resources/download/jquery-ui-1.14.1.zip
unzip jquery-ui-1.14.1.zip

getfile https://github.com/twbs/bootstrap/releases/download/v4.1.3/bootstrap-4.1.3-dist.zip
mkdir bootstrap-4.1.3
unzip -d bootstrap-4.1.3 bootstrap-4.1.3-dist.zip

getfile https://unpkg.com/popper.js/dist/umd/popper.min.js
getfile https://unpkg.com/popper.js/dist/umd/popper.min.js.map

