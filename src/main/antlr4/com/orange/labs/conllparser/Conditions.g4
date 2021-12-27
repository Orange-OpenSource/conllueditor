/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2021, Orange S.A.

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
 @version 2.14.1 as of 22nd December 2021
*/

// grammar to parse the conditions for complex search (and replace) and mass editing
grammar Conditions;


prog : expression  EOF # printResult
    ;

expression
	: field                            # column
	| NOT expression                   # nicht
	| OPEN inner=expression CLOSE      # klammern
        | 'head' OPEN expression CLOSE     # kopf
        | 'child' OPEN expression CLOSE    # child
        | 'prec' OPEN expression CLOSE     # vorher
        | 'next' OPEN expression CLOSE     # nachher
        | left=expression operator=AND right=expression  # und
	| left=expression operator=OR right=expression   # oder
        ;


field
    : UPOS       # checkUpos
    | LEMMA      # checkLemma
    | FORM       # checkForm
    | XPOS       # checkXpos
    | DEPREL     # checkDeprel
    | FEAT       # checkFeat
    | MISC       # checkMisc
    | ID         # checkID
    | HEADID     # checkHeadID
    | ABSEUD     # checkAbsEUD
    | RELEUD     # checkRelEUD
//    | EUD     # checkEUD
    | MTW        # checkMTW
    | ISEMPTY    # checkEmpty
    | ISMWT      # checkIsMWT
    ;


UPOS   : 'Upos:' [A-Z]+ ;
LEMMA  : 'Lemma:' ~[ \n\t)&|]+ ;
FORM   : 'Form:' ~[ \n\t)&|]+ ;
XPOS   : 'Xpos:' ~[ \n\t)&|]+ ;
DEPREL : 'Deprel:' [a-z]+( ':' ~[ \n\t)&|]+)? ;
FEAT   : 'Feat:' [A-Za-z_[\]]+ [:=] [A-Za-z0-9]+ ;
MISC   : 'Misc:' [A-Za-z_]+ [:=] ~[ \n\t)&|]+ ;
ID     : 'Id:' [1-9][0-9]* ; // no "n.m" nor "n-m" yet
MTW    : 'MWT:' [2-9] ; // length of a MWT in tokens
HEADID : 'HeadId:' [+-]?[0-9]+ ;
RELEUD    : 'EUD:' ([+-][0-9]+) [:=]  [a-z]+( ':' ~[ \n\t)&|]+)? ;
ABSEUD    : 'EUD:' ([0-9]+|'*') [:=]  [a-z]+( ':' ~[ \n\t)&|]+)? ;

ISEMPTY: 'IsEmpty' ; // emptyword
ISMWT  : 'IsMWT' ; // multi word tokenemptyword

AND   : 'and' | '&&' ;
OR    : 'or' | '||' ;
NOT   : '!' | '~' ;
OPEN  : '(';
CLOSE : ')';



// NEWLINE:'\r'? '\n' ;     // return newlines to parser (is end-statement signal)
WS : [ \t] + -> skip ;

