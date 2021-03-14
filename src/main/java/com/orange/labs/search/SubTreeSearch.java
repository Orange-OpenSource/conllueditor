/* This library is under the 3-Clause BSD License

Copyright (c) 2021, Orange S.A.

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
 @version 2.11.0 as of 13th March 2021
 */
package com.orange.labs.search;

import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import com.orange.labs.conllparser.ConlluPlusConverter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Find a sentence which matches the partial tree
 *
 * @author johannes.heinecke@orange.com
 */
public class SubTreeSearch {

    List<ConllSentence> subtrees;
    final boolean debug = false;

    public SubTreeSearch(String conllusentence) throws ConllException, IOException {
        // read input string/ It must contain a single sentence and have a single root
        if (conllusentence.startsWith("# global.columns =")) {
            //conllusentence = conllusentence.replaceAll(" +", "\t");
            // we convert to the standard 10 column format (since CE cannot deal with missing standard columns)
            ConlluPlusConverter cpc = new ConlluPlusConverter(null);
            //System.out.println("BEFORE\n" + conllusentence);
            InputStream is = new ByteArrayInputStream(conllusentence.getBytes(StandardCharsets.UTF_8));
            conllusentence = cpc.convert(is);
            //System.out.println("AFTER\n" + conllusentence);
        }
        //conllusentence = conllusentence.replaceAll(" +", "\t");
        ConllFile cfile = new ConllFile(conllusentence);
        //if (cfile.getSentences().size() != 1) {
        //    throw new ConllException("Exactly one subtree allowed");
        //}
        subtrees = new ArrayList<>();
        for (ConllSentence subtree : cfile.getSentences()) {
            subtree.makeTrees(null);
            if (subtree.getHeads().size() != 1) {
                throw new ConllException("Subtree has more than one root");
            }
            subtrees.add(subtree);
        }
        if (subtrees.isEmpty()) {
            throw new ConllException("No subtree given");
        }
    }

    /**
     * returns true if the subtree (including its regex does match the sentence
     * We check first whether the head of the subtree matches any word in the
     * sentence. If so we check all dependants of the subtree, whether tehy
     * match the sentence. Additional dependence in the sentence are OK (TODO:
     * unless explicitly negated in the subtree) currently we ignore enhanced
     * dependencies (ant empty (N.1) words
     * returns a set of Ids matching the sentence (or empty set)
     * //return: -1 if subtree does not match, else the id of the word matching the head of the subtree
     * //
     */
    public Set<Integer> match(ConllSentence sentence) throws ConllException {
        sentence.normalise();
        sentence.makeTrees(null);

        System.out.println("Sentence\n"+sentence);

        Set<Integer>matched = new HashSet<>();
        boolean ok = false;
        for (ConllSentence subtree : subtrees) {
            if (debug) System.out.println("SUBTREE\n" + subtree);
            for (ConllWord word : sentence.getWords()) {
                if (debug) System.out.println("is word head of subtree " + word);
                if (matchWord(subtree.getHead(), word)) {
                    if (debug) {
                        System.out.println("HEAD matches: " + word.toString());
                    }
                    
                    ok = matchDependant(subtree.getHead(), word, matched);
                    if (!ok) {
                        System.out.println("bad dep match");
                        matched.clear();
                        //break;
                    }
                }
                if (ok) {
                    matched.add(word.getId());
                    System.out.println("A-MATCHING WORDS " + matched);
                    //return word.getId();
                    return matched;
                }
            }
            if (ok) { // useful ?
                System.out.println("B-MATCHING WORDS " + matched);
                ///return 0; // TODO
                return matched;
            }
        }
        System.out.println("C-MATCHING WORDS " + matched);
        return matched;    
        //return -1;
    }

    private boolean matchDependant(ConllWord subtreeWord, ConllWord word, Set<Integer>matched) {

        boolean ok = true;
         Set<Integer>locallymatched = new HashSet<>();
        for (ConllWord subtreeDep : subtreeWord.getDependents()) {
            // check whether this dependent of our partial trees matches with a dependent
            // of word
            ok = false;
            if (debug) {
                System.out.println("CHECK ST:" + subtreeDep);
            }
            for (ConllWord wordDep : word.getDependents()) {
                if (debug) {
                    System.out.println("   with  " + wordDep);
                }
                if (matchWord(subtreeDep, wordDep)) {
                    if (debug) {
                        System.out.println("    words match");
                    }
                   
                    boolean rtc = matchDependant(subtreeDep, wordDep, locallymatched);
                    if (debug) {
                        System.out.println("   subtrees match: " + rtc);
                    }
                    if (rtc) {
                        ok = true;
                        locallymatched.add(wordDep.getId());
                    } else {
                        locallymatched.clear();
                        ok = false;
                    }
                }
            }
            if (!ok) {
                return false;
            }
        }
        if (ok)  matched.addAll(locallymatched);
        return ok;
    }

    private boolean matchWord(ConllWord subtree, ConllWord word) {
        if (!subtree.getForm().equals("_")) {
            if (!word.matchesForm(subtree.getForm())) {
                return false;
            }
        }
        if (!subtree.getLemma().equals("_")) {
            if (!word.matchesLemma(subtree.getLemma())) {
                return false;
            }
        }
        if (!subtree.getUpostag().equals("_")) {
            if (!word.matchesUpostag(subtree.getUpostag())) {
                return false;
            }
        }
        if (!subtree.getXpostag().equals("_")) {
            if (!word.matchesXpostag(subtree.getXpostag())) {
                return false;
            }
        }
        if (!subtree.getFeaturesStr().equals("_")) {
            for (String fname : subtree.getFeatures().keySet()) {
                if (!word.matchesFeatureValue(fname, subtree.getFeatures().get(fname))) {
                    return false;
                }
            }
        }
        if (!subtree.getDeplabel().equals("_") && !subtree.getDeplabel().equals("root")) {
            if (!word.matchesDeplabel(subtree.getDeplabel())) {
                return false;
            }
        }
        return true;
    }

    private static String readLineByLine(String filePath) {
        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return contentBuilder.toString();
    }

    public static void main(String args[]) throws ConllException, IOException {
        // test sentence and partial tree       
        String subtree = readLineByLine(args[0]);
        SubTreeSearch std = new SubTreeSearch(subtree);

        ConllFile cf = new ConllFile(new File(args[1]), false, false);
        for (ConllSentence cs : cf.getSentences()) {
            //int rtc =
            Set<Integer> rtc = std.match(cs);
            System.out.println("===" + rtc + " " + cs.getSentence());
        }
    }
}
