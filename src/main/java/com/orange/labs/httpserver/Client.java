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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Client to test a file of sentences against the RE server
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class Client {
    String server; // server:port

    public Client(String server) {
        this.server = server;
    }

    private HttpURLConnection httpget(String str) throws MalformedURLException, IOException {
        URL url = new URL("http://" + server + "/" + str);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("GET");
        return connection;
    }

    public void lineByLine(String filename, int debugcode, String encoding) {
        try {
            String olddebugCode = "";
            HttpURLConnection connection = httpget(String.format("updateDebug/%x", debugcode));
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.err.println("Init: HTTP ERROR " + connection.getResponseCode());
                return;
            } else {
                // read original debugcode
                InputStream isd = connection.getInputStream();
                BufferedReader brd = new BufferedReader(new InputStreamReader(isd, StandardCharsets.UTF_8));
                String dline;

                while ((dline = brd.readLine()) != null) {
                    //System.out.println("zzzz " + dline);

                    if (dline.startsWith("debug updated from")) {
                        // until version 4.00: "debug updated from ff to 801"
                        olddebugCode = dline.split(" ")[3];
                    }
                }               
            }

            System.out.printf("Debug: %x\n", debugcode);
            Charset cs = StandardCharsets.UTF_8;
            if (encoding != null
                && (encoding.equalsIgnoreCase("iso8859-1")
                    || encoding.equalsIgnoreCase("latin1"))) {
                cs = StandardCharsets.ISO_8859_1;
            }
            FileInputStream fis = new FileInputStream(new File(filename));
            BufferedReader brf = new BufferedReader(new InputStreamReader(fis, cs));
            String line;

            int ct = 0;
            while ((line = brf.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                ct++;
                String message = URLEncoder.encode(line, "UTF8");
                connection = httpget(String.format("getlog?id=%d&txt=%s", ct, message));

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String rline;

                    while ((rline = br.readLine()) != null) {
                        System.out.println(rline);
                    }
                    System.out.println();

                } else {
                    // Server returned HTTP error code.
                    System.err.println("HTTP ERROR " + connection.getResponseCode());
                    break;
                }
                connection.disconnect();
            }
            brf.close();

            // reset to original debugcode
            if (!olddebugCode.isEmpty()) {
                connection = httpget(String.format("updateDebug/%s", olddebugCode));
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    System.err.println("End: HTTP ERROR " + connection.getResponseCode() + " cannot reset to old debugcode");
                }
            } else {
                System.err.println("No original debugcode to reset to!");
            }

        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }

    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: Client server:port debugcode testfile [latin1]");
        } else {
            Client cl = new Client(args[0]);
            String enc = "utf8";
            int debugcode = Integer.parseInt(args[1], 16);
            if (args.length > 3) {
                enc = args[3];
            }
            cl.lineByLine(args[2], debugcode, enc);
        }
    }
}
