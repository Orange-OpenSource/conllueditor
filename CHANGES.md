# Changes

## Version 2.29.0
* new option to load UI configuration file added
* new dependency to snakeyaml

## Version 2.28.1
* java import error corrected

## Version 2.28.0
* changed format of `shortcut.json` file (old format still understood)
* shortcuts can modify a list of things (UPOS, XPOS, Deprels, FEATS, MISC)

## Version 2.27.0
* if options `--features and --language` are used, in table view mode features can be easier edited
* commented legacy code

## Version 2.26.1
* more specific error messages when loading an invalid file
* show all errors in an CoNLL-U file (not just the first)
* new tests

## Version 2.26.0
* rudimental check to stop two clients modify the same sentence at the same time
* new test

## Version 2.25.6
* correction for warning if feature is used with UPOS which is not valid (needs use of `--features`)

## Version 2.25.5
* accept sentences without `# text = ...` (but issue warning)

## Version 2.25.4
* table view: Ctrl-click on Word ID keeps Word marked until sentence is changed or ID clicked again (to accelerate edtiing using shortcuts)

## Version 2.25.3
* corrections:
   * add non-standard columns when adding words and MWT
   * unmark word when changing current sentence (n  in table view)
* use `*` instead of `_` for empty non-standard columns (CoNLL-U plus)
* new tests

## Version 2.25.2
* clear button also empties comment search

## Version 2.25.1
* table view:
  * keep modified column width
  * FORM, LEMMA, UPOS, XPOS are also modifiable

## Version 2.25.0
* table view:
  * buttons to change display width of FEATS, DEPS and MISC column
  * short cuts work here as well now

## Version 2.24.0
* compiling with openjdk 17
* unitary tests upgraded to jupiter.junit 5
* dependencies upgraded to latest version
* new tests for server API

## Version 2.23.0
* if `show basic in enhanced` is activated, this also applies for LaTeX output
* new test

## Version 2.22.4
* `Feat:VerbForm=` or `Misc:SpaceAfter=` (no value given) searches for words which have the feature/misc name with any value (use `not Feat:VerbForm:` to look for words which do not have the given feature)
* new tests

## Version 2.22.3
* bug concerning --rootdir corrected

## Version 2.22.2
* MISC shortcut correction, `mod addmisc` added
* new test

## Version 2.22.1
* undo/redo bug corrected
* check json encoded error messages in tests

## Version 2.22.0
* shortcuts for MISC column added
* update `# text = ...` after `sentsplit`, `sentjoin`, `compose`, `split` and `join`
* tests updated

## Version 2.21.1
* corrected `bin/replace.sh` for MacOS users

## Version 2.21.0
* warning if the `# text = ...` does not correspond to the concatenated forms (respecting Space(s)After)
* inputfield to edit value for `# text` or set from concatenated forms
* tests updated
* new tests

## Version 2.20.0
* added a search fonction which implements (partially) grew match search patterns
* search menu acces changed to combo box
* new tests

## Version 2.19.5
* corrected Locale issue in tests

## Version 2.19.4
* bug corrected in joining two tokens

