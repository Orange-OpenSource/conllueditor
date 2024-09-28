#!/usr/bin/env python3

# collects feature valid pairs from each token in a CoNLL-U file and
# tries to ignore the rare ones

import collections
import json
import os
import sys


class SetJson(json.JSONEncoder):
    def default(self, obj):
        #print("TYPE", type(obj), obj, file=sys.stderr)
        if isinstance(obj, set):
            #print("SET", obj, file=sys.stderr)
            return list(sorted(obj))

        #elif isinstance(obj, dict):
        #    print("DICT", obj, file=sys.stderr)
        #    return list(sorted(obj))
        return json.JSONEncoder.default(self, obj)
            


class Language:
    def __init__(self, lg):
        self.name = lg
        self.upos = {} # upos: UposObj

    def out(self, threshold):
        lines = [str(self.upos[upos].out(threshold)) for upos in sorted(self.upos)]
        return "%s\n  %s" % (self.name, "\n  ".join(lines))

    def toJson(self, threshold):
        res = { }
        for upos in sorted(self.upos):
            res[upos] = self.upos[upos].toJson(threshold)
        return res

    def toFeatsJson(self, featurevalues, threshold):
        res = { }
        for upos in sorted(self.upos):
            #res[upos] = self.upos[upos].toFeatsJson(featurevalues)
            self.upos[upos].toFeatsJson(featurevalues, res, threshold)
        res2 = collections.OrderedDict()

        for key in sorted(res):
            res2[key] = res[key]
        return res2


class Upos:
    def __init__(self, upos, keep):
        self.name = upos
        self.keep = keep
        self.count = 0
        self.featvals = {}  # {Feat=Val: count}}

    def out(self, threshold):
        lines = ["%s %d" % (self.name, self.count)]
        #for feat, number in sorted(self.featvals.items()):
        for feat, number in sorted(self.featvals.items(), key=lambda x: -x[1]):
            percentage = 100*number/self.count
            if (self.keep and feat not in self.keep) \
               or not self.keep:
                if percentage < threshold:
                    continue
            tag = ""
            if self.keep and feat in self.keep:
                tag += "keep "
            #print("eeee", self.keep, feat)
            lines.append("    %s%-15s\t%5d\t%6.2f" % (tag, feat, self.featvals[feat], percentage))
        return "\n".join(lines)

    def toJson(self, threshold):
        res = { "upos": self.name, "totalcount": self.count, "features": {} }
        featvals = {}
        for feat, number in sorted(self.featvals.items(), key=lambda x: -x[1]):
            percentage = round(100*number/self.count, 2)
            if self.keep and feat not in self.keep \
               or not self.keep:
                if percentage < threshold:
                    continue
            # give frequency and rate as a dict
            #dico = { "count": self.featvals[feat], "rate": percentage }
            # just rate as a value
            dico = [percentage]
            if self.keep and feat in self.keep:
                #dico["keep"] = True
                dico.append(True)
            featvals[feat] = dico

        res["features"] = featvals
        return res


    def toFeatsJson(self, featurevalues, dico, threshold):
        for feat, number in sorted(self.featvals.items(), key=lambda x: -x[1]):
            percentage = round(100*number/self.count, 2)
            if self.keep and feat not in self.keep \
               or not self.keep:
                if percentage < threshold:
                    continue
            if not feat in dico:
                dico[feat] = {
                    "type": "lspec",
                    "doc": "global", # OK?
                    "permitted": 1,
                    "errors": [],
                    "uvalues": set(),
                    "lvalues": [],
                    "byupos": {}
                    }
            dico[feat]["byupos"][self.name] = collections.OrderedDict()
            if feat in featurevalues:
                for val in featurevalues[feat]:
                    dico[feat]["uvalues"].add(val)
                    dico[feat]["byupos"][self.name][val] = 1
                #print("DDD", dico[feat]["byupos"], file=sys.stderr)
                #print("EEE", sorted(dico[feat]["byupos"].items()), file=sys.stderr)

                d1 = collections.OrderedDict()
                for k,v in sorted(dico[feat]["byupos"][self.name].items()):
                    d1[k] = v
                
                dico[feat]["byupos"][self.name] = d1
                #print("ddd", d1)
        return dico

class UposFeatVal:
    def __init__(self):
        self.lgs = {}
        self.features = {} # feat: [value]

    def readconllufiles(self, fns, keep, featvalues, threshold=0.0):
        self.keep = None
        self.threshold = threshold
        if keep:
            self.keep = set(keep)
        for fn in fns:
            self.readconllu(fn, featvalues)

    def readconllu(self, fn, featvalues):
        basename = os.path.basename(fn)
        elems = basename.split("-")
        lg = elems[0]
        if not lg in self.lgs:
            self.lgs[lg] = Language(lg)

        ifp = open(fn)
        for line in ifp:
            line = line.strip()
            if not line or line[0] == "#":
                continue
            elems = line.split("\t")
            if "-" in elems[0]:
                continue
            upos = elems[3]
            if elems[5] == "_":
                continue
            feats = elems[5].split("|")


            nf = []
            for f in feats:
                feat,val = f.split("=", 1)
                nf.append(feat)
                if not feat in self.features:
                    self.features[feat] = set()
                self.features[feat].add(val)
            if not featvalues:
                feats = nf

            if upos not in self.lgs[lg].upos:
                self.lgs[lg].upos[upos] = Upos(upos, self.keep)
            self.lgs[lg].upos[upos].count += 1
            for feat in feats:
                if feat not in self.lgs[lg].upos[upos].featvals:
                    self.lgs[lg].upos[upos].featvals[feat] = 0
                self.lgs[lg].upos[upos].featvals[feat] += 1

    def out(self):
        for lg in self.lgs:
            print(self.lgs[lg].out(self.threshold))
        for f in self.features:
            print(f, " ".join(self.features[f]), sep="\t")


    def toFeatsJson(self):
        # same format as UD tools/data/feats.json
        dico = { "Generated_by": " ".join(sys.argv),
                 "features" : {}
                 }
        for lg in self.lgs:
            lg2 = lg.split("_")[0]
            dico["features"][lg2] = self.lgs[lg].toFeatsJson(self.features, self.threshold)

        print(json.dumps(dico, indent=2, cls=SetJson, sort_keys=False))


    def toJson(self):
        dico = { "features": {},
                 "values": {} }
        for lg in self.lgs:
            dico["features"][lg] = self.lgs[lg].toJson(self.threshold)
            
        for f in self.features:
            #print(f, " ".join(self.features[f]), sep="\t")
            dico["values"][f] = sorted(self.features[f])

        print(json.dumps(dico, indent=2))


if __name__ == '__main__':

    import argparse

    parser = argparse.ArgumentParser(description="Collect UPOS : Feature assignements")
    parser.add_argument("--files", "-f", nargs="+", required=True, help="Conllu files")
    parser.add_argument("--out", "-o", help="output format: txt, json, feats")
    parser.add_argument("--keep", "-k", nargs="+", help="features to keep with every UPOS")
    parser.add_argument("--featvalues", "-v", default=False, action="store_true", help='use features+values')
    parser.add_argument("--threshold", "-t", type=float, default=0.0, help="minimal rate to output feature as part of a given UPOS")


    if len(sys.argv) < 2:
        parser.print_help()
    else:
        args = parser.parse_args()
        ufv = UposFeatVal()
    
        ufv.readconllufiles(args.files, args.keep, args.featvalues, args.threshold)
        if args.out == "json":
            ufv.toJson()
        elif args.out == "feats":
            ufv.toFeatsJson()
        else:
            ufv.out()
        

