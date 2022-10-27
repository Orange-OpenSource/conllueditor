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
 @version 2.17.6 as of 27th October 2022
 */
package com.orange.labs.conllparser;

//import com.orange.labs.conllparser.ConllWord.EnhancedDeps;
import java.util.Map;
import java.util.Set;

/* vistor for the getters in the conditions, which return strings and not booleans */
public class CGetVisitor extends ConditionsBaseVisitor<String> {
    ConllWord cword = null; // all conditions are checked on this word
    ConllWord pointedWord = null; // child/head etc are followed here
    int level = 0; // -1 head, -2 head's head
    int sequence = 0; // -1 word to the left, 1 word to the right etc
    Map<String, Set<String>> wordlists; // stores lists for Form and Lemma: "filename": (words)

    public CGetVisitor(ConllWord cword, Map<String, Set<String>> wordlists) {
        this.cword = cword;
        pointedWord = cword;
        this.wordlists = wordlists;
    }

    /**
     * get the correct ConllWord (current word, its head, head's head, preceing
     * or following). If there is no word at the end of the path (since the path
     * demanded is not in this tree), null is returned
     */
    private ConllWord getCW() {
        return pointedWord;
    }

    // $head(...)
    @Override
    public String visitGetkopf(ConditionsParser.GetkopfContext ctx) {
        ConllWord before = pointedWord;
        //System.err.println("AAAA " + pointedWord);
        if (pointedWord.getHeadWord() != null) {
            pointedWord = pointedWord.getHeadWord();
            //System.err.println("BBBB " + pointedWord);
        } else {
            return null;
        }
        //System.err.println("CCCC " + ctx.columnname().getText());
        String value = visit(ctx.columnname());

        pointedWord = before;
        return value;
    }

    // $Upos
    @Override
    public String visitValueUpos(ConditionsParser.ValueUposContext ctx) {
        // String text = ctx.UPOS().getText();
        ConllWord use = getCW();
        //System.err.println("UPOSVALUE " + use);
        if (use == null) {
            return null;
        }

        return use.getUpostag();
    }

    // $Deprel
    @Override
    public String visitValueDeprel(ConditionsParser.ValueDeprelContext ctx) {
        ConllWord use = getCW();
        //System.err.println("DEPRELVALUE " + use);
        if (use == null) {
            return null;
        }

        return use.getDeplabel();
    }

}