## Version 2.19.3
* option `-r` no longer needed (using another way to calculate the position of `conllueditor/gui` from the compiled `.jar`-file

## Version 2.19.2
* correction in legacy function

## Version 2.19.1
* correction in metadata editing test
* allow compilation without `.git`

## Version 2.19.0
* validation rules (if a list of conditions applies to word than another list of conditions must apply too)
* new tests

## Version 2.18.1
* more tests for value access
* added compatibility test `~` in addition to strict comparison with `=`
* validator shortcut changed from `=` to `!`
* check for absent features/misc-keys

## Version 2.18.0
* extension to mass-edit/complex search&replace: possibility to search heads/childs etc with same Feature value or same UPOS etc
* new tests

## Version 2.17.5
* new option --shortcutTimeout to set the maximal time (milliseconds) between to keys of a shortcut sequence

## Version 2.17.4
* shortcuts sequence must be taped with max 300ms between keys. This permits having short and long shortcuts (e.g. A, and AV)
* bug correction in complex search: child(child(...))

## Version 2.17.3
* more file statistics, doc updated

## Version 2.17.2
* search&replace save problem corrected, doc updated

## Version 2.17.1
* add feature/values in statistics, update numbers if file was edited

## Version 2.17.0
* show CoNLL-U file statistics (sentences, tokens, words, MWTs, UPOS, deprels)

## Version 2.16.2
* in totally empty features/misc field in editor is replaced with `_`
* test extended and new test

## Version 2.16.1
* `IsMWT` and `IsEmpty` in complex search fixed (issue #16)
* new tests

## Version 2.16.0
* make `transliterate.py` more robust against invalid CoNLL-U files
* shortcuts to scroll the tree that a given token is visible in the current viewport

## Version 2.15.4
* optional json output for ConlluComporator

## Version 2.15.3
* test corrected

## Version 2.15.2
* use commons-cli to parse command-line options for ConlluEditor and ConlluComparator
* new tests

## Version 2.15.1
* corrected confusion: ConlluEditor deals with **MWT** (multiword tokens) and not with _MTW_s...

## Version 2.15.0
* added a script to find similar or identical sentences in a single or multiple CoNLL-U files

## Version 2.14.3
* updated versions of dependencies in `pom.xml` to latest version available

## Version 2.14.2
* possibility to modify `deprel` also in the word edit menu

## Version 2.14.1
* mass editing
  * error corrected
  * condition `Empty` renamed in `IsEmpty`, new conditions `IsMWT`, `HeadID`, `EUD`
  * new changeable colums: `Head`, `EUD`
  * new columns to get data from: `HeadId`
  * new tests

## Version 2.14.0
* add/edit sentence metadata (`sent_id`, `newdoc`, `newpar`, transliteration, translations)
* button to initialise sentence metadata `translit` from `MISC:Translit` field if present
* script to add an initial version of Form transliteration (`MISC:Translit`)

## Version 2.13.1
* adding grammar for replacements (and changed format for search and replace)
* correction with subclass issue

## Version 2.13.0
* adding a complex search mode (syntax as in mass editing) and search-and-replace

## Version 2.12.4
* mass-editing:
   * allow brackets in feature names like `Number[psor]=...`
   * delete features if value is empty

## Version 2.12.3
* parser client configuration: accept definition of headers

## Version 2.12.2
* Lemma: and Form: can have filenames as argument

## Version 2.12.1
* added `Misc:...` to search and replace tool

## Version 2.12.0
* Lexer/Grammar based search and replace
* check for missung root nodes, cycles and invalid head ID's and list errors when starting server
* new tests

## Version 2.11.2
* help page updated, `_` key deletes features of active word

## Version 2.11.1
* shortcut display (key `?`) corrected

## Version 2.11.0
* added subtree search (in ConLL-U or sd-parse format)
* importing subtrees of a given word
* tests

## Version 2.10.3
* sort features case insensitive in all cases (even when duplicating a token)

## Version 2.10.2
* bug in maven tests corrected (using `--saveAfter 1` in tests to have output file written immediately)

## Version 2.10.1
* shortcuts can have any length, shortcuts to set _Feature=Value_ pairs
* new tests

## Version 2.10.0
* new default for saving: without the option `--saveAfter` the conllu file is saved whenever a new sentence is chosen (next, prec, ...)
  the old behaviour (save every modification immediately) can be achieved using the option `--saveAfter 1`
* shortcuts can be defined for two keys
* `mod compose startid length [form]` optional form of the new MWT

## Version 2.9.1
* correction: replace `\n` in `MISC:SpacesAfter=` by space in `# text = ` metadata line

## Version 2.9.0
* options `--features` and `--deprels` can read official specifications for valid features and deprels
  in https://github.com/UniversalDependencies/tools/tree/master/data (`features.json` and `deprels.json`)
* new option `--language` (to read the feature/deprel definitions for a given language (if not given, use only universal features/deprels)
* new option `--include_unused` (use features declared as unusued for given language)

## Version 2.8.2
* help text slightly corrected

## Version 2.8.1
* process `SpacesBefore` correctly
* junit 4.12 -> 4.13.1

## Version 2.8.0
* add `--compare` option to Docker image
* make `MISC` autocompletion work when editing MWT
* new command to transform existing word in MWT
* new tests

## Version 2.7.5
* Warning improved
* bug corrected in MWT creation (delete Space(s)After from MWT member tokens and add value from last memeber token to the MWT token
* put concatenated forms of created MWT to the FORM column of the MWT

## Version 2.7.4
* Error if edited file is not controlled under git AND there is already a backup-file (.2) in order to avoid overwriting the output of a preceding editing

## Version 2.7.3
* bug corrected when inserting new MWT and serializing MWTs with Space(s)After to CoNLL-U

## Version 2.7.2
* search: find word on multiple criteria

## Version 2.7.1
* error corrected for sentsplit of sentences with enhanced dependencies and/or empty words
* display roots of enhanced deps (in flat view)
* new test

## Version 2.7.0
* add and delete empty words (enhanced dependencies)
* delete normal words

## Version 2.6.1
* deleted causes for warnings

## Version 2.6.0
* table edit form

## Version 2.5.2
* case insensitive ordering of features (names) in CoNLL-U

## Version 2.5.1
* allow comma seperated feature value list

## Version 2.5.0
* new option `--features` to specify a list of files which define valid _feature=value_ pairs and lists of valid features for a given upos
* autocomplete for _feature=value_ pairs and misc entries

## Version 2.4.3
* multi word token larger, bug for variable size corrected
* fixing issue #7 (if two words overlapping with a MWT are joined, the MWT will be deleted)
* fixing issue #6 (joining/splitting sentences sets correctly `# text =` metadata)
* new tests, tests updates

## Version 2.4.2
* option "auto-adapt" which sets the horizontal width of words to the size needed
* added test whether we are on MacOS in start script

## Version 2.4.1
* test error corrected

## Version 2.4.0
* first (partial) implementation of CoNLL-U plus format. Only conllup files with 10 standard columns and additional columns can be edited
* correction for displaying data in columns > 10
* adding tool to convert any CoNLL-U/CoNLL-U Plus in any other

## Version 2.3.2
* deal correctly with very big number values in `MISC` column (like `LNumValue=4000000000`)

## Version 2.3.1
* new field to edit MISC column for MultiToken words

## Version 2.3.0
* new option `--compare` to compare a CoNLL-U file with the currently edited file (shown in tree and hedge mode)

## Version 2.2.0
* search field for morpho syntactic features
* highlight found words according to column prefix used in "find any" search (f,l,u,x,d)

## Version 2.1.1
* clear button for search fields
* bug correction in "find any" search

## Version 2.1.0
* search for sentence id

## Version 2.0.3
* getLaTex optimized, tests updated

## Version 2.0.2
* read/write correctly sentence initial empty nodes

## Version 2.0.1
* legacy code deleted

## Version 2.0.0
* new functionality: Parser ConLL-U front end in order to see the CoNLL-U result as dependency tree (or hedge)

## Version 1.14.8
* accept d: in 'any' search field

## Version 1.14.7
* `ConllSentence.toString()`: add final empty line

## Version 1.14.6
* bug corrected: missing write

## Version 1.14.5
* bug corrected: NullPointerException at multitokens created with "compose <start> <length>"

## Version 1.14.4
* factorisation in ConllWord class
* added shortcuts: to go to next (`+`) or preceding (`-`) sentence, run validation (`=`)

## Version 1.14.3
* layout, save button only visible when server is started with `--saveAfter` option

## Version 1.14.2
* add JSON export (following Spacy)

## Version 1.14.1
* modification to make ConlluEditor run with Edge
* layout regression in 1.14.0 corrected

## Version 1.14.0
* added shortcuts to change UPOS or deprel with click + key (hardwired defaults, gui/shortcuts.json file to personalise)

## Version 1.13.1
* add xpos and extra columns to LaTeX output

## Version 1.13.0
* can display columns > 10, if present (new button "extra cols")
* verbosity in test
* tests updated for the extra columns
* corrected bug for right2left display of sentences with empty words

## Version 1.12.7
* bug in tests (without git) corrected

## Version 1.12.6
* conlluedit.sh accepts filenames with spaces
* relaxed mode (corrects some formal errors in conllu-file)
* show linenumbers in error messages
* layout problem corrected

## Version 1.12.5
* searching chaines of deprels (up and down) corrected

## Version 1.12.3
* new bug from version 1.12.2 fxied

## Version 1.12.2
* bug corrected in undo/redo

## Version 1.12.1
* bug corrected for split/join function (if a MWT or enhanced dependency followed)
* minor layout modification

## Version 1.12.0
* support for enhanced dependencies (graphical editing)
* form search corrected
* doc updated

## Version 1.11.1
* Bug corrections:
  * in flat mode: shift enhanced down dependencies if features are shown
  * split misc field only at pipe and LF, not at comma
  * larger modal for conllu/latex/validation output
  * tests updated: added features to sentence with enhanced dependencies

## Version 1.11.0
* added support for an external validation script

## Version 1.10.0
* LaTeX output also for deptree.sty (see doc/)

## Version 1.9.0
* added split position parameter to word split function
* add support for creating, editing and deleting multiword tokens

## Version 1.8.2
* double click to edit a word (form, lemma, UPOS, ...), useful for tablets which usually do not have a control key
* button to edit comments (to edit empty comments)

## Version 1.8.1
* return version with /edit/validlists
* display version


