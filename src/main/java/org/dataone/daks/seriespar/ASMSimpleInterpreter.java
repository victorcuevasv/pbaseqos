
package org.dataone.daks.seriespar;

import org.antlr.runtime.*;
import org.antlr.runtime.tree.*;
import java.util.List;
import java.util.ArrayList;


public class ASMSimpleInterpreter {
	
	public InterpreterListener listener = // default response to messages
        new InterpreterListener() {
            
    		public void info(String msg) { 
            	System.out.println(msg);
            }
            
    		public void error(String msg) {
            	System.err.println(msg);
            }
            
    		public void error(String msg, Exception e) {
                error(msg);
                e.printStackTrace(System.err);
            }
            
    		public void error(String msg, Token t) {
                error("line " + t.getLine() + ": " + msg);
            }
        };

    
    
    protected Tree root;
    protected TokenRewriteStream tokens;
    protected ASMSimpleLexer lexer;
    protected ASMSimpleParser parser;
    

    public void init(String inputStr) throws RecognitionException {
        
        this.lexer = new ASMSimpleLexer(new ANTLRStringStream(inputStr));
        this.tokens = new TokenRewriteStream(lexer);
        this.parser = new ASMSimpleParser(tokens);
        ASMSimpleParser.asm_return result = parser.asm();
        if ( parser.getNumberOfSyntaxErrors() == 0 ) {
            this.root = (Tree)result.getTree();
            //System.out.println("tree: " + root.toStringTree());
            List<String> outputs = this.exec(root.getChild(0).getChild(0), new ArrayList<String>());
            for(int i = 0; i < outputs.size(); i++)
            	System.out.println(outputs.get(i));
        }
    }
    
    
    /** visitor dispatch according to node token type */
    public List<String> exec(Tree t, List<String> inputs) {
        try {
            
            switch ( t.getType() ) {
            	case ASMSimpleParser.SEQBLOCK : return seqblock(t, inputs);
            	case ASMSimpleParser.PARBLOCK : return parblock(t, inputs);
                case ASMSimpleParser.ID : return id(t, inputs);
                default : // catch unhandled node types
                    throw new UnsupportedOperationException("Node " +
                        t.getText() + "<" + t.getType() + "> not handled");
            }
            
        }
        catch (Exception e) {
            listener.error("problem executing " + t.toStringTree(), e);
        }
        return null;
    }
    
    
    public List<String> seqblock(Tree t, List<String> inputs) {
    	List<String> retList = null;
    	List<String> inList = copyList(inputs);
    	for(int i = 0; i < t.getChildCount(); i++) {
    		Tree stmt = t.getChild(i);
    		retList = this.exec(stmt, inList);
    		inList = this.copyList(retList);
    	}
    	return retList;
    }
    
    
    public List<String> parblock(Tree t, List<String> inputs) {
    	List<String> retList = new ArrayList<String>();
    	List<String> inList = copyList(inputs);
    	List<String> outList = null;
    	for(int i = 0; i < t.getChildCount(); i++) {
    		Tree stmt = t.getChild(i);
    		outList = this.exec(stmt, inList);
    		retList = this.joinLists(retList, outList);
    	}
    	return retList;
    }
    
    
    public List<String> id(Tree t, List<String> inputs) {
    	List<String> list = new ArrayList<String>();
    	list.add(t.getText() + "_out");
    	System.out.println(t.getText() + " used: " + listAsString(inputs) + " generated: " + t.getText() + "_out");
    	return list;
    }
    
    
    public List<String> copyList(List<String> list) {
    	List<String> copyList = new ArrayList<String>();
    	for(int i = 0; i < list.size(); i++)
    		copyList.add(list.get(i));
    	return copyList;
    }
    
    
    public List<String> joinLists(List<String> list1, List<String> list2) {
    	List<String> joinedList = new ArrayList<String>();
    	for(int i = 0; i < list1.size(); i++)
    		joinedList.add(list1.get(i));
    	for(int i = 0; i < list2.size(); i++)
    		joinedList.add(list2.get(i));
    	return joinedList;
    }
    
    
    public String listAsString(List<String> list) {
    	StringBuilder builder = new StringBuilder();
    	for(int i = 0; i < list.size(); i++)
    		builder.append(list.get(i) + " ");
    	return builder.toString();
    }
	
	
}


