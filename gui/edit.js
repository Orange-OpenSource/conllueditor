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
 @version 2.22.2 as of 24th May 2023
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

var isIE = /*@cc_on!@*/false || !!document.documentMode;
var isEdge = !isIE && !!window.StyleMedia;
var sentenceId = 0;

function choosePort() {
    // http port
    if (window.location.port != "") {
        $("#port").val(window.location.port);
        //$("#port").prop('disabled', true);
        $("#portinfo").hide();
    }
    URL_BASE = 'http://' + window.location.hostname + ':' + $("#port").val() + '/edit/';

    var c = null;
    if (!isIE && !isEdge) {
        var url = new URL(window.location.href);
        c = url.searchParams.get("port"); // crashes apparently on Edge 41
    } else {
        var url = new URL(window.location.href);
        console.log(url);
        c = url.port;
        $("#logo").empty(); // badly scaled on Edge, just don't show it for the time being
    }

    if (c != null) {
        if (c == "6666") {
            alert("Port blocked by Firefox (also 6000, 6665, 6667, 6668, 6669)")
        }
        URL_BASE = 'http://' + window.location.hostname + ':' + c + '/edit/';
        document.getElementById('port').value = c;
    }
}

// cf. https://jsfiddle.net/2wAzx/13/
function enableTab(id) {
    //console.log("aaaa", id);
    var el = document.getElementById(id);
    el.onkeydown = function (e) {
        if (e.keyCode === 9) { // tab was pressed

            // get caret position/selection
            var val = this.value,
                    start = this.selectionStart,
                    end = this.selectionEnd;

            // set textarea value to: text before caret + tab + text after caret
            this.value = val.substring(0, start) + '\t' + val.substring(end);

            // put caret at right position again
            this.selectionStart = this.selectionEnd = start + 1;

            // prevent the focus lose
            return false;
        }
    };
}

// Enable the tab character onkeypress (onkeydown) inside textarea...
// ... for a textarea that has an `id="editsubtree"`
// with //enableTab("editsubtree");
// in $(document).ready(function () {


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


