# Mass Editing

Using a simple language, tokens of a `.conllu`-file can be edited if a condition is met

```
bin/replace.sh rules.txt input.conllu [--nostrict] > output.conllu
```

The rule file as line per rule

```
condition > new_values
```

## Condition
`condition` is a logical expression which is evaluated for each word, and if true the _new values_ are set to the token which satisfies the condition.
The `condition` is a set of `key:values`, operators like `and`, `or`  or `!` (not) and parentheses. The condition may contain whitespaces:

Examples:
* `Upos:ADP and !Deprel:case`: true if the current token has `ADP`  as UPOS and its deprel is not `case`. Available keys:
  * `Upos:` (Values: `[A-Z]+`)
  * `Xpos:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Lemma:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Form:` (Values: string of any character except whitespaces, `)` and `&`)
  * `Deprel:` (Values: string of any character except whitespaces, `)` and `&`, optionally followed by `:` and  a string of  any character except whitespaces, `)` and `&`)
  * `HeadId:` (Values: `[+-][0-9]+` (relative from current head) or `[0-9]+` (absolute head id), true if the head of the current token matches
  * `EUD:` (Values: `[+-][0-9]+` `:`, deprel, if EudHeadId is `*` any head position is accepted, without `-` or `+` the EudHead is interpreted as an absolute value))
  * `Feat:` (Values: FeatureName=Value or FeatureName:Value. The Featurename must match `[A-Za-z_\[\]]+`, the Value `[A-Za-z0-9]+`)
  * `Misc:` (Values: MiscName=Value or MiscName:Value. MiscName must match `[A-Za-z_]+`, the Value can be any string without  whitespaces, `)` and `&`)
  * `Id:` (Values: integer)
  * `MWT:` (Values: length of the multi-word token `[2-9]`)
  * `IsEmpty` (no value, true if the current node is empty)
  * `IsMWT` (no value, true if the current node is empty)

`Form:`, `Lemma:` and `Xpos:` can contain simple regular expression (only the character ')' cannot be used

`EUD` cannot deal (yet) with empty word ids (`n.m`)

 `Lemma` and `Form` can have either a regex as argument or a filename of a file which contains a list of forms or lemmas:
  * `Lemma:sing.* > misc:"Value=Sing"`
  * `Lemma:#mylemmas.txt > misc:"Value=Sing"` (if the file `mylemmas.txt` does not exist, the condition is false)


In addition to key keys listed above, four functions are available to take the context of the token into account:
* `child()` child of current token
* `head()` head of current token
* `prec()` preceding token
* `next()` following token

For example:
* `head(head(Upos:VERB and Feat:Tense=Past))`: true if the current token has a head who has a head with UPOS `VERB and the feature `Tense=Past`
* `child(Upos:VERB && Feat:VerbForm=Part) and child(Upos:DET)`: true if the current token has a dependant with UPOS `VERB`
and a feature `VerbForm=Part` and another child with UPOS `DET`.
* `head(next(Upos:NOUN))`: true if the current token has a head which is followed by a token with UPOS `NOUN`

Functions can be nested (eventhough `child(head())` does not make sense, does it :-)

The same language is used for complex search and replace

For more information check the [formal grammar for conditions](conditions/README.md).


## New Values

`new_values` is a whitespace separated list of `targeted_colum:value` which modify the tokens matched the condition.

The `targeted_column` indicates which column of the word a new value is assigned to:
Possible `keys`:
* `Form`
* `Lemma`
* `Upos`
* `Xpos`
* `Deprel`
* `HeadId`
* `Feat`
* `Eud`
* `Misc`
(the `Id` column cannot be changed).

`value` is a combination (using ` +`) of strings or functions which give access to other columns of the current word or it's head. Strings must be included
in double quotes `"NOUN"`.

`column_name` to retrieve a value from can be:
* `Form`
* `Lemma`
* `Upos`
* `Xpos`
* `Feat_<FeatureName>`
* `Deprel`
* `Misc_<KeyName>`
* `HeadId`

Available functions are:
* `this(<column_name>)` value of the given column of the current token
* `head(<column_name>)` value of the given column of the head of the current token
* `head(head(<column_name>)` value of the given column of the head's head of the current token
* `substring(this()/head(), start, end)`  take the substring of the this/head expression from `start` to `end`
* `substring(this()/head(), start)`  take the substring of the result of the this/head expression from ` start` until the end of the string
* `upper(this()/head())`  uppercase the result of the this/head expression
* `lower(this()/head())` lowercase the result of the this/head expression
* `cap(this()/head())` capitalize (first character uppercase, rest lowercase) the result of the this/head expression
* `replace(this()/head(), regex, newstring) replaces the `regex` of the result fo the this/head expression by `newstring`

If a token has a head 0, it's deprel will always be `root` unless the option `--nostrict` is used with `replace.sh`

## Examples

* `Upos:"NOUN"`                       set Upos to `NOUN`
* `Eud:"+2:dep"`                      add a enhanced UD relation "dep" using the current id + 2 (must be a negative or positive integer without 0 (if resulting head id is out of the sentence, the head id is not modified)
* `Eud:head(HeadId)+":"+head(Deprel)` set EUD to head and deprel of the headword
* `HeadId:"+2"`                      set head to current ud + 2 (must be a negative or positive integer without 0 (if resulting head id is out of the sentence, the head id is not modified)
* `HeadId:"-1"`                      set head to current ud - 1
* `HeadId:"5"`                       set head to 5 (n must be 0 or a positive integer)
* `HeadId:head(Headid)`              set head to the headid of head node
* `Feat:"Number=Sing"`               adds a feature `Number=Sing`  (Number: deletes the feature)
* `Lemma:this(Form)`                set lemma to the form of current token
* `Lemma:this(Misc_Translit)`       set lemma to the key `Translit` of the `Misc` column
* `Lemma:this(Form)+"er"`           set lemma to the form + "er"
* `Lemma:"de"+token(Form)`         set lemma to "de" + form
* `Feat:"Featname"+this(Lemma)`       set the feature Featname to the value of Lemma
* `Feat:"Gender"+this(Misc_Special)` set the feature Gender to the value of the Misc Special
* `Misc:"Keyname"+head(head(Upos))`    set the key "Keyname" of `Misc` column  to the Upos of the head of the head
* `Lemma:substring(this(Form),1,3)`      set lemma to the substring (1 - 3) of the form
* `Lemma:substring(this(Form),1)`       set lemma to the substring (1 - end) ofthe form
* `Form:replace(this(Form),"é","e")`  replace all occurrances of `é` in the form by `e`

N.B. **no white spaces allowed in a value expression!**
therefore `Lemma:substring(this(Form), 1, 3)` or Lemma:this(Form) + "er"` are invalid,
use `Lemma:substring(this(Form),1,3)` or Lemma:this(Form)+"er"` instead.

In order to empty a column, just set it to `"_"`: `Feat:"_"`, `Xpos:"_"`, `Eud:"_"` etc.



For more information check the [formal grammar for replacements](replacements/README.md) (the part after the first `:`).

