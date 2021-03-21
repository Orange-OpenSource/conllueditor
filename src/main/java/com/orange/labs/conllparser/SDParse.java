/* This library is under the 3-Clause BSD License

Copyright (c) 2021, Orange S.A.

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
 @version 2.11.0 as of 20th May 2021
 */
package com.orange.labs.conllparser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 Read various formats of SD-Parse format. For instance the sentence
  "the mouse sleeps" can be reprensented in the following way
    #sdparse
    the mouse sleeps
    nsubj(sleeps, mouse)
    det(mouse, the)
  
  ambiguous tokens can have a "-position" suffixed
    # sdparse
    I can can the can .
    nsubj(can-3, I)
    aux(can-3, can-2)
    det(can-5,the)
    obj(can-3,can-5)
    punct(can-3,.)
    
  This class also read POS tags
    # sdparse
    POS/NNP tags/NNS can/MD be/VB attached/VBN to/TO ( any part of ) the/DT sentence/NN text/NN ./.
    dep(tags-2, POS-1)
    nsubjpass(attached-5, tags-2)
    aux(attached-5, can-3)
    auxpass(attached-5, be-4)
    prep(attached-5, to-6)
    det(text-14, the-12)
    nn(text-14, sentence-13)
    pobj(to-6, text-14)
    det(part, any)
    prep(part, of)
  
 * @author johannes.heinecke@orange.com
 */
public class SDParse {
    private final boolean debug = true;
    private ConllSentence sent;
    
    public SDParse(String contents) throws IOException, ConllException {
        InputStream is = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        parse(is);
    }
    
    public SDParse(File infile) throws IOException, ConllException {
        FileInputStream fis = new FileInputStream(infile);
        parse(fis); 
    }
    
    public SDParse(InputStream instream) throws IOException, ConllException {
        parse(instream);
    }