function getRaw(what, title) {
    var urlbase = URL_BASE + "get" + what + "?sentid=" + ($("#currentsent").text() - 1);
    //console.log("UU " + urlbase);
    $('#rawtext').empty(); // clean text
    $('#showRawModalLabel').html(title); //update modal title
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

var deprellist = ["acl", "acl:relcl", "advcl", "advmod", "amod", "appos", "aux",
    "case", "cc", "ccomp", "conj", "cop", "csubj", "dep", "det",
    "dislocated", "fixed", "flat", "flat:foreign", "flat:name", "iobj",
    "mark", "nmod", "nmod:poss", "nsubj", "nummod", "obj", "obl", "orphan",
    "parataxis", "punct", "root", "xcomp"];
var uposlist = ["ADJ", "ADP", "ADV", "AUX", "CCONJ", "DET", "NOUN", "NUM", "PART", "PRON", "PROPN", "PUNCT", "SCONJ", "SYM", "VERB", "X"];
var xposlist = [];
var featlist = [];
var misclist = ["Gloss=", "LGloss=", "SpaceAfter=No", "SpacesAfter=", "Translit=", "LTranslit=", "Typo=Yes"];
var incorrectwords = {};
var conllucolumns = [];

var shortcuttimeout = 700; // msecs to wait before a shortcut is considered complete


function filestats() {
    getServerInfo(); // get latest stats from server
    $("#fileStats").modal()
}

function makeItemFreqPercent_table(appendto, data, total, what) {
    var upostbl = document.createElement("table");
    upostbl.className = "sortable";
    upostbl.id = "UPT";
    appendto.append(upostbl);

    var thead = document.createElement("thead");
    upostbl.append(thead);
    var headerrow = document.createElement("tr");
    //$("#stats_upos").append(headerrow);
    //upostbl.append(headerrow);
    thead.append(headerrow);
    headerrow.className = "tableheader";

    var hdcell = document.createElement('th');
    headerrow.append(hdcell);
    hdcell.innerHTML = what; //"UPOS";

    hdcell = document.createElement('th');
    headerrow.append(hdcell);
    hdcell.innerHTML = "frequency";

    hdcell = document.createElement('th');
    headerrow.append(hdcell);
    hdcell.innerHTML = "%";
    hdcell.setAttribute('style', 'text-align: right;');

    var tbody = document.createElement("tbody");
    upostbl.append(tbody);
    for (var p in data) {
        var percentage = data[p] * 100 / total;
        var stdrow = document.createElement("tr");
        //upostbl.append(stdrow);
        tbody.append(stdrow);
        //$("#stats_upos").append(stdrow);
        stdrow.className = "everyotherrow";

        var cell = document.createElement('td');
        stdrow.append(cell);
        cell.innerHTML = p;

        cell = document.createElement('td');
        stdrow.append(cell);
        cell.innerHTML = data[p]
        cell.setAttribute('style', 'text-align: right;');

        cell = document.createElement('td');
        stdrow.append(cell);
        cell.innerHTML = percentage.toFixed(2);
        cell.setAttribute('style', 'text-align: right;');
    }
    sorttable.makeSortable(upostbl);
}

/** get information from ConlluEditor server:
 name of edited file
 lists of valid UPOS, XPOS, deprel
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
            if (data.validFeatures)
                featlist = data.validFeatures;
            if (data.columns)
                conllucolumns = data.columns;
            if (data.shortcuttimeout)
                shortcuttimeout = data.shortcuttimeout
            if (data.shortcuts) {
                //console.log("SHORTCUTS", data.shortcuts);
                $("#scfilename").html(data.shortcuts.filename);
                if (data.shortcuts.deplabel) {
                    shortcutsDEPL = data.shortcuts.deplabel;
                }
                if (data.shortcuts.upos) {
                    shortcutsUPOS = data.shortcuts.upos;
                }
                if (data.shortcuts.xpos) {
                    shortcutsXPOS = data.shortcuts.xpos;
                }
                if (data.shortcuts.feats) {
                    shortcutsFEATS = data.shortcuts.feats;
                }
                if (data.shortcuts.misc) {
                    shortcutsMISC = data.shortcuts.misc;
                }
                parseShortcuts();
            } else {
                showshortcuts();
            }

            data.stats;
            $(".stats").empty();
            $("#stats_filename").append(data.stats.filename);
            $("#stats_sent").append(data.stats.sentences);
            $("#stats_syntwords").append(data.stats.syntactic_words);
            $("#stats_surfwords").append(data.stats.surface_words);
            $("#stats_mwts").append(data.stats.mwts);
            $("#stats_emptywords").append(data.stats.emptywords);

            // stats for UPOS
            makeItemFreqPercent_table($("#stats_upos"), data.stats.UPOSs, data.stats.syntactic_words, "UPOS");
            // stats for Deprels
            makeItemFreqPercent_table($("#stats_deprel"), data.stats.Deprels, data.stats.syntactic_words, "Deprel");

            // stats for deprels per UPOS
            var drustbl = document.createElement("table");
            drustbl.className = "sortable";
            $("#stats_deprel_upos").append(drustbl);

            var thead = document.createElement("thead");
            drustbl.append(thead);
            var headerrow = document.createElement("tr");
            thead.append(headerrow);
            headerrow.className = "tableheader";

            var hdcell = document.createElement('th');
            headerrow.append(hdcell);
            hdcell.innerHTML = "Deprel";

            for (var p in data.stats.UPOSs) {
                var hdcell = document.createElement('th');
                headerrow.append(hdcell);
                hdcell.innerHTML = p;
            }

            var tbody = document.createElement("tbody");
            drustbl.append(tbody);

            for (var dr in data.stats.Deprels_UPOS) {
                var stdrow = document.createElement("tr");
                tbody.append(stdrow);
                stdrow.className = "everyotherrow";

                var cell = document.createElement('th');
                stdrow.append(cell);
                cell.innerHTML = dr;

                for (var upos in data.stats.UPOSs) {
                    var uposfreq = data.stats.Deprels_UPOS[dr][upos];
                    var cell = document.createElement('td');
                    stdrow.append(cell);
                    if (uposfreq != undefined) {
                        //featsline += '<td align="right">' + uposfreq + "</td>";
                         cell.innerHTML = uposfreq;
                         cell.setAttribute('style', 'text-align: right;');
                    }
                }
            }
            sorttable.makeSortable(drustbl);


            // stats for features per UPOS
            var fvustbl = document.createElement("table");
            fvustbl.className = "sortable";
            $("#stats_feats").append(fvustbl);

            var thead = document.createElement("thead");
            fvustbl.append(thead);
            var headerrow = document.createElement("tr");
            thead.append(headerrow);
            headerrow.className = "tableheader";

            var hdcell = document.createElement('th');
            headerrow.append(hdcell);
            hdcell.innerHTML = "feature-value";

            for (var p in data.stats.UPOSs) {
                var hdcell = document.createElement('th');
                headerrow.append(hdcell);
                hdcell.innerHTML = p;
            }

            var tbody = document.createElement("tbody");
            fvustbl.append(tbody);

            for (var fv in data.stats.Features) {
                var stdrow = document.createElement("tr");
                tbody.append(stdrow);
                stdrow.className = "everyotherrow";

                var cell = document.createElement('th');
                stdrow.append(cell);
                cell.innerHTML = fv;

                for (var upos in data.stats.UPOSs) {

                    var uposfreq = data.stats.Features[fv][upos];
                    var cell = document.createElement('td');
                    stdrow.append(cell);
                    if (uposfreq != undefined) {
                        //featsline += '<td align="right">' + uposfreq + "</td>";
                        cell.innerHTML = uposfreq;
                        cell.setAttribute('style', 'text-align: right;');
                    }
                }
            }
            sorttable.makeSortable(fvustbl);

            $('#filename').empty();
            $('#filename').append(data.filename);
            if (data.reinit) {
                $('#mode').append("[browse mode only]");
                $('#modifier').prop('disabled', true);
                $('.editmode').hide();
            } else {
                $('.editmode').show();
            }

            if (data.saveafter && (data.saveafter > 1 || data.saveafter == -1)) {
                $('#save').show();
            } else {
                $('#save').hide();
            }

            // set version number to logo (shown if mouse hovers on the logo)
            //$('#logo').attr("title", data.version);
            $('#ce_version').text(data.version);

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
            $(function () {
                $("#cdeprel2").autocomplete({
                    source: deprellist,
                    position: {my: "left top", at: "left bottom"}
                });
            });

            // inspired by https://jsfiddle.net/Twisty/yfdjyq79/
            $(function () {
                function split(val) {
                    return val.split("\n");
                }

                function extractLast(term) {
                    return split(term).pop();
                }

                $("#cfeats")
                        .on("keydown", function (event) {
                            if (event.keyCode === $.ui.keyCode.TAB && $(this).autocomplete("instance").menu.active) {
                                event.preventDefault();
                            }
                        })
                        .autocomplete({
                            minLength: 1, // min length to type before autocomplete kicks in
                            source: function (request, response) {
                                // delegate back to autocomplete, but extract the last term
                                //console.log("aaa", request, response);
                                response($.ui.autocomplete.filter(featlist, extractLast(request.term)));
                            },
                            focus: function () {
                                // prevent value inserted on focus
                                return false;
                            },
                            select: function (event, ui) {
                                //console.log("bbb", this.value);
                                var terms = split(this.value);
                                // remove the current input
                                terms.pop();
                                // add the selected item
                                terms.push(ui.item.value);
                                // add placeholder to get the comma-and-space at the end
                                terms.push("");
                                this.value = terms.join("\r\n");
                                return false;
                            }
                        });
            });

            $(function () {
                function split(val) {
                    return val.split("\n");
                }

                function extractLast(term) {
                    return split(term).pop();
                }
                // use class here to make MISC-autocompletion work in editMWT and wordedit
                $(".classmisc") //$("#cmisc")
                        .on("keydown", function (event) {
                            if (event.keyCode === $.ui.keyCode.TAB && $(this).autocomplete("instance").menu.active) {
                                event.preventDefault();
                            }
                        })
                        .autocomplete({
                            minLength: 1, // min length to type before autocomplete kicks in
                            position: {my: "left top", at: "left bottom-25%"},
                            source: function (request, response) {
                                // delegate back to autocomplete, but extract the last term
                                //console.log("aaa", request, response);
                                response($.ui.autocomplete.filter(misclist, extractLast(request.term)));
                            },
                            focus: function () {
                                // prevent value inserted on focus
                                return false;
                            },
                            select: function (event, ui) {
                                //console.log("bbb", this.value);
                                var terms = split(this.value);
                                // remove the current input
                                terms.pop();
                                // add the selected item
                                terms.push(ui.item.value);
                                // add placeholder to get the comma-and-space at the end
                                terms.push("");
                                this.value = terms.join("");
                                return false;
                            }
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
    $('#widthinfo').hide();
    $('#undo').prop('disabled', true);
    $('#redo').prop('disabled', true);
    $('#save').prop('disabled', true);
    //$('#save').hide();
}


//var more = 1; // 1 normal search, 2: search and replace, 3: subtree search, 4: grewmatchsearch, 0: hide all
//var lastmore = 1; // to get back to the correct search mode after shortcuts have been displayed


function switchSearch(on) {
    if (on) {
        $(".search").show();
        $('body').css("margin-top", "280px");
    } else {
        $(".search").hide();
        if (!showshortcathelp)
            $('body').css("margin-top", "150px"); // header is smaller, decrease body margin
    }
}

function switchSearchReplace(on) {
    if (on) {
        //$("#act_subtree").text("hide search & replace");
        $("#searchandreplace").show();
        $('body').css("margin-top", "300px");
    } else {
        //$("#act_subtree").text("show search & replace");
        $("#searchandreplace").hide();
        $('body').css("margin-top", "280px");
    }
}

function switchSubtree(on) {
    if (on) {
        $("#subtreesearch").show();
        $('body').css("margin-top", "300px");
    } else {
        $("#subtreesearch").hide();
        $('body').css("margin-top", "280px");
    }
}

function switchGrewmatchReplace(on) {
    if (on) {
        $("#grewmatchsearchandreplace").show();
        $('body').css("margin-top", "300px");
    } else {
        $("#grewmatchsearchandreplace").hide();
        $('body').css("margin-top", "280px");
    }
}

// when adding new search mode, change also more = 4 in ToggleShortcutHelp()!
//function ToggleSearch() {
//    if (more == 1) {
//        // go to mode 2: show S&R
//        more = 2;
//        // is on
//        switchSearch(false);
//        switchSearchReplace(true);
//        switchSubtree(false);
//        switchGrewmatchReplace(false);
//
//        if (showshortcathelp) {
//            switchSCHelp(false);
//        }
//
//    } else if (more == 2) {
//        // go to mode ": show Subtrees
//        more = 3;
//        // in mode key '?' does not toogle shortcuts
//        switchSearch(false);
//        switchSearchReplace(false);
//        switchSubtree(true);
//        switchGrewmatchReplace(false);
//
//        if (showshortcathelp) {
//            switchSCHelp(false);
//        }
//    } else if (more == 3) {
//        // go to mode ": show grewmatchsearch
//        more = 4;
//        // in mode key '?' does not toogle shortcuts
//        switchSearch(false);
//        switchSearchReplace(false);
//        switchSubtree(false);
//        switchGrewmatchReplace(true);
//
//        if (showshortcathelp) {
//            switchSCHelp(false);
//        }
//    } else if (more == 4) {
//        // go to mode 0, hide everything
//        more = 0;
//        switchSearch(false);
//        switchSearchReplace(false);
//        switchSubtree(false);
//        switchGrewmatchReplace(false);
//
//    } else {
//        // in mode 0 go to standard mode
//        more = 1;
//        switchSearch(true);
//        switchSearchReplace(false);
//        switchSubtree(false);
//        switchGrewmatchReplace(false);
//
//        if (showshortcathelp) {
//            // switch off
//            //ToggleShortcutHelp();
//            switchSCHelp(false);
//        }
//    }
//    //console.log("SEARCH", more);
//}

var showshortcathelp = false;


function switchSCHelp(on) {
    if (on) {
        $("#shortcuthelp").show();
        $('body').css("margin-top", "290px");
        showshortcathelp = true;
    } else {
        $("#shortcuthelp").hide();
       // if (!more)
        //    $('body').css("margin-top", "150px"); // header is smaller, decrease body margin
        showshortcathelp = false;
    }
}

function ToggleShortcutHelp() {
    //console.log("SC", showshortcathelp, more, lastmore);
    if (showshortcathelp) {
        // hide short cut help
        switchSCHelp(false);
        $("#searchmode").click();
    } else {
        // show short cut help (and hide Search)
        switchSCHelp(true);
        switchSearch(false);
        switchSearchReplace(false);
        switchSubtree(false);
        switchGrewmatchReplace(false);
    }
}


// default shortcuts (overriden by from configuration file)
var shortcutsUPOS = {
    /*   "N": "NOUN",
     "A": "ADV",
     ...*/
};

