/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2022, Orange S.A.

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

import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;


/**
 * compare sentences in CoNLL-U files. find strictly identical sentences (words,
 * lemmas, UPOS, deprels or similar sentences up to a maximal Levenshtein
 * distance
 *
 * @author Johannes Heinecke
 */
public class ConlluComparator {

    private Map<String, Signatures> csents; // unique id (filename#id#number): sentence
    private int numberOfThreads;

    public ConlluComparator(List<? extends Object> objects, int numberOfThreads) throws ConllException, IOException {
        //public ConlluComparator(List<String> objects) throws ConllException, IOException {
        if (objects.isEmpty()) {
            throw new ConllException("No CoNLL-U files/objects given");
        }
        List<ConllFile> cdocs = new ArrayList<>();
        if (objects.get(0) instanceof ConllFile) {

            for (Object o : objects) {
                cdocs.add((ConllFile) o);
            }

        } else if (objects.get(0) instanceof String) {
            for (Object o : objects) {
                cdocs.add(new ConllFile(new File((String) o), false, false));
            }
        }

        this.numberOfThreads = numberOfThreads;
        csents = new LinkedHashMap<>();
        int ct = 0;
        for (ConllFile cf : cdocs) {
            for (ConllSentence csent : cf.getSentences()) {
                ct += 1;
                String id = String.format("%s#%s#%d", cf.getFile(), csent.getSentid(), ct);
                csents.put(id, new Signatures(csent, id));
            }
        }
    }

