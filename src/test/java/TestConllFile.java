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
 @version 2.18.1 as of 29th October 2022
*/

import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestConllFile {

    File folder;
    ConllFile cf;

    private void name(String n) {
        System.out.format("\n***** Testing: %S ****\n", n);
    }

    @Before
    public void setUp() throws ConllException, IOException {
        folder = new File("testoutput");
        folder.mkdir();

        URL url = this.getClass().getResource("test.conllu");
        File file = new File(url.getFile());
        try {
            cf = new ConllFile(file, false, false);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void applyRule(String rule, String newval, String filename) throws IOException, ConllException {
        String[] newvals = newval.split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit(rule, Arrays.asList(newvals), null, warnings);
        } catch(Exception e) {
            e.printStackTrace();
        }

        File out = new File(folder, filename);
        FileUtils.writeStringToFile(out, cf.toString(), StandardCharsets.UTF_8);
        if (warnings.length() > 0) {
            FileUtils.writeStringToFile(out, warnings.toString(), StandardCharsets.UTF_8, true);
        }

        try {
        URL ref = this.getClass().getResource(filename);

        Assert.assertEquals(String.format("Rule '%s' badly used\n ref: %s\n res: %s\n", rule, ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("File missing", filename, "");
        }
    }

    private void applyRules(String [] rule, String [] newval, String filename) throws IOException, ConllException {
        StringBuilder warnings = new StringBuilder();
        for (int x=0; x<rule.length; ++x) {
            String[] newvals = newval[x].split(" ");
            try {
                cf.conditionalEdit(rule[x], Arrays.asList(newvals), null, warnings);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        File out = new File(folder, filename);
        FileUtils.writeStringToFile(out, cf.toString(), StandardCharsets.UTF_8);
        if (warnings.length() > 0) {
            FileUtils.writeStringToFile(out, warnings.toString(), StandardCharsets.UTF_8, true);
        }

        URL ref = this.getClass().getResource(filename);

        Assert.assertEquals(String.format("Rule '%s' badly used\n ref: %s\n res: %s\n", rule, ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }



    @Test
    public void test01rule1() throws IOException, ConllException {
        name("rule 1");
        applyRule("Upos:ADP and Deprel:case", "xpos:\"prep\"", "rule1.conllu");
    }

    @Test
    public void test01rule2() throws IOException, ConllException {
        name("rule 2");
        applyRule("Xpos:_ and Upos:VERB and !Xpos:PARTP and (Feat:Number=Plur or Feat:Number=Sing)", "xpos:\"verbfin\"", "rule2.conllu");
    }

    @Test
    public void test01rule3() throws IOException, ConllException {
        name("rule 3");
        applyRule("Lemma:.*[^A-Za-z,\\._:0-9]+.*", "feat:\"Chars=NonAscii\" xpos:\"NONASCII\"", "rule3.conllu");
    }

    @Test
    public void test01rule4() throws IOException, ConllException {
        name("rule 4");
        applyRule("MWT:2", "form:\"MWT2\"", "rule4.conllu");
    }

    @Test
    public void test01rule5() throws IOException, ConllException {
         name("rule 5");
        applyRule("IsEmpty", "xpos:\"EMPTY\"", "rule5.conllu");
    }

    @Test
    public void test01rule5b() throws IOException, ConllException {
         name("rule 5b");
        applyRule("IsMWT", "MISC:\"MWT=Yes\"", "rule5b.conllu");
    }


    @Test
    public void test01rule6() throws IOException, ConllException {
         name("rule 6");
        applyRule("Upos:NOUN and (Feat:Number=Plur or Feat:Gender=Masc )", "misc:\"Noun=Plural_or_Masc\"", "rule6.conllu");
    }

    @Test
    public void test01rule7() throws IOException, ConllException {
         name("rule 7");
        applyRule("Misc:SpaceAfter=No and Lemma:.*[aeiou]", "misc:\"FinalVowel=Yes\"", "rule6b.conllu");
    }

    @Test
    public void test01rule8() throws IOException, ConllException {
         name("rule 8");
        applyRule("Upos:NOUN", "feat:\"Number=\"", "rule6c.conllu");
    }

    @Test
    public void test01rule9() throws IOException, ConllException {
        name("rule 01-9: test condtions for heads/euds");
        //applyRule("RelHeadId:-2", "Misc:\"RelHead=-2\"", "rule01-9.conllu");
        String [] rules = {"HeadId:-2",
                           "HeadId:+2",
                           "HeadId:9",
                           "EUD:-4:obj",
                           "EUD:*:nsubj",
                           "EUD:3:conj"};
        String [] newvals = {"Misc:\"RelHead=-2\"",
                             "Misc:\"RelHead=2\"",
                             "Misc:\"AbsHead=9\"",
                             "Misc:\"RelEUD=-4_obj\"",
                             "Misc:\"RelEUD=*_nsubj\"",
                             "Misc:\"AbsEUD=3_conj\"",
        };
        applyRules(rules,
                   newvals,
                   "rule01-9.conllu");
    }

    @Test
    public void test01rule10() throws IOException, ConllException {
        name("rule 01-10: test replacements for heads/euds");
        //applyRule("RelHeadId:-2", "Misc:\"RelHead=-2\"", "rule01-9.conllu");
        String [] rules = {"Upos:DET",
                           "Upos:NOUN",
                           "Upos:ADJ",
                           "Deprel:nsubj",
                           "Upos:PROPN",
                           "Upos:ADV"};
        String [] newvals = {"HeadId:\"-2\" Misc:\"Head=-2\"",
                             "HeadId:\"4\" Misc:\"Head=4\"",
                             "HeadId:head(HeadId) Misc:\"Head=Head_HeadId\"",
                             "Eud:head(HeadId)+\":\"+head(Deprel) Misc:\"EUD=Head_headid\"",
                             "HeadId:\"+2\" Misc:\"Head=+2\"",
                             "Eud:\"-1:before\" Misc:\"EUD=Head-1\""
                           };
        applyRules(rules,
                   newvals,
                   "rule01-10.conllu");
    }


    @Test
    public void test02head() throws IOException, ConllException {
        name("rule head");
        applyRule("(head(Upos:VERB) and !Upos:PUNCT)", "misc:\"Head=Verbal\"", "rule7.conllu");
    }

    @Test
    public void test02headshead() throws IOException, ConllException {
         name("rule headshead");
        applyRule("head(head(Upos:VERB and Feat:Tense=Pres ))", "misc:\"GrandmotherHead=Verbal\"", "rule8.conllu");
    }

    @Test
    public void test02headOfPreceding() throws IOException, ConllException {
         name("rule headprec");
        applyRule("prec(head(Upos:VERB))", "misc:\"HeadOfPreceding=Verbal\"", "rule9.conllu");
    }

    @Test
    public void test02PrecedingOfHead() throws IOException, ConllException {
         name("rule prec head");
        applyRule("head(prec(Upos:AUX))", "misc:\"PrecedingOfHead=Aux\"", "rule10.conllu");
    }

    @Test
    public void test02FollowingOfHead() throws IOException, ConllException {
         name("rule following of head");
        applyRule("head(next(Upos:NOUN))", "misc:\"FollowingOfHead=Noun\"", "rule11.conllu");
    }

    @Test
    public void test03Child1() throws IOException, ConllException {
         name("rule child1");
        applyRule("child(Upos:VERB) and child(Upos:DET)", "misc:\"Deps=VERB+DET\"", "rule12.conllu");
    }

    @Test
    public void test03Child2() throws IOException, ConllException {
        name("rule child2");
        applyRule("child(Upos:VERB && Feat:VerbForm=Part) and child(Upos:DET)", "misc:\"Deps=PARTC+DET\"", "rule13.conllu");
    }



    @Test
    public void test11badtoken() throws IOException, ConllException {
        name("test badtoken");
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("(Upos:ADP and Lemma )", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:14 token recognition error at: 'Lemma '";
            Assert.assertEquals(String.format("bad token not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test12badparenthesis() throws IOException, ConllException {
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("Upos:ADP and Xpos:prep )", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:23 extraneous input ')' expecting <EOF>";
            Assert.assertEquals(String.format("missing left parenthesis not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test13badparenthesis() throws IOException, ConllException {
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("(Upos:DET and Xpos:prep ", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:24 missing ')' at '<EOF>'";
            Assert.assertEquals(String.format("missing right parenthesis not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test14missingop() throws IOException, ConllException {
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("Upos:ADP  Xpos:prep ", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:10 extraneous input 'Xpos:prep' expecting <EOF>";
            Assert.assertEquals(String.format("missing operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test15doubleop() throws IOException, ConllException {
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("Upos:ADP and or Xpos:prep ", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:13 extraneous input 'or' expecting {'head', 'child', 'prec', 'next', UPOS, LEMMA, FORM, XPOS, DEPREL, FEAT, MISC, ID, MWT, HEADID, RELEUD, ABSEUD, 'IsEmpty', 'IsMWT', NOT, '(', '@Upos', '@Xpos', '@Deprel', CFEAT}";
            Assert.assertEquals(String.format("double operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test16badNeg() throws IOException, ConllException {
        String[] newvals = "xpos:\"det\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("Upos:ADP !and Xpos:prep ", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "pos:9 mismatched input '!' expecting {<EOF>, AND, OR}";
            Assert.assertEquals(String.format("double operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }


    @Test
    public void test171badAbsHeadId() throws IOException, ConllException {
        String[] newvals = "HeadId:\"titi\"".split(" ");
        StringBuilder warnings = new StringBuilder();
        try {
            cf.conditionalEdit("Upos:NOUN", Arrays.asList(newvals), null, warnings);
        } catch (ConllException e) {
            String expected = "invalid absolute head id, must be positive integer or 0 <titi>";
            Assert.assertEquals(String.format("band absolute HeadId not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

//    @Test
//    public void test172badRelHeadId() throws IOException, ConllException {
//        String[] newvals = "HeadId:\"+0\"".split(" ");
//        StringBuilder warnings = new StringBuilder();
//        try {
//            cf.conditionalEdit("Upos:NOUN", Arrays.asList(newvals), null, warnings);
//        } catch (ConllException e) {
//            String expected = "bad relative head id, must be a negative or positive integer excluding 0 <+0>";
//            Assert.assertEquals(String.format("band relative HeadId not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
//                    expected, e.getMessage());
//        }
//    }

    /* testing replacement grammar */

    @Test
    public void test20repl01() throws IOException, ConllException {
        name("repl 01");
        applyRule("Upos:VERB", "xpos:this(Lemma)", "rule20.conllu");
    }

    @Test
    public void test20repl02() throws IOException, ConllException {
        name("repl 02");
        applyRule("Upos:VERB", "xpos:this(Lemma)+\"INF\" feat:\"Number=\"+this(Form)", "rule21.conllu");
    }

    @Test
    public void test20repl03() throws IOException, ConllException {
        name("repl 03");
        applyRule("Upos:NOUN", "lemma:upper(this(Lemma)) feat:\"Number=\"+head(Lemma)", "rule22.conllu");
    }

    @Test
    public void test20repl04() throws IOException, ConllException {
        name("repl 04");
        applyRule("Upos:DET", "xpos:substring(upper(head(head(Form))),2) misc:\"HeadHeadLemma=\"+head(Lemma)", "rule23.conllu");
    }

    @Test
    public void test20repl05() throws IOException, ConllException {
        name("repl 05");
        applyRule("Upos:VERB", "lemma:\"préfix_\"+replace(this(Form),\"[aeiouy]\",\"V\") feat:\"LowerHeadLemma=\"+lower(head(Lemma))", "rule24.conllu");
    }

    @Test
    public void test20repl06() throws IOException, ConllException {
        name("repl 06");
        applyRule("Misc:Enhanced=.*", "xpos:\"TOTO\" feat:\"EUD=\"+this(Misc_Enhanced)", "rule25.conllu");
    }

    @Test
    public void test20repl07() throws IOException, ConllException {
        name("repl 07");
        applyRule("Upos:VERB", "feat:\"InlfClass=\"+this(Feat_Number) feat:\"Number=\"", "rule26.conllu");
    }


    @Test
    public void test21value01() throws IOException, ConllException {
        name("value 01");
        applyRule("@Upos=head(@Upos)", "Misc:\"FOUND=UPOS_HEADUPOS\"", "value01.conllu");
    }

    @Test
    public void test21value02() throws IOException, ConllException {
        name("value 02");
        applyRule("head(@Upos)=@Upos", "Misc:\"FOUND=HEADUPOS_UPOS\"", "value02.conllu");
    }

    @Test
    public void test21value03() throws IOException, ConllException {
        name("value 03");
        applyRule("@Deprel=head(head(@Deprel))", "Misc:\"FOUND=DEPREL_HEADHEADDEPREL\"", "value03.conllu");
    }

    @Test
    public void test21value04() throws IOException, ConllException {
        name("value 04");
        applyRule("@Feat:Gender=head(@Feat:Gender)", "Misc:\"FOUND=GENDER_HEADGENDER\"", "value04.conllu");
    }

    @Test
    public void test21value05() throws IOException, ConllException {
        name("value 05");
        applyRule("prec(@Deprel)=next(@Deprel)", "Misc:\"FOUND=PRECDEP_NEXTDEP\"", "value05.conllu");
    }

    @Test
    public void test21value06() throws IOException, ConllException {
        name("value 06");
        applyRule("prec(@Deprel)=prec(prec(@Deprel))", "Misc:\"FOUND=PRECDEP_PRECPRECDEP\"", "value06.conllu");
    }
    @Test

    public void test21value07() throws IOException, ConllException {
        name("value 07");
        applyRule("next(@Upos)=next(next(@Upos))", "Misc:\"FOUND=NEXTUPOS_NEXTNEXTUPOS\"", "value07.conllu");
    }

    @Test
    public void test21value08() throws IOException, ConllException {
        name("value 08");
        applyRule("child(@Upos)=@Upos", "Misc:\"FOUND=CHILDUPOS_UPOS\"", "value08.conllu");
    }

    @Test
    public void test21value09() throws IOException, ConllException {
        name("value 09");
        applyRule("@Feat:Gender=head(@Feat:Gender) and not (Upos:DET or Upos:NOUN)", "Misc:\"FOUND=GENDER_NOTUPOS\"", "value09.conllu");
    }

    @Test
    public void test22value01() throws IOException, ConllException {
        name("compatible value 01");
        applyRule("@Feat:Gender~head(@Feat:Gender) and @Feat:Gender=@Feat:Gender", "Misc:\"FOUND=GENDER_COMPAT\"", "value10.conllu");
    }    




    @Test
    public void test30mass_edit() throws IOException, ConllException {
        name("mass edit");

        URL sr = this.getClass().getResource("search_replace.txt");
        File srfile = new File(sr.getFile());
        cf.conditionalEdit(srfile);

        File out = new File(folder, "search_replace.conllu");
        FileUtils.writeStringToFile(out, cf.toString(), StandardCharsets.UTF_8);
       

        try {
            URL ref = this.getClass().getResource("search_replace.conllu");

            Assert.assertEquals(String.format("search & replace incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("File missing", "search_replace.conllu", "");
        }

    }
    

}

