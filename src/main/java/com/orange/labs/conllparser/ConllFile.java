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
 @version 2.6.0 as of 20th June 2020
*/
package com.orange.labs.conllparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ouvrir un fichier CONLL
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConllFile {

    List<ConllSentence> sentences;
    Class<ConllSentence> conllsentenceSubclass = null;
    int ctline;
    boolean standardcols = true;
    Map<String, Integer>columndefs = null; // column definitions: column name: position
    // standard columns for conllu which can be edited graphically (more or less)
    // conllup columns are edited as text only
    static Set<String> conllustandard =  new LinkedHashSet<>(Arrays.asList("ID", "FORM", "LEMMA", "UPOS", "XPOS", "FEATS", "HEAD", "DEPREL", "DEPS", "MISC"));

    /**
     * open CoNLL-U File and read its contents
     *
     * @param file CONLL file
     * @param ignoreSentencesWithoutAnnot ignore sentences which do not have any
     * information above columns 12
     * @param ignoreSentencesWithoutTarget ignore sentences which do not have
     * any target as annotation
     * @throws IOException
     * @throws com.orange.labs.nlp.conllparser.ConllWord.ConllWordException
     */
    public ConllFile(File file, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws IOException, ConllException {
        FileInputStream fis = new FileInputStream(file);
        parse(fis, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget);
        fis.close();
    }

    /**
     *
     * @param filecontents contenu du fichier COLL
     * @param ignoreSentencesWithoutAnnot ignore sentences which do not have any
     * information above columns 12
     * @param ignoreSentencesWithoutTarget ignore sentences which do not have
     * any target as annotation
     * @throws ConllException
     * @throws IOException
     */
    public ConllFile(String filecontents, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws ConllException, IOException {
        InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget);
    }

    public ConllFile(File file, Class cs) throws IOException, ConllException {
        conllsentenceSubclass = cs;
        FileInputStream fis = new FileInputStream(file);
        parse(fis, false, false);
        fis.close();
    }

    public ConllFile(String filecontents) throws ConllException, IOException {
        this(filecontents, null);
    }

    public ConllFile(String filecontents, Class cs) throws ConllException, IOException {
        conllsentenceSubclass = cs;
        InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, false, false);
    }

    public ConllFile(InputStream inputStream) throws ConllException, IOException {

        //InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, false, false);
    }

    private void parse(InputStream ips, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws ConllException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(ips, StandardCharsets.UTF_8));
        sentences = new ArrayList<>();

        List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines = new ArrayList<>();
        int countWords = 0; // count only non-comment lines
        String line;
        // on lit des commentaires dans le fichier CONLL qui sont uniquement utiles pour Gift
        boolean showgrana = true;
        boolean showID = true;
        ctline = 0;

        columndefs = new LinkedHashMap<>();
        try {
            while ((line = br.readLine()) != null) {
                ctline++;
                if (ctline == 1) {
                    // check whether we read a CoNLL-U plus file
                    if (line.startsWith("# global.columns =")) {
                        String [] elems = line.substring(18).trim().split("[ \\t]+");
                        if (elems.length < 2) {
                            throw new ConllException("invalid conllu+ definition " + line);
                        }
                        for (String d : elems) {
                            int pos = columndefs.size();
                            if (columndefs.containsKey(d)) {
                                throw new ConllException("doubled column name in  conllu+ definition " + line);
                            }
                            columndefs.put(d, pos);
                        }
                        standardcols = false;
                        // currently only additional columns are allowed, so the first 10 MUST be the standard CoNLL-U columns
                        Iterator<String> cdefs = columndefs.keySet().iterator();
                        Iterator<String> cst  = conllustandard.iterator();
                        for (int i = 0; i < 10; ++i) {
                            String st = cst.next();
                            if (!cdefs.hasNext()) {
                                throw new ConllException("Missing Standard column '" + st + "' in conllu+ definition " + line);
                            }
                            String def = cdefs.next();

                            if (!def.equals(st)) {
                                 throw new ConllException("Column definition ('" + def + "' != '" + st + "') does not follow Standard column order in conllu+ definition: '" + line + "'");
                            }
                        }
                        continue;
                    } else {
                        // standard CoNLL-U columns
                        columndefs.put("ID", 0);
                        columndefs.put("FORM", 1);
                        columndefs.put("LEMMA", 2);
                        columndefs.put("UPOS", 3);
                        columndefs.put("XPOS", 4);
                        columndefs.put("FEATS", 5);
                        columndefs.put("HEAD", 6);
                        columndefs.put("DEPREL", 7);
                        columndefs.put("DEPS", 8);
                        columndefs.put("MISC", 9);
                    }
                }
                //System.out.println("LINE1:" + line);

                if (ctline % 100000 == 0) {
                    System.err.format("%d lines (%d sentences) read\r", ctline, sentences.size());
                }
                if (line.trim().isEmpty()) {
                    if (!sentenceLines.isEmpty() && countWords != 0) {
                        processSentence(sentenceLines, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget, showgrana, showID, columndefs);
                        countWords = 0;
                    }
                } else {
                    if (line.startsWith("#")) {
                        if (line.startsWith("#NOGRANA")) {
                            showgrana = false;
                        } else if (line.startsWith("#NOID")) {
                            showID = false;
                        } else if (line.startsWith("#GRANA")) {
                            showgrana = true;
                        } else if (line.startsWith("#ID")) {
                            showID = true;
                        }
                        sentenceLines.add(new AbstractMap.SimpleEntry<Integer, String>(ctline, line)); // we add comments line to sentence to be able to reproduce them in output
                    } else {

                        // les lignes CONLL commence toujours avec un nombre, SAUF si on utilise le
                        // shift, dans ce cas on peut trouver autre choses dans des colonnes < shift ...
                        String[] elems = line.split("\t");
                        if (//elems.length >= 8 &&
                                !elems[0].isEmpty() && Character.isDigit(elems[0].charAt(0))) {
                            // supprimer les lignes commentaires ou autres
                            //System.out.println("ADDING " + line);
                            sentenceLines.add(new AbstractMap.SimpleEntry<Integer, String>(ctline, line));
                            countWords++;
                        } else {
                            //System.err.println("WARNING: incorrect line ignored: (line " + ctline + "): " + line);
                            throw new ConllException("incorrect line: (line " + ctline + "): " + line);
                        }
                    }
                }
            }
            // stock last block
            if (!sentenceLines.isEmpty() && countWords > 0) {
                processSentence(sentenceLines, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget,
                        showgrana, showID, columndefs);
            }
        } catch (ConllException e) {
            throw new ConllException(e.getMessage() + " (line " + ctline + ")");
        }
        System.err.format("%d lines (%d sentences) read\n", ctline, sentences.size());
    }

    /**
     * on a lu des lignes qui font une phrases/règle et on est arrivé à la fin
     * du block, maintenant on les traite.
     */
    private void processSentence(List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines,
            boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget,
            boolean showgrana, boolean showID,
            Map<String, Integer>columndefs) throws ConllException {
        // on a lu un bloc de lignes CONLL qui font une phrase
        ConllSentence c = null;
        if (conllsentenceSubclass == null) {
            c = new ConllSentence(sentenceLines, columndefs);
        } else {
            try {
                @SuppressWarnings("rawtypes")
				Class [] cargs = new Class[2];
                cargs[0] = List.class;
                cargs[1] = Map.class;
                // TODO: conllsentence sublcasses do not use yet the CoNLL-U+ format
                // get the constructor with arguments cargs (a list)
                // and create a new instance
                c = conllsentenceSubclass.getDeclaredConstructor(cargs).newInstance(sentenceLines, columndefs);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException ex) {
                System.err.println("Invalid ConllSentence subclass " + ex + ":: " + ex.getMessage());
            } catch (InvocationTargetException ex) {
                //System.err.println("Error in external ConllSentence subclass " + ex.getCause());
                throw new ConllException("" + ex.getCause());
            }
        }
        if (!ignoreSentencesWithoutAnnot || c.isAnnotated()) {
            if (!ignoreSentencesWithoutTarget || c.hasTargets()) {
                sentences.add(c);
            }
        }
        if (c != null) {
            c.setShowID(showID);
            c.setShowgrana(showgrana);
        }
        sentenceLines.clear();

    }

    public List<ConllSentence> getSentences() {
        return sentences;
    }

    public void addSentences(List<ConllSentence> s) {
        sentences.addAll(s);
    }

    /** merge all sentences into a single one.
        usefull to overrun unwanted sentence segmentation of a Tokenizer.
        not useful if sentence contains dependency relations, since the resulting sentence has multiple trees,
        makeTrees() must be called if sentences contain dependency relations
    */
    public void mergeIntoSingleSentence() {
        ConllSentence main = sentences.get(0);
        for (int i = 1; i<sentences.size(); ++i) {
            ConllSentence next = sentences.get(i);
            next.normalise(main.words.size()+10);
            main.words.addAll(next.words);
            main.normalise();
        }
        sentences.clear();
        sentences.add(main);
    }

    public String getColDefString() {
        if (standardcols) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("# global.columns =");
        for (String col : columndefs.keySet()) {
            sb.append(' ').append(col);
        }
        sb.append('\n');
        return sb.toString();
    }

    public Map<String, Integer> getColDefs() {
        return columndefs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        //sb.append("# number of sentences " + sentences.size() + "\n");
        sb.append(getColDefString());
        for (ConllSentence c : sentences) {
            sb.append(c);
        }
        return sb.toString();
    }

    public String getAnn() {
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        int count = 1;
        for (ConllSentence sentence : sentences) {

            for (ConllWord word : sentence.getWords()) {
                sb.append(String.format("T%d\tConllWord %d %d\t%s\n", count, offset, offset + word.getForm().length(), word.getForm()));
                sb.append(String.format("#%d\tAnnotatorNotes T%d\t%s\n", count, count, word.toString()));
                //sb.append(String.format("#%d\tAnnotatorNotes T%d\t%s\n", count, count, "toto"));
                count++;
                offset += word.getForm().length() + 1;
            }
            offset++; // pour le LF
        }

        return sb.toString();
    }

    /** read rules file
     *   condition > newvals
     *   Upos:ADP and Lemma:d.* > Feat:Key=Val, Xpos:prep
     * @param rulefile
     * @return number of changes
     * @throws FileNotFoundException
     */
    public void conditionalEdit(String rulefile) throws ConllException, IOException {
        FileInputStream fis = new FileInputStream(rulefile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        String line;

        int changes = 0;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            //System.err.format("<%s>\n", line);
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String [] elems = line.split(">", 2);
            List<String>newvals = Arrays.asList(elems[1].split("[ \\t]+"));
            int ch = conditionalEdit(elems[0], newvals);
            System.err.println(ch + " changes for condition: " + elems[0] + " values: " + elems[1]);
            changes += ch;
        }
        System.err.println(changes + " changes");
    }


    public int conditionalEdit(String condition, List<String>newvalues) throws ConllException {
        int changes = 0;
        for (ConllSentence cs : sentences) {
            changes += cs.conditionalEdit(condition, newvalues);
        }
        return changes;
    }


    public enum Output {
        TEXT, CONLL, ANN, LATEX
    };

    /**
     * process the contents of a CoNLL file
     *
     * @param out the output stream
     * @param dep2chunk the Dep2Chunk object to generate chunks from dependency
     * trees (or null if absent)
     * @param cf the ConllFile object containing at least one sentence
     * @param output the output format
     * @param filter if not null, output only sentences with a XPOS/UPOS
     * matching the filter
     * @throws ConllException
     */
    public static void processInput(PrintStream out,
            ConllFile cf,
            Output output,
            String filter,
            boolean shuffle,
            int first, int last) throws ConllException {

        if (output == Output.ANN) {
            out.print(cf.getAnn());
        } else {
            List<ConllSentence> sentences = cf.getSentences();
            if (shuffle) {
                Collections.shuffle(sentences);
            }

            int ct = 0;
            if (output == Output.CONLL) {
                out.print(cf.getColDefString());
            }
            for (ConllSentence cs : sentences) {
                ct++;
                if (ct < first) {
                    continue;
                }
                if (last >= 0 && ct > last) {
                    break;
                }
                if (filter != null) {
                    String text = cs.getSentence();
                    if (text.matches(".*" + filter + ".*")) {
                        if (null == output) {
                            out.println(cs.getSentence());
                        } else {
                            switch (output) {
                                case CONLL:
                                    out.print(cs.toString());
                                    break;
                                case LATEX:
                                    out.println(cs.getLaTeX());
                                    break;
                                default:
                                    out.println(cs.getSentence());
                                    break;
                            }
                        }
                    } else {
                        for (ConllWord w : cs.getWords()) {
                            if (w.matchesUpostag(filter) || w.matchesXpostag(filter)) {
                                if (output == Output.CONLL) {
                                    out.print(cs.toString());
                                } else {
                                    out.println(cs.getSentence());
                                }
                                break;
                            }
                        }
                    }
                } else {
                    switch (output) {
                        case CONLL:
                            out.print(cs);
                            break;
                        case LATEX:
                            out.println(cs.getLaTeX());
                            break;
                        default:
                            out.println(cs.getSentence());
                            break;
                    }
                }
            }
        }
    }

    public static void crossval(ConllFile cf, int cvparts, boolean shuffle, String outfileprefix) throws FileNotFoundException, IOException {
        List<ConllSentence> sentences = cf.getSentences();
        if (shuffle) {
            Collections.shuffle(sentences);
        }

        for (int i=0; i<cvparts; ++i) {
            int cttest = 0;
            int cttrain = 0;
            String train = String.format("%s.train-%d.conllu", outfileprefix, i+1);
            BufferedWriter bwtrain = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(train), StandardCharsets.UTF_8));
            String test = String.format("%s.test-%d.conllu", outfileprefix, i+1);
            BufferedWriter bwtest = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(test), StandardCharsets.UTF_8));


            int sct = 0;
            for (ConllSentence cs : sentences) {
                if ((sct + i) % cvparts == 0) {
                    bwtest.write(cs.toString());
                    //bwtest.write('\n');
                    cttest++;
                } else {
                    bwtrain.write(cs.toString());
                    //bwtrain.write('\n');
                    cttrain++;
                }
                sct++;
            }
            bwtrain.close();
            bwtest.close();
            System.err.println("CV part " + cvparts);
            System.err.println("  " + train + ": " + cttrain);
            System.err.println("  " + test + ": " + cttest);
        }
    }

    public static void main(String args[]) {
        if (args.length == 0) {
            System.out.println("usage: ConllFile [--cedit conditionfile] [--filter 'regex'] [--shuffle] [--first n] [--last -n] [--subphrase <deprel,deprel>] [--crossval n --outfileprefix n] [ --conll|--tex|--ann] ] [--dep2chunk rule] file.conll|-");
        } else {
            //for (String a : args) System.err.println("arg " + a);
            String filter = null;

            boolean shuffle = false;
            int first = 1;
            int last = -1; // = all
            int cvparts = 0;
            String outfileprefix = null;
            String conditionfile = null;
            Set<String>subphrase_deprels = null;

            Output output = Output.TEXT;
            int argindex = 0;
            for (int a = 0; a < args.length - 1; ++a) {
                if (args[a].equals("--filter")) {
                    filter = args[++a].replaceAll("\\+", " ");
                    argindex += 2;
                } else if (args[a].equals("--conll")) {
                    output = Output.CONLL;
                    argindex++;
                } else if (args[a].equals("--ann")) {
                    output = Output.ANN;
                    argindex++;
                } else if (args[a].equals("--tex")) {
                    output = Output.LATEX;
                    argindex++;
                } else if (args[a].equals("--cedit")) {
                    conditionfile = args[++a];
                    argindex += 2;
                } else if (args[a].equals("--subphrase")) {
                    subphrase_deprels = new HashSet<String>(Arrays.asList(args[++a].split(",")));
                    argindex += 2;
                } else if (args[a].equals("--shuffle")) {
                    shuffle = true;
                    argindex++;
                } else if (args[a].equals("--first")) {
                    first = Integer.parseInt(args[++a]);
                    argindex += 2;
                } else if (args[a].equals("--last")) {
                    last = Integer.parseInt(args[++a]);
                    argindex += 2;
                } else if (args[a].equals("--crossval")) {
                    cvparts = Integer.parseInt(args[++a]);
                    argindex += 2;
                } else if (args[a].equals("--outfileprefix")) {
                    outfileprefix = args[++a];
                    argindex += 2;

                } else if (args[a].startsWith("-")) {
                    System.err.println("Invalid option " + args[a]);
                    argindex++;
                }
            }

            if (cvparts > 0 && (first > 1 || last > -1)) {
                System.err.println("No point splitting corpus for crossval AND specifying first or last");
                System.exit(1);
            }

            if (cvparts > 0 && outfileprefix == null) {
                System.err.println("--crossval needs option --outfileprefix");
                System.exit(1);
            }

            if (filter != null) {
                System.err.println("FILTER '" + filter + "'");
            }

            if (args.length == (argindex)) {
                System.err.println("Missing CoNLL-file or - ");
                System.exit(2);
            }


            try {

                ConllFile cf;
                PrintStream out = new PrintStream(System.out, true, "UTF-8");
                if (args[argindex].equals("-")) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        if (line.length() != 0) {
                            sb.append(line).append('\n');
                        } else {
                            cf = new ConllFile(sb.toString());

                            processInput(out, cf, output, filter, shuffle, first, last);
                            out.flush();
                            sb = new StringBuilder();
                        }
                    }
                    if (sb.length() != 0) {
                        cf = new ConllFile(sb.toString());
                        processInput(out, cf, output, filter, shuffle, first, last);
                    }

                } else {
                    cf = new ConllFile(new File(args[argindex]), false, false);
                    if (subphrase_deprels != null) {
                        for (ConllSentence cs : cf.sentences) {
                            String s = cs.getSubTreeAsText(subphrase_deprels);
                            if (!s.isEmpty()) {
                                System.out.format("# %s\n%s\n", cs.getSentid(), s);
                            }
                        }
                    }
                    else if (cvparts > 0) {
                        crossval(cf, cvparts, shuffle, outfileprefix);
                    } else {
                        if (conditionfile != null) {
                           cf.conditionalEdit(conditionfile);
                        }
                        processInput(out, cf, output, filter, shuffle, first, last);
                    }
                }
            } catch (ConllException e) {
                System.err.println("Conll Error " + e.getMessage());
                System.exit(10);
            } catch (IOException e) {
                System.err.println("IO Error " + e.getMessage());
                System.exit(1);
            }

        }
    }

}