    /**
     * finds identical/similar sentences. comparing all sentences with all
     * others; $\sum_{i=0}^{i=n} i$ comparisons needed
     *
     * @param form: 0: identical, >0 maximal Levenshtein-Damerau distance on entire sentence (charater level)
     * @param lemma: 0: identical, >0 maximal Levenshtein-Damerau distance on lemmas (token level)
     */
    public void analyse(int form, int lemma, int upos, int xpos, int feats, int deprel) throws InterruptedException {
        List<String> keys = Arrays.asList(csents.keySet().toArray(new String[0]));
      
        List<Thread> thrs = new ArrayList<>();

        for (int th = 0; th < numberOfThreads; ++th) {
            Analyser a = new Analyser(th, numberOfThreads, keys, csents, form, lemma, upos, xpos, feats, deprel);
            Thread thr = new Thread(a);
            thr.start();
            thrs.add(thr);
        }
        
        for(Thread thr : thrs) {
            thr.join();
        }
         
    }
//    public void ooanalyse(int form, int lemma, int upos, int xpos, int feats, int deprel) {
//
//        List<String> keys = Arrays.asList(csents.keySet().toArray(new String[0]));
//        int len = keys.size();
//        for (int i = 0; i < len; ++i) {
//            System.err.println("Checking " + i);
//            Signatures cursent = csents.get(keys.get(i));
//            for (int j = i + 1; j < len; ++j) {
//                //System.err.println("comparing " + i + " " + j);
//                Signatures othersent = csents.get(keys.get(j));
//                // compare
//                if (form == 0) {
//                    boolean rtc = cursent.sent.equals(othersent.sent);
//                    if (rtc) {
//                        identical("FORM", cursent, othersent);
//                    }
//                } else if (form > 0) {
//                    int dist = calculateDistance(cursent.sentence, othersent.sentence);
//                    if (dist == 0) {
//                        identical("FORM", cursent, othersent);
//                    } else if (dist <= form) {
//                        similar("FORM", dist, cursent, othersent);
//                    }
//                }
//
//                if (lemma == 0) {
//                    boolean rtc = cursent.lemmas.equals(othersent.lemmas);
//                    if (rtc) {
//                        identical("LEMMA", cursent, othersent);
//                    }
//                } else if (lemma > 0) {
//                    int dist = calculateDistance(cursent.lemmas, othersent.lemmas);
//                    //System.err.println("ZZZZZ " + dist + "\n" + cursent.lemmas + "\n"+ othersent.lemmas);
//                    if (dist == 0) {
//                        identical("LEMMA", cursent, othersent);
//                    } else if (dist <= lemma) {
//                        similar("LEMMA", dist, cursent, othersent);
//                    }
//                }
//
//                if (upos == 0) {
//                    boolean rtc = cursent.uposs.equals(othersent.uposs);
//                    if (rtc) {
//                        identical("UPOS", cursent, othersent);
//                    }
//                } else if (upos > 0) {
//                    int dist = calculateDistance(cursent.uposs, othersent.uposs);
//                    //System.err.println("ZZZZZ " + dist + "\n" + cursent.lemmas + "\n"+ othersent.lemmas);
//                    if (dist == 0) {
//                        identical("UPOS", cursent, othersent);
//                    } else if (dist <= upos) {
//                        similar("UPOS", dist, cursent, othersent);
//                    }
//                }
//
//                if (xpos == 0) {
//                    boolean rtc = cursent.xposs.equals(othersent.xposs);
//                    if (rtc) {
//                        identical("XPOS", cursent, othersent);
//                    }
//                } else if (xpos > 0) {
//                    int dist = calculateDistance(cursent.xposs, othersent.xposs);
//                    if (dist == 0) {
//                        identical("XPOS", cursent, othersent);
//                    } else if (dist <= xpos) {
//                        similar("XPOS", dist, cursent, othersent);
//                    }
//                }
//
//
//                if (feats == 0) {
//                    boolean rtc = cursent.feats.equals(othersent.feats);
//                    if (rtc) {
//                        identical("FEATS", cursent, othersent);
//                    }
//                } else if (feats > 0) {
//                    int dist = calculateDistance(cursent.feats, othersent.feats);
//                    if (dist == 0) {
//                        identical("FEATS", cursent, othersent);
//                    } else if (dist <= feats) {
//                        similar("FEATS", dist, cursent, othersent);
//                    }
//                }
//
//
//                if (deprel == 0) {
//                    boolean rtc = cursent.deprels.equals(othersent.deprels);
//                    if (rtc) {
//                        identical("DEPREL", cursent, othersent);
//                    }
//                } else if (deprel > 0) {
//                    int dist = calculateDistance(cursent.deprels, othersent.deprels);
//                    if (dist == 0) {
//                        identical("DEPREL", cursent, othersent);
//                    } else if (dist <= deprel) {
//                        similar("DEPREL", dist, cursent, othersent);
//                    }
//                }
//            }
//        }
//    }
//
//    private void identical(String column, Signatures s1, Signatures s2) {
//        System.err.format("%s identical\t%s\t%s\n", column, s1.id, s2.id);
//        System.err.format("# %s\n", s1.sent);
//        if (column.equals("LEMMA")) {
//            System.err.format("# %s\n", s1.lemmas);
//        }
//        else if (column.equals("UPOS")) {
//            System.err.format("# %s\n", s1.uposs);
//        }
//
//    }
//
//    private void similar(String column, int dist, Signatures s1, Signatures s2) {
//        System.err.format("%s similar %d\t%s\t%s\n", column, dist, s1.id, s2.id);
//        System.err.format("# %s\n", s1.sent);
//        System.err.format("# %s\n", s2.sent);
//        if (column.equals("LEMMA")) {
//            System.err.format("# %s\n", s1.lemmas);
//            System.err.format("# %s\n", s2.lemmas);
//        } else if (column.equals("UPOS")) {
//            System.err.format("# %s\n", s1.uposs);
//            System.err.format("# %s\n", s2.uposs);
//        }
//
//    }
//
//    // inspired by https://github.com/crwohlfeil/damerau-levenshtein
//    /**
//     * calculate the levenshtein-damerau distance between two lists of objects (characters or strings)
//     * @param source 
//     * @param target
//     * @return 
//     */
//    private int calculateDistance(List<? extends Object> source, List<? extends Object> target) {
//        //if (source == null || target == null) {
//        //    throw new IllegalArgumentException("Parameter must not be null");
//        //}
//        int sourceLength = source.size();
//        int targetLength = target.size();
//        if (sourceLength == 0) {
//            return targetLength;
//        }
//        if (targetLength == 0) {
//            return sourceLength;
//        }
//        int[][] dist = new int[sourceLength + 1][targetLength + 1];
//        for (int i = 0; i < sourceLength + 1; i++) {
//            dist[i][0] = i;
//        }
//        for (int j = 0; j < targetLength + 1; j++) {
//            dist[0][j] = j;
//        }
//        for (int i = 1; i < sourceLength + 1; i++) {
//            for (int j = 1; j < targetLength + 1; j++) {
//                int cost = source.get(i - 1).equals(target.get(j - 1)) ? 0 : 1;
//                dist[i][j] = Math.min(Math.min(dist[i - 1][j] + 1, dist[i][j - 1] + 1), dist[i - 1][j - 1] + cost);
//                if (i > 1
//                        && j > 1
//                        && source.get(i - 1).equals(target.get(j - 2))
//                        && source.get(i - 2).equals(target.get(j - 1))) {
//                    dist[i][j] = Math.min(dist[i][j], dist[i - 2][j - 2] + cost);
//                }
//            }
//        }
//        return dist[sourceLength][targetLength];
//    }

