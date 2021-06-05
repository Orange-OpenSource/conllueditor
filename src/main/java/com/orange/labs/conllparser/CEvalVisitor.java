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
 @version 2.12.0 as of 5th June 2021
 */


package com.orange.labs.conllparser;


/**
 *
 * @author johannes
 */
public class CEvalVisitor extends ConditionsBaseVisitor<Boolean> {
    ConllWord cword = null; // all conditions are checked on this word

    public CEvalVisitor(ConllWord cword) {
        this.cword = cword;
    }


    @Override
    public Boolean visitPrintResult(ConditionsParser.PrintResultContext ctx) {
        boolean value = visit(ctx.expression()); // evaluate the expression child
        return value;
    }

    @Override
    public Boolean visitCheckUpos(ConditionsParser.CheckUposContext ctx) {
        String text = ctx.UPOS().getText();
        boolean rtc = cword.matchesUpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckXpos(ConditionsParser.CheckXposContext ctx) {
        String text = ctx.XPOS().getText();
        boolean rtc = cword.matchesXpostag(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckID(ConditionsParser.CheckIDContext ctx) {
        String text = ctx.ID().getText();
        boolean rtc = (cword.getId() == Integer.parseInt(text.substring(3)));
        return rtc;
    }

    @Override
    public Boolean visitCheckMTW(ConditionsParser.CheckMTWContext ctx) {
        String text = ctx.MTW().getText();
        int wantedlength = Integer.parseInt(text.substring(4));
        int subid = cword.getSubid();
        if (subid < 2) return false; // not a valid MWT
        int reallength = subid + 1 - cword.getId();
        boolean rtc = (reallength == wantedlength);
        return rtc;
    }

    public Boolean visitCheckEmpty(ConditionsParser.CheckEmptyContext ctx) {
        boolean rtc = (cword.getTokentype() == ConllWord.Tokentype.EMPTY);
        return rtc;
    }

    @Override
    public Boolean visitCheckLemma(ConditionsParser.CheckLemmaContext ctx) {
        String text = ctx.LEMMA().getText();
        boolean rtc = cword.matchesLemma(text.substring(6));
        return rtc;
    }

    @Override
    public Boolean visitCheckForm(ConditionsParser.CheckFormContext ctx) {
        String text = ctx.FORM().getText();
        boolean rtc = cword.matchesForm(text.substring(5));
        return rtc;
    }

    @Override
    public Boolean visitCheckFeat(ConditionsParser.CheckFeatContext ctx) {
        String text = ctx.FEAT().getText();
        String [] fv = text.substring(5).split("=");
        boolean rtc = cword.matchesFeatureValue(fv[0], fv[1]);
        return rtc;
    }

    @Override
    public Boolean visitCheckDeprel(ConditionsParser.CheckDeprelContext ctx) {
        String text = ctx.DEPREL().getText();
        boolean rtc = cword.matchesDeplabel(text.substring(7));
        return rtc;
    }


    @Override
    public Boolean visitNicht(ConditionsParser.NichtContext ctx) {
        boolean value = visit(ctx.expression()); // evaluate the expr child
        return !value;
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
