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

package com.orange.labs.conllparser;

import java.util.Set;

/**
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class Annotation implements Comparable {
    public static boolean showrule = true;
    final boolean target;
     boolean begin;
    final String semanticRole;
    final String frame; 
    final String frameElement_verb;
    Integer ident = -1; // -1: inconnu
    boolean framenet; // true if it is a real Frame from FrameNet (and not a basic semantic relations)
    String rulename = null; // name of the rule which produces this Annotation (if FE)

    public void setRulename(String r) {
        rulename = r;
    }
    
    public Annotation(String semanticrole, String concept, Integer verbID, boolean target, boolean begin) {
        this.target = target;
        this.begin = begin;
        frame = concept;
        this.semanticRole = semanticrole;
        this.frameElement_verb = semanticrole;
        ident = verbID; 
        framenet = false;   
    }
    
    
    public Annotation(Annotation orig) {
        target = orig.target;
        begin = orig.begin;
        frame = orig.frame;
        frameElement_verb = orig.frameElement_verb;
        ident = orig.ident;
        framenet = orig.framenet;
        semanticRole = orig.semanticRole;
        rulename = orig.rulename;
    }
    
    /** parser les annotations dans les fichiers CONLL annotés par le LIF (Univ Marseille) */
    public Annotation(String a, String semrole, Set<Annotation> lastannots) throws ConllException {
        semanticRole = semrole;
        //System.out.println("AAA " + a);
        String[] elems = a.split(":");
        if (elems.length < 4) {
            throw new ConllException("no frame annotation " + a);
        }
        else if (elems[1].equals("OTHER")) {
            throw new ConllException("no target nor frame-element defined " + a);
        }

        target = elems[2].equals("TARGET");         
        begin = elems[0].equals("B");
        frame = elems[1];

        frameElement_verb = elems[3];
        //System.err.println("aaaaa: " + a);
        if (elems.length > 5) {
            if (elems[5].length() > 0 && !"?".equals(elems[5]))
            ident = Integer.parseInt(elems[5]);
        } else if (lastannots != null) {
            //System.out.println("zzz " + lastannots);
            // on essaie de déduire le ident à partir des annotations précédents
            for (Annotation prev : lastannots) {

                if (prev.target == target
                        && prev.frame.equals(frame)
                        && prev.frameElement_verb.equals(frameElement_verb)) {
                    ident = prev.ident;

                    break;
                }
            }
           // if (ident.isEmpty()) {
           //     ident = "?";
           // }

        } else {
            ident = -1;
        }
        framenet = true;
    }

    public boolean isTarget() { return target; }
    public boolean isBegin() { return begin; }
    public void setBegin(boolean b) {begin = b;}
    public boolean isFramenet() { return framenet; }
    public String getFrame() { return frame; }
    public Integer getIdent() { return ident; }
    public String getSemanticRole() { return semanticRole; }
    public String getFrameElement_or_verb() { return frameElement_verb; }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(begin ? "B:" : "I:");
        sb.append(frame).append(':');

        if (target) {
            sb.append("TARGET:");
        } else {
            //if (rulename != null)
            //    sb.append("FE_").append(rulename).append(':');
            //else
            sb.append("FE:");
        }
        
        sb.append(frameElement_verb).append(":G:");
        if (!ident.equals(-1))
            sb.append(ident);
        else
            sb.append("?");
        
        if (showrule) {
            if (!target) {
                if (rulename != null)
                    sb.append(":").append(rulename);
                else sb.append(":MISSING");
            }
            else sb.append(":NONE");
        }
        
        return sb.toString();
    }
    
    
    public String toStringWithoutIdent() {
        StringBuilder sb = new StringBuilder();
        sb.append(begin ? "B:" : "I:");
        sb.append(frame).append(':');

        if (target) {
            sb.append("TARGET:");
        } else {
              //if (rulename != null)
               // sb.append("FE").append(rulename);
            //else
                sb.append("FE:");
        }
        
        sb.append(frameElement_verb);
        return sb.toString();
    }
  
    public int hashCode() {
        return this.toString().hashCode();
    }
    
    
    public boolean equals(Object annot) {
        if (!(annot instanceof Annotation)) return false;
        Annotation a =(Annotation)annot;
        if (target != a.target) return false;
        if (begin != a.begin) return false;
        if (! frame.equals(a.frame)) return false;
        if (! frameElement_verb.equals(a.frameElement_verb)) return false;
        if (! ident.equals(a.ident)) return false;
        return true;
    }

    @Override
    public int compareTo(Object annot) {
        if (!(annot instanceof Annotation)) return 1;
        Annotation a =(Annotation)annot;
        if (!ident.equals(a.ident)) return ident.compareTo(a.ident);
        if (!frame.equals(a.frame)) return frame.compareTo(a.frame);
        if (target != a.target) {
            if (target) return -1; else return 1;
        }
        if (begin != a.begin) {
            if (!begin) return -1; else return 1;
        }
        if (!frameElement_verb.equals(a.frameElement_verb)) return frameElement_verb.compareTo(a.frameElement_verb);
        return 0;
    }
    
}