var shortcutsDEPL = {
    /*   "s": "nsubj",
     "u": "nummod",*/
};

var shortcutsXPOS = {// no point defining language specific xpos here.
    //"N": ["NN", "NOUN"], // XPOS modifies also upos
    //"E": ["NNP", "PROPN"],
    //"W": ["VBZ"] // xpos keeps upos unchanged
};

var shortcutsFEATS = {
};


var shortcutsMISC = {
};


var longestshortcut = 1; // longest shortcut key (in order to know when to top piling key strokes :-)

/** run by getServerInfo() when no shortcuts are provided by server.
 Reads defaults from gui/shortcut.json and updates help page.
 */
function showshortcuts() {
    //console.log("load DEFAULT shortcuts");
    $.getJSON("shortcuts.json", function (json) {
        // if no error, we override defaults
        shortcutsUPOS = json.upos;
        shortcutsXPOS = json.xpos;
        shortcutsDEPL = json.deplabel;
        shortcutsFEATS = json.feats;
        shortcutsMISC = json.misc;
        $("#scfilename").html("gui/shortcuts.json");
        parseShortcuts();
    });
}

/** read json from shortcutsUPOS and update Help Modal */
function parseShortcuts() {
    var sc_uposString = "";
    var sc_xposString = "";
    var sc_deplString = "";
    var sc_featsString = "";
    var sc_miscString = "";


    $("#shortcuttableUPOS").empty(); // clear default values
    $("#uposshortcuts").empty();
    $("#upostr").hide();
    if (Object.keys(shortcutsUPOS).length > 0) {
        $("#shortcuttableUPOS").append("<tr><th>key</th> <th>set UPOS to</th></tr>"); // add header
        for (var p in shortcutsUPOS) {
            longestshortcut = Math.max(longestshortcut, p.length);
            $("#shortcuttableUPOS").append("<tr><td>" + p + "</td> <td>" + shortcutsUPOS[p] + "</td></tr>");
            //sc_uposString += '<span class="sckey">' + p + "=" + shortcutsUPOS[p] + "</span>&nbsp;&nbsp;";
            sc_uposString += '<span class="sckey">' + p + '</span>=<span class="scval">' + shortcutsUPOS[p] + "</span>&nbsp; ";
        }
        $("#upostr").show();
        $("#uposshortcuts").append(sc_uposString);
    }

    $("#shortcuttableDEPL").empty();
    $("#deplshortcuts").empty();
    $("#depreltr").hide();
    if (Object.keys(shortcutsDEPL).length > 0) {
        $("#shortcuttableDEPL").append("<tr><th>key</th> <th>set deplabel to</th></tr>"); // add header
        for (var p in shortcutsDEPL) {
            longestshortcut = Math.max(longestshortcut, p.length);
            $("#shortcuttableDEPL").append("<tr><td>" + p + "</td> <td>" + shortcutsDEPL[p] + "</td></tr>");
            sc_deplString += '<span class="sckey">' + p + '</span>=<span class="scval">' + shortcutsDEPL[p] + "</span>&nbsp; ";
        }
        $("#depreltr").show();
        $("#deplshortcuts").append(sc_deplString);
    }


    $("#xposshortcuts").empty();
    $("#xpostr").hide();
    $("#shortcuttableXPOS").empty();
    if (Object.keys(shortcutsXPOS).length > 0) {
        $("#shortcuttableXPOS").append("<tr><th>key</th> <th>set XPOS to</th> <th>and UPOS to</th></tr>"); // add header in help page
        for (var p in shortcutsXPOS) {
            longestshortcut = Math.max(longestshortcut, p.length);
            $("#shortcuttableXPOS").append("<tr><td>" + p + "</td> <td>"
                    + shortcutsXPOS[p][0] + "</td> <td>"
                    + shortcutsXPOS[p][1] + "</td></tr>");
            sc_xposString += '<span class="sckey">' + p + '</span>=<span class="scval">' + shortcutsXPOS[p][0] + "/" + shortcutsXPOS[p][1] + "</span>&nbsp; ";
        }
        $("#xpostr").show();
        $("#xposshortcuts").append(sc_xposString);
    }


    $("#shortcuttableFEATS").empty();
    $("#featsshortcuts").empty();
    $("#featstr").hide();
    if (Object.keys(shortcutsFEATS).length > 0) {
        $("#shortcuttableFEATS").append("<tr><th>key</th> <th>set feature</th></tr>"); // add header
        for (var p in shortcutsFEATS) {
            longestshortcut = Math.max(longestshortcut, p.length);
            $("#shortcuttableFEATS").append("<tr><td>" + p + "</td> <td>" + shortcutsFEATS[p] + "</td></tr>");
            sc_featsString += '<span class="sckey">' + p + '</span>=<span class="scval">' + shortcutsFEATS[p] + "</span>&nbsp; ";
        }
        $("#featstr").show();
        $("#featsshortcuts").append(sc_featsString);
    }

    $("#shortcuttableMISC").empty();
    $("#miscshortcuts").empty();
    $("#miscstr").hide();
    if (Object.keys(shortcutsMISC).length > 0) {
        $("#shortcuttableMISC").append("<tr><th>key</th> <th>set MISC</th></tr>"); // add header
        for (var p in shortcutsMISC) {
            longestshortcut = Math.max(longestshortcut, p.length);
            $("#shortcuttableMISC").append("<tr><td>" + p + "</td> <td>" + shortcutsMISC[p] + "</td></tr>");
            sc_miscString += '<span class="sckey">' + p + '</span>=<span class="scval">' + shortcutsMISC[p] + "</span>&nbsp; ";
        }
        $("#miscstr").show();
        $("#miscshortcuts").append(sc_miscString);
    }
}

// in order to have word "boxes" as wide as needed, we stock here the width needed for each
// word (taking the word form) as well as the x-position of each word in the graph
var wordlengths = {}; // position (not ID !!): width in px
var wordpositions = {}; // position: x-position in graph
var wordpos = {}; // id: position
var rightmostwordpos = 0; // position of the last word (needed to draw R2L trees)
var hordist = 6; // horizontal distance beween words (if auto-adpat is active)


/* get width needed by a word box (form, xpos, features, misc*/
function getWordLength(cword) {
    var canvas = document.createElement('canvas');
    var ctx = canvas.getContext("2d");
    //ctx.fontSize = "12px"; //document.getElementsByClassName("wordnode").style.fontSize;
    //ctx.fontFamily = "Lato"; //document.getElementsByClassName("wordnode").style.fontFamily;
    ctx.font = "17px Lato"; // TODO get from current Font !!
    wlen = ctx.measureText(cword.form).width;
    wlen = Math.max(wlen, ctx.measureText(cword.xpos).width); // sometimes XPOS are long

    ctx.font = "11px Lato";
    // take into accound Feature values width
    if (showfeats && cword.feats != undefined) { // display misc column info if active
        for (var f in cword.feats) {
            //console.log("ggg", cword.feats[f].val, wlen, 2*ctx.measureText(cword.feats[f].val).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.feats[f].name).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.feats[f].val).width);
            //console.log("hhh", wlen);
        }
    }

    // take into accound MISC values width
    if (showmisc && cword.misc != undefined) { // display misc column info if active
        for (var f in cword.misc) {
            //console.log("ggg", cword.misc[f].val, wlen, 2*ctx.measureText(cword.misc[f].val).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.misc[f].name).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.misc[f].val).width);
            //console.log("hhh", wlen);
        }
    }
    wordlengths[cword.position] = Math.max(50, wlen); // get 50px minimal size
    //console.log("iii", wlen);
    wordpos[cword.id] = cword.position;
    return wlen;
}

