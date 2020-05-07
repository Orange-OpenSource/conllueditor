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
 @version 2.4.0 as of 4th May 2020
 */
package com.orange.labs.conllparser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a CoNLLU or CoNLL-U Plus file into another
 *
 */
public class ConlluPlusConverter {

    List<String> outputorder;
    List<String> inputorder;

    static String emptycol = "_";
    private String globalcolumns = "";

    public ConlluPlusConverter(String columndef) throws IOException {
        // get output column order        
        if (columndef == null) {
            outputorder = new ArrayList<>();
            outputorder.add("ID");
            outputorder.add("FORM");
            outputorder.add("LEMMA");
            outputorder.add("UPOS");
            outputorder.add("XPOS");
            outputorder.add("FEATS");
            outputorder.add("HEAD");
            outputorder.add("DEPREL");
            outputorder.add("DEPS");
            outputorder.add("MISC");
        } else {
            outputorder = Arrays.asList(columndef.split(","));
            StringBuilder sb = new StringBuilder();
            
            sb.append("# global.columns =");
            for (String col : outputorder) {
                sb.append(String.format(" %s", col));
            }
            sb.append('\n');
            globalcolumns = sb.toString();
        }
    }
  
    public String convert(String infile) throws IOException, ConllException {
        if (infile.equals("-")) {
            //br = new BufferedReader(new InputStreamReader(System.in));
            return convert(System.in);
        } else {
            FileInputStream fis = new FileInputStream(infile);
            return convert(fis);
            //br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        }
    }
  
    public String convert(InputStream instream) throws IOException, ConllException {
        BufferedReader br = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));
        
        String line;
        int ct = 0;

        StringBuilder sb = new StringBuilder();
        sb.append(globalcolumns);
        Map<String, String> lineholder = new HashMap<>(); // holds colums of current line
        while ((line = br.readLine()) != null) {
            ct++;

            if (ct == 1) {
                inputorder = new ArrayList<>();
                if (line.startsWith("# global.columns =")) {
                    String[] elems = line.substring(18).trim().split("[ \\t]+");
                    if (elems.length < 2) {
                        throw new ConllException("invalid conllu+ definition " + line);
                    }
                    for (String d : elems) {
                        //int pos = columndefs.size();
                        //if (columndefs.containsKey(d)) {
                        //  throw new ConllException("doubled column name in  conllu+ definition " + line);
                        //}
                        inputorder.add(d);
                    }
                    continue;
                } else {
                    // standard conllu order
                    inputorder.add("ID");
                    inputorder.add("FORM");
                    inputorder.add("LEMMA");
                    inputorder.add("UPOS");
                    inputorder.add("XPOS");
                    inputorder.add("FEATS");
                    inputorder.add("HEAD");
                    inputorder.add("DEPREL");
                    inputorder.add("DEPS");
                    inputorder.add("MISC");
                }

            }
            if (line.length() == 0 || line.startsWith("#")) {
                sb.append(line).append('\n');
            } else {
                // parse line according to input order
                String[] elems = line.split("\t");

                lineholder.clear();
                for (int i = 0; i < elems.length; ++i) {
                    lineholder.put(inputorder.get(i), elems[i]);
                }
                //System.err.println("ssssss " + elems.length + " " + lineholder);
                
                // output the current line in the new column format
                // if a column is not in the input, we put _
                boolean first = true;
                for (String coltype : outputorder) {
                    String value = lineholder.getOrDefault(coltype, emptycol);
                    if (!first) {
                        sb.append("\t");
                    }
                    first = false;
                    sb.append(value);
                }
                sb.append('\n');

            }
        }
        return sb.toString();

    }

    public static void main(String args[]) {
        if (args.length == 0) {
            System.err.println("outformat: comma separated CoNLL-U column names");
        } else {
            String coldefs = null;

            if (args.length == 2) {
                coldefs = args[1];
            }

            try {
                ConlluPlusConverter cpc = new ConlluPlusConverter(coldefs);
                System.out.print(cpc.convert(args[0]));
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("ERROR: " + e.getMessage());
            }
        }
    }
}
