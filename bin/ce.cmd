
set USERNAME=YOU
set CEDIR=C:\Users\%USERNAME%\Desktop\ce\ConlluEditor

java -jar %CEDIR%\ConlluEditor-2.16.2.0-jar-with-dependencies.jar ^
   --rootdir %CEDIR%\demo ^
   --deprels %CEDIR%\deprel.ud,%CEDIR%\deprel.fr ^
   --UPOS %CEDIR%\cpos.ud ^
   %CEDIR%\questionsquizz.annotate.conllu ^
   5555



