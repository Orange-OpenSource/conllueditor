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

package com.orange.labs.editor;

import com.orange.labs.conllparser.ConllSentence;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 *
 * @author Johannes Heinecke <johannes.heinecke@orange.com>
 */
public class History {
    Deque<ConllSentence> history; // last n sentences
    Deque<ConllSentence> future; // next sentences (filled during undo)
    final boolean debug = false;
    int size;

    public History(int size) {
        this.size = size;
        history = new ArrayDeque<>();
        future = new ArrayDeque<>();
    }

    public void add(ConllSentence cs) {
        // add new sentence, if we arrive at size, we kill the oldest entry
        history.addLast(new ConllSentence(cs));
        if (history.size() > size) history.pollFirst();
        // we also empty future
        future.clear();
        if (debug) {
            System.err.println("ADDED " + cs);
            System.err.println("H: " + history.size());
            listwords(history);
            System.err.println("F: " + future.size());
            listwords(future);
        }
    }

    /** returns true if an undo is possible */
    public boolean canUndo() {
        return !history.isEmpty();
    }

    public boolean canRedo() {
        return !future.isEmpty();
    }


    /** get before last version and move last version to future */
    public ConllSentence undo(ConllSentence current) {
        if (!history.isEmpty()) {
            ConllSentence last = history.pollLast();

            future.addLast(current);
            if (future.size() > size) future.pollFirst();

            if (debug) {
                System.err.println("UNDONE to " + last);
                System.err.println("H: " + history.size());
                listwords(history);
                System.err.println("F: " + future.size());
                listwords(future);
            }
            return last;
        }
        return null;
    }

     public ConllSentence redo() {
         if (!future.isEmpty()) {
            ConllSentence cs = future.pollLast();
            ConllSentence csforundo = new ConllSentence(cs);
            history.addLast(csforundo);
            if (debug) {
                System.err.println("UNDONE to " + cs);
                System.err.println("H: " + history.size());
                listwords(history);
                System.err.println("F: " + future.size());
                listwords(future);
            }
            return cs;
         }
         return null;
     }

    private void listwords(Deque<ConllSentence> d) {
        int i = 0;
        for (ConllSentence cs : d) {

            System.err.println(i + " " + cs.getWord(1));
            i++;
        }
    }
}
