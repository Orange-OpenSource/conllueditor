/** This library is under the 3-Clause BSD License

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
 @version 2.7.1 as of 17th August 2020
 */

$(document).ready(function() {
    //console.log("qqqq");
    // focusout must be linked to a non-dynamic parent of the <input> in question
    $("#arbre").focusout(function(e) {
        //console.log("FOT", e.target.id, e.target.origvalue, e.target.origwordid, e.target, e.target.className, e.target.value);

        if (e.target.origvalue != e.target.value) {
            var modcommand = e.target.conllucol; //className.split()[0].substr(1);
            if (modcommand == "head") {
                sendmodifs({"cmd": "mod " + e.target.origwordid + " " + e.target.value});
            } else if (modcommand == "deprel") {
                sendmodifs({"cmd": "mod " + e.target.origwordid + " " + e.target.word.head + " " + e.target.value});
            } else if (modcommand == "ehd") {
                sendmodifs({"cmd": "mod enhdeps " + e.target.origwordid + " " + e.target.value});
            } else if (e.target.extracol != undefined) {
            	sendmodifs({"cmd": "mod extracol " + e.target.origwordid + " " + modcommand + " " + e.target.value});

            } else {
                sendmodifs({"cmd": "mod " + modcommand + " " + e.target.origwordid + " " + e.target.value});
            }
        }
    });

//    $("#arbre").focusin(function() {
//        console.log("FIN", this);
//    });


    });


var formalErrors = 0;
function drawTable(parent, trees) {
    formalErrors = 0;
    var tbl = document.createElement("table");
    tbl.className = "conllutable";

    var rows = {}; // position: row

    if (conllucolumns.length > 0) {
        var headerrow = document.createElement("tr");
        for (var i = 0; i < conllucolumns.length; ++i) {
            var hdcell = document.createElement('th');
            headerrow.append(hdcell);
            hdcell.innerHTML = conllucolumns[i];
            //hdcell.className = "tdid";
        }
        tbl.append(headerrow);
    }

    for (i = 0; i < trees.length; ++i) {
        var tree = trees[i];

        drawTableWord(rows, tree, 0);

        //row.className = "extracoltr";

    }

    for (const pos in rows) {
        tbl.append(rows[pos]);
    }
    parent.append(tbl);
}

var numberofextracols = 0; // needed to complete the table in MWE rows

