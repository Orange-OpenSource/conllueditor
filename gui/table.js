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
 @version 2.29.3 as of 21st February 2025
 */

$(document).ready(function () {
    // focusout must be linked to a non-dynamic parent of the <input> in question

    // called when leaving the table cell which has been edited
    // "arbre" is the id of the table
    $("#arbre").focusout(function (e) {
        //console.log("FOCUSOUT", e.target.id, e.target.origvalue, e.target.origwordid, e.target, e.target.className, e.target.value);
        if (e.target.origvalue !== e.target.value) {
            var modcommand = e.target.conllucol; //className.split()[0].substr(1);
            //console.log("MODCOM", modcommand, e.target);
            if (modcommand === "head") {
                sendmodifs({ "cmd": "mod " + e.target.origwordid + " " + e.target.value });
            } else if (modcommand === "deprel") {
                sendmodifs({ "cmd": "mod " + e.target.origwordid + " " + e.target.word.head + " " + e.target.value });
            } else if (modcommand === "deps") {
                sendmodifs({ "cmd": "mod enhdeps " + e.target.origwordid + " " + e.target.value });
                //} else if (modcommand == "feats") {
                //sendmodifs({"cmd": "mod feat " + e.target.origwordid + " " + e.target.value});
            } else if (e.target.extracol !== undefined) {
                sendmodifs({ "cmd": "mod extracol " + e.target.origwordid + " " + modcommand + " " + e.target.value });
            } else if (modcommand === "editmwt") {
                sendmodifs({ "cmd": "mod editmwt " + e.target.fromid + " " + e.target.toid + " " + e.target.value });
            } else if (modcommand !== undefined) {
                sendmodifs({ "cmd": "mod " + modcommand + " " + e.target.origwordid + " " + e.target.value });
            }
        }
    });

    //    $("#arbre").focusin(function() {
    //        console.log("FIN", this);
    //    });
});

var columnwidth = {}; // if a column width is changed, we store it here

function largertd(where) {
    //console.log("TTTLARGER", where, $(".th" + where).width());
    $(".th" + where).width($(".th" + where).width() + 50);
    //console.log("      TTT", where, $(".th" + where).width());
    columnwidth[where] = $(".th" + where).width();
    if (where === "feats") {
        $(".i" + where).width($(".i" + where).width() + 50);
    }
}
function smallertd(where) {
    //console.log("tttSMALLER", where, $(".th" + where).width());
    if ($(".th" + where).width() > 120) {
        $(".th" + where).width($(".th" + where).width() - 50);
        //console.log("       ttt", where, $(".th" + where).width());
        columnwidth[where] = $(".th" + where).width();
        if (where === "feats") {
            $(".i" + where).width($(".i" + where).width() - 50);
        }
    }
}

var currentwordid = 0; // if != 0, the word to apply edit shortcuts to
var formalErrors = 0;

function drawTable(parent, trees, sentid) {
    formalErrors = 0;
    var tbl = document.createElement("table");
    tbl.className = "conllutable";
    //console.log("ZZZZZZss", keepmarked, sentid);

    if (keepmarked === -1 || keepmarked !== sentid) {
        //console.log("NOT keepmarked");
        currentwordid = 0;
        clickedNodes = [];
        keepmarked = -1;
    }

    //console.log("DRAW", currentwordid, currentwordid, clickedNodes);
    var rows = {}; // position: row

    if (conllucolumns.length > 0) {
        var headerrow = document.createElement("tr");
        for (var i = 0; i < conllucolumns.length; ++i) {
            var hdcellouter = document.createElement('th');
            colname = conllucolumns[i].toLowerCase();
            hdcellouter.className = "theader ooth" + colname;
            headerrow.append(hdcellouter);
            var hdcell = document.createElement('div');
            hdcell.className = "thdiv th" + colname;
            hdcellouter.append(hdcell);
            hdcell.innerHTML = conllucolumns[i]; // headers come from server
            //if (conllucolumns[i] == "FEATS" || conllucolumns[i] == "MISC" || conllucolumns[i] == "DEPS") {
            if (conllucolumns[i] !== "ID" && conllucolumns[i] !== "HEAD" && conllucolumns[i] !== "DEPREL") {
                hdcell.innerHTML += '  <input class="mybutton smallmybutton" id="' + colname + 'sizeup'
                    + '" type="button" value="+" onclick=largertd("'
                    + colname + '") /> <input class="mybutton smallmybutton" id="' + colname + 'sizedown'
                    + '" type="button" value="&ndash;" onclick=smallertd("' + colname + '") />';
            }
        }
        tbl.append(headerrow);
    }

    for (i = 0; i < trees.length; ++i) {
        var tree = trees[i];
        drawTableWord(rows, tree, 0, sentid);
        //row.className = "extracoltr";
    }

    for (const pos in rows) {
        tbl.append(rows[pos]);
    }
    parent.append(tbl);
    for (var i = 0; i < conllucolumns.length; ++i) {
        colname = conllucolumns[i].toLowerCase();
        //console.log("COLN", colname, columnwidth[colname], $(".th" + colname).width());
        if (colname in columnwidth) {
            // reset column width since it was changed by user earlier
            $(".th" + colname).width(columnwidth[colname]);
        }
    }

    if (keepmarked > -1) {
        $("#td" + currentwordid).css("background", "orange");
    }
}

