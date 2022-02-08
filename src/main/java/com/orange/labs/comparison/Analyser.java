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
 @version 2.15.0 as of 6th February 2022
 */
package com.orange.labs.comparison;

import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Johannes Heinecke
 */
public class Analyser implements Runnable {
    private int modulo; // current thread number
    private int totalthreads; // number of threads used (here we process one out of totalthread
    private List<String> keys;
    private int len; // number of sentences
    private int form;
    private int lemma;
    private int upos;
    private int xpos;
    private int feats;
    private int deprel;
    Map<String, ConlluComparator.Signatures> csents; // unique id (filename#id#number): sentence
    //Map<String, List<ConlluComparator.Result>> results; // store similar sentences: {id1: [(id2,dit)]}
    List<String []> results; // store similar sentences: "column <TAB> dist <TAB> id1 <TAB> id2"
    private boolean aggregate; // collect results and return

    public Analyser(int modulo, int totalthreads, List<String> keys, Map<String, ConlluComparator.Signatures> csents,
            int form, int lemma, int upos, int xpos, int feats, int deprel) {
        System.err.println("Create Analyser " + modulo);
        this.modulo = modulo;
        this.totalthreads = totalthreads;
        this.keys = keys;
        this.len = keys.size();
        this.csents = csents;
        this.form = form;
        this.lemma = lemma;
        this.upos = upos;
        this.xpos = xpos;
        this.feats = feats;
        this.deprel = deprel;
        aggregate = true;
        if (aggregate) {
            results = new ArrayList<>();
        }
    }

    @Override
    public void run() {
        for (int i = 0; i < len; ++i) {
            if (modulo == 0 && i % 7 == 0) {
                System.err.format("Checking %d/%d\r", i, len);
            }
            ConlluComparator.Signatures cursent = csents.get(keys.get(i));
            for (int j = i + 1; j < len; ++j) {
                if (j % totalthreads != modulo) {
                    continue; // do only one sentence out of totalthreads in this thread
                }
                //System.err.println("comparing " + i + " " + j);
                ConlluComparator.Signatures othersent = csents.get(keys.get(j));

                if (form == 0) {
                    boolean rtc = cursent.sent.equals(othersent.sent);
                    if (rtc) {
                        identical("FORM", cursent, othersent);
                    }
                } else if (form > 0) {
                    int dist = calculateDistance(cursent.sentence, othersent.sentence, form);
                    if (dist == 0) {
                        identical("FORM", cursent, othersent);
                    } else  if (dist <= form) {
                        similar("FORM", dist, cursent, othersent);
                    }
                }

                if (lemma == 0) {
                    boolean rtc = cursent.lemmas.equals(othersent.lemmas);
                    if (rtc) {
                        identical("LEMMA", cursent, othersent);
                    }
                } else if (lemma > 0) {
                    int dist = calculateDistance(cursent.lemmas, othersent.lemmas, lemma);
                    if (dist == 0) {
                        identical("LEMMA", cursent, othersent);
                    } else if (dist <= lemma) {
                        similar("LEMMA", dist, cursent, othersent);
                    }
                }

                if (upos == 0) {
                    boolean rtc = cursent.uposs.equals(othersent.uposs);
                    if (rtc) {
                        identical("UPOS", cursent, othersent);
                    }
                } else if (upos > 0) {
                    int dist = calculateDistance(cursent.uposs, othersent.uposs, upos);
                    if (dist == 0) {
                        identical("UPOS", cursent, othersent);
                    } else if (dist <= upos) {
                        similar("UPOS", dist, cursent, othersent);
                    }
                }

                if (xpos == 0) {
                    boolean rtc = cursent.xposs.equals(othersent.xposs);
                    if (rtc) {
                        identical("XPOS", cursent, othersent);
                    }
                } else if (xpos > 0) {
                    int dist = calculateDistance(cursent.xposs, othersent.xposs, xpos);
                    if (dist == 0) {
                        identical("XPOS", cursent, othersent);
                    } else if (dist <= xpos) {
                        similar("XPOS", dist, cursent, othersent);
                    }
                }

                if (feats == 0) {
                    boolean rtc = cursent.feats.equals(othersent.feats);
                    if (rtc) {
                        identical("FEATS", cursent, othersent);
                    }
                } else if (feats > 0) {
                    int dist = calculateDistance(cursent.feats, othersent.feats, feats);
                    if (dist == 0) {
                        identical("FEATS", cursent, othersent);
                    } else if (dist <= feats) {
                        similar("FEATS", dist, cursent, othersent);
                    }
                }

                if (deprel == 0) {
                    boolean rtc = cursent.deprels.equals(othersent.deprels);
                    if (rtc) {
                        identical("DEPREL", cursent, othersent);
                    }
                } else if (deprel > 0) {
                    int dist = calculateDistance(cursent.deprels, othersent.deprels, deprel);
                    if (dist == 0) {
                        identical("DEPREL", cursent, othersent);
                    } else if (dist <= deprel) {
                        similar("DEPREL", dist, cursent, othersent);
                    }
                }
            }
        }
        if (modulo == 0) {
            System.err.format("Checked %d      \n", len);
        }
    }

