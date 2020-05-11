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
 @version 2.4.2 as of 11th May 2020
 */

var xlink = "http://www.w3.org/1999/xlink";
var svgNS = "http://www.w3.org/2000/svg";

// garder le x et y maximaux afin de savoir la taille du graph généré
var svgmaxx = 0;
var svgminy = 0;
var svgmaxy = 0;

var ct = 0;

//var extracolumnstypes = new Set(); // here we stock all colNN instances, two know how many different extra columns exist

function drawDepFlat(svg, trees, sentencelength, use_deprel_as_type, isgold, incorrectwords) {
    svgmaxx = 0;
    svgmaxy = 0;
    svgminy = 0;
    useitalic = false;
    ct = 0;

    // define arrows
    var defs = document.createElementNS(svgNS, "defs");
    svg.appendChild(defs);
    var marker = document.createElementNS(svgNS, "marker");
    marker.setAttribute("id", "markerArrow");
    marker.setAttribute("markerWidth", "13");
    marker.setAttribute("markerHeight", "13");
    marker.setAttribute("refX", "11");
    marker.setAttribute("refY", "5");
    marker.setAttribute("orient", "auto");
    defs.appendChild(marker);
    var path = document.createElementNS(svgNS, "path");
    path.setAttribute("d", "M2,2 L4,5 2,8 L12,5 L2,2");
    path.setAttribute("fill", "black");
    path.setAttribute("stroke-width", "1"); // overridden by depgraph.css ?
    marker.appendChild(path);

    var marker = document.createElementNS(svgNS, "marker");
    marker.setAttribute("id", "markerArrowInv");
    marker.setAttribute("markerWidth", "13");
    marker.setAttribute("markerHeight", "13");
    marker.setAttribute("refX", "3");
    marker.setAttribute("refY", "5");
    marker.setAttribute("orient", "auto");

    defs.appendChild(marker);
    var path = document.createElementNS(svgNS, "path");
    path.setAttribute("d", "M2,5 L12,2 10,5 L12,8 L2,5");
    path.setAttribute("fill", "black");
    path.setAttribute("stroke-width", "1");
    marker.appendChild(path);

    svg.setAttribute("xmlns:xlink", xlink);

    //extracolumnstypes.clear();

    //var mwey = 0; // y-position of MWE bars (set when drawing the head word=
    // insert words at the bottom of the tree
    for (i = 0; i < trees.length; ++i) {
        var tree = trees[i];
        insertWord(svg, "1", tree, 0, 5, sentencelength, use_deprel_as_type, isgold, incorrectwords);
    }
    svgmaxy += 30; // MWEs are shown a bit above the bottom line

    // permet de modifier l'arbre en cliquant sur un mot et sa future tete (cf. edit.js)
    svg.setAttribute('onmousedown', "ModifyTree(evt)");
    //svg.setAttribute('onmouseup', "MakeRoot(evt)");
    //console.log("h: " + svgmaxy + " " + svgminy);
    //console.log(svg.getAttribute("height"))
    //console.log(svg.getAttribute("viewBox"))


    if (tree.nonstandard != undefined) {
        svgmaxy -= 40;
        // insert words at the bottom of the tree
        for (i = 0; i < trees.length; ++i) {
            var tree = trees[i];
            insertExtracolumns(svg, "1", tree, 0, 0, sentencelength);
        }

        // add space for extracolumns
        //svgmaxy += 80 + extracolumnstypes.size*20;
        svgmaxy += 80 + Object.keys(tree.nonstandard).length*20;
    }

    svg.setAttribute('height', svgmaxy - svgminy);
    svg.setAttribute('width', svgmaxx + 40);
    svg.setAttribute('viewBox', "0 " + svgminy + " " + (svgmaxx + 40) + " " + (svgmaxy - svgminy));
}

