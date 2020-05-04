/* This library is under the 3-Clause BSD License

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
 @version 2.4.0 as of 5th May 2020
 */

var xlink = "http://www.w3.org/1999/xlink";
var svgNS = "http://www.w3.org/2000/svg";
// garder le x et y maximaux afin de savoir la taille du graph généré
var svgmaxx = 0;
var svgmaxy = 0;

//var extracolumnstypes = new Set(); // here we stock all colNN instances, two know how many different extra exist


/** sessiner un arbre de dépendance
 @param {type} svg élément svg à remplir
 @param {type} trees une liste des arbres
 @return {undefined}
 */
function drawDepTree(svg, trees, sentencelength, use_deprel_as_type, isgold, incorrectwords) {
    svgmaxx = 0;
    svgmaxy = 0;
    useitalic = false;
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
    marker.appendChild(path);
    var marker = document.createElementNS(svgNS, "marker");
    marker.setAttribute("id", "markerArrowInv");
    marker.setAttribute("markerWidth", "13");
    marker.setAttribute("markerHeight", "13");
    marker.setAttribute("refX", "4");
    marker.setAttribute("refY", "5");
    marker.setAttribute("orient", "auto");
    defs.appendChild(marker);
    var path = document.createElementNS(svgNS, "path");
    path.setAttribute("d", "M2,5 L12,2 10,5 L12,8 L2,5");
    path.setAttribute("fill", "black");
    marker.appendChild(path);
    svg.setAttribute("xmlns:xlink", xlink);
    //extracolumnstypes.clear();

    for (i = 0; i < trees.length; ++i) {
        // for each head
        // insert head and all dependants
        var tree = trees[i];
        insertNode(svg, "1", tree, 0, 20, tree.indexshift || 0, 0, 0, sentencelength, //useitalic,
                use_deprel_as_type, isgold, incorrectwords);
    }

    svgmaxy += 50; // + 30 pour les mots en dessous

    // permet de modifier l'arbre en cliquant sur un mot et sa future tete (cf. edit.js)
    svg.setAttribute('onmousedown', "ModifyTree(evt)");
    //svg.setAttribute('onmouseup', "MakeRoot(evt)");

    // insert words at the bottom of the tree (and MWEs if activated)
    if (isgold == 0) {
        for (i = 0; i < trees.length; ++i) {
            var tree = trees[i];
            insertBottomWord(svg, "1", tree, 0, 0, sentencelength, useitalic);
            //if (showextra) 
            if (tree.nonstandard != undefined)    insertExtracolumns(svg, "1", tree, 0, 0, sentencelength);
        }
    }

    // add space for extracolumns
//    if (showextra && extracolumnstypes.size > 0) {
//        svgmaxy += 40 + extracolumnstypes.size*20;
//    }
    if (//showextra 
            tree.nonstandard != undefined && Object.keys(tree.nonstandard).length > 0) {
        svgmaxy += 40 + Object.keys(tree.nonstandard).length*20;
    }


    svg.setAttribute('height', svgmaxy + 30);
    svg.setAttribute('width', svgmaxx + 40);

    return defs;
}


var vertdiff = 12; // height of a line
var hor = 90; // horizontal width of node
var vertspace = 50; // vertical distance between nodes

function setSize(width, height) {
    hor = width;
    vertspace = height;
}

/** insérer un noeud (mot) dans le SVG de l'arbre de dépendance
 * @param {type} svg élément svg à remplir
 * @param {type} item le mot
 * @param {type} level le niveau (valeur y)
 * @param {type} indexshift index du premier mot de la phrase (on utilise index pour calculer la valeur x)
 * @param {type} originx coordinné x de la tête ou 0
 * @param {type} originy coordinné y de la tête ou 0
 * @param {sentencelength} longueur de la phrase: si != 0 on écrit de droite à gauche
 * @returns {undefined}
 */
