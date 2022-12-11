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
 @version 2.20.0 as of 2nd December 2022
 */
package com.orange.labs.conllparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

public class CheckGrewmatch {

    ParseTree tree;
    String condition;

    public CheckGrewmatch(String condition, boolean debug) throws ConllException {
        this.condition = condition;
        try {
            GrewmatchLexer lexer = new GrewmatchLexer(CharStreams.fromString(condition));
            lexer.addErrorListener(new GrammarErrorListener());

            if (debug) {
                // we can see parsed tokens only once !
                for (Token tok : lexer.getAllTokens()) {
                    System.err.println("token: " + tok.getText() + "\t" + tok.getType() + "\t" + lexer.getVocabulary().getSymbolicName(tok.getType()));
                }
                lexer = new GrewmatchLexer(CharStreams.fromString(condition));
                lexer.addErrorListener(new GrammarErrorListener());

            }
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            GrewmatchParser parser = new GrewmatchParser(tokens);
            parser.addErrorListener(new GrammarErrorListener());
            tree = parser.expression(); // parser
        } catch (ParseCancellationException e) {
            throw new ConllException(e.getMessage());
        }
    }

    public List<List<ConllWord>> match(Map<String, Set<String>> wordlists, ConllSentence csent) {
        GrewVisitor eval = new GrewVisitor(wordlists);
        boolean rtc = eval.visit(tree);
        eval.out();

        // TODO APPLY CONDITIONS on word
        return eval.match(csent);        
    }
    
    public int evaluate(Map<String, Set<String>> wordlists, ConllSentence csent) throws Exception {
        List<List<ConllWord>> llcw = match(wordlists, csent);
        int ct = 0;
        if (llcw != null) {
            System.out.println("SFOUND " + llcw.size());
            for (List<ConllWord> lcw : llcw) {
                Collections.sort(lcw, new CWSortbyId());
                ct++;
                System.out.println("SFINAL " + ct);
                for (ConllWord cw : lcw) {
                    System.out.println("  " + cw);
                }
            }
        }
        //System.err.println("MATCH? " + cword + " :: " + rtc);
        return ct;
    }

    public String prettyprint(List<List<ConllWord>> llcw) {
        StringBuilder sb = new StringBuilder();
        if (llcw != null) {
            int ct = 0;
            sb.append("FOUND ").append(llcw.size()).append('\n');
            for (List<ConllWord> lcw : llcw) {
                Collections.sort(lcw, new CWSortbyId());
                ct++;
                sb.append("FINAL ").append(ct).append('\n');
                for (ConllWord cw : lcw) {
                    sb.append(cw.toString()).append('\n');
                }
            }
        }
        return sb.toString();
    }
    
    public int evaluate(Map<String, Set<String>> wordlists, ConllFile cf, boolean debug) throws Exception {
        GrewVisitor eval = new GrewVisitor(wordlists);
        boolean rtc = eval.visit(tree);
        if (debug) eval.out();

        // TODO APPLY CONDITIONS on word
        int ct = 0;
        for (ConllSentence cs : cf.getSentences()) {
            if (debug) System.out.println("======================= ");
            cs.normalise();
            cs.makeTrees(null);
            List<List<ConllWord>> llcw = eval.match(cs);
            if (llcw != null) {
                if (debug) System.out.println("FOUND " + llcw.size());
                for (List<ConllWord> lcw : llcw) {
                    Collections.sort(lcw, new CWSortbyId());
                    ct++;
                    if (debug) {
                        System.out.println("FINAL " + ct);
                        for (ConllWord cw : lcw) {
                            System.out.println("  " + cw);
                        }
                    }
                }
            }
        }
        return ct;
    }

    public class CWSortbyId implements Comparator<ConllWord> {
        // Used for sorting in ascending order of
        // roll number

        @Override
        public int compare(ConllWord a, ConllWord b) {
            return a.getId() - b.getId();
        }
    }

