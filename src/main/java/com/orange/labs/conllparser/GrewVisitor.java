/* This library is under the 3-Clause BSD License

Copyright (c) 2018-2022, Orange S.A.

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
 @version 2.20.0 as of 2nd December 2022
 */
package com.orange.labs.conllparser;

import com.orange.labs.conllparser.GrewmatchParser.DeprelContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class GrewVisitor extends GrewmatchBaseVisitor<Boolean> {

    //ConllWord cword = null; // all conditions are checked on this word
    //ConllWord pointedWord = null; // child/head etc are followed here
    int level = 0; // -1 head, -2 head's head
    int sequence = 0; // -1 word to the left, 1 word to the right etc

    Map<String, Node> nodes; // id: Node
    Map<String, Rel> relations; // dep: Rel
    Node curnode = null;
    Rel currel = null;
    Map<String, String> identicalnodes;
    Map<String, String> differentnodes;

    Map<String, Set<String>> wordlists; // stores lists for Form and Lemma: "filename": (words)
    Map<String, String> before;
    Map<String, String> strictlybefore;
    boolean without = false; // whether or not we parse "without" expressions

    public GrewVisitor(//ConllWord cword,
            Map<String, Set<String>> wordlists) {
        //this.cword = cword;
        //pointedWord = cword;
        this.wordlists = wordlists;
        nodes = new TreeMap<>();
        relations = new TreeMap<>();
        identicalnodes = new HashMap<>();
        differentnodes = new HashMap<>();
        before = new HashMap<>();
        strictlybefore = new HashMap<>();
    }

    /**
     * get the correct ConllWord (current word, its head, head's head, preceing
     * or following). If there is no word at the end of the path (since the path
     * demanded is not in this tree), null is returned
     */
//    private ConllWord getCW() {
//        return pointedWord;
//    }
    // TODO put into CheckGrewmatch ?
    // TODO optimze, far too slow!!!!
    public List<List<ConllWord>> match(ConllSentence csent) {
        final boolean debug = false;
        // find list of nodes which match
        // check whether all relations of query can be established between nodes
        // delete relations if order constraints exist

        // TODO: what does match() return if no node in query ('pattern {A -> B}')
        // find nodes which match every variable
        Map<String, List<ConllWord>> matchednodes = new TreeMap<>(); // nodename (in rule): CW
        //Map<Integer, String> id2node = new TreeMap<>(); // cw id: nodename
        for (ConllWord cw : csent.getWords()) {
            List<String> rtc = match(cw);
            for (String nodename : rtc) {
                List<ConllWord> cws = matchednodes.get(nodename);
                if (cws == null) {
                    cws = new ArrayList<>();
                    matchednodes.put(nodename, cws);
                }
                cws.add(cw);
                //id2node.put(cw.getId(), nodename);
            }
            if (debug) {
                System.out.println("CW " + cw);
                System.out.println("   " + rtc);
            }
        }

        if (debug) {
            System.out.println("MATCHEDNODES " + matchednodes);
            System.out.println(" sss " + nodes);
        }

        // check whether every nodename matches a CW
        if (matchednodes.size() != nodes.size()) {
            return null;
        }

        if (debug) {
            printmap2(matchednodes);
        }

        List<String> node_order = new ArrayList<>();
        for (String nodename : matchednodes.keySet()) {
            node_order.add(nodename);
        }

        if (!nodes.isEmpty() && matchednodes.isEmpty()) {
            return null; // nothing found
        }

        // check for all possible combinations of nodes, whether some combinations
        // are impossible due to relation/order constraints
        // first create a list of combinations
        List<List<ConllWord>> node_combinations = new ArrayList<>();
        for (List<ConllWord> lcw : matchednodes.values()) {
            node_combinations.add(lcw);
        }
        node_combinations = cartesianProduct(node_combinations);
        List<List<ConllWord>> final_node_combinations = new ArrayList<>();
        int num_nodes = matchednodes.size();
        //System.out.println("dsd" + num_nodes);

        // do relations and order constraint inhibit a n-tuple of nodes ?
        if (debug) {
            System.out.println("COMBIS " + node_combinations.size());
        }
        for (List<ConllWord> lcw : node_combinations) {
            Set<ConllWord> tmp = new HashSet<>(lcw);
            if (tmp.size() == num_nodes) {
                //System.out.println("COMBINATION");
                Map<String, ConllWord> node2cw = new HashMap<>();
                int i = 0;
                for (ConllWord cw : lcw) {
                    node2cw.put(node_order.get(i), cw);
                    //System.out.println("   " + node_order.get(i) + " " + cw.getFullId());
                    i++;
                }

                // for all order constraints
                boolean ok = true;
                for (String former : before.keySet()) {
                    String latter = before.get(former);
                    //find CWs associated with nodenames (in current tuple)
                    ConllWord formerid = node2cw.get(former);
                    ConllWord latterid = node2cw.get(latter);
                    if (debug) {
                       System.out.println("ORDER CHECK " + formerid + " " + former + " before " + latterid + " " + latter);
                    }
                    if (formerid.getId() >= latterid.getId()) {
                        ok = false;
                        if (debug) System.out.println(" ORDER FAILED");
                        break;
                    }
                    //System.out.println(" TTTT");
                }
                if (!ok) {
                    continue;
                }

                // for all strict order constraints
                for (String former : strictlybefore.keySet()) {
                    String latter = strictlybefore.get(former);
                    //find CWs associated with nodenames (in current tuple)
                    ConllWord formerid = node2cw.get(former);
                    ConllWord latterid = node2cw.get(latter);
                    
                    if (debug) {
                       System.out.println("ORDER CHECK " + formerid + " " + former + " strictlybefore " + latterid + " " + latter);
                    }

                    if (formerid.getId() + 1 != latterid.getId()) {
                        ok = false;
                        if (debug) System.out.println(" STRICT ORDER FAILED");
                        break;
                    }
                    //System.out.println(" TTTT");
                }
                if (!ok) {
                    continue;
                }

                // check relation constraints
                for (String rid : relations.keySet()) {
                    Rel rel = relations.get(rid);
                    ConllWord head = node2cw.get(rel.head);
                    ConllWord dep = node2cw.get(rel.dep);

                    if (debug) {
                        System.out.println("CHECK REL " + rel);
                        System.out.println("  head " + head);
                        System.out.println("  dep  " + dep);
                    }

                    if (!rel.without) {
                        if (dep.getHeadWord() != head) {
                            // the head is not the head requred
                            ok = false;
                            if (debug) {
                                System.out.println("BAD HEAD");
                            }
                            break;
                        }
                        if (rel.deprels != null 
                                //&& !rel.deprel.equals(dep.getDeplabel())
                                && !rel.deprels.contains(dep.getDeplabel())
                                ) {
                            // dependant does not have the deprel required
                            ok = false;
                            if (debug) {
                                System.out.println("NOT REQUIRED DEPREL");
                            }
                            break;
                        }
                    } else {
                        if (dep == null) {
                            // no dep node defined as node, so check whether the head does not
                            // have a dependent with forbidden relation
                            for (ConllWord d : head.getDependents()) {
                                //if (d.getDeplabel().equals(rel.deprel)) {
                                if (rel.deprels.contains(d.getDeplabel())) {
                                    ok = false;
                                    if (debug) {
                                        System.out.println("FORBIDDEN REPREL");
                                    }
                                }
                            }
                        } else if ((dep.getHeadWord() == head || head == null)
                                && rel.deprels != null
                                //&& rel.deprel.equals(dep.getDeplabel())
                                && rel.deprels.contains(dep.getDeplabel())
                                ) {
                            // dependant hase the forbidden deprel
                            ok = false;
                            if (debug) {
                                System.out.println("FORBIDDEN REPREL");
                            }
                            break;
                        }
                    }
                }
                if (!ok) {
                    continue;
                }

                final_node_combinations.add(lcw);

            }
        }

        //System.out.println("FOUND1 " + final_node_combinations.size());
//        for (List<ConllWord> lcw : final_node_combinations) {
//            System.out.println("FINAL");
//            for (ConllWord cw : lcw) {
//                System.out.println("  " + cw);
//            }
//        }
        if (!final_node_combinations.isEmpty()) {
            return final_node_combinations;
        } else {
            return null;
        }
    }

    //https://stackoverflow.com/questions/714108/cartesian-product-of-an-arbitrary-number-of-sets
    private <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> resultLists = new ArrayList<>();
        if (lists.isEmpty()) {
            resultLists.add(new ArrayList<>());
            return resultLists;
        } else {
            List<T> firstList = lists.get(0);
            List<List<T>> remainingLists = cartesianProduct(lists.subList(1, lists.size()));
            for (T condition : firstList) {
                for (List<T> remainingList : remainingLists) {
                    ArrayList<T> resultList = new ArrayList<>();
                    resultList.add(condition);
                    resultList.addAll(remainingList);
                    resultLists.add(resultList);
                    //System.out.println("zzzz " + resultList.size() + " " + resultList);
                }
            }
        }
        return resultLists;
    }

    private void printmap(Map m) {
        for (Object k : m.keySet()) {
            System.out.println("===" + k + "\t" + m.get(k));
        }
    }

    private void printmap2(Map<?, ?> m) {
        for (Object k : m.keySet()) {
            System.out.println("===" + k);
            for (Object k2 : (List) m.get(k)) {
                System.out.println("\t" + k2);
            }
        }
    }

    private List<String> match(ConllWord cw) {
        final boolean debug = false;
        boolean ok = true; // empty expression matches always

        // for all nodes
        List<String> matched = new ArrayList<>();
        if (debug) {
            System.out.println("Try to match " + cw);
        }
        for (String n : nodes.keySet()) {
            if (debug) {
                System.out.println("..NODE " + n);
            }
            ok = true;
            Node nd = nodes.get(n);
            for (String f : nd.must_feats.keySet()) {
                boolean featok = false;
                for (String val : nd.must_feats.get(f)) {
                    // positive: one of all must be ok
                    if (debug) {
                        System.out.println("CHECKING " + f + "=" + val);
                    }
                    switch (f) {
                        case "upos":
                            if (cw.getUpostag().equals(val)) {
                                // feature OK
                                featok = true;
                            }
                            break;
                        case "xpos":
                            if (cw.getXpostag().equals(val)) {
                                // feature OK
                                featok = true;
                            }
                            break;
                        case "form":
                            if (cw.getForm().equals(val)) {
                                // feature OK
                                featok = true;
                            }
                            break;
                        case "lemma":
                            if (cw.getLemma().equals(val)) {
                                // feature OK
                                featok = true;
                            }
                            break;
                        case "deprels":
                            if (cw.getDeplabel().equals(val)) {
                                // feature OK
                                featok = true;

                            }
                            break;
                        default:
                            if (val.isEmpty()) {
                                // feature must be there
                                if (cw.hasFeature(f)) {
                                    featok = true;
                                }
                            } else {
                                if (cw.hasFeature(f, val)) {
                                    featok = true;
                                }
                            }
                    }
                    if (debug) {
                        System.out.println("checkresult " + featok);
                    }
                }
                if (!featok) {
                    // node invalid
                    ok = false;
                    break;
                }

            }
            if (ok) {
                for (String f : nd.must_not_feats.keySet()) {
                    boolean featok = true;
                    for (String val : nd.must_not_feats.get(f)) {
                        // negative, non feat must fit
                        if (debug) {
                            System.out.println("CHECKING NEGATIV " + f + "<>" + val + "   " + featok);
                        }
                        switch (f) {
                            case "upos":
                                if (cw.getUpostag().equals(val)) {
                                    // feature present
                                    featok = false;
                                }
                                break;
                            case "xpos":
                                if (cw.getXpostag().equals(val)) {
                                    // feature present
                                    featok = false;
                                }
                                break;
                            case "lemma":
                                if (cw.getLemma().equals(val)) {
                                    // feature present
                                    featok = false;
                                }
                                break;
                            case "form":
                                if (cw.getForm().equals(val)) {
                                    // feature present
                                    featok = false;
                                }
                                break;
                            case "deprels":
                                if (cw.getDeplabel().equals(val)) {
                                    featok = false;
                                }
                                break;
                            default:
                                //System.out.println("WWWWWW " + f + " <" + val + "> " + cw.hasFeature(f));
                                if (val.isEmpty()) {
                                    if (cw.hasFeature(f)) {
                                        featok = false;
                                    }
                                } else {
                                    // N [Number<>Sing] is true, when feature Number is present but with a value different than Sing
                                    if (!cw.hasFeature(f) || cw.hasFeature(f, val)) {
                                        featok = false;
                                    }
                                }
                        }
                        if (debug) {
                            System.out.println("neg checking result " + featok);
                        }
                    }
                    if (!featok) {
                        // node invalid
                        ok = false;
                        break;
                    }

                }
            }
            if (ok) {
                matched.add(n);
            }

        }
        return matched;
    }

    public void out() {
        System.out.println("===================");
        for (String n : nodes.keySet()) {
            System.out.println("NODE: " + n + " " + nodes.get(n).toString());
        }
        for (String n : identicalnodes.keySet()) {
            System.out.println("ID: " + n + " " + identicalnodes.get(n).toString());
        }
        for (String n : differentnodes.keySet()) {
            System.out.println("NOT ID: " + n + " " + differentnodes.get(n).toString());
        }
        for (String n : before.keySet()) {
            System.out.println("BEFORE: " + n + " before " + before.get(n).toString());
        }
        for (String n : strictlybefore.keySet()) {
            System.out.println("STRICTLYBEFORE: " + n + " before " + strictlybefore.get(n).toString());
        }

        for (String n : relations.keySet()) {
            System.out.println("REL " + relations.get(n).toString());
        }
        System.out.println("===================");
    }

    @Override
    public Boolean visitFinal(GrewmatchParser.FinalContext ctx) {
        boolean value = visit(ctx.pattern());
        if (ctx.without() != null) {
            for (GrewmatchParser.WithoutContext cc : ctx.without()) {
                value = value && visit(cc);
            }
        }
        return value;
    }

    @Override
    public Boolean visitPatternlist(GrewmatchParser.PatternlistContext ctx) {
        boolean value = true;
        for (GrewmatchParser.RheolContext rc : ctx.rheol()) {
            //value = value && 
            visit(rc);
        }

        return value;
    }

    @Override
    public Boolean visitWithoutlist(GrewmatchParser.WithoutlistContext ctx) {
        boolean value = true;
        without = true;
        for (GrewmatchParser.RheolContext rc : ctx.rheol()) {
            //value = value && 
            visit(rc);
        }

        without = false;
        return value;
    }

    @Override
    public Boolean visitCondlist(GrewmatchParser.CondlistContext ctx) {
        // for each condition in pattern
        String nodename = ctx.nodename().getText();
        curnode = nodes.get(nodename);
        if (curnode == null) {
            curnode = new Node(nodename);
            nodes.put(nodename, curnode);
        }
        //System.out.println("NN " + nodename);
        for (GrewmatchParser.ConditionContext cc : ctx.condition()) {
            //System.out.println("COND " + cc.getText());
            visit(cc);
        }
        curnode = null;

        return true;
    }

    @Override
    public Boolean visitCond(GrewmatchParser.CondContext ctx) {
        // for each condition in pattern
        String left = ctx.conllucolumn().getText();
        //System.out.println("left " + left);
       
        boolean neg = without;
        if (ctx.NOT() != null) neg = !neg;
        curnode.addfeat(left, "", neg);
           
        curnode.seal(left);
        //System.out.println("SEAL " + left);
        //boolean value = visit(ctx.pattern()); // evaluate the expression child
        return true; //value;
    }
    
    @Override
    public Boolean visitCond2(GrewmatchParser.Cond2Context ctx) {
        // for each condition in pattern
        String left = ctx.conllucolumn().getText();
        //System.out.println("left " + left);
        String cp = ctx.eq().getText();
        boolean neg = without;
        if (cp.equals("<>")) {
            neg = !neg;
        }
        //System.out.println("qqqq " + cp + " " + without + " " + neg);
        for (GrewmatchParser.StringContext cc : ctx.string()) {
            //System.out.println("STRING " + left + " " + cc.getText());
            curnode.addfeat(left, cc.getText(), neg);
            visit(cc);
        }
        for (GrewmatchParser.UtfstringContext cc : ctx.utfstring()) {
            //System.out.println("USTRING " + left + " " + cc.getText());
            String us = cc.getText();
            curnode.addfeat(left, us.substring(1, us.length() - 1), neg);
            visit(cc);
        }
        curnode.seal(left);
        //System.out.println("SEAL " + left);
        //boolean value = visit(ctx.pattern()); // evaluate the expression child
        return true; //value;
    }

    @Override
    public Boolean visitOrder(GrewmatchParser.OrderContext ctx) {
        String one = ctx.nodename(0).getText();
        String two = ctx.nodename(1).getText();

        Node cn = nodes.get(one);
        if (cn == null) {
            cn = new Node(one);
            nodes.put(one, cn);
        }
        cn = nodes.get(two);
        if (cn == null) {
            cn = new Node(two);
            nodes.put(two, cn);
        }

        String cp;
        if (ctx.eq() != null) {
            cp = ctx.eq().getText();
        } else {
            cp = ctx.comp().getText();
        }
        switch (cp) {
            case "<<":
                before.put(one, two);
                break;
            case ">>":
                before.put(two, one);
                break;
            case "<":
                strictlybefore.put(one, two);
                break;
            case ">":
                strictlybefore.put(two, one);
                break;
        }
        return true;
    }

