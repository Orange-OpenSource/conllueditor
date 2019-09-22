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
 @version 1.14.0 as of 20th September 2019
 */


var URL_BASE = 'http://' + window.location.hostname + ':12347/edit';

/* test with curl
 curl --noproxy '*' -F "sentid=1" -F "cmd=read 1"  http://localhost:8888/edit/


 */

// Return the value of the named parameter
//function getParameter(name) {
//    var v = window.location.search.match(new RegExp('(?:[\?\&]' + name + '=)([^&]+)'));
//    return v ? decodeURIComponent(v[1]) : null;
//}


// TODO add new sentence


var sentenceId = 0;

function choosePort() {
    // http port
    if (window.location.port != "") {
        $("#port").val(window.location.port);
        //$("#port").prop('disabled', true);
        $("#portinfo").hide();
    }
    URL_BASE = 'http://' + window.location.hostname + ':' + $("#port").val() + '/edit/';
    var url = new URL(window.location.href);
    var c = url.searchParams.get("port");
    //alert(c);
    if (c != null) {
        if (c == "6666") {
            alert("Port blocked by Firefox (also 6000, 6665, 6667, 6668, 6669)")
        }
        URL_BASE = 'http://' + window.location.hostname + ':' + c + '/edit/';
        document.getElementById('port').value = c;
    }
    //alert(c + " " + URL_BASE);
}


function downloadSVG(ident) {
    // download the generated svg to use elsewhere (e.G. transform to pdf with inkscape)
    var svg = document.getElementById("arbre");
    /* TODO add here in to svg/defs:
     <style type="text/css"><![CDATA[
     ... content onf current css ...
     ]]></style>
     */
    var data = new Blob([svg.innerHTML]);
    var a2 = document.getElementById(ident);
    a2.href = URL.createObjectURL(data);
    a2.download = "arbre_" + $("#sentid").val() + ".svg";
}


function getRaw(what) {
    var urlbase = URL_BASE + "get" + what + "?sentid=" + ($("#currentsent").text() - 1);
    //console.log("UU " + urlbase);
    $('#rawtext').empty(); // clean text
    $('#showRawModalLabel').html(what); //update modal title
    $.ajax({
        url: urlbase,
        type: 'GET',
        headers: {
            'Content-type': 'text/plain'

        },
        statusCode: {
            400: function () {
                alert('Bad query');
            }
        },
        success: function (data) {
            //$('#rawtext').empty();
            //$('#showRawModalLabel').html(what);
            //console.log("DD " + JSON.stringify(data));
            $('#rawtext').append(data.raw);
        }
    });
}

var deprellist = [];
var uposlist = [];
var xposlist = [];

/** get information from ConlluEditor server:
 name of edited file
 lists of valid UPOX, XPOS, deprel
 version of server
 edit mode
 */
function getServerInfo() {
    //var urlbase = 'http://' + window.location.host + ':' + $("#port").val() + '/';

    // also get the lists of valid upos and deprels
    var urlbase = URL_BASE + "validlists";

    $.ajax({
        url: urlbase, //+'/foo/fii?fuu=...',
        //async: false,
        type: 'GET',
        headers: {
            'Content-type': 'text/plain'

        },
        statusCode: {
            400: function () {
                alert('Bad query');
            }
        },
        success: function (data) {
            if (data.validdeprels)
                deprellist = data.validdeprels;
            if (data.validUPOS)
                uposlist = data.validUPOS;
            if (data.validXPOS)
                xposlist = data.validXPOS;

            if (data.shortcuts) {
                console.log("SHORTCUTS", data.shortcuts);
                $("#scfilename").html(data.shortcuts.filename);
                if (data.shortcuts.deplabel)
                    shortcutsDEPL = data.shortcuts.deplabel;
                if (data.shortcuts.upos) {
                    shortcutsUPOS = data.shortcuts.upos;
                }
                if (data.shortcuts.xpos)
                    shortcutsXPOS = data.shortcuts.xpos;
                parseShortcuts();
            } else {
               showshortcuts();
            }

            $('#filename').empty();
            $('#filename').append(data.filename);
            if (data.reinit) {
                $('#mode').append("[browse mode only]");
                $('#modifier').prop('disabled', true);
                $('.editmode').hide();
            } else {
                $('.editmode').show();
            }

            // set version number to logo (shown if mouse hovers on the logo)
            $('#logo').attr("title", data.version);

            $(function () {
                $("#cupos").autocomplete({
                    source: uposlist,
                    // https://www.plus2net.com/jquery/msg-demo/autocomplete-position.php
                    position: {my: "left top", at: "left bottom"}
                });
            });

            $(function () {
                $("#cxpos").autocomplete({
                    source: xposlist,
                    position: {my: "left top", at: "left bottom"}
                });
            });

            $(function () {
                $("#cdeprel").autocomplete({
                    source: deprellist,
                    position: {my: "left top", at: "left bottom"}
                });
            });

        },
        error: function (data) {
            // do something else
            console.log("ERREUR " + data);
            alert("ConlluEditor server not running on '" + urlbase + "' ?");
        }
    });

    // initial states
    $('.onlyWithTree').hide();
    $('#bie').hide();
    $('#edit_ed').hide();
    $('#undo').prop('disabled', true);
    $('#redo').prop('disabled', true);
    $('#save').prop('disabled', true);
}