function insertNode(svg, curid, item, head, level, indexshift, originx, originy, sentencelength, //useitalic = true,
        use_deprel_as_type, isgold, incorrectwords) {
    //var hor = 90;
    //console.log("item " + item);
    //console.log("cc " + curid)
    //alert("insNode hor:" + hor + " v:" + vertspace);
    var index = item.position - indexshift;
    var x = index * hor - hor / 2;
    //console.log("index " + index + " hor " + hor + " x " + x);

    if (sentencelength > 0) {
        // we write the tree from right to left
        x = ((sentencelength) * hor) - x;
    }
    //console.log("dep " + index + " " + x + " sl: " + sentencelength);
    //var vertdiff = 12;

    var levelinit = level + 5;

    // display gold tree in comparison mode in gray
    var grayclass = ""; // text
    var grayclass2 = ""; // lines 
    var gold_idprefix = ""; // give word boxes a different ID to avoid editing on them
    if (isgold == 1) {
        grayclass = " goldtree";
        grayclass2 = " goldtree2";
        gold_idprefix = "g";
    }

//    if (showextra) {
//        // get all extra columns in this word
//        var colNN = Object.keys(item).filter((name) => /^col_.*/.test(name));
//        for (var i = 0; i < colNN.length; i++) {
//            extracolumnstypes.add(colNN[i]);
//        }
//    }


    // insert word (with, form, lemma, POS etc)
    bottomlevels = drawWord(item, x, hor, levelinit, curid, svg, isgold, incorrectwords);

    level = bottomlevels[1]; // x-level at bottom of word (with features, if present)
    level += 6;
    svgmaxy = Math.max(svgmaxy, level + 1);
    svgmaxx = Math.max(svgmaxx, x + 10);
    // on garde le bas du noeud pour y mettre lies lignes verticales à la fin
    item.yy = level;


    // faire la ligne entre tete est dépendant
    if (originx != 0 && originy != 0) {
        // si ce n'est pas la tete de la phrase

        // creer le path pour le connecteur tete - fille (une ligne)
        var path = document.createElementNS(svgNS, "path");
        //var pathvar = "path" + curid + "_" + item.id + "_" + level;
        var pathvar = gold_idprefix + "path_" + head + "_" + item.id + "_" + item.deprel;
        path.setAttribute("id", pathvar);
        path.setAttribute("stroke", "black");
        if (use_deprel_as_type) {
            //path.setAttribute("stroke-width", "2");
            path.setAttribute('class', item.deprel.replace(/:/, "_") );
            //} else {
            //path.setAttribute("stroke-width", "1");
        }
        path.setAttribute("opacity", 1);
        path.setAttribute("fill", "none");
        // le texte est associé va toujours du départ (fille) de la ligne vers sa fin (tête).
        // mais si la fille est à droite de la tête, le texte est donc renversé
        // Il faut donc que la ligne va toujours de gauche à droite
        // textpath side could do it, but is not supported in Firefox < 61
        if (x < originx) {
            path.setAttribute("d", "M " + x + " " + (levelinit - 1) + " L " + originx + " " + originy);
            path.setAttribute("style", "marker-start: url(#markerArrowInv);");
            path.setAttribute("class", "deprel_followinghead" + grayclass2);
            path.setAttribute("stroke", "#880088"); // only needed for svg download
        } else {
            path.setAttribute("d", "M " + originx + " " + originy + " L " + x + " " + (levelinit - 1));
            path.setAttribute("style", "marker-end: url(#markerArrow);");
            path.setAttribute("class", "deprel_precedinghead" + grayclass2);
            path.setAttribute("stroke", "blue"); // only needed for svg download
        }

        svg.appendChild(path);
        // creer le texte pour cette ligne
        var depreltext = document.createElementNS(svgNS, "text");
        depreltext.setAttribute("id", "text" + pathvar);
        //depreltext.setAttribute("font-size", "14");
        depreltext.setAttribute("dy", "-3");
        //depreltext.setAttribute("filter", "url(#solid)");

        depreltext.setAttribute("text-anchor", "middle");
        // associer le texte avec la ligne
        var deprelpath = document.createElementNS(svgNS, "textPath");
        deprelpath.setAttributeNS(xlink, "xlink:href", "#" + pathvar); // Id of the path
        deprelpath.setAttribute("id", "textpath_" + head + "_" + item.id + "_" + item.deprel);
        deprelpath.setAttribute("class", "words deprel" + grayclass);
        deprelpath.setAttribute("fill", "#008800"); // only needed for svg download
        // textpath side only supported in Firefox >= 61
        /*
         if (x > originx)
         deprelpath.setAttribute("side", "left");
         else
         deprelpath.setAttribute("side", "right");
         */
        if (item.deprelerror == 1) {
            //deprelpath.setAttribute("fill", "red");
            deprelpath.setAttribute("class", "words deprel worderror");
        }
        //else
        //    deprelpath.setAttribute("fill", "#008800");

        if (item.deprelhighlight == 1) {
            deprelpath.setAttribute("class", "words deprel highlight");
            highlightX = x - 40;
            highlightY = level - 20;
            //depreltext.setAttribute("font-weight", "bold");
            //deprelpath.setAttribute("fill", "orange");
        }


        if (x < originx)
            deprelpath.setAttribute('startOffset', "40%");
        else
            deprelpath.setAttribute('startOffset', "60%");
        deprelpath.textContent = item.deprel;
        depreltext.appendChild(deprelpath);
        svg.appendChild(depreltext);
    } else {
        var depreltext = document.createElementNS(svgNS, "text");
        depreltext.setAttribute("id", "deprel" + curid + "_" + item.id);
        depreltext.setAttribute("class", "deprel words");
        //depreltext.setAttribute("font-size", "1rem");
        //depreltext.setAttribute("font-size", "12");

        depreltext.setAttribute('x', x);
        depreltext.setAttribute('y', 10);
        depreltext.setAttribute("text-anchor", "middle");
        depreltext.textContent = item.deprel;
        svg.appendChild(depreltext);
    }

    if (item.children) {
        for (var i = 0; i < item.children.length; i++) {
            //alert(item.children[i]);
            insertNode(svg, curid, item.children[i], item.id, level + vertspace, indexshift, x, level, sentencelength, //useitalic,
                    use_deprel_as_type, isgold, incorrectwords);
        }
}
}


