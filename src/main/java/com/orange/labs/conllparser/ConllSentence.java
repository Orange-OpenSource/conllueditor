/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2020, Orange S.A.

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
 @version 2.4.3 as of 21st May 2020
 */
package com.orange.labs.conllparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.AbstractMap;
import java.util.ArrayList;
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

/**
 * read and parse CONLL
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConllSentence {

    protected List<ConllWord> words;
    protected Map<Integer, List<ConllWord>> emptywords = null; // main id (before "."): empty nodes
    protected Map<Integer, ConllWord> contracted = null; // contracted words (MWE) startid (before hyphen): word

    //private final int shift; // il y a des fichiers conll qui ont des colonnes supplémentaires AVANT la colonne id il faut les ignorer; shift est le nombre des colonnes à gauche à ignorer
    private boolean hasAnnot = false; // au moins une annotation dans la phrase
    private boolean hasEnhancedDeps = false; // at least one word with enhanced deps (in this case we add basic deps to deps in conllu output)
    private Map<String, ConllWord> frames; // collect frame names of targets of this sentence

    protected ConllWord head = null; // rempli par makeTrees(): premiere ou seule tete
    protected List<ConllWord> headss = null; // rempli par makeTrees(): toutes les tetes

    // initiated by Deb2Chunkd.detect();
//    Map<Integer, Integer> word2chunks = null; // word-id: chunk-id
//    Map<Integer, Set<Integer>> chunks2word = null; // chunk-id: word-ids
    private String newpar = null;
    private String newdoc = null;
    private String sentid = null;
    private int maxdist = 0; // maximal distance between a word and its head (calculated by mameTrees())

    // store preceding comments
    private List<String> comments = null;
    // on lit des commentaires dans le fichier CONLL qui sont uniquement utiles pour Gift
    private boolean showgrana = true;
    private boolean showID = true;

    //private boolean nextToStringcomplete = false; // le prochain toString() rajoute les colonnes prefixées
    Map<String, Integer> columndefs = null;

    public enum Scoretype {
        FORM, LEMMA, UPOS, XPOS, FEATS, LAS, CLAS
    };

    /**
     * @param conlllines les lignes d'une phrase d'un fichier CONLL à parser
     */
    public ConllSentence(List<AbstractMap.SimpleEntry<Integer, String>> conlllines, Map<String, Integer> columndefs) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        //this.shift = shift;
        this.columndefs = columndefs;
        parse(conlllines);
    }

    public ConllSentence(String conllstring, Map<String, Integer> columndefs) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines = new ArrayList<>();
        int ct = 0;
        for (String line : conllstring.split("\n")) {
            sentenceLines.add(new AbstractMap.SimpleEntry(ct, line));
        }
        this.columndefs = columndefs;
        parse(sentenceLines);
    }

    public ConllSentence(List<ConllWord> cw) {
        words = cw;
        frames = new HashMap<>();
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
     * cloner une phrase (sans annotations et dépendances)
     *
     * @param orig sentence to be cloned
     */
    public ConllSentence(ConllSentence orig) {
        words = new ArrayList<>();
        frames = new HashMap<>();
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
        columndefs = orig.columndefs;
    }

    private void parse(List<AbstractMap.SimpleEntry<Integer, String>> conlllines) throws ConllException {
        words = new ArrayList<>();
        frames = new HashMap<>();
        comments = new ArrayList<>();
        hasEnhancedDeps = false;
        //Set<Annotation> lastAnnots = null;
        List<String> lastnonstandardinfo = null;

        for (AbstractMap.SimpleEntry<Integer, String> cline : conlllines) {
            String line = cline.getValue();
            if (line.startsWith("#")) {
                if (line.startsWith("# newpar")) {
                    newpar = line.substring(8).trim();
                } else if (line.startsWith("# newdoc")) {
                    newdoc = line.substring(8).trim();
                } else if (line.startsWith("# sent_id = ")) {
                    sentid = line.substring(12).trim();
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

            ConllWord w = new ConllWord(line, lastnonstandardinfo /*lastAnnots*/, columndefs, cline.getKey());

            if (!w.getDeps().isEmpty() /* || w.isBasicdeps_in_ed_column() */) {
                hasEnhancedDeps = true;
            }

//            lastAnnots = w.getAnnots();
//            if (lastAnnots != null) {
//                hasAnnot = true;
//                for (Annotation a : lastAnnots) {
//                    if (a.target && a.begin) {
//                        frames.put(a.frame, w);
//                    }
//                }
//            }
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
        }
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
        }
    }

    public void setComments(List<String> co) {
        if (co != null) {
            comments = co;
        }
    }

    public int size() {
        return words.size();
    }

    // informations utilisé par Gift pour savoir si on affiche ou pas les IDs et les span (grana)
    public void setShowgrana(boolean showgrana) {
        this.showgrana = showgrana;
    }

    public void setShowID(boolean showID) {
        this.showID = showID;
    }

    public boolean isShowgrana() {
        return showgrana;
    }

    public boolean isShowID() {
        return showID;
    }

    public boolean isAnnotated() {
        return hasAnnot;
    }

    public boolean hasTargets() {
        return !frames.isEmpty();
    }

    public Map<String, ConllWord> getFrameNames() {
        return frames;
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
            //System.err.println("AA " + word);
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
     * split the sentence in two, truncate this, and return the rest as a new
     * sentence
     */
    public ConllSentence splitSentence(int id) {
        id--;
        ConllSentence newsent = new ConllSentence(this);
        newsent.newdoc = null;
        newsent.newpar = null;
        if (newsent.sentid != null) {
            newsent.sentid += "-bis";
        }

        // cut words before id from new word
        Set<Integer> to_delete_from_this = new HashSet<>();
        if (emptywords != null) {
            for (Integer key : emptywords.keySet()) {
                if (key < id) {
                    newsent.emptywords.remove(key);
                } else {
                    to_delete_from_this.add(key);
                }
            }
            // and delete other kesy from this
            for (Integer key : to_delete_from_this) {
                emptywords.remove(key);
            }
        }

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

        // delete heads and words which are before id from newsent
        for (ConllWord cw : newsent.getWords()) {
            if (cw.getHead() <= id) {
                cw.setHead(0);
            }
        }
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

        newsent.normalise();
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
    }

    public void deleteUnusedWords() {
        // supprimer les mots devenus inutiles
        Iterator<ConllWord> it = words.iterator();
        while (it.hasNext()) {
            ConllWord w = it.next();
            if (w.getHead() == -1) {
                it.remove();
            }
        }
    }

    /**
     * next call to toString() prefixes columns cut off with shift parameter in
     * Constructor
     */
//    public void nextToStringComplete() {
//        nextToStringcomplete = true;
//    }
    /**
     * format the sentence in CoNLL-U format
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (newdoc != null && !newdoc.isEmpty()) {
            sb.append("# newdoc ").append(newdoc).append('\n');
        }
        if (newpar != null && !newpar.isEmpty()) {
            sb.append("# newpar ").append(newpar).append('\n');
        }
        if (sentid != null && !sentid.isEmpty()) {
            sb.append("# sent_id = ").append(sentid).append('\n');
        }
        for (String c : comments) {
            sb.append("# ").append(c).append('\n');
        }

        // output 0.1 empty words, if present
        if (emptywords != null) {
            List<ConllWord> ews = emptywords.get(0);
            if (ews != null) {
                for (ConllWord ew : ews) {
                    sb.append(ew.toString(hasEnhancedDeps/* withpartialhead*/)).append("\n");
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

            sb.append(word.toString(hasEnhancedDeps/*withpartialhead*/)).append("\n");
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
     */
    public String getLaTeX() {
        StringBuilder sb = new StringBuilder();
        try {
            makeTrees(null);

            Map<String, Integer> position = new HashMap<>(); // position of each word, needed for latex-deprel
            sb.append("%% for tikz-dependency\n");
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
                                    LinkedHashSet<String> tmp = word.getExtracolumns().get(ec);
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

            int maxdist = 0; // calculate here the most distant word in terms of deprels from root
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
                            if (ewheadpos != headpos) {
                                sb.append("\n\\depedge[edge below]{").append(ewheadpos).append("}{")
                                        .append(mypos).append("}{")
                                        .append(ed.deprel).append("}\n");
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

            sb.append("\n\n%% for deptrees.sty\n");
            sb.append(String.format("\\setbottom{%d} %% set to 0 to hide bottom line of forms\n", maxdist + 1));
            sb.append("\\begin{tikzpicture}[x=15mm,y=20mm]\n");
            for (ConllWord head : getHeads()) {
                sb.append(String.format("\\root{%d}{%s}{%s}\n", head.getId(), head.getForm(), head.getUpostag()));
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
                    sb.append("\\mtw{")
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
        //System.err.println("aaaaa " + contracted.keySet());
        return contracted.get(id);
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
        if (ews.isEmpty()) {
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
        // updating ids of normal words (including ehnanced deps)
        if (emptywords != null) {
            Map<Integer, List<ConllWord>> ews2 = new HashMap<>();
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
                ews2.put(id2 * factor, ewl);
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
        if (contracted != null) {
            contracted.remove(id);
            return;
        }
        throw new ConllException("No composed form with id " + id);
    }

    public Map<Integer, ConllWord> getContractedWords() {
        return contracted;
    }

    /**
     * join word with following. Use the attachment of the one closer to the
     * head. if both are equally close, chose the left
     */
    public void joinWords(int id) throws ConllException {
        if (id < words.size()) {
            ConllWord current = words.get(id - 1);
            ConllWord other = words.get(id);

            // get all first and last tokens of MWE
            // we delete a MWE if the joined words are at hte border or overlapping with the MWE
            if (contracted != null) {
                Set<ConllWord> mwes = new HashSet<>();
                for (ConllWord mwe : contracted.values()) {
                    if (mwe.getId() == id  || mwe.getId() == id + 1
                            || mwe.getSubid() == id || mwe.getSubid() == id + 1) {
                        mwes.add(mwe);
                    }
                }

                for (ConllWord mwe : mwes) {
                    contracted.remove(mwe.getId());
                }
            }

            //System.err.println("THIS  " + current + ": " + getDistanceFromSentenceHead(current));
            //System.err.println("OTHER " + other + ": " + getDistanceFromSentenceHead(other));
            if (getDistanceFromSentenceHead(current) > getDistanceFromSentenceHead(other)) {
                // current word further down than following
                other.setForm(current.getForm() + other.getForm());
                other.setLemma(current.getLemma() + other.getLemma());
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
            System.err.println("eeee\n" + this);
            normalise(1);
            makeTrees(null);
        }
    }

    private int getDistanceFromSentenceHead(ConllWord cw) {
        if (cw.getHead() == 0) {
            return 0;
        }
        ConllWord head = cw;
        int d = 1;
        while (head.getHead() != 0) {
            head = words.get(head.getHead() - 1);
            d++;
        }
        return d;
    }

    public void makeTrees(StringBuilder debug) throws ConllException {
        //parcourir les mots jusqu'à un root,
        // chercher les autres feuilles qui dépendent de cette racine
        // extraire cet arbre partiel
        //PrintStream out = System.out;
        Map<String, ConllWord> table = new HashMap<>(); // All nodes (including empty nodes) and their stringified id

        headss = new ArrayList<>();
//        try {
//            out = new PrintStream(System.out, true, "UTF-8");
//        } catch (UnsupportedEncodingException ex) {
//        }
        head = null;
        List<ConllWord> tempheads = new ArrayList<>();
        for (ConllWord w : words) {
            if (w.getHead() == 0) {
                tempheads.add(w);
            }
            w.getDependents().clear();
            w.getDependentsMap().clear();
        }

        //System.err.println("aaaaaa " + words);
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
                    throw new ConllException("head id is greater than sentence length: " + w.getHead() + " > " + words.size());
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
    }

    /**
     * calculate the Labelled Attachment Score and other metrics. Does not take
     * into accound empty words
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
        public Map<Integer, ConllWord.Fields> idshl;

        // highlight a single word on field
        public Highlight(ConllWord.Fields field, int wordid) {
            //this.field = field;
            //ids = new HashSet<>();
            //ids.add(wordid);
            idshl = new HashMap<>();
            idshl.put(wordid, field);
        }

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

        public AnnotationErrors() {
        }
    }

    /**
     * produire un arbre en Json. Nécessite l'appel a makeTrees()
     */
    public JsonArray toJsonTree(Set<String> validupos, Set<String> validxpos, Set<String> validdeprels,
            Highlight highlight, AnnotationErrors ae) {
        JsonArray jheads = new JsonArray();
        for (ConllWord head : headss) {
            //if (head.getTokentype() != ConllWord.Tokentype.WORD) continue;
            JsonObject jhead = head.toJson(validupos, validxpos, validdeprels, highlight, ae, contracted); //conllWord2json(head);
            jhead.addProperty("indexshift", words.get(0).getId() - 1); // nécessaire s'il y a plusieurs arbres dans la phrase
            jheads.add(jhead);
        }
        return jheads;
    }

    /**
     * json in spacy's format. @see https://spacy.io/api/annotation
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

    public String getSentence() {
        return getSentence(null);
    }

    /**
     * get the sentence with original spaces.
     *
     * @param pos2id if not null a map where the position of each id is written
     * @return the original sentence
     */
    public String getSentence(Map<Integer, Integer> pos2id) {
        StringBuilder sb = new StringBuilder();
        //for (ConllWord word : words) {
        //    sb.append(word.getForm()).append(" ");
        //}

        int contracted_until = 0;
        for (ConllWord word : words) {
            if (pos2id != null) {
                pos2id.put(sb.length(), word.getId());
            }
            ConllWord mwe = null;
            if (contracted != null) {
                mwe = contracted.get(word.getId());
            }
            if (mwe != null) {
                sb.append(mwe.getForm()).append(word.getSpacesAfter());
                contracted_until = mwe.getSubid();
            } else if (contracted_until == 0 || word.getId() > contracted_until) {
                sb.append(word.getForm()).append(word.getSpacesAfter());
            }
        }
        return sb.toString();
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

    public void setNewpar(String newpar) {
        this.newpar = newpar;
    }

    public void setNewdoc(String newdoc) {
        this.newdoc = newdoc;
    }

    public void setSentid(String sentid) {
        this.sentid = sentid;
    }

    public String isNewpar() {
        return newpar;
    }

    public String isNewdoc() {
        return newdoc;
    }

    public String getSentid() {
        return sentid;
    }

    /**
     * calculate start and end offset for each word. contracted word as well.
     * Parts of contracted words copy the values form the MTW
     *
     * @param start offset of first word
     * @param return the offset after the last word (including SpaceAfter)
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
}