var more = true;
var lastmore = true;
function ToggleSearch() {
    if (more) {
        $("#act_search").text("more");
        $(".search").hide();
        $('body').css("margin-top", "150px"); // header is smaller, decrease body margin
        more = false;
    } else {
        $("#act_search").text("less");
        $(".search").show();
        $('body').css("margin-top", "280px");
        more = true;
        if (showshortcathelp)
            ToggleShortcutHelp();
    }
}

var showshortcathelp = false;

function ToggleShortcutHelp() {
    if (showshortcathelp) {
        //$("#act_search").text("more");
        $("#shortcuthelp").hide();
        $('body').css("margin-top", "150px"); // header is smaller, decrease body margin
        showshortcathelp = false;
        if (lastmore)
            ToggleSearch();
    } else {
        //$("#act_search").text("less");
        $("#shortcuthelp").show();

        showshortcathelp = true;
        lastmore = more; // show search again when shortcut help is switched off
        if (more) {
            ToggleSearch();
        }
        $('body').css("margin-top", "260px");
    }
}


// default shortcuts (overriden by from configuration file)
var shortcutsUPOS = {
    /*   "N": "NOUN",
     "A": "ADV",
     "J": "ADJ",
     "V": "VERB",
     "E": "PROPN", // named entity
     "D": "DET",
     ".": "PUNCT",
     "C": "CCONJ",
     "S": "SCONJ",
     "U": "NUM",
     "R": "PRON",
     "X": "AUX",
     "I": "INTJ",
     "P": "ADP",
     "T": "PART",*/
};

var shortcutsDEPL = {
    /*   "s": "nsubj",
     "o": "obj",
     "m": "nmod",
     "c": "case",
     "d": "det",
     "p": "punct",
     "a": "amod",
     "l": "acl",
     "v": "advcl",
     "x": "xcomp",
     "u": "nummod",*/
};

var shortcutsXPOS = {// no point defining language specific xpos here.
    //"N": ["NN", "NOUN"], // XPOS modifies also upos
    //"E": ["NNP", "PROPN"],
    //"W": ["VBZ"] // xpos keeps upos unchanged
};


/** run gy getServerInfo() when no shortcuts are provided by server.
    Reads defaults from gui/shortcut.json and updates help page.
 */
function showshortcuts() {
    console.log("load DEFAULT shortcuts");
    $.getJSON("shortcuts.json", function (json) {
        //console.log("zzzzzz", json);
        // if no error, we override defaults
        shortcutsUPOS = json.upos;
        shortcutsXPOS = json.xpos;
        shortcutsDEPL = json.deplabel;
        $("#scfilename").html("gui/shortcuts.json");
        parseShortcuts();
    });
}

/** read json from shortcutsUPOS and update Help Modal */
function parseShortcuts() {
    console.log("PPPP");
    var sc_uposString = "";
    var sc_xposString = "";
    var sc_deplString = "";

    $("#shortcuttableUPOS").empty(); // clear default values
    $("#shortcuttableUPOS").append("<tr><th>key</th> <th>set UPOS to</th></tr>"); // add header
    $("#uposshortcuts").empty();
    for (var p in shortcutsUPOS) {
        //console.log("eee", shortcutsUPOS[p]);
        $("#shortcuttableUPOS").append("<tr><td>" + p + "</td> <td>" + shortcutsUPOS[p] + "</td></tr>");
        sc_uposString += '<span class="sckey">' + p + "</span>:" + shortcutsUPOS[p] + "&nbsp;&nbsp;";
    }
    $("#uposshortcuts").append(sc_uposString);


    $("#shortcuttableDEPL").empty();
    $("#shortcuttableDEPL").append("<tr><th>key</th> <th>set deplabel to</th></tr>"); // add header
    $("#deplshortcuts").empty();
    for (var p in shortcutsDEPL) {
        $("#shortcuttableDEPL").append("<tr><td>" + p + "</td> <td>" + shortcutsDEPL[p] + "</td></tr>");
        sc_deplString += '<span class="sckey">' + p + "</span>:" + shortcutsDEPL[p] + "&nbsp;&nbsp;";
    }
    $("#deplshortcuts").append(sc_deplString);


    $("#xposshortcuts").empty();
    $("#shortcuttableXPOS").empty();
    $("#shortcuttableXPOS").append("<tr><th>key</th> <th>set XPOS to</th> <th>set UPOS to</th></tr>"); // add header
    for (var p in shortcutsXPOS) {
        //console.log("XXX", p, shortcutsXPOS[p][0], shortcutsXPOS[p][1]);
        $("#shortcuttableXPOS").append("<tr><td>" + p + "</td> <td>"
                + shortcutsXPOS[p][0] + "</td> <td>"
                + shortcutsXPOS[p][1] + "</td></tr>");
        sc_xposString += '<span class="sckey">' + p + "</span>:" + shortcutsXPOS[p][0] + "/" + shortcutsXPOS[p][1] + "&nbsp;&nbsp;";
        // $.each(shortcutsUPOS, function(k, v) {
        //     result += k + " , " + v + "\n";
        // });
    }
    $("#xposshortcuts").append(sc_xposString);
}

