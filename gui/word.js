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
 @version 2.5.0 as of 23rd May 2020
 */

var svgNS = "http://www.w3.org/2000/svg";
//function createClass(name,rules){
//    var style = document.createElement('style');
//    style.type = 'text/css';
//    document.getElementsByTagName('head')[0].appendChild(style);
//    if(!(style.sheet||{}).insertRule)
//        (style.styleSheet || style.sheet).addRule(name, rules);
//    else
//        style.sheet.insertRule(name+"{"+rules+"}",0);
//}
var nodes = {}; // id: {item: svg, types [AGENT, PATIENT]}
/**
 *
 * @param {type} item
 * @param {type} x
 * @param {type} hor
 * @param {type} levelinit
 * @param {type} curid
 * @param {type} svg
 * @return {drawWord.level} height without/with features
 */
function drawWord(item, x, hor, levelinit, curid, svg, gold, incorrectwords) {
    //console.log("eeee", incorrectwords);
    var vertdiff = 12;
    var level = levelinit;
    var bottomlevels = {}; // keep the height of the word with and without features/misc (needed in dependency-flat)
    var grayclass = ""; // text
    var grayclassf = ""; // feature text
    var grayclass2 = ""; // background
    var rect_idprefix = ""; // give word boxes a different ID to avoid editing on them
    // the dep graph in the background is the gold graph in comparison mode,
    if (gold === 1) {
        grayclass = " goldtree";
        grayclassf = " goldtreefeats";
        grayclass2 = " goldtree2";
        rect_idprefix = "g";
    }
    var formtext = document.createElementNS(svgNS, "text");
    formtext.setAttribute("id", "word" + curid + "_" + item.id);
    formtext.setAttribute("class", "words wordform" + grayclass);
    formtext.setAttribute("font-size", "12");
    var fs = parseFloat(window.getComputedStyle(formtext).getPropertyValue('font-size'));
    //console.log("zzz " + window.getComputedStyle(formtext, null).getPropertyValue('font-size'));
    if (!fs)
        fs = 12; // for chrome
    level += fs * 1.2; // start in function of fontsize
    formtext.setAttribute("font-weight", "bold");
    formtext.setAttribute('x', x);
    formtext.setAttribute('y', level);
    formtext.setAttribute("text-anchor", "middle");
    formtext.textContent = item.form;

    if (item.formhighlight === 1) {
        formtext.setAttribute("class", "words wordform highlight");
        highlightX = x;
        highlightY = level;
    }


    var lemmatext = document.createElementNS(svgNS, "text");
    lemmatext.setAttribute("id", "lemma" + curid + "_" + item.id);
    lemmatext.setAttribute("class", "words wordlemma" + grayclass);
    lemmatext.setAttribute("font-size", "10");
    fs = parseFloat(window.getComputedStyle(lemmatext).getPropertyValue('font-size'));
    if (!fs)
        fs = 10; // for chrome
    level += fs * 1.2;
    if (useitalic)
        lemmatext.setAttribute("font-style", "italic");
    lemmatext.setAttribute('x', x);
    lemmatext.setAttribute('y', level);
    lemmatext.setAttribute("text-anchor", "middle");
    lemmatext.textContent = item.lemma;

    if (item.lemmahighlight === 1) {
        lemmatext.setAttribute("class", "words wordlemma highlight");
        highlightX = x;
        highlightY = level;
    }


    var idtext = document.createElementNS(svgNS, "text");
    idtext.setAttribute("id", "id" + curid + "_" + item.id);
    idtext.setAttribute("class", "words wordid" + grayclass);
    idtext.setAttribute("font-size", "10");
    fs = parseFloat(window.getComputedStyle(idtext).getPropertyValue('font-size'));
    if (!fs)
        fs = 10; // for chrome
    level += fs * 1.2;
    idtext.setAttribute('x', x);
    idtext.setAttribute('y', level);
    idtext.setAttribute("text-anchor", "middle");
    idtext.textContent = item.id;


    var upostext = document.createElementNS(svgNS, "text");
    upostext.setAttribute("id", "pos" + curid + "_" + item.id);
    upostext.setAttribute("font-size", "10");
    upostext.setAttribute("class", "words wordupos" + grayclass);
    fs = parseFloat(window.getComputedStyle(upostext).getPropertyValue('font-size'));
    if (!fs)
        fs = 10; // for chrome
    level += fs * 1.2;
    upostext.setAttribute('x', x);
    upostext.setAttribute('y', level);
    upostext.setAttribute("text-anchor", "middle");
    upostext.setAttribute("fill", "blue"); // only for svg download
    if (item.uposerror === 1)
        upostext.setAttribute("class", "words wordupos worderror");

    if (item.uposhighlight === 1) {
        upostext.setAttribute("class", "words wordupos highlight");
        highlightX = x;
        highlightY = level;
    }
    upostext.textContent = item.upos;

    if (item.xpos !== "_") {
        // add xpos if present
        var xpostext = document.createElementNS(svgNS, "text");
        xpostext.setAttribute("id", "pos" + curid + "_" + item.id);
        xpostext.setAttribute("font-size", "10");
        xpostext.setAttribute("class", "words wordxpos" + grayclass);

        fs = parseFloat(window.getComputedStyle(xpostext).getPropertyValue('font-size'));
        if (!fs)
            fs = 10; // for chrome
        level += fs * 1.2;

        xpostext.setAttribute('x', x);
        xpostext.setAttribute('y', level);
        xpostext.setAttribute("text-anchor", "middle");
        xpostext.setAttribute("fill", "blue"); // only for svg download
        xpostext.textContent = item.xpos;

        if (item.xposerror === 1)
            xpostext.setAttribute("class", "words wordxpos worderror");

        if (item.xposhighlight === 1) {
            xpostext.setAttribute("class", "words wordxpos highlight");
            highlightX = x;
            highlightY = level;
        }
    }

    // finally calculate surrounding box
    var rect = document.createElementNS(svgNS, "rect");
    rect.setAttribute("id", rect_idprefix + "rect_" + item.id + "_" + item.upos + "_" + item.xpos + "_" + item.lemma + "_" + item.form + "_" + item.deprel);
    if (!autoadaptwidth) {
        rect.setAttribute('x', x - ((hor - 6) / 2));
        rect.setAttribute('width', hor - 6);
    } else {
        rect.setAttribute('x', x - (wordlengths[item.position] / 2));
        rect.setAttribute('width', wordlengths[item.position]);
        //console.log("jjj", item.form, wordlengths[item.position]);
    }
    rect.setAttribute('y', levelinit);
    rect.setAttribute('height', level - levelinit + 6);
    rect.setAttribute('stroke', 'black');
    rect.setAttribute('fill', '#fffff4'); // only needed for svg download, since the download does not add depgraph.css
    rect.setAttribute("rx", "5");
    rect.setAttribute("ry", "25");
    if (item.type && item.type !== "_") {
        rect.setAttribute('class', item.type);
    } else if (item.chunk && item.chunk !== 0) {
        rect.setAttribute('class', "chunk" + item.chunk % 6);
    } else {
        // needed for ConlluEditor
        if (item.token === "empty") {
            rect.setAttribute('class', rect_idprefix + "wordnode emptynode" + grayclass2);
            rect.setAttribute('stroke-dasharray', '10 5');
        } else {
            //console.log("aaa", item.id, gold,  incorrectwords.has("" + item.id))
            if (incorrectwords !== null && gold === 0 && incorrectwords[item.id]) {
                rect.setAttribute('class', rect_idprefix + "wordnode compareError");
                // needed to pass arguments to ShowCompareErrors()
                function showce() {
                    ShowCompareErrors(incorrectwords[item.id]);
                }

                rect.addEventListener("mouseover", showce);
                rect.addEventListener('mouseout', HideCompareErrors);
            } else {
                rect.setAttribute('class', rect_idprefix + "wordnode" + grayclass2);
            }
        }
    }

    if (item.checktoken === true) {
        rect.setAttribute("class", rect.getAttribute("class") + " wordcheck");
    }

    nodes[item.id] = {"item": rect};

    // add first the box and then all textual items
    svg.appendChild(rect);

    svg.appendChild(formtext);
    svg.appendChild(lemmatext);
    svg.appendChild(idtext);
    svg.appendChild(upostext);
    if (item.xpos !== "_") {
        svg.appendChild(xpostext);
    }


    bottomlevels[0] = level;
    bottomlevels[1] = level;
    if (showfeats && item.feats !== undefined) { // display morpho-syntactical features if active
        level += vertdiff * 2;
        for (var f in item.feats) {
//            if (item.feats[f] == "_") {
//                // pas de traits, on supprime la distance verticale
//                level -= vertdiff * 2;
//                break;
//            }

            if (typeof item.feats[f] === 'string') {
                // to be deleted when no old server is turning any more
                var key_val = item.feats[f].split("=");
                var key = key_val[0];
                var val = key_val[1];
            } else {
                var key = item.feats[f].name;
                var val = item.feats[f].val;
                var error = item.feats[f].error;
            }

            var ftext = document.createElementNS(svgNS, "text");
            ftext.setAttribute("id", "ftext" + curid + "_" + item.id);
            ftext.setAttribute("class", "wordfeature" + grayclassf);
            ftext.setAttribute("font-size", "10");
            ftext.setAttribute('x', x - 5);
            svg.appendChild(ftext); // can only calculat wordsize if it is attached to the document
            fs = parseFloat(window.getComputedStyle(ftext).getPropertyValue('font-size'));
            if (!fs)
                fs = 10; // for chrome
            if (f > 0)
                level += fs * 1.2;
            ftext.setAttribute('y', level);
            ftext.setAttribute("text-anchor", "end");
            ftext.setAttribute("fill", "#004400");
            ftext.textContent = key; // + " :"; //item.feats[f];

            var septext = document.createElementNS(svgNS, "text");
            septext.setAttribute("id", "septext" + curid + "_" + item.id);
            septext.setAttribute("class", "wordfeature words" + grayclassf);
            septext.setAttribute("font-size", "10");
            septext.setAttribute('x', x);
            septext.setAttribute('y', level);
            septext.setAttribute("text-anchor", "middle");
            //septext.setAttribute("fill", "#004400");
            septext.textContent = ":"; //item.feats[f];
            svg.appendChild(septext);

            var valtext = document.createElementNS(svgNS, "text");
            valtext.setAttribute("id", "valtext" + curid + "_" + item.id);
            valtext.setAttribute("class", "wordfeature words" + grayclassf);
            valtext.setAttribute("font-size", "10");
            valtext.setAttribute('x', x + 5);
            valtext.setAttribute('y', level);
            valtext.setAttribute("text-anchor", "start");
            valtext.setAttribute("fill", "#000044");
            valtext.textContent = val; //item.feats[f];
            svg.appendChild(valtext);

            if (error === "name") {
                ftext.setAttribute("class", "words wordfeature worderror " + grayclass);
            }
            if (error === "name" || error === "value") {
                valtext.setAttribute("class", "words wordfeature worderror " + grayclass);
            }
        }
        bottomlevels[1] = level;
    }

    if (showmisc && item.misc !== undefined) { // display misc column info if active
        level += vertdiff * 2;
        for (var f in item.misc) {

            if (typeof item.misc[f] === 'string') {
                // to be deleted when no old server is turning any more
                var key_val = item.misc[f].split("=");
                var key = key_val[0];
                var val = key_val[1];
            } else {
                var key = item.misc[f].name;
                var val = item.misc[f].val;
            }

            var ftext = document.createElementNS(svgNS, "text");
            ftext.setAttribute("id", "mtext" + curid + "_" + item.id);
            ftext.setAttribute("class", "wordfeature" + grayclassf);
            ftext.setAttribute("font-size", "10");
            ftext.setAttribute('x', x - 5);
            svg.appendChild(ftext);
            fs = parseFloat(window.getComputedStyle(ftext).getPropertyValue('font-size'));
            //console.log("rrrr", fs, window.getComputedStyle(ftext).getPropertyValue('font-size'))
            if (!fs)
                fs = 10; // for chrome
            if (f > 0)
                level += fs * 1.2;
            ftext.setAttribute('y', level);
            ftext.setAttribute("text-anchor", "end");
            ftext.setAttribute("fill", "#440000");
            ftext.textContent = key;


            var septext = document.createElementNS(svgNS, "text");
            septext.setAttribute("id", "mseptext" + curid + "_" + item.id);
            septext.setAttribute("class", "wordfeature words" + grayclassf);
            septext.setAttribute("font-size", "10");
            septext.setAttribute('x', x);
            septext.setAttribute('y', level);
            septext.setAttribute("text-anchor", "middle");
            //septext.setAttribute("fill", "#004400");
            septext.textContent = ":"; //item.feats[f];
            svg.appendChild(septext);

            var valtext = document.createElementNS(svgNS, "text");
            valtext.setAttribute("id", "mvaltext" + curid + "_" + item.id);
            valtext.setAttribute("class", "wordfeature words" + grayclassf);
            valtext.setAttribute("font-size", "10");
            valtext.setAttribute('x', x + 5);
            valtext.setAttribute('y', level);
            valtext.setAttribute("text-anchor", "start");
            valtext.setAttribute("fill", "#440044");
            valtext.textContent = val; //item.feats[f];
            svg.appendChild(valtext);
        }
        bottomlevels[1] = level;
    }

    //return level;
    return bottomlevels;
}



