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
 @version 2.13.1 as of 16th October 2021
 */
package com.orange.labs.conllparser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class GetReplacement {

    public static String evaluate(String extraction, ConllWord cword) throws ConllException { 
        try {
            ReplacementsLexer lexer = new ReplacementsLexer(CharStreams.fromString(extraction));
            lexer.addErrorListener(new GrammarErrorListener());

            // we can see tokens only once !
//        for (Token tok : lexer.getAllTokens()) {
//           System.err.println("token: " + tok);
//        }
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ReplacementsParser parser = new ReplacementsParser(tokens);
            parser.addErrorListener(new GrammarErrorListener());
            ParseTree tree = parser.prog(); // parse

            REvalVisitor eval = new REvalVisitor(cword, extraction);
            String rtc = eval.visit(tree);
            //System.err.println("rtc " + rtc);
            return rtc;
        } catch (Exception e) {
            // catch errors from bad replacements
            //e.printStackTrace();
            throw new ConllException(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: Replacements 'expression' ['expression' ...]");
        } else {

            ConllSentence csent = new ConllSentence(
                    "1\tI\tI\tPRON\tPP\tPerson=1\t2\tnsubj\t_\t_\n"
                    + "2\tknew\tknow\tVERB\tvfin\tTense=Past_\t0\troot\t_\t_\n"
                    + "3\tthe\tthe\tDET\tAD\tDefinite=Yes\t4\tdet\t_\tSpacesAfter=\\t|Other=Erthygl\n"
                    + "4\trules\trule\tNOUN\tNNS\tNumber=Plur|Gender=Neut\t5\tnsubj\t_\t_\n"
                    + "5\tstank\tstink\tVERB\tVV\tNumber=Plur|TEnse=Past\t2\tccomp\t_\tSpaceAfter=No", null);
            csent.makeTrees(null);
            System.err.println(csent);
            ConllWord cword = csent.getWord(3);

//            System.out.println(cword);
//            System.out.println(cword.getHeadWord());
//            System.out.println(cword.getHeadWord().getHeadWord());
//            System.out.println(cword.getHeadWord().getHeadWord().getHeadWord());
            //} else {
            //    cword = new ConllWord(args[1].replaceAll(" +", "\t"), null, null);
            //}
            List<String> ex = new ArrayList<>();
            ex.add("head(head(head(Lemma)))");
            ex.add("head(head(Lemma))");
            ex.add("\"a\" + \"b\" +head(Lemma) + \"z\"");
            ex.add("\"NOUN\"");
            ex.add("\"Number=Sing\"");
            ex.add("this(Form)");
            ex.add("this(Feat_Definite)+this(Form)");
            ex.add("substring(head(Form),1)");
            ex.add("substring(head(head(Feat_Number)),1,3)");
            ex.add("replace(head(Form),\"u.\",\"aaaa\")");
            ex.add("replace(replace(head(Form),\"u.\",\"aaaa\"),\"a\",\"B\")");
            ex.add("upper(head(Form))+\"!!!\"");
            ex.add("lower(head(Upos))+\"...\"");
            ex.add("cap(head(head(Upos)))");
            ex.add("substring(upper(head(Form)),2,4)");
            ex.add("this(Misc_Other)");

            ex.addAll(Arrays.asList(args));

            for (String arg : ex) {
                System.err.format("\nparsing <%s>\n", arg);
                ReplacementsLexer lexer = new ReplacementsLexer(CharStreams.fromString(arg));
                lexer.addErrorListener(new GrammarErrorListener());

//                // we can see tokens only once !
//                for (Token tok : lexer.getAllTokens()) {
//                    System.err.println("token: " + tok + "\t" + tok.getType());
//                }
//                if (true)   continue;
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                ReplacementsParser parser = new ReplacementsParser(tokens);
                parser.addErrorListener(new GrammarErrorListener());

                ParseTree tree = parser.prog(); // parse           

                REvalVisitor eval = new REvalVisitor(cword, arg);
                String rtc = eval.visit(tree);
                System.err.format(":: " + arg + " ===> '%s'\n", rtc);
            }
        }

    }
}
