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
 @version 2.18.0 as of 27th October 2022
 */
package com.orange.labs.conllparser;

//import com.orange.labs.conllparser.ConllWord.EnhancedDeps;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/* vistor for the getters in the conditions, which return strings and not booleans */
public class CGetVisitor extends ConditionsBaseVisitor<List<String>> {
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

    // head(@...)
    @Override
    public List<String> visitGetkopf(ConditionsParser.GetkopfContext ctx) {
        ConllWord before = pointedWord;
        //System.err.println("AAAA " + pointedWord);
        if (pointedWord.getHeadWord() != null) {
            pointedWord = pointedWord.getHeadWord();
            //System.err.println("BBBB " + pointedWord);
        } else {
            return null;
        }
        //System.err.println("CCCC " + ctx.columnname().getText());
        List<String> value = visit(ctx.columnname());

        pointedWord = before;
        return value;
    }

    // next(@...)
    @Override
    public List<String> visitGetnachher(ConditionsParser.GetnachherContext ctx) {

        int currentid = pointedWord.getId();
        int size = pointedWord.getMysentence().getAllWords().size();
        int lastid = pointedWord.getMysentence().getAllWords().get(size-1).getId();

        if (currentid == lastid) return null;
        ConllWord before = pointedWord;
        pointedWord = pointedWord.getMysentence().getWord(currentid+1);
        //System.err.println("AAAAAA " + currentid + " " + size + " " + lastid);
        //System.err.println("BBBBBB " + before);
        //System.err.println("CCCCCC " + pointedWord);
    
        List<String> value = visit(ctx.columnname());
 
        pointedWord = before;
        return value;
    }


   /**
     * prec(@...)
     */
    @Override
    public List<String> visitGetvorher(ConditionsParser.GetvorherContext ctx) {
        int currentid = pointedWord.getId();
        if (currentid <= 1) return null;

        ConllWord before = pointedWord;

        pointedWord = pointedWord.getMysentence().getWord(currentid-1);
        List<String> value = visit(ctx.columnname());
        //System.err.println("PREC VALUE " + value);
        pointedWord = before;
        return value;
    }


    @Override
    public List<String> visitGetchild(ConditionsParser.GetchildContext ctx) {
        ConllWord before = pointedWord;
        List<String> rtc = new ArrayList<>();
        for(ConllWord cw : pointedWord.getDependents()) {
            //System.err.println("==HEAD " + pointedWord);
            //System.err.println("==DOWN VISITCHILD " + cw);
            pointedWord = cw;
            rtc.addAll(visit(ctx.columnname()));
        }
        
        pointedWord = before;
        //System.err.println("==RETURN " + rtc + " :: " + pointedWord);
        return rtc;
    }

    // @Upos
    @Override
    public List<String> visitValueUpos(ConditionsParser.ValueUposContext ctx) {
        // String text = ctx.UPOS().getText();
        ConllWord use = getCW();
        //System.err.println("UPOSVALUE " + use);
        if (use == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        values.add(use.getUpostag());
        return values;
        //return use.getUpostag();
    }

    // @Xpos
    @Override
    public List<String> visitValueXpos(ConditionsParser.ValueXposContext ctx) {
        // String text = ctx.XPOS().getText();
        ConllWord use = getCW();
        //System.err.println("XPOSVALUE " + use);
        if (use == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        values.add(use.getXpostag());
        return values;
        //return use.getXpostag();
    }

    // @Feat
    @Override
    public List<String> visitValueFeat(ConditionsParser.ValueFeatContext ctx) {
        ConllWord use = getCW();
        //System.err.println("FEATVALUE " + use);
        if (use == null) {
            return null;
        }
   
        String text = ctx.getText();
        //System.err.println("FEAT " + text);
        String featurename = text.split(":")[1];
        if (featurename.isEmpty()) return null;

        String fval = use.getFeatures().get(featurename);
        if (fval != null) {
            List<String> values = new ArrayList<>();
            values.add(use.getFeatures().get(featurename));
            return values;
        }
        return null;
    }


    // $Deprel
    @Override
    public List<String> visitValueDeprel(ConditionsParser.ValueDeprelContext ctx) {
        ConllWord use = getCW();
        //System.err.println("DEPRELVALUE " + use);
        if (use == null) {
            return null;
        }

        List<String> values = new ArrayList<>();
        values.add(use.getDeplabel());
        return values;
        //return use.getDeplabel();
    }

}
