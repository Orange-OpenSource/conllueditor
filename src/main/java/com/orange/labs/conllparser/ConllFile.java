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
 @version 1.8.0 as of 30th January 2019
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
import java.util.List;
import java.util.Set;

/**
 * Ouvrir un fichier CONLL
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConllFile {

    List<ConllSentence> sentences;
    Class conllsentenceSubclass = null;
    int ctline;

    /**
     * open CONLL File and read its contents
     *
     * @param file CONLL file
     * @param shift number of columns to ignore at the beginning
     * @param ignoreSentencesWithoutAnnot ignore sentences which do not have any
     * information above columns 12
     * @param ignoreSentencesWithoutTarget ignore sentences which do not have
     * any target as annotation
     * @throws IOException
     * @throws com.orange.labs.nlp.conllparser.ConllWord.ConllWordException
     */
    public ConllFile(File file, int shift, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws IOException, ConllException {
        FileInputStream fis = new FileInputStream(file);
        parse(fis, shift, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget);
        fis.close();
    }

    /**
     *
     * @param filecontents contenu du fichier COLL
     * @param shift number of columns to ignore at the beginning
     * @param ignoreSentencesWithoutAnnot ignore sentences which do not have any
     * information above columns 12
     * @param ignoreSentencesWithoutTarget ignore sentences which do not have
     * any target as annotation
     * @throws ConllException
     * @throws IOException
     */
    public ConllFile(String filecontents, int shift, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws ConllException, IOException {
        InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, shift, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget);
    }

    public ConllFile(File file, Class cs) throws IOException, ConllException {
        conllsentenceSubclass = cs;
        FileInputStream fis = new FileInputStream(file);
        parse(fis, 0, false, false);
        fis.close();
    }

    public ConllFile(String filecontents) throws ConllException, IOException {
        this(filecontents, null);
    }

    public ConllFile(String filecontents, Class cs) throws ConllException, IOException {
        conllsentenceSubclass = cs;
        InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, 0, false, false);
    }

    public ConllFile(InputStream inputStream) throws ConllException, IOException {

        //InputStream inputStream = new ByteArrayInputStream(filecontents.getBytes(StandardCharsets.UTF_8));
        parse(inputStream, 0, false, false);
    }

    private void parse(InputStream ips, int shift, boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget) throws ConllException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(ips, StandardCharsets.UTF_8));
        sentences = new ArrayList<>();

        List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines = new ArrayList<>();
        int countWords = 0; // count only non-comment lines
        String line;
        // on lit des commentaires dans le fichier CONLL qui sont uniquement utiles pour Gift
        boolean showgrana = true;
        boolean showID = true;
        ctline = 0;

        try {
            while ((line = br.readLine()) != null) {
                //System.out.println("LINE1:" + line);
                ctline++;
                if (line.trim().isEmpty()) {
                    if (!sentenceLines.isEmpty() && countWords != 0) {
                        processSentence(sentenceLines, shift, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget, showgrana, showID);
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
			sentenceLines.add(new AbstractMap.SimpleEntry(ctline, line)); // we add comments line to sentence to be able to reproduce them in output
                    } else {

                        // il faut ignoré les lignes qui ne sont pas en format CONLL.
                        // les lignes CONLL commence toujours avec un numbre, SAUF si on utilise le
                        // shift, dans ce cas on peut trouver autre choses dans des colonnes < shift ...
                        String[] elems = line.split("\t");
                        if (elems.length >= 8 + shift && !elems[shift].isEmpty() && Character.isDigit(elems[shift].charAt(0))) {
                            // supprimer les lignes commentaires ou autres
                            //System.out.println("ADDING " + line);
                            sentenceLines.add(new AbstractMap.SimpleEntry(ctline, line));
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
                processSentence(sentenceLines, shift, ignoreSentencesWithoutAnnot, ignoreSentencesWithoutTarget,
                        showgrana, showID);
            }
        } catch (ConllException e) {
            throw new ConllException(e.getMessage() + " (line " + ctline + ")");
        }
    }

    /**
     * on a lu des lignes qui font une phrases/règle et on est arrivé à la fin
     * du block, maintenant on les traite.
     */
    private void processSentence(List<AbstractMap.SimpleEntry<Integer, String>> sentenceLines, int shift,
            boolean ignoreSentencesWithoutAnnot, boolean ignoreSentencesWithoutTarget,
            boolean showgrana, boolean showID) throws ConllException {
        // on a lu un bloc de lignes CONLL qui font une phrase
        ConllSentence c = null;
        if (conllsentenceSubclass == null) {
            c = new ConllSentence(sentenceLines, shift);
        } else {
            try {
                Class[] cargs = new Class[2];
                cargs[0] = List.class;
                cargs[1] = Integer.class;

                c = (ConllSentence) conllsentenceSubclass.getDeclaredConstructor(cargs).newInstance(sentenceLines, shift);
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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //sb.append("# number of sentences " + sentences.size() + "\n");
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
                            out.println(cs);
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
            System.out.println("usage: ConllFile [--filter 'regex'] [--shuffle] [--first n] [--last -n] [--subphrase <deprel,deprel>] [--crossval n --outfileprefix n] [ --conll|--tex|--ann] ] [--dep2chunk rule] file.conll|- [shift]");
        } else {
            //for (String a : args) System.err.println("arg " + a);
            int shift = 0;
            String filter = null;

            boolean shuffle = false;
            int first = 1;
            int last = -1; // = all
            int cvparts = 0;
            String outfileprefix = null;
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
            if (args.length == (2 + argindex)) {
                shift = Integer.parseInt(args[argindex + 1]);
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
                    cf = new ConllFile(new File(args[argindex]), shift, false, false);
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