function drawTableMWE(rows, mwe, position) {
    var row = document.createElement("tr");
    var cell1 = document.createElement('td');
    row.append(cell1);
    //var cell1 = row.insertCell(-1); // add at the end
    cell1.innerHTML = mwe.fromid + "-" + mwe.toid;
    cell1.className = "tdid";

    var cell2 = row.insertCell(-1);
    //console.log("ZZZZ", mwe);
    //cell2.innerHTML = '<input class="iform" onfocusout="saveMWEField(this, ' + mwe.fromid + ', ' + mwe.toid + ', \'' + mwe.form + '\')" onfocusout="checkForm(this, ' + word.id + ' )" type="text" id="tmweform_' + mwe.fromid + '" value="' + mwe.form + '"></input>';
    //cell2.className = "tdform";
    cell2.append(makeInputfield("form", word, checkForm, mwe.form));

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
    cell9.className = "tdehd noedit";
    cell9.innerHTML = "_";

    var cell10 = row.insertCell(-1);
    fstr = "";
    if (mwe.misc == undefined)
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
    //cell10.innerHTML = '<input class="imisc" type="text" id="tmiscmwe_' + mwe.fromid + '" value="' + fstr + '"></input>';
    //cell10.className = "tdmisc";
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

function drawTableWord(rows, word, head) {
    if (word.mwe != undefined) {
        drawTableMWE(rows, word.mwe, word.position);
    }

    var combo = false; // for the time being we do not allow a combo input field to allow invalid values

    var row = document.createElement("tr");
    var cell1 = document.createElement('td');
    row.append(cell1);
    //var cell1 = row.insertCell(-1); // add at the end
    cell1.innerHTML = word.id;
    cell1.className = "tdid";

    var cell2 = row.insertCell(-1);
    cell2.className = "tdform";
    //cell2.innerHTML = '<input class="iform tdsave" onfocusout="saveField(this, ' + word.id + ' )" onkeyup="checkForm(this, ' + word.id + ' )" type="text" id="tform_' + word.position + '" value="' + word.form + '"></input>';

    cell2.append(makeInputfield("form", word, checkForm));
//    var icell2 = document.createElement('input');
//    icell2.className = "tdform";
//    icell2.id = "tform_" + word.position;;
//    icell2.value = word.form;
//    icell2.origvalue = word.form; //keep the original value in order to avoid updating server if no change is detected
//    icell2.origwordid = word.id;
//    icell2.onkeyup = function(event) { checkForm(icell2, word.id)};
//    cell2.append(icell2);

    var cell3 = row.insertCell(-1);
    cell3.className = "tdlemma";
    cell3.append(makeInputfield("lemma", word, checkForm));
    //cell3.innerHTML = '<input class="ilemma iisave" onkeyup="checkForm(this, ' + word.id + ' )" type="text" id="tlemma_' + word.position + '" value="' + word.lemma + '"></input>';

    var cell4 = row.insertCell(-1);
    cell4.className = "tdupos";
    if (combo && uposlist.length > 0) {
        var inner = '<select class="iupos" id="tupos_' + word.position + '" origvalue="' + word.upos + '" origwordid="' + word.id + '">';
        for (var i = 0; i < uposlist.length; ++i) {
            var sel = "";
            if (uposlist[i] == word.upos)
                sel = "selected";
            var inner = inner.concat('<option value="' + uposlist[i] + '" ' + sel + '>' + uposlist[i] + '</option>');
        }
        cell4.innerHTML = inner + "</select>";
    } else {
        cell4.append(makeInputfield("upos", word, checkUpos));
        //cell4.innerHTML = '<input class="iupos" type="text" id="tupos_' + word.position + '" value="' + word.upos + '"></input>';
    }


    var cell5 = row.insertCell(-1);
    cell5.className = "tdxpos";
    if (combo && xposlist.length > 0) {
        var inner = '<select class="ixpos" id="txpos">';
        for (var i = 0; i < xposlist.length; ++i) {
            var sel = "";
            if (xposlist[i] == word.upos)
                sel = "selected";
            var inner = inner.concat('<option value="' + xposlist[i] + '" ' + sel + '>' + xposlist[i] + '</option>');
        }
        cell5.innerHTML = inner + "</select>";
    } else {
        cell5.append(makeInputfield("xpos", word, checkForm));
        //cell5.innerHTML = '<input class="ixpos" type="text" id="txpos_' + word.position + '" value="' + word.xpos + '"></input>';
    }

    var cell6 = row.insertCell(-1);
    var fstr = "";
    if (word.feats == undefined)
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
        if (word.feats[f].error != undefined) {
        	featuresAllOK = false;
        }
    }
    //cell6.innerHTML = '<input onkeyup="checkFeat(this, ' + word.id + ' )"  class="ifeat" type="text" id="tfeat_' + word.position + '" value="' + fstr + '"></input>';
    fcell = makeInputfield("feat", word, checkFeat, fstr);
    if (! featuresAllOK) {
    	fcell.className += " worderror";
    }
    cell6.append(fcell);
    cell6.className = "tdfeat";


    var cell7 = row.insertCell(-1);
    cell7.className = "tdhead";
    //cell7.innerHTML = '<input onkeyup="checkHead(this, ' + word.id + ' )" class="ihead" type="text" id="thead_' + word.position + '" value="' + head + '"></input>';
    cell7.append(makeInputfield("head", word, checkHead, head));



    var cell8 = row.insertCell(-1);
    cell8.className = "tddeprel";
    if (combo && deprellist.length > 0) {
        var inner = '<select class="ideprel" id="tdeprel"><option value="_">_</option>';
        for (var i = 0; i < deprellist.length; ++i) {
            var sel = "";
            if (deprellist[i] == word.deprel)
                sel = "selected";
            var inner = inner.concat('<option value="' + xposlist[i] + '" ' + sel + '>' + deprellist[i] + '</option>');
        }
        cell8.innerHTML = inner + "</select>";
    } else {
        //cell8.innerHTML = '<input class="ideprel" type="text" id="tdeprel_' + word.position + '" value="' + word.deprel + '"></input>';
    	word.head = head; // not in original json from server
        cell8.append(makeInputfield("deprel", word, checkDeprel));
    }

    var cell9 = row.insertCell(-1);
    fstr = "";

    if (word.enhancedheads == undefined)
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
    //cell9.innerHTML = '<input class="iehd" onkeyup="checkEUD(this, ' + word.id + ' )" type="text" id="tehd_' + word.position + '" value="' + fstr + '"></input>';
    //cell9.className = "tdehd";
    cell9.append(makeInputfield("ehd", word, checkEUD, fstr));

    var cell10 = row.insertCell(-1);
    fstr = "";
    if (word.misc == undefined)
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
    //cell10.innerHTML = '<input class="imisc" onkeyup="checkFeat(this, ' + word.id + ' )" type="text" id="tmisc_' + word.position + '" value="' + fstr + '"></input>';
    //cell10.className = "tdmisc";
    cell10.append(makeInputfield("misc", word, checkFeat, fstr));

    rows[parseInt(word.position) * 10] = row;
    if (word.children) {
        for (var i = 0; i < word.children.length; i++) {
            drawTableWord(rows, word.children[i], word.id);
        }
    }

    if (word.nonstandard != undefined) {
    	// extra columns
    	for (var x in word.nonstandard) {
    		var cellX = row.insertCell(-1);
    		ecell = makeInputfield(x, word, checkForm, word.nonstandard[x]);
    		ecell.extracol = true;
    		cellX.append(ecell);
    	}
    	if (numberofextracols == 0) numberofextracols =  Object.keys(word.nonstandard).length;
    }
}



