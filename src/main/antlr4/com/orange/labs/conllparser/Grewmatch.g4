/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2022, Orange S.A.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright notice,
     this list of conditions and the following disclaimer in the documentation
     and/or other materials provided with the distribution.

  3. Neither the name of the copyright holder nor the names of its contributors
     may be used to endorse or promote products derived from this software without
     specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 @author Johannes Heinecke
 @version 2.20.0 as of 10th December 2022
 */


/* lexer and parsse to use the grewmatch language to search in the treebank
   see https://match.grew.fr
*/

grammar Grewmatch;

expression : pattern without* EOF # final
	;

pattern
	: PATTERN OPEN rheol ( SEMICOLON rheol )* SEMICOLON? CLOSE # patternlist
	;

without
	: WITHOUT OPEN rheol ( SEMICOLON rheol )* SEMICOLON? CLOSE # withoutlist
	;

rheol
	: nodename BOPEN condition ( COMMA condition )* BCLOSE # condlist
	| (relval COLON)? nodename ARROW nodename # relation
	| (relval COLON)? nodename ARROWOPEN NOT ? deprel ( '|' deprel)* ARROWCLOSE nodename # namedrelation
        | nodenamefield (comp|eq) nodenamefield # order2
	| nodename  (comp|eq) nodename # order
	;


condition
	: NOT? conllucolumn # cond
	| conllucolumn eq (string|utfstring) ( '|' (utfstring|string ))* # cond2
	;

eq  :  '=' | '<>';
comp :  '<' | '<<' | '>' | '>>' ;

//eq	: '=' # equal
//	;
//ne	: '<>' # unequal
//	;

relval
	: LOWER # relationvar
	;

deprel
	: LOWER (':' LOWER)? # rel
	;

// upos, lemma, Number
conllucolumn
	: LOWER | ALPHANUM # cat
	;


nodename : (ALPHANUM|'_')+ # node
	;

nodenamefield : (ALPHANUM|'_')+ '.' field # nodefield
	;

field : (ALPHANUM | LOWER | '_')+   # fieldname
	;

string : LOWER | ALPHANUM # str1
	;

utfstring : UTFSTRING
	;

WS	: [ \t\n] + -> skip ;
PATTERN : 'pattern' ;
WITHOUT	: 'without' ;
COMMA	: ',' ;
SEMICOLON	: ';' ;
COLON	: ':' ;
OPEN	: '{' ;
CLOSE	: '}' ;
BOPEN	: '[' ;
BCLOSE	: ']' ;
ARROWOPEN	: '-[' ;
ARROWCLOSE	: ']->' ;
ARROW	: '->' ;
NOT     : [!^] ;

LOWER		: [a-z]+ ;
ALPHANUM	: [A-Za-z0-9]+ ;
UTFSTRING	: '"' ~["<>]+ '"' ;
//UTFSTRING	: UTFCHAR+ ;
//fragment UTFCHAR :  ~["<> [\]=;:|\n,_.{}] ;





