/* This library is under the 3-Clause BSD License

Copyright (c) 2020-2024, Orange S.A.

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
 @version 2.25.6 as of 29th August 2024
 */
package com.orange.labs.conllparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a file which defines all valid features and optionally gives for each
 * UPOS the list of valid features names. Format: Feature1=Val1 Feature1=Val2
 * ... U:NOUN Feature1 Feature2 ... X:NN Feature1 Feature2
 *
 * or
 *
 * loads the official data/feats.json file (from  https://github.com/UniversalDependencies/tools)
 * which defines for all UD languages valid features/values for each UPOS
 * if no language is given, it loads only universal feature/value pairs
 */
public class ValidFeatures {

    Map<String, Set<String>> validFeatures; // fname: [values]
    Map<String, Set<String>> uposFnames = null; // upos: [fnames] // valid featurenames for a given upos
    Map<String, Set<String>> xposFnames = null; // xpos: [fnames] // valid featurenames for a given xpos

    public ValidFeatures(List<String> filenames, String lg, boolean include_unused) throws IOException {
        validFeatures = new HashMap<>();
        xposFnames = new HashMap<>();
        for (String fn : filenames) {
            if (fn.endsWith(".json")) {
                readjson(fn, lg, include_unused);
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fn), StandardCharsets.UTF_8));

                String line;
                //int ct = 0;
                while ((line = br.readLine()) != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        if (line.startsWith("U:")) {
                            if (uposFnames == null) {
                                uposFnames = new HashMap<>();
                            }
                            // list of feature names available for a given UPOS. Format U:UPOS Fname
                            String[] elems = line.substring(2).split("[ \t]+");
                            Set<String> tmp = new HashSet<>(Arrays.asList(elems));
                            uposFnames.put(elems[0], tmp);
                            tmp.remove(elems[0]); // upos
                        } else if (line.startsWith("X:")) {
                            if (xposFnames == null) {
                                xposFnames = new HashMap<>();
                            }
                            // list of feature names available for a given UPOS. Format U:UPOS Fname
                            String[] elems = line.substring(2).split("[ \t]+");
                            Set<String> tmp = new HashSet<>(Arrays.asList(elems));
                            xposFnames.put(elems[0], tmp);
                            tmp.remove(elems[0]); // xpos
                        } else {
                            String[] elems = line.split("=", 2);
                            addFvalue(elems[0], elems[1]);
                        }
                    }
                }
                br.close();
            }
        }
        System.err.format("%d valid Features read from %s\n", validFeatures.size(), filenames.toString());
        System.err.println(validFeatures);
        System.err.println(uposFnames);
        System.err.println(xposFnames);
    }

    /* checks whether feature is valid. If upos and/or xpos are missing, we do not check.
    if the current upos/xpos has no defined list of valid features, all are accepted
         @return 0 all ok, 1 fname invalid (absolutely or with given upos/xpos), 2: fvalue invalid

     */
    public int isValid(String upos, String xpos, String fname, String fvalue) {
        Set<String> vals = validFeatures.get(fname);
        if (vals == null) {
            return 1;
        }
        if (!vals.contains(fvalue)) {
            return 2;
        }

        boolean uposok = true;
        if (uposFnames != null && upos != null) {
            Set<String> fnames = uposFnames.get(upos);
            if (fnames != null && !fnames.contains(fname)) {
                uposok = false;
            }
        }

        boolean xposok = true;
        if (xposFnames != null && xpos != null) {
            Set<String> fnames = xposFnames.get(xpos);
            if (fnames != null && !fnames.contains(fname)) {
                xposok = false;
            }
        }


        if (uposok && xposok) return 0;
        return 1;
    }

    //Map<String, Set<String>> validFeatures; // fname: [values]
    //Map<String, Set<String>> uposFnames = null; // upos: [fnames] // valid featurenames for a given upos
    //Map<String, Set<String>> xposFnames = null; // xpos: [fnames] // valid featurenames for a given xpos
    public JsonObject getAsJson() {
        JsonObject upos = new JsonObject();
        for (String key : uposFnames.keySet()) {
            JsonArray feat = new JsonArray();
            for (String fname : uposFnames.get(key)) {
                feat.add(fname);
            }
            upos.add(key, feat);
        }
        JsonObject xpos = new JsonObject();
        for (String key : xposFnames.keySet()) {
            JsonArray feat = new JsonArray();
            for (String fname : xposFnames.get(key)) {
                feat.add(fname);
            }
            xpos.add(key, feat);
        }
        JsonObject validvalues = new JsonObject();
        for (String key : validFeatures.keySet()) {
            JsonArray feat = new JsonArray();
            for (String fname : validFeatures.get(key)) {
                feat.add(fname);
            }
            validvalues.add(key, feat);
        }
        JsonObject descriptors = new JsonObject();
        descriptors.add("uposfeats", upos);
        descriptors.add("xposfeats", xpos);
        descriptors.add("featvalues", validvalues);
        //System.err.println("AAAAA " + descriptors);
        return descriptors;
    }
    
    public List<String> getList() {
        List<String> fl = new ArrayList<>();
        for (String fname : validFeatures.keySet()) {
            for (String val : validFeatures.get(fname)) {
                fl.add(String.format("%s=%s", fname, val));
            }
        }
        Collections.sort(fl);
        return fl;
    }

    private void addFvalue(String featurename, String val) {
        Set<String> vals = validFeatures.get(featurename);
        if (vals == null) {
            vals = new HashSet<>();
            validFeatures.put(featurename, vals);
        }
        vals.add(val);
    }

    private void readjson(String fn, String lg, boolean include_unused) throws IOException {
        // read data/feats.json from  https://github.com/UniversalDependencies/tools.git
        // if language is given:
        // read all defined features. if "byupos" restricts the UPOS for which the feature is defined
        // use uposFnames too
        // if no language is given, read all features of all languages which are type=universal and doc:global, read uvalues and unused_uvalues
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fn), StandardCharsets.UTF_8));
        JsonObject jfile = JsonParser.parseReader(br).getAsJsonObject();

        JsonObject features = jfile.getAsJsonObject("features");
        if (features == null) return;
        if (lg == null) {
            // get all universal define features

            for (String lgcode : features.keySet()) {
                JsonObject jlang = features.getAsJsonObject(lgcode);
                for (String featurename : jlang.keySet()) {
                    JsonObject feature = jlang.getAsJsonObject(featurename);

                    String type = feature.get("type").getAsString();
                    String doc = feature.get("doc").getAsString();
                    if (!type.equals("universal") || !doc.equals("global")) {
                        // we only use universal features
                        continue;
                    }

                    Iterator<JsonElement> it = feature.get("uvalues").getAsJsonArray().iterator();
                    while (it.hasNext()) {
                        String val = it.next().getAsString();
                        //System.err.format("%s=%s\n", featurename, val);
                        addFvalue(featurename, val);
                    }
                    it = feature.get("unused_uvalues").getAsJsonArray().iterator();
                    while (it.hasNext()) {
                        String val = it.next().getAsString();
                        //System.err.format("%s=%s\n", featurename, val);
                        addFvalue(featurename, val);
                    }

                }
            }
        } else {
            JsonObject jlang = features.getAsJsonObject(lg);
            for (String featurename : jlang.keySet()) {
                JsonObject feature = jlang.getAsJsonObject(featurename);
                int permitted = feature.get("permitted").getAsInt();
                if (permitted == 0) {
                    continue;
                }

                //String type = feature.get("type").getAsString();
                //String doc = feature.get("doc").getAsString();

                // read universal values
                Iterator<JsonElement> it = feature.get("uvalues").getAsJsonArray().iterator();
                while (it.hasNext()) {
                    String val = it.next().getAsString();
                    //System.err.format("%s=%s\n", featurename, val);
                    addFvalue(featurename, val);

                }

                // read local (lang spec) values
                it = feature.get("lvalues").getAsJsonArray().iterator();
                while (it.hasNext()) {
                    String val = it.next().getAsString();
                    //System.err.format("%s=%s\n", featurename, val);
                    addFvalue(featurename, val);
                }

                if (include_unused) {
                    // read values delcared as unused
                    // read universal values
                    it = feature.get("unused_uvalues").getAsJsonArray().iterator();
                    while (it.hasNext()) {
                        String val = it.next().getAsString();
                        //System.err.format("%s=%s\n", featurename, val);
                        addFvalue(featurename, val);

                    }

                    // read local (lang spec) values
                    it = feature.get("unused_lvalues").getAsJsonArray().iterator();
                    while (it.hasNext()) {
                        String val = it.next().getAsString();
                        //System.err.format("%s=%s\n", featurename, val);
                        addFvalue(featurename, val);
                    }
                }

                JsonObject byupos = feature.getAsJsonObject("byupos");
                if (byupos.size() > 0) {
                    if (uposFnames == null) {
                        uposFnames = new HashMap<>();
                    }
                    for (String pos : byupos.keySet()) {
                        // currently we allow all values of a feature if its in the bypos-list
                        //JsonObject posfeatures = byupos.getAsJsonObject(pos);

                        Set<String> feats = uposFnames.get(pos);
                        if (feats == null) {
                            feats = new HashSet<>();
                            uposFnames.put(pos, feats);
                        }
                        feats.add(featurename);
                    }
                }
            }
        }

    }
}
