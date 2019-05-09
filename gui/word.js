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
function drawWord(item, x, hor, levelinit, curid, svg) {
    var vertdiff = 12;
    var level = levelinit;
    var bottomlevels={}; // keep the height of the word with and without features (needed in dependency-flat)

    var formtext = document.createElementNS(svgNS, "text");
    formtext.setAttribute("id", "word" + curid + "_" + item.id);
    formtext.setAttribute("class", "words wordform");
    formtext.setAttribute("font-size", "12");
    var fs =  parseFloat(window.getComputedStyle(formtext).getPropertyValue('font-size'));
    //console.log("zzz " + window.getComputedStyle(formtext, null).getPropertyValue('font-size'));
    if (!fs) fs = 12; // for chrome
    level += fs*1.2; // start in function of fontsize
    formtext.setAttribute("font-weight", "bold");
    formtext.setAttribute('x', x);
    formtext.setAttribute('y', level);
    formtext.setAttribute("text-anchor", "middle");
    formtext.textContent = item.form;

    if (item.formhighlight == 1) {
        formtext.setAttribute("class", "words wordform highlight");
	highlightX = x;
	highlightY = level;
    }


    var lemmatext = document.createElementNS(svgNS, "text");
    lemmatext.setAttribute("id", "lemma" + curid + "_" + item.id);
    lemmatext.setAttribute("class", "words wordlemma");
    lemmatext.setAttribute("font-size", "10");
    fs =  parseFloat(window.getComputedStyle(lemmatext).getPropertyValue('font-size'));
     if (!fs) fs = 10; // for chrome
    level += fs*1.2;
    if (useitalic)
        lemmatext.setAttribute("font-style", "italic");
    lemmatext.setAttribute('x', x);
    lemmatext.setAttribute('y', level);
    lemmatext.setAttribute("text-anchor", "middle");
    lemmatext.textContent = item.lemma;

    if (item.lemmahighlight == 1) {
        lemmatext.setAttribute("class", "words wordlemma highlight");
	highlightX = x;
	highlightY = level;
    }


    var idtext = document.createElementNS(svgNS, "text");
    idtext.setAttribute("id", "id" + curid + "_" + item.id);
    idtext.setAttribute("class", "words wordid");
    idtext.setAttribute("font-size", "10");
    fs = parseFloat(window.getComputedStyle(idtext).getPropertyValue('font-size'));
     if (!fs) fs = 10; // for chrome
    level += fs*1.2;
    idtext.setAttribute('x', x);
    idtext.setAttribute('y', level);
    idtext.setAttribute("text-anchor", "middle");
    idtext.textContent = item.id;


    var upostext = document.createElementNS(svgNS, "text");
    upostext.setAttribute("id", "pos" + curid + "_" + item.id);
    upostext.setAttribute("font-size", "10");
    upostext.setAttribute("class", "words wordupos");
    fs = parseFloat(window.getComputedStyle(upostext).getPropertyValue('font-size'));
     if (!fs) fs = 10; // for chrome
    level += fs*1.2;
    upostext.setAttribute('x', x);
    upostext.setAttribute('y', level);
    upostext.setAttribute("text-anchor", "middle");
    upostext.setAttribute("fill", "blue"); // only for svg download
    if (item.uposerror == 1)
        upostext.setAttribute("class", "words wordupos worderror");

    if (item.uposhighlight == 1) {
        upostext.setAttribute("class", "words wordupos highlight");
	highlightX = x;
	highlightY = level;
    }
    upostext.textContent = item.upos;

    if (item.xpos != "_") {
        // add xpos if present
        var xpostext = document.createElementNS(svgNS, "text");
        xpostext.setAttribute("id", "pos" + curid + "_" + item.id);
        xpostext.setAttribute("font-size", "10");
        xpostext.setAttribute("class", "words wordxpos");

        fs = parseFloat(window.getComputedStyle(xpostext).getPropertyValue('font-size'));
         if (!fs) fs = 10; // for chrome
        level += fs*1.2;

        xpostext.setAttribute('x', x);
        xpostext.setAttribute('y', level);
        xpostext.setAttribute("text-anchor", "middle");
        xpostext.setAttribute("fill", "blue"); // only for svg download
        xpostext.textContent = item.xpos;

        if (item.xposerror == 1)
            xpostext.setAttribute("class", "words wordxpos worderror");

        if (item.xposhighlight == 1) {
            xpostext.setAttribute("class", "words wordxpos highlight");
	    highlightX = x;
	    highlightY = level;
        }
    }

    // finaly calculate surrounding box
    var rect = document.createElementNS(svgNS, "rect");
    rect.setAttribute("id", "rect_" + item.id + "_" + item.upos + "_" +  item.xpos + "_" +  item.lemma + "_"+  item.form + "_" + item.deprel);
    rect.setAttribute('x', x - ((hor - 6) / 2));
    rect.setAttribute('y', levelinit);
    rect.setAttribute('height', level-levelinit+6);
    rect.setAttribute('width', hor - 6);
    rect.setAttribute('stroke', 'black');
    rect.setAttribute('fill', '#fffff4'); // only needed for svg download, since the download does not add depgraph.css
    rect.setAttribute("rx", "5");
    rect.setAttribute("ry", "25");
    if (item.type && item.type != "_") {
        rect.setAttribute('class', item.type);
    } else if (item.chunk && item.chunk != 0) {
        rect.setAttribute('class', "chunk" + item.chunk % 6);
    } else {
        // needed for ConllEditor
        if (item.token == "empty") {
            rect.setAttribute('class', "wordnode emptynode");
            rect.setAttribute('stroke-dasharray', '10 5');
        } else
            rect.setAttribute('class', "wordnode");
    }


    nodes[item.id] = {"item": rect}

    // add first the box and then all textual items
    svg.appendChild(rect);

    svg.appendChild(formtext);
    svg.appendChild(lemmatext);
    svg.appendChild(idtext);
    svg.appendChild(upostext);
    if (item.xpos != "_") {
        svg.appendChild(xpostext);
    }


   bottomlevels[0] = level;
   bottomlevels[1] = level;
   if (showfeats && item.feats != undefined) { // display morph-syntactical features if active
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
            }

            var ftext = document.createElementNS(svgNS, "text");
            ftext.setAttribute("id", "ftext" + curid + "_" + item.id);
            ftext.setAttribute("class", "wordfeature");
            ftext.setAttribute("font-size", "10");
             fs = parseFloat(window.getComputedStyle(ftext).getPropertyValue('font-size'));
              if (!fs) fs = 10; // for chrome
              if (f > 0)
                level += fs*1.2;
            ftext.setAttribute('x', x - 5);
            ftext.setAttribute('y', level);
            ftext.setAttribute("text-anchor", "end");
            ftext.setAttribute("fill", "#004400");
            ftext.textContent = key; // + " :"; //item.feats[f];
            svg.appendChild(ftext);

            var septext = document.createElementNS(svgNS, "text");
            septext.setAttribute("id", "septext" + curid + "_" + item.id);
            septext.setAttribute("class", "wordfeature words");
            septext.setAttribute("font-size", "10");
            septext.setAttribute('x', x);
            septext.setAttribute('y', level);
            septext.setAttribute("text-anchor", "middle");
            //septext.setAttribute("fill", "#004400");
            septext.textContent = ":"; //item.feats[f];
            svg.appendChild(septext);

            var valtext = document.createElementNS(svgNS, "text");
            valtext.setAttribute("id", "valtext" + curid + "_" + item.id);
            valtext.setAttribute("class", "wordfeature words");
            valtext.setAttribute("font-size", "10");
            valtext.setAttribute('x', x + 5);
            valtext.setAttribute('y', level);
            valtext.setAttribute("text-anchor", "start");
            valtext.setAttribute("fill", "#000044");
            valtext.textContent = val; //item.feats[f];
            svg.appendChild(valtext);
        }
        bottomlevels[1] = level;
    }

    //return level;
    return bottomlevels;
}


