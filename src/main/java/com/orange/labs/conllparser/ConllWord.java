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
 @version 1.14.2 as of 27th September 2019
 */
package com.orange.labs.conllparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConllWord {
    private List<String> prefixed = null; // colonnes à ignorer avec shift
    private boolean nextToStringcomplete = false; // le prochain toString() rajoute les colonnes prefixées

    private int position; // needed for GUI, since empty nodes will be inserted in between regulare nodes. Filled by makeTrees
    private int arc_height = 0; // needed for GUI to disply arc height in flat graphs nicely
    private int id; // colonne 1 (or bit before -/. in case of contracted words or empty nodes
    private int subid = -1; // the bit after . or -)
    private String form; // 2
    private String lemma; // 3
    private String wordcontext; // utilisé pour y mettre un lemme "étendu" comme se@installer (si la définition de target est différent)
    private String xpostag; // 4
    private String upostag; // 5
    Map<String, String> features; // 6
    private int head; //7
    private String deplabel; // 8

    private List<EnhancedDeps> deps; // 9 // badly named, its the heads of the enhanced dependencies
    //private boolean basicdeps_in_ed_column = false; // true if basic deps are copied to column 9
    Map<String, Object> misc; // 10 value can be string or integer

    private String spacesAfter = null;

    private ConllWord headWord = null;
    private final List<ConllWord> dependents;// = null; // pour pouvoir afficher les arbres graphiquements (debugage). Cette variable est rempli par ConllSentence.makeTrees()
    private final Map<Integer, ConllWord> depmap; // pour pouvour accéder aux dépendants avec leur ID, rempli par makeTrees()

    private Tokentype toktype = Tokentype.WORD;
    public final static String EmptyColumn = "_";

    private static Pattern number = Pattern.compile("\\d{1,3}(#\\d{3})+");
    public static final boolean DEBUG = false;
    public static boolean RELAXED = false; // if true, we correct errors in conllu file which are non ambiguous
    // if head == id --> set head to 0
    // empty columns --> set to "_"

    private int partOfChunk = 0; // 0 no chunk calculated
    // this word is a wh question pronoun
    private boolean whquestion = false;
    // additional semantic annotation (non-conllu)
    private Set<Annotation> annot = null; // column 11 if B/I/O:....
    // column 11 otherwise
    private List<String> nonstandardInfo = null;

    public static boolean orderfeatures = true; // order morphological features ore keep them as they are in the CoNLL-U data

    public enum Tokentype {
        WORD, CONTRACTED, EMPTY
    };

    public enum Fields {
        FORM, LEMMA, UPOS, XPOS, DEPREL
    };

    /**
     * cloner un mot (uniquement colonnes ID à FEAT
     *
     * @param orig
     */
    public ConllWord(ConllWord orig) {
        if (orig.getPrefixed() != null) {
            prefixed = new ArrayList<>();
            prefixed.addAll(orig.getPrefixed());
        }

        position = orig.getPosition();
        id = orig.getId();
        subid = orig.getSubid();
        toktype = orig.getTokentype();
        form = orig.getForm();
        lemma = orig.getLemma();
        xpostag = orig.getXpostag();
        upostag = orig.getUpostag();
        if (orig.getFeatures() != null) {
            if (orderfeatures) {
                features = new TreeMap<>(orig.getFeatures());
            } else {
                features = new LinkedHashMap<>(orig.getFeatures());
            }
        } else {
            if (orderfeatures) {
                features = new TreeMap<>();
            } else {
                features = new LinkedHashMap<>();
            }
        }
        head = orig.getHead();
        deplabel = orig.getDeplabel();
        //deps = orig.getDeps();
        deps = new ArrayList<>();
        for (EnhancedDeps ed : orig.getDeps()) {
            deps.add(new EnhancedDeps(ed));
        }

        //basicdeps_in_ed_column = orig.basicdeps_in_ed_column;
        //misc = orig.getMisc();
        misc = new LinkedHashMap<>(orig.misc);
        spacesAfter = orig.spacesAfter;
        dependents = new ArrayList<>();
        depmap = new TreeMap<>();

        if (orig.annot != null) {
            annot = new TreeSet<>();
            for (Annotation a : orig.getAnnots()) {
                annot.add(new Annotation(a));
            }
        }
        whquestion = orig.isWhquestion();
    }

    public ConllWord(String conllline, Set<Annotation> lastannots) throws ConllException {
        this(conllline, lastannots, 0, -1);
    }

    public ConllWord(String form) {
        dependents = new ArrayList<>();
        depmap = new TreeMap<>();
        this.form = form;
        id = 1;
        head = 0;
        lemma = EmptyColumn;
        upostag = EmptyColumn;
        xpostag = EmptyColumn;
        deplabel = EmptyColumn;
        if (orderfeatures) {
            features = new TreeMap<>(); //Arrays.asList(elems[shift + 5].split("\\|")));
        } else {
            features = new LinkedHashMap<>();
        }
        misc = new LinkedHashMap<>();
        deps = new ArrayList<>();
    }

    public ConllWord(String composedform, int from, int to) {
        dependents = new ArrayList<>();
        depmap = new TreeMap<>();
        this.form = composedform;
        id = from;
        subid = to;
        toktype = Tokentype.CONTRACTED;
        head = 0;
        lemma = EmptyColumn;
        upostag = EmptyColumn;
        xpostag = EmptyColumn;
        deplabel = EmptyColumn;
    }

    /*    @param shift si > 0 on ignore les premières colonnes (le LIF a préfixé deux colonne au format CONLL "normal"   */
    public ConllWord(String conllline, Set<Annotation> lastannots, int shift, int linenumber) throws ConllException {
        dependents = new ArrayList<>();
        depmap = new TreeMap<>();
        String[] elems = conllline.split("\t");

        //System.err.println("SHIFT " + shift + "  LEN " +  elems.length + "\t" + conllline);
        //System.err.println("     " + elems.length);
        if (elems.length < 8 + shift) {
            throw new ConllException("invalid line: " + linenumber + " '" + conllline + "'");
        }
        //System.out.println("L:"+conllline);
        String[] idelems = elems[shift].split("[\\.-]");
        id = Integer.parseInt(idelems[0]);
        if (idelems.length > 1) {
            subid = Integer.parseInt(idelems[1]);
            if (elems[shift].contains(".")) {
                toktype = Tokentype.EMPTY;
            } else {
                toktype = Tokentype.CONTRACTED;
            }
        }

        if (shift > 0) {
            prefixed = new ArrayList<>();
            for (int i = 0; i < shift; ++i) {
                prefixed.add(elems[i]);
            }
        }

        form = elems[shift + 1];

        //System.err.println("FORM:"+form);
        Matcher m = number.matcher(form);
        if (m.matches()) {
            //System.out.println("NNN " + form);
            form = form.replaceAll("#", "");
            /* } else if (form.contains("#")) {
            if (form.charAt(0) == '#') {
                form = form.replaceAll("#", "-");
            } else {
                form = form.replaceAll("#", " ");
            }*/
        }

        if (form.isEmpty()) {
            throw new ConllException("empty form. Use '" + EmptyColumn + "' in line (" + linenumber + "): " + conllline);
        }

        misc = new LinkedHashMap<>();
        if (toktype == Tokentype.CONTRACTED) {
            for (int x = 2 + shift; x < shift + 9; ++x) {
                if (!elems[x].equals(EmptyColumn)) {
                    throw new ConllException("Contracted word must not have columns filled after position 2");
                }
                // processing Misc column
            }
            setMisc(elems[shift + 9]);
            deps = new ArrayList<>(); // TODO either keep this or add checks to getDeps() calls
        } else {
            lemma = elems[shift + 2];
            if (lemma.isEmpty()) {
                if (RELAXED) {
		    lemma = EmptyColumn;
		    System.err.println("lemma column empty. Set to \"_\" in line (" + linenumber +") \"" + conllline + '"');
		}
                else {
		    throw new ConllException("empty lemma. Use '" + EmptyColumn + "' in line (" + linenumber +") \"" + conllline + '"');
		}
            }

            upostag = elems[shift + 3];
            if (upostag.isEmpty()) {
                if (RELAXED) {
		    upostag = EmptyColumn;
		    System.err.println("upostag column empty. Set to \"_\" in line (" + linenumber +") \"" + conllline + '"');
		}
                else {
		    throw new ConllException("empty upostag. Use '" + EmptyColumn + "' in line (" + linenumber +") \"" + conllline + '"');
		}
            }

            xpostag = elems[shift + 4];
            if (xpostag.isEmpty()) {
                if (RELAXED) {
		    xpostag = EmptyColumn;
		    System.err.println("xpostag column empty. Set to \"_\" in line (" + linenumber +") \"" + conllline + '"');
		}
                else {
		    throw new ConllException("empty xpostag. Use '" + EmptyColumn + "' in line (" + linenumber +") \"" + conllline + '"');
		}
            }

            if (orderfeatures) {
                features = new TreeMap<>();
            } else {
                features = new LinkedHashMap<>();
            }
	    if (elems[shift + 5].isEmpty()) {
                if (RELAXED) {
		    elems[shift + 5] = EmptyColumn;
		    System.err.println("feature column empty. Set to \"_\" in line (" + linenumber +") \"" + conllline + '"');
		} else {
		    throw new ConllException("empty features. Use '" + EmptyColumn + "' in line (" + linenumber +") \"" + conllline + '"');
		}
	    }
            this.setFeatures(elems[shift + 5]);

            if (elems[shift + 6].isEmpty()) {
                if (RELAXED) {
                   head = 0;
                   System.err.println("head id is empty. Set to 0 in line (" + linenumber +") \"" + conllline + '"');
                } else {
		    throw new ConllException("empty head. Use '" + EmptyColumn + "' or head number in line (" + linenumber +") \"" + conllline + '"');
		}
            }
            if (elems[shift + 6].equals(EmptyColumn)) {
                head = -1;
            } else {
                try {
                    head = Integer.parseInt(elems[shift + 6]);
                } catch (NumberFormatException e) {
                    if (RELAXED) {
                      head = 0;
                      System.err.println("head id is no number. Set to 0 in line (" + linenumber +") \"" + conllline + '"');
                    } else {
			throw new ConllException("head id must be a number in line (" + linenumber +") \"" + conllline + '"');
		    }
                }
            }

            if (head == id && head != 0) {
                if (RELAXED) {
                   head = 0;
                   System.err.println("head id == word id. Set to 0 in line (" + linenumber +") \"" + conllline + '"');
                } else {
		    throw new ConllException("head id must be different from word id in line (" + linenumber +") \"" + conllline + '"');
		}
            }

            deplabel = elems[shift + 7];
            if (deplabel.isEmpty()) {
                if (RELAXED) {
		    deplabel = EmptyColumn;
		    System.err.println("deplabel column empty. Set to \"_\" in line (" + linenumber +") \"" + conllline + '"');
		}
                else {
		    throw new ConllException("empty deplabel. Use '" + EmptyColumn + "' in line (" + linenumber +") \"" + conllline + '"');
		}
            }

            whquestion = false;

            deps = new ArrayList<>();

            if (elems.length > shift + 9) {
                if (!elems[shift + 8].equals(EmptyColumn)) {
                    //basicdeps_in_ed_column = true;
                    String[] eds = elems[shift + 8].split("\\|");
                    for (String ed : eds) {
                        if (!ed.equals("0")) {
                            EnhancedDeps ehd = new EnhancedDeps(ed);
//                            if (ehd.headid == head && ehd.headsubid == 0) {
//                                // we do not read the copy of basic deps in the enhanced deps column
//                                // but we keep in mind that it is so
//                                continue;
//                            }
                            deps.add(ehd);
                        }
                    }
                }

                // processing Misc column
                setMisc(elems[shift + 9]);

                if (elems.length > shift + 10) {
                    try {
                        // est-ce qu'il s'agit dun mot avec une annotation en frame ?
                        Annotation a = new Annotation(elems[shift + 10], null, lastannots);
                        annot = new TreeSet<>();
                        annot.add(a);
                        for (int i = shift + 10 + 1; i < elems.length; ++i) {
                            try {
                                annot.add(new Annotation(elems[i], null, lastannots));
                            } catch (ConllException ex) {
                                System.err.println("Annotation error " + ex.getMessage());
                            }
                        }
                        if (annot.isEmpty()) {
                            annot = null;
                        }
                    } catch (ConllException ex) {
                        // non, apparament c'est autre chose
                        nonstandardInfo = new ArrayList<>();
                        for (int i = shift + 10; i < elems.length; ++i) {
                            nonstandardInfo.add(elems[i]);
                        }
                    }

                }
            }
        }
    }

    public boolean isWhquestion() {
        return whquestion;
    }

    public void setWhquestion(boolean whquestion) {
        this.whquestion = whquestion;
    }

//    public boolean isBasicdeps_in_ed_column() {
//        return basicdeps_in_ed_column;
//    }
    public List<String> getPrefixed() {
        return prefixed;
    }

    public void setPrefixed(List<String> p) {
        prefixed = p;
    }

    public void setPartOfChunk(int i) {
        partOfChunk = i;
    }

    /**
     * utilisée par le Diff !
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        //System.err.println("CW EQUALS\n  " + this + "\n  " + o );
        if (!(o instanceof ConllWord)) {
            return false;
        }
        ConllWord cw = (ConllWord) o;
        // System.out.println("AAA"+form.equals(((ConllWord)o).getForm())+"\n  " + o + "\n  " + this);
        if (!form.equals(cw.getForm())) {
            return false;
        }
        if (id != cw.getId()) {
            return false;
        }
        if (!upostag.equals(cw.getUpostag())) {
            return false;
        }
        if (!lemma.equals(cw.getLemma())) {
            return false;
        }

        return true;

//        if (form.equals(((ConllWord) o).getForm())) {
//            if (id != ((ConllWord) o).getId()
//                    || !lemma.equals(((ConllWord) o).getLemma())
//                    || !upostag.equals(((ConllWord) o).getUpostag()))
//                //System.err.println("\nCW EQUALS\n  " + this + "\n  " + o );
//                System.err.println("ICIIIIIIIIIIIIIIIIIIII CW EQUALS "); // + this + " " + (ConllWord)o );
//
//        }
//
//        return form.equals(((ConllWord) o).getForm());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.form);
        return hash;
    }

    /**
     * rendre une mot dépendant ayant le label donnée ou null
     *
     * @param deplabel
     * @return le mot dépendant
     */
    public ConllWord getDWord(String deplabel) {
        if (DEBUG) {
            System.out.println("DDWord " + id + ":" + form + " " + deplabel);
        }
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel)) {
                if (DEBUG) {
                    System.out.println(" OK " + dep);
                }
                return dep;
            }
        }
        if (DEBUG) {
            System.out.println(" KO");
        }
        return null;
    }

    /**
     * rendre tous les mots dépendants ayant le label
     *
     * @param deplabel
     * @return
     */
    public List<ConllWord> getDWords(String deplabel) {
        List<ConllWord> res = null;
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel)) {
                if (res == null) {
                    res = new ArrayList<>();
                }
                res.add(dep);
            }
        }
        return res;
    }

    public List<ConllWord> getDWordsRE(String deplabel, boolean inverse) {
        List<ConllWord> res = null;
        for (ConllWord dep : dependents) {
            //System.err.println("zzzz " + dep.getDeplabel() + " m " + deplabel);
            boolean matchOK = dep.getDeplabel().matches(deplabel);
            //if (dep.getDeplabel().matches(deplabel)) {
            if ((matchOK && !inverse) || (!matchOK && inverse)) {
                if (res == null) {
                    res = new ArrayList<>();
                }
                res.add(dep);
            }
        }
        return res;
    }

    /**
     * rendre le mot à deux niveau au-dessous du mot actuel (via deux relations
     * de dépendance
     *
     * @param deplabel1 relation du mot courant au dépendant
     * @param deplabel2 relation du dépendant à un de ces dépendants
     * @return le petit enfant
     */
    public ConllWord getDDWord(String deplabel1, String deplabel2) {

        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel1)) {
                ConllWord ddep = dep.getDWord(deplabel2);
                if (ddep != null) {
                    return ddep;
                }
            }
        }
        return null;
    }

    /**
     * rendre le dépendant si il à un dépendant du mot actuel (via deux
     * relations de dépendance
     *
     * @param deplabel1 relation du mot courant au dépendant
     * @param deplabel2 relation du dépendant à un de ces dépendants
     * @param lemma2 le lemme du 2e dépendants
     * @return le dépendant
     */
    public ConllWord getDWordIfDLabel(String deplabel1, String deplabel2, String lemma2) {
        if (DEBUG) {
            System.out.println("DDWordIfLabel " + id + ":" + form + " " + deplabel1 + " " + deplabel2 + " <" + lemma2 + ">");
        }
        for (ConllWord dep : dependents) {

            if (dep.getDeplabel().equals(deplabel1)) {
                ConllWord ddep = dep.getDWordLemma(deplabel2, lemma2);
                if (ddep != null) {
                    if (DEBUG) {
                        System.out.println(" OK " + dep);
                    }
                    return dep;
                }
            }
        }
        if (DEBUG) {
            System.out.println(" KO");
        }
        return null;
    }

    /**
     * rendre le dépendant si il à un dépendant du mot actuel (via deux
     * relations de dépendance
     *
     * @param deplabel1 relation du mot courant au dépendant
     * @param lemma1 le lemme du dépendant
     * @param deplabel2 relation du dépendant à un de ces dépendants
     *
     * @return le dépendant
     */
    public ConllWord getDWordIfDLabel1(String deplabel1, String lemma1, String deplabel2) {
        if (DEBUG) {
            System.out.println("DDWordIfLabel1 " + id + ":" + form + " " + deplabel1 + " <" + lemma1 + "> " + deplabel2);
        }
        for (ConllWord dep : dependents) {
            if (dep.hasDeplabel(deplabel1) && dep.hasLemma(lemma1)) {
                ConllWord ddep = dep.getDWord(deplabel2);
                if (ddep != null) {
                    if (DEBUG) {
                        System.out.println(" OK " + ddep);
                    }
                    return ddep;
                }
            }
        }
        if (DEBUG) {
            System.out.println(" KO");
        }
        return null;
    }

    public List<ConllWord> getDWordsIfDLabel(String deplabel1, String deplabel2, String lemma2) {
        if (DEBUG) {
            System.out.println("DDWordsIfLabel " + id + ":" + form + " " + deplabel1 + " " + deplabel2 + " <" + lemma2 + ">");
        }
        List<ConllWord> res = new ArrayList<>();
        for (ConllWord dep : dependents) {

            if (dep.hasDeplabel(deplabel1)) {
                ConllWord ddep = dep.getDWordLemma(deplabel2, lemma2);
                if (ddep != null) {
                    if (DEBUG) {
                        System.out.println(" OK " + dep);
                    }
                    res.add(dep);
                }
            }
        }
        if (DEBUG) {
            System.out.println(" KO");
        }
        return res;
    }

    /**
     * rendre un mot dépendant ayant le label et le upos données ou null
     *
     * @param deplabel
     * @param upostag
     * @return
     */
    public ConllWord getDWordUpos(String deplabel, String upostag) {
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel) && dep.getUpostag().equals(upostag)) {
                return dep;
            }
        }
        return null;
    }

    /**
     * rendre tous les mots dépendants ayant le label et le upos données ou null
     *
     * @param deplabel
     * @param upostag
     * @return
     */
    public List<ConllWord> getDWordsUpos(String deplabel, String upostag) {
        List<ConllWord> res = null;
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel) && dep.getUpostag().equals(upostag)) {
                if (res == null) {
                    res = new ArrayList<>();
                }
                //return dep;
                res.add(dep);
            }
        }
        return res;
    }

    /**
     * rendre une mot dépendant ayant le label et le pos données ou null
     *
     * @param deplabel
     * @param postag
     * @return
     */
    public ConllWord getDWordPos(String deplabel, String postag) {
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel) && dep.getXpostag().equals(postag)) {
                return dep;
            }
        }
        return null;
    }

    /**
     * rendre tous les mots dépendants ayant le label et le pos données ou null
     *
     * @param deplabel
     * @param postag
     * @return
     */
    public List<ConllWord> getDWordsPos(String deplabel, String postag) {
        List<ConllWord> res = null;
        for (ConllWord dep : dependents) {
            if (dep.getDeplabel().equals(deplabel) && dep.getXpostag().equals(postag)) {
                if (res == null) {
                    res = new ArrayList<>();
                }
                //return dep;
                res.add(dep);
            }
        }
        return res;
    }

    /**
     * rendre une mot dépendant ayant le label et le lemme données ou null
     *
     * @param deplabel
     * @param lemma
     * @return
     */
    public ConllWord getDWordLemma(String deplabel, String lemma) {
        if (DEBUG) {
            System.out.println("DDWordLemma " + id + ":" + form + " " + deplabel + " <" + lemma + ">");
        }
        for (ConllWord dep : dependents) {
            // System.out.println(" fff " + dep + " <" + dep.getLemma() + ">");
            if (dep.hasDeplabel(deplabel) && dep.hasLemma(lemma)) {
                if (DEBUG) {
                    System.out.println(" OK " + dep);
                }
                return dep;
            }
        }
        if (DEBUG) {
            System.out.println(" KO");
        }
        return null;
    }

    public void printDeps(StringBuilder out, String indent) {
        //out.println(indent + id + " " + deplabel + " '" + form + "' " + upostag + "/" + xpostag);
        out.append(indent).append(id).append(" ").append(deplabel).append(" '").append(form).append("' ")
                .append(upostag).append("/").append(xpostag).append('\n');
        for (ConllWord dep : dependents) {
            dep.printDeps(out, indent + "   ");
        }
    }

    /**
     * le mots (avec ses dépendants) en json
     *
     * @param validupos
     * @param validxpos
     * @param validdeprels
     * @param highlight
     * @param ae
     * @return
     */
    // TODO: add misc column
    public JsonObject toJson(Set<String> validupos, Set<String> validxpos, Set<String> validdeprels,
                             ConllSentence.Highlight highlight, ConllSentence.AnnotationErrors ae,
                             Map<Integer, ConllWord> contracted) {
        JsonObject jword = new JsonObject();
        jword.addProperty("position", position);
        if (arc_height > 0) {
            jword.addProperty("archeight", arc_height);
        }
        if (toktype == Tokentype.WORD) {
            jword.addProperty("id", id);
        } else {
            jword.addProperty("id", id + "." + subid);
            jword.addProperty("token", "empty");
        }
        jword.addProperty("form", form);
        jword.addProperty("lemma", lemma);

        if (contracted != null) {
            ConllWord contr = contracted.get(id);
            if (contr != null) {
                JsonObject jcontr = new JsonObject();
                jcontr.addProperty("fromid", contr.id);
                jcontr.addProperty("toid", contr.subid);
                jcontr.addProperty("form", contr.form);
                jword.add("mwe", jcontr);
            }
        }

        if (!features.isEmpty()) { // && !"_".equals(features.get(0))) {
            JsonArray jfeats = new JsonArray();
            for (String f : features.keySet()) {

                JsonObject jfeat = new JsonObject();
                jfeat.addProperty("name", f);
                String val = features.get(f);
                if (val != null) {
                    jfeat.addProperty("val", val);
                }
                jfeats.add(jfeat);
            }
            jword.add("feats", jfeats);
        }

        if (misc != null && !misc.isEmpty()) {
            JsonArray jmiscs = new JsonArray();
            for (String f : misc.keySet()) {

                JsonObject jmisc = new JsonObject();
                jmisc.addProperty("name", f);
                Object val = misc.get(f);
                if (val != null) {
                    if (val instanceof Number) {
                        jmisc.addProperty("val", (Number) val);
                    } else {
                        jmisc.addProperty("val", (String) val);
                    }
                }
                jmiscs.add(jmisc);
            }
            jword.add("misc", jmiscs);
        }

        jword.addProperty("xpos", xpostag);
        jword.addProperty("upos", upostag);

        if (!deps.isEmpty()) {
            JsonArray edeps = new JsonArray();
            for (EnhancedDeps ed : deps) {
                if (ed.headword == null) {
                    continue;
                }
                JsonObject edo = new JsonObject();
                edo.addProperty("id", ed.getFullHeadId());
                edo.addProperty("position", ed.headword.getPosition());
                edo.addProperty("deprel", ed.deprel);
                edeps.add(edo);
            }
            if (edeps.size() > 0) {
                jword.add("enhancedheads", edeps);
            }
        }

        if (validupos != null && !EmptyColumn.equals(upostag) && !validupos.contains(upostag)) {
            jword.addProperty("uposerror", 1);
            ae.upos++;
        }
        if (validxpos != null && !EmptyColumn.equals(xpostag) && !validxpos.contains(xpostag)) {
            jword.addProperty("xposerror", 1);
            ae.xpos++;
        }
        // if (head != 0 /*&& "root".equals(deplabel)*/) { //(head > -1 /*&& !EmptyColumn.equals(deplabel)*/) {
        // we don't mount a deplabel "root", since it should not exist if head != 0
        // and if head == 0, it will not be displayed
        // TODO: delete "root" if head != 0 ??
        jword.addProperty("deprel", deplabel);
        if (validdeprels != null && toktype == Tokentype.WORD && !validdeprels.contains(deplabel)) {
            jword.addProperty("deprelerror", 1);
            ae.deprel++;
        }
        // }

        if (highlight != null && highlight.ids.contains(id) //&& highlight.wordid <= id && highlight.lastwordid >= id
                ) {
            //System.err.println("tttt " + this + " " + highlight);
            switch (highlight.field) {
                case FORM:
                    jword.addProperty("formhighlight", 1);
                    break;
                case LEMMA:
                    jword.addProperty("lemmahighlight", 1);
                    break;
                case UPOS:
                    jword.addProperty("uposhighlight", 1);
                    break;
                case XPOS:
                    jword.addProperty("xposhighlight", 1);
                    break;
                case DEPREL:
                    jword.addProperty("deprelhighlight", 1);
                    break;
            }
        }

        jword.addProperty("chunk", partOfChunk);
        //jword.addProperty("type", word.getPartialDeplabel());
        jword.addProperty("type", "");
        //Set<Annotation> annots = word.getAnnots();
        if (annot != null) {
            for (Annotation a : annot) {
                jword.addProperty("type", a.getSemanticRole());
                break;
            }
        } else if (nonstandardInfo != null) {
            // TODO change one conllu plus support is OK
            // for the time being we just display anything in columns > 10 as is, without interpretation
            int colct = 11;
            for (String colvalue : nonstandardInfo) {
                jword.addProperty("col" + colct, colvalue);
                colct++;
            }
        }



        if (!dependents.isEmpty()) {
            JsonArray jchildren = new JsonArray();
            jword.add("children", jchildren);
            for (ConllWord child : dependents) {
                JsonObject jchild = child.toJson(validupos, validxpos, validdeprels, highlight, ae, contracted);
                jchildren.add(jchild);
            }
        }
        return jword;
    }

    public JsonObject toSpacyJson() {
        JsonObject w = new JsonObject();
        w.addProperty("id", getId());
        if (!EmptyColumn.equals(getDeplabel())) w.addProperty("dep", getDeplabel());
        if (getHead() <= 0) w.addProperty("head", 0);
        else w.addProperty("head", getHead()-getId());
        if (!EmptyColumn.equals(getUpostag())) w.addProperty("tag", getDeplabel());
        w.addProperty("orth", getForm());

        return w;
    }

    public List<String> getExtracolumns() {
        return nonstandardInfo;
    }


    public Set<Annotation> getAnnots() {
        return annot;
    }

    /**
     * retourner l'Annotation si elle est du frame frameName
     *
     * @param framename nom du frame qu'on cherche
     * @return l'annotation ou null
     */
    public Annotation getAnnot(String framename) {
        if (annot == null) {
            return null;
        }
        for (Annotation a : annot) {
            //if (!a.framenet) {
            //return null; // non-frame annotation (role semantique de base
            //}
            if (a.frame.equals(framename)) {
                return a;
            }
        }
        return null;
    }

    public synchronized void addAnnot(Annotation a) {
        if (annot == null) {
            annot = new TreeSet<>();
        }
        annot.add(a);
    }

    public void addAnnots(Set<Annotation> a) {
        if (annot == null) {
            annot = a;
        } else {
            annot.addAll(a);
        }
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /* returns the numerical id (not the part after . or -) */
    public int getId() {
        return id;
    }

    public int getSubid() {
        return subid;
    }


    /* returns the numerical id (uncluding the part after . or -) */
    public String getFullId() {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        switch (toktype) {
            case WORD:
                break;
            case EMPTY:
                sb.append('.').append(subid);
                break;
            case CONTRACTED:
                sb.append('-').append(subid);
        }
        return sb.toString();
    }

    public Tokentype getTokentype() {
        return toktype;
    }

    public void setId(int i) {
        id = i;
    }

    public void setSubId(int i) {
        subid = i;
    }

    public String getForm() {
        return form;
    }

    public boolean hasForm(String f) {
        return form.equals(f);
    }

    public void setForm(String l) {
        this.form = l;
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String l) {
        this.lemma = l;
    }

    public boolean hasLemma(String s) {
        return lemma.equals(s);
    }

    public String getWordcontext() {
        if (wordcontext != null) {
            return wordcontext;
        } else {
            return lemma;
        }
    }

    public void setWordcontext(String wordcontext) {
        this.wordcontext = wordcontext;
    }

    public String getXpostag() {
        return xpostag;
    }

    public void setXpostag(String xpostag) {
        this.xpostag = xpostag;
    }

    public boolean hasXpostag(String s) {
        return xpostag.equals(s);
    }

    public String getUpostag() {
        return upostag;
    }

    public void setUpostag(String c) {
        upostag = c;
    }

    public boolean hasUpostag(String regex) {
        return upostag.equals(regex);
    }

    public boolean matchesXpostag(String regex) {
        return xpostag.matches(regex);
    }

    public boolean matchesUpostag(String regex) {
        return upostag.matches(regex);
    }

    public boolean matchesLemma(String regex) {
        return lemma.matches(regex);
    }

    public boolean matchesField(Fields f, String regex) {
        switch (f) {
            case LEMMA:
                return matchesLemma(regex);
            case UPOS:
                return matchesUpostag(regex);
            case XPOS:
                return matchesXpostag(regex);
            default:
                return false;
        }
    }

    /** return feature map */
    public Map<String, String> getFeatures() {
        return features;
    }

    /** return reafures as UD string */
    public String getFeaturesStr() {
        if (features.isEmpty()) {
            return EmptyColumn;
        }

        StringBuilder sb = new StringBuilder();

        for (String f : features.keySet()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(f).append('=').append(features.get(f));
        }
        return sb.toString();
    }

    /** replace features by other features */
    public void setFeatures(Map<String, String> fs) {
        features = fs;
    }

    /** replace features by other features (in an unparsed UD string */
    public void setFeatures(String unparsed_featsstring) {
        features.clear();

        if (!EmptyColumn.equals(unparsed_featsstring)) {
            String[] elems = unparsed_featsstring.split("[\\|,\n]");
            for (String fv : elems) {
                addFeature(fv);
            }
        }
    }

    // add a unparsed feature=value pair */
    public void addFeature(String fval_unparsed) {
        String[] name_val = fval_unparsed.split("=", 2);
        features.put(name_val[0], name_val.length > 1 ? name_val[1] : null);
    }

    // check whether any fatures are present
    public boolean anyFeatures() {
        return !features.isEmpty();
    }

    // check whether featyre with value is present
    public boolean hasFeature(String name, String val) {
        if (features.isEmpty()) {
            return false;
        }
        String v = features.get(name);
        if (v == null) {
            return false;
        }
        return v.equals(val);
    }

    // check whether features with regex value is present
    public boolean matchesFeatureValue(String name, String valregex) {
        if (features.isEmpty()) {
            return false;
        }
        String v = features.get(name);
        if (v == null) {
            return false;
        }
        return (v.matches(valregex));

    }

    public int getHead() {
        return head;
    }

    public List<EnhancedDeps> getDeps() {
        return deps;
    }

    public void setHead(int head) {
        this.head = head;
    }

    public void setDeps(List<EnhancedDeps> deps) {
        this.deps = deps;
    }

    public void setDeps(String unparsed_enhdepsstring) throws ConllException {
        deps.clear();
        if (unparsed_enhdepsstring.length() > 1) {
            for (String ed : unparsed_enhdepsstring.split("[\\|,\n]")) {
                ConllWord.EnhancedDeps ehd = new ConllWord.EnhancedDeps(ed);
                deps.add(ehd);
            }
        }
    }

    public void addDeps(String headId, String deprel) throws ConllException {
        ConllWord.EnhancedDeps ehd = new ConllWord.EnhancedDeps(headId, deprel);
        deps.add(ehd);
    }

    public boolean delDeps(String headId) {
        for (EnhancedDeps ed : deps) {
            if (ed.getFullHeadId().equals(headId)) {
                deps.remove(ed);
                return true;
            }
        }

        return false;
    }

    public Map<String, Object> getMisc() {
        return misc;
    }

    public String getMiscStr() {
        // TODO rethink
        if (misc == null || misc.isEmpty()) {
            return EmptyColumn;
        }
        List<String> e = new ArrayList<>();
        for (String k : misc.keySet()) {

            e.add(k + "=" + misc.get(k));
        }
        return String.join("|", e);
    }

    public void setMisc(String unparsed_miscstring) {
        spacesAfter = " ";
        misc.clear();
        if (!EmptyColumn.equals(unparsed_miscstring)) {
            String[] fields = unparsed_miscstring.trim().split("[\\|\n]"); // needs \n to split return from the GUI
            for (String f : fields) {
                String[] kv = f.split("=", 2);
                if (kv.length > 1) {
                    if (kv[1].isEmpty()) {
                        //throw new ConllException("empty value in Misc field: " + conllline);
                        misc.put(kv[0], null);
                    } else {
                        if (isPosNumeric(kv[1])) {
                            misc.put(kv[0], Integer.parseInt(kv[1]));
                        } else {
                            misc.put(kv[0], kv[1]);
                            setSpacesAfter(kv[0], kv[1]);
                        }
                    }
                } else {
                    misc.put(kv[0], null);
                }
            }
        }
    }

    /** set the spaces afterthe token, return true, if the key was Space(s)After */
    private boolean setSpacesAfter(String misckey, String miscval) {
        if (misckey.equals("SpaceAfter") && miscval.equals("No")) {
            spacesAfter = "";
            return true;
        } else if (misckey.equals("SpacesAfter")) {
            spacesAfter = miscval.replace("\\s", " ").replace("\\t", "\t").replace("\\n", "\n");
            return true;
        }
        return false;
    }

    public void setMisc(Map<String, Object> misc) {
        this.misc = misc;
        spacesAfter = " ";
        for (Map.Entry<String, Object> pair : misc.entrySet()) {
            if (pair.getValue() instanceof String) {
                boolean rtc = setSpacesAfter(pair.getKey(), (String) pair.getValue());
                if (rtc) {
                    break; // spaceafter found
                }
            }
        }
    }

    public boolean addMisc(String key, String val) {
        boolean prexists = misc.containsKey(key);
        misc.put(key, val);
        setSpacesAfter(key, val);
        return prexists;
    }

    public boolean addMisc(String key, Integer val) {
        boolean prexists = misc.containsKey(key);
        misc.put(key, val);
        return prexists;
    }

    String getSpacesAfter() {
        return spacesAfter;
    }

    public ConllWord getHeadWord() {
        return headWord;
    }

    public void setHeadWord(ConllWord headWord) {
        this.headWord = headWord;
    }

    public String getDeplabel() {
        return deplabel;
    }

    public void setDeplabel(String l) {
        this.deplabel = l;
    }

    public boolean hasDeplabel(String s) {
        return deplabel.equals(s);
    }

    public boolean matchesDeplabel(String d) {
        return deplabel.matches(d);
    }

    /** checks whether you can go up and down from the current word to another
         by following the relations and directions
    @param rels
    @param direction
    @return
     */
    public boolean matchesTree(int start, String[] rels, String[] direction, Set<Integer>toHighlight) {
        ConllWord head;
        //System.err.println("MT " + start);
        //System.err.println("  RL " + String.join(",", rels));
        //System.err.println("  UD " + String.join(",", direction));
        for (int r = start; r < rels.length; ++r) {
            if (direction[r].equals(">")) {
                // check whether head matches
                head = this.getHeadWord();
                if (head == null || !head.matchesDeplabel(rels[r])) {
                    return false;
                }
                //System.err.println("2=============" + start + "  " + head.getId());
                toHighlight.add(head.getId());
                return head.matchesTree(r+1, rels, direction, toHighlight);
            } else {
                List<ConllWord> deps;
                if (direction[r].equals("=")) {
                    // check whether there is a sibling
                    head = this.getHeadWord();
                    deps = head.getDWordsRE(rels[r], false);
                } else {
                    // check whether child matches ("<")
                    deps = this.getDWordsRE(rels[r], false);
                }
                if (deps == null || deps.isEmpty()) {
                    //System.err.println("no deps");
                    return false;
                }
                boolean ok = false;
                for (ConllWord dep : deps) {
                    if (dep.equals(this)) continue;
                    //System.err.println("DEP " + r + " " + dep);
                    if (dep.matchesDeplabel(rels[r])) {
                        boolean rtc = dep.matchesTree(r+1, rels, direction, toHighlight);
                        ok = ok || rtc;
                        if (rtc) {
                            //System.err.println("3=============" + start + "  " + this.getId());
                            toHighlight.add(this.getId());
                        }
                    }
                }
                if (ok) {
                    //System.err.println("1=============" + start + "  " + this.getId());
                    toHighlight.add(this.getId());
                }
                return ok;
            }
        }
        toHighlight.add(this.getId());
        //System.err.println("==============" + start + "  " + this.getId());

        return true;
    }

    public List<ConllWord> getDependents() {
        return dependents;
    }

//    public List<ConllWord> getEnhanceddeps() {
//        return enhanceddeps;
//    }
    public Map<Integer, ConllWord> getDependentsMap() {
        return depmap;
    }

    /**
     * returns true if dep depends directly or indirectly from this
     *
     * @param dep depdendant word
     * @return vrai si dep est un dépendant (direct ou indirect)
     */
    public boolean commands(ConllWord dep) {
        for (ConllWord child : dependents) {
            if (dep == child) {
                //System.err.println(dep.getId() + " commands " + child.getId());
                return true;
            }
            boolean rtc = child.commands(dep);
            if (rtc) {
                //System.err.println(dep.getId() + " commands " + child.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * get direct and indirect heads from this to head (including head). If this
     * is not commanded by head, return null
     *
     * @param head
     * @return
     */
    public List<ConllWord> getAllHeads(ConllWord head) {
        List<ConllWord> res = new ArrayList<>();
        ConllWord child = this;
        while (child != null) {
            child = child.getHeadWord();

            res.add(child);
            if (child == head) {
                break;
            }
        }

        if (res.isEmpty()) {
            return null;
        } else {
            return res;
        }
    }

    /**
     * next call to toString() prefixes columns cut off with shift parameter in
     * Constructor
     */
    public void nextToStringComplete() {
        nextToStringcomplete = true;
    }

    /**
     * return this, dependents and their dependents (top to bottom)
     *
     * @return
     */
    public List<ConllWord> getSubTree() {
        List<ConllWord> dependencies = new ArrayList<>();
        //System.err.println("Adding HEad" + this.getId());
        dependencies.add(this);
        for (ConllWord w : dependents) {
            dependencies.addAll(w.getSubTree());

        }
        return dependencies;
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public class SortIgnoreCase implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            return s1.toLowerCase().compareTo(s2.toLowerCase());
        }
    }

    public String toString(boolean withEnhancedDeps) {
        StringBuilder sb = new StringBuilder();
        if (nextToStringcomplete && prefixed != null) {
            for (String c : prefixed) {
                sb.append(c).append("\t");
            }
            nextToStringcomplete = false;
        }

        sb.append(getFullId());
        sb.append("\t").append(form);
        if (toktype == Tokentype.CONTRACTED) {
            sb.append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append('\t').append(EmptyColumn)
                    .append("\t").append(getMiscStr());
        } else {
            sb.append("\t").append(lemma);
            sb.append("\t").append(upostag);
            sb.append("\t").append(xpostag);

            sb.append('\t').append(getFeaturesStr());
//            if (!features.isEmpty()) {
//                //Collections.sort(features, new SortIgnoreCase());
//                sb.append("\t").append(features.get(0));
//                for (int i = 1; i < features.size(); ++i) {
//                    sb.append("|").append(features.get(i));
//                }
//            } else {
//                sb.append('\t').append(EmptyColumn);
//            }

            if (toktype != Tokentype.WORD) {
                sb.append('\t').append(EmptyColumn).append('\t').append(EmptyColumn);
            } else if (head == -1) {
                sb.append('\t').append(EmptyColumn).append('\t').append(EmptyColumn);
            } else {
                sb.append("\t").append(head);
                if (head == 0) {
                    deplabel = "root";
                }
                sb.append("\t").append(deplabel);
            }
            if (withEnhancedDeps) {
                if (deps.isEmpty()/* && !basicdeps_in_ed_column*/) {
                    sb.append('\t').append(EmptyColumn);
                } else {
                    List<String> els = new ArrayList<>();
//                    // we do not store basic dep in deps field. Just put it there for conllu output
//                    if (toktype == Tokentype.WORD) {
//                        els.add(head + ":" + deplabel);
//                    }

                    for (int i = 0; i < deps.size(); ++i) {
                        els.add(deps.get(i).toString());
                    }

                    sb.append('\t').append(String.join("|", els));
                }
            } else {
                sb.append('\t').append(EmptyColumn);
            }

            sb.append("\t").append(getMiscStr());

            if (annot != null) {
                for (Annotation a : annot) {
                    sb.append('\t').append(a);
                }
            } else if (nonstandardInfo != null) {
                for (String i : nonstandardInfo) {
                    sb.append('\t').append(i);
                }
            }
        }
        return sb.toString();
    }

    public int getArc_height() {
        return arc_height;
    }

    public void setArc_height(int arc_height) {
        this.arc_height = arc_height;
    }

    private boolean isPosNumeric(String str) {
        for (char c : str.toCharArray()) {
            int cp = (int) c;
            if (cp < 48 || cp > 57) {
                //if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /** enhanced deps are in the 9th column of a CoNLL-U file.
     */
    public class EnhancedDeps {
        int headid;
        int headsubid;
        String deprel;
        ConllWord headword;

        public EnhancedDeps(EnhancedDeps orig) {
            this.headid = orig.headid;
            this.headsubid = orig.headsubid;
            this.deprel = orig.deprel;
        }

        public EnhancedDeps(String head, String deprel) {
            init(head, deprel);
        }

        private void init(String head, String deprel) {
            String[] elems = head.split("\\.");
            this.headid = Integer.parseInt(elems[0]);
            if (elems.length > 1) {
                this.headsubid = Integer.parseInt(elems[1]);
            } else {
                this.headsubid = 0;
            }
            this.deprel = deprel;
        }

        public EnhancedDeps(String ed) throws ConllException {
            String[] elems = ed.split(":", 2);
            if (elems.length != 2) {
                throw new ConllException("Invalid enhanced exception " + ed);
            }
            init(elems[0], elems[1]);
        }

        public String getFullHeadId() {
            if (headsubid != 0) {
                return String.format("%d.%d", headid, headsubid);
            } else {
                return Integer.toString(headid);
            }
        }

        public String toString() {
            return getFullHeadId() + ":" + deprel;
        }
    }
}
