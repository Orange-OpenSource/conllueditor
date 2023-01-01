/* This library is under the 3-Clause BSD License

Copyright (c) 2021-2022, Orange S.A.

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
 @version 2.20.0 as of 23rd December 2022
 */

import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.SDParse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestConllSentence {

    File folder;

    private void name(String n) {
        System.out.format("\n***** Testing: %s ****\n", n);
    }


    @Before
    public void setUp() throws ConllException, IOException {
        folder = new File("testoutput");
        folder.mkdir();
    }

    @Test
    public void test01readSDparse() throws IOException, ConllException {
        name("read sd-parse without position indicators");

        SDParse sdp = new SDParse("the little sleeps mouse\nnsubj(sleeps, mouse)\ndet(mouse, the)\namod(mouse, little)");

        String ref = "# text = the little sleeps mouse\n"+
                     "1	the	_	_	_	_	4	det	_	_\n"+
                     "2	little	_	_	_	_	4	amod	_	_\n"+
                     "3	sleeps	_	_	_	_	0	root	_	_\n"+
                     "4	mouse	_	_	_	_	3	nsubj	_	_\n\n";
         Assert.assertEquals("read sd-parse", ref, sdp.getSentence().toString());

    }

    private void parse(String infile, String reffn) throws IOException, ConllException {
        URL inputurl = this.getClass().getResource(infile);
        String input = FileUtils.readFileToString(new File(inputurl.getFile()), StandardCharsets.UTF_8);

        File out = new File(folder, reffn);

        SDParse sdp = new SDParse(input);

        FileUtils.writeStringToFile(out, sdp.getSentence().toString(), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource(reffn);

        Assert.assertEquals(String.format("Find head id too big return incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test02readSDparse() throws IOException, ConllException {
        name("read sd-parse with position indicators");
        parse("sdparse2.txt", "sdparse2.conllu");
    }

    @Test
    public void test03readSDparse() throws IOException, ConllException {
        name("read sd-parse with position indicators and POS");
        parse("sdparse3.txt", "sdparse3.conllu");
    }


    @Test
    public void test10cycles() throws IOException, ConllException {
        URL url = this.getClass().getResource("badtrees.conllu");
        File file = new File(url.getFile());
        ConllFile cf = new ConllFile(file, false, false);
        StringBuilder sb = new StringBuilder();
        for (ConllSentence csent : cf.getSentences()) {
            try {
                csent.makeTrees(null);
            } catch (ConllException e) {
                sb.append(csent.getSentid()).append(": ").append(e.getMessage()).append('\n');
            }
        }
        File out = new File(folder, "tree-errors.txt");
        FileUtils.writeStringToFile(out, sb.toString(), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("tree-errors.txt");

        Assert.assertEquals(String.format("tree errors not detected correctly\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void testProjectivity() throws IOException, ConllException {
        URL url = this.getClass().getResource("projtest.conllu");
        File file = new File(url.getFile());
        ConllFile cf = new ConllFile(file, false, false);
        StringBuilder sb = new StringBuilder();
        for (ConllSentence csent : cf.getSentences()) {
            csent.normalise();
            csent.makeTrees(null);
            sb.append(csent);
            boolean rtc = csent.isProjective(null);
            if (rtc) sb.append("is projective\n");
            else  sb.append("is NOT projective\n");
        }

        File out = new File(folder, "projectivity.txt");
        FileUtils.writeStringToFile(out, sb.toString(), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("projectivity.txt");

        Assert.assertEquals(String.format("tree projectivity not detected correctly\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }
}
