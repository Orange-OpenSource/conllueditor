/* This library is under the 3-Clause BSD License

Copyright (c) 2025, Orange S.A.

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
 @version 2.31.2 as of 23rd December 2025
*/

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.editor.ConlluEditor;
import com.orange.labs.conllparser.ConllException;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestConlluComparison {
        ConlluEditor ce;
    File folder;


    @Before
    public void setUp() throws ConllException, IOException {
        URL url = this.getClass().getResource("mini-system.conllu");
        File file = new File(url.getFile());

        URL url2 = this.getClass().getResource("mini-gold.conllu");
        File file2 = new File(url2.getFile());
        try {
            ce = new ConlluEditor(file.toString(), true);
            ce.setComparisonFile(file2.toString());
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


    @Test
    public void test01_file_comparison() throws IOException {
        name("compare two sentences and get metrics");
        ce.setCallgitcommit(false);
        ce.setBacksuffix(".2");
        ce.setSaveafter(1);

        String rtc = ce.process("read 0", 0, "editinfo", 1); // we changed the sentence already once
        JsonObject jobj = JsonParser.parseString(rtc).getAsJsonObject();
        System.err.println("QQQQQQ " + jobj.get("Lemma"));
        System.err.println("qqqqqq " + rtc);
        Assert.assertEquals("Lemma comparison", 90.0, jobj.get("Lemma").getAsDouble(), 0);
        Assert.assertEquals("Feature comparison", 80.0, jobj.get("Features").getAsFloat(), 0);
        Assert.assertEquals("UPOS comparison", 80.0, jobj.get("UPOS").getAsFloat(), 0);
        Assert.assertEquals("XPOS comparison", 70.0, jobj.get("XPOS").getAsFloat(), 0);
        Assert.assertEquals("LAS comparison", 80.0, jobj.get("LAS").getAsFloat(), 0);
        Assert.assertEquals("UAS comparison", 90.0, jobj.get("UAS").getAsFloat(), 0);
        Assert.assertEquals("DepLables comparison", 90.0, jobj.get("DEPLAB").getAsFloat(), 0);
        Assert.assertEquals("MISC comparison", 90.0, jobj.get("MISC").getAsFloat(), 0);
    }
}