/** calculate a hashcode (needed to define the color for the extra colums)
 *
 * @return {Number}
 */
String.prototype.hashCode = function () {
    var hash = 0, i, chr;
    if (this.length === 0)
        return hash;
    for (i = 0; i < this.length; i++) {
        chr = this.charCodeAt(i);
        hash = ((hash << 5) - hash) + chr;
        hash |= 0; // Convert to 32bit integer
    }
    return hash;
};



/** display contents of columns > 10 underneath the words
 *
 * @param {type} svg    svg to add the object
 * @param {type} curid  ide of current word
 * @param {type} item   the word as JSON object (sent drom server)
 * @param {type} level  level of recursion (not needed here)
 * @param {type} indexshift
 * @param {type} sentencelength if >0: right2left
 */
function insertExtracolumns(svg, curid, item, level, indexshift, sentencelength) {
    //console.log("item ", item);
    //console.log("cc " + curid)

    //var index = item.position - indexshift;
    //var x = index * hor - hor / 2;
    //if (sentencelength > 0) {
    //    // we write the tree from right to left
    //    x = ((sentencelength) * hor) - x;
    //}

    if (!autoadaptwidth) {
        var index = item.position - indexshift;
        var x = index * hor - hor / 2;
    } else {
        var x = wordpositions[item.position];
    }
    //console.log("index " + index + " hor " + hor + " x " + x);

    if (sentencelength > 0) {
        // we write the tree from right to left
        if (!autoadaptwidth) {
            x = ((sentencelength) * hor) - x;
        } else {
            x = rightmostwordpos - x;
        }
    }

    // we try all columtypes of this sentences for each word and draw them
    var ypos = 50; // start y difference under word
    for (let [coltype, colval] of Object.entries(item.nonstandard)) {
        //for (coltype of extracolumnstypes) {
        //if (item[coltype] == undefined)
        //    continue;
        //if (item[coltype] == "_") {
        if (colval === "_") {
            // nothing to show, but keep empty space
            ypos += 30;
            continue;
        }

        // calculate background color (cut BIO prefixes if present)
        //if (item[coltype][1] == ':')
        //    colvalue = item[coltype].substr(2);
        //else
        //    colvalue = item[coltype];
        if (colval[1] === ':')
            colvalue = colval.substr(2);
        else
            colvalue = colval;

        var hcode = colvalue.hashCode();
        var red = Math.abs(hcode % 148);
        var blue = Math.abs((hcode >> 4) % 148);
        var green = Math.abs((hcode >> 10) % 148);

        color = "#" + ("0" + red.toString(16)).substr(-2) + ("0" + green.toString(16)).substr(-2) + ("0" + blue.toString(16)).substr(-2);

        var rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("id", "rect_" + item.id + "_" + coltype);
        rect.setAttribute('x', x - ((hor - 8) / 2));
        rect.setAttribute('y', svgmaxy + ypos - 18);
        rect.setAttribute('height', 22);
        rect.setAttribute('width', hor - 8);
        rect.setAttribute('stroke', color);
        rect.setAttribute('fill', color); // only needed for svg download, since the download does not add depgraph.css
        rect.setAttribute("rx", "5");
        rect.setAttribute("ry", "25");
        rect.setAttribute("class", "extracol_rect");
        svg.appendChild(rect);


        var coltext = document.createElementNS(svgNS, "text");
        coltext.setAttribute("id", coltype + "_" + curid + "_" + item.id);
        coltext.setAttribute("font-size", "14");
        coltext.setAttribute('x', x);
        coltext.setAttribute('y', svgmaxy + ypos);
        coltext.setAttribute("fill", "white");
        ypos += 30;
        coltext.setAttribute("text-anchor", "middle");
        coltext.textContent = colval;
        coltext.setAttribute("class", "extracol");
        svg.appendChild(coltext);
    }

    if (item.children) {
        for (var i = 0; i < item.children.length; i++) {
            //alert(item.children[i]);
            insertExtracolumns(svg, curid, item.children[i], 0 /*level*/, indexshift, sentencelength);
        }
    }
}


