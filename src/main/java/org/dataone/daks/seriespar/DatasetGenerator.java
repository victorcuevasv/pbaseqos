
package org.dataone.daks.seriespar;


import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Hashtable;

import org.json.*;
import org.antlr.runtime.*;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.antlr.runtime.tree.Tree;


public class DatasetGenerator {
	
	
	//Maximum number of traces to generate for a given workflow
	private static final int MAXTRACES = 5;
	private Random rand;
	private MinMaxRandomServiceCatalog serviceCatalog;
	
	
	public DatasetGenerator() {
		this.rand = new Random();
	}  
	
	
	public static void main(String args[]) {
		if( args.length != 3 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.DatasetGenerator <wf names file> <folder prefix> <service catalog file>");
			System.exit(0);
		}
		DatasetGenerator generator = new DatasetGenerator();
		generator.generateDataset(args[0], args[1], args[2]);
	}
	
	
	public void generateDataset(String wfNamesFile, String folderPrefix, String catalogFile) {
    	//Read the service catalog
		this.serviceCatalog = new MinMaxRandomServiceCatalog();
		this.serviceCatalog.initializeFromJSONFile(catalogFile);
		//Read the file with the workflow names
		String wfNamesText = readFile(wfNamesFile);
		//System.out.println(wfNamesText);
		//Create a list with the workflow names
		List<String> wfNames = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(wfNamesText);
		while( tokenizer.hasMoreTokens() ) {
			String token = tokenizer.nextToken();
			wfNames.add(token);
		}
		for( String wfName: wfNames )
			this.generateWfAndTraces(wfName, folderPrefix);
	}

	
	public void generateWfAndTraces(String wfName, String folderPrefix) {
		//Generate the ASM
		SeriesParallelGenerator spGenerator = new SeriesParallelGenerator();
		int minStatements = 2;
		int maxStatements = 4;
		double nonTermProb = 0.6;
		double weaken = 0.1;
		String asmText = spGenerator.asm(minStatements, maxStatements, nonTermProb, weaken);
		this.saveTextToFile(asmText, wfName + ".txt");
        try {
        	//Create a CharStream that reads from the input stream provided as a parameter
        	ANTLRStringStream input = new ANTLRStringStream(asmText);
        	//Create a lexer that feeds-off of the input CharStream
        	ASMSimpleLexer lexer = new ASMSimpleLexer(input);
        	//Create a buffer of tokens pulled from the lexer
        	CommonTokenStream tokens = new CommonTokenStream(lexer);
        	//Create a parser that feeds off the tokens buffer
        	ASMSimpleParser parser = new ASMSimpleParser(tokens);
        	//Begin parsing at rule query
        	ASMSimpleParser.asm_return result = null;
    		result = parser.asm();
        	//Pull out the tree and cast it
        	Tree t = (Tree)result.getTree();    	
            //System.out.println(t.toStringTree());
            //Execute the walker over the AST generated by the parser
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
            nodes.setTokenStream(tokens);
            ASMSimpleGraph walker = new ASMSimpleGraph(nodes);
            //Execute the walker beginning at the query rule
            walker.asm();
			ASTtoDigraph astToDigraph = walker.astToDigraph;
			Digraph wfGraph = astToDigraph.getDigraph();
			//System.out.println(graph.toString());
			//Generate the workflow graph files
			wfGraph.toDotFile(folderPrefix + "/" + wfName + ".dot", false);
			JSONObject wfGraphObj = this.generateWFJSON(wfGraph);
			this.saveJSONObjAsFile(wfGraphObj, folderPrefix + "/" + wfName + ".json");
			//Generate the traces
			int nTraces = randInt(1, MAXTRACES);
			for(int i = 1; i <= nTraces; i++) {
				//Run the simulator with the ASM tree
				ASMBindingSimulator simulator = new ASMBindingSimulator(this.serviceCatalog);
				simulator.init(asmText);
				simulator.run();
				simulator.getTraceDigraph().toDotFile(folderPrefix + "/" + wfName + "trace" + i + ".dot", true);
				Hashtable<String, String> bindingHT = simulator.getBindingHT();
				Hashtable<String, QoSMetrics> qosHT = simulator.getQoSHT();
				JSONObject traceGraphObj = this.generateTraceJSON(simulator.getTraceDigraph(), bindingHT, qosHT);
				this.saveJSONObjAsFile(traceGraphObj, folderPrefix + "/" + wfName + "trace" + i + ".json");
			}
		}
        catch (RecognitionException e) {
			e.printStackTrace();
		}
	}
	
	
	private JSONObject generateWFJSON(Digraph graph) {
		List<String> nodes = graph.getVertices();
		JSONArray nodesArray = new JSONArray();
		JSONArray edgesArray = new JSONArray();
		JSONObject graphObj = new JSONObject();
		try {
			for(String node1Id: nodes) {
				JSONObject nodeObj = new JSONObject();
				nodeObj.put("nodeId", node1Id);
				nodesArray.put(nodeObj);
				List<String> adjList = graph.getAdjList(node1Id);
				for( String node2Id: adjList ) {
					JSONObject edgeObj = new JSONObject();
					edgeObj.put("startNodeId", node1Id);
					edgeObj.put("edgeLabel", "");
					edgeObj.put("endNodeId", node2Id);
					edgesArray.put(edgeObj);
				}
			}
			graphObj.put("nodes", nodesArray);
			graphObj.put("edges", edgesArray);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		return graphObj;
	}
	
	
	private JSONObject generateTraceJSON(Digraph graph, Hashtable<String, String> bindingHT,
			Hashtable<String, QoSMetrics> qosHT) {
		List<String> nodes = graph.getVertices();
		JSONArray nodesArray = new JSONArray();
		JSONArray edgesArray = new JSONArray();
		JSONObject graphObj = new JSONObject();
		boolean node1IdisData = false;
		try {
			for(String node1Id: nodes) {
				if( node1Id.contains("_") )
					node1IdisData = true;
				JSONObject nodeObj = new JSONObject();
				nodeObj.put("nodeId", node1Id);
				if( !node1IdisData ) {
					String service = bindingHT.get(node1Id);
					nodeObj.put("service", service);
					QoSMetrics qosMetrics = qosHT.get(node1Id);
					nodeObj.put("time", qosMetrics.getTime());
					nodeObj.put("cost", qosMetrics.getCost());
					nodeObj.put("reliability", qosMetrics.getReliability());
				}
				nodesArray.put(nodeObj);
				List<String> adjList = graph.getAdjList(node1Id);
				for( String node2Id: adjList ) {
					JSONObject edgeObj = new JSONObject();
					edgeObj.put("startNodeId", node1Id);
					if( node1IdisData )
						edgeObj.put("edgeLabel", "wasGenBy");
					else
						edgeObj.put("edgeLabel", "used");
					edgeObj.put("endNodeId", node2Id);
					edgesArray.put(edgeObj);
				}
				node1IdisData = false;
			}
			graphObj.put("nodes", nodesArray);
			graphObj.put("edges", edgesArray);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		return graphObj;
	}
	
	
	public void saveJSONObjAsFile(JSONObject jsonObj, String filename) {
		try {
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
			String jsonStr = jsonObj.toString();
			writer.print(jsonStr);
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private String readFile(String filename) {
		BufferedReader reader = null;
		StringBuilder builder = new StringBuilder();
		try {
			String line = null;
			String NEWLINE = System.getProperty("line.separator");
			reader = new BufferedReader(new FileReader(filename));
			while( (line = reader.readLine()) != null ) {
				builder.append(line + NEWLINE);
			}
			reader.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		return builder.toString();
	}
	
	
	private void saveTextToFile(String text, String filename) {
		try {
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
			writer.print(text);
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
    private int randInt(int min, int max) {
	    int randomNum = this.rand.nextInt((max - min) + 1) + min;
	    return randomNum;
	}

		
}

	