    /*
    java -cp target/ConlluEditor-2.20.0-jar-with-dependencies.jar com.orange.labs.conllparser.CheckGrewmatch \
      'pattern { N [upos=NUM, xpos=NOUN|PR];
                 A [lemma="bod"];
                 B [form <> "bydd"|byddaf];
                 A << N;
                  e: A -[acl:relcl]-> N }
       without { N [lemma="toto"]} '

    java -cp target/ConlluEditor-2.20.0-jar-with-dependencies.jar com.orange.labs.conllparser.CheckGrewmatch \
       'pattern { N [upos=VERB] }'

    java -cp target/ConlluEditor-2.20.0-jar-with-dependencies.jar com.orange.labs.conllparser.CheckGrewmatch \
       'pattern { N [upos=NOUN|VERB, xpos<>VBN] }'

     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: Condition 'grewmatch'|@patternfile [conllu file]");
        } else {
            List<CheckGrewmatch> cgs = new ArrayList<>();
            if (args[0].charAt(0) == '@') {
                FileInputStream fis = new FileInputStream(new File(args[0].substring(1)));
                BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.strip();
                    if (line.length() > 0 && line.charAt(0) != '#') {
                        System.out.println("compiling " + line);
                        CheckGrewmatch cg = new CheckGrewmatch(line, false);
                        cgs.add(cg);
                    }
                }
            } else {
                CheckGrewmatch cg = new CheckGrewmatch(args[0], true);
                cgs.add(cg);
            }
            ConllSentence csent;
            if (args.length == 1) {
                //cword = new ConllWord("1\trules\trule\tNOUN\tNNS\tNumber=Plur|Gender=Neut\t2\tnsubj\t_\tSpaceAfter=No", null, null);
//                csent = new ConllSentence("1\trules\trule\tNOUN\tNNS\tNumber=Plur|Gender=Neut\t2\tnsubj\t_\t_\n"
//                        + "2\tsleep\tsleep\tVERB\tNOUN\tNumber=Plur|Person=3\t0\troot\t_\tSpaceAfter=No", null);
//
//                csent = new ConllSentence("1	the	the	DET	DT	Definite=Def|PronType=Art	4	det	_	_\n"
//                        + "2	very	very	ADV	RB	_	3	advmod	_	_\n"
//                        + "3	small	small	ADJ	JJ	Degree=Pos	4	amod	_	_\n"
//                        + "4	mouse	mouse	NOUN	NN	Number=Sing	6	nsubj	_	_\n"
//                        + "5	is	be	AUX	VBZ	Mood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin	6	aux	_	_\n"
//                        + "6	sleeping	sleep	VERB	VBG	Tense=Pres|VerbForm=Part	0	root	_	SpaceAfter=No\n", null);

                csent = new ConllSentence("1	the	the	DET	DT	Definite=Def|PronType=Art	4	det	_	_\n"
                        + "2	very	very	ADV	RB	_	3	advmod	_	_\n"
                        + "3	little	little	ADJ	JJ	Degree=Pos	4	amod	_	_\n"
                        + "4	mouse	mouse	NOUN	NN	Number=Sing	6	nsubj	_	_\n"
                        + "5	has	have	AUX	VBZ	Mood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin	6	aux	_	_\n"
                        + "6	eaten	eat	VERB	VBN	Tense=Past|VerbForm=Part	0	root	_	_\n"
                        + "7	the	the	DET	DT	Definite=Def|PronType=Art	12	det	_	_\n"
                        + "8	very	very	ADV	RB	_	9	advmod	_	_\n"
                        + "9	big	big	ADJ	JJ	Degree=Pos	12	compound	_	_\n"
                        + "10	and	and	CCONJ	CC	_	11	cc	_	_\n"
                        + "11	juicy	juicy	ADJ	JJ	Degree=Pos	9	conj	_	_\n"
                        + "12	cheese	cheese	NOUN	NN	Number=Sing	6	obj	_	_\n"
                        + "13	today	today	ADV	AV	_	6	advmod	_	SpaceAfter=No\n"
                        + "14	.	.	PUNCT	.	_	6	punct	_	SpaceAfter=No\n", null);

                csent.makeTrees(null);
                //ConllWord cword = csent.getWord(6);
                //ConllWord cword2 = csent.getWord(6);
                System.err.println(csent);
                for (CheckGrewmatch cg : cgs) {
                    cg.evaluate(null, csent);
                }
                //System.err.println("rtc " + rtc);
            } else {
                int matches = 0;
                for (int i = 1; i < args.length; ++i) {
                    ConllFile cf = new ConllFile(new File(args[i]), null);
                    for (CheckGrewmatch cg : cgs) {
                        System.out.println(cg.condition);
                        int r = cg.evaluate(null, cf, true);
                        matches += r;
                        System.out.println("solutions: " + r);
                    }
                   
                }
                System.out.println(matches + " matches");
            }

        }
    }

    private Exception ConllException(String message) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
