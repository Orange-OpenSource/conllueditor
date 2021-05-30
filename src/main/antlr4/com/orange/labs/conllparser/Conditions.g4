grammar Conditions;

/* Tokens */


UPOS:  'Upos:'[A-Z]+ ;
LEMMA: 'Lemma:'[^\p{White_Space}]+ ;
FEAT:  'Feat:'[A-Za-z_]+'='[A-Za-z0-9]+ ;
AND: 'and';
OR: 'or';
NOT: '!';
OPEN: '(';
CLOSE: ')';
SPACE: [\p{White_Space}]+ -> skip;

FIELD: (UPOS | LEMMA | FEAT);


start : expression;

expression 
	: FIELD
	| NOT expression
	| OPEN inner=expression CLOSE
	| left=expression operator=AND right=expression
	| left=expression operator=OR right=expression
	;


/*
antlr4 Lexer.g4
javac -cp /usr/share/java/antlr4-runtime-4.7.2.jar Lexer*.java

*/