    class Signatures {

        public ConllSentence cs;
        public String sent;
        public List<Character> sentence;
        //public List<String> forms;
        public List<String> lemmas;
        public List<String> uposs;
        public List<String> xposs;
        public List<String> deprels;
        public List<String> feats;
        public String id;

        public Signatures(ConllSentence cs, String id) {
            this.cs = cs;
            this.id = id;
            //forms = new ArrayList<>();
            lemmas = new ArrayList<>();
            uposs = new ArrayList<>();
            xposs = new ArrayList<>();
            deprels = new ArrayList<>();
            feats = new ArrayList<>();
            sent = cs.getSentence();
            sentence = new ArrayList<>();
            for (char c : sent.toCharArray()) {
                sentence.add(c);
            }
            for (ConllWord cw : cs.getAllWords()) {
                //forms.add(cw.getForm());
                if (cw.getTokentype() != ConllWord.Tokentype.CONTRACTED) {
                    lemmas.add(cw.getLemma());
                    uposs.add(cw.getLemma());
                    xposs.add(cw.getLemma());
                    deprels.add(cw.getLemma());
                    feats.add(cw.getFeaturesStr());
                }
            }
        }
    }

    public static void main(String args[]) {
        if (args.length > 2) {
            try {
                int numberOfThreads = Integer.parseInt(args[0]);
                String[] maxdist = args[1].split(":");
                int forms = Integer.parseInt(maxdist[0]);
                int lemmas = -1;
                int upos = -1;
                int xpos = -1;
                int feats = -1;
                int deprels = -1;
                if (maxdist.length > 1) {
                    lemmas = Integer.parseInt(maxdist[1]);
                    if (maxdist.length > 2) {
                        upos = Integer.parseInt(maxdist[2]);
                        if (maxdist.length > 3) {
                            xpos = Integer.parseInt(maxdist[3]);
                            if (maxdist.length > 4) {
                                feats = Integer.parseInt(maxdist[4]);
                                if (maxdist.length > 5) {
                                    deprels = Integer.parseInt(maxdist[5]);
                                }
                            }
                        }
                    }
                }
                List<String>argl = new ArrayList<>(Arrays.asList(args));
                argl.remove(0);
                argl.remove(0);                
                ConlluComparator cc = new ConlluComparator(argl, numberOfThreads);

                cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("ERROR: " + ex.getMessage());

            }
        } else {
            System.err.println("usage: ConlluComparator numberOfThreads forms:lemma:upos:xpos:feats:deprel  conllu-files");
        }
    }

}
