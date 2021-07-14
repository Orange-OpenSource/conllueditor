## Mass Editing

**Experimental** Using a simple language, tokens of a `.conllu`-file can be edited if a condition is met

```
bin/replace.sh  rules.txt input.conllu > output.conllu
```

The rule file as line per rule

```
expression > new_values
```

_expression_ is a condition, and if true the _new values_ are set to the token which satisfies the condition. 
The _expression_ is a set of `key:values`, operators like `and`, `or`  or `!` (not) and parentheses:

Examples: 
* `Upos:ADP and !Deprel:case`: true if the current token has `ADP`  as UPOS and its deprel is not `case`. Available keys:
  * `Upos`
  * `Xpos`
  * `Lemma`
  * `Form`
  * `Deprel`
  * `Feat` (Featname=Value)
  * `Id`
  * `MTW` (length of the mult-token word)
  * `Empty` (true if the current node is empty)
* `head(head(Upos:VERB and Feat:Tense=Past))`: true if the current token has a head who has a head with UPOS `VERB and the feature `Tense=Past`
* `child(Upos:VERB && Feat:VerbForm=Part) and child(Upos:DET)`: true if the current token has a dependant with UPOS `VERB`
and a feature `VerbForm=Part` and another child with UPOS `DET`. Available functions:
  * `child()` child of current token
  * `head()` head of current token
  * `prec()` preceding token
  * `next()` following token

Functions can be nested (eventhough `child(head())` does not make sense, does it :-)

`new_values` is a list of key:values to set to the current token. Possible keys
* `form` (`form:newform`)
* `lemma`
* `upos`
* `xpos`
* `deprel`
* `feat`  (`feat:Featname=Value`)
* `misc`  (`misc:Key=Value`)

If there is interest in the community, I think this format could be integrated in a search and replace function in the ConlluEditor.
Leave me an issue!