/** insert a word in the flat graph */
function insertWord(svg, curid, item, headpos, level, sentencelength, use_deprel_as_type, isgold, incorrectwords) {
    if (!autoadaptwidth) {
        var index = item.position;
        var depx = (index * hor) - (hor/2);
    } else {
        var depx = wordpositions[item.position];
        hor = 50; // needed for arcs
    }


    if (sentencelength > 0) {
        // we write the tree from right to left
        if (!autoadaptwidth) {
            depx = ((sentencelength ) * hor) - depx;
        } else {
            depx = rightmostwordpos - depx;
        }
    }
    //console.log(index + " " + depx + " " + sentencelength);

    var levelinit = level;
    level = levelinit + vertdiff;

    // display gold tree in comparison mode in gray
    var grayclass = ""; // text
    var grayclass2 = ""; // lines
    var gold_idprefix = ""; // give word boxes a different ID to avoid editing on them
    if (isgold == 1) {
        grayclass = " goldtree";
        grayclass2 = " goldtree2";
        gold_idprefix = "g";
    }


    //level = drawWord(item, depx, hor, levelinit, curid, svg);
    bottomlevels = drawWord(item, depx, hor, levelinit, curid, svg, isgold, incorrectwords);
    level = bottomlevels[0]; // x-level at bottom of word (with features, if present)

    if (showfeats || showmisc) {
        // if features are displayed we add some space. to avoid the enhanced dependencies cross
        // the feature/values
        //level += 70;
        level = bottomlevels[1];
    }

    level += 6;

    svgmaxy = Math.max(svgmaxy, level + 1);
    svgmaxx = Math.max(svgmaxx, depx) + 10; // + hor);

    if (headpos == 0)
         mwey = svgmaxy + 20; // sgxmaxy may grow (with enhanded deps arcs, but mwe bars should be just under the words)

    if (item.children) {
        for (var i = 0; i < item.children.length; i++) {
            //alert(item.children[i]);
            insertWord(svg, curid, item.children[i], item.position, levelinit, sentencelength, use_deprel_as_type, isgold, incorrectwords);
        }
    }


    if (headpos == 0) {
        //var depx = x; //item.id * hor;
        svgminy = Math.min(svgminy, -140);
        var path = document.createElementNS(svgNS, "path");
        var pathvar = "path_" + headpos + "_" + item.id + "_" + item.deprel;
        path.setAttribute("id", pathvar);
        path.setAttribute("stroke", "black");
        //path.setAttribute("stroke-width", "1");
        path.setAttribute("opacity", 1);
        path.setAttribute("fill", "none");
        path.setAttribute("d", "M " + depx + " " + -140 + " L " + depx + " " + (levelinit - 1));
        path.setAttribute("style", "marker-end: url(#markerArrow);");
        path.setAttribute("class", "deprel_root" + grayclass2);
	path.setAttribute("stroke", "red"); // only needed for svg download
        svg.appendChild(path);


	//console.log("BAAA " + headpos + " " + item.form + " " + item.deprel + " " + depx);

        var depreltext = document.createElementNS(svgNS, "text");
        depreltext.setAttribute("id", "deprel" + curid + "_" + item.id);
        depreltext.setAttribute("class", "words deprel" + grayclass);
        depreltext.setAttribute("fill", "green");
        //depreltext.setAttribute("font-size", "14");

        depreltext.setAttribute('x', depx);
        depreltext.setAttribute('y', -120);
        depreltext.setAttribute("text-anchor", "middle");
        depreltext.textContent = item.deprel;
        svg.appendChild(depreltext);

    } else {
        makeDep(svg, item, headpos, item.deprel, depx, sentencelength, levelinit, true, use_deprel_as_type, isgold);
    }

    // make multi word expressions
    if (item.mwe != undefined) {
        var mwe = document.createElementNS(svgNS, "path");

        var mwepathvar = "mwe_" + item.mwe.fromid + "_" + item.mwe.toid + "_" + item.mwe.form;
        mwe.setAttribute("id", mwepathvar);

        mwe.setAttribute("stroke", "#888888");
        mwe.setAttribute("stroke-width", "7");

        mwe.setAttribute("opacity", 1);
        mwe.setAttribute("fill", "none");
        var length = item.mwe.toid - item.mwe.fromid + 1;
        //svgmaxy+=20;

        if (sentencelength > 0) {
            // mwe.setAttribute("d", "M " + (depx - 5 + hor/2) + " " + (svgmaxy+20) + " l " + (-hor*length + 10) + " " + 0);
	    mwe.setAttribute("d", "M " + (depx + 5 - hor * length + hor / 2) + " " + (mwey) + " l " + (hor * length - 10) + " " + 0);

        } else {
	    mwe.setAttribute("d", "M " + (depx + 5 - hor / 2) + " " + (mwey) + " l " + (hor * length - 10) + " " + 0);

        }

        svg.appendChild(mwe);

        // creer le texte pour cette ligne
        var mwetext = document.createElementNS(svgNS, "text");
        mwetext.setAttribute("id", "mwetext" + pathvar);
        mwetext.setAttribute("font-size", "14");
        mwetext.setAttribute("dy", "-8"); // distance of word from path

        mwetext.setAttribute("text-anchor", "middle");

        // associer le texte avec la ligne
        var mwepath = document.createElementNS(svgNS, "textPath");
        mwepath.setAttributeNS(xlink, "xlink:href", "#" + mwepathvar); // Id of the path
        mwepath.setAttribute("id", "mwetextpath_" + item.mwe.fromid + "_" + item.mwe.toid);
        //mwepath.setAttribute("class", "words deprel");
        mwepath.setAttribute("fill", "#888888");
        mwepath.setAttribute('startOffset', "50%");

        mwepath.textContent = item.mwe.form;

        mwetext.appendChild(mwepath);
        svg.appendChild(mwetext);
    }


    // enhanced deps
    if (item.enhancedheads) {
        //var boxheight = 66; // TODO calculate
        for (var i = 0; i < item.enhancedheads.length; i++) {
            ed = item.enhancedheads[i];
            if (ed.position == headpos && !show_basic_in_enhanced)
                continue;

            makeDep(svg, item, ed.position, ed.deprel, depx, sentencelength, level, false, use_deprel_as_type, isgold);
        }
    }
}


