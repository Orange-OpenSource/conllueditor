#!/usr/bin/env python3

# This software is under the 3-Clause BSD License
#
# Copyright (c) 2021-2025, Orange S.A.
# 
# Redistribution and use in source and binary forms, with or without modification,
# are permitted provided that the following conditions are met:
# 
#   1. Redistributions of source code must retain the above copyright notice,
#      this list of conditions and the following disclaimer.
# 
#   2. Redistributions in binary form must reproduce the above copyright notice,
#      this list of conditions and the following disclaimer in the documentation
#      and/or other materials provided with the distribution.
# 
#   3. Neither the name of the copyright holder nor the names of its contributors
#      may be used to endorse or promote products derived from this software without
#      specific prior written permission.
# 
#  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
#  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
#  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
#  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
#  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
#  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
#  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
#  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
#  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
#  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
# 
#  @author Johannes Heinecke
#  @version 2.29.4 as of 25th April 2025



# transliterate into the latin script

import collections
import json
import os
import re
import readline
import sys

class Transliterator:
    def __init__(self, datafile, language):
        if datafile:
            defp = open(datafile)
        else:
            defp = open(os.path.dirname(__file__) + "/translit.json")
        self.data = json.load(defp)

        if not language in self.data:
            print("unknown language/alphabet")
            print("available languages/alphabets")
            for lg in self.data:
                print("\t", lg)
            raise Exception("unknown language <%s>" % language)
        else:
            if isinstance(self.data[language], str):
                pointer = self.data[language][1:]
                if not pointer in self.data:
                    print("%s: pointing to unknown language/alphabet" % pointer)
                    raise Exception("unknown language")
                language = pointer
            replacements = self.data[language].items()
            self.newreplacements = []            
            for k,v in replacements:
                self.newreplacements.append((re.compile(k), v))


    def transliterate(self, text):
        for k,v in self.newreplacements:
            text = k.sub(v, text)
        return text


class RWconllu:
    def __init__(self, datafile, language, conllufile, outfile,
                 overwrite=False, sentence=False,
                 forms=True, lemmas=False):
        self.tl = Transliterator(datafile, language)
        ifp = open(conllufile)

        if outfile:
            ofp = open(outfile, "w")
        else:
            ofp = sys.stdout

        allforms = []
        comments = collections.OrderedDict()
        words = []
        for i,line in enumerate(ifp):
            line = line.rstrip()
            if not line:
                if sentence:
                    tl = self.tl.transliterate("".join(allforms) + " ")
                    comments["# translit"] = tl.rstrip()

                for k,v in comments.items():
                    if v:
                        print("%s = %s" % (k,v), file=ofp)
                    else:
                        print(k, file=ofp)
                for c in words:
                    print(c, file=ofp)
                print("", file=ofp)
                comments = collections.OrderedDict()
                words = []
                allforms = []
                    
            else:
                if line[0] == "#":
                    elems = line.split("=", 1)
                    if len(elems) == 1:
                        comments[line.strip()] = ""
                    else:
                        comments[elems[0].strip()] = elems[1].strip()
                else:
                    cols = line.split("\t")
                    if len(cols) < 10:
                        self.errmsg(i, "invalid CoNLL-U length of %d" % len(cols))
                        continue
                    #print("LL", line)
                    if "-" in cols[0]:
                        words.append(line)
                    else:
                        form = cols[1]
                        lemma = cols[2]
                        allforms.append(form)
                        miscs = self.getmiscs(cols[9], i)

                        if "SpaceAfter" in miscs: # =No
                            # no spaces
                            pass
                        elif "SpacesAfter" in miscs:
                            sa = miscs["SpacesAfter"]
                            sa = sa.replace("\\s", " ").replace("\\t", "\t").replace("\\n", " ")
                            allforms.append(sa)
                        else:
                            allforms.append(" ")

                        if forms:
                            if "Translit" in miscs:
                                if overwrite:
                                    tl = self.tl.transliterate(form + " ")
                                    miscs["Translit"] = "%s" % tl.rstrip()
                                    cols[9] = self.misc2str(miscs)
                            else:
                                tl = self.tl.transliterate(form + " ")
                                miscs["Translit"] = "%s" % tl.rstrip()
                                cols[9] = self.misc2str(miscs)

                        if lemmas:
                            if "LTranslit" in miscs:
                                if overwrite:
                                    tl = self.tl.transliterate(lemma + " ")
                                    miscs["LTranslit"] = "%s" % tl.rstrip()
                                    cols[9] = self.misc2str(miscs)
                            else:
                                tl = self.tl.transliterate(lemma + " ")
                                miscs["LTranslit"] = "%s" % tl.rstrip()
                                cols[9] = self.misc2str(miscs)

                        words.append("\t".join(cols))
        # last block
        for c in comments:
            print(c, file=ofp)
        for c in words:
            print(c, file=ofp)


    def misc2str(self, miscs):
        m = []
        for k,v in miscs.items():
            m.append("%s=%s" % (k,v))
        return "|".join(m)
    
    def getmiscs(self, misccol, i):
        if misccol == "_":
            return {}
        else:
            elems = misccol.split("|")
            m = collections.OrderedDict()
            for elem in elems:
                if not "=" in elem:
                    self.errmsg(i, "MISC key without value: <%s>" % elem)
                    m[elem] = None
                else:
                    k,v = elem.split("=", 1)
                    m[k] = v
            return m

    def errmsg(self, linenr, msg):
        print("%5d: %s" % (linenr, msg), file=sys.stderr)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--outfile", "-o", default=None, type=str, help="output file")
    parser.add_argument("--infile", "-i", default=None, type=str, help="input file (- reads from stdin)")
    parser.add_argument("--data", "-d", default=None, required=None, type=str, help="data file (default translit.json)")
    parser.add_argument("--language", "-l", default=None, required=True, type=str, help="script/language to transliterate")
    parser.add_argument("--lemmas", default=False, action="store_true", help="transliterate lemmas")
    parser.add_argument("--noforms", default=False, action="store_true", help="do not transliterate forms")
    parser.add_argument("--raw", default=False, action="store_true", help="raw text, transliterate everything")
    parser.add_argument("--overwrite", default=False, action="store_true", help="overwrite existing transliteration in MISC:Translit and in # translit")
    parser.add_argument("--sentence", default=False, action="store_true", help="add sentence transliteration by concatening forms")
    parser.add_argument("--interactive", "-I", default=False, action="store_true", help="interactive use")

    if len(sys.argv) < 2:
        parser.print_help()
    else:
        args = parser.parse_args()

        try:
            tl = Transliterator(args.data,
                                args.language)

            if args.interactive:
                txt = input(">> ")
                while txt:
                    print(tl.transliterate(txt), end="")
                    txt = input(">> ")

            elif args.raw:
                if args.infile == "-":
                    ifp = sys.stdin
                else:
                    ifp = open(args.infile)

                if args.outfile:
                    ofp = open(args.outfile, "w")
                else:
                    ofp = sys.stdout
                for line in ifp:
                    print(tl.transliterate(line), end="")

            else:
                rw = RWconllu(args.data,
                              args.language, args.infile, args.outfile,
                              overwrite=args.overwrite, sentence=args.sentence,
                              forms=not args.noforms, lemmas=args.lemmas)
        except Exception as e:
            import traceback
            traceback.print_tb(sys.exc_info()[2])

            print("Error:" , e, file=sys.stderr)