var numberofextracols = 0; // needed to complete the table in MWE rows

function drawTableMWE(rows, mwe, position) {
    var row = document.createElement("tr");
    var cell1 = document.createElement('td');
    row.append(cell1);
    //var cell1 = row.insertCell(-1); // add at the end
    cell1.innerHTML = mwe.fromid + "-" + mwe.toid;
    cell1.className = "tdid";
     if (mwe.checktoken === true) {
        cell1.className = "tdid tokencheck_table";
    }
    //console.log("mmmmm", mwe);
    var cell2 = row.insertCell(-1);
    cell2.append(makeInputfield("editmwt", mwe, checkForm, mwe.form));

    var cell3 = row.insertCell(-1);
    cell3.className = "tdlemma noedit";
    cell3.innerHTML = "_";

    var cell4 = row.insertCell(-1);
    cell4.className = "tdupos noedit";
    cell4.innerHTML = "_";

    var cell5 = row.insertCell(-1);
    cell5.className = "tdxpos noedit";
    cell5.innerHTML = "_";

    var cell6 = row.insertCell(-1);
    cell6.className = "tdfeat noedit";
    cell6.innerHTML = "_";

    var cell7 = row.insertCell(-1);
    cell7.className = "tdhead noedit";
    cell7.innerHTML = "_";

    var cell8 = row.insertCell(-1);
    cell8.className = "tddeprel noedit";
    cell8.innerHTML = "_";

    var cell9 = row.insertCell(-1);
    cell9.className = "tddeps noedit";
    cell9.innerHTML = "_";

    var cell10 = row.insertCell(-1);
    fstr = "";
    if (mwe.misc === undefined)
        fstr = "_";
    for (var f in mwe.misc) {
        var key = mwe.misc[f].name;
        var val = mwe.misc[f].val;
        if (f > 0)
            fstr = fstr + "|";
        fstr = fstr.concat(key);
        fstr = fstr.concat('=');
        fstr = fstr.concat(val);
    }
    cell10.append(makeInputfield("misc", word, checkFeat, fstr));

    // found no other way to order table rows correctly ...
    rows[(parseInt(position) - 0.1) * 10] = row;

    // extra columns
    for (var x = 0; x < numberofextracols; x++) {
        var cellX = row.insertCell(-1);
        cellX.className = "tdX noedit";
        cellX.innerHTML = "_";
    }
}