function makeInputfield(idsuffix, word, checkfct, value) {
    var icell = document.createElement('input');
    icell.className = "tablecell  i" + idsuffix;// + " worderror";
    if (word[idsuffix + "highlight"] != undefined) {
    	icell.className += " highlight";
    }

    if (word[idsuffix + "error"] != undefined) {
    	icell.className += " worderror";
    }
    icell.conllucol = idsuffix;
    icell.id = "t" + idsuffix + "_" + word.position;
    if (value == undefined) {
        icell.value = word[idsuffix]; // form; lemma, ...
        icell.origvalue = word[idsuffix]; //keep the original value in order to avoid updating server if no change is detected
    } else {
        icell.value = value;
        icell.origvalue = value;
    }
    icell.word = word;
    icell.origwordid = word.id; // keep word id as well
    icell.onkeyup =  function(event) { checkfct(icell, word.id)};
    return icell;
}


function checkHead(evt, wid) {
    //console.log("AAA", evt, wid);
    if (evt.value == wid || !(evt.value in wordpositions)) {
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
    if (evt.value != "_") {
        var elems = evt.value.split("|");
        var valid = 1;
        for (fv in elems) {
            var sep = elems[fv].indexOf(":");
            if (sep == -1) {
                valid = 0;
                break;
            }
            var eudhead = elems[fv].slice(0, sep);
            var deprel = elems[fv].slice(sep + 1);
            if (eudhead == wid || !(eudhead in wordpositions)) {
                valid = 0;
                break;
            }
            if (!deprel.match(/^[a-z:]+$/)) {
                valid = 0;
                break;
            }
        }
    }

    if (valid == 0) {
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
    if (evt.value != "_") {
        var elems = evt.value.split("|");
        var valid = 1;
        for (fv in elems) {
            if (!elems[fv].match(/^[A-Za-z0-9\[\]]+=[^ \t=]+$/)) {
                valid = 0;
                break;
            }
        }
    }

    if (valid == 0) {
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

    if (form != evt.value) {
        sendmodifs({"cmd": "mod editmwe "
                    + fromid
                    + " " + toid
                    + " " + evt.value
                    + "  " + $("#tmiscmwe_" + fromid).val()
        });
    }
}