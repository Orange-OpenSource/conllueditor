/* This library is under the 3-Clause BSD License

Copyright (c) 2018, Orange S.A.

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
 @version 1.14.8 as of 28th November 2019
 */
package com.orange.labs.editor;

import com.orange.labs.conllparser.ConllSentence;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Validation wrapper, to call an (external) validation script with one CoNLL-U sentence
 * @author Johannes Heinece <johannes dot heinecke at orange point com>
 */
public class Validator {
    String validationcommand;
    boolean readStdout = true;
    boolean readStderr = true;

    public Validator(String conffile) {
        try {
            FileInputStream fis = new FileInputStream(new File(conffile));
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.charAt(0) == '#') continue;
                String [] elems = line.split(":", 2);
                if (elems.length == 2) {
                    if (elems[0].equals("script")) {
                        if(elems[1].contains("{FILE}")) {
                            validationcommand = elems[1].trim();
                        } else {
                            System.err.format("Validator ERROR: \"script:\" value does not contain \"{FILE}\"\n");
                        }
                    }
//                        else if (elems[0].equals("stdout")) {
//                            if (elems[1].equals("true") || elems[1].equals("yes") ) {
//                            } else {
//                                readStdout = false;
//                            }
//                        }
//                        else if (elems[0].equals("stderr")) {
//                            if (elems[1].equals("true") || elems[1].equals("yes") ) {
//                            } else {
//                                readStderr = false;
//                            }
//                        }
                } else {
                    System.err.format("Validator ERROR: invalid format <%s>\n", line);
                }
            }
            br.close();
        } catch (IOException ex) {
            System.err.format("Validator ERROR: Cannot load validation configuration: %s: %s\n", conffile, ex.getMessage());
        }
        System.err.println("Validation command: " + validationcommand);
    }

    public String validate(ConllSentence cs) throws IOException, InterruptedException {
        if (validationcommand == null) return "Validator ERROR: bad initialisation";
        File f = File.createTempFile("ce_", ".conllu");
        FileOutputStream fos = new FileOutputStream(f);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
        bw.append(cs.toString());
        bw.close();

        // TODO: add timeout
        ProcessBuilder pb = new ProcessBuilder(validationcommand.replace("{FILE}", f.getAbsolutePath()).split("\\s+"))
                .redirectErrorStream(true);
        Process validationprocess = pb.start();


        StringBuilder sb = new StringBuilder();
        String line = null;

        //BufferedReader br =  new BufferedReader(new InputStreamReader(val.getErrorStream()));
        BufferedReader br =  new BufferedReader(new InputStreamReader(validationprocess.getInputStream()));
        while ( (line = br.readLine()) != null) {
            sb.append(line);
            sb.append(System.getProperty("line.separator"));
        }


//        System.err.println("zzzzzz");
//        boolean exitednormally = validationprocess.waitFor(10, TimeUnit.SECONDS);  // let the process run for 5 seconds
//        validationprocess.destroy();                     // tell the process to stop
//        boolean killed = validationprocess.waitFor(10, TimeUnit.SECONDS); // give it a chance to stop
//        validationprocess.destroyForcibly();             // tell the OS to kill the process
//        validationprocess.waitFor();
//
//        if (killed) sb.append("Validation process killed after timeout of 20 seconds");
//        else if (!exitednormally) sb.append("Validation process terminated after timeout of 10 seconds");
//
        f.delete();
        return sb.toString();
    }
}