    // inspired by https://github.com/crwohlfeil/damerau-levenshtein
    /**
     * calculate the levenshtein-damerau distance between two lists of objects (characters or strings)
     * levenstein_distance(a,b) >= |len(a) - len(b)|
     * @param source
     * @param target
     * @return
     */
    private int calculateDistance(List<? extends Object> source, List<? extends Object> target, int maxdist) {
        //if (source == null || target == null) {
        //    throw new IllegalArgumentException("Parameter must not be null");
        //}
        int sourceLength = source.size();
        int targetLength = target.size();
        // if the length of the two sentences differs more than maxdist, we stop here
        if (abs(sourceLength - targetLength) > maxdist) return abs(sourceLength - targetLength);

        if (sourceLength == 0) {
            return targetLength;
        }
        if (targetLength == 0) {
            return sourceLength;
        }
        int[][] dist = new int[sourceLength + 1][targetLength + 1];
        for (int i = 0; i < sourceLength + 1; i++) {
            dist[i][0] = i;
        }
        for (int j = 0; j < targetLength + 1; j++) {
            dist[0][j] = j;
        }
        for (int i = 1; i < sourceLength + 1; i++) {
            for (int j = 1; j < targetLength + 1; j++) {
                int cost = source.get(i - 1).equals(target.get(j - 1)) ? 0 : 1;
                dist[i][j] = Math.min(Math.min(dist[i - 1][j] + 1, dist[i][j - 1] + 1), dist[i - 1][j - 1] + cost);
                if (i > 1
                        && j > 1
                        && source.get(i - 1).equals(target.get(j - 2))
                        && source.get(i - 2).equals(target.get(j - 1))) {
                    dist[i][j] = Math.min(dist[i][j], dist[i - 2][j - 2] + cost);
                }
            }
        }
        return dist[sourceLength][targetLength];
    }

    public List<String []> getResults() {
        return results;
    }
    
    
    private void identical(String column, ConlluComparator.Signatures s1, ConlluComparator.Signatures s2) {
        if (aggregate) {
            //results.add(String.format("%s\t0\t%s\t%s\t%s", column, s1.id, s2.id, s1.sent));
            String [] e = { column, "0", s1.id, s2.id, s1.sent};
            results.add(e);
        } else {
            System.out.format("%s identical\t%s\t%s\n", column, s1.id, s2.id);
            System.out.format("# %s\n", s1.sent);
            printColumn(column, s1);
        }
    }

    private void similar(String column, int dist, ConlluComparator.Signatures s1, ConlluComparator.Signatures s2) {
        if (aggregate) {
            //results.add(String.format("%s\t%d\t%s\t%s\t%s\t%s", column, dist, s1.id, s2.id, s1.sent, s2.sent));
            String [] e = {column, ""+dist, s1.id, s2.id, s1.sent, s2.sent};
            results.add(e);
        } else {
            System.out.format("%s similar %d\t%s\t%s\n", column, dist, s1.id, s2.id);
            System.out.format("# %s\n", s1.sent);
            printColumn(column, s1);
            System.out.format("# %s\n", s2.sent);
            printColumn(column, s2);
        }
    }


    private void printColumn(String column, ConlluComparator.Signatures sig) {
        if (column.equals("LEMMA")) {
            System.out.format("# Lemmas %s\n", String.join("\t", sig.lemmas));
        } else if (column.equals("UPOS")) {
            System.out.format("# Upos %s\n", String.join("\t", sig.uposs));
        } else if (column.equals("XPOS")) {
            System.out.format("# Xpos %s\n", String.join("\t", sig.xposs));
        } else if (column.equals("FEATS")) {
            System.out.format("# Feats %s\n", String.join("\t", sig.feats));
        } else if (column.equals("DEPREL")) {
            System.out.format("# Deprel %s\n", String.join("\t", sig.deprels));
        }
    }


}
