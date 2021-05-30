/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.orange.labs.conllparser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/**
 *
 * @author 
 */
public class ConditionalReplace {
   public static void main(String argv[]) {
       
       ConditionsLexer lexer = new ConditionsLexer(CharStreams.fromString(argv[0]));

	//Then, we instantiate the parser:

	CommonTokenStream tokens = new CommonTokenStream(lexer);
	ConditionsParser parser = new ConditionsParser(tokens);
System.err.println("zz " + parser);
	//ParseTree tree = parser.compilationUnit();

	//And then, the walker and the listener:

	//ParseTreeWalker walker = new ParseTreeWalker();
	//UppercaseMethodListener listener= new UppercaseMethodListener();

	//Lastly, we tell ANTLR to walk through our sample class:
	//walker.walk(listener, tree);
    }
    
}
