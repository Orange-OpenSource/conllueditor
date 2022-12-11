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
import com.orange.labs.conllparser.GetReplacement;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

    private void name(String n) {
        System.out.format("\n***** Testing: %s ****\n", n);
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

    private void applySearch(String rule, String filename) throws IOException, ConllException {
        StringBuilder results = new StringBuilder();
        try {
            CheckGrewmatch gc = new CheckGrewmatch(rule, false);
            for (ConllSentence cs : cf.getSentences()) {
                cs.normalise();
                cs.makeTrees(null);
                List<List<ConllWord>> llcw = gc.match(null, cs);
                if (llcw != null) {
                    results.append("# sent_id = ").append(cs.getSentid()).append("\n# text = ").append(cs.getSentence()).append('\n');
                    results.append(gc.prettyprint(llcw));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        File out = new File(folder, filename);
        FileUtils.writeStringToFile(out, results.toString(), StandardCharsets.UTF_8);
//        if (warnings.length() > 0) {
//            FileUtils.writeStringToFile(out, warnings.toString(), StandardCharsets.UTF_8, true);
//        }

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

}
