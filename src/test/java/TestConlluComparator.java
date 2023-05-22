
import com.orange.labs.comparison.ConlluComparator;
import com.orange.labs.conllparser.ConllException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2023, Orange S.A.

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
 @version 2.15.2 as of 9th February 2022
 */

/**
 *
 * @author Johannes Heinecke
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestConlluComparator {
    File folder;
    //File infile;
    //File infile2;
    List<File>g1;
    List<File>g2;


    @Before
    public void setUp() throws ConllException, IOException {
        URL url = this.getClass().getResource("similar.conllu");
        File infile = new File(url.getFile());
        g1 = new ArrayList<>();
        g1.add(infile);

        url = this.getClass().getResource("similar2.conllu");
        infile = new File(url.getFile());
        g2 = new ArrayList<>();
        g2.add(infile);

        folder = new File("testoutput");
        folder.mkdir();
    }

    //@Rule
    //public TemporaryFolder folder = new TemporaryFolder();

    private void name(String n) {
        System.out.format("\n***** Testing: %s ****\n", n);
    }

    @Test
    public void test01OneGroupForm0() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in one group: Form 0");
        //List<File>g1 = new ArrayList<>();
        //g1.add(infile);
        ConlluComparator cc = new ConlluComparator(g1, null, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(0, -1, -1, -1, -1, -1, false);

        File out = new File(folder, "sim-form-0.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim-form-0.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test01OneGroupForm1() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in one group: Form 1");
        //List<File>g1 = new ArrayList<>();
        //g1.add(infile);
        ConlluComparator cc = new ConlluComparator(g1, null, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(1, -1, -1, -1, -1, -1, false);

        File out = new File(folder, "sim-form-1.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim-form-1.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test01OneGroupForm2() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in one group: Form 2");
        //List<File>g1 = new ArrayList<>();
        //g1.add(infile);
        ConlluComparator cc = new ConlluComparator(g1, null, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(2, -1, -1, -1, -1, -1, false);

        File out = new File(folder, "sim-form-2.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim-form-2.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test01OneGroupLemma2() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in one group: Lemma 2");
        //List<File>g1 = new ArrayList<>();
        //g1.add(infile);
        ConlluComparator cc = new ConlluComparator(g1, null, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(-1, 2, -1, -1, -1, -1, false);

        File out = new File(folder, "sim-lemma-2.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim-lemma-2.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test01OneGroupFormUpos2() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in one group: Form 1 Upos 2");
        //List<File>g1 = new ArrayList<>();
        //g1.add(infile);
        ConlluComparator cc = new ConlluComparator(g1, null, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(1, -1, 2, -1, -1, -1, false);

        File out = new File(folder, "sim-form-0-upos-2.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim-form-0-upos-2.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }



    @Test
    public void test02TwoForm0() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in two groups: Form 0");

        ConlluComparator cc = new ConlluComparator(g1, g2, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(0, -1, -1, -1, -1, -1, false);
        File out = new File(folder, "sim2-form-0.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim2-form-0.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test02TwoForm2() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in two groups: Form 2");

        ConlluComparator cc = new ConlluComparator(g1, g2, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels);
        String res = cc.analyse(2, -1, -1, -1, -1, -1, false);
        File out = new File(folder, "sim2-form-2.txt");
        FileUtils.writeStringToFile(out, res, StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim2-form-2.txt");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }


    @Test
    public void test02TwoForm2json() throws ConllException, IOException, InterruptedException {
        name("Compare sentences in two groups: Form 2, json output");

        ConlluComparator cc = new ConlluComparator(g1, g2, 2);
        //cc.analyse(forms, lemmas, upos, xpos, feats, deprels, json);
        String res = cc.analyse(2, -1, -1, -1, -1, -1, true);
        File out = new File(folder, "sim2-form-2.json");
        FileUtils.writeStringToFile(out, res + "\n", StandardCharsets.UTF_8, false);
        URL inurl = this.getClass().getResource("sim2-form-2.json");
        Assert.assertEquals(String.format("CoNLL-U comparison\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }
}
