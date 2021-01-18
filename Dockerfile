FROM openjdk:8-alpine

# docker build -t jheinecke/conllueditor:2.9.1 .
# docker build -t jheinecke/conllueditor:latest .
# docker run -t --name conllueditor -p 5555:5556 --user 1000:1000  -v </absolute/path/to/datadir>:/data  --env filename=tt.conllu jheinecke/conllueditor:latest
# docker push jheinecke/conllueditor:2.9.1 .
# docker push jheinecke/conllueditor:latest .

# docker exec -it conllueditor /bin/sh

# stop and remove container docker rm --force conllueditor

# https://ropenscilabs.github.io/r-docker-tutorial/04-Dockerhub.html

RUN apk update && apk add  --no-cache bash

WORKDIR /usr/src/ConlluEditor
COPY target/ConlluEditor-2.9.1-jar-with-dependencies.jar ./ConlluEditor.jar

COPY gui .
COPY dockerstart.sh .
#ENV CONLLUFILE=$filename

#CMD echo "aa ${CONLLUFILE} bb $filename"

#RUN ls -laR /
#USER $uid:$gid

#CMD java -Xmx4g -cp /usr/src/ConlluEditor/ConlluEditor-2.7.0-jar-with-dependencies.jar \
#	com.orange.labs.editor.ConlluEditor \
#	--rootdir /usr/src/ConlluEditor \
#	/data/${filename}  5556
WORKDIR /data
CMD /usr/src/ConlluEditor/dockerstart.sh "$filename" "$validator" "$UPOS" "$XPOS" "$deprels" "$features" "$language" "${include_unused}" "$shortcuts" "$saveAfter" "$compare"




