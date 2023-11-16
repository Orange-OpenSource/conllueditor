/** This library is under the 3-Clause BSD License

Copyright (c) 2018-2023, Orange S.A.

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
package com.orange.labs.httpserver;

import com.orange.labs.editor.ConlluEditor;
import com.orange.labs.parserclient.ParserClient;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Server for CoNLLL-U file editing
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class ServeurHTTP {

    private int port;
    private ConlluEditor ce = null;
    private ParserClient pc = null;
    //private Date startdate;

    /* x1   show connections
       x2   show hostname of client (deactivate when no DNS is available, e.g. sometimes in Docker)
       x20  parameters EditHandler,
       x40  answer EditHandler
       x200 parameters File Handler,
       x400 answer FileHandler
     */
    private int debug = 1;

    protected enum Log {
        NONE, COUNT, FULL
    };

    private static String errortemplate = "<html>\n<head>\n"
            + "<title >%s</title>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <img alt=\"404 Not Found\" width=\"100%%\" src=\"img/%s.svg\">\n"
            + "</body>\n</html>\n";
    private static String e404 = String.format(errortemplate, "404 Not Found", "404");
    private static String e400 = String.format(errortemplate, "400 Bad Request", "400");
    private HttpServer server;

    /**
     * use server for CoNLL-U editing
     *
     * @param port port to use
     * @throws IOException
     */
    public ServeurHTTP(int port, /*ConlluEditor */ Object e, String rootdir, int debug) throws IOException {
        this.port = port;
        //this.ce = ce;
        this.debug = debug;

        server = HttpServer.create(new InetSocketAddress(this.port), 0);

        String indexhtml = null;
        String indexjs = null;
        if (e instanceof ConlluEditor) {
            ce = (ConlluEditor) e;
            server.createContext("/edit/", new EditHandler(ce));
            indexhtml = "index.html";
            indexjs = "edit.js";
        } else if (e instanceof ParserClient) {
            pc = (ParserClient) e;
            server.createContext("/parse/", new ParseHandler(pc));
            indexhtml = "parse.html";
            indexjs = "parse.js";
        }

        if (rootdir == null) {
            // if no rootdir (path to /gui) is given we assume the jar file is in /target, so we can
            // calculate the position of /gui from the postion of the jar file
            String s = new File(ServeurHTTP.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath()).getParentFile().getParent() + File.separator + "gui";
            s = URLDecoder.decode(s, "UTF-8");
            rootdir = s;
            System.err.println("calculated rootdir from .jar: " + s);
        } else {
            Path rdir = new File(rootdir).toPath();
            if (!Files.exists(rdir)) {
                throw new IOException(rootdir + " does not exist");
            }
            if (!Files.isDirectory(rdir)) {
                throw new IOException(rootdir + " is not a directory");
            }
            if (!Files.exists(new File(rootdir, indexhtml).toPath())) {
                throw new IOException(rootdir + " does not contain '" + indexhtml + "'");
            }
            if (!Files.exists(new File(rootdir, indexjs).toPath())) {
                throw new IOException(rootdir + " does not contain '" + indexjs + "'");
            }
        }

        server.createContext("/", new FileHandler(rootdir, indexhtml));
        server.setExecutor(null);

        String hostname = "localhost";

        try {
            InetAddress addr = InetAddress.getLocalHost();
            //if ((debug & 2) != 0) {
            hostname = addr.getHostName();
            //} else {
            //    hostname = addr.toString();
            //}
        } catch (UnknownHostException ex) {
            System.err.println("Hostname can not be resolved: " + ex.getMessage());
        }

        System.err.println("\nConlluEditor HTTP Server started at http://" + hostname + ":" + this.port + "/");
        if (rootdir == null) {
            System.err.println("Point your browser to http://" + hostname + "/conllueditor?port=" + this.port);
        }
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    /**
     * the handler for the CoNLL-U Editor
     */
    class EditHandler implements HttpHandler {

        private final ConlluEditor ce;

        public EditHandler(ConlluEditor ce) {
            this.ce = ce;
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            InetSocketAddress remoteAddress = he.getRemoteAddress();
            String client;
            if ((debug & 2) != 0) {
                client = remoteAddress.getHostName();
            } else {
                client = remoteAddress.toString();
            }
            if ((debug & 1) != 0) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Date date = new Date();
                System.out.println(dateFormat.format(date) + " " + he.getRequestMethod() + " EditHandler request from " + client);
            }

            boolean json = true;
            String response = null;

            URI requestedUri = he.getRequestURI();
            String path = requestedUri.getPath();
            System.err.println("METHOD " + he.getRequestMethod());
            System.err.println("PATH   " + path);
            int http_rtc = HttpURLConnection.HTTP_NO_CONTENT;

            // try {
            if (he.getRequestMethod().equalsIgnoreCase("POST")) {

                if (!"/edit/".equals(path)) {
                    http_rtc = HttpURLConnection.HTTP_BAD_REQUEST;
                    json = true;
                    response = "{ \"error\": \"bad POST path: '" + path + "'\" }";
                } else {
                    // REQUEST Headers
                    //Headers requestHeaders = he.getRequestHeaders();
                    //for (String key : requestHeaders.keySet())
                    //    System.err.println("HEADER " + key  + ":"  + requestHeaders.getFirst(key));
                    // REQUEST Body
                    InputStream is = he.getRequestBody();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

                    String command = null;
                    Integer sentid = null;

                    String boundary = Multipart.getBoundary(he.getRequestHeaders());
                    if (boundary != null) {
                        // multipart
                        Multipart contents = new Multipart(boundary, br);
                        if ((debug & 0x20) != 0) {
                            System.err.println("EditHandler: multiparts:\n" + contents);
                        }
                        command = contents.getFields().get("cmd").trim();
                        sentid = Integer.parseInt(contents.getFields().get("sentid").trim());

                    } else {
                        // post normal
                        StringBuilder contents = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            contents.append(line).append(" ");
                        }

                        if ((debug & 0x20) != 0) {
                            System.err.println("EditHandler: data: ");
                        }
                        String[] lines = contents.toString().split("&");
                        for (String l : lines) {
                            l = l.trim();

                            if ((debug & 0x20) != 0) {
                                System.err.println("  " + l);
                            }

                            //System.err.println("ddd " + l);
                            if (l.startsWith("cmd=")) {
                                command = URLDecoder.decode(l.substring(4), "UTF-8"); // couper cmd=
                                // break;
                            } else if (l.startsWith("sentid=")) {
                                String s = URLDecoder.decode(l.substring(7), "UTF-8"); // couper sentid=
                                sentid = Integer.parseInt(s);
                            }
                        }
                    }

                    if (command == null || sentid == null) {
                        http_rtc = HttpURLConnection.HTTP_BAD_REQUEST;
                        json = true;
                        response = "{ \"error\": \"parameters 'cmd' and 'sentid' are mandatory\" }";
                    }
                    if (command != null && !command.isEmpty()
                            && sentid != null) {
                        response = ce.process(command, sentid, client);
                        http_rtc = HttpURLConnection.HTTP_OK;
                        if (response == null) {
                            response = "";
                            http_rtc = HttpURLConnection.HTTP_NO_CONTENT;
                        }
                    }
                }
            } else if (he.getRequestMethod().equalsIgnoreCase("GET")) {
                //URI requestedUri = he.getRequestURI();
                //String path = requestedUri.getPath();
                json = false;

                String query = requestedUri.getQuery();
                System.err.println("QUERY " + query);
                if ((debug & 0x20) != 0) {
                    System.err.println("EditHandler <" + path + "> query <" + query + ">");
                }

                Integer sentid = null;
                if (query != null) {
                    Map<String, String> ma = queryToMap(query);
                    String s = ma.get("sentid");
                    String e = ma.get("all_enhanced");
                    boolean all_enhanced = false;
                    if ("true".equals(e)) {
                        all_enhanced = true;
                    }

                    if (s != null) {
                        sentid = Integer.parseInt(s);

                        if (path.equals("/edit/getvalidation")) {
                            response = ce.getraw(ConlluEditor.Raw.VALIDATION, sentid, false) + "\n";
                            http_rtc = HttpURLConnection.HTTP_OK;
                            json = true;
                        } else if (path.equals("/edit/getlatex")) {
                            response = ce.getraw(ConlluEditor.Raw.LATEX, sentid, all_enhanced) + "\n";
                            http_rtc = HttpURLConnection.HTTP_OK;
                            json = true;
                        } else if (path.equals("/edit/getconllu")) {
                            response = ce.getraw(ConlluEditor.Raw.CONLLU, sentid, false) + "\n";
                            http_rtc = HttpURLConnection.HTTP_OK;
                            json = true;
                        } else if (path.equals("/edit/getsdparse")) {
                            response = ce.getraw(ConlluEditor.Raw.SDPARSE, sentid, all_enhanced) + "\n";
                            http_rtc = HttpURLConnection.HTTP_OK;
                            json = true;
                        } else if (path.equals("/edit/getspacyjson")) {
                            response = ce.getraw(ConlluEditor.Raw.SPACY_JSON, sentid, all_enhanced) + "\n";
                            http_rtc = HttpURLConnection.HTTP_OK;
                            json = true;
                        } else {
                            http_rtc = HttpURLConnection.HTTP_BAD_REQUEST;
                            response = e400;
                        }
                    } else {
                            http_rtc = HttpURLConnection.HTTP_BAD_REQUEST;
                            json = true;
                            response = "{ \"error\": \"parameter 'sentid' is mandatory\" }";
                    }
                } else if (path.equals("/edit/info")) {
                    response = ce.getInfo() + "\n";
                    http_rtc = HttpURLConnection.HTTP_OK;
                } else if (path.equals("/edit/validlists")) {
                    response = ce.getValidlists() + "\n";
                    http_rtc = HttpURLConnection.HTTP_OK;
                    json = true;
                } else {
                    http_rtc = HttpURLConnection.HTTP_NOT_FOUND;
                    response = e404;
                }
            }

            if ((debug & 0x40) != 0) {
                System.err.format("EditHandler Response %d <%s>\n", http_rtc, response);
            }

            Headers hdrs = he.getResponseHeaders();
            if (json) {
                hdrs.set("Content-Type", "application/json" + ";charset=UTF-8");
            } else {
                hdrs.set("Content-Type", "text/plain" + ";charset=UTF-8");
            }
            hdrs.set("Access-Control-Allow-Origin", "*");
            //if (http_rtc == HttpURLConnection.HTTP_OK) {
            he.sendResponseHeaders(http_rtc, response.getBytes("utf-8").length);
            // }

            // RESPONSE Body
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes("utf-8"));
            //Close
            he.close();
        }
    }

    /**
     * Handler for the parser client
     */
    class ParseHandler implements HttpHandler {

        private final ParserClient cl;

        public ParseHandler(ParserClient cl) {
            this.cl = cl;
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            InetSocketAddress remoteAddress = he.getRemoteAddress();
            String client;
            if ((debug & 8) != 0) {
                client = remoteAddress.getHostName();
            } else {
                client = remoteAddress.toString();
            }
            if ((debug & 4) != 0) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Date date = new Date();
                System.out.println(dateFormat.format(date) + " " + he.getRequestMethod() + " ParseHandler request from " + client);
            }

            boolean json = true;
            String response = null;
            int http_rtc = HttpURLConnection.HTTP_NO_CONTENT;
            // try {
            //URI requestedUri = he.getRequestURI();
            //String path = requestedUri.getPath();
            if (he.getRequestMethod().equalsIgnoreCase("POST")) {
                // REQUEST Headers
                //Headers requestHeaders = he.getRequestHeaders();
                //for (String key : requestHeaders.keySet())
                //    System.err.println("HEADER " + key  + ":"  + requestHeaders.getFirst(key));
                // REQUEST Body
                InputStream is = he.getRequestBody();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

                String text = null;
                //Integer sentid = null;

                String boundary = Multipart.getBoundary(he.getRequestHeaders());
                if (boundary != null) {
                    // multipart
                    Multipart contents = new Multipart(boundary, br);
                    if ((debug & 0x20) != 0) {
                        System.err.println("ParseHandler: multiparts:\n" + contents);
                    }
                    text = contents.getFields().get("text").trim();
                    //sentid = Integer.parseInt(contents.getFields().get("sentid").trim());

                } else {
                    // post normal
                    StringBuilder contents = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        contents.append(line).append(" ");
                    }
                    //text = contents.toString().trim();
                    if ((debug & 0x20) != 0) {
                        System.err.println("ParseHandler: data: ");
                    }
                    String[] lines = contents.toString().split("&");
                    for (String l : lines) {
                        l = l.trim();

                        if ((debug & 0x20) != 0) {
                            System.err.println("  " + l);
                        }

                        //System.err.println("ddd " + l);
                        if (l.startsWith("txt=")) {
                            text = URLDecoder.decode(l.substring(4), "UTF-8"); // couper cmd=
                            // break;
//                        } else if (l.startsWith("sentid=")) {
//                            String s = URLDecoder.decode(l.substring(7), "UTF-8"); // couper sentid=
//                            sentid = Integer.parseInt(s);
                        }
                    }
                }

                if (text != null && !text.isEmpty()) {
                    response = cl.ConlluEditor_json(text);
                    http_rtc = HttpURLConnection.HTTP_OK;
                    if (response == null) {
                        response = "";
                        http_rtc = HttpURLConnection.HTTP_NO_CONTENT;
                    }
                }

            } else if (he.getRequestMethod().equalsIgnoreCase("GET")) {
                URI requestedUri = he.getRequestURI();
                String path = requestedUri.getPath();
                json = false;

                String query = requestedUri.getQuery();

                if ((debug & 0x20) != 0) {
                    System.err.println("ParserHandler <" + path + "> query <" + query + ">");
                }

//                Integer sentid = null;
//                if (query != null) {
//                    Map<String, String> ma = EditorHTTPServer.queryToMap(query);
//                    String s = ma.get("sentid");
//                    if (s != null) {
//                        sentid = Integer.parseInt(s);
//
//                        if (path.equals("/parse/todo")) {
//                            response = "todoo";
//                            http_rtc = HttpURLConnection.HTTP_OK;
//                            json = true;
//
//                        } else {
//                            http_rtc = HttpURLConnection.HTTP_BAD_REQUEST;
//                            response = e400;
//                        }
//                    }
//                } else
                if (path.equals("/parse/info")) {
                    response = cl.getInfo() + "\n";
                    http_rtc = HttpURLConnection.HTTP_OK;
                    json = true;
                } else {
                    http_rtc = HttpURLConnection.HTTP_NOT_FOUND;
                    response = e404;
                }
            }

            if ((debug & 0x40) != 0) {
                System.err.format("EditHandler Response %d <%s>\n", http_rtc, response);
            }

            Headers hdrs = he.getResponseHeaders();
            if (json) {
                hdrs.set("Content-Type", "application/json" + ";charset=UTF-8");
            } else {
                hdrs.set("Content-Type", "text/plain" + ";charset=UTF-8");
            }
            hdrs.set("Access-Control-Allow-Origin", "*");
            //if (http_rtc == HttpURLConnection.HTTP_OK) {
            he.sendResponseHeaders(http_rtc, response.getBytes("utf-8").length);
            // }

            // RESPONSE Body
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes("utf-8"));
            //Close
            he.close();
        }
    }

    /**
     * class for the simple HTTP server (providing html/css/js files for the
     * editor or parserclient)
     */
    class FileHandler implements HttpHandler {

        private final String rootdir;
        private final String indexhtml;

        public FileHandler(String rootdir, String indexhtml) {
            this.rootdir = rootdir;
            this.indexhtml = indexhtml;
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            if ((debug & 4) != 0) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Date date = new Date();
                InetSocketAddress remoteAddress = he.getRemoteAddress();
                String client;
                if ((debug & 8) != 0) {
                    client = remoteAddress.getHostName();
                } else {
                    client = remoteAddress.toString();
                }
                System.out.println(dateFormat.format(date) + " " + he.getRequestMethod() + " FileHandler request from " + client);
            }

            //String response = "";
            byte[] fileContent = null;
            String ctype = "text/html;charset=UTF-8";
            int http_rtc = HttpURLConnection.HTTP_NOT_FOUND;
            if (he.getRequestMethod().equalsIgnoreCase("GET")) {
                URI requestedUri = he.getRequestURI();
                String path = requestedUri.getPath();
                if ((debug & 0x200) != 0) {
                    System.err.println("FileHandler <" + path + ">");
                }

                if (path.isEmpty() || "/".equals(path)) {
                    path = rootdir + '/' + indexhtml;
                    ctype = "text/html;charset=UTF-8";
                } else {
                    path = rootdir + path;
                    if (path.endsWith(".js")) {
                        ctype = "application/javascript;charset=UTF-8";
                    } else if (path.endsWith(".css")) {
                        ctype = "text/css;charset=UTF-8";
                    } else if (path.endsWith(".ico")) {
                        ctype = "image/vnd.microsoft.icon";
                    } else if (path.endsWith(".jpg")) {
                        ctype = "image/jpeg";
                    } else if (path.endsWith(".jpeg")) {
                        ctype = "image/jpeg";
                    } else if (path.endsWith(".png")) {
                        ctype = "image/png";
                    } else if (path.endsWith(".svg")) {
                        ctype = "image/svg+xml";
                    } else if (path.endsWith(".md")) {
                        ctype = "text/markdown";
                    }
                }

                //System.err.println("QQQQ " + path);
                try {
                    fileContent = Files.readAllBytes(new File(path).toPath());
                    http_rtc = HttpURLConnection.HTTP_OK;
                } catch (Exception ex) {
                    //ex.printStackTrace();
                    http_rtc = HttpURLConnection.HTTP_NOT_FOUND; // 404
                    fileContent = e404.getBytes();
                }
            } else {
                http_rtc = HttpURLConnection.HTTP_BAD_METHOD; // 405
                URI requestedUri = he.getRequestURI();
                String path = requestedUri.getPath();
                fileContent = String.format("Filehandler: Bad POST Path '%s'", path).getBytes();
            }

            if ((debug & 0x400) != 0) {
                System.err.format("FileHandler Response %d <%s>\n", http_rtc, ctype);
            }
            Headers hdrs = he.getResponseHeaders();
            hdrs.set("Content-Type", ctype);

            hdrs.set("Access-Control-Allow-Origin", "*");
            //if (http_rtc == HttpURLConnection.HTTP_OK)
            he.sendResponseHeaders(http_rtc, fileContent.length);

            // RESPONSE Body
            OutputStream os = he.getResponseBody();
            os.write(fileContent);
            he.close();
        }
    }

    /**
     * returns the url parameters in a map
     *
     * @param query
     * @return map
     */
    static protected Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                if (pair.length > 1) {
                    result.put(pair[0], pair[1]);
                } else {
                    result.put(pair[0], "");
                }
            }
        }
        return result;
    }

}
