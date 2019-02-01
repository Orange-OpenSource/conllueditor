/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.editor.ConlluEditor;
import com.orange.labs.conllparser.ConllException;
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

/**
 *
 * @author Johannes Heinece <johannes dot heinecke at wanadoo point org>
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestConlluEditor {

    ConlluEditor ce;
    File folder;


    @Before
    public void setUp() throws ConllException, IOException {
        URL url = this.getClass().getResource("/test.conllu");
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
    public void testLatex() throws IOException {
        name("LaTeX output");

        //System.out.println("=================== " + folder.getRoot());
        //File out = folder.newFile("test.tex");
        File out = new File(folder,  "test.tex");

        URL url = this.getClass().getResource("/test.tex");

        // call proces to be sure makeTrees has been  called
        String rtc = ce.process("read 13", 1, "editinfo");
        String res = ce.getraw(ConlluEditor.Raw.LATEX, 13);
        JsonElement jelement = new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();

        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8);

        Assert.assertEquals("LaTeX output incorrect",
                FileUtils.readFileToString(new File(url.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

    @Test
    public void testConllu() throws IOException {
        name("CoNLL-U output");

        //File out = folder.newFile("out1.conllu");
        File out = new File(folder, "out1.conllu");

        URL url = this.getClass().getResource("/out1.conllu");

        String res = ce.getraw(ConlluEditor.Raw.CONLLU, 13);
        JsonElement jelement = new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString()+"\n", StandardCharsets.UTF_8);

        String rtc = ce.process("read 14", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 14);
        jelement = new JsonParser().parse(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString()+"\n", StandardCharsets.UTF_8, true);

        rtc = ce.process("read 15", 1, "editinfo");
        res = ce.getraw(ConlluEditor.Raw.CONLLU, 15);
        jelement = new JsonParser().parse(res);
        jobject = jelement.getAsJsonObject();
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString()+"\n", StandardCharsets.UTF_8, true);

        Assert.assertEquals("CoNLL-U output incorrect",
                FileUtils.readFileToString(new File(url.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

    @Test
    public void testEditJoinSplit() throws IOException {
        name("modifying lemma, deprel, and split/join");
        ce.setCallcitcommot(false);
        String rtc = ce.process("mod lemma 3 Oasis", 3, "editinfo");
        rtc = ce.process("mod 1 2 detfalse", 3, "editinfo");
        rtc = ce.process("mod split 19", 3, "editinfo");
        rtc = ce.process("mod join 5", 3, "editinfo");

        URL ref = this.getClass().getResource("/test.mod.conllu");
        URL res = this.getClass().getResource("/test.conllu.2"); // modified file
        Assert.assertEquals("CoNLL-U output incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(new File(res.getFile()), StandardCharsets.UTF_8));

    }

    @Test
    public void testRead() throws IOException {
        name("read sentence");
        ce.setCallcitcommot(false);
        String rtc = ce.process("read 13", 1, "");
        JsonElement jelement = new JsonParser().parse(rtc);

        //File out = folder.newFile("read.json");
        File out = new File(folder, "read.json");
        //System.err.println(prettyprintJSON(jelement));
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("/read.json");

        Assert.assertEquals("Read return incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void testRead2() throws IOException {
        name("read a second sentence");
        ce.setCallcitcommot(false);
        String rtc = ce.process("read 16", 1, "");
        JsonElement jelement = new JsonParser().parse(rtc);

        //File out = folder.newFile("read.json");
        File out = new File(folder, "read_16.json");
        //System.err.println(prettyprintJSON(jelement));
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("/read_16.json");

        Assert.assertEquals("Read return incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void testFindLemma() throws IOException {
        name("findlemma");
        ce.setCallcitcommot(false);
        String rtc = ce.process("findlemma false fromage/.*/puer", 1, "");
        JsonElement jelement = new JsonParser().parse(rtc);

        //File out = folder.newFile("findlemma.json");
        File out = new File(folder, "findlemma.json");

        //System.err.println(prettyprintJSON(jelement));
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("/findlemma.json");

        Assert.assertEquals("Find lemma return incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

    @Test
    public void testFindForm() throws IOException {
        name("findword");
        ce.setCallcitcommot(false);
        String rtc = ce.process("findword false \" and \"", 1, "");
        JsonElement jelement = new JsonParser().parse(rtc);

        //File out = folder.newFile("findform.json");
        File out = new File(folder, "findform.json");

        //System.err.println(prettyprintJSON(jelement));
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("/findform.json");

        Assert.assertEquals("Find form return incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

    @Test
    public void testFindNothing() throws IOException {
        name("findupos (error)");
        ce.setCallcitcommot(false);
        String rtc = ce.process("findupos false TOTO", 1, "");
        JsonElement jelement = new JsonParser().parse(rtc);

        //File out = folder.newFile("finduposKO.json");
        File out = new File(folder, "finduposKO.json");

        //System.err.println(prettyprintJSON(jelement));
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement), StandardCharsets.UTF_8);

        URL ref = this.getClass().getResource("/finduposKO.json");

        Assert.assertEquals("Find upos error return incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));

    }

    @Test
    public void testUndo() throws IOException {
        name("modifying UPOS and Lemma, followed by undo");
        ce.setCallcitcommot(false);
        ce.setBacksuffix(".3");
        String rtc = ce.process("mod lemma 1 Sammie", 13, "editinfo");
        rtc = ce.process("mod upos 2 VERBPAST", 13, "editinfo");
        rtc = ce.process("mod undo", 13, "editinfo");

        URL ref = this.getClass().getResource("/test.mod.undo.conllu");
        URL res = this.getClass().getResource("/test.conllu.3");
        Assert.assertEquals("mod undo output incorrect",
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(new File(res.getFile()), StandardCharsets.UTF_8));

    }

    private String prettyprintJSON(JsonElement j) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(j);
    }

}
