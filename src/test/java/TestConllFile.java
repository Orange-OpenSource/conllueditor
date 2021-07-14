
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.editor.ConlluEditor;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

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
 @version 2.10.2 as of 22nd February 2021
 */
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
        //System.err.println("RRRRR " + rule + " " + Arrays.asList(newvals));
//        try {
        cf.conditionalEdit(rule, Arrays.asList(newvals));
//        } catch(Exception e) {
//            e.printStackTrace();
//            System.err.println("EEEEEEE " + e.getMessage());
//        }

        File out = new File(folder, filename);
        FileUtils.writeStringToFile(out, cf.toString(), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource(filename);

        Assert.assertEquals(String.format("Rule '%s' badly used\n ref: %s\n res: %s\n", rule, ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test01rule1() throws IOException, ConllException {
        name("rule 1");
        applyRule("Upos:ADP and Deprel:case", "xpos:prep", "rule1.conllu");
    }

    @Test
    public void test01rule2() throws IOException, ConllException {
        applyRule("Xpos:_ and Upos:VERB and !Xpos:PARTP and (Feat:Number=Plur or Feat:Number=Sing)", "xpos:verbfin", "rule2.conllu");
    }

    @Test
    public void test01rule3() throws IOException, ConllException {
        applyRule("Lemma:.*[^A-Za-z,\\._:0-9]+.*", "feat:Chars=NonAscii xpos:NONASCII", "rule3.conllu");
    }

    @Test
    public void test01rule4() throws IOException, ConllException {
        applyRule("MTW:2", "form:MTW2", "rule4.conllu");
    }

    @Test
    public void test01rule5() throws IOException, ConllException {
        applyRule("Empty", "xpos:EMPTY", "rule5.conllu");
    }

    @Test
    public void test01rule6() throws IOException, ConllException {
        applyRule("Upos:NOUN and (Feat:Number=Plur or Feat:Gender=Masc )", "misc:Noun=Plural_or_Masc", "rule6.conllu");
    }

    @Test
    public void test02head() throws IOException, ConllException {
        applyRule("(head(Upos:VERB) and !Upos:PUNCT)", "misc:Head=Verbal", "rule7.conllu");
    }

    @Test
    public void test02headshead() throws IOException, ConllException {
        applyRule("head(head(Upos:VERB and Feat:Tense=Pres ))", "misc:GrandmotherHead=Verbal", "rule8.conllu");
    }

    @Test
    public void test02headOfPreceding() throws IOException, ConllException {
        applyRule("prec(head(Upos:VERB))", "misc:HeadOfPreceding=Verbal", "rule9.conllu");
    }

    @Test
    public void test02PrecedingOfHead() throws IOException, ConllException {
        applyRule("head(prec(Upos:AUX))", "misc:PrecedingOfHead=Aux", "rule10.conllu");
    }

    @Test
    public void test02FollowingOfHead() throws IOException, ConllException {
        applyRule("head(next(Upos:NOUN))", "misc:FollowingOfHead=Noun", "rule11.conllu");
    }

    @Test
    public void test03Child1() throws IOException, ConllException {
        applyRule("child(Upos:VERB) and child(Upos:DET)", "misc:Deps=VERB+DET", "rule12.conllu");
    }

    @Test
    public void test03Child2() throws IOException, ConllException {
        applyRule("child(Upos:VERB && Feat:VerbForm=Part) and child(Upos:DET)", "misc:Deps=PARTC+DET", "rule13.conllu");
    }

    
    
    @Test
    public void test11badtoken() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("(Upos:ADP and Lemma )", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:14 token recognition error at: 'Lemma '";
            Assert.assertEquals(String.format("bad token not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test12badparenthesis() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("Upos:ADP and Xpos:prep )", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:23 extraneous input ')' expecting <EOF>";
            Assert.assertEquals(String.format("missing left parenthesis not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test13badparenthesis() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("(Upos:DET and Xpos:prep ", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:24 missing ')' at '<EOF>'";
            Assert.assertEquals(String.format("missing right parenthesis not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test14missingop() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("Upos:ADP  Xpos:prep ", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:10 extraneous input 'Xpos:prep' expecting <EOF>";
            Assert.assertEquals(String.format("missing operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test15doubleop() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("Upos:ADP and or Xpos:prep ", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:13 extraneous input 'or' expecting {'head', 'child', 'prec', 'next', UPOS, LEMMA, FORM, XPOS, DEPREL, FEAT, ID, MTW, 'Empty', NOT, '('}";
            Assert.assertEquals(String.format("double operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }

    @Test
    public void test16badNeg() throws IOException, ConllException {
        String[] newvals = "xpos:det".split(" ");
        try {
            cf.conditionalEdit("Upos:ADP !and Xpos:prep ", Arrays.asList(newvals));
        } catch (ConllException e) {
            String expected = "line 1:9 mismatched input '!' expecting {<EOF>, AND, OR}";
            Assert.assertEquals(String.format("double operator not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, e.getMessage()),
                    expected, e.getMessage());
        }
    }
    
}
