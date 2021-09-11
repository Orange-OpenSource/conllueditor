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
 @version 2.12.1 as of 11th September 2021
 */


package com.orange.labs.conllparser;


import java.util.Stack;


/**
 *
 * @author johannes
 */
public class CEvalVisitor extends ConditionsBaseVisitor<Boolean> {
    ConllWord cword = null; // all conditions are checked on this word
    int level = 0; // -1 head, -2 head's head
    int sequence = 0; // -1 word to the left, 1 word to the right etc
    enum Movement { UP, DOWN, LEFT, RIGHT};
    Stack<Movement>movements; // keep the directions we walk from current node to node to test
    Stack<Children>children; // a node may have more than one dependant. Here we have a counter to get through them all

    public CEvalVisitor(ConllWord cword) {
        this.cword = cword;
        movements = new Stack<>();
        children = new Stack<>();
    }


    public class Children {
        int index = 0; // current dependant to use
        boolean allSeen = false; // set to true when we have seen all dependants
    }


    /** get the correct ConllWord (current word, its head, head's head, preceing or following). If there is no word
     * at the end of the path (since the path demanded is not in this tree), null is returned
     */
    private ConllWord getCW() {
        //System.err.println("FROM " + cword);
        ConllWord pointingTo = cword;
        //System.err.println("movements " + movements);
        for (Movement m : movements) {
            //System.err.println("mov " + m);
            if (m == Movement.UP) {
                if (pointingTo.getHeadWord() != null) pointingTo = pointingTo.getHeadWord();
                else return null; // no head (i.e. we are at root)
            } else if (m == Movement.LEFT) {
                if (pointingTo.getId() > 1) {
                    pointingTo = cword.getMysentence().getWord(pointingTo.getId()-1);
                } else return null; // no preceding token
            } else if (m == Movement.RIGHT) {
                if (pointingTo.getId() < cword.getMysentence().size()-1) {
                    pointingTo = cword.getMysentence().getWord(pointingTo.getId()+1);
                } else return null; // no following token
            } else if (m == Movement.DOWN) {
                if (pointingTo.getDependents() == null || pointingTo.getDependents().isEmpty()) {
                    children.lastElement().allSeen = true; // we've seen all dependants
                    return null; // no dependants
                } else {
                    if (children.lastElement().index < pointingTo.getDependents().size())
                        pointingTo = pointingTo.getDependents().get(children.lastElement().index);
                    else {
                       // no more dependants
                       children.lastElement().allSeen = true; // we've seen all dependants
                       return null;
                   }
               }
            }
        }
        //System.err.println("TO   " + pointingTo);
        return pointingTo;
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
        if (use == null) return false;
        boolean rtc = use.matchesUpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckXpos(ConditionsParser.CheckXposContext ctx) {
        String text = ctx.XPOS().getText();
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesXpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckID(ConditionsParser.CheckIDContext ctx) {
        String text = ctx.ID().getText();
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = (use.getId() == Integer.parseInt(text.substring(3)));
        return rtc;
    }

    @Override
    public Boolean visitCheckMTW(ConditionsParser.CheckMTWContext ctx) {
        String text = ctx.MTW().getText();
        int wantedlength = Integer.parseInt(text.substring(4));
        ConllWord use = getCW();
        if (use == null) return false;
        int subid = use.getSubid();
        if (subid < 2) return false; // not a valid MWT
        int reallength = subid + 1 - use.getId();
        boolean rtc = (reallength == wantedlength);
        return rtc;
    }

    public Boolean visitCheckEmpty(ConditionsParser.CheckEmptyContext ctx) {
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = (use.getTokentype() == ConllWord.Tokentype.EMPTY);
        return rtc;
    }

    @Override
    public Boolean visitCheckLemma(ConditionsParser.CheckLemmaContext ctx) {
        String text = ctx.LEMMA().getText();
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesLemma(text.substring(6));
        return rtc;
    }

    @Override
    public Boolean visitCheckForm(ConditionsParser.CheckFormContext ctx) {
        String text = ctx.FORM().getText();
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesForm(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckFeat(ConditionsParser.CheckFeatContext ctx) {
        String text = ctx.FEAT().getText();
        String [] fv = text.substring(5).split("=");
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesFeatureValue(fv[0], fv[1]);
        return rtc;
    }

    @Override
    public Boolean visitCheckMisc(ConditionsParser.CheckMiscContext ctx) {
        String text = ctx.MISC().getText();
        String [] fv = text.substring(5).split("=");
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesMiscValue(fv[0], fv[1]);
        return rtc;
    }

    @Override
    public Boolean visitCheckDeprel(ConditionsParser.CheckDeprelContext ctx) {
        String text = ctx.DEPREL().getText();
        ConllWord use = getCW();
        if (use == null) return false;
        boolean rtc = use.matchesDeplabel(text.substring(7));
        return rtc;
    }


    /** negation ! or ~ */
    @Override
    public Boolean visitNicht(ConditionsParser.NichtContext ctx) {
        boolean value = visit(ctx.expression()); // evaluate the expr child
        return !value;
    }

    /** 'head' '(' expression ')' */
    @Override
    public Boolean visitKopf(ConditionsParser.KopfContext ctx) {
        movements.push(Movement.UP);
        boolean rtc = visit(ctx.expression());
        movements.pop();
        return rtc;
    }


    @Override
    public Boolean visitChild(ConditionsParser.ChildContext ctx) {
        movements.push(Movement.DOWN);
        children.push(new Children()); // current node may have more then on dependant, we count the one used and set allSeen to true when we are done

        boolean rtc = false;
        while(!children.lastElement().allSeen && !rtc) {
            rtc = visit(ctx.expression());
            //System.err.println("aaaaaaaa " + rtc + " " + children.lastElement().index + " " + children.lastElement().allSeen);
            children.lastElement().index++;
        }
        movements.pop();
        children.pop();
        return rtc;
    }



    /** 'prec' '(' expression ')' */
    @Override
    public Boolean visitVorher(ConditionsParser.VorherContext ctx) {
        movements.push(Movement.LEFT);
        boolean rtc = visit(ctx.expression());
        movements.pop();
        return rtc;
    }

    /** 'next' '(' expression ')' */
    @Override
    public Boolean visitNachher(ConditionsParser.NachherContext ctx) {
        movements.push(Movement.RIGHT);
        boolean rtc = visit(ctx.expression()); // return child expr's value
        movements.pop();
        return rtc;
    }

   /** '(' expression ')' */
    @Override
    public Boolean visitKlammern(ConditionsParser.KlammernContext ctx) {
        return visit(ctx.expression()); // return child expr's value
    }

    /** expr op=('or') expr */
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
}
