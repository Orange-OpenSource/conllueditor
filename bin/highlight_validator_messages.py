#!/usr/bin/env python3

#!/usr/bin/env python3

# This software is under the 3-Clause BSD License
#
# Copyright (c) 2025, Orange S.A.
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
#  @version 2.32.0 as of 6th July 2025


# read error message written by UD validator
# and add `# highlight tokens/deprels` lines to CoNLL-U file

import re
import subprocess
import sys

class HLConLLU:
    def __init__(self, conllufile, lang="fr", maxerr=0, level=5, val_path=None):
        if val_path:
            arglist = [val_path + "/validate.py"]
        else:
            arglist = ["validate.py"]
        if lang:
            arglist.extend(["--lang", lang])
        if maxerr:
            arglist.extend(["--max-err", str(maxerr)])
        if level:
            arglist.extend(["--level", str(level)])
        arglist.append(conllufile)

        # [Line 19 Sent fr-ud-dev_00002 Node 1]: [L4 Morpho feature-value-upos-not-permitted] Value Fem of feature Gender is not permitted with UPOS DET in language [en].
        remessages = re.compile(r"\[Line (\d+) Sent (.+) Node (\d+)\]: \[(L\d) (.+)\] (.+)")

        self.messages = {} # sentid: {node: [message]}
        with subprocess.Popen(arglist, #stdout=subprocess.PIPE, 
                              stderr=subprocess.PIPE) as proc:
            lines = str(proc.stderr.read(), "utf8").split("\n")
            for line in lines:
                #print("LL", line)
                mo = remessages.match(line)
                if mo:
                    #print(mo.groups())
                    linenr, sentid, node, mlevel, mtype, mess = mo.groups()
                    if sentid not in self.messages:
                        self.messages[sentid] = {}
                    if node not in self.messages[sentid]:
                        self.messages[sentid][node] = []
                    self.messages[sentid][node].append((mlevel, mtype, mess))
        #for x in self.messages:
        #    print(x)

        self.update_conllu(conllufile)

    def update_conllu(self, fn):
        with open(fn) as ifp:
            addok = False
            for line in ifp:
                line = line.strip()
            
                if line.startswith("# sent_id"):
                    sentid = line.split("=", 1)[-1].strip()

                    if sentid in self.messages:
                        addok = True
                elif line.startswith("# text ="):
                    if addok:
                        tokens = set()
                        deprels = set()
                        for node in self.messages[sentid]:
                            for mlevel, mtype, mess in self.messages[sentid][node]:
                                if "deprel" in mtype.lower() or "deprel" in mess.lower():
                                    deprels.add(node)
                                else:
                                    tokens.add(node)
                        
                        if deprels:
                            print("# highlight deprels = %s" % " ".join([str(x) for x in sorted(deprels)]))
                        if tokens:
                            print("# highlight tokens = %s" % " ".join([str(x) for x in sorted(tokens)]))

                elif not line.startswith("#"):
                    if addok and sentid in self.messages:
                        for node in self.messages[sentid]:
                            for mlevel, mtype, mess in self.messages[sentid][node]:
                                print("# validator: %s %s %s %s" % (node, mlevel, mtype, mess))
                    addok = False

                print(line)

if __name__ == "__main__":
    #hl = HLConLLU(        sys.argv[1])

    import argparse
    parser = argparse.ArgumentParser("UD validator 2 Conllu")
    parser.add_argument("--lang", required=True, type=str, help="Which langauge are we checking? If you specify this (as a two-letter code), the tags will be checked using the language-specific files in the data/directory of the validator")
    parser.add_argument("--level", default=5, type=int, help="Level 1: Test only CoNLL-U backbone. Level 2: UD format. Level 3: UD contents. Level 4: Language-specific labels. Level 5: Language-specific contents.")
    parser.add_argument("--max-err", default=0, type=int, help="How many errors to output before exiting? 0 for all.")

    parser.add_argument("--val_path", default=None, type=str, help="path to validator.py")
    parser.add_argument("input", type=str, help="CoNLL-U file")

    if len(sys.argv) < 2:
        parser.print_help()
    else:
        args = parser.parse_args()
    #print(args)

    hl = HLConLLU(args.input, lang=args.lang, maxerr=args.max_err, level=args.level, val_path=args.val_path)