//    @Override
//    public Boolean visitOrder2(GrewmatchParser.Order2Context ctx) {
//        String one = ctx.nodenamefield(0).getText();
//        String two = ctx.nodenamefield(1).getText();
//        System.out.println("ooooooooooooooooooooooo " + one);
//        //ctx.nodenamefield(1).
//        Node cn = nodes.get(one);
//        if (cn == null) {
//            cn = new Node(one);
//            nodes.put(one, cn);
//        }
//        cn = nodes.get(two);
//        if (cn == null) {
//            cn = new Node(two);
//            nodes.put(two, cn);
//        }
//
//        String cp;
//        if (ctx.eq() != null) {
//            cp = ctx.eq().getText();
//        } else {
//            cp = ctx.comp().getText();
//        }
//        switch (cp) {
//            case "<<":
//                before.put(one, two);
//                break;
//            case ">>":
//                before.put(two, one);
//                break;
//            case "<":
//                strictlybefore.put(one, two);
//                break;
//            case ">":
//                strictlybefore.put(two, one);
//                break;
//        }
//        return true;
//    }

    
    @Override
    public Boolean visitRelation(GrewmatchParser.RelationContext ctx) {
        String head = ctx.nodename(0).getText();
        String dep = ctx.nodename(1).getText();

        if (!without) {
            Node cn = nodes.get(head);
            if (cn == null) {
                cn = new Node(head);
                nodes.put(head, cn);
            }
            cn = nodes.get(dep);
            if (cn == null) {
                cn = new Node(dep);
                nodes.put(dep, cn);
            }
        }
        String relval = null;
        if (ctx.relval() != null) {
            relval = ctx.relval().getText();
        }
        currel = new Rel(relval, head, dep, null, without);
        relations.put(dep, currel);
        return true;
    }

    @Override
    public Boolean visitNamedrelation(GrewmatchParser.NamedrelationContext ctx) {
        String head = ctx.nodename(0).getText();
        String dep = ctx.nodename(1).getText();
        List<String>deprels = new ArrayList<>();
      
        for (DeprelContext c : ctx.deprel()) {
            deprels.add(c.getText());
        }
        
        boolean neg = without;
        if (ctx.NOT() != null) neg = !neg;
        
        if (!without) {
            Node cn = nodes.get(head);
            if (cn == null) {
                cn = new Node(head);
                nodes.put(head, cn);
            }
            cn = nodes.get(dep);
            if (cn == null) {
                cn = new Node(dep);
                nodes.put(dep, cn);
                cn.addfeats("deprels", deprels, neg);
            }
        }
        String relval = null;
        if (ctx.relval() != null) {
            relval = ctx.relval().getText();
        }
        currel = new Rel(relval, head, dep, deprels, neg);
        relations.put(dep, currel);
        return true;
    }

    class Node {

        String id;

        Map<String, List<String>> must_feats;
        Map<String, List<String>> must_not_feats;
        Set<String> sealed; // to avoid { N [upos=1]; N [upos=2] }

        public Node(String id) {
            this.id = id;
            must_feats = new HashMap<>();
            must_not_feats = new HashMap<>();
            sealed = new HashSet<>();
        }

        public void seal(String cat) {
            sealed.add(cat);
        }

        public void addfeat(String k, String v, boolean negated) throws GrewException {
            if (sealed.contains(k)) {
                throw new GrewException("inconsistent nodes");
            }
            //System.out.println("ADDFEAT " + k + " " + v);
            if (negated) {
                List<String> vals = must_not_feats.get(k);
                if (vals == null) {
                    vals = new ArrayList<>();
                    must_not_feats.put(k, vals);
                }
                vals.add(v);
            } else {
                List<String> vals = must_feats.get(k);
                if (vals == null) {
                    vals = new ArrayList<>();
                    must_feats.put(k, vals);
                }
                vals.add(v);
            }
        }

         public void addfeats(String k, List<String> v, boolean negated) throws GrewException {
            if (sealed.contains(k)) {
                throw new GrewException("inconsistent nodes");
            }
            //System.out.println("ADDFEAT " + k + " " + v);
            if (negated) {
                List<String> vals = must_not_feats.get(k);
                if (vals == null) {
                    vals = new ArrayList<>();
                    must_not_feats.put(k, vals);
                }
                vals.addAll(v);
            } else {
                List<String> vals = must_feats.get(k);
                if (vals == null) {
                    vals = new ArrayList<>();
                    must_feats.put(k, vals);
                }
                vals.addAll(v);
            }
        }
        
        public String toString() {
            StringBuilder tmp = new StringBuilder();
            tmp.append(id).append("::");
            for (String k : must_feats.keySet()) {
                tmp.append(k).append(':').append(must_feats.get(k)).append('/');
            }
            for (String k : must_not_feats.keySet()) {
                tmp.append(k).append(":!").append(must_not_feats.get(k)).append('/');
            }
            return tmp.toString();

        }

    }

    class Rel {

        String id;
        List<String> deprels;
        String head;
        String dep;
        boolean without = false;

        public Rel(String id, String head, String dep, List<String> deprels, boolean without) {
            this.id = id;
            this.head = head;
            this.dep = dep;
            this.deprels = deprels;
            this.without = without;
        }

        public String toString() {
            return id + " " + (without ? '!' : "") + head + " --" + deprels + "--> " + dep;
        }

    }

}