var conllwords = {};
var clickedNodes = [];
var deprels = [];
var uposs = [];
var clickCount = 0;
var wordedit = false;
var editword_with_doubleclick = true; // in order to deactivate word-edit with double click, set to false


// process shortcuts: we catch keys hit in the editor. If a word is active, we try to apply
$(window).on('keypress', function (evt) {
    //console.log("AEVT", evt, evt.keyCode, String.fromCharCode(evt.keyCode), clickedNodes);
    /*
     $.getJSON("shortcuts.json", function (json) {
     //console.log("zzzzzz", json);
     // if no error, we override defaults
     shortcutsUPOS = json.upos;
     shortcutsXPOS = json.xpos;
     shortcutsDEPL = json.deplabel;
     });
     */
    //$("#shortcuthelp").hide(); // initially hidden

    if (evt.keyCode == 63) {
        ToggleShortcutHelp();
    } else if (clickedNodes.length == 1) {
        //
        // a word is active
        // interpret shortkeys

        var newval = shortcutsUPOS[String.fromCharCode(evt.keyCode)];
        if (newval != undefined) {
            //console.log("UPOS", newval);
            sendmodifs({"cmd": "mod upos " + clickedNodes[0] + " " + newval});
            clickedNodes = [];
            deprels = [];
            uposs = [];
        }

        newval = shortcutsDEPL[String.fromCharCode(evt.keyCode)];
        if (newval != undefined) {
            //console.log("DEPL", newval);
            sendmodifs({"cmd": "mod deprel " + clickedNodes[0] + " " + newval});
            clickedNodes = [];
            deprels = [];
            uposs = [];
        }

        newval = shortcutsXPOS[String.fromCharCode(evt.keyCode)];
        if (newval != undefined) {
            console.log("XPOS", newval, newval[0]);
            sendmodifs({"cmd": "mod xpos " + clickedNodes[0] + " " + newval[0]});
            if (newval.length > 1) {
                console.log("--UPOS", newval[1]);
                sendmodifs({"cmd": "mod upos " + clickedNodes[0] + " " + newval[1]});
            }
            clickedNodes = [];
            deprels = [];
            uposs = [];
        }

    }
})

