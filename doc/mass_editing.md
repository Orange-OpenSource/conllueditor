# Mass Editing

Using a simple language, tokens of a `.conllu`-file can be edited if a condition is met

```
bin/replace.sh  rules.txt input.conllu > output.conllu
```

The rule file as line per rule

```
expression > new_values
```

## Expression 
_expression_ is a condition, and if true the _new values_ are set to the token which satisfies the condition. 
The _expression_ is a set of `key:values`, operators like `and`, `or`  or `!` (not) and parentheses:

Examples: 
* `Upos:ADP and !Deprel:case`: true if the current token has `ADP`  as UPOS and its deprel is not `case`. Available keys:
  * `Upos:` (Values: `[A-Z]+`)
  * `Xpos:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Lemma:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Form:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Deprel:` (Values: `[a-z]+`, optionally followed by `:` and  a string of  any character except whitespaces, `)` and `&`)
  * `Feat:` (Values: FeatureName=Value or FeatureName:Value. The Featurename must match `[A-Za-z_\[\]]+`, the Value `[A-Za-z0-9]+`)
  * `Misc:` (Values: MiscName=Value or MiscName:Value. MiscName must match `[A-Za-z_]+`, the Value can be any string without  whitespaces, `)` and `&`)
  * `Id:` (Values: integer)
  * `MTW:` (Values: length of the mult-token word `[2-9]`)
  * `Empty` (no value, true if the current node is empty)

 `Lemma` and `Form` can have either a regex as argument or a filename of a file which contains a list of forms or lemmas:
  * `Lemma:sing.* > misc:Value=Sing`
  * `Lemma:#mylemmas.txt > misc:Value=Sing` (if the file `mylemmas.txt` does not exist, the condition is false)


In addition to key keys listed above, four functions are available to take the context of the token into account:
* `child()` child of current token
* `head()` head of current token
* `prec()` preceding token
* `next()` following token

For example:
* `head(head(Upos:VERB and Feat:Tense=Past))`: true if the current token has a head who has a head with UPOS `VERB and the feature `Tense=Past`
* `child(Upos:VERB && Feat:VerbForm=Part) and child(Upos:DET)`: true if the current token has a dependant with UPOS `VERB`
and a feature `VerbForm=Part` and another child with UPOS `DET`. 

Functions can be nested (eventhough `child(head())` does not make sense, does it :-)

The same language is used for complex search and replace

## New Values

`new_values` is a list of key:values to set to the current token. Possible keys
* `form` (`form:newform`)
* `lemma`
* `upos`
* `xpos`
* `deprel`
* `feat`  (`feat:Featname=Value` or `feat:Featname:Value`, an empty value like `feat:Number=` deletes the feature from the word)
* `misc`  (`misc:Key=Value` or `misc:Key:Value`, an empty value deletes the misc from the word)

