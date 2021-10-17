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
        for (int i = 0; i< ctx.token().size(); ++i) {
            String value = visit(ctx.token(i)); // evaluate the expression child
            if (value == null) value = ctx.token(i).getText();
            //System.err.println("element " + value);
            zeichenkette.append(value);
        }
        return zeichenkette.toString();
    }
    

    @Override
    public String visitTeil(ReplacementsParser.TeilContext ctx) {
        //System.err.println("visitTeil " + ctx.getText());
        String value = visit(ctx.substring()); // evaluate the expression child
        //System.err.println("WORT " + value);
        return value;
    }

    @Override
    public String visitSubstr(ReplacementsParser.SubstrContext ctx) {
        //System.err.println("visitSubstring " + ctx.getText());
        String value = visit(ctx.token()); 
        int start = Integer.parseInt(ctx.NUMBER(0).getText());
        int end = Integer.parseInt(ctx.NUMBER(1).getText());
        if (end < start || end>value.length()) return value;
        //System.err.println("SUBSTR " + value+ " " +  + " " + ctx.NUMBER(1));
        return value.substring(start,end);
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
        StringBuilder zeichenkette = new StringBuilder();
        for (int i = 0; i< ctx.CHAR().size(); ++i) {
            zeichenkette.append(ctx.CHAR(i));
        }
        //System.err.println("WORTOHNE " + zeichenkette.toString());
        return zeichenkette.toString();
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
        }
        else if ("Lemma".equals(column)) {
            return cword.getLemma();
        }
        else if ("Upos".equals(column)) {
            return cword.getUpostag();
        }
        else if ("Xpos".equals(column)) {
            return cword.getXpostag();
        }
        else if ("Deprel".equals(column)) {
            return cword.getDeplabel();
        }
        else if (column.startsWith("Feat_")) {
            String val = cword.getFeatures().get(column.substring(5));
            if (val != null) return val;
            return "";
        }
        else if (column.startsWith("Misc_")) {
            String val = cword.getFeatures().get(column.substring(5));
            if (val != null) return val;
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
        ConllWord head = current.getHeadWord();


        if (head == null) return "";
        return getColumn(head, column);
    }
    

}

