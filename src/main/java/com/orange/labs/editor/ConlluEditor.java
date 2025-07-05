/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2025, Orange S.A.

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
 @version 2.32.0 as of 5th July 2025
 */
package com.orange.labs.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orange.labs.conllparser.CheckCondition;
import com.orange.labs.conllparser.CheckGrewmatch;
import com.orange.labs.conllparser.ConllException;
import com.orange.labs.conllparser.ConllFile;
import com.orange.labs.conllparser.ConllSentence;
import com.orange.labs.conllparser.ConllWord;
import com.orange.labs.conllparser.ConlluPlusConverter;
import com.orange.labs.conllparser.GetReplacement;
import com.orange.labs.conllparser.ValidFeatures;
import com.orange.labs.httpserver.ServeurHTTP;
import com.orange.labs.search.SubTreeSearch;
import java.io.BufferedReader;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * classe très basique qui lit un fichier CONLL, affiche la premiere phrase,
 * permet de modifier les liens et sauvegarder le fichier modifié
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ConlluEditor {
    ConllFile cfile;
    File filename;
    File outfilename = null; // == filename, only in tests this has a different value
    //ConllSentence csent = null;
    //int currentSentenceId = 0;
    //ConllWord modWord = null; // last modified word
    int numberOfSentences = 0;
    int mode = 0; // 1: noedit 2: reread file at each operation (disables editing)

    Set<String> validUPOS = null;
    Set<String> validXPOS = null;
    Set<String> validDeprels = null;
    //Map<String, Set<String>> validFeatures = null; // [upos:]features = [values]
    ValidFeatures validFeatures = null;
    JsonObject shortcuts = null;
    Validator validator = null;
    History history;
    boolean callgitcommit = true;
    int changesSinceSave = 0;
    int saveafter = -1; // save after n changes // -1: save when changing sentence
    int shortcuttimeout = 700; // GUI waits for 700ms, before decing that shortcut is complete

    ConllFile comparisonFile = null;

    Map<String, String>uiconfig = null;

    // info on version and commit
    static private String programmeversion;
    //private String gitcommitidfull;
    static private String gitcommitidabbrev = "?";
    static private String gitcommittime = "?";
    static private boolean gitdirty = false;
    static private String gitbranch = "?";

    private String suffix = ".2"; // used to write the edited file to avoid overwriting the original file

    private int debug = 1;

    public enum Raw {
        LATEX, CONLLU, SDPARSE, VALIDATION, SPACY_JSON
    };

    public ConlluEditor(String conllfile) throws ConllException, IOException {
        this(conllfile, false);
    }

    static public String getVersion() throws IOException {
        // read properties file created by the maven plugin "properties-maven-plugin" (cf. pom.xml)
        java.util.Properties p = new Properties();
        p.load(ClassLoader.getSystemResourceAsStream("conllueditor.properties"));
        programmeversion = p.getProperty("version");
        InputStream gitprops = ClassLoader.getSystemResourceAsStream("git.properties");
        if (gitprops != null) {
            p.load(ClassLoader.getSystemResourceAsStream("git.properties"));
            //gitcommitidfull = p.getProperty("git.commit.id.full");
            gitcommitidabbrev = p.getProperty("git.commit.id.abbrev");
            gitcommittime = p.getProperty("git.commit.time");
            gitdirty = "true".equalsIgnoreCase(p.getProperty("git.dirty"));
            gitbranch = p.getProperty("git.branch");
        }

        StringBuilder sb = new StringBuilder();
        if (!"master".equals(gitbranch)) {
            sb.append(", branche: ").append(gitbranch);
        }
        if (gitdirty) {
            sb.append(", this build contains uncommitted modifications!");
        }
        return String.format("ConlluEditor V %s (commit %s at %s%s)\n", programmeversion, gitcommitidabbrev, gitcommittime, sb.toString());
    }

    public ConlluEditor(String conllfile, boolean overwrite) throws ConllException, IOException {
        System.err.println(getVersion());
        filename = new File(conllfile);
        filename = filename.getAbsoluteFile().toPath().normalize().toFile();

        switch (versionning()) {
            case 1:
                // OK, conllfile is git controlled
                System.err.format("+++ edited file '%s' is git controlled, commiting all changes\n", conllfile);
                break;
            case 2:

                // file is not git controlled but directory is. Check whether conllfile + suffix exist
                File temp = new File(conllfile + suffix);
                if (temp.exists()) {
                    if (overwrite) {
                        System.err.format("*** ATTENTION option --overwrite: overwriting existing backup file '%s%s'\n", conllfile, suffix);
                    } else {
                        throw new ConllException(String.format("Backup file '%s%s' exists already. Either rename or put edited file '%s' under git control", conllfile, suffix, conllfile));
                    }
                }
                System.err.format("+++ edited file '%s' not tracked by git, writing all changes to '%s%s'\n", conllfile, conllfile, suffix);
                break;
            case 3:
                // neither file nor directory are git controlled. Check whether conllfile + suffix exist
                temp = new File(conllfile + suffix);
                if (temp.exists()) {
                    if (overwrite) {
                        System.err.format("*** ATTENTION option --overwrite: overwriting existing backup file '%s%s'\n", conllfile, suffix);
                    } else {
                        throw new ConllException(String.format("Backup file '%s%s' exists already. Either rename or put edited file '%s' under git control", conllfile, suffix, conllfile));
                    }
                }
                System.err.format("+++ edited file '%s' not in git controlled directory, writing all changes to '%s%s'\n", conllfile, conllfile, suffix);

                break;
            default:
                throw new ConllException(String.format("Will not be able to save edited file '%s'", conllfile));
        }

        init();

        // get CTRL-C and try two write changes not yet saved (if used with option --saveAfter)
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    if (changesSinceSave > 0) {
                        System.err.println("Shutting down ConlluEditor, saving pending " + changesSinceSave + " edits ...");
                        changesSinceSave = saveafter;
                        String f = writeBackup(0, null, null, true);
                        System.err.println("Saved " + f);
                    } else {
                        System.err.println("Shutting down ConlluEditor ...");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException ex) {
                    System.err.println("Error during shutdown: " + ex.getMessage());
                }
            }
        });
    }

    private void init() throws IOException, ConllException {
        if ((debug & 0x01) == 1) {
            System.err.println("Loading " + filename);
        }
        cfile = new ConllFile(filename, null);
        numberOfSentences = cfile.getSentences().size();

        System.out.println("Number of sentences loaded: " + numberOfSentences);

        if (true) {
            cfile.checkTree();
        }

    }

    public void setMode(int m) {
        mode = m;
    }

    private Set<String> readList(List<String> filenames) throws IOException {
        Set<String> valid = new HashSet<>();

        for (String fn : filenames) {
            if (fn.endsWith(".json")) {
                continue; // processed elsewhere
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fn), StandardCharsets.UTF_8));

            String line;
            //int ct = 0;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    valid.add(line);
                }
            }
            br.close();
        }
        return valid;
    }

    private Set<String> readUposJson(List<String> filenames) throws IOException {
        Set<String> valid = new HashSet<>();
        for (String lfilename : filenames) {
            if (!lfilename.endsWith(".json")) {
                continue;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(lfilename), StandardCharsets.UTF_8));
            JsonObject jfile = JsonParser.parseReader(br).getAsJsonObject();
            JsonArray upos = jfile.getAsJsonArray("upos");
            if (upos == null) {
                return null;
            }

            for (JsonElement u : upos.asList()) {
                valid.add(u.getAsString());
            }
        }
        return valid;
    }

    private Set<String> readDeprelsJson(List<String> filenames, String lg) throws IOException {
        // read tools/data/deprels.json
        Set<String> valid = new HashSet<>();
        for (String lfilename : filenames) {
            if (!lfilename.endsWith(".json")) {
                continue;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(lfilename), StandardCharsets.UTF_8));
            JsonObject jfile = JsonParser.parseReader(br).getAsJsonObject();
            JsonObject deprels = jfile.getAsJsonObject("deprels");
            if (deprels == null) {
                return null;
            }

            if (lg != null) {
                JsonObject jlang = deprels.getAsJsonObject(lg);
                for (String deprelname : jlang.keySet()) {
                    JsonObject deprel = jlang.getAsJsonObject(deprelname);
                    int permitted = deprel.get("permitted").getAsInt();
                    if (permitted == 0) {
                        continue;
                    }
                    valid.add(deprelname);
                }
            } else {
                // read all universal/global deprels from all languages
                for (String lgcode : deprels.keySet()) {
                    JsonObject jlang = deprels.getAsJsonObject(lgcode);
                    for (String deprelname : jlang.keySet()) {
                        JsonObject deprel = jlang.getAsJsonObject(deprelname);
                        String type = deprel.get("type").getAsString();
                        String doc = deprel.get("doc").getAsString();
                        if (!type.equals("universal") || !doc.equals("global")) {
                            // we only use universal features
                            continue;
                        }
                        valid.add(deprelname);
                    }
                }
            }
        }
        //System.err.println(valid);
        return valid;
    }

    public void setValidUPOS(List<String> filenames) throws IOException {
        // TODO take from --features if given
        // read json files in list
        validUPOS = readUposJson(filenames);
        // rread other files in list
        validUPOS.addAll(readList(filenames));
        System.err.format("%d valid UPOS read from %s\n", validUPOS.size(), filenames.toString());
    }

    public void setValidXPOS(List<String> filenames) throws IOException {
        validXPOS = readList(filenames);
        System.err.format("%d valid XPOS read from %s\n", validXPOS.size(), filenames.toString());
    }

    public void setValidDeprels(List<String> filenames, String lg) throws IOException {
        // read json file in list
        validDeprels = readDeprelsJson(filenames, lg);
        // read the other (text) files
        validDeprels.addAll(readList(filenames));

        if (validDeprels != null) {
            System.err.format("%d valid Deprel read from %s\n", validDeprels.size(), filenames.toString());
        } else {
            System.err.format("no Deprel definitions found in %s. Bad file?\n", filenames.toString());
        }
    }

    public void setValidFeatures(List<String> filenames, String lg, boolean include_unused) throws IOException {
        validFeatures = new ValidFeatures(filenames, lg, include_unused);
    }

    public void setShortcuts(String filename) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(filename));

        Gson gson = new Gson();
        shortcuts = gson.fromJson(bufferedReader, JsonObject.class);

        /* transform legacy shortcuts into new format */
        JsonElement version = shortcuts.get("version");
        if (version == null || version.getAsInt() < 2) {
            JsonObject v2 = new JsonObject();
            for (String key : shortcuts.keySet()) {

                if (key.equals("upos")) {
                    for (String sc : shortcuts.get(key).getAsJsonObject().keySet()) {
                        JsonObject edits = new JsonObject();
                        edits.addProperty("UPOS", shortcuts.get(key).getAsJsonObject().get(sc).getAsString());
                        v2.add(sc, edits);
                    }
                }
                else if (key.equals("deplabel")) {
                    for (String sc : shortcuts.get(key).getAsJsonObject().keySet()) {
                        JsonObject edits = new JsonObject();
                        edits.addProperty("DEP", shortcuts.get(key).getAsJsonObject().get(sc).getAsString());
                        v2.add(sc, edits);
                    }
                }
                else if (key.equals("feats")) {
                     for (String sc : shortcuts.get(key).getAsJsonObject().keySet()) {
                        JsonObject edits = new JsonObject();
                        JsonArray feats = new JsonArray();
                        feats.add(shortcuts.get(key).getAsJsonObject().get(sc).getAsString());
                        edits.add("FEATS", feats);
                        v2.add(sc, edits);
                    }

                }
                else if (key.equals("misc")) {
                     for (String sc : shortcuts.get(key).getAsJsonObject().keySet()) {
                        JsonObject edits = new JsonObject();
                        JsonArray feats = new JsonArray();
                        feats.add(shortcuts.get(key).getAsJsonObject().get(sc).getAsString());
                        edits.add("MISC", feats);
                        v2.add(sc, edits);
                    }

                }
                else if (key.equals("xpos")) {
                    for (String sc : shortcuts.get(key).getAsJsonObject().keySet()) {
                        JsonObject edits = new JsonObject();
                        JsonArray xpos_upos = shortcuts.get(key).getAsJsonObject().get(sc).getAsJsonArray();
                        edits.addProperty("XPOS", xpos_upos.get(0).getAsString());
                        edits.addProperty("UPOS", xpos_upos.get(1).getAsString());
                        v2.add(sc, edits);
                    }
                }
            }
            shortcuts = new JsonObject();
            shortcuts.addProperty("version", 2);
            shortcuts.add("shortcuts", v2);
        }

        shortcuts.addProperty("filename", filename);
        System.err.format("Shortcut file '%s' read\n", filename);
    }

    /** read the User Interface configuration file. The information in the "ui" part
     *  is sent to the client in order to activate/shos/hide buttons
     * @param configfile
     * @throws IOException
     */
    public void setUIConfig(String configfile) throws IOException {
        uiconfig = new HashMap<>();
        InputStream inputStream = new FileInputStream(new File(configfile));
        Yaml yaml = new Yaml();
        Map<String, Object> cfg = yaml.load(inputStream);
        Map<String, Object> ui = (Map<String, Object>)cfg.get("ui");
        if (ui != null) {
            for (String key : ui.keySet()) {
                Object val = ui.get(key);
                if (val.getClass() == String.class) {
                    uiconfig.put(key, (String) ui.get(key));
                } else if (val.getClass() == LinkedHashMap.class) {
                    LinkedHashMap<String, String> map = (LinkedHashMap<String, String>) val;
                    for (String what : map.keySet()) {
                        uiconfig.put(key + "_" + what, map.get(what));
                    }
                } else {
                    System.err.println("Bad value in " + configfile + " vor key " + key);
                }
            }
        } else {
            System.err.format("*** UIConfig '%s': key 'ui' missing\n", configfile);
        }

        String mydir = (new File(configfile)).getParent();
        if (mydir == null) {
            mydir = ".";
        }
        Map<String, Object> validation = (Map<String, Object>)cfg.get("validation");

        if (validation != null) {
            String language = (String)validation.get("language");

            for (String key : validation.keySet()) {
                // System.err.println("QQQQQ " + key + " " + validation.get(key).getClass());
                Object val = validation.get(key);
                try {
                    if (key.equals("xpos")) {
                        List<String> paths = new ArrayList<>();
                        for (String fn : (ArrayList<String>) val) {
                            paths.add(makePath(mydir, fn));
                        }
                        setValidXPOS(paths);
                    } else if (key.equals("upos")) {
                        List<String> paths = new ArrayList<>();
                        //System.err.println("QQQQQ " + key + " " + val.getClass());
                        for (String fn : (ArrayList<String>) val) {
                            paths.add(makePath(mydir, fn));
                        }
                        setValidUPOS(paths);
                    } else if (key.equals("deprels")) {
                        List<String> paths = new ArrayList<>();
                        for (String fn : (ArrayList<String>) val) {
                            paths.add(makePath(mydir, fn));
                        }
                        setValidDeprels(paths, language);
                    } else if (key.equals("features")) {
                        List<String> paths = new ArrayList<>();
                        for (String fn : (ArrayList<String>) val) {
                            paths.add(makePath(mydir, fn));
                        }
                        setValidFeatures(paths, language, false);
                    } else if (key.equals("validator")) {
                        setValidator(makePath(mydir, (String) val));
                    } else if (key.equals("shortcuts")) {
                        setShortcuts(makePath(mydir, (String) val));
                    }
                } catch (ClassCastException ex) {
                    System.err.format("*** UIConfig '%s': Invalid format for key 'validation: %s'\n", configfile, key);
                }
            }
        }
    }

    /* make a file relative from a directory */
    private String makePath(String dir, String path) {
        Pattern pattern = Pattern.compile("\\$\\{(.+)\\}");
        Matcher matcher = pattern.matcher(path);
        while (matcher.find()) {
            String variable = matcher.group(1);
            String replace = System.getenv(variable);
            path = path.replaceAll("\\$\\{" + variable + "\\}", replace);
        }
        File f = new File(path);
        if (f.isAbsolute()) {
            return path;
        } else {
            return Paths.get(dir, path).toString();
        }
    }

    public void setValidator(String validatorconf) {
        validator = new Validator(validatorconf);
    }

    public void setSaveafter(int saveafter) {
        this.saveafter = saveafter;
        if (this.saveafter < 0) {
            System.err.format("saving file when current sentence is changed\n");
        } else {
            System.err.format("saving file after %d edits\n", saveafter);
        }
    }

    public void setShortcutTimeout(int timeout) {
        this.shortcuttimeout = timeout;
    }

    public void setDebug(int d) {
        debug = d;
    }

    public void setComparisonFile(String compfilename) {
        try {
            comparisonFile = new ConllFile(new File(compfilename), null);
            if (cfile.getSentences().size() != comparisonFile.getSentences().size()) {
                System.err.println("Comparison file and edited file must have the same number of sentences");
                comparisonFile = null;
            }
            for (ConllSentence cs : comparisonFile.getSentences()) {
                cs.normalise();
                cs.makeTrees(null);
            }
        } catch (IOException | ConllException e) {
            e.printStackTrace();
            System.err.format("Cannot use comparison file '%s': %s\n", compfilename, e.getMessage());
        }
    }

    private JsonObject prepare(int sentid) {
        JsonObject solution = new JsonObject();
        solution.addProperty("sentenceid", sentid); //currentSentenceId);
        solution.addProperty("maxsentence", numberOfSentences);
        return solution;
    }

    private String returnSubtree(String subtree, int sentid) {
        JsonObject solution = prepare(sentid);
        solution.addProperty("ok", subtree);
        solution.addProperty("changes", changesSinceSave);
        return solution.toString();
    }

    private String formatErrMsg(String msg, int sentid) {
        JsonObject solution = prepare(sentid);
        solution.addProperty("error", msg);
        return solution.toString();
    }

    private String formatSaveMsg(String msg, int sentid) {
        JsonObject solution = prepare(sentid);
        solution.addProperty("ok", msg);
        solution.addProperty("changes", changesSinceSave);
        return solution.toString();
    }

    private String returnTree(int sentid, ConllSentence csent) throws ConllException {
        return returnTree(sentid, csent, null);
    }

    private String returnTree(int sentid, ConllSentence csent, ConllSentence.Highlight highlight) throws ConllException {
        csent.normalise();
        csent.makeTrees(null);

        // calculate arcs for dependency hedge
        Map<Integer, Integer> heights = csent.calculate_flat_arcs_height(); //calculate_flat_arcs_height(csent);
        for (Integer id : heights.keySet()) {
            ConllWord cw = csent.getWord(id);
            cw.setArc_height(heights.get(id));
        }
        JsonObject solution = prepare(sentid);
        solution.addProperty("text", csent.getText()); // contents of "# text = ..."
        Map<Integer, Integer> pos2id = new LinkedHashMap<>();
        solution.addProperty("sentence", csent.getSentence(pos2id)); // tokens concatenated

        // map id:token to mark tokens with checkdeprel/checktoken
        List<Integer>token_start = new ArrayList<>();
        for (int pos : pos2id.keySet()) {
            token_start.add(pos);
        }
        JsonArray check = new JsonArray();
        for (int x = token_start.size()-2; x>= 0; x--) {
            int start = token_start.get(x);
            int end = token_start.get(x+1);

            int i = pos2id.get(start);

            ConllWord mwt = csent.getContracted(i);
            //if (mwt != null) {
            //    System.err.println("eeee" + mwt.getCheckToken());
            //}
            if (csent.getWord(i).getCheckToken() == true
                    || csent.getWord(i).getcheckDeprel()== true
                    || (mwt != null && mwt.getCheckToken() == true)
                    ) {
                JsonArray from_to = new JsonArray();
                from_to.add(start);
                from_to.add(end);
                int what = 0;
                if (csent.getWord(i).getCheckToken()== true
                || (mwt != null && mwt.getCheckToken() == true)) {
                    //from_to.add(0); // token
                    what = 1;
                } //else
                if (csent.getWord(i).getcheckDeprel()== true) {
                    //from_to.add(1); // deprel
                    what = what | 0x02;
                }
                from_to.add(what);
                check.add(from_to);
            }
        }
        //System.err.println("CCCCC " + check);
        if (!check.isEmpty()) {
            solution.add("textcheck", check);
        }
        solution.addProperty("previous_modification", csent.getLastModification());
        solution.addProperty("length", (csent.getWords().size() + csent.numOfEmptyWords()));

//        if (csent.getHightlightdeprels() != null) {
//            JsonArray tokens = new JsonArray();
//            for (String t : csent.getHightlightdeprels()) {
//                tokens.add(t);
//            }
//            solution.add("hightlightdeprels", tokens);
//        }
//
//        if (csent.getHightlighttokens() != null) {
//            JsonArray tokens = new JsonArray();
//            for (String t : csent.getHightlighttokens()) {
//                tokens.add(t);
//            }
//            solution.add("hightlighttokens", tokens);
//        }

        if (csent.getSentid() != null) {
            solution.addProperty("sent_id", csent.getSentid());
        }
        if (csent.getNewpar() != null) {
            solution.addProperty("newpar", csent.getNewpar());
        }
        if (csent.getNewdoc() != null) {
            solution.addProperty("newdoc", csent.getNewdoc());
        }

        if (csent.getTranslit() != null) {
            solution.addProperty("translit", csent.getTranslit());
        }

        // potential transliteration: concatenating the Misc:Translit values of all words
        boolean missingtranslit = false;
        JsonArray translits = new JsonArray();
        for (ConllWord cw : csent.getWords()) {
            if (cw.getMisc().containsKey("Translit")) {
                translits.add("" + cw.getMisc().get("Translit"));
            } else {
                missingtranslit = true;
            }
        }
        if (translits.size() > 0) {
            solution.add("translit_words", translits);
            solution.addProperty("translit_missing", missingtranslit);
        }

        if (csent.getTranslations() != null) {
            JsonObject t = new JsonObject();
            for (String key : csent.getTranslations().keySet()) {
                //solution.addProperty("text_" + key , csent.getTranslations().get(key));
                t.addProperty(key, csent.getTranslations().get(key));
            }

            solution.add("translations", t);
        }

        solution.addProperty("changes", changesSinceSave);
        //solution.addProperty("latex", csent.getLaTeX());
        ConllSentence.AnnotationErrors ae = new ConllSentence.AnnotationErrors();
        solution.add("tree", csent.toJsonTree(validUPOS, validXPOS, validDeprels, validFeatures, highlight, ae)); // RelationExtractor.conllSentence2Json(csent));
        if (comparisonFile != null) {
            ConllSentence goldsent = comparisonFile.getSentences().get(sentid);
            // calculate arcs for dependency hedge
            heights = goldsent.calculate_flat_arcs_height(); //calculate_flat_arcs_height(csent);
            for (Integer id : heights.keySet()) {
                ConllWord cw = goldsent.getWord(id);
                cw.setArc_height(heights.get(id));
            }

            solution.add("comparisontree", goldsent.toJsonTree(validUPOS, validXPOS, validDeprels, validFeatures, null, ae));
//            JsonArray diffs = new JsonArray();
//            for (ConllWord sysw : csent.getWords()) {
//                ConllWord goldw = goldsent.getWord(sysw.getFullId());
//                if (goldw == null) diffs.add(sysw.getFullId());
//                else if (!sysw.equals(goldw)) diffs.add(sysw.getFullId());
//            }
//            //System.err.println("sssss" + diffs);
//            solution.add("differs", diffs);

            JsonObject diffmap = new JsonObject();
            for (ConllWord sysw : csent.getWords()) {
                ConllWord goldw = goldsent.getWord(sysw.getFullId());
                if (goldw == null) {
                    //    diffmap.add(sysw.getFullId());
                } else if (!sysw.equals(goldw)) {
                    JsonObject lines = new JsonObject();
                    // TODO improve: do not code HTML in json !!
                    lines.addProperty("gold", "<td>" + goldw.toString().replaceAll("[ \t]+", "</td> <td>") + "</td>");
                    lines.addProperty("edit", "<td>" + sysw.toString().replaceAll("[ \t]+", "</td> <td>") + "</td>");

                    diffmap.add(sysw.getFullId(), lines);
                }
            }
            //System.err.println("sssss" + diffs);
            solution.add("differs", diffmap);

            solution.addProperty("Lemma", String.format("%.2f", 100 * csent.score(goldsent, ConllSentence.Scoretype.LEMMA)));
            solution.addProperty("Features", String.format("%.2f", 100 * csent.score(goldsent, ConllSentence.Scoretype.FEATS)));
            solution.addProperty("UPOS", String.format("%.2f", 100 * csent.score(goldsent, ConllSentence.Scoretype.UPOS)));
            solution.addProperty("XPOS", String.format("%.2f", 100 * csent.score(goldsent, ConllSentence.Scoretype.XPOS)));
            solution.addProperty("LAS", String.format("%.2f", 100 * csent.score(goldsent, ConllSentence.Scoretype.LAS)));
        }
        solution.addProperty("info", csent.getHead().getMiscStr()); // pour les fichiers de règles, il y a de l'info dans ce champs

        // returning number of errors
        boolean anyerrors = false;
        JsonObject errors = new JsonObject();
        int numHeads = csent.getHeads().size() - csent.numOfEmptyWords();

        if (numHeads > 1) {
            anyerrors = true;
            errors.addProperty("heads", csent.getHeads().size());
        }

        int badroots = 0;
        for (ConllWord cw : csent.getWords()) {
            if (cw.getHead() != 0 && cw.getDeplabel().equals("root")) {
                badroots++;
            }
        }
        if (badroots > 0) {
            anyerrors = true;
            errors.addProperty("badroots", badroots);
        }
        if (ae.upos > 0) {
            anyerrors = true;
            errors.addProperty("invalidUPOS", ae.upos);
        }
        if (ae.xpos > 0) {
            anyerrors = true;
            errors.addProperty("invalidXPOS", ae.xpos);
        }
        if (ae.deprel > 0) {
            anyerrors = true;
            errors.addProperty("invalidDeprels", ae.deprel);
        }
        if (ae.features > 0) {
            anyerrors = true;
            errors.addProperty("invalidFeatures", ae.features);
        }
        String sent = csent.getSentence();
        if (csent.getText() == null) {
            anyerrors = true;
            JsonObject tt = new JsonObject();
            tt.addProperty("text", "");
            tt.addProperty("forms", csent.getSentence());
            tt.addProperty("differs", 1); // first differing character
            errors.add("incoherenttext", tt);
        } else if (!sent.equals(csent.getText())) {
            int pos = -1;
            for (int i = 0; i < sent.length() && i < csent.getText().length(); ++i) {
                if (sent.charAt(i) != csent.getText().charAt(i)) {
                    pos = i;
                    break;
                }
            }
            anyerrors = true;
            JsonObject tt = new JsonObject();
            tt.addProperty("text", csent.getText());
            tt.addProperty("forms", csent.getSentence());
            tt.addProperty("differs", pos); // first differing character
            errors.add("incoherenttext", tt);
        }
        if (anyerrors) {
            solution.add("errors", errors);
        }

        solution.addProperty("comments", csent.getCommentsStr());
        if (history != null) {
            solution.addProperty("canUndo", history.canUndo());
            solution.addProperty("canRedo", history.canRedo());
        } else {
            solution.addProperty("canUndo", false);
            solution.addProperty("canRedo", false);
        }
//        Map<Integer, Integer> heights = calculate_flat_arcs_height(csent);
//        JsonObject arc = new JsonObject();
//        for (Integer id : heights.keySet()) {
//            arc.addProperty("" + id, heights.get(id));
//        }
        //solution.add("arc_heights", arc);

        //Gson gson = new GsonBuilder().setPrettyPrinting().create();
        //System.out.println("SSSSSSSS " +solution);
        return solution.toString();
    }

    public String getInfo() {
        if (mode > 0) {
            return filename.getAbsolutePath() + " [browsing mode only]";
        }
        return filename.getAbsolutePath();
    }

    /**
     * get raw text; Latex, conllu, sdparse or the output of the validation
     * @param raw output format
     * @param currentSentenceId
     * @param all_enhanced if true also enhanced dependencies which are merely a copy ob base dependencies is shown
     * @return raw text as LaTeX, CoNLL-U or SD parse
     */
    public String getraw(Raw raw, int currentSentenceId, boolean all_enhanced) {
        JsonObject solution = new JsonObject();

        ConllSentence csent = null;
        if (currentSentenceId >= 0 && currentSentenceId < numberOfSentences) {
            csent = cfile.getSentences().get(currentSentenceId);
        }

        if (csent != null) {
            switch (raw) {
                case VALIDATION:
                    if (validator == null) {
                        solution.addProperty("raw", "ERROR: no validator configuration given");
                    } else {
                        try {
                            solution.addProperty("raw", validator.validate(csent));
                        } catch (InterruptedException | IOException e) {
                            solution.addProperty("raw", "Validator error: " + e.getMessage());
                        }
                    }

                    break;
                case LATEX:
                    solution.addProperty("raw", csent.getLaTeX(all_enhanced));
                    break;
                case SDPARSE:
                    solution.addProperty("raw", csent.getSDparse());
                    break;
                case SPACY_JSON:
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String jsonOutput = gson.toJson(csent.toSpacyJson());
                    solution.addProperty("raw", jsonOutput);
                    break;
                //case CONLLUPLUS:
                //    solution.addProperty("raw", cfile.getColDefString() + csent.toString());
                //    break;
                case CONLLU:
                default:
                    solution.addProperty("raw", cfile.getColDefString() + csent.toString());
                    //solution.addProperty("raw", csent.toString());
                    break;
            }
        } else {
            solution.addProperty("raw", "no sentence available");
        }
        return solution.toString();
    }

    /**
     * get the lists of valid UPOS/XMPOS/deprel/Features as well as other
     * information
     *
     * @return
     */
    public String getValidlists() {
        JsonObject solution = new JsonObject();
        if (validDeprels != null) {
            List<String> tmp = new ArrayList<>();
            for (String d : validDeprels) {
                tmp.add(d);
            }
            Collections.sort(tmp);

            JsonArray jd = new JsonArray();
            for (String d : tmp) {
                jd.add(d);
            }
            solution.add("validdeprels", jd);
        }
        if (validUPOS != null) {
            List<String> tmp = new ArrayList<>();
            for (String d : validUPOS) {
                tmp.add(d);
            }
            Collections.sort(tmp);
            JsonArray jd = new JsonArray();
            for (String d : tmp) {
                jd.add(d);
            }
            solution.add("validUPOS", jd);
        }

        if (validXPOS != null) {
            List<String> tmp = new ArrayList<>();
            for (String d : validXPOS) {
                tmp.add(d);
            }
            Collections.sort(tmp);
            JsonArray jd = new JsonArray();
            for (String d : tmp) {
                jd.add(d);
            }
            solution.add("validXPOS", jd);
        }

        if (validFeatures != null) {
            JsonArray jd = new JsonArray();
            for (String d : validFeatures.getList()) {
                jd.add(d);
            }
            solution.add("validFeatures", jd);

            JsonObject desc = validFeatures.getAsJson();
            if (desc != null) {
                solution.add("features", desc);
            }
        }

        if (shortcuts != null) {
            solution.add("shortcuts", shortcuts);
        }

        solution.addProperty("filename", filename.getAbsolutePath());
        solution.addProperty("version", programmeversion);
        solution.addProperty("git_commit_id", gitcommitidabbrev);
        solution.addProperty("git_commit_time", gitcommittime);
        solution.addProperty("git_branch", gitbranch);
        solution.addProperty("git_dirty", gitdirty);
        solution.addProperty("reinit", mode);
        solution.addProperty("saveafter", saveafter);
        solution.addProperty("shortcuttimeout", shortcuttimeout);
        solution.add("stats", cfile.getFilestats());
        JsonArray coldefs = new JsonArray();
        for (String cd : cfile.getColDefs().keySet()) {
            coldefs.add(cd);
        }
        solution.add("columns", coldefs);
        if (uiconfig != null) {
            JsonObject jo = new JsonObject();
            for (String key : uiconfig.keySet()) {
                jo.addProperty(key, uiconfig.get(key));
            }
            solution.add("uiconfig", jo);
        }

        //System.err.println("qqqqqqqqq " + solution);
        return solution.toString();
    }

    // only for the unitary tests. Get the lastmodification value of a sentences without using "read" (which will delete undo history)
    public int getLastModification(int sentid) {
        ConllSentence csnt = cfile.getSentences().get(sentid);
        return csnt.getLastModification();
    }

    // TODO: write and commit file only when changing sentence: problem with multithread
    /* Process the commands comming from the JavaScript GUI.
        @param command command string
        @param currentSentenceId the id of the current sentence (sent by the GUI
        @return json representation of the sentence after the command has been applied or error message
        The command has the following format:
          nect                       next sentence
          prec                       preceeding sentence
          read <num>                 return sentence <num>
          line <num>                 return sentence which contains line number <num>
          findsentid "true" <regex>  find sentence with sentid matching regex. if 2nd argument == "true", search backwards
          findhighlight "true"       find sentence with highlighted token or deprel
          findcomment "true" <regex> find sentence with comment matching regex. if 2nd argument == "true", search backwards
          findword "true" <string>   find string in sentence (may include spaces). if 2nd argument == "true", search backwards
          findmulti "true" <string>  find sequence of tokens: "l:the/u:NOUN" finds the lemma "the" followed by a token with upos "NOUN3
                                     "l:fish;u:VERB" finds token with lemma "fish" and upos "VERB"
          find{lemma|feat|upos|xpos} "true" <regex>
                                     find sentence with lemma/feat/upos/xpos matching regex
          finddeprel "true" <string> find a sentence with a deprel or a subtree: "aux" or "det>nsubj"
          findsubtree "true" <string> find a sentence which matches the partial tree (given as CoNLL-U or CoNLL-U plus)
          createsubtree <tokenid>    create a subtree by taking given id as root
          replaceexpression "false" <conditions> > <replacements>
          mod <field> <tokenid> <value>
                                     modifiy token of current sentence
                                       field: form leamm upos xpos feat deprel enhdpes misc
          mod <fields> <tokenid> <value> <value2>
                                     modifiy token of current sentence (no blanks allowed in <value> or <value2>)
                                       fields:
                                         pos (set upos to <value> and xpos to <value2>)
                                         extracol (set extracolumn named <value> to <value2>)
          mod tomwt <tokenid> <form1> <form2> [...]
                                     transform a token into a multiword token, with the tokens given (form1, form2, ...)
                                     e.g if token 3 is "wanna",
                                         3	wanna	wanna VERB	...
                                     "mod tomwt 2 want to" creates
                                         3-4	wanna	_	_	...
                                         3	want	wanna	VERB	...
                                         4	to	_	_	...
                                    (tokens 3 and 4 normally have to be further edited)
          mod compose <tokenid> <length> [form]
                                     make a MWT from <length> tokens starting with token <tokenid>
                                     e.g
                                         5	do	VERB	...
                                         6	n't	PART	...
                                     "mod compose 5 2 don't" produces
                                         5-6	don't	_	_	...
                                         5	do	VERB	...
                                         6	n't	PART	...
          mod editmwt <starttokenid> <endtokenid> <highlighttoken> [<MISC data>]
                                     modify the span of a current MWT (or delete it by setting <endtokenid> to 0

          mod split <tokenid> [<position>]]
                                     split the token with <tokenid> in two. If <position> split form  (and lemma) at this position,
                                     else the new token is simply a copy of the existing one
          mod join <tokenid>         merge the token <tokenid> with the following
          mod delete <tokenid>       delete token with <tokenid> (all deprels of the sentence and all subsequent tokenids will be adapted)
          mod sentsplit <tokenid>    split the current sentence into two sentence, with token <tokenid> being the first token of the new sentence
          mod sentjoin               merge the current sentence with the following
          mod insert <tokenid> <form> [<lemma> [<upos> [<xpos>]]]
                                     insert a new token after the token <tokenid> (new token has id tokenid+1
          mod emptyinsert <tokenid> <form> [<lemma> [<upos> [<xpos>]]]
                                     enhanced dependencies: insert a new EMPTY token before the token <tokenid>
                                     (new token has id "<tokenid>.1"
          mod editmetadata <json>    modify sentence metadata, needs an json object like
                                     { "newdoc":"document id",
                                       "newpar":"paragraph id",
                                       "sent_id":"sentence id",
                                       "text": "sentence", // for "# text"
                                       "translit":"transliteration",
                                       "translations":"de: German translation\nen: English translation ..."
                                      }
          mod comments <comment>     set the comment fo a sentence (# text, # sent_id, # newpar etc are set automatically!)
          mod undo                   undo last "mod" command (if we are still in the same sentence)
          mod redo                   redo last "undone" "mod" command
          mod ed add <depid> <headid> <deprel>
                                     add an enhanced dependency <deprel> from <headid> to <depid>
          mod ed del <depid> <headid>
                                     delete enhanced dependency from <headid> to <depid>
          mod <newheadid> <tokenid> [<deprel>]
                                     make token <tokenid> a dependant of token <newheadid>.
                                     Keep current deprel unless a new <deprel> is provided.
          save                       save the file either to <filename.conllu>.2 (if <filename.conllu> not git controlled)
                                     or to <filename.conllu> and execute a "git add" and "git commit"
    */
    public String process(String command, int currentSentenceId, String editinfo, int prevmod) {
        if (mode == 2) {
            try {
                init();
            } catch (IOException | ConllException ex) {
                return formatErrMsg("Cannot reinit file " + filename, currentSentenceId);
            }
        }

        if ((debug & 0x01) == 1) {
            System.err.println("COMMAND [" + command + "] sid: " + currentSentenceId);
        }
        try {
            if (!command.startsWith("mod ")) {
                // we changed the sentence, forget history
                history = null;

                if (!command.startsWith("save") && changesSinceSave > 0 && saveafter == -1) {
                    // save sentence because the changed sentence (if saveafter == -1)
                    try {
                        writeBackup(currentSentenceId, null, editinfo, true);
                    } catch (IOException ex) {
                        return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                    }
                }
            } else {
                if (mode != 0) {
                    return formatErrMsg("NO editing in browse mode", currentSentenceId);
                }

                // check whether the previous_modificatin date returned by the client is the same
                // as the one of the server. If not another client has changed the sentence in the meantime and we
                // must reject the modification coming in.
                ConllSentence csentt = cfile.getSentences().get(currentSentenceId);
                System.err.println("PREVMOD local:" + csentt.getLastModification() + " client: " + prevmod);
                if (csentt.getLastModification() > prevmod) {
                     return formatErrMsg(String.format("This sentence (%s) has been modified by another user (mod counter %d > %d).\nModification rejected.\nPlease reload sentence before next modification",
                               currentSentenceId, csentt.getLastModification(), prevmod),  currentSentenceId);
                }

                changesSinceSave += 1;
            }


            ConllSentence csent; // = cfile.getSentences().get(currentSentenceId);

            if (command.equals("prec")) {
                if (currentSentenceId > 0) {
                    currentSentenceId--;
                }

                csent = cfile.getSentences().get(currentSentenceId);
                return returnTree(currentSentenceId, csent);
            } else if (command.equals("next")) {
                if (currentSentenceId < numberOfSentences - 1) {
                    currentSentenceId++;
                }
                csent = cfile.getSentences().get(currentSentenceId);
                return returnTree(currentSentenceId, csent);
            } else if (command.startsWith("findhighlight")) {
                String[] f = command.trim().split(" +", 2);
                if (f.length != 2) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    for (ConllWord cw : cs.getAllWords()) {
                        if (cw.getCheckToken() || cw.getcheckDeprel()) {
                           currentSentenceId = i;
                           return returnTree(currentSentenceId, cs, null /* highlight*/);
                        }
                    }
                }
                return formatErrMsg("No sentence with highlighted tokens/deprels found", currentSentenceId);
            } else if (command.startsWith("findsentid")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");
                Pattern idAtrouver = Pattern.compile(f[2]);

                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    String sid = cs.getSentid();
                    if (sid != null) {
                        Matcher m = idAtrouver.matcher(sid);
                        if (m.find()) {
                            currentSentenceId = i;
                            return returnTree(currentSentenceId, cs, null /* highlight*/);
                        }
                    }
                }

                return formatErrMsg("Sentence id not found «" + idAtrouver + "»", currentSentenceId);

            } else if (command.startsWith("findcomment ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");
                // si on met le mot cherché entre double quote, on peut y inclure des espaces
                String motAtrouver = f[2];
                if (motAtrouver.charAt(0) == '"' && motAtrouver.endsWith("\"")) {
                    motAtrouver = motAtrouver.substring(1, motAtrouver.length() - 1);
                    //System.err.println("<" + motAtrouver + ">");
                }
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    String text = cs.getCommentsStr();
                    int wordoffset = text.indexOf(motAtrouver);

                    if (wordoffset > -1) {
                        List<Integer> startindex = new ArrayList<>(); // offset where the words start
                        int offset = 0;
                        for (ConllWord cw : cs.getWords()) {
                            startindex.add(offset);
                            offset += cw.getForm().length() + 1;
                        }

                        int firstid = -1;
                        //int lastid = -1;
                        int id = 0;
                        for (int ix : startindex) {
                            //System.err.println("AAA " + wordoffset + " ix:" + ix + " " + id);
                            if (ix > wordoffset && firstid == -1) {
                                firstid = id;
                                //System.err.println("First " + firstid);
                            }
                            id++;
                            if (ix <= wordoffset + motAtrouver.length()) {
                                //lastid = id;
                                //System.err.println("Last " + lastid);
                            }
                        }

                        currentSentenceId = i;

                        if (motAtrouver.charAt(0) == ' ') {
                            firstid++;
                        }
                        //if (motAtrouver.endsWith(" ")) {
                        //    lastid--;
                        //}

                        ConllSentence.Highlight hl = null; //new ConllSentence.Highlight(ConllWord.Fields.FORM, firstid, lastid);
                        return returnTree(currentSentenceId, cs, hl /*, motAtrouver*/);
                    }
                }
                return formatErrMsg("Comment not found «" + motAtrouver + "»", currentSentenceId);

            } else if (command.startsWith("findword ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                // si on met le mot cherché entre double quote, on peut y inclure des espaces
                String motAtrouver = f[2];
                if (motAtrouver.charAt(0) == '"' && motAtrouver.endsWith("\"")) {
                    motAtrouver = motAtrouver.substring(1, motAtrouver.length() - 1);
                    //System.err.println("<" + motAtrouver + ">");
                }
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    Map<Integer, Integer> pos2id = new TreeMap<>();
                    String text = cs.getSentence(pos2id);
                    //System.err.println("zzzzz " + pos2id);
                    int wordoffset = text.indexOf(motAtrouver);

                    if (wordoffset > -1) {
                        int wordendoffset = wordoffset + motAtrouver.length();
                        if (motAtrouver.charAt(0) == ' ') {
                            wordoffset++;
                        }
                        if (motAtrouver.endsWith(" ")) {
                            wordendoffset--;
                        }
                        //System.err.println("eeee " + motAtrouver + " "+ wordoffset + " " + wordendoffset);
                        int firstid = -1;
                        int lastid = -1;

                        for (Integer cwstartpos : pos2id.keySet()) {
                            if (cwstartpos <= wordoffset) {
                                firstid = pos2id.get(cwstartpos);
                            }
                            if (cwstartpos < wordendoffset) {
                                lastid = pos2id.get(cwstartpos);
                            }
                        }

                        //System.err.println("rrrrr " + firstid + " " + lastid);
                        currentSentenceId = i;

                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.FORM, firstid, lastid);
                        return returnTree(currentSentenceId, cs, hl /*, motAtrouver*/);
                    }
                }
                return formatErrMsg("Word not found «" + motAtrouver + "»", currentSentenceId);

            } else if (command.startsWith("findmulti ")) {
                // find sequences of words by different criteria:  u:NOUN/l:yn/x:verbnoun
                // TODO: or find word with multiple criteria u:NOUN%l:power/l:of
                String[] f = command.trim().split(" +");
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");
                String[] elems = f[2].split("/");

                class SearchField {

                    List<ConllWord.Fields> fields;
                    List<String> values;

                    public SearchField(String f) {
                        String[] andconditions = f.split("%", 2);
                        fields = new ArrayList<>();
                        values = new ArrayList<>();
                        for (String andcondition : andconditions) {
                            String[] tmp = andcondition.split(":", 2);
                            //value = tmp[1];
                            values.add(tmp[1]);

                            if (tmp[0].startsWith("l")) {
                                fields.add(ConllWord.Fields.LEMMA);
                            } else if (tmp[0].startsWith("u")) {
                                fields.add(ConllWord.Fields.UPOS);
                            } else if (tmp[0].startsWith("x")) {
                                fields.add(ConllWord.Fields.XPOS);
                            } else if (tmp[0].startsWith("d")) {
                                fields.add(ConllWord.Fields.DEPREL);
                            } else if (tmp[0].startsWith("e")) {
                                // enhanced deps
                                fields.add(ConllWord.Fields.DEPS);
                            } else /*if (f[0].equals("findxpos")) */ {
                                fields.add(ConllWord.Fields.FORM);
                            }
                        }
                    }
                }

                // list of expressions to match a sequnce of words
                List<SearchField> fields = new ArrayList<>();
                for (String e : elems) {
                    if (!e.contains(":")) {
                        return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                    }
                    fields.add(new SearchField(e));
                }

                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);

                    Iterator<ConllWord> cwit = cs.getAllWords().iterator();//cs.getWords().iterator();
                    while (cwit.hasNext()) {
                        ConllWord cw = cwit.next();
                        if (cw.matchesFields(fields.get(0).fields, fields.get(0).values)) {
                            currentSentenceId = i;
                            int firstid = cw.getId();
                            boolean ok = true;
                            List<ConllWord.Fields> fl = new ArrayList<>();
                            fl.add(fields.get(0).fields.get(0)); // for highlighting
                            for (int j = 1; j < elems.length; ++j) {
                                if (!cwit.hasNext()) {
                                    ok = false;
                                    break;
                                }
                                cw = cwit.next();
                                if (!cw.matchesFields(fields.get(j).fields, fields.get(j).values)) {
                                    ok = false;
                                    break;
                                }
                                fl.add(fields.get(j).fields.get(0));
                            }
                            if (ok) {
                                // TODO in case of enhanced deps, all ehds of a word are highlighted
                                ConllSentence.Highlight hl = new ConllSentence.Highlight(
                                        fl,
                                        firstid, cw.getId());
                                return returnTree(currentSentenceId, cs, hl);
                            }
                        }
                    }
                }
                return formatErrMsg("not found «" + f[2] + "»", currentSentenceId);

            } else if (command.startsWith("findgrewmatch ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                CheckGrewmatch cgm = new CheckGrewmatch(f[2], false);
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    List<List<ConllWord>> llcw = cgm.match(null, cs);
                    if (llcw != null) {
                        Set<Integer> ids = new TreeSet<>();
                        for (List<ConllWord> lcw : llcw) {
                            for (ConllWord cw : lcw) {
                                ids.add(cw.getId());
                            }
                        }

                        currentSentenceId = i;
                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.FORM, ids);
                        return returnTree(currentSentenceId, cs, hl); //cw.getUpostag());
                    }
                }
                return formatErrMsg("not found «" + f[2] + "»", currentSentenceId);

            } else if (command.startsWith("findexpression ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                CheckCondition findpt = new CheckCondition(f[2], false);
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    ConllWord cw = cs.conditionalSearch(findpt); //f[2]);
                    if (cw != null) {
                        currentSentenceId = i;
                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.LEMMA, cw.getId(), cw.getId());
                        return returnTree(currentSentenceId, cs, hl); //cw.getUpostag());
                    }
                }
                return formatErrMsg("not found «" + f[2] + "»", currentSentenceId);

            } else if (command.startsWith("replaceexpression ")) {
                if (mode != 0) {
                    return formatErrMsg("NO editing in browse mode", currentSentenceId);
                }
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                String[] find_replace = f[2].split(" >");
                if (find_replace.length != 2) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                String find = find_replace[0].strip();
                String replace = find_replace[1].strip();
                if (find.isEmpty()) {
                    return formatErrMsg("INVALID syntax. Missing search expression «" + command + "»", currentSentenceId);
                }
                if (replace.isEmpty()) {
                    return formatErrMsg("INVALID syntax. Missing replace expression «" + command + "»", currentSentenceId);
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                //List<String>newvals = Arrays.asList(replace.split("[ \\t]+"));
                List<GetReplacement> newvals = new ArrayList<>();
                for (String repl : replace.split("[ \\t]+")) {
                    newvals.add(new GetReplacement(repl));
                }

                CheckCondition findpt = new CheckCondition(find, false);
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);
                    if (history == null) {
                        history = new History(200);
                    }
                    history.add(cs);

                    StringBuilder warnings = new StringBuilder();
                    Set<ConllWord> cws = cs.conditionalEdit(findpt, newvals, null, warnings);
                    // TODO display warnings in GUI!
                    if (!cws.isEmpty()) {
                        currentSentenceId = i;
                        Set<Integer> ids = new HashSet<>();
                        for (ConllWord cw : cws) {
                            ids.add(cw.getId());
                        }
                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.LEMMA, ids);

                        changesSinceSave += 1;
                        try {
                            writeBackup(currentSentenceId, null, editinfo);
                        } catch (IOException ex) {
                            return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                        }

                        return returnTree(currentSentenceId, cs, hl); //cw.getUpostag());
                    }
                }
                return formatErrMsg("not found «" + find + "»", currentSentenceId);

            } else if (command.startsWith("findlemma ")
                    || command.startsWith("findfeat ")
                    || command.startsWith("findupos ")
                    || command.startsWith("findxpos ")) {
                String[] f = command.trim().split(" +");
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                ConllWord.Fields field;
                if (f[0].equals("findlemma")) {
                    field = ConllWord.Fields.LEMMA;
                } else if (f[0].equals("findfeat")) {
                    field = ConllWord.Fields.FEATURE;
                } else if (f[0].equals("findupos")) {
                    field = ConllWord.Fields.UPOS;
                } else /*if (f[0].equals("findxpos")) */ {
                    field = ConllWord.Fields.XPOS;
                }

                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                String[] elems = f[2].split("/");
                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);

                    Iterator<ConllWord> cwit = cs.getWords().iterator();
                    while (cwit.hasNext()) {
                        ConllWord cw = cwit.next();
                        if (cw.matchesField(field, elems[0])) {
                            currentSentenceId = i;
                            int firstid = cw.getId();
                            boolean ok = true;
                            for (int j = 1; j < elems.length; ++j) {
                                if (!cwit.hasNext()) {
                                    ok = false;
                                    break;
                                }
                                cw = cwit.next();
                                if (!cw.matchesField(field, elems[j])) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) {
                                ConllSentence.Highlight hl = new ConllSentence.Highlight(field, firstid, cw.getId());
                                return returnTree(currentSentenceId, cs, hl); //cw.getUpostag());
                            }
                        }
                    }
                }
                return formatErrMsg(field + " not found «" + f[2] + "»", currentSentenceId);
            } else if (command.startsWith("findsubtree ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }
                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                SubTreeSearch std = new SubTreeSearch(f[2]);

                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {
                    ConllSentence cs = cfile.getSentences().get(i);

                    Set<Integer> ids = std.match(cs);
                    if (!ids.isEmpty()) {
                        currentSentenceId = i;
                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.FORM, ids);
                        return returnTree(currentSentenceId, cs, hl);
                    }
                }
                return formatErrMsg("subtree not found «" + f[2] + "»", currentSentenceId);

            } else if (command.startsWith("createsubtree ")) {
                String[] f = command.trim().split(" +", 3);
                if (f.length < 2) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                String coldefs = null;
                if (f.length == 3) {
                    if (!f[2].startsWith("# global.columns =")
                            && f[2].startsWith("#global.columns=")) {
                        return formatErrMsg("INVALID global.columns line «" + command + "»", currentSentenceId);
                    }
                    if (!f[2].contains("ID") || !f[2].contains("HEAD")) {
                        return formatErrMsg("INVALID glocal.columns line, must contain at least ID and HEAD «" + command + "»", currentSentenceId);
                    }
                    coldefs = f[2].split("=")[1].strip().replaceAll("\\s+", ",");
                }

                ConllSentence cs = cfile.getSentences().get(currentSentenceId);
                int wid = Integer.parseInt(f[1]);
                ConllSentence subtree = cs.getSubtree(wid);
                StringBuilder sb = new StringBuilder();

                if (coldefs != null) {
                    ConlluPlusConverter cpc = new ConlluPlusConverter(coldefs);
                    InputStream is = new ByteArrayInputStream(subtree.toString().getBytes(StandardCharsets.UTF_8));
                    String gg = cpc.convert(is);
                    sb.append(gg);
                } else {
                    sb.append("# global.columns = ");
                    for (String s : cs.getColumndefs().keySet()) {
                        sb.append(" ").append(s);
                    }
                    sb.append("\n").append(subtree.toString());
                }
                return returnSubtree(sb.toString(), currentSentenceId);

            } else if (command.startsWith("finddeprel ")) {
                String[] f = command.trim().split(" +");
                if (f.length != 3) {
                    return formatErrMsg("INVALID syntax «" + command + "»", currentSentenceId);
                }

                String[] rels = f[2].split("[<>=]");
                String[] updown = f[2].split("[^<>=]+");
                //System.err.println("RL " + String.join(",", rels));
                //System.err.println("UD " + String.join(",", updown));
                // si le deuxième mot est "true" on cherche en arrière
                boolean backwards = f[1].equalsIgnoreCase("true");

                for (int i = (backwards ? currentSentenceId - 1 : currentSentenceId + 1);
                        (backwards ? i >= 0 : i < numberOfSentences);
                        i = (backwards ? i - 1 : i + 1)) {

                    ConllSentence cs = cfile.getSentences().get(i);
                    for (ConllWord cw : cs.getWords()) {
                        if (cw.matchesDeplabel(rels[0])) {
                            if (rels.length == 1) {
                                currentSentenceId = i;
                                //csent = cs;
                                ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.DEPREL, cw.getId());
                                return returnTree(currentSentenceId, cs, hl); //cw.getDeplabel());
                            } else {
                                // chaine de deprels
                                Set<Integer> toHighlight = new HashSet<>();
                                cs.makeTrees(null);
                                boolean ok = cw.matchesTree(1, rels, updown, toHighlight);
                                if (ok) {
                                    //System.err.println("--------------- " + cw.getId());
                                    toHighlight.add(cw.getId());
                                    currentSentenceId = i;
                                    ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.DEPREL, toHighlight);
                                    return returnTree(currentSentenceId, cs, hl);
                                }
                                /*
                                boolean ok = true;
                                ConllWord head = cw;
                                Set<Integer>toHighlight = new HashSet<>();
                                //System.err.println("CW: " + cw);
                                toHighlight.add(cw.getId());
                                for (int r = 1; r < rels.length; ++r) {
                                    if (updown[r].equals(">")) {
                                        // does head has deprel ?
                                        head = head.getHeadWord();
                                        if (head == null || !head.matchesDeplabel(rels[r])) {
                                            ok = false;
                                            break;
                                        }
                                        toHighlight.add(head.getId());
                                    } else {
                                        // does word have dependent with deprel
                                        head = head.getHeadWord();
                                        List<ConllWord> deps = head.getDWordsRE(rels[r], false);
                                        if (deps == null || deps.isEmpty()) {
                                            ok = false;
                                            break;
                                        }
                                        //System.err.println("deps" + deps);
                                    }
                                }
                                if (ok) {
                                    currentSentenceId = i;
                                    ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.DEPREL, toHighlight);
                                    return returnTree(currentSentenceId, cs, hl);
                                }*/
                            }
                        }
                    }
                }
                return formatErrMsg("DepRel not found «" + f[2] + "»", currentSentenceId);

            } else if (command.startsWith("line ")) {
                // find sentence in function of linenumber in source file
                String[] f = command.trim().split(" +");
                if (f.length != 2) {
                    return formatErrMsg("INVALID command «" + command + "»", currentSentenceId);
                }
                int ln = Integer.parseInt(f[1]);
                int [] sn_offset = cfile.getSentence_with_line(ln);
                if (sn_offset == null) {
                    return formatErrMsg("linenumber beyond EOF «" + command + "»", currentSentenceId);
                } else {
                    csent = cfile.getSentences().get(sn_offset[0]);
                    currentSentenceId = sn_offset[0];
                    int highlighted_word = ln - sn_offset[1] - sn_offset[2] + 1; // given line number - first line number of sentence in file - comment-length
                    if (highlighted_word > 0) {
                        ConllSentence.Highlight hl = new ConllSentence.Highlight(ConllWord.Fields.FORM, highlighted_word);
                        return returnTree(currentSentenceId, csent, hl);
                    } else {
                        return returnTree(currentSentenceId, csent);
                    }
                }

            } else if (command.startsWith("read ")) {
                String[] f = command.trim().split(" +");
                if (f.length != 2) {
                    return formatErrMsg("INVALID command «" + command + "»", currentSentenceId);
                }
                int sn;
                if (f[1].equals("last")) {
                    sn = cfile.getSentences().size() - 1;
                } else {
                    sn = Integer.parseInt(f[1]);
                }
                if (sn < 0 && sn >= cfile.getSentences().size()) {
                    return formatErrMsg("NO sentence number «" + command + "»", currentSentenceId);

                }
                if (sn >= 0 && sn < cfile.getSentences().size()) {
                    csent = cfile.getSentences().get(sn);
                    currentSentenceId = sn;
                    return returnTree(currentSentenceId, csent);
                } else {
                    return formatErrMsg("INVALID sentence number «" + command + "»", currentSentenceId + 1);
                }

            } else if (command.startsWith("mod upos ") // mod upos id val
                    || command.startsWith("mod xpos ")
                    || command.startsWith("mod pos ") // changes upos and xpos
                    || command.startsWith("mod lemma ")
                    || command.startsWith("mod form ")
                    || command.startsWith("mod deprel ")
                    || command.startsWith("mod enhdeps ")
                    || command.startsWith("mod feats ") // || command.startsWith("mod feat ") // TODO delete "mod feat", it is legacy
                    || command.startsWith("mod addfeat ")
                    || command.startsWith("mod misc ")
                    || command.startsWith("mod addmisc ")
                    || command.startsWith("mod extracol ") // mod extracol id colname vals....
                    ) {
                // on attend
                //    "mod upos id newupos" par ex. mod xpos 3 ART"
                String[] f = command.trim().split(" +", 4); // 4th element may contain blanks
                if (f.length < 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                if (f.length < 4 && !f[1].equals("feats") && !f[1].equals("misc")) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                if ("pos".equals(f[1]) || "extracol".equals(f[1])) {
                    // resplit, since we to expect an UPOS and an XPOS without any blanks
                    f = command.trim().split(" +", 5);
                    if (f.length < 5) {
                        return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                    }
                }

                ConllWord modWord = null;
                try {
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (f[2].contains(".")) {
                        /*String[] id = f[2].split("\\.");
                        List<ConllWord> ews = csent.getEmptyWords().get(Integer.decode(id[0]));
                        int subid = Integer.parseInt(id[1]);
                        if (subid > ews.size()) {
                            return formatErrMsg("INVALID subid «" + command + "»", currentSentenceId);
                        }
                        modWord = ews.get(subid - 1);*/
                        modWord = csent.getEmptyWord(f[2]);
                        if (modWord == null) {
                            return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                        }
                    } else {
                        int id = Integer.parseInt(f[2]);

                        if (id < 1 || id > csent.getWords().size()) {
                            return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                        }
                        modWord = csent.getWords().get(id - 1);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                //ConllWord modWord = csent.getWords().get(id - 1);
                switch (f[1]) {
                    case "pos":
                        modWord.setUpostag(f[3]);
                        modWord.setXpostag(f[4]);
                        break;
                    case "upos":
                        modWord.setUpostag(f[3]);
                        break;
                    case "xpos":
                        modWord.setXpostag(f[3]);
                        break;
                    case "form":
                        modWord.setForm(f[3]);
                        break;
                    case "lemma":
                        modWord.setLemma(f[3]);
                        break;
                    case "deprel": // change only dep label, not head
                        modWord.setDeplabel(f[3]);
                        break;
		    //case "feat": // TODO delete "mod feat", it is legacy
                    case "feats": {
                        if (f.length < 4) {
                            modWord.setFeatures(ConllWord.EmptyColumn);
                        } else {
                            modWord.setFeatures(f[3]);
                        }
                        break;
                    }
                    case "addfeat": {
                        modWord.addFeature(f[3]);
                        break;
                    }

                    case "misc": {
                        if (f.length < 4) {
                            modWord.setMisc(ConllWord.EmptyColumn);
                        } else {
                            modWord.setMisc(f[3]);
                        }
                        break;
                    }
                    case "addmisc": {
                        modWord.addMisc(f[3]);
                        break;
                    }
                    case "enhdeps": {
                        modWord.setDeps(f[3]);
                        break;
                    }
                    case "extracol": {
                        // reject invalid column
                        if (!csent.isValidExtraColumn(f[3])) {
                            return formatErrMsg("Invalid extracolumn for this sentence: " + f[3], currentSentenceId);
                        }
                        modWord.setExtracolumns(f[3], f[4]);
                        break;
                    }
                }

                csent.increaseModificationCounter();

                try {
                    writeBackup(currentSentenceId, modWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod tomwt")) {
                // make a MWT from a word: mod tomwt <id> word1 word2 ...
                String[] f = command.trim().split(" +");
                if (f.length < 5) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                int id;

                try {
                    id = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + f[2] + "» " + e.getMessage(), currentSentenceId);
                }

                if (csent.isPartOfMWT(id)) {
                    return formatErrMsg("Word " + id + " is part of a MWT already. «" + command + "»", currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                // insert a new MWT before the current word
                // the current word becomes the first word of the MWT (inheriting all columns except form)
                ConllWord cw = csent.getWord(id);
                ConllWord composedWord = new ConllWord(cw.getForm(), id, id + f.length - 4, csent.getColumndefs());

                //get spaceafterfrom orginal word
                Map<String, Object> misc = cw.getMisc();
                String sakey = null;
                String savalue = (String) misc.get("SpaceAfter");
                if (savalue == null) {
                    savalue = (String) misc.get("SpacesAfter");
                    if (savalue != null) {
                        sakey = "SpacesAfter";
                    }
                } else {
                    sakey = "SpaceAfter";
                }

                if (sakey != null) {
                    misc.remove(sakey);
                    composedWord.addMisc(sakey, savalue);
                }

                cw.setForm(f[3]);
                cw.setLemma(f[3]);

                for (int x = 4; x < f.length; ++x) {
                    // add other new words
                    ConllWord cw2 = new ConllWord(f[x], csent.getColumndefs());
                    cw2.setLemma(f[x]);
                    cw2.setHead(cw.getId());
                    cw2.setDeplabel("fixed");
                    csent.addWord(cw2, id + x - 4);

                }

                csent.addWord(composedWord, id);

                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, composedWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod compose")) {
                // add composed form: mod compose <id> <composelength> [form]
                String[] f = command.trim().split(" +");
                if (f.length < 4) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                int id;
                int complen;
                try {
                    id = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                    complen = Integer.parseInt(f[3]);
                    if (id + complen > csent.getWords().size()) {
                        return formatErrMsg("INVALID MWT length (to big) «" + command + "»", currentSentenceId);
                    }
                    if (complen < 2) {
                        return formatErrMsg("INVALID MWT length (must be >= 2) «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }
                //System.err.println("<" + f[1] + ">");

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                // delete all Space(s)After in tokens which are now part of the MWT and add the correct SpaceAfter
                // to the new MWT
                String spaceAfterVal = null;
                String spaceAfterKey = null;
                String composedForm = "";

                for (int pos = id - 1; pos < id + complen - 1; ++pos) {
                    spaceAfterKey = null;
                    ConllWord cw = csent.getWords().get(pos);
                    composedForm += cw.getForm();
                    Map<String, Object> misc = cw.getMisc();
                    spaceAfterVal = (String) misc.get("SpaceAfter");
                    if (spaceAfterVal == null) {
                        spaceAfterVal = (String) misc.get("SpacesAfter");
                        if (spaceAfterVal != null) {
                            misc.remove("SpacesAfter");
                            spaceAfterKey = "SpacesAfter";
                        }
                    } else {
                        misc.remove("SpaceAfter");
                        spaceAfterKey = "SpaceAfter";
                    }
                }

                if (f.length > 4) {
                    composedForm = f[4];
                }
                ConllWord composedWord = new ConllWord(composedForm, //csent.getWords().get(id - 1).getForm(),
                        id, id + complen - 1);

                if (spaceAfterKey != null) {
                    composedWord.addMisc(spaceAfterKey, spaceAfterVal);
                }
                csent.addWord(composedWord, id);
                csent.setText(csent.getSentence());
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, composedWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod editmwt") || command.startsWith("mod editmwe")) { // mod editmwt current_start new_end form [MISC column data]
                String[] f = command.trim().split(" +");
                if (f.length < 6) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                int start;
                int end;
                String form = f[4];
                String misc = "_";
                String highlighttoken = f[5];
                if (f.length > 6) {
                    misc = f[6];
                }

                try {
                    start = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (start < 1 || start > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                    end = Integer.parseInt(f[3]);

                    if ((end > 0 && end < start) || end > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }

                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }
                //System.err.println("<" + f[1] + ">");

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                // delete MT word
                if (end == 0) {
                    csent.deleteContracted(start);
                    csent.setText(csent.getSentence());
                    return returnTree(currentSentenceId, csent);
                }
                // modify it
                ConllWord cw = csent.getContracted(start);
                if (cw != null) {
                    cw.setForm(form);
                    cw.setSubId(end);
                    cw.setId(start);
                    cw.setCheckToken(highlighttoken.equalsIgnoreCase("true"));
                    if (misc != null) {
                        cw.setMisc(misc);
                    }
                }

                csent.setText(csent.getSentence());
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, cw, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod split ")
                    || command.startsWith("mod join ")
                    || command.startsWith("mod delete ")) {
                String[] f = command.trim().split(" +");

                if (f.length < 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                int id;
                try {
                    id = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }
                //System.err.println("<" + f[1] + ">");

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                ConllWord modWord = null;
                if (f[1].equals("split")) {
                    //System.err.println("SPLIT");
                    int splitpos = -1;
                    if (f.length > 3) {
                        try {
                            splitpos = Integer.parseInt(f[3]);
                        } catch (NumberFormatException e) {
                            return formatErrMsg("INVALID splitpos (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                        }
                    }

                    ConllWord curWord = csent.getWords().get(id - 1);
                    modWord = new ConllWord(curWord);
                    // delete enhanced deps from copy. Does not make sense keeping them
                    modWord.setDeps("_");

                    if (splitpos > 0 && splitpos < curWord.getForm().length()) {
                        curWord.setForm(curWord.getForm().substring(0, splitpos));
                        modWord.setForm(modWord.getForm().substring(splitpos));
                        if (splitpos < curWord.getLemma().length()) {
                            curWord.setLemma(curWord.getLemma().substring(0, splitpos));
                            modWord.setLemma(modWord.getLemma().substring(splitpos));
                        }
                    }

                    csent.addWord(modWord, id);

                } else if (f[1].equals("join")) {
                    // System.err.println("JOIN");
                    if (id >= csent.getWords().size()) {
                        return formatErrMsg("Cannot join last word «" + command + "»", currentSentenceId);
                    }
                    modWord = csent.getWords().get(id - 1);
                    csent.joinWords(id);
                } else if (f[1].equals("delete")) {
                    if (id >= csent.getWords().size()) {
                        return formatErrMsg("Cannot delete last word «" + command + "»", currentSentenceId);
                    }
                    modWord = csent.getWords().get(id - 1);
                    csent.deleteWord(id);
                }

                csent.setText(csent.getSentence());
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, modWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod sentsplit ")) {
                String[] f = command.trim().split(" +");

                if (f.length < 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                int id;
                try {
                    id = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }
                //System.err.println("<" + f[1] + ">");

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);
                // split current sentence at word id
                ConllSentence newsent = csent.splitSentence(id);
//                System.out.println(csent);
//                System.out.println(newsent);

                cfile.getSentences().add(currentSentenceId + 1, newsent);
                numberOfSentences++;
                newsent.increaseModificationCounter();
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, null, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod sentjoin")) {
                //String[] f = command.trim().split(" +");
                csent = cfile.getSentences().get(currentSentenceId);

//                if (history == null) {
//                    history = new History(200);
//                }
//                history.add(csent);
                if (currentSentenceId >= cfile.getSentences().size()) {
                    return formatErrMsg("No next sentence to join", currentSentenceId);
                }
                ConllSentence nextsent = cfile.getSentences().get(currentSentenceId + 1);
                csent.joinsentence(nextsent);
                csent.increaseModificationCounter();
                cfile.getSentences().remove(currentSentenceId + 1);
                numberOfSentences--;
                try {
                    writeBackup(currentSentenceId, null, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);
            } else if (command.startsWith("mod emptydelete ")) {
                // mod emptydelete id.subid

                String[] f = command.trim().split(" +");
                if (f.length != 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                String[] g = f[2].split("\\.");
                if (g.length != 2) {
                    return formatErrMsg("INVALID empty word id «" + command + "»", currentSentenceId);
                }

                int id;
                int subid;

                try {
                    id = Integer.parseInt(g[0]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }

                try {
                    subid = Integer.parseInt(g[1]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    ConllWord ew = csent.getEmptyWord(id, subid);
                    if (ew == null) {
                        return formatErrMsg("Empty word noes not exist «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID subid (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                csent.deleteEmptyWord(id, subid);
                csent.increaseModificationCounter();

                try {
                    writeBackup(currentSentenceId, null, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod insert ")
                    || command.startsWith("mod emptyinsert ")) {
                // add new word:
                // mod insert id form [lemma [upos [xpos]]]
                // or add new empty word
                // mod emptyinsert id form [lemma [upos [xpos]]]
                String[] f = command.trim().split(" +");
                if (f.length < 4) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                int id;
                try {
                    id = Integer.parseInt(f[2]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (id < 1 || id > csent.getWords().size()) {
                        return formatErrMsg("INVALID id «" + command + "»", currentSentenceId);
                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }
                //System.err.println("<" + f[1] + ">");

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);
                String form = f[3];

                ConllWord newword = new ConllWord(form, csent.getColumndefs());
                if (f.length > 4) {
                    newword.setLemma(f[4]);
                    if (f.length > 5) {
                        newword.setUpostag(f[5]);
                        if (f.length > 6) {
                            newword.setXpostag(f[6]);
                        }
                    }
                }
                if ("emptyinsert".equals(f[1])) {
                    newword.setId(id);
                    csent.addEmptyWord(newword);
                } else {
                    csent.addWord(newword, id);
                }

                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, newword, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);
            } else if (command.startsWith("mod editmetadata ")) {
                String[] f = command.trim().split(" +", 3);

                if (f.length < 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                csent = cfile.getSentences().get(currentSentenceId);
                history.add(csent);
                ConllWord modWord = csent.getHead();

                JsonElement jelement = JsonParser.parseString(f[2]);
                JsonObject jo = jelement.getAsJsonObject();
                if (jo.has("sent_id")) {
                    String sentid = jo.get("sent_id").getAsString().trim();
                    csent.setSentid(sentid);
                }
                if (jo.has("newdoc")) {
                    String newdoc = jo.get("newdoc").getAsString().trim();
                    //if (!newdoc.isEmpty()) {
                    csent.setNewdoc(newdoc);
                    //}
                }
                if (jo.has("newpar")) {
                    String newdoc = jo.get("newpar").getAsString().trim();
                    //if (!newdoc.isEmpty()) {
                    csent.setNewpar(newdoc);
                    //}
                }
                if (jo.has("text")) {
                    String text = jo.get("text").getAsString().trim();
                    //if (!newdoc.isEmpty()) {
                    csent.setText(text);
                    //}
                }
                if (jo.has("translit")) {
                    String newdoc = jo.get("translit").getAsString().trim();
                    //if (!newdoc.isEmpty()) {
                    csent.setTranslit(newdoc);
                    //}
                }
                if (jo.has("translations")) {
                    String t = jo.get("translations").getAsString().trim();
                    boolean rtc = csent.setTranslations(t);
                    if (!rtc) {
                        return formatErrMsg("Bad format in translations box: ''" + t, currentSentenceId);
                    }
                }
                csent.increaseModificationCounter();

                try {
                    writeBackup(currentSentenceId, modWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod checkdeprel ")) {
                String[] f = command.trim().split(" +", 4);
                if (f.length != 4) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }
                csent = cfile.getSentences().get(currentSentenceId);

                ConllWord cword = csent.getWord(f[2]);
                if (cword == null) {
                     return formatErrMsg("INVALID word id «" + command + "»", currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                if (f[3].equals("false")) {
                    //csent.removeHighlightDeprel(f[2]);
                    cword.setCheckDeprel(false);
                } else {
                    //csent.addHighlightDeprel(f[2]);
                    cword.setCheckDeprel(true);
                }

                try {
                    writeBackup(currentSentenceId, cword, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);
            } else if (command.startsWith("mod checktoken ")) {
                String[] f = command.trim().split(" +", 4);
                if (f.length != 4) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                csent = cfile.getSentences().get(currentSentenceId);
                ConllWord cword = csent.getWord(f[2]);
                if (cword == null) {
                     return formatErrMsg("INVALID word id «" + command + "»", currentSentenceId);
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);


                if (f[3].equals("false")) {
                    //csent.removeHighlightToken(f[2]);
                    cword.setCheckToken(false);
                } else {
                    //csent.addHighlightToken(f[2]);
                    cword.setCheckToken(true);
                }

                try {
                    writeBackup(currentSentenceId, null, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod comments ")) {
                String[] f = command.trim().split(" +", 3);
                String newcomment;
                if (f.length < 3) {
                    //return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                    newcomment = ""; // = delete comment
                } else {
                    newcomment = f[2];
                }

                if (history == null) {
                    history = new History(200);
                }
                csent = cfile.getSentences().get(currentSentenceId);
                history.add(csent);

                ConllWord modWord = csent.getHead();
                csent.setComments(newcomment);
                csent.increaseModificationCounter();

                try {
                    writeBackup(currentSentenceId, modWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }
                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod undo")) {
                if (history != null) {
                    csent = cfile.getSentences().get(currentSentenceId);
                    ConllSentence cs = history.undo(csent);
                    if (cs != null) {
                        cfile.getSentences().set(currentSentenceId, cs);
                        csent = cfile.getSentences().get(currentSentenceId);
                        writeBackup(currentSentenceId, null, "undo");
                        return returnTree(currentSentenceId, csent);
                    }
                }
                return formatErrMsg("No more undo possible", currentSentenceId);

            } else if (command.startsWith("mod redo")) {
                if (history != null) {
                    ConllSentence cs = history.redo();
                    if (cs != null) {
                        cfile.getSentences().set(currentSentenceId, cs);
                        csent = cfile.getSentences().get(currentSentenceId);
                        csent.increaseModificationCounter();
                        writeBackup(currentSentenceId, null, "redo");
                        return returnTree(currentSentenceId, csent);
                    }
                }
                return formatErrMsg("No more redo possible", currentSentenceId);

            } else if (command.startsWith("mod ed ")) {
                // enhanced deps
                // we expect
                //        "mod ed add <dep> <head> nsubj"
                //        "mod ed del <dep> <head>"
                String[] f = command.trim().split(" +");

                if (f.length < 5) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                if (f[2].equals("add") && f.length < 6) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);
                }

                csent = cfile.getSentences().get(currentSentenceId);
                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);

                ConllWord dep = csent.getWord(f[3]);
                if (dep == null) {
                    formatErrMsg("INVALID dep id «" + command + "»", currentSentenceId);
                }
                ConllWord head = csent.getWord(f[4]);
                if (head == null) {
                    formatErrMsg("INVALID head id «" + command + "»", currentSentenceId);
                }

                if ("add".equals(f[2])) {
                    // before we add a new enhanced dep, we delete a potentially
                    // existing enhn.deprel to the same head
                    /*boolean rtc = */
                    dep.delDeps(head.getFullId());
                    dep.addDeps(head.getFullId(), f[5]);
                    csent.setHasEnhancedDeps(true);
                } else if ("del".equals(f[2])) {
                    boolean rtc = dep.delDeps(f[4]);
                    if (!rtc) {
                        return formatErrMsg("ED does not exist «" + command + "»", currentSentenceId);
                    }
                } else {
                    return formatErrMsg("INVALID ed command «" + command + "»", currentSentenceId);
                }
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, dep, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("mod ")) {
                // we expect
                //    "mod id newheadid [newdeprel]", par ex "mod 3 6 nsubj"
                //if (mode != 0) {
                //    return formatErrMsg("NO editing in browse mode", currentSentenceId);
                //}
                // mod dep newhead [deprel]
                String[] f = command.trim().split(" +");

                if (f.length < 3) {
                    return formatErrMsg("INVALID command length «" + command + "»", currentSentenceId);

                }
                int dep_id;
                int newhead_id;

                if (f[1].contains(".") || f[2].contains(".")) {
                    return formatErrMsg("empty nodes cannot be head/dependant in basic dependencies «" + command + "»", currentSentenceId);
                }

                try {
                    dep_id = Integer.parseInt(f[1]);
                    csent = cfile.getSentences().get(currentSentenceId);
                    if (dep_id < 1 || dep_id > csent.getWords().size()) {
                        return formatErrMsg("INVALID dependant id «" + command + "»", currentSentenceId);
                    }

                    newhead_id = Integer.parseInt(f[2]);
                    if (newhead_id < 0 || newhead_id > csent.getWords().size()) {
                        return formatErrMsg("head id must be 0 < headid <= " + csent.getWords().size() + " «" + command + "»", currentSentenceId);

                    }
                } catch (NumberFormatException e) {
                    return formatErrMsg("INVALID id (not an integer) «" + command + "» " + e.getMessage(), currentSentenceId);
                }

                if (newhead_id == dep_id) {
                    return formatErrMsg("INVALID head id. Cannot be identical to id «" + command + "»", currentSentenceId);
                }

                String newdeprel = null;
                if (f.length == 4) {
                    newdeprel = f[3];
                }

                if (history == null) {
                    history = new History(200);
                }
                history.add(csent);
                ConllWord depword = csent.getWords().get(dep_id - 1);
                ConllWord oldhead = depword.getHeadWord();
                if (depword.getHead() == newhead_id) {
                    if (newdeprel == null) {
                        //return formatErrMsg("new head == old head");
                        return returnTree(currentSentenceId, csent);
                    } else if (newdeprel.equals(depword.getDeplabel())) {
                        return formatErrMsg("new head == old head, identical deprel", currentSentenceId);
                    }
                }

                ConllWord newhead = null;
                if (newhead_id > 0) {
                    newhead = csent.getWords().get(newhead_id - 1); // 0 : nouvelle tête de la phrase
                    // check whether newhead n'est pas qq part au-dessous du mot courant
                    if (depword.commands(newhead)) {
                        return formatErrMsg("cannot make " + newhead_id + " head of " + dep_id + " since " + newhead_id + " is currently a dependent of " + dep_id, currentSentenceId);
                    }
                }
                depword.setHeadWord(newhead);
                depword.setHead(newhead_id);

                if (oldhead != null) {
                    oldhead.getDependentsMap().remove(dep_id);
                }
                if (newhead != null) {
                    newhead.getDependentsMap().put(dep_id, depword);
                }
                if (newdeprel != null) {
                    depword.setDeplabel(newdeprel);
                }

                ConllWord modWord = depword;
                csent.increaseModificationCounter();
                try {
                    writeBackup(currentSentenceId, modWord, editinfo);
                } catch (IOException ex) {
                    return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                }

                return returnTree(currentSentenceId, csent);

            } else if (command.startsWith("save")) {
                if (mode != 0) {
                    return formatErrMsg("NO editing in browse mode", currentSentenceId);
                }
                //System.err.println("CHANGES " + changesSinceSave);
                if (changesSinceSave != 0) {
                    try {
                        changesSinceSave = 0; //saveafter;
                        String f = writeBackup(currentSentenceId, null, editinfo, true);
                        return formatSaveMsg("saved «" + f + "»", currentSentenceId);

                        //return returnTree(currentSentenceId, csent);
                    } catch (IOException ex) {
                        return formatErrMsg("Cannot save file: " + ex.getMessage(), currentSentenceId);
                    }

                } else {
                    return formatErrMsg("no changes to be saved", currentSentenceId);
                }
            } else {
                return formatErrMsg("invalid command «" + command + "»", currentSentenceId);
            }
        } catch (ConllException e) {
            //e.printStackTrace();
            return formatErrMsg("CoNLL-U error: " + e.getMessage(), currentSentenceId);
        } catch (PatternSyntaxException e) {
            //e.printStackTrace();
            return formatErrMsg("Bad regular expression: " + e.getMessage(), currentSentenceId);
        } catch (Exception e) {
            e.printStackTrace();
            return formatErrMsg("General error: " + e.getMessage(), currentSentenceId);
        }
    }

    /**
     * only needed for test, to avoid committing the test file
     */
    public void setCallgitcommit(boolean b) {
        callgitcommit = b;
    }

    public void setBacksuffix(String s) {
        suffix = s;
    }

    public void setOutfilename(File f) {
        outfilename = f;
    }

    /**
     * check whether the directory of the edited file is under git version
     * control, and if so whether the edited file is under git control, or not
     *
     * @return 0, error, 1 if the file is under Git Version control, 2 if the
     * directory is git controlled but the file is not, else 3
     */
    // TODO merge with writeBackup()
    private synchronized int versionning() throws IOException {
        File dir = filename.getParentFile().toPath().normalize().toFile();

        try {
            //Git git = Git.open(dir);
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repositoryBuilder.findGitDir(dir);
            File gitdir = repositoryBuilder.getGitDir();
            //System.err.println("EEEEEE " + gitdir + " eeee " + dir );
            if (gitdir != null) {
                Git git = Git.open(gitdir);
                Status status = git.status().call();

                // calculer le nom du fichier par rapport du réperoitre .git
                Path gitdirbase = gitdir.getAbsoluteFile().getParentFile().toPath().normalize();
                //System.err.println("gitdirbase    " + gitdirbase);
                //System.err.println("filename      " + filename.toPath().normalize());
                Path filepathInGit = gitdirbase.relativize(filename.toPath().normalize());
                //System.err.println("IGNORED " + status.getIgnoredNotInIndex());
                //System.err.println("filenameInGit " + filepathInGit);
                Set<String> untracked = status.getUntracked();
                //System.err.println("UNTRACKED " + untracked);
//                boolean ignore = false;
//                for (String pattern : status.getIgnoredNotInIndex()) {
//
//                }
                if (!callgitcommit || untracked.contains(filepathInGit.toString())) {
                    // untracked file in git controlled directory
                    System.err.println("Git dir untracked");
                    return 2;
                } else {
                    // file controlled by git
                    System.err.println("Git OK");
                    return 1;
                }
            } else {
                // no git in view
                System.err.println("any");
                return 3;
            }
        } catch (GitAPIException ex) {
            System.err.println("GIT ERROR: " + ex.getMessage());
            return 0;

        }
    }

    private synchronized String writeBackup(int currentSentenceId, ConllWord modWord, String editinfo) throws IOException {
        return writeBackup(currentSentenceId, modWord, editinfo, false);
    }

    private synchronized String writeBackup(int currentSentenceId, ConllWord modWord, String editinfo, boolean forcesave) throws IOException {
        if (!forcesave && (saveafter < 0 || changesSinceSave < saveafter)) {
            return null; // no need to save yet
        }
        if (outfilename == null) {
            outfilename = filename;
        }
        File dir = outfilename.getParentFile().toPath().normalize().toFile();
        if ((debug & 0x01) == 1) {
            System.err.println("Saving file " + outfilename);
        }

        try {
            //Git git = Git.open(dir);
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repositoryBuilder.findGitDir(dir);
            File gitdir = repositoryBuilder.getGitDir();
            //System.err.println("EEEEEE " + gitdir + " eeee " + dir );
            if (gitdir != null) {
                Git git = Git.open(gitdir);
                Status status = git.status().call();

                // calculer le nom du fichier par rapport du réperoitre .git
                Path gitdirbase = gitdir.getAbsoluteFile().getParentFile().toPath().normalize();
                //System.err.println("gitdirbase    " + gitdirbase);
                //System.err.println("filename      " + filename.toPath().normalize());
                Path filepathInGit = gitdirbase.relativize(outfilename.toPath().normalize());
                //System.err.println("IGNORED " + status.getIgnoredNotInIndex());
                //System.err.println("filenameInGit " + filepathInGit);
                Set<String> untracked = status.getUntracked();
                //System.err.println("UNTRACKED " + untracked);
//                boolean ignore = false;
//                for (String pattern : status.getIgnoredNotInIndex()) {
//
//                }
                if (!callgitcommit || untracked.contains(filepathInGit.toString())) {
                    String backUpFilename = outfilename + suffix;
                    //System.err.println("Write ddddd " );
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(backUpFilename), StandardCharsets.UTF_8));
                    bw.write(cfile.toString());
                    bw.close();
                    System.err.printf("File '%s' not tracked by git\n", backUpFilename);
                    changesSinceSave = 0;
                    return backUpFilename;
                    //} else if (false) {
                    // git.tag().setName(tagname).setMessage(tagmessage).call();
                    //System.err.format("Tag '%s': '%s' set.", tagname, tagmessage);
                    //return tagname;
                } else {
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outfilename), StandardCharsets.UTF_8));
                    bw.write(cfile.toString());
                    bw.close();
                    changesSinceSave = 0;
                    git.add().addFilepattern(filepathInGit.toString()).call();
                    if (modWord == null) {
                        git.commit().setMessage(String.format("saving %s sentence: %d (%s)", outfilename, currentSentenceId + 1, editinfo)).call();
                    } else {
                        //String sentid = "";
                        //if ()
                        git.commit().setMessage(String.format("modification: %s sentence %d, word: %d (%s)", outfilename, currentSentenceId + 1, modWord.getId(), editinfo)).call();
                    }
                    System.err.printf("File '%s' committed\n", filepathInGit);
                    return outfilename.toString();

                }
            } else {
                String backUpFilename = outfilename + suffix;
                //System.err.println("Write ddddd " );
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(backUpFilename), StandardCharsets.UTF_8));
                bw.write(cfile.toString());
                bw.close();
                changesSinceSave = 0;
                System.err.printf("Directory '%s' is not a git repository\n", dir);
                return backUpFilename;
            }
        } catch (GitAPIException ex) {
            System.err.println("GIT ERROR: " + ex.getMessage());
            // } catch (Exception ex) {
            //    ex.printStackTrace();
            //System.err.printf("Directory '%s' is not a git repository\n", dir);
        }
        return null;
    }

    public static void main(String[] args) {
        Options options = new Options();
        Option upos = Option.builder("U").longOpt("UPOS")
                .argName("files")
                .hasArg()
                .desc("comma separated list of files with valid UPOS (if filenames ends in .json, a format as in data/upos.json is expected (https://github.com/UniversalDependencies/tools.git)")
                .build();
        options.addOption(upos);
        Option xpos = Option.builder("x").longOpt("XPOS")
                .argName("files")
                .hasArg()
                .desc("comma separated list of files with valid XPOS")
                .build();
        options.addOption(xpos);
        Option deprels = Option.builder("d").longOpt("deprels")
                .argName("files")
                .hasArg()
                .desc("comma separated list of files with valid deprels or data/deprels.json from https://github.com/UniversalDependencies/tools.git (the latter requires --language)")
                .build();
        options.addOption(deprels);
        Option features = Option.builder("f").longOpt("features")
                .argName("files")
                .hasArg()
                .desc("comma separated list of files with valid [upos:]feature=value pairs or data/feats.json from https://github.com/UniversalDependencies/tools.git (the latter requires --language)")
                .build();
        options.addOption(features);

        Option language = Option.builder("l").longOpt("language")
                .argName("file")
                .hasArg()
                .desc("language (needed to read data/deprels.json or data/feats.json)")
                .build();
        options.addOption(language);

        Option validator = Option.builder("v").longOpt("validator")
                .argName("LANG")
                .hasArg()
                .desc("file with validator configuration")
                .build();
        options.addOption(validator);

        Option version = Option.builder("V").longOpt("version")
                .desc("show version and exit")
                .build();
        options.addOption(version);

        Option help = Option.builder("h").longOpt("help")
                .desc("show this help and exit")
                .build();
        options.addOption(help);

        Option rootdir = Option.builder("r").longOpt("rootdir")
                .argName("dir")
                .hasArg()
                .desc("root of fileserver (must include index.html and edit.js etc. for ConlluEditor). Default: gui/")
                .build();
        options.addOption(rootdir);

        Option shortcuts = Option.builder("s").longOpt("shortcuts")
                .argName("file")
                .hasArg()
                .desc("list of shortcut definition (json)")
                .build();
        options.addOption(shortcuts);

        Option saveAfter = Option.builder("S").longOpt("saveAfter")
                .argName("int")
                .hasArg()
                .desc("saves edited file after n changes (default save (commit) when going to another sentence")
                .build();
        options.addOption(saveAfter);

        Option shortcuttimeout = Option.builder("T").longOpt("shortcutTimeout")
                .argName("milliseconds")
                .hasArg()
                .desc("sets the maximal time the GUI waits before it accepts the shortcut")
                .build();
        options.addOption(shortcuttimeout);

        Option verbosity = Option.builder().longOpt("verb")
                .argName("hex")
                .hasArg()
                .desc("specifiy verbosity (hexnumber, interpreted as bitmap)")
                .build();
        options.addOption(verbosity);

        Option noedit = Option.builder().longOpt("noedit")
                .desc("inhibit editing, only display sentences")
                .build();
        options.addOption(noedit);

        Option relax = Option.builder().longOpt("relax")
                .desc("correct some formal errors in CoNLL-U more or less silently")
                .build();
        options.addOption(relax);

        Option reinit = Option.builder().longOpt("reinit")
                .desc("only browsing, reload file after each sentence (to read changes if the file is changed by other means)")
                .build();
        options.addOption(reinit);

        Option overwrite = Option.builder().longOpt("overwrite")
                .desc("force overwriting of existing file (unless git-controlled)")
                .build();
        options.addOption(overwrite);

        Option include_unused = Option.builder().longOpt("include_unused")
                .desc("include unused features from data/feats.json")
                .build();
        options.addOption(include_unused);

        Option compare = Option.builder("c").longOpt("compare")
                .argName("file")
                .hasArg()
                .desc("comparison mode: display a second (gold) tree in gray behind the current tree to see differences")
                .build();
        options.addOption(compare);

        Option uiconfig = Option.builder("u").longOpt("uiconfig")
                .argName("file")
                .hasArg()
                .desc("UI configuration file (to choose default views and hide unneeded buttons")
                .build();
        options.addOption(uiconfig);

        CommandLineParser parser = new DefaultParser();

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);
            if (line.hasOption(help)) {
                throw new ParseException(""); // prints help
            }
            if (line.hasOption(version)) {
                System.err.println(getVersion());
                System.exit(0);
            }

            if (line.getArgList().size() != 2) {
                throw new ParseException("missing CoNLL-U-filename and/or port number");
            }

            ConlluEditor ce = new ConlluEditor(line.getArgList().get(0), line.hasOption(overwrite));

            if (line.hasOption(uiconfig)) {
                ce.setUIConfig(line.getOptionValue(uiconfig));
            }
            if (line.hasOption(upos)) {
                ce.setValidUPOS(Arrays.asList(line.getOptionValue(upos).split(",")));
            }
            if (line.hasOption(xpos)) {
                ce.setValidXPOS(Arrays.asList(line.getOptionValue(xpos).split(",")));
            }
            if (line.hasOption(deprels)) {
                ce.setValidDeprels(Arrays.asList(line.getOptionValue(deprels).split(",")), line.getOptionValue(language));
            }
            if (line.hasOption(features)) {
                ce.setValidFeatures(Arrays.asList(line.getOptionValue(features).split(",")), line.getOptionValue(language), line.hasOption(include_unused));
            }

            if (line.hasOption(validator)) {
                ce.setValidator(line.getOptionValue(validator));
            }

            if (line.hasOption(shortcuts)) {
                ce.setShortcuts(line.getOptionValue(shortcuts));
            }

            String savea = line.getOptionValue(saveAfter);

            if (savea != null) {
                if (Integer.parseInt(savea) > 0) {
                    ce.setSaveafter(Integer.parseInt(savea));
                } else {
                    System.err.println("Invalid value for option --saveAfter. Must be positive integer");
                }
            }

            String scto = line.getOptionValue(shortcuttimeout);
            if (scto != null) {
                if (Integer.parseInt(scto) > 0) {
                    ce.setShortcutTimeout(Integer.parseInt(scto));
                } else {
                    System.err.println("Invalid value for option --shortcutTimeout. Must be positive integer");
                }
            }

            int debug = 0x0d;
            if (line.hasOption(verbosity)) {
                debug = Integer.parseInt(line.getOptionValue(verbosity), 16);
            }
            ce.setDebug(debug);

            if (line.hasOption(compare)) {
                ce.setComparisonFile(line.getOptionValue(compare));
            }

            int mode = 0; // noedit: 1, reinit: 2
            if (line.hasOption(noedit)) {
                mode = 1;
            }
            if (line.hasOption(reinit)) {
                mode = 2;
            }
            if (mode > 0) {
                ce.setMode(mode);
            }

            int port = Integer.parseInt(line.getArgList().get(1));
            // ServeurHTTP sh =
            new ServeurHTTP(port, ce, line.getOptionValue(rootdir), debug, false);
        } catch (ParseException e) {
            // oops, something went wrong
            //System.err.println("Command line error: " + exp.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(90);
            formatter.printHelp("ConlluEditor [options] CoNLL-U-file port", e.getMessage(), options, null);
            System.exit(1);
        } catch (ConllException | IOException ex) {
            System.err.println("*** Error: " + ex.getMessage());
            System.exit(11);
        }
    }
}
