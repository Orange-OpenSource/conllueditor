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
 @version 2.20.0 as of 11th December 2022
 */

import com.orange.labs.conllparser.CheckGrewmatch;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestGrewmatch {

    File folder;
    ConllFile cf;
    ConllFile cf2;

    private void name(String n) {
        System.out.format("\n***** Testing: %s ****\n", n);
    }

    @Before
    public void setUp() throws ConllException, IOException {
        folder = new File("testoutput");
        folder.mkdir();

        URL url = this.getClass().getResource("test.conllu");
        File file = new File(url.getFile());
        URL url2 = this.getClass().getResource("projtest.conllu");
        File file2 = new File(url2.getFile());
        try {
            cf = new ConllFile(file, false, false);
            cf2 = new ConllFile(file2, false, false);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void applySearch(String rule, String filename) throws IOException, ConllException {
        applySearch(rule, filename, cf);
    }

    private void applySearch(String rule, String filename, ConllFile conllf) throws IOException, ConllException {
        StringBuilder results = new StringBuilder();
        try {
            CheckGrewmatch gc = new CheckGrewmatch(rule, false);
            for (ConllSentence cs : conllf.getSentences()) {
                cs.normalise();
                cs.makeTrees(null);
                List<List<ConllWord>> llcw = gc.match(null, cs);
                if (llcw != null) {
                    results.append("# sent_id = ").append(cs.getSentid()).append("\n# text = ").append(cs.getSentence()).append('\n');
                    results.append(gc.prettyprint(llcw));
                }
            }
        } catch (ConllException e) {
            e.printStackTrace();
            throw e;
        }

        if (filename != null) {
            File out = new File(folder, filename);
            FileUtils.writeStringToFile(out, results.toString(), StandardCharsets.UTF_8);

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
    }

    @Test
    public void test01() throws IOException, ConllException {
        name("search 1");
        applySearch("pattern { N [upos=NUM];}", "search1.conllu");
    }

    @Test
    public void test02() throws IOException, ConllException {
        name("search 2");
        applySearch("pattern { N [upos=NUM]; H -[nummod]-> N }", "search2.conllu");
    }

    @Test
    public void test03() throws IOException, ConllException {
        name("search 3");
        applySearch("pattern { V [upos=VERB]; V-[obj]-> N}", "search3.conllu");
    }

    @Test
    public void test04() throws IOException, ConllException {
        name("search 4");
        applySearch("pattern { V [upos=VERB]; V-[obj]-> N} without { V -[nsubj]-> N2}", "search4.conllu");
    }

    @Test
    public void test05() throws IOException, ConllException {
        name("search 5");
        applySearch("pattern { V [upos=VERB]; V-[obj]-> N; V -[nsubj]-> N2}", "search5.conllu");
    }

    @Test
    public void test06() throws IOException, ConllException {
        name("search 6");
        applySearch("pattern { N [upos=NOUN]; A [upos=ADJ]; D [upos=DET]; A > N}", "search6.conllu");
    }

    @Test
    public void test07() throws IOException, ConllException {
        name("search 7");
        applySearch("pattern { V -[obj|nsubj]-> N; N -[det]-> D}", "search7.conllu");
    }

    @Test
    public void test08() throws IOException, ConllException {
        name("search 8");
        applySearch("pattern { N [ upos=VERB, Mood=Ind, Person=\"1\" ] }", "search8.conllu");
    }

    @Test
    public void test10() throws IOException, ConllException {
        name("search 10");
        applySearch("pattern { N [ upos=VERB, Mood=Ind, Person<>\"1\" ] }", "search10.conllu");
    }

    @Test
    public void test11() throws IOException, ConllException {
        name("search 11");
        applySearch("pattern { N [ upos=VERB, Tense ] }", "search11.conllu");
    }

    @Test
    public void test12() throws IOException, ConllException {
        name("search 12");
        applySearch("pattern { N [ upos=VERB, !Tense ] }", "search12.conllu");
    }

    @Test
    public void test13() throws IOException, ConllException {
        name("search 13");
        applySearch("pattern { V [ upos=VERB] } without {V -[nsubj]-> N} ", "search13.conllu");
    }

    @Test
    public void test14() throws IOException, ConllException {
        name("search 14");
        applySearch("pattern { V [ upos=VERB]; V -[nsubj]-> N} without { N [upos=NOUN]} ", "search14.conllu");
    }

    @Test
    public void test15() throws IOException, ConllException {
        name("search 15");
        applySearch("pattern { V -[aux]-> AP; P [lemma=dans]; V -[obl]-> N; N -[case]-> P; }", "search15.conllu");
    }

    @Test
    public void test16() throws IOException, ConllException {
        name("search 16");
        applySearch("pattern { V -[obj]-> N;  V < N;}", "search16.conllu");
    }
    @Test
    public void test17() throws IOException, ConllException {
        name("search 17");
        applySearch("pattern { V -[obj]-> N;  N > V;}", "search16.conllu");
    }

    @Test
    public void test18() throws IOException, ConllException {
        name("search 18");
        applySearch("pattern { V -[obj]-> N;  N >> V;}", "search18.conllu");
    }

    @Test
    public void test19() throws IOException, ConllException {
        name("search 19");
        applySearch("pattern { V -[obj]-> N;  N >> V;} without { N > V} ", "search19.conllu");
    }

    @Test
    public void test20() throws IOException, ConllException {
        name("search 20");
        applySearch("pattern { V -[obl]-> N} without {N << V} ", "search20.conllu");
    }

    @Test
    public void test21() throws IOException, ConllException {
        name("search 21");
        applySearch("pattern{V[lemma=\"être\"]}", "search21.conllu");
    }

    @Test
    public void test22() throws IOException, ConllException {
        name("search 22");
        applySearch("pattern { V [upos=VERB] } without { V -[nsubj]-> S; V -[obj]-> O }", "search22.conllu");
    }

    @Test
    public void test23() throws IOException, ConllException {
        name("search 23");
        applySearch("pattern { V [upos=VERB] } without { V -[nsubj]-> S} without { V -[obj]-> O }", "search23.conllu");
    }

    @Test
    public void test24() throws IOException, ConllException {
        name("search 24");
        applySearch("pattern { N -[nsubj]-> M; N.Number = M.Number}", "search24.conllu");
    }

    @Test
    public void test25() throws IOException, ConllException {
        name("search 25");
        applySearch("pattern { N -[nsubj]-> M; N.Number <> M.Number}", "search25.conllu");
    }

    @Test
    public void test26() throws IOException, ConllException {
        name("search 26");
        applySearch("pattern { N -[nsubj]-> M; N.Number <> M.BadFeature}", "search26.conllu");
    }

    @Test
    public void test27() throws IOException, ConllException {
        name("search 27");
        applySearch("global { is_tree }", "search27.conllu", cf2);
    }

    @Test
    public void test28() throws IOException, ConllException {
        name("search 28");
        applySearch("global { is_not_tree }", "search28.conllu", cf2);
    }

    @Test
    public void test28a() throws IOException, ConllException {
        name("search 28a");
        applySearch("global { is_not_tree } pattern { N [upos=NOUN]}", "search28.conllu", cf2);
    }

    @Test
    public void test28b() throws IOException, ConllException {
        name("search 28b");
        applySearch("global { is_not_tree } pattern { N [upos=VERB]}", "search28.conllu", cf2);
    }


    @Test
    public void test29() throws IOException, ConllException {
        name("search 29");
        applySearch("global { is_projective }", "search29.conllu", cf2);
    }

    @Test
    public void test30() throws IOException, ConllException {
        name("search 30");
        applySearch("global { is_not_projective }", "search30.conllu", cf2);
    }

    @Test
    public void test30a() throws IOException, ConllException {
        name("search 30a");
        applySearch("global { is_not_projective } pattern { N [upos=NOUN]}", "search30a.conllu", cf2);
    }

    @Test
    public void test31() throws IOException, ConllException {
        name("search 31");
        applySearch("pattern { V -[obj|subj]-> N}", "search31.conllu", cf2);
    }

    @Test
    public void test32() throws IOException, ConllException {
        name("search 32");
        applySearch("pattern { V -[^obj|subj]-> N}", "search32.conllu", cf2);
    }


    @Test
    public void testerror1() throws IOException, ConllException {
        name("error 1");
        String msg = null;
        try {
            applySearch("pattern { V -[obl-> N}",  null);
        } catch (Exception e) {
            msg = e.getMessage();
        }

        String expected = "pos:17 mismatched input '->' expecting {'|', ']->'}";
        Assert.assertEquals(String.format("missing right bracket not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, msg),
        expected, msg);

    }

    @Test
    public void testerror2() throws IOException, ConllException {
        name("error 2");
        String msg = null;
        try {
            applySearch("pattern { V -[obl]-> N} without N << V}",  null);
        } catch (Exception e) {
            msg = e.getMessage();
        }

        String expected = "pos:32 missing '{' at 'N'";
        Assert.assertEquals(String.format("missing left brace not detected\n ref: <<%s>>\n res: <<%s>>\n", expected, msg),
        expected, msg);
    }


    @Test
    public void testerror3() throws IOException, ConllException {
        name("error 3");
        String msg = null;
        try {
            applySearch("pattern { V[lemma=être]} ", null);
        } catch (Exception e) {
            msg = e.getMessage();
        }

        String expected = "pos:18 token recognition error at: 'ê'";
        Assert.assertEquals(String.format("missing quotes for non-ASCII characters\n ref: <<%s>>\n res: <<%s>>\n", expected, msg),
        expected, msg);
    }

    @Test
    public void testerror4() throws IOException, ConllException {
        name("error 4");
        String msg = null;
        try {
            applySearch("pattern { N -[nsubj]-> M; N.Number <> MM.Number}", null);
        } catch (Exception e) {
            msg = e.getMessage();
        }

        String expected = "Identifier MM not found";
        Assert.assertEquals(String.format("invalid query\n ref: <<%s>>\n res: <<%s>>\n", expected, msg),
        expected, msg);
    }


}