/* get word of all words of all trees of the sentence (all trees in case of multiple trees) */
function getAllWordLengths(trees, maxlen) {
    wordlengths = {};
    for (var k = 0; k < trees.tree.length; ++k) {
        var item = trees.tree[k];
        maxlen = Math.max(maxlen, getWordLengthsOfTree(item, maxlen));
    }

    // calculate x-positions
    //console.log("aaa", wordlengths);
    wordpositions = {};
    rightmostwordpos = 0;
    var pos = Object.keys(wordlengths); //.sort();
    var last = 0;
    for (var i = 0; i < pos.length; ++i) {
        wordpositions[pos[i]] = last + wordlengths[pos[i]] / 2;
        last += wordlengths[pos[i]] + hordist;
        rightmostwordpos = Math.max(last, rightmostwordpos);
        //console.log("aa2", i, ids[i], last);
    }
    //console.log("bbb", wordpositions);
    return maxlen;
}

/* get word length for all words of a tree */
function getWordLengthsOfTree(item, maxlen) {
    maxlen = Math.max(maxlen, getWordLength(item, maxlen));
    if (item.children) {
        for (var i = 0; i < item.children.length; i++) {
            maxlen = Math.max(maxlen, getWordLengthsOfTree(item.children[i], maxlen));
        }
    }

    return Math.round(maxlen);
}

var conllwords = {}; // all words of current sentence
var mwts = {}; // all multiword tokens of current sentence
var clickedNodes = [];
//var unprocessedkeystrokes = []; // process multi key shortcuts
//var jumptotokendigits = []; // process keys to get the number of the token to jump to
//var token_to_be_focussed = -1; // set to 0 if "&" is hit, the following two digits focuss the token, if any of the following keys is not a digit, we quit the loop
var deprels = [];
var uposs = [];
var clickCount = 0;
var wordedit = false;
var editword_with_doubleclick = true; // in order to deactivate word-edit with double click, set to false

// capture delete key
$(window).on('keydown', function (evt) {
    //console.log("DDAEVT", evt.which, evt.keyCode, String.fromCharCode(evt.keyCode), clickedNodes);

    if ($(".modal").is(":visible")) {
        // if a model is open, we do not want to catch keypress events, since we are editing text
        return;
    }

    if (graphtype == 3) {
        // in table mode, we need all keys to edit the table cells
        return;
    }

    // a word is active
    if (clickedNodes.length == 1) {
        if (evt.which == 46) { // '.'
            //console.log("UPOS", newval);
            if (clickedNodes[0].indexOf(".") !== -1) {
                // delete empty word
                sendmodifs({"cmd": "mod emptydelete " + clickedNodes[0]});
            } else {
                // delete word
                sendmodifs({"cmd": "mod delete " + clickedNodes[0]});
            }
            clickedNodes = [];
            deprels = [];
            uposs = [];
            return;
        }
    }

})

function unsetPShC() {
    //unprocessedkeystrokes = [];
    //jumptotokendigits = [];
    $("#pendingshortcuts").empty();
    $("#psc").hide();
}


// inpsired by https://stackoverflow.com/questions/123999/how-can-i-tell-if-a-dom-element-is-visible-in-the-current-viewport
function isElementInViewport(el) {

    // Special bonus for those using jQuery
    //if (typeof jQuery === "function" && el instanceof jQuery) {
    //    el = el[0];
    //}


    //console.log("EL", el[0]);
    var rect = el[0].getBoundingClientRect();
    el[0].scrollIntoView(true);
    //console.log("rrr", rect);
    // return the coordinates
    return (
            rect.top >= 0 &&
            rect.left >= 0 &&
            rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) && /* or $(window).height() */
            rect.right <= (window.innerWidth || document.documentElement.clientWidth) /* or $(window).width() */
            );
}


// process shortcuts: we catch keys hit in the editor. If a word is active, we try to apply
let timeout = null;

var  shortcutseq = "";
// Listen for keystroke events
$(window).on('keypress', function (evt) {
    //console.log("yyyy", evt, shortcutseq, shortcuttimeout);
    if ($(".modal").is(":visible")) {
        // if a model is open, we do not want to catch keypress events, since we are editing text
        unsetPShC();
        return;
    }

    else if (graphtype == 3) {
        // in table mode, we need all keys to edit the table cells
        unsetPShC();
        return;
    }

    else if ($("#subtreesearch").is(":visible") && evt.which !== 33) {
        // when editing a subtree, we need all keys to edit the table cells
        unsetPShC();
        return;
    }

    // hardwired keys
    else if (evt.which == 63) { // "?"
        unsetPShC();
        ToggleShortcutHelp();
        //} else if (evt.which == 33) { // "!"
        //    ToggleSubtree();
    } else if (evt.which == 43) { // "+"
        unsetPShC();
        sendmodifs({"cmd": "next"});
    } else if (evt.which == 45) { // "-"
        unsetPShC();
        sendmodifs({"cmd": "prec"});
    } else if (evt.which == 33) { // '!' // 61 "=" validator
        unsetPShC();
        $("#valid").click();
    } else  if (evt.which == 95) { // "_" delete all features
        sendmodifs({"cmd": "mod feat " + clickedNodes[0] + " " + "_"});
        unsetPShC();
        return;
    } else  if (evt.which == 47) { // "/" delete all misc key-values
        sendmodifs({"cmd": "mod misc " + clickedNodes[0] + " " + "_"});
        unsetPShC();
        return;
    } else if (clickedNodes.length == 0) {
        unsetPShC();
        return;
    } else {

        // Clear the timeout if it has already been set.
        // This will prevent the previous task from executing
        // if it has been less than <MILLISECONDS>
        clearTimeout(timeout);
        shortcutseq += String.fromCharCode(evt.which);
        $("#pendingshortcuts").text(shortcutseq);
        // Make a new timeout set to go off in 1000ms (1 second)
        timeout = setTimeout(function () {
            // process sequence
            //    console.log('Input Value:', shortcutseq);

            var newval = shortcutsUPOS[shortcutseq];
            if (newval != undefined) {
                //console.log("UPOS", newval);
                sendmodifs({"cmd": "mod upos " + clickedNodes[0] + " " + newval});
                shortcutseq = "";
                deprels = [];
                uposs = [];
                clickedNodes = [];
                unsetPShC();
                return;
            }

            newval = shortcutsDEPL[shortcutseq];
            if (newval != undefined) {
                //console.log("DEPL", newval);
                sendmodifs({"cmd": "mod deprel " + clickedNodes[0] + " " + newval});
                shortcutseq = "";
                deprels = [];
                uposs = [];
                clickedNodes = [];
                unsetPShC();
                return;
            }

            newval = shortcutsFEATS[shortcutseq];
            if (newval != undefined) {
                sendmodifs({"cmd": "mod addfeat " + clickedNodes[0] + " " + newval});
                shortcutseq = "";
                deprels = [];
                uposs = [];
                clickedNodes = [];
                unsetPShC();
                return;
            }

            newval = shortcutsMISC[shortcutseq];
            if (newval != undefined) {
                sendmodifs({"cmd": "mod addmisc " + clickedNodes[0] + " " + newval});
                shortcutseq = "";
                deprels = [];
                uposs = [];
                clickedNodes = [];
                unsetPShC();
                return;
            }

            newval = shortcutsXPOS[shortcutseq];
            if (newval != undefined) {
                //console.log("XPOS", newval, newval[0]);
                if (newval.length > 1) {
                    // change UPOS and XPOS
                    sendmodifs({"cmd": "mod pos " + clickedNodes[0] + " " + newval[1] + " " + newval[0]});
                } else {
                    // change only XPOS
                    sendmodifs({"cmd": "mod xpos " + clickedNodes[0] + " " + newval[0]});
                }

                shortcutseq = "";
                deprels = [];
                uposs = [];
                clickedNodes = [];
                unsetPShC();
                return;
            }

            if (shortcutseq[0] == "&" && shortcutseq.length > 1) {
                var gotonode = parseInt(shortcutseq.substring(1), 10);
                if (gotonode > 0) {
                    //console.log("EZEZE", gotonode);
                    var tokid = "#id1_" + gotonode;
                    try {
                        $(tokid)[0].scrollIntoView({block: "center", inline: "center"});
                    } catch (TypeError) {
                        console.log("Invalid node number (above mex token length)", gotonode);
                    }
                } else {
                    console.log("Invalid node number", shortcutseq.substring(1));
                }
                shortcutseq = "";
                //deprels = [];
                //uposs = [];
                //clickedNodes = [];
                unsetPShC();
                return;
            }

            shortcutseq = "";
            unsetPShC();
            return;

        }, shortcuttimeout); //700);
    }
});

