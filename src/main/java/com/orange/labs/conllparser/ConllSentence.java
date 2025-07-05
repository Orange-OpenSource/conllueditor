/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2025, Orange S.A.

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
 @version 2.32.0 as of 5th July 2025
*/
package com.orange.labs.conllparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orange.labs.conllparser.ConllWord.EnhancedDeps;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * read and parse CONLL
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConllSentence {

    protected List<ConllWord> words; // no empty words and MWT
    protected Map<Integer, List<ConllWord>> emptywords = null; // main id (before "."): empty nodes
    protected Map<Integer, ConllWord> contracted = null; // contracted words (MWT) startid (before hyphen): word

    private boolean hasEnhancedDeps = false; // at least one word with enhanced deps (in this case we add basic deps to deps in conllu output)

    protected ConllWord head = null; // rempli par makeTrees(): premiere ou seule tete
    protected List<ConllWord> headss = null; // rempli par makeTrees(): toutes les tetes

    // initiated by Deb2Chunkd.detect();
//    Map<Integer, Integer> word2chunks = null; // word-id: chunk-id
//    Map<Integer, Set<Integer>> chunks2word = null; // chunk-id: word-ids
    private String newpar = null;
    private String newdoc = null;
    private String sentid = null;
    private String translit = null;
    private Map<String, String> translations = null; // lg: text
    private String text = null; // # text = ... (only to detect errors, when editing (TODO). For the output we regenerate this from the words
    private int maxdist = 0; // maximal distance between a word and its head (calculated by mameTrees())

    // store preceding comments
    private List<String> comments = null;

    // needed to find a sentences with line number. The empty line at the end of a CoNLL-U block is counted as well
    private int number_of_conllu_lines = 0;
    private int number_of_comments = 0; // includes newpar, newdoc etc
    private boolean is_modified = true;

    //private boolean nextToStringcomplete = false; // le prochain toString() rajoute les colonnes prefixées
    Map<String, Integer> columndefs = null;
    private int last_modified = 0; // last modification date in this session. To avoid to users edit the same sentence at the same time. When a modification is sent by the client, the modifcation date must still be the same

    public enum Scoretype {
        /*FORM, */
        LEMMA, UPOS, XPOS, FEATS, LAS
        /*, CLAS*/
    };

    /**
     * @param conlllines les lignes d'une phrase d'un fichier CONLL à parser
     */
    public ConllSentence(List<AbstractMap.SimpleEntry<Integer, String>> conlllines, Map<String, Integer> columndefs) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        this.columndefs = columndefs;
        parse(conlllines);
    }

    public ConllSentence(String conllstring, Map<String, Integer> columndefs) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines = new ArrayList<>();
        int ct = 0;
        for (String line : conllstring.split("\n")) {
            sentenceLines.add(new AbstractMap.SimpleEntry<Integer, String>(ct, line));
        }
        this.columndefs = columndefs;
        parse(sentenceLines);
    }

    public ConllSentence(List<ConllWord> cw) {
        words = cw;
        //hasEnhancedDeps = words.get(0).isBasicdeps_in_ed_column();

        comments = new ArrayList<>();
        for (ConllWord w : words) {
            if (!w.getDeps().isEmpty()) {
                hasEnhancedDeps = true;
                break;
            }
            break; // we check only the first word
        }
    }

    /**
     * cloner une phrase (sans dépendances)
     *
     * @param orig sentence to be cloned
     */
    public ConllSentence(ConllSentence orig) {
        words = new ArrayList<>();
        comments = new ArrayList<>(orig.comments);
        hasEnhancedDeps = orig.hasEnhancedDeps;
        for (ConllWord word : orig.getWords()) {
            words.add(new ConllWord(word));
        }
        if (orig.emptywords != null) {
            emptywords = new HashMap<>();

            for (Integer ei : orig.emptywords.keySet()) {
                List<ConllWord> ews = new ArrayList<>();
                for (ConllWord cw : orig.emptywords.get(ei)) {
                    ews.add(new ConllWord(cw));
                }
                emptywords.put(ei, ews);
            }
        }

        if (orig.contracted != null) {
            contracted = new HashMap<>();
            for (Integer id : orig.contracted.keySet()) {
                contracted.put(id, new ConllWord(orig.contracted.get(id)));
            }
        }
        newdoc = orig.newdoc;
        newpar = orig.newpar;
        sentid = orig.sentid;
        translit = orig.translit;
        text = orig.text;
        if (orig.translations != null) {
            translations = new HashMap<>();
            translations.putAll(orig.translations);
        }
        columndefs = orig.columndefs;
        // every time the sentence is modified in a ConlluEditor sesscion we increase this counter
        // a client gets the current value, and when the client sents the modification back to the server the
        // value on the server side must not have changed. If it has changed a second client edited the same
        // sentence at the same time and was quicker to send it back to the server.
        // ConlluEditor.process() increases this value if the sentences was succesfully modifed
        // it would have been better to use system time here, but this coplicated the unittests too much for
        // the time being
        last_modified = 0; //System.currentTimeMillis();
    }

    private void parse(List<AbstractMap.SimpleEntry<Integer, String>> conlllines) throws ConllException {
        words = new ArrayList<>();
        comments = new ArrayList<>();
        hasEnhancedDeps = false;
        List<String> lastnonstandardinfo = null;
        Pattern translationFields = Pattern.compile("^# text_([a-z]{2,}) *= *(.*)$");
        int hlt = 0;
        int hld = 0;
        Set<String> highlighttokens = null;
        Set<String> highlightdeprels = null;

        List<ConllException> errors = new ArrayList<>();
        for (AbstractMap.SimpleEntry<Integer, String> cline : conlllines) {
            try {
                String line = cline.getValue();
                if (line.startsWith("#")) {
                    if (line.startsWith("# newpar")) {
                        newpar = line.substring(8).trim();
                        if (newpar.startsWith("id =")) {
                            newpar = newpar.substring(4).strip();
                        }
                    } else if (line.startsWith("# newdoc")) {
                        newdoc = line.substring(8).trim();
                        if (newdoc.startsWith("id =")) {
                            newdoc = newdoc.substring(4).strip();
                        }
                    } else if (line.startsWith("# sent_id = ")) {
                        sentid = line.substring(12).trim();
                    } else if (line.startsWith("# text = ")) {
                        text = line.substring(9).trim();
                    } else if (line.startsWith("# translit = ")) {
                        translit = line.substring(13).trim();
                    } else if (line.startsWith("# text_")) {
                        if (translations == null) {
                            translations = new HashMap<>();
                        }
                        Matcher m = translationFields.matcher(line);
                        if (m.matches()) {
                            translations.put(m.group(1), m.group(2));
                        } else {
                            System.err.format("WARNING: ignoring invalid '# text_LG' line %d: \"%s\"\n", cline.getKey(), line);
                        }
                    } else if (line.startsWith("# highlight tokens =")) {
                        String tmp = line.substring(20).trim();
                        highlighttokens = new TreeSet<>(Arrays.asList(tmp.split("\\s+")));
                        hlt = cline.getKey();
                    } else if (line.startsWith("# highlight deprels =")) {
                        String tmp = line.substring(21).trim();
                        hld = cline.getKey();
                        highlightdeprels = new TreeSet<>(Arrays.asList(tmp.split("\\s+")));
                        for (String t: highlightdeprels) {
                            if (t.contains("-")) {
                                System.err.format("WARNING: ignoring invalid token for highlighting deprels '%s'. line %d: \"%s\"\n", t, cline.getKey(), line);
                            }
                        }
                    } else {
                        comments.add(line.substring(1).trim());
                    }
                    continue;
                }
                String[] fields = line.split("\t", 1);
                //System.err.println("LINE\t" + line + " ");
                if (fields.length < 1) {
                    System.err.format("WARNING: ignoring short line %d: \"%s\"\n", cline.getKey(), line);
                    continue;
                }

                ConllWord w = new ConllWord(line, lastnonstandardinfo, columndefs, cline.getKey());
                w.setMysentence(this);
                if (highlighttokens != null && highlighttokens.contains(w.getFullId())) {
                    w.setCheckToken(true);
                }
                if (highlightdeprels != null && highlightdeprels.contains(w.getFullId())) {
                    w.setCheckDeprel(true);
                }
                if (!w.getDeps().isEmpty() /* || w.isBasicdeps_in_ed_column() */) {
                    hasEnhancedDeps = true;
                }

                if (w.getTokentype() == ConllWord.Tokentype.WORD) {
                    if (!w.hasXpostag("NON-VU")) {
                        words.add(w);
                    }
                } else if (w.getTokentype() == ConllWord.Tokentype.EMPTY) {
                    if (emptywords == null) {
                        emptywords = new HashMap<>();
                    }
                    List<ConllWord> ew = emptywords.get(w.getId());
                    if (ew == null) {
                        ew = new ArrayList<>();
                        emptywords.put(w.getId(), ew);
                    }
                    ew.add(w);
                } else { // w.getTokentype() == ConllWord.Tokentype.CONTRACTED)
                    if (contracted == null) {
                        contracted = new HashMap<>();
                    }
                    contracted.put(w.getId(), w);
                }
            } catch (ConllException ex) {
                // catch all errors of the sentence, not just the first
                errors.add(ex);
            }
        }
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConllException ex : errors) {
                sb.append(ex.getMessage()).append('\n');
            }
            throw new ConllException(sb.toString());
        }
        if (highlighttokens != null) {
            Set<String>to_remove = new HashSet<>();
            for (String t: highlighttokens) {
                if (getWord(t) == null) {
                    System.err.format("WARNING: ignoring invalid token for highlighting tokens '%s'. line %d\n", t, hlt);
                    to_remove.add(t);
                }
            }
            for (String t : to_remove) {
                highlighttokens.remove(t);
            }
        }
        if (highlightdeprels  != null) {
            Set<String>to_remove = new HashSet<>();
            for (String t: highlightdeprels) {
                if (getWord(t) == null) {
                    System.err.format("WARNING: ignoring invalid token for highlighting deprels '%s'. line %d\n", t, hld);
                    to_remove.add(t);
                }
            }
            for (String t : to_remove) {
                highlightdeprels.remove(t);
            }
        }

    }

    // adds a new emptyword, at the position given in emptyword.
    public void addEmptyWord(ConllWord emptyword) {
        emptyword.setTokenType(ConllWord.Tokentype.EMPTY);
        if (emptywords == null) {
            emptywords = new HashMap<>();
        }
        List<ConllWord> ew = emptywords.get(emptyword.getId());
        if (ew == null) {
            ew = new ArrayList<>();
            emptywords.put(emptyword.getId(), ew);
            emptyword.setSubId(1);
        }
        ew.add(emptyword);
        emptyword.setSubId(ew.size());
        emptyword.setMysentence(this);
        is_modified = true;
    }

    public Map<String, Integer> getColumndefs() {
        return columndefs;
    }

    public boolean isValidExtraColumn(String colname) {
        if (columndefs == null) {
            return false;
        }
        return columndefs.containsKey(colname);
    }

    public void setHasEnhancedDeps(boolean hasEnhancedDeps) {
        this.hasEnhancedDeps = hasEnhancedDeps;
    }

    public List<String> getComments() {
        return comments;
    }

    public String getCommentsStr() {
        if (comments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String c : comments) {
            sb.append(c).append('\n');
        }
        return sb.toString();
    }

    public void setComments(String co) {
        if (co != null) {
            comments.clear();
            if (!co.isEmpty()) {
                String[] elems = co.trim().split("\n");
                for (String e : elems) {
                    if (!e.isEmpty()) {
                        comments.add(e);
                    }
                }
            }
            is_modified = true;
        }
    }

    public void setComments(List<String> co) {
        if (co != null) {
            comments = co;
            is_modified = true;
        }
    }

    public int size() {
        return words.size();
    }

    public ConllWord getHead() {
        return head;
    }

    public List<ConllWord> getHeads() {
        return headss;
    }

    public void normalise() {
        normalise(1);
    }

    /**
     * normaliser les index (y compris pour les relations de dépendances
     *
     * @param first id du premier mot
     */
    public void normalise(int first) {
        Map<Integer, Integer> oldnewIds = new HashMap<>(); // correspondances entre des IDs originaux et les IDs normalisés
        int ct = first;

        for (ConllWord word : words) {
            // stocker les anciens IDs
            oldnewIds.put(word.getId(), ct);
            // mettre à jour les IDs
            word.setId(ct);
            ct++;
        }

        // mettre à jours les IDs des tetes
        for (ConllWord word : words) {
            if (word.getHead() > 0) {
                word.setHead(oldnewIds.get(word.getHead()));
            }

            if (word.getDeps() != null) {
                for (ConllWord.EnhancedDeps ed : word.getDeps()) {
                    if (ed.headid > 0) {
                        ed.headid = oldnewIds.get(ed.headid);
                    }
                }
            }
        }

        if (contracted != null && !contracted.isEmpty()) {
            Map<Integer, ConllWord> c2 = new HashMap<>();
            for (ConllWord cw : contracted.values()) {
                cw.setId(oldnewIds.get(cw.getId()));
                cw.setSubId(oldnewIds.get(cw.getSubid()));
                c2.put(cw.getId(), cw);
            }
            contracted = c2;
        }
        // TODO emptywords
        if (emptywords != null) {
            Map<Integer, List<ConllWord>> e2 = new HashMap<>();
            for (List<ConllWord> ews : emptywords.values()) {
                for (ConllWord ew : ews) {
                    if (ew.getId() != 0) {
                        // enhanced dependencies may contain words with id 0.1
                        ew.setId(oldnewIds.get(ew.getId()));
                    }
                    for (ConllWord.EnhancedDeps ed : ew.getDeps()) {
                        if (ed.headid > 0) {
                            ed.headid = oldnewIds.get(ed.headid);
                        }
                    }
                }
                e2.put(ews.get(0).getId(), ews);
            }
            emptywords = e2;
        }
    }

    /**
     * split the sentence in two, truncate this, and return the rest as a new sentence
     * @param id first word in split sentence
     * @return the new sentence, split form the current one
     */
    public ConllSentence splitSentence(int id) {
        id--;
        ConllSentence newsent = new ConllSentence(this);
        newsent.newdoc = null;
        newsent.newpar = null;
        if (newsent.sentid != null) {
            newsent.sentid += "-bis";
        }

        // cut emptywords before id from newsent
        Set<Integer> to_delete_from_this = new HashSet<>();
        if (emptywords != null) {
            for (Integer key : emptywords.keySet()) {
                if (key < id + 1) { // id is position of word in List, but for emptywords is the real id
                    newsent.emptywords.remove(key);
                } else {
                    to_delete_from_this.add(key);
                }
            }
            // and delete other keys from this
            for (Integer key : to_delete_from_this) {
                emptywords.remove(key);
            }
        }

        // delete MWTs if the split goes through it
        to_delete_from_this.clear();
        if (contracted != null) {
            for (Integer key : contracted.keySet()) {
                if (key < id) {
                    newsent.contracted.remove(key);
                } else {
                    to_delete_from_this.add(key);
                }
            }
            // and delete other kesy from this
            for (Integer key : to_delete_from_this) {
                contracted.remove(key);
            }
        }

        // delete heads from newsent which are before the separation id
        for (ConllWord cw : newsent.getWords()) {
            if (cw.getHead() <= id) {
                cw.setHead(0);
            }
        }
        // delete enhanced dependencies heads from newsent which are before the separation id
        for (ConllWord cw : newsent.getWords()) {
            Set<EnhancedDeps> ehd_to_delete_from_this = new HashSet<>();
            for (EnhancedDeps ehd : cw.getDeps()) {
                if (ehd.headid <= id) {
                    ehd_to_delete_from_this.add(ehd);
                }
            }
            for (EnhancedDeps ehd : ehd_to_delete_from_this) {
                cw.getDeps().remove(ehd);
            }
            // add an enhanced dep if nothing is left
            if (hasEnhancedDeps && cw.getDeps().isEmpty()) {
                cw.initialiseEHDs();
            }
        }

        if (newsent.emptywords != null) {
            for (List<ConllWord> ewids : newsent.emptywords.values()) {
                for (ConllWord ewid : ewids) {
                    Set<EnhancedDeps> ehd_to_delete_from_this = new HashSet<>();
                    for (EnhancedDeps ehd : ewid.getDeps()) {
                        if (ehd.headid <= id) {
                            ehd_to_delete_from_this.add(ehd);
                        }
                    }
                    for (EnhancedDeps ehd : ehd_to_delete_from_this) {
                        ewid.getDeps().remove(ehd);
                    }

                    // add an enhanced dep if nothing is left
                    if (hasEnhancedDeps && ewid.getDeps().isEmpty()) {
                        ewid.initialiseEHDs();
                    }

                }
            }
        }

        // delete words which are before id from newsent
        for (int x = 0; x < id; ++x) {
            newsent.getWords().remove(0);
        }

        // cut words starting with id from this
        while (words.size() > id) {
            words.remove(id);
        }

        // delete heads which are no longer in this
        for (ConllWord cw : words) {
            if (cw.getHead() > id) {
                cw.setHead(0);
            }
        }

        // delete enhanced dependencies heads from this which are no longer in this
        for (ConllWord cw : words) {
            Set<EnhancedDeps> ehd_to_delete_from_this = new HashSet<>();
            for (EnhancedDeps ehd : cw.getDeps()) {
                if (ehd.headid > id) {
                    ehd_to_delete_from_this.add(ehd);
                }
            }
            for (EnhancedDeps ehd : ehd_to_delete_from_this) {
                cw.getDeps().remove(ehd);
            }
            if (hasEnhancedDeps && cw.getDeps().isEmpty()) {
                cw.initialiseEHDs();
            }
        }

        if (emptywords != null) {
            // delete enhanced dependencies heads from this.emptywords which are no longer in this
            for (List<ConllWord> ewids : emptywords.values()) {
                for (ConllWord ewid : ewids) {
                    Set<EnhancedDeps> ehd_to_delete_from_this = new HashSet<>();
                    for (EnhancedDeps ehd : ewid.getDeps()) {
                        if (ehd.headid > id) {
                            ehd_to_delete_from_this.add(ehd);
                        }
                    }
                    for (EnhancedDeps ehd : ehd_to_delete_from_this) {
                        ewid.getDeps().remove(ehd);
                    }
                    // add an enhanced dep if nothing is left
                    if (hasEnhancedDeps && ewid.getDeps().isEmpty()) {
                        ewid.initialiseEHDs();
                    }
                }
            }
        }
        // set text of split sentences
        text = getSentence();
        newsent.normalise();
        newsent.setText(newsent.getSentence());
        is_modified = true;
        return newsent;
    }

    public void joinsentence(ConllSentence n) {
        n.normalise(words.size() + 1);
        //System.out.println("nnnnnn " + n);

        words.addAll(n.getWords());
        if (n.emptywords != null) {
            if (emptywords == null) {
                emptywords = n.emptywords;
            } else {
                emptywords.putAll(n.emptywords);
            }
        }
        if (n.contracted != null) {
            if (contracted == null) {
                contracted = n.contracted;
            } else {
                contracted.putAll(n.contracted);
            }
        }
        text = getSentence();
        is_modified = true;
    }

    /**
     * fusionner deux mots sur un (par ex. "-" et "ce"). Ne fonctionne que sur
     * les tags, pas sur les dépendances
     *
     * @param infoOfWord index du mot dont on prend les information à mettre
     * dans le ConllWord
     * @param forms liste de mots à fusionner
     */
    public void compact(int infoOfWord, String... forms) {
        for (int ix = 0; ix < words.size(); ++ix) {
            //System.out.println("AAA " +  words.get(ix));
            boolean listMatches = true;
            int delta = 0;

            for (String form : forms) {
                //System.err.println(" form " + form + " ix: " + ix + " delta:" + delta + " ww:" + words.size());
                ConllWord word = words.get(ix + delta);

                if (!word.hasForm(form) || word.getHead() == -1) { // head -1 == mot déjà à supprimer
                    // le mot de la phrase n'est pas égal au premier mot de notre liste
                    listMatches = false;
                    break;
                }
                delta++;
                if (ix + delta >= words.size()) {
                    listMatches = false;
                    break;
                } // si par exemple la premiere form de forms est la dernière forme de la phrase
            }
            if (listMatches) {
                //System.out.println("CAN FUSION " + forms);
                // nouvelle forme
                String newform = "";
                for (String form : forms) {
                    newform += form;
                }
                String newlemma = "";

                // mettre tous les head à -1
                for (int d = 0; d < forms.length; ++d) {
                    ConllWord w = words.get(ix + d);
                    w.setHead(-1);
                    newlemma += w.getLemma();
                }
                ConllWord modified = words.get(ix);
                ConllWord keepInfo = words.get(ix + infoOfWord);
                modified.setForm(newform);
                modified.setLemma(newlemma);
                modified.setXpostag(keepInfo.getXpostag());
                modified.setUpostag(keepInfo.getUpostag());
                modified.setFeatures(keepInfo.getFeatures());
                modified.setDeplabel(keepInfo.getDeplabel());
                modified.setDeps(keepInfo.getDeps());
                modified.setMisc(keepInfo.getMisc());
                //modified.setPrefixed(keepInfo.getPrefixed());
                modified.setHead(0);
            }
        }

        // supprimer les mots devenus inutiles
        deleteUnusedWords();
        is_modified = true;
    }

    public void deleteUnusedWords() {
        // supprimer les mots devenus inutiles
        Iterator<ConllWord> it = words.iterator();
        while (it.hasNext()) {
            ConllWord w = it.next();
            if (w.getHead() == -1) {
                w.setMysentence(null);
                it.remove();
            }
        }
        is_modified = true;
    }

    /**
     * format the sentence in CoNLL-U format
     * @return sentence in CoNLL-U format
     */
    @Override
    public String toString() {
        return toString(true);
    }

    /*
     @param strict: if false allow a token with head 0 have a deprel different than "root"
    */
    public String toString(boolean strict) {
        StringBuilder sb = new StringBuilder();
        if (newdoc != null) {
            if (!newdoc.isEmpty()) {
                sb.append("# newdoc id = ").append(newdoc).append('\n');
            } else {
                sb.append("# newdoc\n");
            }
        }
        if (newpar != null) {
            if (!newpar.isEmpty()) {
                sb.append("# newpar id = ").append(newpar).append('\n');
            } else {
                sb.append("# newpar\n");
            }
        }
        if (sentid != null && !sentid.isEmpty()) {
            sb.append("# sent_id = ").append(sentid).append('\n');
        }
        if (getText() != null) {
            sb.append("# text = ").append(getText().replaceAll("\n", " ").trim()).append('\n');
        } else {
            // do not add "# text = ..." if absent in file and not explicitely added by user
            //sb.append("# text = ").append(getSentence().replaceAll("\n", " ").trim()).append('\n');
        }

        if (translit != null && !translit.isEmpty()) {
            sb.append("# translit = ").append(translit).append('\n');
        }

        if (translations != null) {
            for (String lg : translations.keySet()) {
                sb.append("# text_").append(lg).append(" = ").append(translations.get(lg)).append('\n');
            }
        }

        Set<String> highlighttokens = new LinkedHashSet<>();
        Set<String> highlightdeprels = new LinkedHashSet<>();
        for (ConllWord cw : words) {
            if (cw.getCheckToken()) {
                highlighttokens.add(cw.getFullId());
            }
            if (cw.getcheckDeprel()) {
                highlightdeprels.add(cw.getFullId());
            }
        }
        if (contracted != null) {
            for (ConllWord cw : contracted.values()) {
                if (cw.getCheckToken()) {
                    highlighttokens.add(cw.getFullId());
                }
                if (cw.getcheckDeprel()) {
                    highlightdeprels.add(cw.getFullId());
                }
            }
        }
        if (emptywords != null) {
            for (List<ConllWord> cws : emptywords.values()) {
                for (ConllWord cw : cws) {
                    if (cw.getCheckToken()) {
                        highlighttokens.add(cw.getFullId());
                    }
                    if (cw.getcheckDeprel()) {
                        highlightdeprels.add(cw.getFullId());
                    }
                }
            }
        }
        if (!highlightdeprels.isEmpty()) {
            sb.append("# highlight deprels =");
            for (String i : highlightdeprels) {
                sb.append(" ").append(i);
            }
            sb.append('\n');
        }

        if (!highlighttokens.isEmpty()) {
            sb.append("# highlight tokens =");
            for (String i : highlighttokens) {
                sb.append(" ").append(i);
            }
            sb.append('\n');
        }


        for (String c : comments) {
            sb.append("# ").append(c).append('\n');
        }
        // output 0.1 empty words, if present
        if (emptywords != null) {
            List<ConllWord> ews = emptywords.get(0);
            if (ews != null) {
                for (ConllWord ew : ews) {
                    sb.append(ew.toString(hasEnhancedDeps)).append("\n");
                }
            }
        }

        for (ConllWord word : words) {
//            if (nextToStringcomplete) {
//                word.nextToStringComplete();
//            }

            // output eventual contracted form
            if (contracted != null) {
                ConllWord cc = contracted.get(word.getId());
                if (cc != null) {
                    sb.append(cc.toString()).append('\n');
                }
            }

            sb.append(word.toString(hasEnhancedDeps, strict)).append("\n");
            if (emptywords != null) {
                List<ConllWord> ews = emptywords.get(word.getId());
                if (ews != null) {
                    for (ConllWord ew : ews) {
                        sb.append(ew.toString(hasEnhancedDeps/* withpartialhead*/)).append("\n");
                    }
                }
            }
        }
        //nextToStringcomplete = false;
        sb.append('\n');
        return sb.toString();
    }

    /**
     * format the sentence in SD-parse format
     * @return sentence in SD-parse format
     */
    public String getSDparse() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ConllWord word : words) {
            if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            sb.append(word.getForm());
        }
        sb.append('\n');

        for (ConllWord word : words) {
            if (word.getHead() == 0) {
            } else {
                sb.append(word.getDeplabel())
                        .append("(")
                        .append(word.getHeadWord().getForm())
                        .append(", ")
                        .append(word.getForm())
                        .append(")\n");
            }
        }
        return sb.toString();
    }

    /**
     * format the sentence for use with xelatex (tikz-dependencie)
     * @param all_enhanced if true, show all enhanced dependencies
     * @return sentence in LaTeX format
     */
    public String getLaTeX(boolean all_enhanced) {
        StringBuilder sb = new StringBuilder();
        try {
            makeTrees(null);

            Map<String, Integer> position = new HashMap<>(); // position of each word, needed for latex-deprel
            sb.append("%% for tikz-dependency\n%% use following packages:\n");
            sb.append("%% \\usepackage{tikz}\n");
            sb.append("%% \\usepackage{tikz-dependency}\n");

            if (newdoc != null) {
                sb.append("% newdoc ").append(newdoc).append('\n');
            }
            if (newpar != null) {
                sb.append("% newpar ").append(newpar).append('\n');
            }
            if (sentid != null && !sentid.isEmpty()) {
                sb.append("% sent_id = ").append(sentid).append('\n');
            }
            sb.append("\\begin{dependency}\n\\begin{deptext}\n");

            StringBuilder forms = new StringBuilder("%% forms:\n");
            StringBuilder lemmas = new StringBuilder("%% lemmas:\n% ");
            StringBuilder uposs = new StringBuilder("%% UPOS:\n% ");
            StringBuilder xposs = new StringBuilder("%% XPOS:\n% ");
            StringBuilder ids = new StringBuilder("%% IDs:\n% ");
            StringBuilder positions = new StringBuilder("%% Position in sentence:\n% ");

            // find the extracolumns used by any of the words
            Set<String> extracols = new LinkedHashSet<>();
            for (ConllWord word : words) {
                if (word.getExtracolumns() != null) {
                    extracols.addAll(word.getExtracolumns().keySet());
                }
            }

            // stringbuilders for all needed extra columns
            Map<String, StringBuilder> ecs = new LinkedHashMap<>();
            for (String ec : extracols) {
                StringBuilder extra = new StringBuilder(String.format("%%%% extra column %s:\n%% ", ec));
                ecs.put(ec, extra);
            }

            boolean first = true;
            if (emptywords != null) {
                // sentence initial emptry words
                List<ConllWord> ews = emptywords.get(0);
                if (ews != null) {
                    if (first) {
                        first = false;
                    } else {
                        forms.append("\\& ");
                        lemmas.append("\\& ");
                        uposs.append("\\& ");
                        xposs.append("\\& ");
                        ids.append("\\& ");
                        positions.append("\\& ");
                    }
                    for (ConllWord ew : ews) {
                        forms.append(ew.getForm()).append("\t");
                        lemmas.append(ew.getLemma()).append("\t");
                        uposs.append(ew.getUpostag()).append("\t");
                        xposs.append(ew.getXpostag()).append("\t");
                        ids.append(ew.getFullId()).append("\t");
                        position.put(ew.getFullId(), position.size() + 1);
                        positions.append(position.size()).append("\t");
                    }
                }
            }

            // all words, including empty words following a regular word
            for (ConllWord word : words) {
                if (first) {
                    first = false;
                } else {
                    forms.append("\\& ");
                    lemmas.append("\\& ");
                    uposs.append("\\& ");
                    xposs.append("\\& ");
                    ids.append("\\& ");
                    positions.append("\\& ");
                    for (String ec : extracols) {
                        StringBuilder ecsb = ecs.get(ec);
                        ecsb.append("\t\\&\n% ");
                    }
                }

                forms.append(word.getForm()).append("\t");
                lemmas.append(word.getLemma()).append("\t");
                uposs.append(word.getUpostag()).append("\t");
                xposs.append(word.getXpostag()).append("\t");
                ids.append(word.getFullId()).append("\t");
                position.put(word.getFullId(), position.size() + 1);
                positions.append(position.size()).append("\t");

                for (String ec : extracols) {
                    LinkedHashSet<String> tmp = null;
                    if (word.getExtracolumns() != null) {
                        tmp = word.getExtracolumns().get(ec);
                    }
                    StringBuilder ecsb = ecs.get(ec);
                    //ecsb.append("\t\\&\n");
                    if (tmp != null && !tmp.isEmpty()) {
                        ecsb.append(String.join(",", word.getExtracolumns().get(ec)));
                    }
                }

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            forms.append("\\& ").append(ew.getForm()).append("\t");
                            lemmas.append("\\& ").append(ew.getLemma()).append("\t");
                            uposs.append("\\& ").append(ew.getUpostag()).append("\t");
                            xposs.append("\\& ").append(ew.getXpostag()).append("\t");
                            ids.append("\\& ").append(ew.getFullId()).append("\t");
                            position.put(ew.getFullId(), position.size() + 1);
                            positions.append("\\& ").append(position.size()).append("\t");

                            if (ew.getExtracolumns() != null) {
                                for (String ec : extracols) {
                                    //LinkedHashSet<String> tmp = word.getExtracolumns().get(ec);
                                    StringBuilder ecsb = ecs.get(ec);
                                    ecsb.append("\\& ");
                                    if (!word.getExtracolumns().get(ec).isEmpty()) {
                                        sb.append(String.join(",", word.getExtracolumns().get(ec)));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // add lines to main StringBuilder
            sb.append(forms).append("\\\\\n");
            sb.append(lemmas).append("\\\\\n");
            sb.append(uposs).append("\\\\\n");
            sb.append(xposs).append("\\\\\n");
            sb.append(ids).append("\\\\\n");
            sb.append(positions).append("\\\\\n");
            for (String ec : extracols) {
                sb.append(ecs.get(ec)).append("\\\\\n");
            }

            sb.append("\\end{deptext}\n\n%        head dependent deprel\n");

            maxdist = 0; // calculate here the most distant word in terms of deprels from root
            //System.err.println("ppppppppppp " + position);
            for (ConllWord word : words) {
                maxdist = Math.max(getDistanceFromSentenceHead(word), maxdist);
                int mypos = position.get(word.getFullId());
                int headpos = 0;
                if (word.getHead() == 0) {
                    sb.append("\\deproot[edge unit distance=4ex]{").append(mypos).append("}{root}%%\n");
                } else if (word.getHead() > 0) {
                    headpos = position.get(word.getHeadWord().getFullId());
                    sb.append("\\depedge{").append(headpos).append("}{")
                            .append(mypos).append("}{")
                            .append(word.getDeplabel()).append("}\n");
                }

                // adding enhanced deps
                for (ConllWord.EnhancedDeps ed : word.getDeps()) {
                    // eds can have headid 0 if they are sentence initial
                    if (headpos != 0 /*ed.headid != 0*/) {
                        if (!position.containsKey(ed.getFullHeadId())) {
                            System.err.println("Invalid empty word head " + ed.getFullHeadId() + " " + ed);
                        } else {
                            int ewheadpos = position.get(ed.getFullHeadId());
                            if (all_enhanced || ewheadpos != headpos) {
                                sb.append("\n\\depedge[edge below]{").append(ewheadpos).append("}{")
                                        .append(mypos).append("}{")
                                        .append(ed.deprel).append("}");
                                if (ewheadpos == headpos) {
                                    sb.append(" % enhanced == basic\n");
                                } else {
                                    sb.append("\n");
                                }
                            }
                        }
                    }
                }

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            int ewpos = position.get(ew.getFullId());
                            for (ConllWord.EnhancedDeps ehd : ew.getDeps()) {
                                int ewheadpos = position.get(ehd.getFullHeadId());
                                sb.append("\\depedge[edge below]{").append(ewheadpos).append("}{")
                                        .append(ewpos).append("}{")
                                        .append(ehd.deprel).append("}\n");
                            }
                        }
                    }
                }
            }

            if (contracted != null) {
                for (ConllWord cc : contracted.values()) {
                    sb.append("\\wordgroup{1}{")
                            .append(position.get(Integer.toString(cc.getId()))).append("}{")
                            .append(position.get(Integer.toString(cc.getSubid()))).append("}{mw")
                            .append(cc.getId()).append("}\n"); // id
                }
            }

            sb.append("\\end{dependency}\n");

            sb.append("\n\n\n%% for deptrees.sty\n");
            sb.append("%% \\usepackage{deptrees}\n");
            sb.append(String.format("\\setbottom{%d} %% set to 0 to hide bottom line of forms\n", maxdist + 1));
            sb.append("\\begin{tikzpicture}[x=15mm,y=20mm]\n");
            for (ConllWord chead : getHeads()) {
                sb.append(String.format("\\root{%d}{%s}{%s}\n", chead.getId(), chead.getForm(), chead.getUpostag()));
            }

            sb.append("% headpos, hor-pos, vert-pos, form, UPOS, deprel\n");
            List<ConllWord> words2 = new ArrayList<>();
            words2.addAll(words);
            Collections.sort(words2, new CWSortbyHead());
            for (ConllWord cw : words2) {
                if (cw.getHead() != 0) {
                    sb.append(String.format("\\dep{%d}{%s}{%d}{%s}{%s}{%s}\n", cw.getHead(), cw.getId(),
                            getDistanceFromSentenceHead(cw) - 1,
                            cw.getForm(), cw.getUpostag(), cw.getDeplabel()));
                }
            }

            if (contracted != null) {
                for (ConllWord cc : contracted.values()) {
                    sb.append("\\mwt{")
                            .append(position.get(Integer.toString(cc.getId()))).append("}{")
                            .append(position.get(Integer.toString(cc.getSubid()))).append("}{")
                            .append(cc.getForm()).append("}\n");
                }
            }

            sb.append("\\end{tikzpicture}\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public List<ConllWord> getWords() {
        return words;
    }

    public ConllWord getContracted(int id) {
        if (contracted != null) {
            return contracted.get(id);
        } else {
            return null;
        }
    }

    /**
     * get all words, including empty words in a list (mainly for searching
     * consecutive words)
     *
     * @return a (copied) list of all Words and EmptyWords
     */
    public List<ConllWord> getAllWords() {
        List<ConllWord> allwords = new ArrayList<>();
        List<ConllWord> current = getEmptyWords(0);
        if (current != null) {
            allwords.addAll(current);
        }
        for (ConllWord cw : words) {
            allwords.add(cw); // get normal word
            // get eventual empty word after cw
            current = getEmptyWords(cw.getId());
            if (current != null) {
                allwords.addAll(current);
            }
        }
        return allwords;
    }

    public Map<Integer, List<ConllWord>> getEmptyWords() {
        return emptywords;
    }

    /**
     * get an empty word (n.m). If it does not exist return null
     *
     * @param id the n.m CoNLL-U id
     * @return the word nor null
     */
    public ConllWord getEmptyWord(String id) {
        String[] elems = id.split("\\.");
        if (elems.length != 2) {
            return null;
        }
        List<ConllWord> ews = emptywords.get(Integer.parseInt(elems[0]));
        if (ews.isEmpty()) {
            return null;
        }
        int subid = Integer.parseInt(elems[1]);
        if (subid <= ews.size()) {
            return ews.get(subid - 1);
        }
        return null;
    }

    /**
     * get an empty word (n.m). If it does not exist return null
     *
     * @param id the n.m CoNLL-U id (m.)
     * @param subid the n.m CoNLL-U subid (.n)
     * @return the word nor null
     */
    public ConllWord getEmptyWord(int id, int subid) {
        List<ConllWord> ews = emptywords.get(id);
        if (ews == null || ews.isEmpty()) {
            return null;
        }
        if (subid <= ews.size()) {
            return ews.get(subid - 1);
        }
        return null;
    }

    /**
     * get all empty word of an id (id.m). If it does not exist return null
     *
     * @param id the n.m CoNLL-U id (m.)
     * @return the word nor null
     */
    public List<ConllWord> getEmptyWords(int id) {
        if (emptywords == null) {
            return null;
        }
        List<ConllWord> ews = emptywords.get(id);
        if (ews == null || ews.isEmpty()) {
            return null;
        }
        return ews;
    }

    public int numOfEmptyWords() {
        if (emptywords == null) {
            return 0;
        } else {
            int ew = 0;
            for (List<ConllWord> lcw : emptywords.values()) {
                ew += lcw.size();
            }
            return ew;
        }
    }

    /**
     * return a word or null. This method returns normal word, a contracted word
     * or an empty word
     * @param id id of the word
     * @return the ConllWord instance
     */
    public ConllWord getWord(String id) {
        if (id.contains(".")) {
            return getEmptyWord(id);
        } else if (id.contains("-")) {
            String[] elems = id.split("-");
            return getContracted(Integer.parseInt(elems[0]));
        } else {
            int iid = Integer.parseInt(id);
            if (iid > 0 && iid <= words.size()) {
                return words.get(iid - 1);
            }
        }
        return null;
    }

    /**
     * return word with ident i. No empty or contracted word
     * @param i the position in the sentence
     * @return ConllWord instance
     */
    public ConllWord getWord(int i) {
        return words.get(i - 1);
    }

    private static final int factor = 10; // needed to create space for inserting a new word

    /**
     * add a new word after the word with id (0 inserts at the beginning).
     * invalidates word2chunks and chunk2words
     *
     * @param cw
     * @param id
     */
    public void addWord(ConllWord cw, int id) throws ConllException {
        cw.setMysentence(this);
        is_modified = true;
        if (cw.getTokentype() == ConllWord.Tokentype.CONTRACTED) {
            if (contracted == null) {
                contracted = new HashMap<>();
            }
            contracted.put(cw.getId(), cw);
            return;
        }

        // updating ids of normal words (including ehnanced deps)
        for (ConllWord w : words) {
            w.setId(w.getId() * factor);
            if (w.getHead() > 0) {
                w.setHead(w.getHead() * factor);
            }
            if (w.getDeps() != null) {
                for (ConllWord.EnhancedDeps ed : w.getDeps()) {
                    ed.headid *= factor;
                }
            }
        }

        // update ids of contracted words
        if (contracted != null) {
            Map<Integer, ConllWord> c2 = new HashMap<>();
            for (Integer id2 : contracted.keySet()) {
                ConllWord mwt = contracted.get(id2);
                mwt.setId(id2 * factor);
                mwt.setSubId(mwt.getSubid() * factor);
                c2.put(id2 * factor, mwt);
            }
            contracted = c2;
        }

        // update ids of empty words
        // updating ids of normal words (including enhanced deps)
        if (emptywords != null) {
            //Map<Integer, List<ConllWord>> ews2 = new HashMap<>();
            for (Integer id2 : emptywords.keySet()) {
                List<ConllWord> ewl = new ArrayList<>();
                for (ConllWord ew : emptywords.get(id2)) {
                    ew.setId(ew.getId() * factor);
                    if (ew.getHead() > 0) {
                        ew.setHead(ew.getHead() * factor);
                    }
                    if (ew.getDeps() != null) {
                        for (ConllWord.EnhancedDeps ed : ew.getDeps()) {
                            ed.headid *= factor;
                        }
                    }
                    ewl.add(ew);
                }
                //ews2.put(id2 * factor, ewl);
            }
        }
        // insert new sord
        words.add(id, cw);
        cw.setId(id * factor + 1);
        if (cw.getHead() > 0) {
            cw.setHead(cw.getHead() * factor);
        }
        normalise(1);
        makeTrees(null);
    }

    public void deleteContracted(int id) throws ConllException {
        is_modified = true;
        if (contracted != null) {
            ConllWord removed = contracted.remove(id);
            removed.setMysentence(null);
            return;
        }
        throw new ConllException("No composed form with id " + id);
    }

    public Map<Integer, ConllWord> getContractedWords() {
        return contracted;
    }

    /**
     * delete empty word
     *
     * @param id (1 .. lastWord)
     * @param subid (1.. n)
     * @return true if deletion worked
     * @throws com.orange.labs.conllparser.ConllException
     */
    public boolean deleteEmptyWord(int id, int subid) throws ConllException {
        List<ConllWord> ews = this.getEmptyWords(id);
        if (ews == null) {
            return false;
        }
        if (ews.size() < subid) {
            return false;
        }

        ConllWord removed = ews.remove(subid - 1);
        removed.setMysentence(null);

        if (ews.isEmpty()) {
            emptywords.remove(id);
        } else {
            // renumber remaining empty words
            int ct = 1;
            for (ConllWord ew : ews) {
                ew.setSubId(ct++);
            }
        }
        normalise(1);
        makeTrees(null);
        is_modified = true;
        return true;
    }

    /**
     * returns true, if the word is part of a MWT
     * @param id of words we want to cgeck whether it is part of a MWT
     * @return true if this is the case
     */
    public boolean isPartOfMWT(int id) {
        if (contracted == null) {
            return false;
        }
        for (ConllWord mwt : contracted.values()) {
            if (id >= mwt.getId() && id <= mwt.getSubid()) {
                return true;
            }
        }
        return false;
    }

    public void deleteWord(int id) throws ConllException {
        makeTrees(null);
        if (id < words.size()) {
            // make all dependants of word to be removed root
            for (ConllWord cw : words.get(id - 1).getDependents()) {
                cw.setHead(0);
            }

            // delete MWT of which removed word is part
            if (contracted != null) {
                Set<ConllWord> mwts = new HashSet<>();
                for (ConllWord mwt : contracted.values()) {
                    if (mwt.getId() == id || mwt.getSubid() == id) {
                        mwts.add(mwt);
                    }
                }

                for (ConllWord mwt : mwts) {
                    ConllWord removed = contracted.remove(mwt.getId());
                    removed.setMysentence(null);
                }
            }

            // atach eventual existing empty word to next word, or (if we delete the last word to preceding word
            //System.err.println("QSQSQSQS " + emptywords);
            if (emptywords != null) {
                List<ConllWord> ews = emptywords.get(id);
                //System.err.println("GGGGGG " + ews);
                if (ews != null) {
                    // found some empty words
                    // check whether following word has emptywords
                    List<ConllWord> ewsnext = emptywords.get(id + 1);
                    if (ewsnext == null) {
                        // no, put current ones to following word
                        emptywords.put(id, ews);
                        for (ConllWord cw : ews) {
                            cw.setId(id + 1);
                        }

                    } else {
                        // yes add current ones to following word
                        ewsnext.addAll(ews);
                        int ct = 1;
                        for (ConllWord cw : ewsnext) {
                            cw.setSubId(ct++);
                            cw.setId(id + 1);
                        }
                    }
                }
            }

            // delete enhanced dependencies to removed word
            for (ConllWord cw : words) {
                List<EnhancedDeps> ehds = cw.getDeps();
                if (ehds != null) {
                    List<EnhancedDeps> toremove = new ArrayList<>();
                    for (EnhancedDeps eh : ehds) {
                        if (eh.headid == id) {
                            toremove.add(eh);
                        }
                    }
                    //System.err.println("DELETE EHDS " + toremove);
                    for (EnhancedDeps eh : toremove) {
                        ehds.remove(eh);
                    }
                }
            }

            ConllWord removed = words.remove(id - 1);
            removed.setMysentence(null);
            normalise(1);
            makeTrees(null);
            is_modified = true;
        }
    }

    /**
     * join word with following.Use the attachment of the one closer to the
 head.if both are equally close, chose the left
     * @param id id of work to be joint with following
     * @throws ConllException
     */
    public void joinWords(int id) throws ConllException {
        if (id < words.size()) {
            ConllWord current = words.get(id - 1);
            ConllWord other = words.get(id);

            // get all first and last tokens of MWT
            // we delete a MWT if the joined words are at hte border or overlapping with the MWT
            if (contracted != null) {
                Set<ConllWord> mwts = new HashSet<>();
                for (ConllWord mwt : contracted.values()) {
                    if (mwt.getId() == id || mwt.getId() == id + 1
                            || mwt.getSubid() == id || mwt.getSubid() == id + 1) {
                        mwts.add(mwt);
                    }
                }

                for (ConllWord mwt : mwts) {
                    contracted.remove(mwt.getId());
                }
            }

            //System.err.println("THIS  " + current + ": " + getDistanceFromSentenceHead(current));
            //System.err.println("OTHER " + other + ": " + getDistanceFromSentenceHead(other));
            if (getDistanceFromSentenceHead(current) > getDistanceFromSentenceHead(other)) {
                // current word further down than following
                other.setForm(current.getForm() + other.getForm());
                other.setLemma(current.getLemma() + other.getLemma());

                // all children of current must become children of other
                for (ConllWord deps : current.getDependents()) {
                    deps.setHead(other.getId());
                }
                //System.err.println("CURRENT FURTHER DOWN, deleting " + current);
                words.remove(current);
            } else {
                // current word further up than following
                current.setForm(current.getForm() + other.getForm());
                current.setLemma(current.getLemma() + other.getLemma());

                // all children of other must become children of current
                for (ConllWord deps : other.getDependents()) {
                    deps.setHead(current.getId());
                }
                //System.err.println("OTHER FURTHER DOWN, deleting " + other);
                words.remove(other);
            }

            normalise(1);
            makeTrees(null);
            is_modified = true;
        }
    }

    private int getDistanceFromSentenceHead(ConllWord cw) {
        if (cw.getHead() == 0) {
            return 0;
        }
        ConllWord chead = cw;
        int d = 1;
        while (chead.getHead() != 0) {
            chead = words.get(chead.getHead() - 1);
            d++;
        }
        return d;
    }

    /**
     * creates the tree structure. In case of an error a ConllException is
     * thrown.
     *
     * @param debug
     * @throws ConllException
     */
    public void makeTrees(StringBuilder debug) throws ConllException {
        //parcourir les mots jusqu'à un root,
        // chercher les autres feuilles qui dépendent de cette racine
        // extraire cet arbre partiel
        //PrintStream out = System.out;
        Map<String, ConllWord> table = new HashMap<>(); // All nodes (including empty nodes) and their stringified id
        //StringBuilder errs = new StringBuilder();
        headss = new ArrayList<>();
//        try {
//            out = new PrintStream(System.out, true, "UTF-8");
//        } catch (UnsupportedEncodingException ex) {
//        }
        head = null;
        List<ConllWord> tempheads = new ArrayList<>();
        // clean all old dependents from all words
        for (ConllWord w : words) {
            if (w.getHead() == 0) {
                tempheads.add(w);
            }
            w.getDependents().clear();
            w.getDependentsMap().clear();
        }

        int position = 1;

        // sentence initial empty word (0.1)
        if (emptywords != null) {
            List<ConllWord> ews = emptywords.get(0);
            if (ews != null) {
                for (ConllWord ew : ews) {
                    ew.setPosition(position++);
                    //sb.append(ew.toString(withpartialhead)).append("\n");
                    headss.add(ew);
                    table.put(ew.getFullId(), ew);
                }
            }
        }

        for (ConllWord w : words) {
            table.put(w.getFullId(), w);
            w.setPosition(position++);
            if (w.getHead() > 0) {
                // mettre W dans la liste des dépendants de sa tête
                if (w.getHead() > words.size()) {
                    //String si = sentid;
//                    if (si == null) {
//                        si = "";
//                    }
                    //errs.append("head id is greater than sentence length: " + w.getHead() + " > " + words.size() + ". forced to first word");
                    //w.setHead(0);
                    throw new ConllException(//sentid +
                            "Token " + w.getId() + ": head id is greater than sentence length: " + w.getHead() + " > " + words.size());
                }

                int dist = Math.abs(w.getId() - w.getHead());
                maxdist = dist > maxdist ? dist : maxdist;
                ConllWord lhead = words.get(w.getHead() - 1);
                w.setHeadWord(lhead);
                lhead.getDependents().add(w);
                lhead.getDependentsMap().put(w.getId(), w);
            } else {
                if (head == null) { // the first found is the head of the sentence
                    head = w;
                }
                headss.add(w);
            }

            if (emptywords != null) {
                List<ConllWord> ews = emptywords.get(w.getId());
                if (ews != null) {
                    for (ConllWord ew : ews) {
                        ew.setPosition(position++);
                        //sb.append(ew.toString(withpartialhead)).append("\n");
                        headss.add(ew);
                        table.put(ew.getFullId(), ew);
                    }
                }
            }
        }

        if (debug != null) {
            for (ConllWord lhead : tempheads) {
                lhead.printDeps(debug, "");
            }
        }

        /**
         * add ConllWords references to enhanded deps
         */
        for (ConllWord w : words) {
            if (w.getDeps() != null) {
                for (ConllWord.EnhancedDeps ed : w.getDeps()) {
                    ed.headword = table.get(ed.getFullHeadId());
                }
            }
        }

        if (emptywords != null) {
            for (List<ConllWord> ews : emptywords.values()) {
                for (ConllWord ew : ews) {
                    for (ConllWord.EnhancedDeps ed : ew.getDeps()) {
                        ed.headword = table.get(ed.getFullHeadId());
                    }
                }
            }
        }

        if (head == null) {
            //head = words.get(0);
            //headss.add(head);
            //errs.append("no word with head == 0. Forced Node 1 to be root\n");
            throw new ConllException(//sentid +
                    "no word with head == 0");
        }

        // check for cycles
        for (ConllWord cw : words) {
            Set<ConllWord> passednodes = new HashSet<>();
            passednodes.add(cw);
            cw.checkCycles(passednodes);
        }
        //return errs.toString();
    }

    /**
     * calculate the Labelled Attachment Score and other metrics.Does not take
 into accound empty words
     * @param gold gold sentence to be evaluated against
     * @param scoretype Metric to use
     * @return score
     *
     */
    public double score(ConllSentence gold, Scoretype scoretype) {
        if (gold.size() != this.size()) {
            return 0.0;
        }
        double score = 0.0;
        for (int ix = 0; ix < this.size(); ++ix) {
            ConllWord goldw = gold.getWord(ix + 1);
            ConllWord sysw = this.getWord(ix + 1);
            switch (scoretype) {
                case UPOS:
                    if (goldw.getUpostag().equals(sysw.getUpostag())) {
                        score++;
                    }
                    break;
                case XPOS:
                    if (goldw.getXpostag().equals(sysw.getXpostag())) {
                        score++;
                    }
                    break;
                case LEMMA:
                    if (goldw.getLemma().equals(sysw.getLemma())) {
                        score++;
                    }
                    break;
                case LAS:
                    if (goldw.getHead() == sysw.getHead() && goldw.getDeplabel().equals(sysw.getDeplabel())) {
                        score++;
                    }
                    break;
                //case CLAS:
                //    if (goldw.getHead() == sysw.getHead()  &&  goldw.getDeplabel().equals(sysw.getDeplabel())) score ++;
                //    break;
                case FEATS:
                    if (goldw.getFeaturesStr().equals(sysw.getFeaturesStr())) {
                        score++;
                    }
            }
        }
        return score / gold.size();
    }

    /**
     * calculate the height if each arc, by taking into account all short arcs
     * below. Does not take into account non-projective arcs, only arcs from n
     * to m
     * @return hight of acrs for each word
     *
     */
    public Map<Integer, Integer> calculate_flat_arcs_height() {
        // min heights of deps
        Map<Integer, Integer> minheightsLeft = new HashMap<>(); // head: highest arc to the left
        Map<Integer, Integer> minheightsRight = new HashMap<>(); // head: highest arc to the right
        // height for my arc
        Map<Integer, Integer> height = new HashMap<>(); // position: height

//        System.err.println("A maxdist " + cs.getMaxdist());
        for (int d = 1; d <= this.getMaxdist(); ++d) {
//            System.err.println("D: " + d);
//            System.err.println(" LEFT  " + minheightsLeft);
//            System.err.println(" RIGHT " + minheightsRight);
//            System.err.println(" H     " + height);
            for (ConllWord cw : this.getWords()) {
                if (cw.getHead() <= 0) {
                    continue;
                }

                int dist = cw.getId() - cw.getHead(); // negative: head is right
                if (Math.abs(dist) != d) {
                    continue;
                }
//                System.err.println(" cw: " + cw.getId() + " dist " + dist);
                if (dist < 0) {
                    // head is right, so we look at deps following (left of) the head
                    Integer mh = minheightsLeft.getOrDefault(cw.getHead(), 0);
                    Integer m = height.getOrDefault(cw.getId(), 0);
//                    System.err.println("  minh l for cw " + mh + " " + m);

                    int h = Math.min(Math.max(mh + 1, m + 1), d);

//                    System.err.println("   l id:" + cw.getId() + " h:" + cw.getHead());
//                    System.err.println("   lheight " + h);
                    for (int i = cw.getId(); i < cw.getHead(); ++i) {
                        h = Math.max(h, height.getOrDefault(i, 0) + 1);
//                        System.err.println("  li:" + i + " " + h);
                    }
//                    System.err.println("  height for cw " + h);
                    height.put(cw.getId(), h);
                    minheightsLeft.put(cw.getHead(), h);

                } else {
                    Integer mh = minheightsRight.getOrDefault(cw.getHead(), 0);
                    Integer m = height.getOrDefault(cw.getId(), 0);
//                    System.err.println("  rminh for cw " + mh + " " + m);

                    int h = Math.min(Math.max(mh + 1, m + 1), d);

//                    System.err.println("   r id:" + cw.getId() + " h:" + cw.getHead());
//                    System.err.println("   rheight " + h);
                    for (int i = cw.getId(); i > cw.getHead(); --i) {
                        h = Math.max(h, height.getOrDefault(i, 0) + 1);
//                        System.err.println("  ri:" + i + " " + h);
                    }

                    height.put(cw.getId(), h);
//                    System.err.println("  rheight for cw " + h);
                    minheightsRight.put(cw.getHead(), h);
                }
            }
        }
//        System.err.println("LEFT  " + minheightsLeft);
//        System.err.println("RIGHT " + minheightsRight);
//        System.err.println("H     " + height);
        return height;
    }

    public int getMaxdist() {
        return maxdist;
    }

    class CWSortbyHead implements Comparator<ConllWord> {
        // Used for sorting in ascending order of
        // roll number

        @Override
        public int compare(ConllWord a, ConllWord b) {
            return a.getHead() - b.getHead();
        }
    }

    /**
     * class to store information of what parts of a ConllWord should be
     * highlighted. Add this information in the jsonTree
     */
    public static class Highlight {

        //public ConllWord.Fields field;
        //public Set<Integer> ids;
        public Map<Integer, ConllWord.Fields> idshl; // wordid: fields to highlight

        // highlight a single word on field
        public Highlight(ConllWord.Fields field, int wordid) {
            //this.field = field;
            //ids = new HashSet<>();
            //ids.add(wordid);
            idshl = new HashMap<>();
            idshl.put(wordid, field);
        }

        // highlight all words from wordid to lastwordid
        public Highlight(ConllWord.Fields field, int wordid, int lastwordid) {
            //this.field = field;
            //ids = new HashSet<>();
            idshl = new HashMap<>();
            for (int id = wordid; id <= lastwordid; id++) {
                //ids.add(id);
                idshl.put(id, field);
            }
        }

        // highlight a set of words on field
        public Highlight(ConllWord.Fields field, Set<Integer> ids) {
            //this.field = field;
            //this.ids = ids;
            idshl = new HashMap<>();
            for (Integer id : ids) {
                //ids.add(id);
                idshl.put(id, field);
            }
        }

        // highlight a set of words on different fields
        public Highlight(List<ConllWord.Fields> fields, int wordid, int lastwordid) {
            //this.field = field;
            //this.ids = ids;
            idshl = new HashMap<>();
            int ct = 0;
            for (int id = wordid; id <= lastwordid; ++id, ++ct) {
                idshl.put(id, fields.get(ct));
            }
        }

    }

    public static class AnnotationErrors {

        public int xpos; // invalid xpos
        public int upos;
        public int deprel;
        public int features;

        public AnnotationErrors() {
        }
    }

    /**
     * produire un arbre en Json, returned to GUI. Nécessite l'appel a makeTrees()
     *
     * @param validupos
     * @param validxpos
     * @param validdeprels
     * @param validfeats
     * @param highlight
     * @param ae
     * @return
     */
    public JsonArray toJsonTree(Set<String> validupos, Set<String> validxpos, Set<String> validdeprels,
            ValidFeatures validfeats,
            Highlight highlight, AnnotationErrors ae) {
        JsonArray jheads = new JsonArray();
        for (ConllWord chead : headss) {
            //if (head.getTokentype() != ConllWord.Tokentype.WORD) continue;
            JsonObject jhead = chead.toJson(validupos, validxpos, validdeprels, validfeats, highlight, ae, contracted); //conllWord2json(head);
            jhead.addProperty("indexshift", words.get(0).getId() - 1); // nécessaire s'il y a plusieurs arbres dans la phrase
            jheads.add(jhead);
        }
        return jheads;
    }

    /**
     * json in spacy's format. @see https://spacy.io/api/annotation
     * @return array with tree in spacy-like json
     */
    public JsonArray toSpacyJson() {
        JsonArray jdocs = new JsonArray();
        JsonObject jdoc = new JsonObject();
        jdocs.add(jdoc);

        jdoc.addProperty("id", getSentid());

        JsonArray jpars = new JsonArray();
        jdoc.add("paragraphs", jpars);
        JsonObject jpar = new JsonObject();
        jpars.add(jpar);
        jpar.addProperty("raw", getSentence());

        JsonArray jsents = new JsonArray();
        jpar.add("sentences", jsents);
        JsonObject jsent = new JsonObject();
        jsents.add(jsent);

        JsonArray jtoks = new JsonArray();
        jsent.add("tokens", jtoks);

        for (ConllWord cw : words) {
            jtoks.add(cw.toSpacyJson());
        }

        return jdocs;
    }

    public String[] tokens() {
        // pour l'entrée à Maltparser. Une ligne par mot
        return toString().split("\n");
    }

    /** @return the contents of the '# text = ...' comment line */
    public String getText() {
        return text;
    }

    /** change the text of the sentence,
     * @return true if the text really changed */
    public boolean setText(String t) {
        if (t == null || t.isEmpty()) return false;

        boolean changed = false;
        if (!t.equals(text)) {
            changed = true;
            text = t;
        }

        return changed;
    }

    /** gets sentence by concatenating forms
     * @return concatenated forms for all tokens
     */
    public String getSentence() {
        return getSentence(null);
    }

    /**
     * get the sentence with original spaces.
     *
     * @param id2pos if not null a map where the start and end character position of each id is written
     * @return the original sentence
     */
    public String getSentence(Map<Integer, List<Integer> > id2pos) {
        StringBuilder sb = new StringBuilder();
        //for (ConllWord word : words) {
        //    sb.append(word.getForm()).append(" ");
        //}

        int contracted_until = 0;
        int start = 0;
        for (ConllWord word : words) {
            //if (id2pos != null) {
            //    id2pos.put(word.getId(), sb.length());
            //}
            ConllWord mwt = null;
            if (contracted != null) {
                mwt = contracted.get(word.getId());
            }

//            if (id2pos != null) {
//                System.err.println("bbbb " + word.getId() + " " + id2pos.get(word.getId()) + " " + mwt + " " + contracted_until);
//            }
            if (mwt != null) {
                // we are at an MWT, we add this (and have to ignore the words which are part of the MWT)
                start = sb.length();
                sb.append(mwt.getForm()).append(mwt.getSpacesAfter());
                contracted_until = mwt.getSubid();

            } else if (contracted_until == 0 || word.getId() > contracted_until) {
                start = sb.length();
                sb.append(word.getForm()).append(word.getSpacesAfter());
            }
            if (id2pos != null) {
                ArrayList<Integer> start_end = new ArrayList<>(Arrays.asList(start, sb.length()));
                id2pos.put(word.getId(), start_end);
            }
        }
        //if (id2pos != null) {
        //    id2pos.put(-1, sb.length()); // end of last token
        //}
        return sb.toString().trim();
    }

    /**
     * get the sentence with original spaces as a list
     *
     * @return the original sentence
     */
    public Map<String, String> getSentenceAsList() {
        Map<String, String> tl = new LinkedHashMap<>();
        //StringBuilder sb = new StringBuilder();
        //for (ConllWord word : words) {
        //    sb.append(word.getForm()).append(" ");
        //}

        int contracted_until = 0;
        for (ConllWord word : words) {

            ConllWord mwt = null;
            if (contracted != null) {
                mwt = contracted.get(word.getId());
            }
            if (mwt != null) {
                //sb.append(mwt.getForm()).append(mwt.getSpacesAfter());
                tl.put(mwt.getFullId(), mwt.getForm() + mwt.getSpacesAfter());
                contracted_until = mwt.getSubid();
            } else if (contracted_until == 0 || word.getId() > contracted_until) {
                //sb.append(word.getForm()).append(word.getSpacesAfter());
                tl.put(word.getFullId(), word.getForm() + word.getSpacesAfter());
            }
        }
        return tl;
    }

    /**
     * returns head if the words from first to last are a subtree (with a common
     * head). Else it returns null
     *
     * @param first index of first word
     * @param last index of last word
     * @return head word or null
     */
    public ConllWord isSubTree(int first, int last) {
        ConllWord lhead = null;
        int count_heads = 0; // compte les tetes qui ne sont pas entre first et last
        for (int i = first; i < last; ++i) {
            ConllWord w = words.get(i);
            int h = w.getHead() - 1;
            if (h < first || h >= last) {
                count_heads++;
                if (count_heads > 1) {
                    return null;
                }
                lhead = w;
            }
        }

        return lhead;
    }

    /**
     * get a substring of the sentence which includes the given deprels. Words
     * not linked with these deprels but between head/dep of a given deprel will
     * be output in parentheses.
     *
     * @param deprels
     * @return
     */
    public String getSubTreeAsText(Set<String> deprels) {
        Set<Integer> ids = new HashSet<>();
        int first = 100000;
        int last = 0;
        for (ConllWord cw : words) {
            if (deprels.contains(cw.getDeplabel())) {
                first = Math.min(cw.getId(), first);
                last = Math.max(cw.getId(), last);
                if (cw.getHead() != 0) {
                    first = Math.min(cw.getHead(), first);
                    last = Math.max(cw.getHead(), last);
                }

                ids.add(cw.getId());
                ids.add(cw.getHead());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ConllWord cw : words) {
            if (cw.getId() < first) {
                continue;
            } else if (cw.getId() > last) {
                continue;
            }
            if (ids.contains(cw.getId())) {
                sb.append(" ").append(cw.getForm());
            } else {
                // sb.append(" (").append(cw.getForm()).append(")");
            }
        }
        return sb.toString();
    }

    // outputs the word with id and all it is commanding as a ConlluSentence
    public ConllSentence getSubtree(int id) throws ConllException {
        if (id < 1 || id > words.size()) {
            throw new ConllException("ID is not in sentence");
        }
        this.makeTrees(null);

        Map<Integer, ConllWord> subtree = new TreeMap<>();
        ConllWord chead = getWord(id);

        subtree.put(chead.getId(), chead);
        getSTdeps(chead, subtree);

        List<ConllWord> subtreelist = new ArrayList<>();
        for (ConllWord cw : subtree.values()) {
            ConllWord clone = new ConllWord(cw);
            if (cw == chead) {
                clone.setHead(0);
                clone.setDeplabel("_");
            }
            subtreelist.add(clone);
        }

        ConllSentence newcs = new ConllSentence(subtreelist);
        newcs.normalise();

        //System.out.println(columndefs.keySet());
        //System.out.println(newcs);
        return newcs;
    }

    // recursively get all kids
    private void getSTdeps(ConllWord head, Map<Integer, ConllWord> st) {
        //System.out.println("RECURSION " + head.getDependents());
        for (ConllWord dep : head.getDependents()) {
            //System.out.println("adding " + dep );
            st.put(dep.getId(), dep);
            getSTdeps(dep, st);
        }
    }

    public void setNewpar(String newpar) {
        this.newpar = newpar;
        is_modified = true;
    }

    public void setNewdoc(String newdoc) {
        this.newdoc = newdoc;
        is_modified = true;
    }

    public void setSentid(String sentid) {
        this.sentid = sentid;
        is_modified = true;
    }

    public void setTranslit(String translit) {
        this.translit = translit;
        is_modified = true;
    }

    public String getNewpar() {
        return newpar;
    }

    public String getNewdoc() {
        return newdoc;
    }

    public String getSentid() {
        return sentid;
    }

    public String getTranslit() {
        return translit;
    }

    public Map<String, String> getTranslations() {
        return translations;
    }

    public int getLastModification() {
        return last_modified;
    }
    /*public void setLastModification(long m) {
        last_modified = m;
    }
    */
    public void increaseModificationCounter() {
        last_modified++;
    }

    /**
     * translations: \n separated lines with "iso: translated text"
     */
    public boolean setTranslations(String translations) {
        if (translations.trim().isEmpty()) {
            return true;
        }
        String[] tt = translations.trim().split("\n");
        int errors = 0;
        if (this.translations == null) {
            this.translations = new HashMap<>();
        } else {
            this.translations.clear();
        }
        for (String translation : tt) {
            String[] elems = translation.split(":", 2);
            if (elems.length == 2) {
                this.translations.put(elems[0].trim(), elems[1].trim());
            } else {
                errors++;
            }
        }
        is_modified = true;
        if (errors > 0) {
            return false;
        }
        return true;
    }

    /**
     * calculate start and end offset for each word. contracted word as well.
     * Parts of contracted words copy the values form the MWT
     *
     * @param start offset of first word
     * @return the offset after the last word (including SpaceAfter)
     */
    public int calculateOffsets(int start) {
        for (ConllWord cw : words) {
            cw.setStart(start);
            int end = start + cw.getForm().length();
            cw.setEnd(end);
            start = end + cw.getSpacesAfter().length();
        }
        return start;
    }

    /**
     * find first word in sentence, which matches the condition
     */
    public ConllWord conditionalSearch(CheckCondition condition) throws ConllException {
        normalise();
        makeTrees(null);
        for (ConllWord cw : words) {
            if (cw.matchCondition(condition, null)) {
                return cw;
            }
        }
        if (emptywords != null) {
            for (List<ConllWord> cws : emptywords.values()) {
                for (ConllWord cw : cws) {
                    if (cw.matchCondition(condition, null)) {
                        return cw;
                    }
                }
            }
        }
        if (contracted != null) {
            for (ConllWord cw : contracted.values()) {
                if (cw.matchCondition(condition, null)) {
                    return cw;
                }
            }
        }
        return null;
    }

    /**
     * change all words of the sentence which match the condition
     *
     * @param condition a condition like (UPOS:NOUN and Lemma:de.*)
     * @param newvalues a list of new values FORM:"value" (as defined in Replacements.g4)
     * @param wordlists here we for put contents of files in conditions like Lemma:#filename.txt
     */
    public Set<ConllWord> conditionalEdit(CheckCondition condition, List<GetReplacement> newvalues, Map<String, Set<String>> wordlists, StringBuilder warnings) throws ConllException {
        //int changes = 0;
        Set<ConllWord> matching_cw = new HashSet<>();
        if (newvalues.isEmpty()) {
            //return 0;
            return matching_cw;
        }
        normalise();
        makeTrees(null);
        for (ConllWord cw : words) {
            if (cw.matchCondition(condition, wordlists)) {
                //changes++;
                matching_cw.add(cw);
                applyEdits(cw, newvalues, warnings);
            }
        }
        is_modified = true;
        if (contracted != null) {
            for (ConllWord cw : contracted.values()) {
                if (cw.matchCondition(condition, wordlists)) {
                    //changes++;
                    matching_cw.add(cw);
                    for (GetReplacement val : newvalues) {
                        //String[] elems = val.split(":", 2);
                        //if (elems.length == 2) {
                            //String newvalue = GetReplacement.parse_and_evaluate_replacement(elems[1], cw, false);
                            String newvalue = val.evaluate(cw);
                            switch (val.column.toLowerCase()) {
                                case "form":
                                    cw.setForm(newvalue);
                                    break;
                                case "misc":
                                    String[] key_value = newvalue.split("[:=]", 2);
                                    if (key_value.length != 2) {
                                        throw new ConllException("invalid new misc, must be Name=[value] <" + newvalue + ">");
                                    }

                                    if (key_value[1].isEmpty()) {
                                        cw.delMiscWithName(key_value[0]);
                                    } else {
                                        cw.addMisc(key_value[0] + "=" + key_value[1]);
                                    }
                                    break;

                                default:
                                    //throw new ConllException("invalid new value " + val);
                                    System.err.println("Multitoken words cannot have " + val.column + "-value. Ignored");
                            }
                        //}
                    }
                }
            }
        }

        if (emptywords != null) {
            for (List<ConllWord> cws : emptywords.values()) {
                for (ConllWord cw : cws) {
                    if (cw.matchCondition(condition, wordlists)) {
                        //changes++;
                        matching_cw.add(cw);
                        applyEdits(cw, newvalues, warnings);
                    }
                }
            }
        }

        //return changes;
        return matching_cw;
    }

    /**
     * apply a list of changes "column:value". Needed for the application of
     * rules in mass edit mode
     *
     * @param newvalues
     * @param warnings
     */
    void applyEdits(ConllWord cw, List<GetReplacement> newvalues, StringBuilder warnings) throws ConllException {
        is_modified = true;
        for (GetReplacement val : newvalues) {
            //String[] elems = val.split(":", 2);
            //if (elems.length == 2) {
                //String newvalue = GetReplacement.parse_and_evaluate_replacement(elems[1], cw, false);
                String newvalue = val.evaluate(cw);
                switch (val.column.toLowerCase()) {
                    case "upos":
                        cw.setUpostag(newvalue);
                        break;
                    case "xpos":
                        cw.setXpostag(newvalue);
                        break;
                    case "deprel":
                        cw.setDeplabel(newvalue);
                        break;
                    case "headid":
                        if (newvalue.isEmpty()) {
                            warnings.append("Warning: No value found for ")
                                    .append(val.column).append(" in sentence ")
                                    .append(sentid).append(", word ").append(cw.getFullId()).append('\n');
                            break;
                        }
                        boolean absid = true;
                        if (newvalue.charAt(0) == '+' || newvalue.charAt(0) == '-') {
                            absid = false;
                        }


                        if (absid) {
                            try {
                                int numval = Integer.parseInt(newvalue);
                                if (numval > words.size()) {
                                    warnings.append("Warning: Cannot set absolute head id in sentence ").append(sentid).append(", word ")
                                            .append(cw.getFullId()).append(": ").append(numval)
                                            .append(" > sentence length ").append(words.size()).append('\n');
                                    break;
                                }
                                if (numval == cw.getId()) {
                                    warnings.append("Warning: Cannot set absolute head id in sentence ").append(sentid).append(", word ")
                                            .append(cw.getFullId()).append(": ").append(numval).append(" == word id").append('\n');
                                    break;
                                }
                                if (numval != 0 && cw.commands(getWord(numval))) {
                                    warnings.append("Warning: Cannot set absolute head id in sentence ").append(sentid).append(", word ")
                                            .append(cw.getFullId()).append(": ").append(numval).append(" depends from current word").append('\n');
                                    break;
                                }
                                cw.setHead(numval);
                            } catch (NumberFormatException e) {
                                throw new ConllException("invalid absolute head id, must be positive integer or 0 <" + newvalue + ">");
                            }
                        } else {
                            try {
                                //System.err.println("ppppp " + newvalue);
                                int numval = Integer.parseInt(newvalue);
                                //if (numval == 0) {
                                //    throw new ConllException("bad relative head id, must be a negative or positive integer excluding 0 <" + newvalue + ">");
                                //}
                                int abshead = cw.getId() + numval;
                                //System.err.println("qqqqqq " + cw.getId() + " " + relhead + " " + abshead);
                                if (abshead < 1) {
                                    warnings.append("Warning: Cannot set relative head id in sentence ").append(sentid)
                                            .append(", word ").append(cw.getFullId()).append(": negative or 0 absolute head ")
                                            .append(abshead).append('\n');
                                    break;
                                }
                                if (abshead > words.size()) {
                                    warnings.append("Warning: Cannot set relative head id in sentence ").append(sentid)
                                            .append(", word ").append(cw.getFullId()).append(": absolute head ").append(abshead)
                                            .append(" > sentence length " + words.size()).append('\n');
                                    break;
                                }
                                if (abshead == cw.getId()) {
                                    warnings.append("Warning: Cannot set relative head id in sentence ").append(sentid)
                                            .append(", word ").append(cw.getFullId()).append(": absolute head ")
                                            .append(abshead).append(" == word id").append('\n');
                                    break;
                                }
                                if (cw.commands(getWord(abshead))) {
                                    warnings.append("Warning: Cannot set relative head id in sentence ").append(sentid)
                                            .append(", word ").append(cw.getFullId()).append(": absolute head ")
                                            .append(abshead).append(" depends from current word").append('\n');
                                    break;
                                }
                                cw.setHead(abshead);
                            } catch (NumberFormatException e) {
                                throw new ConllException("invalid relative head id, must be a negative or positive integer excluding 0 <" + newvalue + ">");
                            }
                        }
                        break;

                    case "feat":
                        if ("_".equals(newvalue)) {
                            cw.setFeatures("_");
                        } else {
                            String[] key_value = newvalue.split("[:=]", 2);
                            if (key_value.length != 2) {
                                throw new ConllException("invalid new feature, must be Name=[value] <" + newvalue + ">");
                            }

                            if (key_value[1].isEmpty()) {
                                cw.delFeatureWithName(key_value[0]);
                            } else {
                                cw.addFeature(key_value[0], key_value[1]);
                            }
                        }
                        break;
                    case "lemma":
                        cw.setLemma(newvalue);
                        break;
                    case "form":
                        cw.setForm(newvalue);
                        break;
                    case "misc":
                        if ("_".equals(newvalue)) {
                            cw.setMisc("_");
                        } else {
                            String[] key_value = newvalue.split("[:=]", 2);
                            if (key_value.length != 2) {
                                throw new ConllException("invalid new misc, must be Name=[value] <" + newvalue + ">");
                            }

                            if (key_value[1].isEmpty()) {
                                cw.delMiscWithName(key_value[0]);
                            } else {
                                cw.addMisc(key_value[0] + "=" + key_value[1]);
                            }
                        }
                        break;
                    case "eud":
                        if ("_".equals(newvalue)) {
                            cw.setDeps("_");
                            //hasEnhancedDeps = false;
                        } else {
                            String[] head_dep = newvalue.split(":", 2);
                            if (head_dep.length != 2) {
                                throw new ConllException("invalid new EUD, must be headId:deprel <" + newvalue + ">");
                            }
                            if (head_dep[0].matches("[+-][0-9]+")) {
                                // relative head
                                int eudhead = cw.getId() + Integer.parseInt(head_dep[0]);
                                if (eudhead < 0 || eudhead > words.size()) {
                                    warnings.append("bad EUD head in sentence " + getSentid() + " word " + cw.getId()).append('\n');
                                    break;
                                }
                                cw.addDeps("" + eudhead, head_dep[1]);
                                hasEnhancedDeps = true;
                            } else if (head_dep[0].matches("[0-9]+")) {
                                int eudhead = Integer.parseInt(head_dep[0]);
                                if (eudhead > words.size()) {
                                    warnings.append("bad absolute EUD head in sentence " + getSentid() + " word " + cw.getId()).append('\n');
                                    break;
                                }
                                cw.addDeps("" + eudhead, head_dep[1]);
                                hasEnhancedDeps = true;
                            } else {
                                throw new ConllException("invalid new EUD, must be absolute_head:deprel <" + newvalue + ">");
                            }
                        }

                        break;

                    default:
                        throw new ConllException("invalid receiving column <" + val.column + ">");
                }
            //} else {
            //    throw new ConllException("invalid newvalue specification, must be receiving_column:newvalue <" + val + ">");
            //}
        }
    }


    public int conditionalValidation(CheckCondition ifcondition, CheckCondition thencondition, StringBuilder warnings) throws ConllException {
        normalise();
        makeTrees(null);
        int cterrors = 0;
        for (ConllWord cw : words) {
            if (cw.matchCondition(ifcondition, null)) {
                if (!cw.matchCondition(thencondition, null)) {
                    warnings.append("   ERROR ").append(cw.toString()).append('\n');
                    cterrors++;
                }
            }
        }
        return cterrors;
    }

/** check whether current sentence is projective. Implies that makeTree() has been called.
 * for trees with more than one root this may return an invalid value
 * @return true if sentence is projective
 */
    public boolean isProjective(List<ConllWord> unproj) {

        for (ConllWord cw : words) {
            //System.err.println("WWWWWW " + cw);
            if (cw.getHead() != 0 && cw.getHeadWord() != null) {
                int distance = cw.getId() - cw.getHead();
                if (distance > 2) {
                    // head before dependant
                    for (int id = cw.getHead()+1; id < cw.getId(); id++) {
                        // all nodes between dep and head must depend from head
                        ConllWord c = getWord(id);
                        if (!cw.getHeadWord().commands(c)) {
                            //System.err.println(cw.getHeadWord() + " does not command " + c);
                            if (unproj == null) {
                                return false;
                            } else {
                                unproj.add(c);
                            }
                        }
                    }
                } else if (distance < -2) {
                    // head after dependant
                    for (int id = cw.getId()+1; id < cw.getHead(); id++) {
                        // all nodes between dep and head must depend from head
                        ConllWord c = getWord(id);
                        //System.err.println("ttttt " + id + " " + c);
                        if (!cw.getHeadWord().commands(c)) {
                            //System.err.println(cw.getHeadWord() + " Does not command " + c);
                            if (unproj == null) {
                                return false;
                            } else {
                                unproj.add(c);
                            }
                        }
                    }
                }
            }
        }
        if (unproj == null || unproj.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /** count the length of this sentence in terms of CoNLL-U file lines. Will recount if the sentence has been modified
     *
     * @return number of lines in CoNLL-U file lines
     */
    public int get_source_length() {
        if (is_modified) {
            number_of_comments = comments.size();
            if (newpar != null) number_of_comments++;
            if (newdoc != null) number_of_comments++;
            if (translit != null) number_of_comments++;
            if (translations != null) {
                number_of_comments += translations.size();
            }
            number_of_comments += 2; // add sent_id and text

            number_of_conllu_lines = words.size();
            if (emptywords != null) number_of_conllu_lines += emptywords.size();
            if (contracted != null) number_of_conllu_lines += contracted.size();
            is_modified = false;
        }
        return number_of_comments + number_of_conllu_lines + 1;
    }
    public int get_comment_length() {
        return number_of_comments;
    }
}