// permet de modifier l'arbre en cliquant sur un mot et sa future tete (cf. edit.js)
// il faut clikcer sur un mot et sur sa future tete pour changer l'arbre
// si on click deux fois sur le meme mot, ce mot devient racine
// si on click ailleurs, l'historique est vidée.
function ModifyTree(evt) {
    var target = evt.target;
    // id: "rect_" + item.id + "_" + item.upos + "_" +  item.xpos + "_" +  item.lemma + "_" +  item.form + "_" + item.deprel
    //        0        1                 2                   3                4                  5                 6
    var id = target.id.split("_");
    //alert("MTT " + target.id + " == " + target);
    //alert("IDDD " + id );
    //alert("rrSSr " + conllwords[id[1]].feats);

    if (target.id) {
        if (id[0] == "rect") {
            //alert("SHIFT: " + evt.shiftKey)

            // deal with a doubleclick: a quick double click opens the edit window

            if (editword_with_doubleclick) {
                clickCount++;
                if (clickCount === 1) {
                    singleClickTimer = setTimeout(function () {
                        clickCount = 0;
                    }, 200);
                } else if (clickCount === 2) {
                    clearTimeout(singleClickTimer);
                    clickCount = 0;
                    //counter.textContent = count;
                    wordedit = true;
                }
            }

            if (evt.ctrlKey || wordedit) {
                wordedit = false;
                //if (evt.shiftKey) {
                var conllword = conllwords[id[1]];
                $("#cid").text(conllword.id);
                $("#cform").val(conllword.form);
                $("#clemma").val(conllword.lemma);
                $("#cupos").val(conllword.upos);
                $("#cxpos").val(conllword.xpos);

                // get features from json and put them into a list for the edit window
                // TODO improve edit window
                var fs = "";
                if (conllword.feats != undefined) {
                    for (e = 0; e < conllword.feats.length; ++e) {
                        var feat = conllword.feats[e];
                        if (e > 0)
                            fs += "\n";
                        fs += feat.name + "=" + feat.val;
                    }
                } else
                    fs = "_";
                $("#cfeats").val(fs);

                // get enhanced deps from json and put them into a list for the edit window
                // TODO graphical edit
                var eh = "";
                if (conllword.enhancedheads != undefined) {
                    for (e = 0; e < conllword.enhancedheads.length; ++e) {
                        var edh = conllword.enhancedheads[e];
                        if (e > 0)
                            eh += "\n";
                        eh += edh.id + ":" + edh.deprel;
                    }
                } else
                    eh = "_";
                $("#cenhdeps").val(eh);

                // get misc from json and put them into a list for the edit window
                // TODO improve edit window
                var mc = "";
                if (conllword.misc != undefined) {
                    for (e = 0; e < conllword.misc.length; ++e) {
                        var mch = conllword.misc[e];
                        if (e > 0)
                            mc += "\n";
                        mc += mch.name + "=" + mch.val;
                    }
                } else
                    mc = "_";
                $("#cmisc").val(mc);


                // open edit window
                $("#wordEdit").modal();
                clickedNodes = [];
                $(".wordnode").attr("class", "wordnode");
            } else {
                // dependency editing
                clickedNodes.push(id[1]);
                //target.setAttribute("class", "wordnode boxhighlight");
                target.setAttribute("class", target.getAttribute("class") + " boxhighlight");
                // not very good ...
                deprels.push(id[6]);
                uposs.push(id[2]);
                //alert("CCC: " + deprels);
                //console.log("toto " + target.id+ " " + id + " deprel0 <" + deprels[0] + ">");
                if (clickedNodes.length == 2) {
                    var makeRoot = false;
                    if (clickedNodes[0] == clickedNodes[1]) {
                        // clicked twice on same node: current nodes becomes a root node
                        clickedNodes[1] = "0 root";
                        makeRoot = true;
                    }

                    if (editing_enhanced) {
                        //$("#mods").val("ed add " + clickedNodes[0] + " " + clickedNodes[1]);
                    } else {
                        $("#mods").val(clickedNodes[0] + " " + clickedNodes[1] + " ");
                        $("#modifier").click();
                    }
                    //alert("AAAAA: " + $("#mods").val());


                    //alert("BBBB: " + makeRoot + " " + JSON.stringify(deprels));
                    if (!makeRoot
                            && (editing_enhanced || deprels[0] == "" || deprels[0] == "root" || deprels[0] == "undefined" || deprels[0] == undefined)) {
                        var potential = "";
                        if (uposs[0] == "DET")
                            potential = "det";
                        else if (uposs[0] == "AUX")
                            potential = "cop";
                        else if (uposs[0] == "ADP")
                            potential = "case";
                        else if (uposs[0] == "PUNCT")
                            potential = "punct";
                        else if (uposs[0] == "CCONJ")
                            potential = "cc";
                        else if (uposs[0] == "SCONJ")
                            potential = "mark";
                        else if (uposs[0] == "ADJ")
                            potential = "amod";
                        else if (uposs[0] == "ADV")
                            potential = "advmod";
                        else if (uposs[0] == "PART") // yn dda
                            potential = "case:pred";

                        // open deprel edit (for basic or enhanced deps)
                        if (editing_enhanced) {
                            $("#cheaden").text(clickedNodes[1]);
                            $("#cdepen").text(clickedNodes[0]);
                            $("#cdeprelen").val(potential);
                            $("#enhdeprelEdit").modal()
                        } else {
                            $("#chead").text(clickedNodes[1]);
                            $("#cdep").text(clickedNodes[0]);
                            $("#cdeprel").val(potential);
                            $("#deprelEdit").modal()
                        }


//                        var deprel = prompt("also enter deprel", potential);
//                        if (deprel != "" && deprel != null) {
//                            //alert(id[3] + " --> " + deprel);
//                            $("#mods").val(clickedNodes[0] + " " + clickedNodes[1] + " " + deprel);
//                            $("#modifier").click();
//                        }
                    }

                    clickedNodes = [];
                    deprels = [];
                    uposs = [];

                } else {
                    $("#mods").val(clickedNodes[0] + " ");
                }
            }
        } else if (id[0] == "textpath" || id[0] == "path") { // TODO use classes ?
            // update deprel
            $("#chead").text(id[1]);
            $("#cdep").text(id[2]);
            $("#cdeprel").val(id[3]);
            //$("#depreledit").dialog("open");
            $("#deprelEdit").modal()
//            var deprel = prompt("enter new deprel", id[3]);
//            if (deprel != "" && deprel != null && deprel != id[3]) {
//                //alert(id[3] + " --> " + deprel);
//                $("#mods").val(/*child*/ id[2] + " " + /* head */ id[1] + " " + deprel);
//                $("#modifier").click();
//            }
        } else if (id[0] == "enhtextpath") { // TODO use classes ?
            // update deprel
            $("#cheaden").text(id[1]);
            $("#cdepen").text(id[2]);
            $("#cdeprelen").val(id[3]);
            $("#enhdeprelEdit").modal()
        } else if (id[0] == "mwe") {
            //alert("MT MWE: " + id + " " + target);
            $("#currentmwefrom").val(id[1]);
            $("#currentmweto").val(id[2])
            $("#currentmweform").val(id[3])

            $("#editMWE").modal();

            $("#mods").val("");
            //$(".wordnode").attr('fill', 'white');
            $(".wordnode").attr("class", "wordnode");
            clickedNodes = [];
            deprels = [];
            uposs = [];

        } else {
            //alert("MT " + target.id + " == " + target);
            $("#mods").val("");
            //$(".wordnode").attr('fill', 'white');
            $(".wordnode").attr("class", "wordnode");
            clickedNodes = [];
            deprels = [];
            uposs = [];
        }
    } else {
        $("#mods").val("");
        //$(".wordnode").attr('fill', 'white');
        $(".wordnode").attr("class", "wordnode");
        clickedNodes = [];
        deprels = [];
        uposs = [];
    }
    //console.log("aaa " + JSON.stringify(clickedNodes));
}