function drawTableWord(rows, word, head, sentid) {
    if (word.mwe !== undefined) {
        drawTableMWE(rows, word.mwe, word.position);
    }

    var combo = false; // for the time being we do not allow a combo input field to allow invalid values

    var row = document.createElement("tr");

    var cell1 = document.createElement('td');
    row.append(cell1);
    //var cell1 = row.insertCell(-1); // add at the end
    cell1.innerHTML = word.id;
    cell1.className = "tdid";
    cell1.id = "td" + word.id;

    if (word.checktoken === true) {
        cell1.className = "tdid tokencheck_table";
    }

    cell1.onclick = function (event) {
        //console.log("hit id", word.id);
        $("." + cell1.className).css("background", "white");
        if (currentwordid === word.id) {
            currentwordid = 0;
            clickedNodes = [];
        } else {
            // word to apply shortcuts to
            if (event.ctrlKey) {
                $("#" + cell1.id).css("background", "orange");
                keepmarked = sentid;
                clickedNodes = []; // keepmarked true means that the last clicked word is still active
            } else {
                $("#" + cell1.id).css("background", "yellow");
                keepmarked = -1;
            }
            currentwordid = word.id;
            clickedNodes.push(word.id);
        }
    };

    /*
    if (keepmarked && word.id == currentwordid) {
        console.log("RECOLOR", cell1.id, word.id, currentwordid, $("#" + cell1.id));
        $("#" + cell1.id).css("background", "orange"); // DOES NOT WORK HERE
    }
    */

    var cell2 = row.insertCell(-1);
    cell2.className = "tdform";
    cell2.append(makeInputfield("form", word, checkForm));

    var cell3 = row.insertCell(-1);
    cell3.className = "tdlemma";
    cell3.append(makeInputfield("lemma", word, checkForm));

    var cell4 = row.insertCell(-1);
    cell4.className = "tdupos";
    if (combo && uposlist.length > 0) {
        var inner = '<select class="iupos" id="tupos_' + word.position + '" origvalue="' + word.upos + '" origwordid="' + word.id + '">';
        for (var i = 0; i < uposlist.length; ++i) {
            var sel = "";
            if (uposlist[i] === word.upos)
                sel = "selected";
            var inner = inner.concat('<option value="' + uposlist[i] + '" ' + sel + '>' + uposlist[i] + '</option>');
        }
        cell4.innerHTML = inner + "</select>";
    } else {
        cell4.append(makeInputfield("upos", word, checkUpos));
    }

    var cell5 = row.insertCell(-1);
    cell5.className = "tdxpos";
    if (combo && xposlist.length > 0) {
        var inner = '<select class="ixpos" id="txpos">';
        for (var i = 0; i < xposlist.length; ++i) {
            var sel = "";
            if (xposlist[i] === word.upos)
                sel = "selected";
            var inner = inner.concat('<option value="' + xposlist[i] + '" ' + sel + '>' + xposlist[i] + '</option>');
        }
        cell5.innerHTML = inner + "</select>";
    } else {
        cell5.append(makeInputfield("xpos", word, checkForm));
    }

    var cell6 = row.insertCell(-1);
    var fstr = "";
    if (word.feats === undefined)
        fstr = "_";
    var featuresAllOK = true;
    for (var f in word.feats) {
        var key = word.feats[f].name;
        var val = word.feats[f].val;
        if (f > 0)
            fstr = fstr + "|";
        fstr = fstr.concat(key);
        fstr = fstr.concat('=');
        fstr = fstr.concat(val);
        if (word.feats[f].error !== undefined) {
            featuresAllOK = false;
        }
    }

    fcell = makeInputfield("feats", word, checkFeat, fstr);
    //console.log("TTT", word, checkFeat, fstr);
    fcell.className += " featsicol"; // "replace" icol
    if (!featuresAllOK) {
        fcell.className += " worderror";
    }

    if (Object.keys(feats_per_upos).length > 0) {
        // language specific feature list for each UPOS available
        eb = makeEditbutton("feats", word, word.feats);
        cell6.append(eb);
    }
    cell6.append(fcell);
    cell6.className = "tdfeats";

    var cell7 = row.insertCell(-1);
    cell7.className = "tdhead";
    icell7 = makeInputfield("head", word, checkHead, head)
    cell7.append(icell7);

    if (word.checkdeprel === true) {
        icell7.className += " tokencheck_table";
    }

    var cell8 = row.insertCell(-1);
    cell8.className = "tddeprel";

    if (combo && deprellist.length > 0) {
        var inner = '<select class="ideprel" id="tdeprel"><option value="_">_</option>';
        for (var i = 0; i < deprellist.length; ++i) {
            var sel = "";
            if (deprellist[i] === word.deprel)
                sel = "selected";
            var inner = inner.concat('<option value="' + xposlist[i] + '" ' + sel + '>' + deprellist[i] + '</option>');
        }
        cell8.innerHTML = inner + "</select>";
        if (word.checkdeprel === true) {
          $("#tdeprel").class += "tdid tokencheck_table";
       }
    } else {
        word.head = head; // not in original json from server
        icell8 = makeInputfield("deprel", word, checkDeprel);
        cell8.append(icell8);
        if (word.checkdeprel === true) {
           icell8.className += " tokencheck_table";
       }
    }



    var cell9 = row.insertCell(-1);
    fstr = "";

    if (word.enhancedheads === undefined)
        fstr = "_";
    for (var f in word.enhancedheads) {
        var key = word.enhancedheads[f].id;
        var val = word.enhancedheads[f].deprel;
        if (f > 0)
            fstr = fstr + "|";
        fstr = fstr.concat(key);
        fstr = fstr.concat(':');
        fstr = fstr.concat(val);
    }

    cell9.append(makeInputfield("deps", word, checkEUD, fstr));

    var cell10 = row.insertCell(-1);
    fstr = "";
    if (word.misc === undefined)
        fstr = "_";
    for (var f in word.misc) {
        var key = word.misc[f].name;
        var val = word.misc[f].val;
        if (f > 0)
            fstr = fstr + "|";
        fstr = fstr.concat(key);
        fstr = fstr.concat('=');
        fstr = fstr.concat(val);
    }

    cell10.append(makeInputfield("misc", word, checkFeat, fstr));

    rows[parseInt(word.position) * 10] = row;
    if (word.children) {
        for (var i = 0; i < word.children.length; i++) {
            drawTableWord(rows, word.children[i], word.id, sentid);
        }
    }

    if (word.nonstandard !== undefined) {
        // extra columns
        for (var x in word.nonstandard) {
            var cellX = row.insertCell(-1);
            ecell = makeInputfield(x, word, checkForm, word.nonstandard[x]);
            ecell.extracol = true;
            cellX.append(ecell);
        }
        if (numberofextracols === 0) numberofextracols = Object.keys(word.nonstandard).length;
    }
}

