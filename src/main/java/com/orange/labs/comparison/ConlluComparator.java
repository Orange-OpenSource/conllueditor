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
 @version 2.19.5 as of 13th December 2022
 */
package com.orange.labs.comparison;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * compare sentences in CoNLL-U files. find strictly identical sentences (words,
 * lemmas, UPOS, deprels or similar sentences up to a maximal Levenshtein
 * distance
 *
 * @author Johannes Heinecke
 */
public class ConlluComparator {

    // group1
    private Map<String, Signatures> csents; // unique id (filename#id#number): sentence
    Map<Integer, Integer> sentencelengths;  // sentencelength: number of sentences

    // group2
    private Map<String, Signatures> csents2; // unique id (filename#id#number): sentence
    Map<Integer, Integer> sentencelengths2;  // sentencelength: number of sentences

    private int numberOfThreads;

    public ConlluComparator(List<? extends Object> objects,
            List<? extends Object> objects2, // if present find identical/similar sentences only if in both groups
            int numberOfThreads) throws ConllException, IOException {
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
        } else if (objects.get(0) instanceof File) {
            for (Object o : objects) {
                cdocs.add(new ConllFile((File) o, false, false));
            }
        }

        List<ConllFile> cdocs2;
        if (objects2 != null) {
            cdocs2 = new ArrayList<>();
            if (objects2.get(0) instanceof ConllFile) {
                for (Object o : objects2) {
                    cdocs2.add((ConllFile) o);
                }
            } else if (objects2.get(0) instanceof String) {
                for (Object o : objects2) {
                    cdocs2.add(new ConllFile(new File((String) o), false, false));
                }
            } else if (objects2.get(0) instanceof File) {
                for (Object o : objects2) {
                    cdocs2.add(new ConllFile((File) o, false, false));
                }
            }
        } else {
            cdocs2 = cdocs;
        }

        this.numberOfThreads = numberOfThreads;
        csents = new LinkedHashMap<>();
        sentencelengths = new TreeMap<>(); // sentencelength: number of sentences
        //int ct = 0;
        for (ConllFile cf : cdocs) {
            for (ConllSentence csent : cf.getSentences()) {
                //ct += 1;
                //String id = String.format("%s#%s#%d", cf.getFile(), csent.getSentid(), ct);
                String[] elems = cf.getFile().toString().split(File.separator);
                String basename = elems[elems.length - 1];
                String id = String.format("%s#%s", basename, csent.getSentid());
                csents.put(id, new Signatures(csent, id));
                int tokens = csent.getAllWords().size();
                Integer occ = sentencelengths.get(tokens);
                if (occ == null) {
                    sentencelengths.put(tokens, 1);
                } else {
                    sentencelengths.put(tokens, occ + 1);
                }

            }
        }
        if (objects2 != null) {
            csents2 = new LinkedHashMap<>();
            sentencelengths2 = new TreeMap<>(); // sentencelength: number of sentences
            //ct = 0;
            for (ConllFile cf : cdocs2) {
                for (ConllSentence csent : cf.getSentences()) {
                    //ct += 1;
                    String[] elems = cf.getFile().toString().split(File.separator);
                    String basename = elems[elems.length - 1];
                    //String id = String.format("%s#%s#%d", cf.getFile(), csent.getSentid(), ct);
                    String id = String.format("%s#%s", basename, csent.getSentid());
                    csents2.put(id, new Signatures(csent, id));
                    int tokens = csent.getAllWords().size();
                    Integer occ = sentencelengths2.get(tokens);
                    if (occ == null) {
                        sentencelengths2.put(tokens, 1);
                    } else {
                        sentencelengths2.put(tokens, occ + 1);
                    }

                }
            }
        } else {
            csents2 = csents;
            sentencelengths2 = sentencelengths;
        }

    }

    /**
     * finds identical/similar sentences. comparing all sentences with all
     * others; $\sum_{i=1}^{i=n-1} i$ comparisons needed
     *
     * @param form: 0: identical, >0 maximal Levenshtein-Damerau distance on
     * entire sentence (charater level)
     * @param lemma: 0: identical, >0 maximal Levenshtein-Damerau distance on
     * lemmas (token level)
     */
    public String analyse(int form, int lemma, int upos, int xpos, int feats, int deprel, boolean json) throws InterruptedException {
        List<String> keys = Arrays.asList(csents.keySet().toArray(new String[0]));
        List<String> keys2 = Arrays.asList(csents2.keySet().toArray(new String[0]));
        long comps = 0;
        for (int x = 1; x < keys.size(); ++x) {
            comps += x;
        }
        System.err.println(comps + " comparisons needed");
        List<Thread> thrs = new ArrayList<>();
        List<Analyser> analysers = new ArrayList<>();

        for (int th = 0; th < numberOfThreads; ++th) {
            Analyser a = new Analyser(th, numberOfThreads,
                    keys, csents, keys2, csents2,
                    form, lemma, upos, xpos, feats, deprel);
            Thread thr = new Thread(a);
            thr.start();
            thrs.add(thr);
            analysers.add(a);
        }

        for (Thread thr : thrs) {
            thr.join();
        }

        StringBuilder out = new StringBuilder();

        if (json) {
            JsonObject main = new JsonObject();
            for (Analyser a : analysers) {
                for (String[] elems : a.getResults()) {
                    JsonObject sentobject;

                    if (main.has(elems[2])) {
                        sentobject = main.getAsJsonObject(elems[2]);
                    } else {
                        sentobject = new JsonObject();
                        main.add(elems[2], sentobject);
                    }

                    if (!elems[0].equals("FORM")) {
                        String col = elems[0].toLowerCase();
                        if (!sentobject.has(col)) {
                            JsonArray fields = new JsonArray();
                            String fieldSep = "\\|";
                            if ("FEATS".equals(elems[0])) {
                                fieldSep = ",";
                            }
                            for (String e : elems[4].split(fieldSep)) {
                                fields.add(e);
                            }
                            sentobject.add(col, fields);
                        }
                    } else {
                        sentobject.addProperty("text", elems[4]);
                    }

                    JsonObject colobject;
                    if (!sentobject.has(elems[0])) {
                        colobject = new JsonObject();
                        sentobject.add(elems[0], colobject);
                    } else {
                        colobject = sentobject.getAsJsonObject(elems[0]);
                    }
                    if (colobject.has(elems[1])) {
                        if ("0".equals(elems[1])) {
                            colobject.getAsJsonObject(elems[1]).addProperty(elems[3], "");
                        } else {
                            if ("FORM".equals(elems[0])) {
                                colobject.getAsJsonObject(elems[1]).addProperty(elems[3], elems[5]);
                            } else {
                                JsonArray fields = new JsonArray();
                                String fieldSep = "\\|";
                                if ("FEATS".equals(elems[0])) {
                                    fieldSep = ",";
                                }
                                for (String e : elems[5].split(fieldSep)) {
                                    fields.add(e);
                                }
                                colobject.getAsJsonObject(elems[1]).add(elems[3], fields);
                            }
                        }
                    } else {
                        JsonObject idlist = new JsonObject();
                        if ("0".equals(elems[1])) {
                            idlist.addProperty(elems[3], "");
                        } else {
                            if ("FORM".equals(elems[0])) {
                                idlist.addProperty(elems[3], elems[5]);
                            } else {
                                JsonArray fields = new JsonArray();
                                String fieldSep = "\\|";
                                if ("FEATS".equals(elems[0])) {
                                    fieldSep = ",";
                                }
                                for (String e : elems[5].split(fieldSep)) {
                                    fields.add(e);
                                }
                                idlist.add(elems[3], fields);
                            }
                        }
                        colobject.add(elems[1], idlist);
                    }
                }
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(main);
            //return main.toString();
        } else {
            // text (TSV) output
            Map<String, Set<String>> identical = new LinkedHashMap<>(); // sentence: [ids]
            List<String[]> similar = new ArrayList<>();
            // aggregate identical

            for (Analyser a : analysers) {
                for (String[] elems : a.getResults()) {
                    if (elems[0].equals("FORM") && elems[1].equals("0")) {
                        Set<String> ids = identical.get(elems[4]);
                        if (ids == null) {
                            ids = new LinkedHashSet<>();
                            identical.put(elems[4], ids);
                        }
                        ids.add(elems[2]);
                        ids.add(elems[3]);
                    } else {
                        similar.add(elems);
                    }
                }
            }

            out.append("# sentence lengths (group1)\n");
            for (int slen : sentencelengths.keySet()) {
                out.append(String.format("#  %3d tokens: %4d sentences\n", slen, sentencelengths.get(slen)));
            }
            if (sentencelengths != sentencelengths2) {
                out.append("# sentence lengths (group2)\n");
                for (int slen : sentencelengths2.keySet()) {
                    out.append(String.format("#  %3d tokens: %4d sentences\n", slen, sentencelengths2.get(slen)));
                }
            }

            if (form >= 0) {
                int identical_form = 0;
                for (String sentence : identical.keySet()) {
                    identical_form += identical.get(sentence).size();
                }
                out.append(String.format(Locale.ROOT, "# identical sentences (Form) %d/%d  %.1f%%\n", identical_form, keys.size(), (100.0 * identical_form / keys.size())));
            }

            // output identical sentences
            for (String sentence : identical.keySet()) {
                out.append(String.format("FORM\t0\t%s\t%d\t%s\n", sentence, identical.get(sentence).size(), String.join("\t", identical.get(sentence))));
            }
            for (String[] sim : similar) {
                out.append(String.join("\t", sim)).append('\n');
            }
        }

        return out.toString();
    }

    /**
     * comparison results
     */
    class Result {

        public String id;
        public int dist;
        public String col;
    }

    /**
     * preprocess the sentences to speed up the comparison
     */
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
            sent = cs.getSentence().strip();
            sentence = new ArrayList<>();
            for (char c : sent.toCharArray()) {
                sentence.add(c);
            }
            for (ConllWord cw : cs.getAllWords()) {
                //forms.add(cw.getForm());
                if (cw.getTokentype() != ConllWord.Tokentype.CONTRACTED) {
                    lemmas.add(cw.getLemma());
                    uposs.add(cw.getUpostag());
                    xposs.add(cw.getXpostag());
                    deprels.add(cw.getDeplabel());
                    feats.add(cw.getFeaturesStr());
                }
            }
        }

        public String getColumnAsString(String col) {
            if ("FORM".equals(col)) {
                return sent;
            } else if ("LEMMA".equals(col)) {
                return String.join("|", lemmas);
            } else if ("UPOS".equals(col)) {
                return String.join("|", uposs);
            } else if ("XPOS".equals(col)) {
                return String.join("|", xposs);
            } else if ("DEPREL".equals(col)) {
                return String.join("|", deprels);
            } else if ("FEATS".equals(col)) {
                return String.join(",", feats);
            }
            return "";
        }
    }

    public static void main(String args[]) throws ConllException {
        Options options = new Options();
        Option group1 = Option.builder().longOpt("group1")
                .argName("files")
                .hasArgs()
                .desc("first group of conllu files")
                .required()
                .build();
        options.addOption(group1);

        Option group2 = Option.builder().longOpt("group2")
                .argName("files")
                .hasArgs()
                .desc("second group of conllu files (if present only sentences overlapping both groups are shown")
                .build();
        options.addOption(group2);

        Option formdist = Option.builder("f").longOpt("form")
                .argName("int")
                .hasArg()
                //.type(Integer.TYPE)
                .desc("maximal Levenshtein distance for forms (character level)")
                .build();
        options.addOption(formdist);

        Option lemmadist = Option.builder("l").longOpt("lemma")
                .argName("int")
                .hasArg()
                .desc("maximal Levenshtein distance for lemmas (token level)")
                .build();
        options.addOption(lemmadist);

        Option uposdist = Option.builder("u").longOpt("upos")
                .argName("int")
                .hasArg()
                .desc("maximal Levenshtein distance for upos (token level)")
                .build();
        options.addOption(uposdist);

        Option xposdist = Option.builder("x").longOpt("xpos")
                .argName("int")
                .hasArg()
                .desc("maximal Levenshtein distance for xpos (token level)")
                .build();
        options.addOption(xposdist);

        Option featsdist = Option.builder().longOpt("feats")
                .argName("int")
                .hasArg()
                .desc("maximal Levenshtein distance for feats (token level)")
                .build();
        options.addOption(featsdist);

        Option depreldist = Option.builder("d").longOpt("deprel")
                .argName("int")
                .hasArg()
                .desc("maximal Levenshtein distance for deprel (token level)")
                .build();
        options.addOption(depreldist);

        Option threads = Option.builder("t").longOpt("threads")
                .argName("int")
                .hasArg()
                .desc("number of threads to use")
                .build();
        options.addOption(threads);

        Option jsonoutput = Option.builder("j")
                .desc("output results in json")
                .build();
        options.addOption(jsonoutput);

        CommandLineParser parser = new DefaultParser();

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            int forms = Integer.parseInt(line.getOptionValue(formdist, "0"));
            int lemmas = Integer.parseInt(line.getOptionValue(lemmadist, "-1"));
            int upos = Integer.parseInt(line.getOptionValue(uposdist, "-1"));
            int xpos = Integer.parseInt(line.getOptionValue(xposdist, "-1"));
            int feats = Integer.parseInt(line.getOptionValue(featsdist, "-1"));
            int deprels = Integer.parseInt(line.getOptionValue(depreldist, "-1"));

            int threadnum = Integer.parseInt(line.getOptionValue(threads, "2"));

            List<String> g1 = new ArrayList<>(Arrays.asList(line.getOptionValues(group1)));
            List<String> g2 = null;
            if (line.getOptionValues(group2) != null) {
                g2 = new ArrayList<>(Arrays.asList(line.getOptionValues(group2)));
            }

            ConlluComparator cc = new ConlluComparator(g1, g2, threadnum);
            System.out.println(cc.analyse(forms, lemmas, upos, xpos, feats, deprels, line.hasOption(jsonoutput)));
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(90);
            formatter.printHelp("ConlluComparator [options]", e.getMessage(), options, null);
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }
}
