/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2024, Orange S.A.

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
 @version 2.25.3 as of 28th October 2023
*/

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConlluPlusConverter;
import com.orange.labs.editor.ConlluEditor;
import java.io.File;
import java.io.FileInputStream;
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
        URL url = this.getClass().getResource("test.conllup");
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
        System.out.format("\n***** Testing: %s ****\n", n);
    }


    /** catch errror message from ce.process() when it is a real error (and not a test for an error).
     * ce.process() catches all java Exceptions and returns a json error message with "error": "mesage".
     * Sometimes we test whether CE detected an error, but when this is not the case the presence of "error" in the
     * returned String fo ce.process() means that something went wrong which should not have gone wrong.
     */
    private String processwrapper(String command, int sentid, String comment) {
        String res = ce.process(command, sentid, comment);
        if (res.contains("\"error\"")) {
             Assert.assertFalse("Exception catched: " + res, true);
        }
        return res;
    }

    @Test
    public void test01Conllu() throws ConllException, IOException {
        name("CoNLL-U PLUS read valid file");

        URL inurl = this.getClass().getResource("test.conllup");
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


        URL inurl = this.getClass().getResource("test2.conllup");
        File out = new File(folder, "fileout2.txt");
        try {
            //ConllFile cd =
            		new ConllFile(new File(inurl.getFile()), false, false);
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

        URL url = this.getClass().getResource("out1.conllup");

        String res = ce.getraw(ConlluEditor.Raw.CONLLU, 0, false); // to have the global.columns line
        JsonElement jelement = JsonParser.parseString(res);
        JsonObject jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8);

        processwrapper("read 1", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 1, false);
        jelement = JsonParser.parseString(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8, true);


        processwrapper("read 2", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 2, false);
        jelement = JsonParser.parseString(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8, true);

        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", url.toString(), out.toString()),
                FileUtils.readFileToString(new File(url.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }


    @Test
    public void test11EditExtraCol() throws IOException {
        name("modifying columns 11 and 12");
        ce.setCallgitcommit(false);
        ce.setBacksuffix("");
        ce.setSaveafter(1);

        File out = new File(folder, "test.mod.conllup");
        ce.setOutfilename(out);

        processwrapper("mod extracol 1 SEM:NE B:OEUVRE", 0, "editinfo");
        processwrapper("mod extracol 6 SEM:COREF B:COREF9", 0, "editinfo");
        processwrapper("mod extracol 7 SEM:COREF I:COREF9", 0, "editinfo");


        URL ref = this.getClass().getResource("test.mod.conllup");
        //URL res = this.getClass().getResource("test.conllup.3"); // modified file
        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }


    @Test
    public void test12EditInvalidExtraCol() throws IOException {
        name("trying to modify columns 11 and 12 with invalid colname");
        ce.setCallgitcommit(false);
        ce.setBacksuffix(".4");
        ce.setSaveafter(1);

        String rtc = ce.process("mod extracol 8 ZZSEM:COREF I:COREF9", 0, "editinfo");
        File out = new File(folder, "error3.json");
        FileUtils.writeStringToFile(out, rtc, StandardCharsets.UTF_8, false);
        URL ref = this.getClass().getResource("error3.json");

        Assert.assertEquals(String.format("CoNLL-U Plus format incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
        FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test21insertEmptyWords() throws IOException {
        name("insert empty word");
        ce.setCallgitcommit(false);
        ce.setBacksuffix("");
        ce.setSaveafter(1);
        File out = new File(folder, "test.insertempty.conllup");
        ce.setOutfilename(out);
        processwrapper("mod emptyinsert 4 first lemma1 POS1 XPOS1", 0, "editinfo");
        processwrapper("mod emptyinsert 4 second lemma2 POS2 XPOS2", 0, "editinfo");
        processwrapper("mod emptyinsert 3 premier lemma3 POS1 XPOS1", 0, "editinfo");

        URL ref = this.getClass().getResource("test.insertempty.conllup");

        Assert.assertEquals(String.format("insert empty words incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }


    @Test
    public void test22insertWords() throws IOException {
        name("insert empty word");
        ce.setCallgitcommit(false);
        ce.setBacksuffix("");
        ce.setSaveafter(1);
        File out = new File(folder, "test.insert.conllup");
        ce.setOutfilename(out);
        processwrapper("mod insert 5 premier", 1, "editinfo");

        URL ref = this.getClass().getResource("test.insert.conllup");
        Assert.assertEquals(String.format("insert empty words incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test23MWTfromWord() throws IOException {
        name("create MWT from Word");
        ce.setCallgitcommit(false);
        ce.setBacksuffix("");
        ce.setSaveafter(1);

        File out = new File(folder, "test.split-to-mwt.conllup");
        ce.setOutfilename(out);

        processwrapper("mod misc 4 SpaceAfter=No", 1, "editinfo");
        processwrapper("mod tomwt 4 dan le", 1, "editinfo");

        URL ref = this.getClass().getResource("test.split-to-mwt.conllup");
        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
        FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test44ConlluConversion() throws ConllException, IOException {
        name("CoNLL-U PLUS conversion");

        // first conversion
        ConlluPlusConverter cpc = new ConlluPlusConverter("ID,FORM,HEAD,DEPREL,SEM:NE");
        URL inurl = this.getClass().getResource("test.conllup");
        //ConllFile cd = new ConllFile(new File(inurl.getFile()), false, false);
        String converted = cpc.convert(new FileInputStream(new File(inurl.getFile())));
        File out = new File(folder, "conversion.conllup");
        FileUtils.writeStringToFile(out, converted, StandardCharsets.UTF_8, false);

        URL ref = this.getClass().getResource("conversion.conllup");

        Assert.assertEquals(String.format("CoNLL-U Plus conversion incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
        FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out, StandardCharsets.UTF_8));

        // convert result back into a (incomplete) conllu
        ConlluPlusConverter cpc2 = new ConlluPlusConverter(null);
        String converted2 = cpc2.convert(new FileInputStream(out));
        File out2 = new File(folder, "conversion2.conllu");
        FileUtils.writeStringToFile(out2, converted2, StandardCharsets.UTF_8, false);


        URL ref2 = this.getClass().getResource("conversion2.conllu");

        Assert.assertEquals(String.format("CoNLL-U conversion incorrect\n ref: %s\n res: %s\n", ref2.toString(), out2.toString()),
        FileUtils.readFileToString(new File(ref2.getFile()), StandardCharsets.UTF_8),
        FileUtils.readFileToString(out2, StandardCharsets.UTF_8));
    }
}