function MWEbar(mwe, item, x, wordy, sentencelength) {
    if (autoadaptwidth) {
        if (sentencelength > 0) {
            // right-to-left
            // xposition are always for left-to-right, we haveto inverse them
            var mwestart = x + wordlengths[item.position] / 2 - 5;
            var mweend = wordpositions[wordpos[item.mwe.toid]] + wordlengths[wordpos[item.mwe.toid]] / 2 - 5;
            mweend = rightmostwordpos - mweend;

            mwe.setAttribute("d", "M " + mwestart + " " + (wordy) + " L " + mweend + " " + (wordy));
        } else {
            var mwestart = x - wordlengths[item.position] / 2 + 5;
            var mweend = wordpositions[wordpos[item.mwe.toid]] + wordlengths[wordpos[item.mwe.toid]] / 2 - 5;
            mwe.setAttribute("d", "M " + mwestart + " " + (wordy) + " L " + mweend + " " + wordy);
        }
    } else {
        // width of the first and last word of the MWE
        var length = item.mwe.toid - item.mwe.fromid + 1;
        var bwidth = hor * length - 10;

        if (sentencelength > 0) {
            // right-to-left
            mwe.setAttribute("d", "M " + (x + 5 - hor * length + hor / 2) + " " + (wordy) + " l " + (hor * length - 10) + " " + 0);
        } else {
            mwe.setAttribute("d", "M " + (x + 5 - hor / 2) + " " + (wordy) + " l " + bwidth + " " + 0);
        }
    }
}