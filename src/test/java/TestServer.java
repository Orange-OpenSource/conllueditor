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
 @version 2.24.0 as of 14th November 2023
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.editor.ConlluEditor;
import com.orange.labs.httpserver.ServeurHTTP;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// jupiter.juit 5 https://symflower.com/en/company/blog/2023/migrating-from-junit-4-to-5/
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
public class TestServer {

    ServeurHTTP ces;
    int port = 5558;
    File folder;


    @BeforeEach
    public void setUp() throws Exception {
        URL url = this.getClass().getResource("test.conllu");
        File file = new File(url.getFile());
        ConlluEditor ce = null;
        try {
            ce = new ConlluEditor(file.toString(), true);
            ce.setCallgitcommit(false); // to avoid git commits on the test files
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        folder = new File("testoutput");
        folder.mkdir();

        ces = new ServeurHTTP(port, ce, "./gui", 0, true);
    }

    @AfterEach
    public void tearDown() {
        ces.stop();
    }

    private void name(String n) {
        System.out.format("\n***** Testing: %s ****\n", n);
    }

    private String httpget(String str) throws Exception {
       return httpget(str, HttpURLConnection.HTTP_OK);
    }

    private String httpget(String str, int status) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + "/" + str))
          .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals("HTTP status code", status, response.statusCode());

        return response.body();
    }

    private String httppost(String path, String cmd, int sentid) throws Exception {
        return httppost(path, cmd, sentid, HttpURLConnection.HTTP_OK);
    }

    private String httppost(String path, String cmd, int sentid, int expected_status) throws Exception {
        StringBuilder params = new StringBuilder();
        params.append("cmd=").append(cmd).append('&').append("sentid=").append(sentid);

        HttpClient client = //HttpClient.newBuilder()
                //.version(HttpClient.Version.HTTP_1_1)
                //                .build();
                HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + path))
          .POST(HttpRequest.BodyPublishers.ofString(params.toString()))
          .header("Content-type", "text/plain")
          .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals("HTTP status code", expected_status, response.statusCode());

        return response.body();
    }

    private String prettyprintJSON(JsonElement j) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(j);
    }


    private void compareFile(String result, String title, String filename) throws IOException {
        JsonElement jelement = JsonParser.parseString(result);  //new JsonParser().parse(res);

        File out = new File(folder, filename);
        URL ref = this.getClass().getResource(filename);
        FileUtils.writeStringToFile(out, prettyprintJSON(jelement).replaceAll("\\\\r", ""), StandardCharsets.UTF_8);

        Assert.assertEquals(String.format("%s\n ref: %s\n res: %s\n", title, ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

//    private String oohttpget(String str, String method) throws MalformedURLException, IOException {
//        URL url = new URL("http://localhost:" + port + "/" + str);
//        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//        connection.setDoOutput(true);
//        connection.setRequestMethod(method);
//        if ("POST".equals(method)) {
//            String boundary = "abcdefghijk"; //UUID.randomUUID().toString();
//            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
//        }
//        Assert.assertEquals("HTTP status code", HttpURLConnection.HTTP_OK, connection.getResponseCode());
//
//        InputStream isd = connection.getInputStream();
//        BufferedReader brd = new BufferedReader(new InputStreamReader(isd, StandardCharsets.UTF_8));
//        String dline;
//        StringBuilder sb = new StringBuilder();
//        while ((dline = brd.readLine()) != null) {
//            //System.out.println("zzzz " + dline);
//            sb.append(dline);
//
//        }
//        return sb.toString();
//    }

    @Test
    public void test01_getlists() throws Exception {
        name("get lists");

        String res = httpget("edit/validlists");
        //System.err.println("AAAA " + res);
        JsonElement jelement = JsonParser.parseString(res);  //new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();

        Assert.assertEquals("shortcuttimeout", 700, jobject.get("shortcuttimeout").getAsInt());
        String keys[] = {"filename", "version", "git.commit.id", "git.branche", "git.dirty", "reinit", "saveafter", "shortcuttimeout", "stats", "columns"};
        for (String key : keys) {
            jobject.has(key);
            //System.err.println("ZZZ " + key + " " + jobject.has(key));
            Assert.assertEquals("server info", true, jobject.has(key));
        }
        JsonObject stats = jobject.getAsJsonObject("stats");
        Assert.assertEquals("syntactic_words", 244, stats.get("syntactic_words").getAsInt());
    }

    @Test
    public void test02_getRaw() throws Exception {
        name("get latex");

        String res = httpget("edit/getlatex?all_enhanced=true&sentid=13");

        JsonElement jelement = JsonParser.parseString(res);  //new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();

        File out = new File(folder, "test.getraw.tex");

        URL ref = this.getClass().getResource("test.getraw.tex");
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8);

        Assert.assertEquals(String.format("LaTeX output incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }

    @Test
    public void test03_getConllu() throws Exception {
        name("get CoNLL-U");

        String res = httpget("edit/getconllu?all_enhanced=true&sentid=13");

        JsonElement jelement = JsonParser.parseString(res);  //new JsonParser().parse(res);
        JsonObject jobject = jelement.getAsJsonObject();

        File out = new File(folder, "test.getraw.conllu");

        URL ref = this.getClass().getResource("test.getraw.conllu");
        FileUtils.writeStringToFile(out, jobject.get("raw").getAsString(), StandardCharsets.UTF_8);

        Assert.assertEquals(String.format("CoNLL-U output incorrect\n ref: %s\n res: %s\n", ref.toString(), out.toString()),
                FileUtils.readFileToString(new File(ref.getFile()), StandardCharsets.UTF_8),
                FileUtils.readFileToString(out, StandardCharsets.UTF_8));
    }


    @Test
    public void test031_BAD_getConllu() throws Exception {
        name("bad get CoNLL-U");

        String res = httpget("edit/getconllu?all_enhanced=true&sentidBAD=3", 400);

        String expected = "{ \"error\": \"parameter 'sentid' is mandatory\" }";
        Assert.assertEquals(String.format("BAD command message\n ref: <<%s>>\n res: <<%s>>\n", expected, res), expected, res);

    }

    @Test
    public void test041_next() throws Exception {
        name("next");

        String res = httppost("/edit/", "next", 3);
        compareFile(res, "API 'next' output incorrect", "api.next.json");
    }

    @Test
    public void test042_prec() throws Exception {
        name("prec");

        String res = httppost("/edit/", "prec", 18);
        compareFile(res, "API 'prec' output incorrect", "api.prec.json");
    }

    @Test
    public void test043_readlast() throws Exception {
        name("read last");

        String res = httppost("/edit/", "read+last", 3);
        compareFile(res, "API 'read last' output incorrect", "api.readlast.json");
    }

    @Test
    public void test044_badurl() throws Exception {
        name("bad URL");

        String res = httppost("/edit", "read+last", 3, 405);
        String expected = "Filehandler: Bad POST Path '/edit'";
        Assert.assertEquals(String.format("BAD URL message\n ref: <<%s>>\n res: <<%s>>\n", expected, res), expected, res);
    }

    @Test
    public void test045_badcommand() throws Exception {
        name("bad command");

        String res = httppost("/edit/", "badcommand", 3, 200);
        //System.err.println("BBAAAA " + res);
        String expected = "{\"sentenceid\":3,\"maxsentence\":19,\"error\":\"invalid command «badcommand»\"}";
        Assert.assertEquals(String.format("BAD command message\n ref: <<%s>>\n res: <<%s>>\n", expected, res), expected, res);
    }

    @Test
    public void test046_incompletecommand() throws Exception {
        name("incomplete command: missing sentid");

        StringBuilder params = new StringBuilder();
        params.append("cmd=").append("next").append('&').append("qqsentid=").append(13);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + "/edit/"))
          .POST(HttpRequest.BodyPublishers.ofString(params.toString()))
          .header("Content-type", "text/plain")
          .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals("HTTP status code", 400, response.statusCode());

        String res = response.body();

        String expected = "{ \"error\": \"parameters 'cmd' and 'sentid' are mandatory\" }";
        Assert.assertEquals(String.format("BAD command message\n ref: <<%s>>\n res: <<%s>>\n", expected, res), expected, res);
    }

    @Test
    public void test047_POS_bad_edit_path() throws Exception {
        name("POS bad edit path");

        String res = httppost("/edit/bad/path/", "read+first", 3, 400);

        String expected = "{ \"error\": \"bad POST path: '/edit/bad/path/'\" }";
        Assert.assertEquals(String.format("BAD command message\n ref: <<%s>>\n res: <<%s>>\n", expected, res), expected, res);
    }

    @Test
    public void test051_mod() throws Exception {
        name("mod 9 3");

        String res = httppost("/edit/", "mod+9+3", 0);
        compareFile(res, "API 'mod head' output incorrect", "api.mod.json");
    }

    @Test
    public void test052_metadata() throws Exception {
        name("read last");

        String cmd = URLEncoder.encode("mod editmetadata {\"newdoc\":\"\",\"newpar\":\"\",\"sent_id\":\"fr-ud-dev_00006\",\"translit\":\"\",\"translations\":\"en: They visited the Louvre museum.\",\"text\":\"ils ont visité le Musée du Louvre.\"}", StandardCharsets.UTF_8);
        String res = httppost("/edit/", cmd, 6);
        compareFile(res, "API 'mod metadata' output incorrect", "api.metadata.json");
    }

    @Test
    public void test053_split() throws Exception {
        name("mod split 5 4");

        String res = httppost("/edit/", "mod+split+5+4", 8);
        compareFile(res, "API 'mod head' output incorrect", "api.split.json");
    }

    @Test
    public void test061_getfile() throws Exception {
        name("get file");

        String res = httpget("index.css", 200);
        //System.err.println("BBAAAA " + res);
        Assert.assertTrue("File does contain correct string", res.contains("body {\n    margin-top: 280px; /* header height + top offset */"));
    }

    @Test
    public void test061_badfile() throws Exception {
        name("bad file");

        String res = httpget("index.cssBAD", 404);
    }
}