// OLD
// process shortcuts: we catch keys hit in the editor. If a word is active, we try to apply
//$(window).on('keypressQQQ', function (evt) {
//    return;
//    //console.log("AEVT", evt.which, evt.keyCode, String.fromCharCode(evt.keyCode), clickedNodes);
//    //console.log("kk", evt.which, unprocessedkeystrokes);
//
//    if ($(".modal").is(":visible")) {
//        // if a model is open, we do not want to catch keypress events, since we are editing text
//        unsetPShC();
//        return;
//    }
//
//    if (graphtype == 3) {
//        // in table mode, we need all keys to edit the table cells
//        unsetPShC();
//        return;
//    }
//
//    if ($("#subtreesearch").is(":visible") && evt.which !== 33) {
//        // when editing a subtree, we need all keys to edit the table cells
//        unsetPShC();
//        return;
//    }
//
//    // inputting a token number to center in viewport
//    if (token_to_be_focussed > -1) {
//        if (evt.which >= 48 && evt.which <= 57) { // 0 - 9
//            if (token_to_be_focussed == 0) {
//                jumptotokendigits.push(String.fromCharCode(evt.which));
//                $("#pendingshortcuts").text("&" + jumptotokendigits.join(""));
//                token_to_be_focussed++;
//                return;
//            } else if (token_to_be_focussed == 1) {
//                jumptotokendigits.push(String.fromCharCode(evt.which));
//                //} else {
//                // we have seen two digits
//                var tokid = "#id1_" + parseInt(jumptotokendigits.join(""));
//                $(tokid)[0].scrollIntoView({block: "center", inline: "center"});
//                token_to_be_focussed = -1;
//                unsetPShC();
//            }
//
//        } else {
//            // no integer, stop this and to not center token in viewport
//            token_to_be_focussed = -1;
//            unsetPShC();
//        }
//    }
//
//    // hardwired keys
//    else if (evt.which == 63) { // "?"
//        unsetPShC();
//        ToggleShortcutHelp();
//        //} else if (evt.which == 33) { // "!"
//        //    ToggleSubtree();
//    } else if (evt.which == 43) { // "+"
//        unsetPShC();
//        sendmodifs({"cmd": "next"});
//    } else if (evt.which == 45) { // "-"
//        unsetPShC();
//        sendmodifs({"cmd": "prec"});
//    } else if (evt.which == 61) { // "=" validator
//        unsetPShC();
//        $("#valid").click();
//
//    } else if (evt.which == 38) { // "&" followed by three digits: put word with ID into viewport
//        unsetPShC();
//        // move viewport to make token visible
//        token_to_be_focussed = 0;
//        $("#pendingshortcuts").text("&");
//        $("#psc").show();
//        return;
//
//    } else if (clickedNodes.length == 1) {
//        // a word is active
//        if (evt.which == 95) { // "_" delete all features
//            sendmodifs({"cmd": "mod feat " + clickedNodes[0] + " " + "_"});
//            clickedNodes = [];
//            deprels = [];
//            uposs = [];
//            unsetPShC();
//            return;
//        }
//
//        // interpret shortkeys
//        currentkey = String.fromCharCode(evt.which);
//        if (unprocessedkeystrokes.length < longestshortcut) { //== 1) {
//            currentkey = unprocessedkeystrokes.join("") + currentkey;
//        }
//
//        var newval = shortcutsUPOS[currentkey];
//        if (newval != undefined) {
//            //console.log("UPOS", newval);
//            sendmodifs({"cmd": "mod upos " + clickedNodes[0] + " " + newval});
//            clickedNodes = [];
//            deprels = [];
//            uposs = [];
//            unsetPShC();
//            return;
//        }
//
//        newval = shortcutsDEPL[currentkey];
//        if (newval != undefined) {
//            //console.log("DEPL", newval);
//            sendmodifs({"cmd": "mod deprel " + clickedNodes[0] + " " + newval});
//            clickedNodes = [];
//            deprels = [];
//            uposs = [];
//            unsetPShC();
//            return;
//        }
//
//        newval = shortcutsFEATS[currentkey];
//        if (newval != undefined) {
//            sendmodifs({"cmd": "mod addfeat " + clickedNodes[0] + " " + newval});
//            clickedNodes = [];
//            deprels = [];
//            uposs = [];
//            unsetPShC();
//            return;
//        }
//
//        newval = shortcutsXPOS[currentkey];
//        if (newval != undefined) {
//            //console.log("XPOS", newval, newval[0]);
//            if (newval.length > 1) {
//                // change UPOS and XPOS
//                sendmodifs({"cmd": "mod pos " + clickedNodes[0] + " " + newval[1] + " " + newval[0]});
//            } else {
//                // change only XPOS
//                sendmodifs({"cmd": "mod xpos " + clickedNodes[0] + " " + newval[0]});
//            }
//
//            clickedNodes = [];
//            deprels = [];
//            uposs = [];
//            unsetPShC();
//            return;
//        }
//
//        // so we got here with a key which is not a shortcut. So maybe it is the first key
//        // of a multi-key shortcut?
//        if (unprocessedkeystrokes.length < longestshortcut - 1) { //(unprocessedkeystrokes.length == 0) {
//            unprocessedkeystrokes.push(String.fromCharCode(evt.which));
//            $("#pendingshortcuts").text(unprocessedkeystrokes.join(""));
//            $("#psc").show();
//        } else if (token_to_be_focussed >= 0) {
//        } else {
//            unsetPShC();
//        }
//        return;
//
//    }
//    unprocessedkeystrokes = [];
//    $("#pendingshortcuts").empty();
//});

// hovering on a word which differes from the corresponding word in the --compare file
// will show the differences
function ShowCompareErrors(v) {
    //$("#goldword").append('<td class="filename">' + $('#filename').text() + "</td>").append(v.gold);
    $("#goldword").append('<td class="compfilename">' + "gold file:" + "</td>").append(v.gold);
    $("#editedword").append('<td class="compfilename">' + "edited file:" + "</td>").append(v.edit);
}

function HideCompareErrors() {
    $(".comparediff").empty();
}

var extracols = []; // ids of extra column fields

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

