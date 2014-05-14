
package org.dataone.daks.seriespar;

import org.antlr.runtime.*;
import org.antlr.runtime.tree.*;

import org.dataone.daks.pbase.treecover.Digraph;

import java.util.List;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;


public class ASMSimpleSimulator {
	
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
    protected Hashtable<String, Boolean> failed;
    protected Digraph digraph;
    protected List<String> asmInProcesses;
    protected Hashtable<String, Integer> asmInProcessesNumInputs;
    protected double failureProb;
    protected Random rand;
    protected double minTime;
    protected double maxTime;
    protected double minCost;
    protected double maxCost;
    protected Hashtable<String, QoSMetrics> qosHT;
    
    
    public ASMSimpleSimulator() {
    	this.rand = new Random();
    	this.failureProb = randDouble(this.rand, 0, 0.05);
    	this.asmInProcessesNumInputs = null;
    }
    
    
    public ASMSimpleSimulator(double minTime, double maxTime, double minCost, double maxCost) {
    	this();
    	this.minTime = minTime;
    	this.maxTime = maxTime;
    	this.minCost = minCost;
    	this.maxCost = maxCost;
    }
    

    public void init(String inputStr) throws RecognitionException {
        this.lexer = new ASMSimpleLexer(new ANTLRStringStream(inputStr));
        this.tokens = new TokenRewriteStream(lexer);
        this.parser = new ASMSimpleParser(tokens);
        ASMSimpleParser.asm_return result = parser.asm();
        if ( parser.getNumberOfSyntaxErrors() == 0 ) {
            this.root = (Tree)result.getTree();
            //System.out.println("tree: " + root.toStringTree());
            this.failed = new Hashtable<String, Boolean>();
            this.digraph = new Digraph();
            this.qosHT = new Hashtable<String, QoSMetrics>();
            this.asmInProcesses = new ArrayList<String>();
        }
    }
    
    
    public void run() {
    	List<String> outputs = this.exec(root.getChild(0).getChild(0), new ArrayList<String>());
    	if( this.asmInProcessesNumInputs == null )
    		this.createAsmInProcessesNumInputs(1, 2);
    	this.addAsmInputs();
        //for(int i = 0; i < outputs.size(); i++)
        	//System.out.println(outputs.get(i));
    }
    
    
    public Digraph getTraceDigraph() {
    	return this.digraph;
    }
    
    
    public Hashtable<String, QoSMetrics> getQoSHT() {
    	return this.qosHT;
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
    		if( this.checkFailures(retList) )
    			break;
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
    	//Check if the process does not have any inputs, i.e., it is an IN process
    	if( inputs.size() == 0 )
    		this.asmInProcesses.add(t.getText());
    	//Create list with output
    	List<String> list = new ArrayList<String>();
    	list.add(t.getText() + "_out");
    	//Simulate possible failure
    	double val = randDouble(this.rand, 0, 1.0);
    	boolean failure = false;
    	if( val < this.failureProb )
    		failure = true;
    	//Add the QoS metrics
    	double reliability = 1.0;
    	if( failure )
    		reliability = 0.0;
    	double time = randDouble(this.rand, this.minTime, this.maxTime);
    	double cost = randDouble(this.rand, this.minCost, this.maxCost);
    	QoSMetrics qosMetrics = new QoSMetrics(time, cost, reliability);
    	this.qosHT.put(t.getText(), qosMetrics);
    	//System.out.println(t.getText() + " used: " + listAsString(inputs) + " generated: " + t.getText() + "_out");
    	//Generate used edges
    	for(int i = 0; i < inputs.size(); i++)
    		this.digraph.addEdge(t.getText(), inputs.get(i));
    	//Generate was generated by edge if there is not a failure
    	if( !failure )
    		this.digraph.addEdge(t.getText() + "_out", t.getText());
    	else {
    		this.failed.put(t.getText() + "_out", true);
    		//System.out.println(t.getText() + "_out: FAILED");
    	}
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
    
    
    public boolean checkFailures(List<String> list) {
    	boolean retVal = false;
    	for(int i = 0; i < list.size(); i++) {
    		String s = list.get(i);
    		if( this.failed.get(s) != null ) {
    			retVal = true;
    			break;
    		}
    	}
    	return retVal;
    }
    
    
    public void addAsmInputs() {
    	for(int i = 0; i < this.asmInProcesses.size(); i++) {
    		int nInputs = this.asmInProcessesNumInputs.get(this.asmInProcesses.get(i));
    		for(int j = 1; j <= nInputs; j++) {
    			this.digraph.addEdge(this.asmInProcesses.get(i), this.asmInProcesses.get(i) + "_in" + j);
    		}
    	}
    }
    
    
    public static double randDouble(Random rand, double min, double max) {
	    double randomNum = min + (max - min) * rand.nextDouble();
	    return randomNum;
	}
    
    
    public static int randInt(Random rand, int min, int max) {
	    int randomNum = rand.nextInt((max - min) + 1) + min;
	    return randomNum;
	}
	
    
    public void createAsmInProcessesNumInputs(int minInputs, int maxInputs) {
    	this.asmInProcessesNumInputs = new Hashtable<String, Integer>();
    	for(int i = 0; i < this.asmInProcesses.size(); i++) {
    		int nInputs = randInt(this.rand, minInputs, maxInputs);
    		this.asmInProcessesNumInputs.put(this.asmInProcesses.get(i), nInputs);
    	}
    }

    
}


