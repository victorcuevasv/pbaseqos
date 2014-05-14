
package org.dataone.daks.seriespar;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.json.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.dataone.daks.pbaserdf.dao.LDBDAO;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;

import org.dataone.daks.pbase.treecover.Digraph;


public class DigraphJSONtoRDF {
	
	
	private static final String PROVONE_NS = "http://purl.org/provone/ontology#";
	private static final String PROV_NS = "http://www.w3.org/ns/prov#";
	private static final String DCTERMS_NS = "http://purl.org/dc/terms/";
	private static final String EXAMPLE_NS = "http://example.com/";
	private static final String WFMS_NS = "http://www.vistrails.org/registry.xsd#";
	private static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
	private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	private static final String SOURCE_URL = "http://purl.org/provone/ontology";
	private static final String SOURCE_FILE = "./provone.owl";
	
	private static final String DBNAME = "seqpargraphs";
	
	private OntModel model;
	private HashMap<String, Individual> idToInd;
	
	
	public DigraphJSONtoRDF() {
		this.model = createOntModel();
		this.idToInd = new HashMap<String, Individual>();
	}
	
	
	public static void main(String args[]) {
		if( args.length != 3 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.DigraphJSONtoRDF <json files folder> <wf ids file> <n traces file>");      
			System.exit(0);
		}
		DigraphJSONtoRDF converter = new DigraphJSONtoRDF();
		List<String> wfNamesList = converter.createWFNamesList(args[1]);
		HashMap<String, Integer> numTracesHT = converter.createNumTracesHT(args[2]);
		String folder = args[0];
		for(String wfName: wfNamesList) {
			String wfJSONStr = converter.readFile(folder + "/" + wfName + ".json");
			converter.createRDFWFFromJSONString(wfJSONStr, wfName);
			int nTraces = numTracesHT.get(wfName);
			for( int i = 1; i <= nTraces; i++ ) {
				String traceJSONStr = converter.readFile(folder + "/" + wfName + "trace" + i + ".json");
				converter.createRDFTraceFromJSONString(traceJSONStr, wfName, i);
			}
		}
		converter.saveModelAsXMLRDF();
		converter.createTDBDatabase(DBNAME);
	}

	
	private List<String> createWFNamesList(String wfNamesFile) {
		String wfNamesText = readFile(wfNamesFile);
		List<String> wfNamesList = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(wfNamesText);
		while( tokenizer.hasMoreTokens() ) {
			String token = tokenizer.nextToken();
			wfNamesList.add(token);
		}
		return wfNamesList;
	}
	
	
	private HashMap<String, Integer> createNumTracesHT(String numTracesFile) {
		String numTracesText = readFile(numTracesFile);
		HashMap<String, Integer> numTracesHT = new HashMap<String, Integer>();
		StringTokenizer tokenizer = new StringTokenizer(numTracesText);
		while( tokenizer.hasMoreTokens() ) {
			String wfId = tokenizer.nextToken();
			String nTracesStr = tokenizer.nextToken();
			int nTraces = Integer.parseInt(nTracesStr);
			numTracesHT.put(wfId, nTraces);
		}
		return numTracesHT;
	}
	
	
	private OntModel createOntModel() {
		OntModel m = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM );
		//[Un]comment to use file or URL
		FileManager.get().getLocationMapper().addAltEntry( SOURCE_URL, SOURCE_FILE );
		Model baseOntology = FileManager.get().loadModel( SOURCE_URL );
		m.addSubModel( baseOntology );
		m.setNsPrefix( "provone", SOURCE_URL + "#" );
		return m;
	}
	
	
	private String saveModelAsXMLRDF() {
		String tempXMLRDFFile = "tempdataxml.xml";
		String tempTTLRDFFile = "tempdatattl.ttl";
		try {
			FileOutputStream fos = new FileOutputStream(new File(tempXMLRDFFile));
			this.model.write(fos, "RDF/XML");
			FileOutputStream out = new FileOutputStream(new File(tempTTLRDFFile));
			Model model = RDFDataMgr.loadModel(tempXMLRDFFile);
	        RDFDataMgr.write(out, model, RDFFormat.TURTLE_PRETTY);
		}
		catch (IOException e) {
			e.printStackTrace();
	    }
		return tempXMLRDFFile;
	}
	
	
	private void createTDBDatabase(String dbname) {
		LDBDAO dao = LDBDAO.getInstance();
		dao.init(dbname);
		dao.addModel(this.model);
		dao.shutdown();
		System.out.println("Database created/updated with the name : " + dbname);
	}
	
	
	public void createRDFWFFromJSONString(String jsonStr, String wfID) {
		try {
			Digraph digraph = this.createDigraphFromJSONString(jsonStr);
			Digraph revDigraph = digraph.reverse();
			JSONObject graphObj = new JSONObject(jsonStr);
			JSONArray nodesArray = graphObj.getJSONArray("nodes");
			JSONArray edgesArray = graphObj.getJSONArray("edges");
			//Generate the Workflow entity
			String wfIndId = this.createWorkflowEntity(wfID);
			int ip = 0;
			int op = 0;
			HashMap<String, List<String>> processInPortsHT = new HashMap<String, List<String>>();
			HashMap<String, List<String>> processOutPortsHT = new HashMap<String, List<String>>();
			//Iterate over nodes to generate process entities
			for( int i = 0; i < nodesArray.length(); i++ ) {
				JSONObject nodeObj = nodesArray.getJSONObject(i);
				String nodeId = nodeObj.getString("nodeId");
				String processIndId = this.createProcessEntity(wfID, nodeId, nodeId, i);
				//Generate hasSubProcess object properties
				this.createHasSubProcessObjectProperty(wfIndId, processIndId);
				//Generate service object property for processes bound to services
				if( nodeObj.has("service") )
					this.createServiceObjectProperty(processIndId, nodeObj.getString("service"));
				// Generate InputPort and OutputPort entities
				List<String> nodeRevAdjList = revDigraph.getAdjList(nodeId);
				int nInPorts = nodeRevAdjList.size();
				List<String> inPortsList = new ArrayList<String>();
				for(int j = 0; j < nInPorts; j++) {
					String inPortIndId = this.createInputPortEntity(wfID, i, ip, nodeId);
					this.createHasInputPortObjectProperty(processIndId, inPortIndId);
					inPortsList.add(inPortIndId);
					ip++;
				}
				processInPortsHT.put(nodeId, inPortsList);
				List<String> nodeAdjList = digraph.getAdjList(nodeId);
				int nOutPorts = nodeAdjList.size();
				List<String> outPortsList = new ArrayList<String>();
				for(int j = 0; j < nOutPorts; j++) {
					String outPortIndId = this.createOutputPortEntity(wfID, i, op, nodeId);
					this.createHasOutputPortObjectProperty(processIndId, outPortIndId);
					outPortsList.add(outPortIndId);
					op++;
				}
				processOutPortsHT.put(nodeId, outPortsList);
			}
			//Iterate over the edges to generate DataLink entities
			for( int i = 0; i < edgesArray.length(); i++ ) {
				JSONObject edgeObj = edgesArray.getJSONObject(i);
				String source = edgeObj.getString("startNodeId");
				String dest = edgeObj.getString("endNodeId");
				String dataLinkIndId = this.createDataLinkEntity(wfID, i, source, dest);
				//Generate DLToInPort and outPortToDL object properties
				String outPortIndId = processOutPortsHT.get(source).remove(0);
				this.createDLToOutPortObjectProperty(dataLinkIndId, outPortIndId);
				String inPortIndId = processInPortsHT.get(dest).remove(0);
				this.createDLToInPortObjectProperty(dataLinkIndId, inPortIndId);
			}
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	public void createRDFTraceFromJSONString(String jsonStr, String wfID, int traceNumber) {
		try {
			JSONObject graphObj = new JSONObject(jsonStr);
			JSONArray nodesArray = graphObj.getJSONArray("nodes");
			JSONArray edgesArray = graphObj.getJSONArray("edges");
			int pExecIndex = 1;
			int dataIndex = 1;
			//Generate process execution entity for the workflow
			String wfProcessExecIndId = this.createWFProcessExecEntity(wfID, traceNumber);
			//Associate the workflow process execution with the workflow itself
			this.createWasAssociatedWithObjectProperty(wfProcessExecIndId, wfID);
			//Iterate over nodes to generate process execution entities
			for( int i = 0; i < nodesArray.length(); i++ ) {
				JSONObject nodeObj = nodesArray.getJSONObject(i);
				String nodeId = nodeObj.getString("nodeId");
				String pExecIndId = null;
				String dataIndId = null;
				//Check if the node is an activity or a data item
				if( !nodeId.contains("_") ) { //activity
					pExecIndId = this.createProcessExecEntity(wfID, nodeId, traceNumber, pExecIndex);
					this.createWasAssociatedWithObjectProperty(pExecIndId, wfID + nodeId);
					this.createIsPartOfObjectProperty(pExecIndId, wfProcessExecIndId);
					this.createQoSObjectProperties(pExecIndId, nodeObj.getDouble("time"), nodeObj.getDouble("cost"), nodeObj.getDouble("reliability"));
					//Generate service object property
					this.createServiceObjectProperty(pExecIndId, nodeObj.getString("service"));
					pExecIndex++;
				}
				else { //data
					dataIndId = this.createDataEntity(wfID, nodeId, traceNumber, dataIndex);
					dataIndex++;
				}
			}
			//Generate wasGeneratedBy and used object properties between Data and ProcessExec entities
			for( int i = 0; i < edgesArray.length(); i++ ) {
				JSONObject edgeObj = edgesArray.getJSONObject(i);
				String startNodeId = edgeObj.getString("startNodeId");
				String endNodeId = edgeObj.getString("endNodeId");
				String edgeLabel = edgeObj.getString("edgeLabel");
				String pExecIndId = null;
				String dataIndId = null;
				if( edgeLabel.equals("wasGenBy") ) {
					dataIndId = wfID + "trace" + traceNumber + startNodeId;
					pExecIndId = wfID + "trace" + traceNumber + endNodeId;
					this.createWasGeneratedByObjectProperty(dataIndId, pExecIndId);
				}
				else if( edgeLabel.equals("used") ) {
					dataIndId = wfID + "trace" + traceNumber + endNodeId;
					pExecIndId = wfID + "trace" + traceNumber + startNodeId;
					this.createUsedObjectProperty(pExecIndId, dataIndId);
				}
			}
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	private String createWorkflowEntity(String wfID) {
		OntClass workflowClass = this.model.getOntClass( PROVONE_NS + "Workflow" );
		Individual workflowInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/wf", workflowClass );
		Property wfIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		workflowInd.addProperty(wfIdentifierP, wfID, XSDDatatype.XSDstring);
		this.idToInd.put(wfID, workflowInd);
		return wfID;
	}
	
	
	private String createProcessEntity(String wfID, String id, String title, int processIndex) {
		OntClass processClass = this.model.getOntClass( PROVONE_NS + "Process" );
		Individual processInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/process_" + processIndex, processClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		processInd.addProperty(identifierP, id, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + id, processInd);
		Property titleP = this.model.createProperty(DCTERMS_NS + "title");
		processInd.addProperty(titleP, title, XSDDatatype.XSDstring);
		return wfID + id;
	}
	
	
	private String createProcessExecEntity(String wfID, String nodeId, int traceNumber, int processExecIndex) {
		OntClass processExecClass = this.model.getOntClass( PROVONE_NS + "ProcessExec" );
		Individual processExecInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/trace" + traceNumber + "processExec_" + processExecIndex, processExecClass );    
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		processExecInd.addProperty(identifierP, nodeId, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + "trace" + traceNumber + nodeId, processExecInd);
		return wfID + "trace" + traceNumber + nodeId;
	}
	
	
	private String createWFProcessExecEntity(String wfID, int traceNumber) {
		OntClass processExecClass = this.model.getOntClass( PROVONE_NS + "ProcessExec" );
		Individual processExecInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/wfprocessExec_" + traceNumber, processExecClass );    
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		processExecInd.addProperty(identifierP, wfID + "trace" + traceNumber, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + "trace" + traceNumber, processExecInd);
		return wfID + "trace" + traceNumber;
	}
	
	
	private String createDataEntity(String wfID, String nodeId, int traceNumber, int dataIndex) {
		OntClass dataClass = this.model.getOntClass( PROVONE_NS + "Data" );
		Individual dataInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/trace" + traceNumber + "data_" + dataIndex, dataClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		dataInd.addProperty(identifierP, nodeId, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + "trace" + traceNumber + nodeId, dataInd);
		return wfID + "trace" + traceNumber + nodeId;
	}
	
	
	private void createHasSubProcessObjectProperty(String wfIndId, String processIndId) {
		ObjectProperty hasSubProcessOP = this.model.createObjectProperty(PROVONE_NS + "hasSubProcess");
		this.model.add(this.idToInd.get(wfIndId), hasSubProcessOP, this.idToInd.get(processIndId));
	}
	
	
	private String createInputPortEntity(String wfID, int processIndex, int ipIndex, String processId) {
		OntClass inputPortClass = this.model.getOntClass( PROVONE_NS + "InputPort" );
		Individual inputPortInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/p" + processIndex + "_ip" + 
				ipIndex, inputPortClass );
		Property ipIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		inputPortInd.addProperty(ipIdentifierP, processId + "_" + "ip" + ipIndex, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + processId + "_" + "ip" + ipIndex, inputPortInd);
		return wfID + processId + "_" + "ip" + ipIndex;
	}
	
	
	private String createOutputPortEntity(String wfID, int processIndex, int opIndex, String processId) {
		OntClass outputPortClass = this.model.getOntClass( PROVONE_NS + "OutputPort" );
		Individual outputPortInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/p" + processIndex + "_op" + 
				opIndex, outputPortClass );
		Property opIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		outputPortInd.addProperty(opIdentifierP, processId + "_" + "op" + opIndex, XSDDatatype.XSDstring);
		this.idToInd.put(wfID + processId + "_" + "op" + opIndex, outputPortInd);
		return wfID + processId + "_" + "op" + opIndex;
	}
	
	
	private void createHasInputPortObjectProperty(String processIndId, String inPortIndId) {
		ObjectProperty hasInputPortOP = this.model.createObjectProperty(PROVONE_NS + "hasInputPort");
		this.model.add(this.idToInd.get(processIndId), hasInputPortOP, this.idToInd.get(inPortIndId));
	}
	
	
	private void createHasOutputPortObjectProperty(String processIndId, String outPortIndId) {
		ObjectProperty hasOutputPortOP = this.model.createObjectProperty(PROVONE_NS + "hasOutputPort");
		this.model.add(this.idToInd.get(processIndId), hasOutputPortOP, this.idToInd.get(outPortIndId));
	}
	
	
	private String createDataLinkEntity(String wfID, int dlIndex, String source, String dest) {
		OntClass dataLinkClass = this.model.getOntClass( PROVONE_NS + "DataLink" );
		Individual dataLinkInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/dl" + dlIndex, dataLinkClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		dataLinkInd.addProperty(identifierP, source + "_" + dest + "DL", XSDDatatype.XSDstring);
		this.idToInd.put(wfID + "_" + source + "_" + dest + "DL", dataLinkInd);
		return wfID + "_" + source + "_" + dest + "DL";
	}
	
	
	private void createDLToInPortObjectProperty(String dataLinkIndId, String inPortIndId) {
		ObjectProperty DLToInPortOP = this.model.createObjectProperty(PROVONE_NS + "DLToInPort");
		this.model.add(this.idToInd.get(dataLinkIndId), DLToInPortOP, this.idToInd.get(inPortIndId));
	}
	
	
	private void createDLToOutPortObjectProperty(String dataLinkIndId, String outPortIndId) {
		ObjectProperty DLToOutPortOP = this.model.createObjectProperty(PROVONE_NS + "DLToOutPort");
		this.model.add(this.idToInd.get(dataLinkIndId), DLToOutPortOP, this.idToInd.get(outPortIndId));
	}
	
	
	private void createWasAssociatedWithObjectProperty(String processExecIndId, String processIndId) {
		ObjectProperty wasAssociatedWithOP = this.model.createObjectProperty(PROV_NS + "wasAssociatedWith");
		this.model.add(this.idToInd.get(processExecIndId), wasAssociatedWithOP, this.idToInd.get(processIndId));
	}
	
	
	private void createIsPartOfObjectProperty(String processExecIndId, String wfProcessExecIndId) {
		Property isPartOfOP = this.model.createProperty(PROVONE_NS + "isPartOf");
		this.model.add(this.idToInd.get(processExecIndId), isPartOfOP, this.idToInd.get(wfProcessExecIndId));
	}
	
	
	private void createWasGeneratedByObjectProperty(String dataIndId, String processExecIndId) {
		Property wasGeneratedByOP = this.model.createProperty(PROV_NS + "wasGeneratedBy");
		this.model.add(this.idToInd.get(dataIndId), wasGeneratedByOP, this.idToInd.get(processExecIndId));
	}
	
	
	private void createUsedObjectProperty(String processExecIndId, String dataIndId) {
		Property usedOP = this.model.createProperty(PROV_NS + "used");
		this.model.add(this.idToInd.get(processExecIndId), usedOP, this.idToInd.get(dataIndId));
	}
	
	
	private void createQoSObjectProperties(String pExecIndId, double time, double cost, double reliability) {
		Individual pExecInd = this.idToInd.get(pExecIndId);
		Property timeOP = this.model.createProperty(WFMS_NS + "time");
		pExecInd.addProperty(timeOP, time + "", XSDDatatype.XSDdouble);
		Property costOP = this.model.createProperty(WFMS_NS + "cost");
		pExecInd.addProperty(costOP, cost + "", XSDDatatype.XSDdouble);
		Property reliabilityOP = this.model.createProperty(WFMS_NS + "reliability");
		pExecInd.addProperty(reliabilityOP, reliability + "", XSDDatatype.XSDdouble);
	}
	
	
	private void createServiceObjectProperty(String indId, String service) {
		Individual ind = this.idToInd.get(indId);
		Property serviceOP = this.model.createProperty(WFMS_NS + "service");
		ind.addProperty(serviceOP, service, XSDDatatype.XSDstring);
	}
	
	
	public Digraph createDigraphFromJSONString(String jsonStr) {
		Digraph digraph = new Digraph();
		try {
			JSONObject graphObj = new JSONObject(jsonStr);
			JSONArray edgesArray = graphObj.getJSONArray("edges");
			for( int i = 0; i < edgesArray.length(); i++ ) {
				JSONObject edgeObj = edgesArray.getJSONObject(i);
				String startNodeId = edgeObj.getString("startNodeId");
				String endNodeId = edgeObj.getString("endNodeId");
				digraph.addEdge(startNodeId, endNodeId);
			}		
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
		return digraph;
	}
	
	
	private String readFile(String filename) {
		StringBuilder builder = null;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line = null;
			builder = new StringBuilder();
			String NEWLINE = System.getProperty("line.separator");
			while( (line = reader.readLine()) != null ) {
				builder.append(line + NEWLINE);
			}
			reader.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return builder.toString();
	}
	
	
}