var flatgraph = false;
var showfeats = false;
var showmisc = false;
var showr2l = false;
var showextra = false;
var backwards = false;
var show_basic_in_enhanced = false; // if true we display enhanced deps which are identical two basic deps
var editing_enhanced = false;

// position of highlighted things (search result)
var highlightX = 0;
var highlightY = 0;
/**
 *  afficher une phrase avec ses relations sémantique
 * @param {type} item json retourné par le serveur avec "relation" et "tree" liste avec un arbre ou plusieurs arbres partiels
 * @returns {undefined}
 */
function formatPhrase(item) {
    //console.log("eee " + item.message )

    highlightX = 0;
    highlightY = 0;

    if (item.error) {
        alert(item.error);
        $("#mods").val("");
        $(".wordnode").attr("class", "wordnode");

    } else if (item.message) {
        if (item.changes > 0)
            $('#save').prop('disabled', false);
        else
            $('#save').prop('disabled', true);
        $("#changespendingsave").html("(" + item.changes + ")")
        alert(item.message);
    } else {
        $('.onlyWithTree').show();
        setSize(parseInt($("#bwidth").val()), parseInt($("#bheight").val()));

        var svg = document.createElementNS(svgNS, "svg");

// TODO: various trials to add css data to the svg tree
//        $.ajax({
//            url: "./depgraph.css",
//            dataType: "text",
//            success: function (cssText) {
//                // cssText will be a string containing the text of the file
//               // console.log("rrr " + cssText);
//               // var style = document.createElementNS(svgNS, "style");
//               // svg.appendChild(style);
//               toto = cssText;
//            }
//        });

//
//    $.get("depgraph.css", function(cssContent){
//        alert("My CSS = " + cssContent);
//        toto = cssContent;
//    });
//console.log("rrr " + toto);

        //   $.when($.get("./depgraph.css"))
        //       .done(function(response) {
        //console.log(response);

        //var style = document.createElementNS(svgNS, "style");
        //svg.appendChild(style);
//                style.textContent = response;
        //console.log(svg);
//              //  //$('<style />').text(response).appendTo(svg);
//               // $('div').html(response);
        //   });


        //  var style = document.createElementNS(svgNS, "style");
        //        svg.appendChild(style);
        //
        //
        //svg.setAttribute('x', 10);
        //svg.setAttribute('y', 20);
        $("#arbre").empty(); // vider le div
        $("#titre").empty();
        $("#total").empty();
        $("#currentsent").empty();
        $("#cursentid").empty();
        $("#commentfield").empty();
        $("#errors").empty();

        // display sentence text
        $("#sentid").val(item.sentenceid + 1);
        $("#currentsent").append(item.sentenceid + 1);
        if (item.sent_id) {
            $("#cursentid").append(" (sent_id: ").append(item.sent_id).append(") ");
        }
        $("#total").append(item.maxsentence);
        $("#titre").append(item.sentence);
        if (item.errors != undefined) {
            if (item.errors.heads)
                $("#errors").append("|" + item.errors.heads + " roots");
            if (item.errors.badroots)
                $("#errors").append("|" + item.errors.badroots + " 'root' without head == 0");
            if (item.errors.invalidUPOS)
                $("#errors").append("|" + item.errors.invalidUPOS + " invalid UPOS");
            if (item.errors.invalidXPOS)
                $("#errors").append("|" + item.errors.invalidXPOS + " invalid XPOS");
            if (item.errors.invalidDeprels)
                $("#errors").append("|" + item.errors.invalidDeprels + " invalid Deprels");
            $("#errors").append("|");
        }
        $("#sentencetext").show();


        //if (item.info != "_")
        //    $("#titre").append(" (" + item.info + ")");


        if (item.tree.length > 1) {
            // count trees other than empty nodes
            var ct = 0;
            for (var k = 0; k < item.tree.length; ++k) {
                var head = item.tree[k];
                if (head.token != "empty") {
                    ct++;
                }
            }
            if (ct > 1)
                document.getElementById("titre").style.color = "#880000";
            else
                document.getElementById("titre").style.color = "#101010";
        } else
            document.getElementById("titre").style.color = "#101010";
        $("#arbre").append(svg);
        var use_deprel_as_type = true;
        var sentencelength = 0;
        //if ($("#right2left").is(":checked")) {
        if (showr2l) {
            sentencelength = item.length;
        }
        //alert("SENT LENGTH: " + item.length);
        //alert("CCC: " + item.tree.length);
        //if ($("#flat").is(":checked")) {
        if (flatgraph) {
            drawDepFlat(svg, item.tree, sentencelength, use_deprel_as_type);
        } else {
            drawDepTree(svg, item.tree, sentencelength, use_deprel_as_type);
        }


        if (highlightX > 40 || highlightY > 100) {
            //alert("hlt " + highlightX + " " +highlightY);
            $('body, html').scrollTop(highlightY - 100);
            $('body, html').scrollLeft(highlightX - 120);
        } else if (showr2l) {
            // scroll to right for languages like Hebrew or Arabic
            $('body, html').scrollLeft($(document).outerWidth());
        }


        // display comments
        $("#commentfield").append(item.comments)

        // install svg download button
        downloadSVG("a2");


        //alert("rr" + item.canUndo);
        if (item.canUndo)
            $('#undo').prop('disabled', false);
        else
            $('#undo').prop('disabled', true);
        if (item.canRedo)
            $('#redo').prop('disabled', false);
        else
            $('#redo').prop('disabled', true);

        if (item.changes > 0)
            $('#save').prop('disabled', false);
        else
            $('#save').prop('disabled', true);
        $("#changespendingsave").html("(" + item.changes + ")")

        // make a table wordid: word to access data easier for editing
        conllwords = {};
        for (i = 0; i < item.tree.length; ++i) {
            var head = item.tree[i];
            getConllWords(conllwords, head);
        }
    }
    // make modals draggable
    $('.modal-dialog').draggable({
        handle: ".modal-header"
    });
}