    private void parse(InputStream instream) throws IOException, ConllException {
     BufferedReader br = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));

        String line;
        int ct = 0;

        StringBuilder sb = new StringBuilder();
        
        Pattern depre = Pattern.compile("([a-z:_]+)\\s*\\(\\s*([\\S]+?)(-(\\d+))?\\s*,\\s*([\\S]+?)(-(\\d+))?\\s*\\)");
        //Pattern formpos = Pattern.compile("(\\S+)-(\\d+)");
        String sentence = null;
        List<String> words = null;
        Map<String, Integer>ids = new HashMap<>(); // word-pos: id
        Map<String, Integer>ids2 = new HashMap<>(); // word: id
        Map<Integer, Word>positions = new HashMap<>(); // pos (1, ...): form
        Set<String>heads = new HashSet<>(); // all words which are head
        Map<String, String>deps = new HashMap<>(); // all words which are dep and their head
        Map<String, DepRel> deprels = new HashMap<>(); // dep: deprel
        while ((line = br.readLine()) != null) {
            line = line.trim();
            System.out.println("lll " + line);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (sentence == null) {
                sentence = line;
                
                words = Arrays.asList(sentence.split("\\s+"));
                int i = 1;
                for (String word : words) {
                    Word wordc = new Word(word, i);
                    ids.put(wordc.longform, i);
                    ids2.put(wordc.form, i);
                    positions.put(i, wordc);
                    i++;
                }
                if (words.isEmpty()) {
                    throw new ConllException("words in sentence line «" + line + "»");
                }
                
                continue;
            }
            
            if (debug) {
                System.out.println("WORDS   " + words);
                System.out.println("IDS     " + ids);
            }
            
            Matcher m = depre.matcher(line);            
            if (!m.matches()) {
                throw new ConllException("invalid sd-parse line «" + line + "»");
            }
            String deprel = m.group(1);
            String head = m.group(2);
            int headpos;
            if (m.group(4) != null) {
                 headpos = Integer.parseInt(m.group(4));
            } else {
                // no position given: it's the first occurance in the sentence
                if (!ids2.containsKey(head)) {
                   throw new ConllException("head «"+ head +"» not in sentence «" + sentence + "»"); 
                }
                headpos = ids2.get(head);
            }
            String headAndPos = head + "-" + headpos;
            String dep = m.group(5);
           
            if (! ids.containsKey(headAndPos)) {
                throw new ConllException("head «"+ headAndPos +"» not in sentence «" + sentence + "»");
            }

            int deppos;
            if (m.group(7) != null) {
                deppos = Integer.parseInt(m.group(7));
            } else {
                 // no position given: it's the first occurance in the sentence
                if (!ids2.containsKey(dep)) {
                   throw new ConllException("dep «"+ dep +"» not in sentence «" + sentence + "»"); 
                }
                deppos = ids2.get(dep);
            }
            String depAndPos = dep + "-" + deppos;
            if (! ids.containsKey(depAndPos)) {
                throw new ConllException("dep «"+ depAndPos +"» not in sentence «" + sentence + "»");
            }
            
            if (debug) {
                System.out.println("LINE " + line + ", " + m.groupCount());       
                for (int i=0; i<=m.groupCount(); ++i) {
                    System.out.println("  " + i + " " + m.group(i));
                }
            }
            if (head.equals(depAndPos)) {
                throw new ConllException("dep must be different from head «" + line + "»");
            }
            heads.add(headAndPos);
            if (deps.containsKey(depAndPos)) {
                throw new ConllException("«" + depAndPos + "» has already a head «" + line + "»");
            }
            deps.put(depAndPos, headAndPos);
            deprels.put(depAndPos, new DepRel(deprel, headAndPos, depAndPos));
        }
        if (deprels.isEmpty()) {
            throw new ConllException("no relations in sd-parse");
        }

       if (debug) {
            System.out.println("HEADS   " + heads);
            System.out.println("DEPS    " + deps.keySet());
            System.out.println("DEPRELS " + deprels);
        }
        
        heads.removeAll(deps.keySet());
        
        if (debug) {
            System.out.println("HEAD    " + heads);
        }
        
        if (heads.size() != 1) {
        //    throw new ConllException("there must exactly one head in sd-parse");
        }
 
        
        List<ConllWord> cws = new ArrayList<>();
        String head = heads.iterator().next();
        for (Integer pos : positions.keySet()) {
        //for (String word : words) {
            Word word = positions.get(pos);
            String deprel;
            int headid;
            int id = ids.get(word.longform);
            //System.out.format("<%s><%s>\n", head, word.longform);
           // if (word.longform.equals(head)) {
            if (heads.contains(word.longform)) {
                deprel = "root";
                headid = 0;
                //System.out.println("is head " + word.longform);
            } else if (deps.containsKey(word.longform)) {
                //System.out.println("is dep " + word.longform);
                String headword = deps.get(word.longform);
                deprel = deprels.get(word.longform).deprel;
                headid = ids.get(headword);
            } else {
                deprel = "_";
                headid = 0;
            }
            //System.out.format("%d %s %d %s\n", id, word, headid, deprel);
            ConllWord cw = new ConllWord(word.form);
            cw.setId(id);
            cw.setDeplabel(deprel);
            cw.setHead(headid);
            if (word.upos != null) cw.setUpostag(word.upos);
            cws.add(cw);
        }
        
        sent = new ConllSentence(cws);

    }
    
    private void ooparse(InputStream instream) throws IOException, ConllException {
        BufferedReader br = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));

        String line;
        int ct = 0;

        StringBuilder sb = new StringBuilder();
        
        Pattern dep = Pattern.compile("([a-z:_]+)\\s*\\(\\s*([\\S]+)\\s*,\\s*([\\S]+)\\s*\\)");
        Pattern formpos = Pattern.compile("(\\S+)-(\\d+)");
        String sentence = null;
        List<String> words = null;
        Map<String, Integer>ids = new HashMap<>(); // word: id
        Map<Integer, String>positions = new HashMap<>(); // pos (1, ...): form
        Set<String>heads = new HashSet<>(); // all words which are head
        Map<String, String>deps = new HashMap<>(); // all words which are dep and their head
        Map<String, DepRel> deprels = new HashMap<>(); // dep: deprel
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (sentence == null) {
                // first non-comment is the sentence
                sentence = line;
                words = Arrays.asList(sentence.split("\\s+"));
                int i = 1;
                for (String word : words) {
                    ids.put(word, i);
                    positions.put(i++, word);
                }
                if (words.isEmpty()) {
                    throw new ConllException("words in sentence line «" + line + "»");
                }
                Matcher m = dep.matcher(line);
                if (m.matches()) {
                throw new ConllException("missing full sentence line");
            }
                continue;
            }
            Matcher m = dep.matcher(line);
            if (!m.matches()) {
                throw new ConllException("invalid sd-parse line «" + line + "»");
            }
            if (debug) {
                System.out.println("LINE " + line + ", " + m.groupCount());       
                for (int i=0; i<=m.groupCount(); ++i) {
                    System.out.println("  " + i + " " + m.group(i));
                }
            }
                if (m.group(2).equals(m.group(3))) {
                    throw new ConllException("dep must be different from head «" + line + "»");
                }
            
            heads.add(m.group(2));
            if (deps.containsKey(m.group(3))) {
                throw new ConllException("«" + m.group(3) + "» has already a head «" + line + "»");
            }
            deps.put(m.group(3), m.group(2));
            deprels.put(m.group(3), new DepRel(m.group(1), m.group(2), m.group(3)));
        }
        if (deprels.isEmpty()) {
            throw new ConllException("no relations in sd-parse");
        }
        
        
        if (debug) {
            System.out.println("HEADS   " + heads);
            System.out.println("DEPS    " + deps.keySet());
            System.out.println("DEPRELS " + deprels);
            System.out.println("WORDS   " + words);
        }
        
        heads.removeAll(deps.keySet());
        
        if (debug) {
            System.out.println("HEAD    " + heads);
        }
        
        if (heads.size() != 1) {
            throw new ConllException("there must exactly one head in sd-parse");
        }
 
        for (String form : deps.keySet()) {
            Matcher m = formpos.matcher(form);
            if (m.matches()) {
                if (!ids.containsKey(m.group(1))) {
                    throw new ConllException("Aform «"+form+"» not in sentence (first line)");
                }
                if (m.group(1).equals(positions.get(Integer.parseInt(m.group(2))))) {
                     throw new ConllException("Bform «"+form+"» not in sentence (first line)");
                }
            }
            if (!ids.containsKey(form)) {
                throw new ConllException("form «"+form+"» not in sentence (first line)");
            }
        }
        
        List<ConllWord> cws = new ArrayList<>();
        String head = heads.iterator().next();
        for (String word : words) {
            String deprel;
            int headid;
            int id = ids.get(word);
            System.out.format("<%s><%s>\n", head, word);
            if (word.equals(head)) {
                deprel = "root";
                headid = 0;
            } else {
                String headword = deps.get(word);
                deprel = deprels.get(word).deprel;
                headid = ids.get(headword);
            }
            //System.out.format("%d %s %d %s\n", id, word, headid, deprel);
            ConllWord cw = new ConllWord(word);
            cw.setId(id);
            cw.setDeplabel(deprel);
            cw.setHead(headid);
            cws.add(cw);
        }
        
        sent = new ConllSentence(cws);
        
    }
    
    
    public ConllSentence getSentence() {
        return sent;
    }
    
    
    /** word/UPOS */
    class Word {
        String form;
        String longform;
        String upos = null;
        
        public Word(String word, int pos) {
            String [] elems = word.split("/", 2);
            form = elems[0];
            if (elems.length > 1) upos = elems[1];
            longform = form + "-" + pos;
        }
        
         public String toString() {
             if (upos != null) return String.format("%s/%s", form, upos);
             else return form;
        }
    }
    
    /** deptel(head, dep) */
    class DepRel {
        String deprel;
        String head;
        String dep;
        public DepRel(String deprel, String head, String dep) {
            this.deprel = deprel;
            this.head = head;
            this.dep = dep;
        }
        
        public String toString() {
            return String.format("%s(%s, %s)", deprel, head, dep);
        }
    }
    
     public static void main(String args[]) throws ConllException, IOException {
         try {
            SDParse sdp = new SDParse(new File(args[0]));
             System.out.println(sdp.getSentence());
         } catch (ConllException e) {
             System.out.println("sdparse error: " + e.getMessage());
         } catch (IOException e) {
             System.out.println("IO error: " + e.getMessage());
         }
     }
}