/** function to create the words at the bottom of the dependency trees
 *
 * @param {type} svg svg opbject to add the word to
 * @param {type} curid current id of word
 * @param {type} item the json object with the current word and its dependants
 * @param {type} level the level (number of relations up to the root)
 * @param {type} indexshift left of right from root
 * @param {type} sentencelength used to display right-to-left alphabets
 * @param {type} useitalic
 * @return {undefined}
 */
function insertBottomWord(svg, curid, item, level, indexshift, sentencelength = 0, useitalic = true) {
    //var hor = 90;
    //console.log("item " + item);
    //console.log("cc " + curid)

    var index = item.position - indexshift;
    var x = index * hor - hor / 2;
    if (sentencelength > 0) {
        // we write the tree from right to left
        x = ((sentencelength) * hor) - x;
    }
    // en arrivant ici, on a déssiné tout l'arbre. Maintenant on connait la profondeur de l'arbre et on peut écrire les mots en bas avec des ligne
    // du noeud vers le mot
    var wordtext = document.createElementNS(svgNS, "text");
    wordtext.setAttribute("id", "wordb" + curid + "_" + item.id);
    wordtext.setAttribute("class", "words word");
    wordtext.setAttribute("font-size", "16");
    if (useitalic)
        wordtext.setAttribute("font-style", "italic");
    //else
    //     wordtext.setAttribute("font-weight", "bold");
    wordtext.setAttribute('x', x);
    wordtext.setAttribute('y', svgmaxy);
    wordtext.setAttribute("text-anchor", "middle");
    wordtext.textContent = item.form;
    svg.appendChild(wordtext);

    // show word ID
    var idtext = document.createElementNS(svgNS, "text");
    idtext.setAttribute("id", "id" + curid + "_" + item.id);
    idtext.setAttribute("font-size", "10");
    idtext.setAttribute("class", "words wordid");
    idtext.setAttribute('x', x);
    idtext.setAttribute('y', svgmaxy + vertdiff + 7);
    idtext.setAttribute("text-anchor", "middle");
    idtext.textContent = item.id;
    svg.appendChild(idtext);
    //level += vertdiff;

    // vertical line between tree and bottom word
    var path = document.createElementNS(svgNS, "path");
    var pathvar = "pathb" + curid + "_" + item.id + "_" + level;
    path.setAttribute("id", pathvar);
    path.setAttribute("stroke", "gray");
    path.setAttribute("stroke-width", "1");
    path.setAttribute("opacity", 1);
    path.setAttribute("stroke-dasharray", "3,3");
    path.setAttribute("fill", "none");
    path.setAttribute("d", "M " + x + " " + item.yy + " L " + x + " " + (svgmaxy - 15));
    svg.appendChild(path);


    // multi word entites
    if (item.mwe != undefined) {
        var wordy = svgmaxy - 30;
        var mwe = document.createElementNS(svgNS, "path");
        var mwepathvar = "mwe_" + item.mwe.fromid + "_" + item.mwe.toid + "_" + item.mwe.form;
        mwe.setAttribute("id", mwepathvar);
        mwe.setAttribute("stroke", "#888888");
        mwe.setAttribute("stroke-width", "4");
        mwe.setAttribute("opacity", 1);
        mwe.setAttribute("fill", "none");
        var length = item.mwe.toid - item.mwe.fromid + 1;
        if (sentencelength > 0) {
            //mwe.setAttribute("d", "M " + (x - 5 + hor/2) + " " + (wordy) + " l " + (-hor*length + 10) + " " + 0);
            mwe.setAttribute("d", "M " + (x + 5 - hor * length + hor / 2) + " " + (wordy) + " l " + (hor * length - 10) + " " + 0);
        } else {
            mwe.setAttribute("d", "M " + (x + 5 - hor / 2) + " " + (wordy) + " l " + (hor * length - 10) + " " + 0);
        }
        svg.appendChild(mwe);
        // creer le texte pour cette ligne
        var mwetext = document.createElementNS(svgNS, "text");
        mwetext.setAttribute("id", "mwetext" + pathvar);
        mwetext.setAttribute("font-size", "14");
        mwetext.setAttribute("dy", "-8");
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

    if (item.children) {
        for (var i = 0; i < item.children.length; i++) {
            //alert(item.children[i]);
            insertBottomWord(svg, curid, item.children[i], 0 /*level*/, indexshift, sentencelength, useitalic);
        }
    }
}
