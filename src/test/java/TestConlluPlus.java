/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2020, Orange S.A.

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
 @version 2.4.0 as of 2nd May 2020
 */

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.editor.ConlluEditor;
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
public class TestConlluPlus {
    ConlluEditor ce;
    File folder;


    @Before
    public void setUp() throws ConllException, IOException {
        URL url = this.getClass().getResource("/test.conllup");
        File file = new File(url.getFile());
        try {
            ce = new ConlluEditor(file.toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        folder = new File("testoutput");
        folder.mkdir();
    }

    //@Rule
    //public TemporaryFolder folder = new TemporaryFolder();

    private void name(String n) {
        System.out.format("\n***** Testing: %S ****\n", n);
    }

    @Test
    public void test01Conllu() throws ConllException, IOException {
        name("CoNLL-U PLUS read valid file");

        URL inurl = this.getClass().getResource("/test.conllup");
        ConllFile cd = new ConllFile(new File(inurl.getFile()), false, false);
        File out = new File(folder, "fileout.conllup");
        FileUtils.writeStringToFile(out, cd.toString(), StandardCharsets.UTF_8, false);

        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", inurl.toString(), out.toString()),
        FileUtils.readFileToString(new File(inurl.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test02Conllu() throws ConllException, IOException {
        name("CoNLL-U PLUS read invalid file");


        URL inurl = this.getClass().getResource("/test2.conllup");
        File out = new File(folder, "fileout2.txt");
        try {
            ConllFile cd = new ConllFile(new File(inurl.getFile()), false, false);
        } catch (Exception e) {
            FileUtils.writeStringToFile(out, e.getMessage()+ "\n", StandardCharsets.UTF_8, false);
        }
        URL ref = this.getClass().getResource("fileout2.txt");


        Assert.assertEquals(String.format("CoNLL-U Plus format incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
        FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }


    @Test
    public void test03Conllu() throws IOException {
        name("CoNLL-U plus output");

        //File out = folder.newFile("out1.conllu");
        File out = new File(folder, "out1.conllup");
        FileUtils.writeStringToFile(out, "", StandardCharsets.UTF_8);

        URL url = this.getClass().getResource("/out1.conllup");

        String res = ce.getraw(ConlluEditor.Raw.CONLLU, 0); // to have the global.columns line
        JsonElement jelement = new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8);

        String rtc = ce.process("read 1", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 1);
        jelement = new JsonParser().parse(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8, true);

        rtc = ce.process("read 2", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 2);
        jelement = new JsonParser().parse(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8, true);

        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", url.toString(), out.toString()),
                FileUtils.readFileToString(new File(url.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

}
