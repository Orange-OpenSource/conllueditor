/* This library is under the 3-Clause BSD License

Copyright (c) 2018, Orange S.A.

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
 @version 1.9.0 as of 8th March 2019
 */
package com.orange.labs.conllparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    private final int shift; // il y a des fichiers conll qui ont des colonnes supplémentaires AVANT la colonne id il faut les ignorer; shift est le nombre des colonnes à gauche à ignorer
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

    private boolean nextToStringcomplete = false; // le prochain toString() rajoute les colonnes prefixées

    /**
     * @param conlllines les lignes d'une phrase d'un fichier CONLL à parser
     * @param shift si > 0 on ignore les premières colonnes (le LIF a préfixé
     * deux colonne au format CONLL "normal"
     */
    public ConllSentence(List<String> conlllines, Integer shift) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        this.shift = shift;
        parse(conlllines);
    }

    public ConllSentence(String conllstring, int shift) throws ConllException {
        //17	la	le	DET	GN-D	NOMBRE=SINGULIER|GENRE=FEMININ	0	_	_	_
        this.shift = shift;

        parse(new ArrayList<>(Arrays.asList(conllstring.split("\n"))));
    }

    public ConllSentence(List<ConllWord> cw) {
        shift = 0;
        words = cw;
        frames = new HashMap<>();
        hasEnhancedDeps = words.get(0).isBasicdeps_in_ed_column();

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
     */
    public ConllSentence(ConllSentence orig) {
        shift = 0;
        words = new ArrayList<>();
        frames = new HashMap<>();
        comments = orig.comments;
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
    }

    private void parse(List<String> conlllines) throws ConllException {
        words = new ArrayList<>();
        frames = new HashMap<>();
        comments = new ArrayList<>();
        hasEnhancedDeps = false;
        Set<Annotation> lastAnnots = null;

        for (String line : conlllines) {
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
            String[] fields = line.split("\t", shift + 1);
            //System.err.println("LINE\t" + line + " ");
            if (fields.length < shift + 1) {
                System.err.println("WARNING: ignoring short line: " + line);
                continue;
            }

            ConllWord w = new ConllWord(line, lastAnnots, shift);

            if (!w.getDeps().isEmpty() || w.isBasicdeps_in_ed_column()) {
                hasEnhancedDeps = true;
            }

            lastAnnots = w.getAnnots();
            if (lastAnnots != null) {
                hasAnnot = true;
                for (Annotation a : lastAnnots) {
                    if (a.target && a.begin) {
                        frames.put(a.frame, w);
                    }
                }
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
        }
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

//    public Map<Integer, Integer> getWord2chunks() {
//        return word2chunks;
//    }
    /**
     * integrate the chunk id to the "depscolumn"
     */
//    public void note_chunkId() {
//        for (ConllWord word : words) {
//            int chunkid = word2chunks.get(word.getId());
//            //word.setDeps(""+chunkid);
//            //word.setMisc("ChunkID");
//            // TODO use chunkid with key=value
//            //word.setMisc("" + chunkid);
//            word.addMisc("Chunk", chunkid);
//        }
//    }
//    public Map<Integer, Set<Integer>> getChunks2word() {
//        return chunks2word;
//    }
//
//    public void setWord2chunks(Map<Integer, Integer> word2chunks) {
//        this.word2chunks = word2chunks;
//    }
//
//    public void setChunks2word(Map<Integer, Set<Integer>> chunks2word) {
//        this.chunks2word = chunks2word;
//    }
//    /**
//     * return a chunk (necessites a call ro Dep2Chunk). Does not check, whether
//     * chunks2word is null !!
//     */
//
//    public List<String> getChunk(int id) {
//        Set<Integer> chunkIDs = chunks2word.get(id);
//        if (chunkIDs != null) {
//            //StringBuilder sb = new StringBuilder();
//            List<String> res = new ArrayList<>();
//            //boolean first = true;
//            for (Integer wordid : chunkIDs) {
//                ConllWord w = this.getWord(wordid);
//                //if (!first) {
//                //    sb.append(" ");
//                //}
//                //first = false;
//                //sb.append(w.getForm());
//                res.add(w.getForm());
//            }
//            //return sb.toString();
//            return res;
//        }
//        return null;
//    }
//
//
//    public String getChunks() {
//        StringBuilder sb = new StringBuilder();
//        int lastid = -1;
//        for (ConllWord word : words) {
//            int chunkid = (Integer) word.getMisc().get("Chunk");
//            if (lastid != chunkid/*Integer.parseInt(word.getMisc())*/) {
//                if (lastid != -1) {
//                    // close last chunk
//                    sb.append("] ");
//                }
//                sb.append("[");
//                lastid = chunkid; //Integer.parseInt(word.getMisc());
//            } else {
//                sb.append(" ");
//            }
//            List<String> feats = word.getFeatures();
//            String f;
//            if (feats.isEmpty()) {
//                f = EmptyColumn;
//            } else {
//                //f = String.join("|", feats);
//                List<String> ts = new ArrayList<>();
//                for (String t : feats) {
//                    if (t.startsWith("Number") || t.startsWith("Gender") || t.startsWith("Case")) {
//                        ts.add(t);
//                    }
//                }
//                if (ts.isEmpty()) {
//                    f = EmptyColumn;
//                } else {
//                    //f = String.join(",", ts);
//                    StringBuilder sb2 = new StringBuilder();
//                    boolean first = true;
//                    for (String t : ts) {
//                        if (!first) {
//                            sb2.append(',');
//                        } else {
//                            first = false;
//                        }
//                        sb2.append(t);
//                    }
//                    f = sb2.toString();
//                }
//            }
//            sb.append(String.format("%s|%s|%s|%s", word.getUpostag(), word.getLemma(), word.getForm(), f));
//        }
//        if (lastid != -1) {
//            sb.append("]");
//        }
//        return sb.toString();
//    }
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

    public int getShift() {
        return shift;
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
                    ew.setId(oldnewIds.get(ew.getId()));
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

    /** split the sentence in two, truncate this, and return the rest as a new sentence */
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
        n.normalise(words.size()+1);
        //System.out.println("nnnnnn " + n);

        words.addAll(n.getWords());
        if (n.emptywords != null) {
            if (emptywords == null) emptywords = n.emptywords;
            else emptywords.putAll(n.emptywords);
        }
        if (n.contracted != null) {
            if (contracted == null) contracted = n.contracted;
            else contracted.putAll(n.contracted);
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
                modified.setPrefixed(keepInfo.getPrefixed());
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
    public void nextToStringComplete() {
        nextToStringcomplete = true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (newdoc != null) {
            sb.append("# newdoc ").append(newdoc).append('\n');
        }
        if (newpar != null) {
            sb.append("# newpar ").append(newpar).append('\n');
        }
        if (sentid != null && !sentid.isEmpty()) {
            sb.append("# sent_id = ").append(sentid).append('\n');
        }
        for (String c : comments) {
            sb.append("# ").append(c).append('\n');
        }

        for (ConllWord word : words) {
            if (nextToStringcomplete) {
                word.nextToStringComplete();
            }

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
        nextToStringcomplete = false;
        return sb.toString();
    }

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

    public String getLaTeX() {
        StringBuilder sb = new StringBuilder();
        try {
            makeTrees(null);

            Map<String, Integer> position = new HashMap<>(); // position of each word, needed for latex-deprel

            if (newdoc != null) {
                sb.append("% newdoc ").append(newdoc).append('\n');
            }
            if (newpar != null) {
                sb.append("% newpar ").append(newpar).append('\n');
            }
            if (sentid != null && !sentid.isEmpty()) {
                sb.append("% sentid = ").append(sentid).append('\n');
            }
            sb.append("\\begin{dependency}\n\\begin{deptext}\n");

            boolean first = true;
            for (ConllWord word : words) {
                position.put(word.getFullId(), position.size() + 1);

                if (first) {
                    first = false;
                } else {
                    sb.append("\\& ");
                }
                sb.append(word.getForm()).append("\t");

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            sb.append("\\& ")
                                    .append(ew.getForm()).append("\t");
                            position.put(ew.getFullId(), position.size() + 1);
                        }
                    }
                }
            }
            sb.append("\\\\ % forms\n% ");

            first = true;
            for (ConllWord word : words) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\\& ");
                }
                sb.append(word.getLemma()).append("\t");

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            sb.append("\\& ")
                                    .append(ew.getLemma()).append("\t");
                        }
                    }
                }
            }
            sb.append("\\\\ % lemmas\n% ");

            first = true;
            for (ConllWord word : words) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\\& ");
                }
                sb.append(word.getUpostag()).append("\t");

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            sb.append("\\& ")
                                    .append(ew.getUpostag()).append("\t");
                        }
                    }
                }
            }
            sb.append("\\\\ % upos\n% ");

            first = true;
            for (ConllWord word : words) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\\& ");
                }
                sb.append(word.getFullId()).append("\t");

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            sb.append("\\& ")
                                    .append(ew.getFullId()).append("\t");
                        }
                    }
                }
            }
            sb.append("\\\\ % ids\n% ");

            first = true;
            for (ConllWord word : words) {
                if (first) {
                    first = false;
                } else {
                    sb.append("\\& ");
                }
                sb.append(position.get(word.getFullId())).append("\t");

                if (emptywords != null) {
                    List<ConllWord> ews = emptywords.get(word.getId());
                    if (ews != null) {
                        for (ConllWord ew : ews) {
                            sb.append("\\& ")
                                    .append(position.get(ew.getFullId())).append("\t");
                        }
                    }
                }
            }
            sb.append("\\\\ % position\n\\end{deptext}\n\n%        tete dep fonc\n");

            // System.err.println("ppp " + position);
            for (ConllWord word : words) {
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
                    if (ed.headid != 0) {
                        if (!position.containsKey(ed.getFullHeadId())) {
                            System.err.println("Invalid empty word head " + ed.getFullHeadId());
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
                            .append(position.get(Integer.toString(cc.getSubid()))).append("}{id")
                            .append(cc.getForm()).append("}\n");
                }
            }

            sb.append("\\end{dependency}\n");
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

    public Map<Integer, List<ConllWord>> getEmptyWords() {
        return emptywords;
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
     * return word with ident i
     */
    public ConllWord getWord(int i) {
        return words.get(i - 1);
    }

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

        for (ConllWord w : words) {
            w.setId(w.getId() * 10);
            if (w.getHead() > 0) {
                w.setHead(w.getHead() * 10);
            }
            if (w.getDeps() != null) {
                for (ConllWord.EnhancedDeps ed : w.getDeps()) {
                    ed.headid *= 10;
                }
            }
        }

        words.add(id, cw);
        cw.setId(id * 10 + 1);
        if (cw.getHead() > 0) {
            cw.setHead(cw.getHead() * 10);
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

    /**
     * join word with following. Use the attachment of the one closer to the
     * head. if both are equally close, chose the left
     */
    public void joinWords(int id) throws ConllException {
        if (id < words.size()) {
            ConllWord current = words.get(id - 1);
            ConllWord other = words.get(id);
            //System.err.println("THIS  " + current + ": " + getDistanceFromSentenceHead(current));
            //System.err.println("OTHER " + other   + ": " + getDistanceFromSentenceHead(other));
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
            //System.err.println("eeee\n" + this);
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

    public int getMaxdist() {
        return maxdist;
    }

    /**
     * class to store information of what parts of a ConllWord should be
     * highlighted. Add this information in the jsonTree
     */
    public static class Highlight {

        public ConllWord.Fields field;
        public int wordid;
        public int lastwordid;

        public Highlight(ConllWord.Fields field, int wordid) {
            this.field = field;
            this.wordid = wordid;
            this.lastwordid = wordid;
        }

        public Highlight(ConllWord.Fields field, int wordid, int lastwordid) {
            this.field = field;
            this.wordid = wordid;
            this.lastwordid = wordid;
            this.lastwordid = lastwordid;
        }
    }

    public static class AnnotationErrors {
        public int xpos; // invalid xpos
        public int upos;
        public int deprel;

        public AnnotationErrors() {
        }
    }

    /** produire un arbre en Json. Nécessite l'appel a makeTrees()
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

    public String[] tokens() {
        // pour l'entrée à Maltparser. Une ligne par mot
        return toString().split("\n");
    }

    public String getSentence() {
        StringBuilder sb = new StringBuilder();
        //for (ConllWord word : words) {
        //    sb.append(word.getForm()).append(" ");
        //}

        int contracted_until = 0;
        for(ConllWord word : words) {
            ConllWord mwe = null;
            if (contracted != null) mwe = contracted.get(word.getId());
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

    /** get a substring of the sentence which includes the given deprels.
     * Words not linked with these deprels but between head/dep of a given deprel will be output in parentheses.
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
}