function makeEditbutton(idsuffix, word, keyvaluelist) {
    // cell6.append('<input class="mybutton smallmybutton" id="edit" type="button" value="e" onclick="smallertd(&quot;feats&quot;)">');
    var icell = document.createElement('input');
    icell.className = "mybutton smallmybutton e" + idsuffix;
    icell.id = "et" + idsuffix + "_" + word.position;
    icell.type = "button";
    icell.value = "m";


    icell.onclick = function (event) {
        //checkfct(icell, word.id)
        //console.log("YYY", word.id, word.upos, keyvaluelist, );
        //<tr><th>Name</th> <th>Values</th> </tr>
        $("#featureEditing").empty();

        var currentkeyvalues = {};
        for (var x in keyvaluelist) {
            currentkeyvalues[keyvaluelist[x].name] = keyvaluelist[x].val;
        }

        var feats_possible = null;
        if (feats_per_upos) {
            var feats_possible = new Set(feats_per_upos[word.upos]);
        } else {
            // imposible to know whether a fature is valid for the given UPOS, no editing here possible

        }
        //console.log("FEATVALUES", featvalues);

        $("#FE_id").text(word.id);
        $("#FE_form").text(word.form);
        $("#FE_upos").text(word.upos);
        var x = 0;
        for (let p of feats_possible) {
            //console.log("rrr", x, p, feats_possible);
            $("#featureEditing").append('<tr class="feedit" id="fe_' + x + '">');

            $("#fe_" + x).append('<td id="fe_name_' + x + '">' + p + '</td>');
            $("#fe_" + x).append('<td id="fe_radio_' + x + '">')
            var found = false;
            for (var ii = 0; ii < featvalues[p].length; ii++) {
                //$("#fe_radio_" + x).append(featvalues[p][ii] + ": ");
                var checked = "";
                if (featvalues[p][ii] === currentkeyvalues[p]) {
                    checked = "checked";
                    found = true;
                }
                $("#fe_radio_" + x).append('<input type="radio" id="fe_radiob_' + x + '_' + ii + '" name="fval_' + x + '" value="'
                    + featvalues[p][ii] + '" ' + checked + '/> <label for="fe_radiob_' + x + '_' + ii + '">'
                    + featvalues[p][ii] + '</label>');
            }
            //$("#fe_radio_" + x).append("None: ");
            var checked = "";
            if (!found) {
                checked = "checked";
            }
            $("#fe_radio_" + x).append('<input type="radio" id="fe_radiobn_' + x + '" name="fval_' + x + '" value="'
                + "None" + '" ' + checked + '/> <label for="fe_radiobn_' + x + '">None</label>');
            x++;
        }
        for (f in currentkeyvalues) {
            // features which are not defined for this UPOS and for which we do not have valid values
            if (!feats_possible.has(f)) {
                $("#featureEditing").append('<tr class="feedit_unknown" id="fe_' + x + '">');
                $("#fe_" + x).append('<td id="fe_name_' + x + '">' + f + '</td>');
                //$("#fe_" + x).append('<td id="fe_val_' + x + '">' + currentkeyvalues[f] + '</td>');
                $("#fe_" + x).append('<td id="fe_val_' + x + '"><input type="text" id="fe_value_' + x + '" value="' + currentkeyvalues[f] + '"></td>');
                x++;
            }
        }
        $("#editFeats").modal();
    };

    return icell;
}

