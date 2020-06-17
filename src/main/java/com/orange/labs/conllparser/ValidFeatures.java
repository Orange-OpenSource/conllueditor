/* This library is under the 3-Clause BSD License

Copyright (c) 2020, Orange S.A.

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
 @version 2.5.0 as of 23rd May 2020
 */
package com.orange.labs.conllparser;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a file which defines all valid features and optionally gives for each UPOS the list of valid features names.
 * Format:
 * Feature1=Val1
 * Feature1=Val2
 * ...
 * U:NOUN Feature1 Feature2
 * ...
 * X:NN Feature1 Feature2
 */

public class ValidFeatures {

    Map<String, Set<String>> validFeatures; // fname: [values]
    Map<String, Set<String>> uposFnames = null; // upos: [fnames] // valid featurenames for a given upos
    Map<String, Set<String>> xposFnames = null; // xpos: [fnames] // valid featurenames for a given xpos

    public ValidFeatures(List<String> filenames) throws IOException {
        validFeatures = new HashMap<>();
        xposFnames = new HashMap<>();
        for (String fn : filenames) {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fn), StandardCharsets.UTF_8));

            String line;
            //int ct = 0;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.startsWith("U:")) {
                        if (uposFnames == null)  uposFnames = new HashMap<>();
                        // list of feature names available for a given UPOS. Format U:UPOS Fname
                        String[] elems = line.substring(2).split("[ \t]+");
                        Set<String> tmp = new HashSet<>(Arrays.asList(elems));
                        uposFnames.put(elems[0], tmp);
                        tmp.remove(elems[0]); // upos
                    } else if (line.startsWith("X:")) {
                        if (xposFnames == null)  xposFnames = new HashMap<>();
                        // list of feature names available for a given UPOS. Format U:UPOS Fname
                        String[] elems = line.substring(2).split("[ \t]+");
                        Set<String> tmp = new HashSet<>(Arrays.asList(elems));
                        xposFnames.put(elems[0], tmp);
                        tmp.remove(elems[0]); // xpos
                    } else {
                        String[] elems = line.split("=", 2);
                        Set<String> vals = validFeatures.get(elems[0]);
                        if (vals == null) {
                            vals = new HashSet<>();
                            validFeatures.put(elems[0], vals);
                        }
                        vals.add(elems[1]);
                    }
                }
            }
            br.close();
        }
        System.err.format("%d valid Features read from %s\n", validFeatures.size(), filenames.toString());
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

        if (uposFnames != null && upos != null) {
            Set<String> fnames = uposFnames.get(upos);
            if (fnames != null && !fnames.contains(fname)) return 1;
        }

        if (xposFnames != null && xpos != null) {
            Set<String> fnames = xposFnames.get(xpos);
            if (fnames != null && !fnames.contains(fname)) return 1;
        }

        return 0;
    }

    public List<String>getList() {
        List<String>fl = new ArrayList<>();
        for (String fname : validFeatures.keySet()) {
            for (String val : validFeatures.get(fname)) {
                fl.add(String.format("%s=%s", fname,val));
            }
        }
        Collections.sort(fl);
        return fl;
    }
}