/** produce a nice dependency relation for the flat view. The height is provided by the server
 *
 * @param {type} svg  the svg object to place the arc in
 * @param {type} item the dependent word
 * @param {type} headpos the position of the head (horizontal, in word units, not pixels)
 * @param {type} deprel  the dependency label
 * @param {type} depx    the x axis pixel of the arc at the dependent word
 * @param {type} sentencelength
 * @param {type} basey
 * @param {type} above
 * @param {type} use_deprel_as_type
 * @return {undefined}
 */
function makeDep(svg, item, headpos, deprel, depx, sentencelength, basey, above, use_deprel_as_type, isgold) {
     if (!autoadaptwidth) {
        var headx = headpos * hor - hor/2;
    } else {
        var headx = wordpositions[headpos];
    }
    
    var headbeforedep = (headpos < item.position);

    if (sentencelength != 0) {
        // we write the tree from right to left
        headbeforedep = !headbeforedep;
        if (!autoadaptwidth) {
            headx = ((sentencelength) * hor) - headx;   
        } else {
            headx = rightmostwordpos - headx;
        }
    }


    if (headbeforedep) {
        // head is left of dep
        headx += hor / 10;
        //depx -= hor / 10;
    } else {
        headx -= hor / 10;
        //depx += hor / 10;
    }


    // display gold tree in comparison mode in gray
    var grayclass = ""; // text
    var grayclass2 = ""; // lines 
    var gold_idprefix = ""; // give word boxes a different ID to avoid editing on them
    if (isgold == 1) {
        grayclass = " goldtree";
        grayclass2 = " goldtreearc";
        gold_idprefix = "g";
    }


    if (above) {
       // var middley = /*basey - */-Math.sqrt(Math.abs(headpos - item.position)) * 45;
        var middley = /*basey - */-(item.archeight) * 25;
        svgminy = Math.min(svgminy, middley - 10); // 10 for the deprel label above the arc
        var classname = "deprel";
        var idprefix = ""; // ModifyTree only seeds id, not class. prefix needed to distinguish enhanced from basic deprels
    } else {
        //var middley = /*basey + */Math.sqrt(Math.abs(headpos - item.position)) * 45;
        var middley = Math.abs(headpos - item.position) * 20;
        svgmaxy = Math.max(svgmaxy, middley + basey + 2);
        var classname = "enhdeprel";
        var idprefix = "enh";
    }
    // creer le path pour le connecteur tete - fille
    var path = document.createElementNS(svgNS, "path");
    var pathvar = gold_idprefix + idprefix + "path_" + headpos + "_" + item.id + "_" + deprel;
    path.setAttribute("id", pathvar);

    path.setAttribute("stroke", "black");
    if (use_deprel_as_type) {
        path.setAttribute('class', item.deprel.replace(/:/, "_"));
    } else {
        path.setAttribute("stroke-width", "1");
    }
    path.setAttribute("opacity", 1);
    path.setAttribute("fill", "none");

    // le texte est associé va toujours du départ (fille) de la ligne vers sa fin (tête).
    // mais si la fille est à droite de la tête, le texte est donc renversé
    // Il faut donc que la ligne va toujours de gauche à droite
    // textpath side could do it, but is not supported in Firefox < 61
    //
    if (headbeforedep) {
        // head is left of dep
        path.setAttribute("d", niceArc2(headx, basey /* levelinit */, depx, middley));
        path.setAttribute("style", "marker-end: url(#markerArrow);");
        path.setAttribute("class", "deprel_precedinghead" + grayclass2);
	path.setAttribute("stroke", "#880088"); // only needed for svg download
    } else {
        path.setAttribute("d", niceArc2(depx, basey /*levelinit*/, headx, middley));
        path.setAttribute("style", "marker-start: url(#markerArrowInv);");
        path.setAttribute("class", "deprel_followinghead" + grayclass2);
	path.setAttribute("stroke", "blue"); // only needed for svg download
    }

    svg.appendChild(path);

    // creer le texte pour cette ligne
    var depreltext = document.createElementNS(svgNS, "text");
    depreltext.setAttribute("id", "text" + pathvar);
    depreltext.setAttribute("font-size", "14");
    depreltext.setAttribute("dy", "-3");
    //depreltext.setAttribute("filter", "url(#solid)");
    //depreltext.setAttribute("font-family", "Arial");
    depreltext.setAttribute("text-anchor", "middle");

    // associer le texte avec la ligne
    var deprelpath = document.createElementNS(svgNS, "textPath");
    deprelpath.setAttributeNS(xlink, "xlink:href", "#" + pathvar); // Id of the path
    deprelpath.setAttribute("id", idprefix + "textpath_" + headpos + "_" + item.id + "_" + deprel);
    deprelpath.setAttribute("class", classname + grayclass);
    deprelpath.setAttribute("fill", "#008800"); // only needed for svg download
    // textpath side only supported in Firefox >= 61
    /*
     if (x > originx)
     deprelpath.setAttribute("side", "left");
     else
     deprelpath.setAttribute("side", "right");
     */
    if (item.deprelerror == 1) {
        deprelpath.setAttribute("class", classname + grayclass + " worderror");
    }


    if ((above && item.deprelhighlight == 1) || (!above && item.depshighlight)) {
        deprelpath.setAttribute("class", classname + grayclass + " highlight");
	highlightX = headx;
	highlightY = middley;
    }


    if (headbeforedep)
        deprelpath.setAttribute('startOffset', "50%");
    else
        deprelpath.setAttribute('startOffset', "50%");

    deprelpath.textContent = deprel;
    depreltext.appendChild(deprelpath);

    svg.appendChild(depreltext);
}