//            if (evt.altKey) {
//                // editing columns > 10
//                var conllword = conllwords[id[1]];
//                console.log("zzzz", conllword.col11);
//            } else
            if (evt.ctrlKey || wordedit) {
                // editing a word (form, lemma, UPOS, XPOS, features, MISC)
                wordedit = false;
                //if (evt.shiftKey) {
                var conllword = conllwords[id[1]];
                $("#cid").text(conllword.id);
                $("#cform").val(conllword.form);
                $("#clemma").val(conllword.lemma);
                $("#cupos").val(conllword.upos);
                $("#cxpos").val(conllword.xpos);
                $("#cdeprel2").val(conllword.deprel);
                extracols = [];
                // delete eventually remaining table rows for extra columns
                var ecs = document.getElementsByClassName("extracoltr");
                while (ecs.length > 0) {
                    ecs[0].remove();
                }
                if (conllword.nonstandard != undefined) {
                    var table = document.getElementById('wordedittable');
                    for (let [coltype, colval] of Object.entries(conllword.nonstandard)) {
                        var row = table.insertRow(-1);
                        row.className = "extracoltr";
                        extracols.push(coltype);
                        var cell1 = row.insertCell(0);
                        cell1.innerHTML = coltype;
                        var cell2 = row.insertCell(1);
                        cell2.innerHTML = '<div class="ui-widget"><textarea type="text" id="ct_' + coltype + '" rows="1" cols="60">' + colval + '</textarea></div>';
                    }
                }


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



                // clean all nodes (delete clocked-status)
                $(".wordnode").attr("class", "wordnode");

                // add errorclass for words which are different from gold
                if (incorrectwords.has("" + id[1])) {
                    errorclass = " compareError";
                    $('#' + target.id).attr("class", "wordnode compareError");
                }
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
        } else if (id[0] === "textpath" || id[0] === "path") { // TODO use classes ?
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
            $("#enhdeprelEdit").modal();
        } else if (id[0] == "mwe") {
            //alert("MT MWE: " + id + " " + target);
            $("#currentMWTfrom").val(id[1]);
            $("#currentMWTto").val(id[2]);
            $("#currentMWTform").val(id[3]);

            var mc = "";

            if (mwts[id[1]].misc != undefined) {
                for (e = 0; e < mwts[id[1]].misc.length; ++e) {
                    var mch = mwts[id[1]].misc[e];
                    if (e > 0)
                        mc += "#"; //\n";
                    mc += mch.name + "=" + mch.val;
                }
            } else
                mc = "_";
            $("#currentMWTmisc").val(mc)


            $("#editMWT").modal();

            $("#mods").val("");

            //$(".wordnode").attr("class", "wordnode");
            unhighlight();
            clickedNodes = [];
            deprels = [];
            uposs = [];

        } else {
            //alert("MT " + target.id + " == " + target);
            $("#mods").val("");
            //$(".wordnode").attr("class", "wordnode");
            unhighlight();
            clickedNodes = [];
            deprels = [];
            uposs = [];
        }
    } else {
        $("#mods").val("");
        unhighlight();
        /*$(".wordnode").each(function( index ) {
         // get all word rectangles, and delete highlighting (boxhighlight) class but keep compareError class (in case of compare mode)
         var currentclasses = $(this).attr("class");
         currentclasses = currentclasses.replace("boxhighlight", "");
         console.log( index + "A::: " + $(this).attr("class") );
         $(this).attr("class", currentclasses)
         console.log( index + "B::: " + $(this).attr("class") );
         });*/
        //$(".wordnode").attr("class", "wordnode");

        clickedNodes = [];
        deprels = [];
        uposs = [];
    }
    //console.log("aaa " + JSON.stringify(clickedNodes));
}

function unhighlight() {
    // delete boxhighlight class from all word rectangles
    $(".wordnode").each(function (index) {
        // get all word rectangles, and delete highlighting (boxhighlight) class but keep compareError class (in case of compare mode)
        var currentclasses = $(this).attr("class");
        currentclasses = currentclasses.replace("boxhighlight", "");
        $(this).attr("class", currentclasses)
    });
}


//var flatgraph = false;
var graphtype = 1; // 1: tree, 2: hedge, 3: table
var showfeats = false;
var showmisc = false;
var showr2l = false;
var autoadaptwidth = true;
var backwards = false;
var show_basic_in_enhanced = false; // if true we display enhanced deps which are identical two basic deps
var editing_enhanced = false;

// transliterations from words
var translit_words = [];
var missingtranslits = false;

// concatenated forms (in a correct file this equals the contents of the "# text" field
var concatenatedforms = "";

// position of highlighted things (search result)
var highlightX = 0;
var highlightY = 0;
/**
 *  afficher une phrase avec ses relations sémantique
 * @param {type} item json retourné par le serveur avec "relation" et "tree" liste avec un arbre ou plusieurs arbres partiels
 * @returns {undefined}
 */
function formatPhrase(item) {
    //console.log("eee " + item.message);
    if (autoadaptwidth) {
        var maxlen = getAllWordLengths(item, 0);
        //console.log("MAXLEN " + maxlen);
    }



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
        $("#changespendingsave").html(item.changes)
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

        // metadata edit
        $("#csent_id").val("");
        $("#ctext").val("");
        $("#cnewdoc").val("");
        $("#cnewpar").val("");
        $("#ctranslit").val("");
        $("#ctranslations").val("");
        // add new info to metadat edit
        $("#csent_id").val(item.sent_id);
        if (item.newdoc)
            $("#cnewdoc").val(item.newdoc);
        if (item.newpar)
            $("#cnewpar").val(item.newpar);
        if (item.text)
            $("#ctext").val(item.text);
        if (item.translit)
            $("#ctranslit").val(item.translit);

        concatenatedforms = item.sentence;

        if (item.translit_words) {
            translit_words = item.translit_words;
            missingtranslits = item.translit_missing;
        }
        if (item.translations) {
            var text = "";
            for (lg in item.translations) {
                text += lg + ": " + item.translations[lg] + "\n";
            }
            $("#ctranslations").val(text);
        }

        // display sentence text
        $("#sentid").val(item.sentenceid + 1);
        $("#currentsent").append(item.sentenceid + 1);
        if (item.sent_id) {
            $("#cursentid").append(" (sent_id: ").append(item.sent_id).append(") ");
        }
        $("#total").append(item.maxsentence);
        if (item.text)
            $("#titre").append(item.text);
        else
            $("#titre").append(item.sentence);


        if (item.errors != undefined) {
            if (item.errors.heads)
                $("#errors").append("|" + item.errors.heads + " roots");
            if (item.errors.badroots)
                $("#errors").append("|" + item.errors.badroots + " deprel 'root' used for non-root");
            if (item.errors.invalidUPOS)
                $("#errors").append("|" + item.errors.invalidUPOS + " invalid UPOS");
            if (item.errors.invalidXPOS)
                $("#errors").append("|" + item.errors.invalidXPOS + " invalid XPOS");
            if (item.errors.invalidDeprels)
                $("#errors").append("|" + item.errors.invalidDeprels + " invalid Deprels");
            if (item.errors.invalidFeatures)
                $("#errors").append("|" + item.errors.invalidFeatures + " invalid Features");
            if (item.errors.incoherenttext) {
                var text = item.errors.incoherenttext.text;
                var concat = item.errors.incoherenttext.forms;
                var pos = item.errors.incoherenttext.differs;
                var text_line = text.substring(0, pos)
                                + '<span class="errorhl">' + text.substring(pos, pos+1)
                                + "</span>" + text.substring(pos+1);
                var forms_line = concat.substring(0, pos)
                                + '<span class="errorhl">' + concat.substring(pos, pos+1)
                                + "</span>" + concat.substring(pos+1);

                $("#errors").append("|incoherent \"# text\" and forms: «" + text_line + "» ≠ «" + forms_line) + "»";
            }
            $("#errors").append("|");
        }
        $("#sentencetext").show();

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
        if (graphtype != 3)
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
        if (graphtype == 2 /*flatgraph*/) {
            if (item.comparisontree) {
                // we display the gold tree (given with --compare) in gray underneath the edited tree
                $("#scores").empty();
                $("#scores").append("(evaluation Lemma: " + item.Lemma);
                $("#scores").append(", Features: " + item.Features);
                $("#scores").append(", UPOS: " + item.UPOS);
                $("#scores").append(", XPOS: " + item.XPOS);
                $("#scores").append(", LAS: " + item.LAS + ")");
                drawDepFlat(svg, item.comparisontree, sentencelength, use_deprel_as_type, 1, null);

                if (item.differs) {
                    //console.log("zz", item.differs);
                    //for (i = 0; i < item.differs.length; i++) {
                    //    incorrectwords.add(item.differs[i]);
                    //}
                    incorrectwords = item.differs;
                }
            }
            drawDepFlat(svg, item.tree, sentencelength, use_deprel_as_type, 0, incorrectwords);
        } else if (graphtype == 3) {
            drawTable($("#arbre"), item.tree);
        } else {
            //console.log(item.comparisontree);
            incorrectwords = new Set(); // put incorrect word in comparison mode
            if (item.comparisontree) {
                $("#scores").empty();
                $("#scores").append("(evaluation: Lemma: " + item.Lemma);
                $("#scores").append(", Features: " + item.Features);
                $("#scores").append(", UPOS: " + item.UPOS);
                $("#scores").append(", XPOS: " + item.XPOS);
                $("#scores").append(", LAS: " + item.LAS + ")");
                drawDepTree(svg, item.comparisontree, sentencelength, use_deprel_as_type, 1, null);
                if (item.differs) {
                    //console.log("zz", item.differs);
                    //for (i = 0; i < item.differs.length; i++) {
                    //    incorrectwords.add(item.differs[i]);
                    //}
                    incorrectwords = item.differs;
                }
            }
            drawDepTree(svg, item.tree, sentencelength, use_deprel_as_type, 0, incorrectwords);
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

        $("#changespendingsave").html(item.changes)

        // make a table wordid: word to access data easier for editing
        conllwords = {};

        for (i = 0; i < item.tree.length; ++i) {
            var head = item.tree[i];
            getConllWords(conllwords, head);
        }

        // create similar table for MWE
        mwts = {};
        for (wid in conllwords) {
            cw = conllwords[wid];
            if (cw.mwe != undefined) {
                mwts[wid] = cw.mwe;
            }
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


function getSubtree(commands) {
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

            if (data.error != undefined) {
                //alert(data.error);
                /* show error message */
                $("#errormessagefield").text(data.error);
                $("#errorMessage").modal();
            } else {
                $("#editsubtree").val(data.ok);
                //formatPhrase(data);
                //olddata = data;
            }
        },
        error: function (data) {
            //console.log("ERREUR " + data);
            alert("An error occurred (is the ConlluEditor server running?)" + data);
        }
    });
}

