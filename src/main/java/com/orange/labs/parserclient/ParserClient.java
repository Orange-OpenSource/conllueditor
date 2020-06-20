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
 @version 2.6.0 as of 20th June 2020
 */
package com.orange.labs.parserclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import com.orange.labs.httpserver.ServeurHTTP;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class makes an HTTP GET/POST request to a dependency parser server which
 * returns CoNLL-U. The exact API is read from a configuration file.
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ParserClient {

    private String API; // API for parsing (HTTP POST)
    private String info; // URL to get infro from parser (HTTP GET)
    private String txt_param;
    private Map<String, String> other;
    private List<String> jsonpath;

    /* just for testing */
//    public ParserClient(String url, String txtparam, String otherparams, String jsonpath) {
//        API = url;
//        txt_param = txtparam;
//
//        if (otherparams != null) {
//            parseOther(otherparams);
//        }
//
//        if (jsonpath != null) {
//            this.jsonpath = Arrays.asList(jsonpath.split("[/\\.]"));
//        }
//    }
    public ParserClient(String config) throws IOException {
        readConfig(config);
    }

    private void parseOther(String line) {
        other = new HashMap<>();
        for (String pv : line.split(",")) {
            String[] elems = pv.split("=", 2);
            if (elems.length == 2) {
                other.put(elems[0], elems[1]);
            } else {
                other.put(pv, "");
            }
        }
    }

    /**
     * read configfile. Format url: http://my.host:port/path txt: parameter name
     * for the text to be analysed other: other params:
     * key1=val,key2=val2,key3=... jsonpath: path to find CoNLL-U result in
     * parser return list of keys
     *
     * @param fn
     * @throws IOException
     */
    private void readConfig(String fn) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fn));
        String line;
        while ((line = br.readLine()) != null) {

            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] elems = line.split(":", 2);
            if (elems.length != 2) {
                System.err.println("Error in line: '" + line + "'");
            } else {
                if ("url".equals(elems[0].trim())) {
                    API = elems[1].trim();
                } else if ("info".equals(elems[0].trim())) {
                    info = elems[1].trim();
                } else if ("txt".equals(elems[0].trim())) {
                    txt_param = elems[1].trim();
                } else if ("other".equals(elems[0].trim())) {
                    parseOther(elems[1].trim());
                } else if ("jsonpath".equals(elems[0].trim())) {
                    jsonpath = Arrays.asList(elems[1].trim().split("[/\\.]"));
                }
            }
        }
    }

    public String httpget(String address) throws IOException {
        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            StringBuilder result = new StringBuilder();
            InputStream isd = connection.getInputStream();
            BufferedReader brd = new BufferedReader(new InputStreamReader(isd, StandardCharsets.UTF_8));
            String dline;

            while ((dline = brd.readLine()) != null) {
                result.append(dline).append('\n');
            }
            return result.toString();
        }
        return "ERROR " + connection.getResponseCode();
    }

    public List<ConllSentence> makerequest(String text) throws IOException, ConllException {
        System.err.println(API + " " + txt_param + " " + other + " " + jsonpath);

        URL url = new URL(API);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");

        // get other params
        Map<String, String> params = new HashMap<>();
        params.put(txt_param, text);
        if (other != null) {
            params.putAll(other);
        }

        byte[] data = processParams(params);
        connection.setRequestProperty("Content-Length", String.valueOf(data.length));

        connection.setDoOutput(true);
        try (DataOutputStream writer = new DataOutputStream(connection.getOutputStream())) {
            writer.write(data);

            writer.flush();
            writer.close();
        }

        StringBuilder response;

// Get the input stream of the connection
        try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            response = new StringBuilder();
            while ((line = input.readLine()) != null) {
                // Append each line of the response and separate them
                response.append(line);
                response.append(System.lineSeparator());
            }
        } finally {
            connection.disconnect();
        }

        String result = response.toString();

        if (jsonpath != null) {
            JsonElement jelem = JsonParser.parseString(result); // new JsonParser().parse(result);
            JsonObject jobject = jelem.getAsJsonObject();
            Iterator<String> it = jsonpath.iterator();
            JsonElement je = null;
            while (it.hasNext()) {
                je = jobject.get(it.next());
                if (it.hasNext()) {
                    jobject = je.getAsJsonObject();
                }
            }

            result = je.getAsString();
        }

        ConllFile cf = new ConllFile(result, false, false);

        return cf.getSentences();
    }

    public String getInfo() {
        JsonObject solution = new JsonObject();
        solution.addProperty("url", API);

        if (info != null) {
            try {
                String response = httpget(info);
                try {
                	//JsonParser jp = new JsonParser();
                    //JsonElement je = jp.parse(response);
                	JsonElement je = JsonParser.parseString(response);
                    solution.add("info", je);
                } catch (JsonSyntaxException e) {
                    solution.addProperty("info", response);
                }
            } catch (IOException e) {
                return e.getMessage();
            }
        }

        return solution.toString();
    }


    /** send senteces to parser server, and format the result in json for the displayer */
    public String ConlluEditor_json(String text) {
        try {
            JsonArray solutions = new JsonArray();

            List<ConllSentence> css = makerequest(text);
            //solution.addProperty("maxsentence", 1);
            int ct = 0;
            for (ConllSentence csent : css) {
                ct++;
                //ConllSentence csent = css.get(0);
                csent.makeTrees(null);

                Map<Integer, Integer> heights = csent.calculate_flat_arcs_height();
                for (Integer id : heights.keySet()) {
                    ConllWord cw = csent.getWord(id);
                    cw.setArc_height(heights.get(id));
                }

                JsonObject solution = new JsonObject();
                solution.addProperty("sentenceid", ct); //currentSentenceId);

                solution.addProperty("sentence", csent.getSentence());
                solution.addProperty("length", (csent.getWords().size() + csent.numOfEmptyWords()));
                if (csent.getSentid() != null) {
                    solution.addProperty("sent_id", csent.getSentid());
                }

                solution.add("tree", csent.toJsonTree(null, null, null, null, null, null));
                solution.addProperty("info", csent.getHead().getMiscStr()); // pour les fichiers de règles, il y a de l'info dans ce chapps

                // adding also a CoNLL-U, sd-parse and a LaTeX representation
                solution.addProperty("latex", csent.getLaTeX());
                solution.addProperty("sdparse", csent.getSDparse());
                solution.addProperty("conllu", csent.toString());
                solutions.add(solution);

            }

            return solutions.toString();

        } catch (IOException | ConllException e) {
            JsonObject solution = new JsonObject();
            solution.addProperty("sentenceid", 0); //currentSentenceId);
            solution.addProperty("maxsentence", 1);
            solution.addProperty("error", e.getMessage());
            return solution.toString();
        }
    }

    private byte[] processParams(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> param : params.entrySet()) {
            if (sb.length() != 0) {
                sb.append('&');
            }

            sb.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }

        // Convert the requestData into bytes
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return bytes;
    }

    static void help() {
        System.err.println("usage: parseclient.sh [options] configfile port");

        System.err.println("   --rootdir <dir>      root of fileserver (must include index.html and edit.js etc.  for ConlluEditor");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            help();
            System.exit(1);
        }

        String rootdir = null;
        int debug = 3;

        int argindex = 0;
        for (int a = 0; a < args.length - 1; ++a) {

            if (args[a].equals("--rootdir")) {
                rootdir = args[++a];
                argindex += 2;

            } else if (args[a].startsWith("-")) {
                System.err.println("Invalid option " + args[a]);
                //help();
                System.exit(2);
            }
        }

        if (args.length < 2 + argindex) {
            help();
            System.exit(1);
        }

        try {
            ParserClient cl = new ParserClient(args[argindex]);

            //cl = new ParserClient("http://localhost:2241", "txt", null, null);
            // UDPipe
            //cl = new ParserClient("http://localhost:3333/process", "data", "tokenizer=", "result");
//            List<ConllSentence> css = cl.makerequest("Mae'r gath wedi bwyta. Roedd hi'n oer");
//            for (ConllSentence cs : css) {
//                System.out.println(cs.toString());
//            }
            ServeurHTTP sh = new ServeurHTTP(Integer.parseInt(args[argindex + 1]), cl, rootdir, debug);
        } catch (IOException /*| ConllException */ ex) {
            System.err.println("ERROR: " + ex.getMessage());
        }

    }

}
