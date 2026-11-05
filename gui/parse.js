/** This library is under the 3-Clause BSD License

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
 @version 2.31.2 as of 25th June 2025
 */


var URL_BASE = 'http://' + window.location.hostname + ':12347/parse';


var isIE = /*@cc_on!@*/false || !!document.documentMode;
var isEdge = !isIE && !!window.StyleMedia;
var sentenceId = 0;

var wordlengths = {}; // position (not ID !!): width in px
var wordpositions = {}; // position: x-position in graph
var wordpos = {}; // id: position
var rightmostwordpos = 0; // position of the last word (needed to draw R2L trees)
var hordist = 6; // horizontal distance beween words (if auto-adpat is active)


function choosePort() {
    // http port
    if (window.location.port != "") {
        $("#port").val(window.location.port);
        $("#portinfo").hide();
    }
    URL_BASE = 'http://' + window.location.hostname + ':' + $("#port").val() + '/parse/';

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
        URL_BASE = 'http://' + window.location.hostname + ':' + c + '/parse/';
        document.getElementById('port').value = c;
    }
    //alert(c + " " + URL_BASE);
}


var flatgraph = false;
//var showfeats = false;
//var showmisc = false;
//var showr2l = false;
var showextra = false;
//var autoadaptwidth = false; // TODO factorise word width calculation and add here
var show_basic_in_enhanced = false; // if true we display enhanced deps which are identical two basic deps
var parserinfo = null; // output from HTTP get to URL at "info" key in in configuration file
var parseraddress = "?"
var incorrectwords = {};

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
    if (//showfeats
        $("#feat3").is(":checked")
         && cword.feats !== undefined) { // display misc column info if active
        for (var f in cword.feats) {
            //console.log("ggg", cword.feats[f].val, wlen, 2*ctx.measureText(cword.feats[f].val).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.feats[f].name).width);
            wlen = Math.max(wlen, 2 * ctx.measureText(cword.feats[f].val).width);
            //console.log("hhh", wlen);
        }
    }

    // take into accound MISC values width
    if (//showmisc
        $("#misc3").is(":checked")
        && cword.misc !== undefined) { // display misc column info if active
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

function ModifyTree(evt) {
    // used when clicking in dependency tree/hedge (by editor) not needed here
}

var lastdata = []; // the results of the last call to the parser

/** format all sentences as syntax trees in svg */
function formatToutesPhrases() {
    $("#arbre").empty();
    for (e = 0; e < lastdata.length; ++e) {
        formatPhrase(lastdata[e]);
    }
    // install svg download button
    downloadSVG("a2");
     $('.onlyWithTree').show();
}

/** format the given sentence as syntax tree in svg */
function formatPhrase(item) {
    if (!$("#fixedwidth").is(":checked")) {
        var maxlen = getAllWordLengths(item, 0);
    }

    //$("#arbre").append('<li id="li' + sentenceId + '">');
    var svg = document.createElementNS(svgNS, "svg");
    //$("#det" + curid).append('<h3>Syntax</h3>');
    $("#arbre").append('<div id="s' + item.sentenceid + '">');
    $("#s" + item.sentenceid).append(svg);

    var use_deprel_as_type = true;
    var sentencelength = 0;

    /*if (showr2l) {
        sentencelength = item.length;
    }*/

    if ($("#r2l2").is(":checked")) {
        sentencelength = item.length;
    }

    if (flatgraph) {
        drawDepFlat(svg, item.tree, sentencelength, use_deprel_as_type);
    } else {
        drawDepTree(svg, item.tree, sentencelength, use_deprel_as_type, 0, incorrectwords);
    }
}


function getServerInfo() {
    //var urlbase = 'http://' + window.location.host + ':' + $("#port").val() + '/';

    // also get the lists of valid upos and deprels
    var urlbase = URL_BASE + "info";

    $.ajax({
        url: urlbase, //+'/foo/fii?fuu=...',
        //async: false,
        type: 'GET',
        //headers: {
        //    'Content-type': 'text/plain'
        //},
        statusCode: {
            400: function () {
                alert('Bad query');
            }
        },
        success: function (data) {
            if (data.url) {
                parseraddress = data.url;
                window.document.title = "Parser CoNLL-U FrontEnd: " + data.url;
                $("#parserurl").text(data.url);
            }

            if (data.info) {
                parserinfo = data.info;
            }

            // set version number to logo (shown if mouse hovers on the logo)
            //$('#logo').attr("title", data.version);
            //$('#ce_version').text(data.version);

        },
        error: function (data) {
            // do something else
            console.log("ERREUR " + data);
            alert("ConlluEditor server not running on '" + urlbase + "' ?");
        }
    });

    // initial states
    $('.onlyWithTree').hide();
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
    a2.download = "arbre.svg";
}

function getRaw(what, title) {
    $('#rawtext').empty(); // clean text
    $('#showRawModalLabel').html(title); //update modal title
    for (e = 0; e < lastdata.length; ++e) {
       if (what == "conllu") {
            $('#rawtext').append(lastdata[e].conllu);
        } else if (what == "latex") {
            $('#rawtext').append(lastdata[e].latex);
        } else if (what == "sdparse") {
            $('#rawtext').append(lastdata[e].sdparse);
        }
    }
}



$(document).ready(function () {
    choosePort();
    getServerInfo();

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
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        } else if (this.id == "feat2") {
            if (!showfeats) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showfeats = !showfeats;
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        } else if (this.id == "misc2") {
            if (!showmisc) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showmisc = !showmisc;
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        } else if (this.id == "bie") {
            if (!show_basic_in_enhanced) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            show_basic_in_enhanced = !show_basic_in_enhanced;
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        } else if (this.id == "r2l") {
            if (!showr2l) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showr2l = !showr2l;
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        } else if (this.id == "extracols") {
            if (!showextra) {
                $(this).addClass('active');
            } else {
                $(this).removeClass('active');
            }
            showextra = !showextra;
            //var datadico = {"cmd": "read " + ($("#sentid").val() - 1)};
        }
        //sendmodifs(datadico);
        formatToutesPhrases();
    });



    /** cliquer sur 'analyser"
     *
     */
    $("#analyser").click(function () {
        //console.log("URL ", URL_BASE);
        var inputtext = $("#texte").val();
        //console.log("TTT", inputtext);
        
        if (inputtext == "") {
            alert("no sentence(s) given");
            return;
        } 
        $.ajax({
            url: URL_BASE, // + "json", //+'/foo/fii?fuu=...',
            type: 'POST',
            data: {"txt": inputtext},
            //data:  inputtext  ,

            //headers: {
            //    'Content-type': 'text/plain',
            //},
            statusCode: {
                204: function () {
                    alert('No text to parse');
                },
                400: function () {
                    alert('Bad query');
                }
            },
            success: function (data) {
                //console.log("Donnees");
                //console.log(data);
                $("#arbre").empty(); // vider le div

                //if (nn)
                //   data = [data];
                //_.each(data, formatPhrase);

                lastdata = data;
                formatToutesPhrases();

//                for (e = 0; e < data.length; ++e) {
//                    formatPhrase(data[e]);
//                }

//                $("rect")
//                        .bind('mousedown', function (event, ui) {
//                            alert("drag");
//                        }
//                        );
            },
            error: function (data) {
                // do something else
                console.log("ERREUR ", data);
                alert("Server not running on '" + URL_BASE + "' ?");
            }
        });
    });

    $("#vider").click(function () {
        $("#texte").val("");
        $("#arbre").empty();
        $(window).scrollTop(0);
    });


    $("#parseraddress").click(function () {
        $('#showRawModalLabel').html("Parser information"); //update modal title
        //getRaw("conllu", "CoNLL-U");
         console.log("zzz", parserinfo);
         $('#rawtext').empty(); // clean text

        $('#rawtext').append(parseraddress);
        $('#rawtext').append("\n\n");
        if (typeof parserinfo == "string")
            $('#rawtext').append(parserinfo);
        else
            $('#rawtext').append(JSON.stringify(parserinfo, null, 2));

        //$("#showraw").dialog("open");
        $("#showRawModal").modal()
    });

    // get CoNLL-U
    $("#conllu").click(function () {
        getRaw("conllu", "CoNLL-U");
        $("#showRawModal").modal()
    });

        // start show sdparse
    $("#sdparse").click(function () {
        getRaw("sdparse", "SD-Parse");
        $("#showRawModal").modal()
    });

     $("#latex").click(function () {
        getRaw("latex", '<span class="latex">L<sup>a</sup>T<sub>e</sub>X</span>');
        $("#showRawModal").modal()
    });
});



