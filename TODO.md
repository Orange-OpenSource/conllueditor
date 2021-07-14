# TODO

* shortcuts for features (add or modify if present)
* API: modify more than a thing at a time

* "search and replace"
if condition: then

condition:
difficult:            UPOS:regex && (Lemma:regex || Feat:Key=regex || ....)
	Use a stack. When you encounter an open bracket, push whatever you're working on onto the stack and start the new expression. When you hit a closing bracket, pop the stack and use the expression you just calculated as the next item. Or, as previous posters have said, use recursion or a tree.

	and what if we get "A and B or C"  (precedance ....)

easier (only ands):   UPOS:regex Lemma:regex  Feat:Key=regex

action:
Feat:Key=Val
UPOS:upos
...


ANTLR Grammar for (Upos:VERB && (Lemma:mange || Feat:Number=3))

grammar Condition;

Upos:  "Upos:"[A-Z]+ ;
Lemma: "Lemma:"[^\s]+ ;
Feat:  "Feat:"[A-Za-z_]+=[A-Za-z0-9]+ ;