var olddata = undefined;
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

            if (data.error != undefined) {
                //alert(data.error);
                /* show error message */
                $("#errormessagefield").text(data.error);
                $("#errorMessage").modal();
                if (olddata != undefined) {
                    formatPhrase(olddata);
                }
            } else if (data.ok != undefined) {
                // save file on server OK
                $("#errorMsgTitle").text("OK");
                $("#errormessagefield").text(data.ok);
                $("#errorMessage").modal();
                if (olddata != undefined) {
                    olddata.changes = 0;
                    formatPhrase(olddata);
                }
                //$("#changespendingsave").html(0);

            } else {
                formatPhrase(data);
                olddata = data;
            }
        },
        error: function (data) {
            //console.log("ERREUR " + data);
            alert("An error occurred (is the ConlluEditor server running?)" + data);
        }
    });
}



$(document).ready(function () {
    choosePort();
    getServerInfo();
    // Enable the tab character onkeypress (onkeydown) inside textarea...
    // ... for a textarea that has an `id="editsubtree"`
    enableTab("editsubtree");
    $("#sentencetext").hide();

    /* start comment edit function */
    $("#commentfield").click(function () {
        $("#commenttext").val($("#commentfield").text());
        //$("#commentedit").dialog("open");
        $("#commentEdit").modal()
    });


    $("#editcommentbutton").click(function () {
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


    /* edit sent-id, text_LG, etc */
    $("#editmetadata").click(function () {
        $("#metadataEdit").modal();
    });

    $('#savemetadata').click(function () {
        object = {"newdoc": $("#cnewdoc").val(),
            "newpar": $("#cnewpar").val(),
            "sent_id": $("#csent_id").val(),
            "translit": $("#ctranslit").val(),
            "translations": $("#ctranslations").val(),
            "text": $("#ctext").val(),
        }
        sendmodifs({"cmd": "mod editmetadata " + JSON.stringify(object)});
        $("#metadataEdit").modal("hide");
    });

    $("#inittranslit").click(function () {
        //console.log("IIII ", translit_words.join(" "));
        $("#ctranslit").val(translit_words.join(" "));

    });

    $("#inittext").click(function () {
        // create # text from forms
        //console.log("IIII ", translit_words.join(" "));
        $("#ctext").val(concatenatedforms);

    });
    /* delete clicked MWT form */
    $('#editMWtoken').click(function () {
        misc = $("#currentMWTmisc").val(); //.replace(/\n+/, ",");
        sendmodifs({"cmd": "mod editmwt "
                    + $("#currentMWTfrom").val()
                    + " " + $("#currentMWTto").val()
                    + " " + $("#currentMWTform").val()
                    + " " + misc});
        $('#editMWT').modal('hide');
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
        if (conllword.deprel != $("#cdeprel2").val()) {
            sendmodifs({"cmd": "mod deprel " + conllword.id + " " + $("#cdeprel2").val()});
        }
        for (i = 0; i < extracols.length; i++) {
            //curval = document.getElementById("ct_" + extracols[i]).textContent.replace("[ \n]+", "\|");
            curval = document.getElementById("ct_" + extracols[i]).value.replace(/[ \n]+/, "\|");
            origval = conllword.nonstandard[extracols[i]];
            //console.log("DDDDD", curval, origval);

            if (curval != origval) {
                sendmodifs({"cmd": "mod extracol " + conllword.id + " " + extracols[i] + " " + curval});
            }
        }

        // delete table rows for additional columns
        var ecs = document.getElementsByClassName("extracoltr");
        while (ecs.length > 0) {
            ecs[0].remove();
        }

        // TODO: well, can be improved too
        var fs = "";
        if (conllword.feats != undefined) {
            for (e = 0; e < conllword.feats.length; ++e) {
                var feat = conllword.feats[e];
                if (e > 0)
                    fs += "|";
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
        conllword = conllwords[$("#cdep").text()];

        if (conllword.deprel != $("#cdeprel").val()) {
            sendmodifs({"cmd": "mod " + $("#cdep").text() + " " + $("#chead").text() + " " + $("#cdeprel").val()});
        }
        $('#deprelEdit').modal('hide');
    });

    // add/modify enhanced dep relation
    $('#savedeprelen').click(function () {
        conllword = conllwords[$("#cdepen").text()];
        if (/*flatgraph*/ graphtype == 2 && editing_enhanced) {
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
        if (/*flatgraph*/ graphtype == 2 /* && editing_enhanced */) {
            sendmodifs({"cmd": "mod ed del " + $("#cdepen").text() + " " + $("#cheaden").text()});
        }
        $('#enhdeprelEdit').modal('hide');
    });


    // start show latex
    $("#latex").click(function () {
        //$('#showraw').dialog({title: 'LaTeX code'});
        getRaw("latex", '<span class="latex">L<sup>a</sup>T<sub>e</sub>X</span>');
        //$("#showraw").dialog("open");
        $("#showRawModal").modal();

    });

    // start show conllu
    $("#conllu").click(function () {
        //$('#showraw').dialog({title: "CoNLL-U format"});
        getRaw("conllu", "CoNLL-U");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal();
    });

    // start show sdparse
    $("#sdparse").click(function () {
        //$('#showraw').dialog({title: "SD-Parse format"});
        getRaw("sdparse", "SD-Parse");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal();
    });

    // start show spacy's json
    $("#json").click(function () {
        //$('#showraw').dialog({title: "SD-Parse format"});
        getRaw("spacyjson", "JSON (Spacy)");
        $("#showRawModal").modal();
    });


    // run validation
    $("#valid").click(function () {
        getRaw("validation", "validation");
        //$("#showraw").dialog("open");
        $("#showRawModal").modal();

    });

    $("#searchmode").click(function () {
        if (this.id === "searchmode") {
            console.log("zzz", $(this).val());
            if ($(this).val() === "complex") {
                switchSearch(false);
                switchSearchReplace(true);
                switchSubtree(false);
                switchGrewmatchReplace(false);
                $('body').css("margin-top", "200px"); // header is smaller, decrease body margin
                if (showshortcathelp) {
                    switchSCHelp(false);
                }
            }
            else if ($(this).val() === "subtrees") {
                // in mode key '?' does not toogle shortcuts
                switchSearch(false);
                switchSearchReplace(false);
                switchSubtree(true);
                switchGrewmatchReplace(false);
                if (showshortcathelp) {
                    switchSCHelp(false);
                }
            }
            else if ($(this).val() === "grew") {
                // in mode key '?' does not toogle shortcuts
                switchSearch(false);
                switchSearchReplace(false);
                switchSubtree(false);
                switchGrewmatchReplace(true);
                $('body').css("margin-top", "160px"); // header is smaller, decrease body margin
                if (showshortcathelp) {
                    switchSCHelp(false);
                }
            }
            else if ($(this).val() === "simple") {
                switchSearch(true);
                switchSearchReplace(false);
                switchSubtree(false);
                switchGrewmatchReplace(false);
                $('body').css("margin-top", "260px"); // header is smaller, decrease body margin
                if (showshortcathelp) {
                    switchSCHelp(false);
                }
            }
            else {
                // hide search panels
                switchSearch(false);
                switchSearchReplace(false);
                switchSubtree(false);
                switchGrewmatchReplace(false);
                $('body').css("margin-top", "120px"); // header is smaller, decrease body margin
            }
        }

    });

    $("#flat3").click(function () {
        if (this.id === "flat3") {
            //console.log("zzz", $(this).val());

            if ($(this).val() === "tree") {
                //$(this).addClass('active');
                $("#bie").hide();
                $("#edit_ed").hide();
                //flatgraph = false;
                graphtype = 1;
            } else if ($(this).val() === "table") {
                graphtype = 3;
            } else {
                //$(this).removeClass('active');
                //$("#flat2").text("show tree" + flatgraph);
                //flatgraph = true;
                graphtype = 2;
                $("#bie").show();
                $("#edit_ed").show();
                editing_enhanced = false;
                //$("#edit_ed").removeClass('active');
            }

            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        }
    });

    $(".mycheck").click(function () {
        if (this.id === "feat2") {
            if (!showfeats) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showfeats = !showfeats;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id === "misc2") {
            if (!showmisc) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showmisc = !showmisc;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);

        } else if (this.id === "bie") {
            if (!show_basic_in_enhanced) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            show_basic_in_enhanced = !show_basic_in_enhanced;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id === "edit_ed") {
            if (!editing_enhanced) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            editing_enhanced = !editing_enhanced;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id === "r2l") {
            if (!showr2l) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showr2l = !showr2l;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id === "adaptwidth") {
            if (!autoadaptwidth) {
                // was disabled, enable
                //$(this).addClass('active');
                $("#widthinfo").hide();
                $("#adaptwidth").text("fixed width");
            } else {
                // was endabled, disable
                //$(this).removeClass('active');
                // and clear wordlength/position tables
                wordlengths = {};
                wordpositions = {};
                $("#widthinfo").show();
                $("#adaptwidth").text("var. width");
            }
            autoadaptwidth = !autoadaptwidth;
            var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
            sendmodifs(datadico);
        } else if (this.id === "backwards") {
            backwards = !backwards;
            if (backwards) {
                //$(this).addClass('active');
                $(this).parent().addClass('active');
            } else {
                //$(this).removeClass('active');
                $(this).parent().removeClass('active');
                //$("#flat2").text("show tree" + flatgraph);
            }
        } else if (this.id == "clear") {
            $("#word").val("");
            $("#lemma").val("");
            $("#upos").val("");
            $("#xpos").val("");
            $("#deprel").val("");
            $("#multifield").val("");
            $("#sentenceid").val("");
        }
    });

    /**
     * cliquer sur un des boutons déclenche une action en fonction de leur attribut id
     */
    $(".editbuttons").click(function () {
        //console.log("sss " + this.id);
        //URL_BASE = 'http://localhost:' + $("#port").val() + '/edit';
        URL_BASE = 'http://' + window.location.hostname + ':' + $("#port").val() + '/edit/';

        //var backwards = $("#backwards").is(":checked");

        var inputtext;
        if (this.id === "findword") {
            inputtext = "findword " + backwards + " " + $("#word").val();
        } else if (this.id === "findlemma") {
            inputtext = "findlemma " + backwards + " " + $("#lemma").val();
        } else if (this.id === "findupos") {
            inputtext = "findupos " + backwards + " " + $("#upos").val();
        } else if (this.id === "findxpos") {
            inputtext = "findxpos " + backwards + " " + $("#xpos").val();
        } else if (this.id === "finddeprel") {
            inputtext = "finddeprel " + backwards + " " + $("#deprel").val();
        } else if (this.id === "findfeature") {
            inputtext = "findfeat " + backwards + " " + $("#featureval").val();
        } else if (this.id === "findcomment") {
            inputtext = "findcomment " + backwards + " " + $("#comment").val();
        } else if (this.id === "findmulti") {
            inputtext = "findmulti " + backwards + " " + $("#multifield").val();
        } else if (this.id === "findsentid") {
            inputtext = "findsentid " + backwards + " " + $("#sentenceid").val();
        } else if (this.id === "grewmatchsearchgo") {
            inputtext = "findgrewmatch " + backwards + " " + $("#grewmatchsearchexpression").val();
        } else if (this.id === "searchgo") {
            inputtext = "findexpression " + backwards + " " + $("#searchexpression").val();
        } else if (this.id === "replacego") {
            inputtext = "replaceexpression " + backwards + " " + $("#searchexpression").val() + " > " + $("#replaceexpression").val();
        } else if (this.id === "findsubtree") {
            inputtext = "findsubtree " + backwards + " " + $("#editsubtree").val();
        } else if (this.id === "createsubtree") {
            // get the id of the head of the subtree
            inputtext = "createsubtree " + $("#subtreeroot").val();
            // get an eventual CoNLL-U Plus column definition
            var globalcolumns = $("#editsubtree").val().trim().split("\n")[0];
            if (globalcolumns.startsWith("# global.columns") ||
                    globalcolumns.startsWith("#global.columns")) {
                inputtext += " " + globalcolumns;
            }
            //console.log("AAAA", globalcolumns);
            datadico = {"cmd": inputtext};
            getSubtree(datadico);
            return;
        } else if (this.id === "save") {
            inputtext = "save";
        } else if (this.id === "redo") {
            var inputtext = "mod redo";
        } else if (this.id === "undo") {
            var inputtext = "mod undo";
        } else if (this.id === "next") {
            inputtext = "next";
            //$("#sentid").val(inputtext);
        } else if (this.id === "prec") {
            inputtext = "prec";
        } else if (this.id === "first") {
            inputtext = "read 0";
        } else if (this.id === "last") {
            inputtext = "read last";
        } else if (this.id === "lire") {
            inputtext = "read " + ($("#sentid").val() - 1);
        } else if (this.id === "modifier") {
            var inputtext = "mod " + $("#mods").val();
        } else if (this.id === "valid") {
            inputtext = "valid";
        } else {
            alert("error in GUI" + this)
        }

        datadico = {"cmd": inputtext};
        sendmodifs(datadico);
    });


    // use ENTER to read sentence
    $("#sentid").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#lire").click();
        }
    });

    // use ENTER to read sentence
    $("#word").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findword").click();
        }
    });

    // use ENTER to read sentence
    $("#lemma").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findlemma").click();
        }
    });

    // use ENTER to read sentence
    $("#xpos").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findxpos").click();
        }
    });

    // use ENTER to read sentence
    $("#upos").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findupos").click();
        }
    });

    $("#deprel").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#finddeprel").click();
        }
    });

    $("#featureval").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findfeature").click();
        }
    });

    $("#comment").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findcomment").click();
        }
    });

    $("#sentenceid").keyup(function (event) {
        if (event.keyCode === 13) {
            $("#findsentid").click();
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

    // displays differences between word from goldfile (--compare) and editied file
    // when hovering on the word rectangle
    $(".wordnode").hover(function () {
        $("#comparediff").append("eeeee");

    },
            function () {
                $("#comparediff").empty();

            });

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


