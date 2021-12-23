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
 @version 2.14.1 as of 22nd December 2021
 */
package com.orange.labs.conllparser;

//public class CEvalVisitor extends ConditionsBaseVisitor<Boolean> {
public class REvalVisitor extends ReplacementsBaseVisitor<String> {

    ConllWord cword = null; // all conditions are checked on this word
    ConllWord current = null;
    int level = 0; // -1 head, -2 head's head
    int sequence = 0; // -1 word to the left, 1 word to the right etc

    public REvalVisitor(ConllWord cword, String extractexpression) {
        this.cword = cword;
        current = cword;
    }

    public class Children {

        int index = 0; // current dependant to use
        boolean allSeen = false; // set to true when we have seen all dependants
    }

    @Override
    public String visitPrintResult(ReplacementsParser.PrintResultContext ctx) {
        //System.err.println("visitPrintresult " + ctx.getText());
        String value = visit(ctx.expression()); // evaluate the expression child
        return value;
    }

    @Override
    public String visitElement(ReplacementsParser.ElementContext ctx) {
        //System.err.println("visitElement " + ctx.getText());
        StringBuilder zeichenkette = new StringBuilder();
        for (int i = 0; i < ctx.token().size(); ++i) {
            String value = visit(ctx.token(i)); // evaluate the expression child
            if (value == null) {
                value = ctx.token(i).getText();
            }
            //System.err.println("element " + value);
            zeichenkette.append(value);
        }
        return zeichenkette.toString();
    }

//    @Override
//    public String visitTeil(ReplacementsParser.TeilContext ctx) {
//        //System.err.println("visitTeil " + ctx.getText());
//        String value = visit(ctx.substring()); // evaluate the expression child
//        //System.err.println("WORT " + value);
//        return value;
//    }

    @Override
    public String visitSubstr(ReplacementsParser.SubstrContext ctx) {
        //System.err.println("visitSubstring " + ctx.getText());
        String value = visit(ctx.token());
        int start = Integer.parseInt(ctx.NUMBER(0).getText());
        if (ctx.NUMBER().size() > 1) {
            int end = Integer.parseInt(ctx.NUMBER(1).getText());
            if (end < start || end > value.length()) {
                return value;
            }
            return value.substring(start, end);
        } else {
            if (start > value.length()) {
                return value;
            }
            return value.substring(start);
        }
    }

//    @Override
//    public String visitAendern(ReplacementsParser.AendernContext ctx) {
//        //System.err.println("visitTeil " + ctx.getText());
//        String value = visit(ctx.replace()); // evaluate the expression child
//        //System.err.println("WORT " + value);
//        return value;
//    }

    @Override
    public String visitRepl(ReplacementsParser.ReplContext ctx) {
        String value = visit(ctx.token());
        String from = visit(ctx.value(0));
        String to = visit(ctx.value(1));
        //System.err.println("REPL "+ value  + " " + from + "-->" + to);
        String res = value.replaceAll(from, to);

        return res;
    }

        @Override
    public String visitBlock(ReplacementsParser.BlockContext ctx) {
        String value = visit(ctx.token());

        //System.err.println("REPL "+ value  + " " + from + "-->" + to);
        String res = value.toUpperCase();

        return res;
    }

    public String visitKlein(ReplacementsParser.KleinContext ctx) {
        String value = visit(ctx.token());
        String res = value.toLowerCase();

        return res;
    }

    public String visitGross(ReplacementsParser.GrossContext ctx) {
        String value = visit(ctx.token());
        String res = value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();

        return res;
    }
    
    @Override
    public String visitWort(ReplacementsParser.WortContext ctx) {
        //System.err.println("visitWort " + ctx.getText());
        String value = visit(ctx.value()); // evaluate the expression child
        //System.err.println("WORT " + value);
        return value;
    }

    @Override
    public String visitWortohne(ReplacementsParser.WortohneContext ctx) {
        //System.err.println("visitWortohne " + ctx.getText());
        String value = ctx.getText(); // evaluate the expression child
        //StringBuilder zeichenkette = new StringBuilder();
        //System.err.println("C " + ctx.CHARS() + " " + value);       
//        for (int i = 0; i < ctx.CHARS().size(); ++i) {
//            zeichenkette.append(ctx.CHARS(i));
//        }
        //System.err.println("WORTOHNE " + zeichenkette.toString());
        return value.substring(1, value.length()-1);
        //return zeichenkette.toString();
    }

    @Override
    public String visitSpalte(ReplacementsParser.SpalteContext ctx) {
        //System.err.println("visitSpalte " + ctx.getText());
        String column = ctx.COLUMN().getText(); // evaluate the expression child
        //System.err.println("SPALTE " + column );

        return getColumn(cword, column);
    }

    private String getColumn(ConllWord cword, String column) {
        if ("Form".equals(column)) {
            return cword.getForm();
        } else if ("Lemma".equals(column)) {
            return cword.getLemma();
        } else if ("Upos".equals(column)) {
            return cword.getUpostag();
        } else if ("Xpos".equals(column)) {
            return cword.getXpostag();
        } else if ("Deprel".equals(column)) {
            return cword.getDeplabel();
        } else if ("HeadId".equals(column)) {
            return "" + cword.getHead();

        } else if (column.startsWith("Feat_")) {
            String val = cword.getFeatures().get(column.substring(5));
            if (val != null) {
                return val;
            }
            return "";
        } else if (column.startsWith("Misc_")) {
            Object val = cword.getMisc().get(column.substring(5));
            if (val != null) {
                return val.toString();
            }
            return "";
        }
        return column;
    }

    @Override
    public String visitKopf(ReplacementsParser.KopfContext ctx) {
        //System.err.println("visitKopf " + ctx.getText());
        String rtc = visit(ctx.head());
        //System.err.println("KOPF " + rtc);
        return rtc;
    }

    // Soemthing is wrong here: head(head()) does not work
    @Override
    public String visitKopfkopf(ReplacementsParser.KopfkopfContext ctx) {
        //System.err.println("visitKopfkopf " + ctx.getText());

        //System.err.println("KOPFKOPF   " + current);
        current = current.getHeadWord();
        //System.err.println("  KOPFKOPF " + current);
        String rtc = visit(ctx.inner);
        //System.err.println("          " + rtc);
        return rtc;
    }

    @Override
    public String visitKopfspalte(ReplacementsParser.KopfspalteContext ctx) {
        //System.err.println("visitKopfspalte " + ctx.getText());
        String column = ctx.COLUMN().getText();
        if (current == null) return "";
        ConllWord head = current.getHeadWord();

        if (head == null) {
            // no head, no column value
            return "";
        }
        return getColumn(head, column);
    }

}
