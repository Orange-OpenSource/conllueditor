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

import com.orange.labs.conllparser.ConllWord.EnhancedDeps;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class CEvalVisitor extends ConditionsBaseVisitor<Boolean> {
    ConllWord cword = null; // all conditions are checked on this word
    ConllWord pointedWord = null; // child/head etc are followed here
    int level = 0; // -1 head, -2 head's head
    int sequence = 0; // -1 word to the left, 1 word to the right etc

//    enum Movement {
//        UP, DOWN, LEFT, RIGHT
//    };
//    Stack<Movement> movements; // keep the directions we walk from current node to node to test
//    //Stack<Children> children; // a node may have more than one dependant. Here we have a counter to get through them all
//    Stack<Children> children;
    Map<String, Set<String>> wordlists; // stores lists for Form and Lemma: "filename": (words)

    public CEvalVisitor(ConllWord cword, Map<String, Set<String>> wordlists) {
        this.cword = cword;
        pointedWord =cword;
//        movements = new Stack<>();
//        children = new Stack<>();
        this.wordlists = wordlists;
    }

//    public class Children {
//
//        int index = 0; // current dependant to use
//        boolean allSeen = false; // set to true when we have seen all dependants
//
//        public String toString() {
//            return "CH:" + index + " " + allSeen;
//        }
//    }

    /**
     * get the correct ConllWord (current word, its head, head's head, preceing
     * or following). If there is no word at the end of the path (since the path
     * demanded is not in this tree), null is returned
     */
    private ConllWord getCW() {
        return pointedWord;

//        System.err.println("FROM " + cword);
//        ConllWord pointingTo = cword;
//        System.err.println("movements " + movements);
//        System.err.println("CHILDREN" );
//        for (Children ch : children) {
//            System.err.println("   " + ch);
//        }
//        int childrenindex = -1;
//        for (Movement m : movements) {
//            childrenindex++;
//            System.err.println("mov " + m + " ci " + childrenindex);
//            if (m == Movement.UP) {
//                if (pointingTo.getHeadWord() != null) {
//                    pointingTo = pointingTo.getHeadWord();
//                } else {
//                    return null; // no head (i.e. we are at root)
//                }
//            } else if (m == Movement.LEFT) {
//                if (pointingTo.getId() > 1) {
//                    pointingTo = cword.getMysentence().getWord(pointingTo.getId() - 1);
//                } else {for (Children ch : children) {
//            System.err.println("   " + ch);
//        }
//                    return null; // no preceding token
//                }
//            } else if (m == Movement.RIGHT) {
//                if (pointingTo.getId() < cword.getMysentence().size() - 1) {
//                    pointingTo = cword.getMysentence().getWord(pointingTo.getId() + 1);
//                } else {
//                    return null; // no following token
//                }
//            } else if (m == Movement.DOWN) {
//                Children currentchild = children.get(childrenindex);
//                //Children currentchild = children.lastElement()
//                if (pointingTo.getDependents() == null || pointingTo.getDependents().isEmpty()) {
//                    currentchild.allSeen = true; // we've seen all dependants
//                    System.err.println("D AAA " + currentchild);
//                    return null; // no dependants
//                } else {
//                    if (currentchild.index < pointingTo.getDependents().size()) {
//                        pointingTo = pointingTo.getDependents().get(currentchild.index);
//                        System.err.println("D CAN GO ON " +  currentchild + " TO " + pointingTo);
//                    } else {
//                        // no more dependants
//                        currentchild.allSeen = true; // we've seen all dependants
//                        System.err.println("D CCC " + currentchild);
//                        return null;
//                    }
//                }
//            }
//        }
//        System.err.println("TO   " + pointingTo);
//        return pointingTo;
    }

    @Override
    public Boolean visitPrintResult(ConditionsParser.PrintResultContext ctx) {
        boolean value = visit(ctx.expression()); // evaluate the expression child
        return value;
    }

    @Override
    public Boolean visitCheckUpos(ConditionsParser.CheckUposContext ctx) {
        String text = ctx.UPOS().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = use.matchesUpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckXpos(ConditionsParser.CheckXposContext ctx) {
        String text = ctx.XPOS().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = use.matchesXpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckID(ConditionsParser.CheckIDContext ctx) {
        String text = ctx.ID().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = (use.getId() == Integer.parseInt(text.substring(3)));
        return rtc;
    }


    @Override
    public Boolean visitCheckHeadID(ConditionsParser.CheckHeadIDContext ctx) {
        String text = ctx.HEADID().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc;
        text = text.substring(7); // cut "HeadId:"
        if (text.charAt(0) == '+' || text.charAt(0) == '-') {
            // relative head
            int relhead = Integer.parseInt(text);
            rtc = (use.getHead() == use.getId() + relhead );
        } else {
            // absolute head
            //int abshead = Integer.parseInt(text);
            rtc = (use.getHead() == Integer.parseInt(text));
        }
        return rtc;
    }


    @Override
    public Boolean visitCheckAbsEUD(ConditionsParser.CheckAbsEUDContext ctx) {
        String text = ctx.ABSEUD().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }

        if (use.getDeps().isEmpty()) return false;

        String [] elems = text.split(":", 3);
        int eudhead;
        if ("*".equals(elems[1])) {
            eudhead = -1; // any value allowed
        } else {
            eudhead = Integer.parseInt(elems[1]);
        }
        String euddeprel = elems[2];

        for (EnhancedDeps ehd : use.getDeps()) {
            if ((eudhead == -1 || ehd.headid == eudhead)
                    && ehd.deprel.equals(euddeprel)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Boolean visitCheckRelEUD(ConditionsParser.CheckRelEUDContext ctx) {
        String text = ctx.RELEUD().getText();

         ConllWord use = getCW();
        if (use == null) {
            return false;
        }

        if (use.getDeps().isEmpty()) return false;

        String [] elems = text.split(":", 3);
        int eudhead = 0;
        boolean anyheadid = false;
        if ("*".equals(elems[1])) {
            anyheadid = true; // any value allowed
        } else {
            eudhead = Integer.parseInt(elems[1]);
        }

        String euddeprel = elems[2];
        for (EnhancedDeps ehd : use.getDeps()) {
            if ((anyheadid || ehd.headid == use.getId() + eudhead)
                    && ehd.deprel.equals(euddeprel)) {
                return true;
            }
        }

        return false;
    }


    @Override
    public Boolean visitCheckMWT(ConditionsParser.CheckMWTContext ctx) {
        String text = ctx.MWT().getText();
        int wantedlength = Integer.parseInt(text.substring(4));
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        int subid = use.getSubid();
        if (subid < 2) {
            return false; // not a valid MWT
        }
        int reallength = subid + 1 - use.getId();
        boolean rtc = (reallength == wantedlength);
        return rtc;
    }

    public Boolean visitCheckEmpty(ConditionsParser.CheckEmptyContext ctx) {
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = (use.getTokentype() == ConllWord.Tokentype.EMPTY);
        return rtc;
    }

    public Boolean visitCheckIsMWT(ConditionsParser.CheckIsMWTContext ctx) {
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = (use.getTokentype() == ConllWord.Tokentype.CONTRACTED);
        return rtc;
    }

    private Set<String> readWordsFromFile(String filename) {
        try {
            FileInputStream fis = new FileInputStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            Set<String> words = new HashSet<>();
            String line;
            while ((line = br.readLine()) != null) {
                words.add(line);
            }
            br.close();
            return words;
        } catch (IOException e) {
            System.err.format("Cannot open wordlist <%s>: %s\n", filename, e.getMessage());
            return null;
        }

    }

    @Override
    public Boolean visitCheckLemma(ConditionsParser.CheckLemmaContext ctx) {
        String text = ctx.LEMMA().getText().substring(6);
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }

        if (wordlists != null && text.startsWith("#") && text.length() > 1) {
            String fn = text.substring(1);
            if (!wordlists.containsKey(fn)) {
                wordlists.put(fn, readWordsFromFile(fn));
            }
            Set<String> words = wordlists.get(fn);
            if (words != null && words.contains(use.getLemma())) {
                return true;
            } else {
                return false;
            }
        } else {
            boolean rtc = use.matchesLemma(text);
            return rtc;
        }
    }

    @Override
    public Boolean visitCheckForm(ConditionsParser.CheckFormContext ctx) {
        String text = ctx.FORM().getText().substring(5);
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }

        if (wordlists != null && text.startsWith("#") && text.length() > 1) {
            String fn = text.substring(1);
            if (!wordlists.containsKey(fn)) {
                wordlists.put(fn, readWordsFromFile(fn));
            }
            Set<String> words = wordlists.get(fn);
            if (words != null && words.contains(use.getForm())) {
                return true;
            } else {
                return false;
            }
        } else {
            boolean rtc = use.matchesForm(text);
            return rtc;
        }
    }

    @Override
    public Boolean visitCheckFeat(ConditionsParser.CheckFeatContext ctx) {
        String text = ctx.FEAT().getText();
        String[] fv = text.substring(5).split("[:=]");
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = use.matchesFeatureValue(fv[0], fv[1]);
        return rtc;
    }

    @Override
    public Boolean visitCheckMisc(ConditionsParser.CheckMiscContext ctx) {
        String text = ctx.MISC().getText();
        String[] fv = text.substring(5).split("=");
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = use.matchesMiscValue(fv[0], fv[1]);
        return rtc;
    }

    @Override
    public Boolean visitCheckDeprel(ConditionsParser.CheckDeprelContext ctx) {
        String text = ctx.DEPREL().getText();
        ConllWord use = getCW();
        if (use == null) {
            return false;
        }
        boolean rtc = use.matchesDeplabel(text.substring(7));
        return rtc;
    }

    /**
     * negation ! or ~
     */
    @Override
    public Boolean visitNicht(ConditionsParser.NichtContext ctx) {
        boolean value = visit(ctx.expression()); // evaluate the expr child
        return !value;
    }

    /**
     * 'head' '(' expression ')'
     */
    @Override
    public Boolean visitKopf(ConditionsParser.KopfContext ctx) {
        //movements.push(Movement.UP);
        ConllWord before = pointedWord;
        if (pointedWord.getHeadWord() != null) pointedWord = pointedWord.getHeadWord();
        else return false;
        //System.err.println("ME   " + before);
        //System.err.println("HEAD " + pointedWord);
        boolean rtc = visit(ctx.expression());
        //System.err.println("head rtc " + rtc);
        //movements.pop();
        pointedWord = before;
        return rtc;
    }

    @Override
    public Boolean visitChild(ConditionsParser.ChildContext ctx) {
        //System.err.println("\nVISITCHILD " + ctx.getText());
        //movements.push(Movement.DOWN);
        //children.push(new Children()); // current node may have more then one dependant, we count the one used and set allSeen to true when we are done
        //System.err.println("level " + children.size());
        ConllWord before = pointedWord;
        boolean rtc = false;
        for(ConllWord cw : pointedWord.getDependents()) {
            pointedWord = cw;
            rtc = visit(ctx.expression());
            if (rtc) break;
        }
        //System.err.println("UP VISITCHILD " + children);
        pointedWord = before;
        return rtc;
    }

    /**
     * 'prec' '(' expression ')'
     */
    @Override
    public Boolean visitVorher(ConditionsParser.VorherContext ctx) {
        //movements.push(Movement.LEFT);
        //boolean rtc = visit(ctx.expression());
        //movements.pop();
        int currentid = pointedWord.getId();
        if (currentid == 1) return false;

        ConllWord before = pointedWord;

        pointedWord = pointedWord.getMysentence().getWord(currentid-1);
        boolean rtc = visit(ctx.expression());
        pointedWord = before;
        return rtc;
    }

    /**
     * 'next' '(' expression ')'
     */
    @Override
    public Boolean visitNachher(ConditionsParser.NachherContext ctx) {
        //movements.push(Movement.RIGHT);
        //boolean rtc = visit(ctx.expression()); // return child expr's value
        //movements.pop();
        int currentid = pointedWord.getId();
        int size = pointedWord.getMysentence().getAllWords().size();
        int lastid = pointedWord.getMysentence().getAllWords().get(size-1).getId();
        if (currentid == lastid) return false;
        ConllWord before = pointedWord;
        pointedWord = pointedWord.getMysentence().getWord(currentid+1);
        //System.err.println("AAAAAA " + currentid + " " + size + " " + lastid);
        //System.err.println("BBBBBB " + before);
        //System.err.println("CCCCCC " + pointedWord);
        boolean rtc = visit(ctx.expression()); // return child expr's value
        //System.err.println("next rtc " + rtc);
        pointedWord = before;
        return rtc;
    }

    /**
     * '(' expression ')'
     */
    @Override
    public Boolean visitKlammern(ConditionsParser.KlammernContext ctx) {
        return visit(ctx.expression()); // return child expr's value
    }

    /**
     * expr op=('or') expr
     */
    @Override
    public Boolean visitUnd(ConditionsParser.UndContext ctx) {
        boolean left = visit(ctx.expression(0));  // get value of left subexpression
        boolean right = visit(ctx.expression(1)); // get value of right subexpression
        //if ( ctx.op.getType() == ConditionsParser.AND )
        return (left && right);
    }

    @Override
    public Boolean visitOder(ConditionsParser.OderContext ctx) {
        boolean left = visit(ctx.expression(0));  // get value of left subexpression
        boolean right = visit(ctx.expression(1)); // get value of right subexpression
        //if ( ctx.op.getType() == ConditionsParser.OR )
        return (left || right);
    }




    // $Deprel:$head(Deprel)
    // $Feat:Gender:$head(Feat:Gender)
    @Override
    public Boolean visitValcompare(ConditionsParser.ValcompareContext ctx) {
        CGetVisitor getvisitor = new CGetVisitor(cword, wordlists);
       
        String left = getvisitor.visit(ctx.columnname(0));  // get value of left columnname
        String right = getvisitor.visit(ctx.columnname(1));  // get value of right columnname
        //System.err.println("COMPARE " + left + " " + right);
        if (left == null || right == null) return false;
        return (left.equals(right));
        //return true;
    }

}
