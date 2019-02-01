/** This library is under the 3-Clause BSD License

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
 @version 1.0 as of 5th November 2018
*/

package com.orange.labs.httpserver;

import com.sun.net.httpserver.Headers;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * very simple multipart parser
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class Multipart {
    private Map<String, String> fields; // key: value

    public Multipart(String boundary, BufferedReader br) throws IOException {
        fields = new HashMap<>();
        String line;
        //int status = 0; // wait for boundary 1: boundary found 2: contents
        boolean contents = false;
        String fieldname = null;
        StringBuilder fieldvalue = null;
        //System.err.println("B:" + boundary);

        while ((line = br.readLine()) != null) {
            //System.err.println("L:" + line);
            if (line.startsWith("--"+boundary)) {
                // nouvelle boundary, on peut stocker les info lues auparavant
                //System.err.println("AAA");
                if (fieldname != null) {
                    fields.put(fieldname, fieldvalue.toString());
                }
                fieldname = null;
                fieldvalue = new StringBuilder();
                contents = false;
            } else {
                // nous somme dans champs
                //System.err.println("BB " + contents);
                if (contents) {
                   // System.err.println("adding " + line);
                    fieldvalue.append(line).append('\n');
                }
                if (line.isEmpty()) {
                    contents = true;
                } else if (line.startsWith("Content-Disposition")) {
                    String fs[] = line.split(";");
                    for (String f : fs) {
                        String field = f.trim();
                        String e[] = field.split("name=");
                        if (e.length == 2) {
                            fieldname = e[1].replaceAll("\"", "");
                            break;
                        }
                    }
                    //String e[] = line.split(" name=");
                    //if (e.length == 2) {
                    //    fieldname = e[1].replaceAll("\"", "");
                    //}
                }
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String k : fields.keySet()) {
            sb.append(k).append('=').append(fields.get(k).trim()).append('\n');
        }
        return sb.toString().trim();
    }

    public Map<String, String>getFields() {
        return fields;
    }

    /** extract the boundary from the HTTP headers */
    public static String getBoundary(Headers headers) {
        String val = headers.getFirst("Content-type");
        if (val == null) {
            return null;
        }

        if (!val.startsWith("multipart/form-data")) return null;
        // multipart/form-data; boundary=---------------------------9479665629876452731671960449
        String e[] = val.split("boundary=");
        if (e.length != 2) {
            return null;
        }

        return e[1];
    }
}