// recursively produce table id:word
function getConllWords(table, head) {
    table[head.id] = head;
    //alert("QQQ " + head);
    if (head.children) {
        for (var i = 0; i < head.children.length; i++) {
            //alert("zzz " + i)
            getConllWords(table, head.children[i]);
        }
    }
}

/* send correct command to ConlluEditor server using ajax http post
 and re diesplay sentence afterwards (with json receivend from server after the modif) */
function sendmodifs(commands) {
    commands["sentid"] = $("#currentsent").text() - 1;
    $.ajax({
        url: URL_BASE,
        type: 'POST',
        async: false, // wait for HTTP finished before returning
        data: commands, //{ "cmd" : inputtext },
        headers: {
            'Content-type': 'text/plain',
            //'Content-length': inputtext.length
        },
        statusCode: {
            204: function () {
                alert('No command given');
            },
            400: function () {
                alert('Bad query: ' + data);
            }
        },
        success: function (data) {
            //console.log("zzzz " + JSON.stringify(data));
            formatPhrase(data);
        },
        error: function (data) {
            //console.log("ERREUR " + data);
            alert("An error occurred (i the ConlluEditor server running?)" + data);
        }
    });
}



$(document).ready(function () {
    choosePort();
    getServerInfo();
    $("#sentencetext").hide();


    /* start comment edit function */
    $("#commentfield").click(function () {
        //alert("rrr "+ $("#commentfield").text());
        $("#commenttext").val($("#commentfield").text());
        //$("#commentedit").dialog("open");

        $("#commentEdit").modal()
    });


    $("#editcommentbutton").click(function () {
        //alert("rrr "+ $("#commentfield").text());
        $("#commenttext").val($("#commentfield").text());
        //$("#commentedit").dialog("open");

        $("#commentEdit").modal()
    });


    /* save edited comment */
    $('#savecomment').click(function () {
        var newcomment = $("#commenttext").val();
        if (newcomment != $("#commentfield").text()) {
            // send comments to server directly (not via #mods)
            sendmodifs({"cmd": "mod comments " + newcomment});
        }

        $('#commentEdit').modal('hide');
    });

    /* delete clicked MWE form */
    $('#editMWtoken').click(function () {
        // send comments to server directly (not via #mods)
        sendmodifs({"cmd": "mod editmwe " + $("#currentmwefrom").val() + " " + $("#currentmweto").val() + " " + $("#currentmweform").val()});
        $('#editMWE').modal('hide');
    });



    /* save edited word */
    $('#saveword').click(function () {
        conllword = conllwords[$("#cid").text()];
        if (conllword.form != $("#cform").val()) {
            sendmodifs({"cmd": "mod form " + conllword.id + " " + $("#cform").val()});
        }
        if (conllword.lemma != $("#clemma").val()) {
            sendmodifs({"cmd": "mod lemma " + conllword.id + " " + $("#clemma").val()});
        }
        if (conllword.upos != $("#cupos").val()) {
            sendmodifs({"cmd": "mod upos " + conllword.id + " " + $("#cupos").val()});
        }
        if (conllword.xpos != $("#cxpos").val()) {
            sendmodifs({"cmd": "mod xpos " + conllword.id + " " + $("#cxpos").val()});
        }

        // TODO: well, can be improved too
        var fs = "";
        if (conllword.feats != undefined) {
            for (e = 0; e < conllword.feats.length; ++e) {
                var feat = conllword.feats[e];
                if (e > 0)
                    fs += ",";
                fs += feat.name + "=" + feat.val;
            }
        } else
            fs = "_";

        if (fs != $("#cfeats").val()) {
            sendmodifs({"cmd": "mod feat " + conllword.id + " " + $("#cfeats").val()});
        }

        // TODO: well, can be improved too
        var ms = "";
        if (conllword.misc != undefined) {
            for (e = 0; e < conllword.misc.length; ++e) {
                var misc = conllword.misc[e];
                if (e > 0)
                    ms += ",";
                ms += misc.name + "=" + misc.val;
            }
        } else
            ms = "_";

        if (ms != $("#cmisc").val()) {
            sendmodifs({"cmd": "mod misc " + conllword.id + " " + $("#cmisc").val()});
        }

        // TODO: well, can be improved too
        var eh = "";
        if (conllword.enhancedheads != undefined) {
            for (e = 0; e < conllword.enhancedheads.length; ++e) {
                var edh = conllword.enhancedheads[e];
                if (e > 0)
                    eh += ",";
                eh += edh.id + ":" + edh.deprel;
            }
        } else
            eh = "_";

        if (eh != $("#cenhdeps").val()) {
            //console.log("<" + $("#cenhdeps").val() + ">\n(" + eh + ">");
            sendmodifs({"cmd": "mod enhdeps " + conllword.id + " " + $("#cenhdeps").val()});
        }
        $('#wordEdit').modal('hide');
    });

    // add/modify basic dep relation
    $('#savedeprel').click(function () {
        //console.log("rrrr " + JSON.stringify(this));
        conllword = conllwords[$("#cdep").text()];
        /* if (flatgraph && editing_enhanced) {
         alert("not good " + $("#cdep").text() + " " + $("#chead").text() + " " + $("#cdeprel").val())
         //sendmodifs({"cmd": "mod ed add " + $("#cdep").text() + " " + $("#chead").text() + " " + $("#cdeprel").val()});
         } else */
        if (conllword.deprel != $("#cdeprel").val()) {
            sendmodifs({"cmd": "mod " + $("#cdep").text() + " " + $("#chead").text() + " " + $("#cdeprel").val()});
        }
        $('#deprelEdit').modal('hide');
    });

    // add/modify enhanced dep relation
    $('#savedeprelen').click(function () {
        //console.log("rrrr " + JSON.stringify(this));
        conllword = conllwords[$("#cdepen").text()];
        if (flatgraph && editing_enhanced) {
            //alert("hhhhhhhh " + $("#cdepen").text() + " " + $("#cheaden").text() + " " + $("#cdeprelen").val())
            sendmodifs({"cmd": "mod ed add " + $("#cdepen").text() + " " + $("#cheaden").text() + " " + $("#cdeprelen").val()});
            // } else if (conllword.deprel != $("#cdeprel").val()) {
            //    sendmodifs({"cmd": "mod " + $("#cdep").text() + " " + $("#chead").text() + " " + $("#cdeprel").val()});
        }
        $('#enhdeprelEdit').modal('hide');
    });

    // delete existing enhanced dep relation
    $('#deletedeprelen').click(function () {
        conllword = conllwords[$("#cdepen").text()];
        //alert("AAA "+ flatgraph + " "+ editing_enhanced);
        if (flatgraph /* && editing_enhanced */) {
            sendmodifs({"cmd": "mod ed del " + $("#cdepen").text() + " " + $("#cheaden").text()});
        }
        $('#enhdeprelEdit').modal('hide');
    });


    // start show latex
    $("#latex").click(function () {
        //$('#showraw').dialog({title: 'LaTeX code'});
        getRaw("latex");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal()

    });

    // start show conllu
    $("#conllu").click(function () {
        //$('#showraw').dialog({title: "CoNLL-U format"});
        getRaw("conllu");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal()
    });

    // start show sdparse
    $("#sdparse").click(function () {
        //$('#showraw').dialog({title: "SD-Parse format"});
        getRaw("sdparse");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal()
    });


    // run validation
    $("#valid").click(function () {
        getRaw("validation");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal()

    });

    $(".mycheck").click(function () {
        if (this.id == "flat2") {
            if (!flatgraph) {
                $(this).addClass('active');
                $("#bie").show();
                $("#edit_ed").show();
            } else {
                $(this).removeClass('active');
                //$("#flat2").text("show tree" + flatgraph);
                $("#bie").hide();
                $("#edit_ed").hide();
                editing_enhanced = false;
                $("#edit_ed").removeClass('active');
            }
            flatgraph = !flatgraph;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "feat2") {
            if (!showfeats) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showfeats = !showfeats;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "misc2") {
            if (!showmisc) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showmisc = !showmisc;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);

        } else if (this.id == "bie") {
            if (!show_basic_in_enhanced) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            show_basic_in_enhanced = !show_basic_in_enhanced;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "edit_ed") {
            if (!editing_enhanced) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            editing_enhanced = !editing_enhanced;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "r2l") {
            if (!showr2l) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showr2l = !showr2l;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "extracols") {
            if (!showextra) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showextra = !showextra;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id == "backwards") {
            backwards = !backwards;
            if (backwards) {
                //$(this).addClass('active');
                $(this).parent().addClass('active');
            } else {
                //$(this).removeClass('active');
                $(this).parent().removeClass('active');
                //$("#flat2").text("show tree" + flatgraph);
            }
        }
    });

    /**
     * cliquer su un des boutons déclenche une action en fonction de leur attribut id
     */
    $(".editbuttons").click(function () {
        //console.log("sss " + this.id);
        //URL_BASE = 'http://localhost:' + $("#port").val() + '/edit';
        URL_BASE = 'http://' + window.location.hostname + ':' + $("#port").val() + '/edit/';

        //var backwards = $("#backwards").is(":checked");

        var inputtext;
        if (this.id == "findword") {
            inputtext = "findword " + backwards + " " + $("#word").val();
        } else if (this.id == "findlemma") {
            inputtext = "findlemma " + backwards + " " + $("#lemma").val();
        } else if (this.id == "findupos") {
            inputtext = "findupos " + backwards + " " + $("#upos").val();
        } else if (this.id == "findxpos") {
            inputtext = "findxpos " + backwards + " " + $("#xpos").val();
        } else if (this.id == "finddeprel") {
            inputtext = "finddeprel " + backwards + " " + $("#deprel").val();
        } else if (this.id == "findcomment") {
            inputtext = "findcomment " + backwards + " " + $("#comment").val();
        } else if (this.id == "findmulti") {
            inputtext = "findmulti " + backwards + " " + $("#multifield").val();
        } else if (this.id == "save") {
            inputtext = "save";
        } else if (this.id == "redo") {
            var inputtext = "mod redo";
        } else if (this.id == "undo") {
            var inputtext = "mod undo";
        } else if (this.id == "next") {
            inputtext = "next";
            //$("#sentid").val(inputtext);
        } else if (this.id == "prec") {
            inputtext = "prec";
        } else if (this.id == "first") {
            inputtext = "read 0";
        } else if (this.id == "last") {
            inputtext = "read last";
        } else if (this.id == "lire") {
            inputtext = "read " + ($("#sentid").val() - 1);
        } else if (this.id == "modifier") {
            var inputtext = "mod " + $("#mods").val();
        } else if (this.id == "valid") {
            inputtext = "valid";
        } else {
            alert("error in GUI" + this)
        }

        datadico = {"cmd": inputtext};
        sendmodifs(datadico);
    });


    // use ENTER to read sentence
    $("#sentid").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#lire").click();
        }
    });

    // use ENTER to read sentence
    $("#word").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#findword").click();
        }
    });

    // use ENTER to read sentence
    $("#lemma").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#findlemma").click();
        }
    });

    // use ENTER to read sentence
    $("#xpos").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#findxpos").click();
        }
    });

    // use ENTER to read sentence
    $("#upos").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#findupos").click();
        }
    });

    $("#deprel").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#finddeprel").click();
        }
    });

    /*$('#depreledit').live('keyup', function(e){
     if (e.keyCode == 13) {
     $(':button:contains("Ok")').click();
     }
     });*/
    /*
     $("#cdeprel").keyup(function (event) {
     if (event.keyCode == 13) {
     //alert("cccc");
     //event.target
     //alert(JSON.stringify($(event)));
     //$("#finddeprel").click();
     //	$("cdeprelOK").click();
     }
     });
     */

    // TODO pour lire la phrase r
    //unction relirePhraseCourante() {
    //    $("#lire").click();
    //}

    // faire lire phrase régulièrement
    // window.setTimeout(relirePhraseCourante, 2000);


//    $("#vider").click(function () {
//        // $("#texte").val("");
//        $("#arbre").empty();
//    });
});