function makeInputfield(idsuffix, word, checkfct, value) {
    var icell = document.createElement('input');
    //icell.className = "tablecell i" + idsuffix;// + " worderror";
    icell.className = "tablecell icol";// + " worderror";
    if (word[idsuffix + "highlight"] !== undefined) {
        icell.className += " highlight";
    }

    if (word[idsuffix + "error"] !== undefined) {
        icell.className += " worderror";
    }

    icell.conllucol = idsuffix;
    icell.id = "t" + idsuffix + "_" + word.position;
    if (value === undefined) {
        icell.value = word[idsuffix]; // form; lemma, ...
        icell.origvalue = word[idsuffix]; //keep the original value in order to avoid updating server if no change is detected
    } else {
        icell.value = value;
        icell.origvalue = value;
    }
    icell.word = word;
    icell.origwordid = word.id; // keep word id as well
    if (word.fromid) {
        // MWEs
        icell.fromid = word.fromid;
        icell.toid = word.toid;
    }
    icell.onkeyup = function (event) { checkfct(icell, word.id) };
    return icell;
}


function checkHead(evt, wid) {
    //console.log("AAA", evt, wid);
    if (evt.value === wid || !(evt.value in wordpositions)) {
        //console.log("CCC", evt, wordpositions);
        //evt.color = "red";
        // TODO: make better ?
        document.getElementById(evt.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(evt.id).style.backgroundColor = "white";
        formalErrors--;
    }
}


function checkEUD(evt, wid) {
    var valid = 1;
    if (evt.value !== "_") {
        var elems = evt.value.split("|");
        var valid = 1;
        for (fv in elems) {
            var sep = elems[fv].indexOf(":");
            if (sep === -1) {
                valid = 0;
                break;
            }
            var eudhead = elems[fv].slice(0, sep);
            var deprel = elems[fv].slice(sep + 1);
            if (eudhead === wid || !(eudhead in wordpositions)) {
                valid = 0;
                break;
            }
            if (!deprel.match(/^[a-z:]+$/)) {
                valid = 0;
                break;
            }
        }
    }

    if (valid === 0) {
        document.getElementById(evt.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(evt.id).style.backgroundColor = "white";
        formalErrors--;
    }
}

function checkForm(elem, wid) {
    //console.log("ELEM", elem);
    if (elem.value.match(/[ \t\n]/)) {
        document.getElementById(elem.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(elem.id).style.backgroundColor = "white";
        formalErrors--;
    }
}

function checkUpos(elem, wid) {
    //console.log("ELEM", elem.value);
    if (!elem.value.match(/^[A-Z]+$/)) {
        document.getElementById(elem.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(elem.id).style.backgroundColor = "white";
        formalErrors--;
    }
}

function checkDeprel(elem, wid) {
    if (!elem.value.match(/^[a-z](:[a-z]+)?$/)) {
        document.getElementById(elem.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(elem.id).style.backgroundColor = "white";
        formalErrors--;
    }
}

function checkFeat(evt, wid) {
    var valid = 1;
    if (evt.value !== "_") {
        var elems = evt.value.split("|");
        var valid = 1;
        for (fv in elems) {
            if (!elems[fv].match(/^[A-Za-z0-9\[\]]+=[^ \t=]+$/)) {
                valid = 0;
                break;
            }
        }
    }

    if (valid === 0) {
        document.getElementById(evt.id).style.backgroundColor = "#ff5555";
        formalErrors++;
    } else {
        document.getElementById(evt.id).style.backgroundColor = "white";
        formalErrors--;
    }
}


function saveField(evt, wid) {
    console.log("SAVING", wid);
}

function saveMWEField(evt, fromid, toid, form) {
    console.log("SAVING", evt, fromid, toid, form, $("#tmiscmwe_" + fromid).val());

    if (form !== evt.value) {
        sendmodifs({
            "cmd": "mod editmwe "
                + fromid
                + " " + toid
                + " " + evt.value
                + "  " + $("#tmiscmwe_" + fromid).val()
        });
    }
}
