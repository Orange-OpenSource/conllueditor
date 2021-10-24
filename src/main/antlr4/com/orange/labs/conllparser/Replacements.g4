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
 @version 2.13.1 as of 16th October 2021
*/

/* grammer for the rules to prepare the new values in search and replace and mass edit
   ... > column:"value"
   ... > column:token(column2)
   ... > column:head(head(column2))

e.g.

   ... > Upos:"NOUN"                       // set Upos to "NOUN"
   ... > Feat:"Number=Sing"                // adds a feature "Number=Sing"  (Number: deletes the feature)
   ... > Lemma:this(Form)                  // set lemma to the Form of current token
   ... > Lemma:this(Misc_Translit)         // set lemma to the key Translit from the Misc comlmn
   ... > Lemma:this(Form)+"er"             // set lemma to Form + "er"
   ... > Lemma:"de" + token(Form)          // set lemma to "de" +  Form
   ... > Feat:"Featname"+this(Lemma)       // set the feature Featnamer to the value of Lemma
   ... > Feat:"Gender"+this(Misc_Special)  // set the feature Gender to the value of the Misc Special
   ... > Misc:"Keyname"+head(head(Upos))   // set the key "Keyname" of column MISC to the Upos of the head of the head
   ... > Lemma:substring(this(Form), 1, 3) // set lemma to the substring (1 - 3) of Form
   ... > Lemma:substring(this(Form), 1)    // set lemma to the substring (1 - end) of Form

The grammer here sees only the part after the first ":"

*/

grammar Replacements;


prog : expression  EOF # printResult
    ;


expression
        : token ( ' '* '+' ' '* token )*      # element
        ;

token
        : THIS OPEN COLUMN CLOSE    # spalte
        | head                      # kopf
        | value                     # wort
        | 'substring' OPEN token ',' NUMBER  ( ',' NUMBER )? CLOSE  # substr
        | 'replace' OPEN token ',' value ',' value CLOSE            # repl
        | 'cap' OPEN token CLOSE    # gross
        | 'upper' OPEN token CLOSE  # block
        | 'lower' OPEN token CLOSE  # klein
        ;


head
      : HEADKW OPEN COLUMN CLOSE # kopfspalte
      | HEADKW OPEN inner=head CLOSE   # kopfkopf
      ;


value
//    : '"' (CHAR+ | NUMBER+)+ '"' # wortohne
    : CHARS                   # wortohne
    ;


// Lexer tules are uppercase

QUOTE : '"' ;
OPEN  : '(' ;
CLOSE : ')' ;
THIS : 'this' ;
HEADKW : 'head' ;
COLUMN : 'Form' | 'Lemma' | 'Upos' | 'Xpos' | 'Feat_' [A-Za-z0-9_]+ | 'Deprel' | 'Misc_' ~[")]+ ;

NUMBER: [0-9]+ ;
//CHAR :  ~["]  ;
CHARS: '"' ~["]+ '"' ;

//fragment NUMBER: [0-9];
//fragment NON_DIGITS : ~["0-9];
//CHAR: (NUMBER+ | NON_DIGITS+ (NUMBER+)?);


// NEWLINE:'\r'? '\n' ;     // return newlines to parser (is end-statement signal)
WS : [ \t] + -> skip ;