function niceArc(xstart, y, xend, height) {
    // calculate parameters for Bezier cubic curve (ystart == yend)
    /*
     *       c2                 c3
     *
     *     c1                      c4
     *
     *   s                            e
     *
     */

    var slant = 0.15;
    var c1x = xstart + Math.abs(hor) * slant / 2;
    var c2x = xstart + Math.abs(hor) * slant;
    var c3x = xend - Math.abs(hor) * slant;
    var c4x = xend - Math.abs(hor) * slant / 2;

    var c1y = y + height / 2;
    var c4y = c1y;
    var c2y = y + height;
    var c3y = c2y;
    var path = " M" + xstart + "," + y +
            " L" + c1x + "," + c1y +
            " C" + c2x + "," + c2y + " " + c3x + "," + c3y + " " + c4x + "," + c4y +
            " L" + xend + "," + y;
    //console.log(path);
    return path;
}

function niceArc2(xstart, y, xend, height) {
    // calculate parameters for Bezier cubic curve (ystart == yend)
    /*
     *      c2   c3   c4        d4   d3   d2
     *
     *   c1                                    d1
     *
     * s                                            e
     */
    var slant = 0.15;
    var c1x = xstart + Math.abs(hor) * slant / 2;
    var c2x = xstart + Math.abs(hor) * slant;
    var c3x = c2x + Math.abs(hor) * slant * 0.7;// / 2;
    var c4x = c3x + Math.abs(hor) * slant * 0.7;// / 2;
    var c1y = y + height / 2;
    var c2y = y + height;
    var c3y = c2y;
    var c4y = c2y;

    var d1x = xend - Math.abs(hor) * slant / 2;
    var d2x = xend - Math.abs(hor) * slant;
    var d3x = d2x - Math.abs(hor) * slant * 0.7;
    var d4x = d3x - Math.abs(hor) * slant * 0.7;
    var d1y = c1y;
    var d2y = c2y;
    var d3y = c3y;
    var d4y = c4y;

    var path = " M" + xstart + "," + y +
            " L" + c1x + "," + c1y +
            " C" + c2x + "," + c2y + " " + c3x + "," + c3y + " " + c4x + "," + c4y +
            " L" + c4x + "," + c4y + " " + d4x + "," + d4y +
            " C" + d3x + "," + d3y + " " + d2x + "," + d2y + " " + d1x + "," + d1y +
            " L" + xend + "," + y;
    //console.log(path);
    return path;
}





