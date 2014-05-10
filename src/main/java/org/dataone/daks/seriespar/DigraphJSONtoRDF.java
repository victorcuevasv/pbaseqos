
package org.dataone.daks.seriespar;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;


public class DigraphJSONtoRDF {
	
	
	String PROVONE_NS = "http://purl.org/provone/ontology#";
	String PROV_NS = "http://www.w3.org/ns/prov#";
	String DCTERMS_NS = "http://purl.org/dc/terms/";
	String EXAMPLE_NS = "http://example.com/";
	String WFMS_NS = "http://www.vistrails.org/registry.xsd";
	String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
	String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	String SOURCE_URL = "http://purl.org/provone/ontology";
	String SOURCE_FILE = "./provone.owl";
	
	private OntModel model;
	private HashMap<String, Individual> idToInd;
	
	
	public DigraphJSONtoRDF() {
		this.model = createOntModel();
		this.idToInd = new HashMap<String, Individual>();
	}
	
	
	public static void main(String args[]) {
		if( args.length != 4 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.DigraphJSONtoRDF <folder> <prefix> <n workflow> <n traces>");      
			System.exit(0);
		}
		DigraphJSONtoRDF converter = new DigraphJSONtoRDF();
		String jsonStr = converter.readFile(args[0] + "/" + args[1] + args[2] + ".json");
		converter.createRDFWFFromJSONString(jsonStr, args[1] + args[2]);
		converter.saveModelAsXMLRDF();
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
	
	
	public void createRDFWFFromJSONString(String jsonStr, String wfID) {
		try {
			Digraph digraph = this.createDigraphFromJSONString(jsonStr);
			Digraph revDigraph = digraph.reverse();
			JSONObject graphObj = new JSONObject(jsonStr);
			JSONArray nodesArray = graphObj.getJSONArray("nodes");
			JSONArray edgesArray = graphObj.getJSONArray("edges");
			//Generate the Workflow entity
			String wfIndId = this.createWorkflowEntity(wfID);
			int ip = 1;
			int op = 1;
			HashMap<String, List<String>> processInPortsHT = new HashMap<String, List<String>>();
			HashMap<String, List<String>> processOutPortsHT = new HashMap<String, List<String>>();
			//Iterate over nodes to generate process entities
			for( int i = 0; i < nodesArray.length(); i++ ) {
				JSONObject nodeObj = nodesArray.getJSONObject(i);
				String nodeId = nodeObj.getString("nodeId");
				String processIndId = this.createProcessEntity(wfID, nodeId, nodeId, i);
				//Generate hasSubProcess object properties
				this.createHasSubProcessObjectProperty(wfIndId, processIndId);
				// Generate InputPort and OutputPort entities
				List<String> nodeRevAdjList = revDigraph.getAdjList(nodeId);
				int nInPorts = nodeRevAdjList.size();
				List<String> inPortsList = new ArrayList<String>();
				for(int j = 1; j <= nInPorts; j++) {
					String inPortIndId = this.createInputPortEntity(wfID, i, ip, nodeId);
					this.createHasInputPortObjectProperty(processIndId, inPortIndId);
					inPortsList.add(inPortIndId);
					ip++;
				}
				processInPortsHT.put(nodeId, inPortsList);
				List<String> nodeAdjList = digraph.getAdjList(nodeId);
				int nOutPorts = nodeAdjList.size();
				List<String> outPortsList = new ArrayList<String>();
				for(int j = 1; j <= nOutPorts; j++) {
					String outPortIndId = this.createOutputPortEntity(wfID, i, op, nodeId);
					this.createHasOutputPortObjectProperty(processIndId, outPortIndId);
					outPortsList.add(outPortIndId);
					op++;
				}
				processOutPortsHT.put(nodeId, outPortsList);
			}
			//Iterate over the edges to generate DataLink entities
			int dl = 1;
			for( int i = 0; i < edgesArray.length(); i++ ) {
				JSONObject edgeObj = edgesArray.getJSONObject(i);
				String source = edgeObj.getString("startNodeId");
				String dest = edgeObj.getString("endNodeId");
				String dataLinkIndId = this.createDataLinkEntity(wfID, dl, source, dest);
				dl++;
				//Generate DLToInPort and outPortToDL object properties
				String outPortIndId = processOutPortsHT.get(source).remove(0);
				this.createOutPortToDLObjectProperty(outPortIndId, dataLinkIndId);
				String inPortIndId = processInPortsHT.get(dest).remove(0);
				this.createDLToInPortObjectProperty(dataLinkIndId, inPortIndId);
			}
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	private String createWorkflowEntity(String wfID) {
		OntClass workflowClass = this.model.getOntClass( SOURCE_URL + "#" + "Workflow" );
		Individual workflowInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/wf", workflowClass );
		Property wfIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		workflowInd.addProperty(wfIdentifierP, wfID, XSDDatatype.XSDstring);
		this.idToInd.put(wfID, workflowInd);
		return wfID;
	}
	
	
	private String createProcessEntity(String wfID, String id, String title, int processIndex) {
		OntClass processClass = this.model.getOntClass( SOURCE_URL + "#" + "Process" );
		Individual processInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/process_" + processIndex, processClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		processInd.addProperty(identifierP, id, XSDDatatype.XSDstring);
		this.idToInd.put(id, processInd);
		Property titleP = this.model.createProperty(DCTERMS_NS + "title");
		processInd.addProperty(titleP, title, XSDDatatype.XSDstring);
		return id;
	}
	
	
	private void createHasSubProcessObjectProperty(String wfIndId, String processIndId) {
		ObjectProperty hasSubProcessOP = this.model.createObjectProperty(SOURCE_URL + "#" + "hasSubProcess");
		this.model.add(this.idToInd.get(wfIndId), hasSubProcessOP, this.idToInd.get(processIndId));
	}
	
	
	private String createInputPortEntity(String wfID, int processIndex, int ipIndex, String processId) {
		OntClass inputPortClass = this.model.getOntClass( SOURCE_URL + "#" + "InputPort" );
		Individual inputPortInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/p" + processIndex + "_ip" + 
				ipIndex, inputPortClass );
		Property ipIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		inputPortInd.addProperty(ipIdentifierP, processId + "_" + "ip" + ipIndex, XSDDatatype.XSDstring);
		this.idToInd.put(processId + "_" + "ip" + ipIndex, inputPortInd);
		return processId + "_" + "ip" + ipIndex;
	}
	
	
	private String createOutputPortEntity(String wfID, int processIndex, int opIndex, String processId) {
		OntClass outputPortClass = this.model.getOntClass( SOURCE_URL + "#" + "OutputPort" );
		Individual outputPortInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/p" + processIndex + "_op" + 
				opIndex, outputPortClass );
		Property opIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		outputPortInd.addProperty(opIdentifierP, processId + "_" + "op" + opIndex, XSDDatatype.XSDstring);
		this.idToInd.put(processId + "_" + "op" + opIndex, outputPortInd);
		return processId + "_" + "op" + opIndex;
	}
	
	
	private void createHasInputPortObjectProperty(String processIndId, String inPortIndId) {
		ObjectProperty hasInputPortOP = this.model.createObjectProperty(SOURCE_URL + "#" + "hasInputPort");
		this.model.add(this.idToInd.get(processIndId), hasInputPortOP, this.idToInd.get(inPortIndId));
	}
	
	
	private void createHasOutputPortObjectProperty(String processIndId, String outPortIndId) {
		ObjectProperty hasOutputPortOP = this.model.createObjectProperty(SOURCE_URL + "#" + "hasOutputPort");
		this.model.add(this.idToInd.get(processIndId), hasOutputPortOP, this.idToInd.get(outPortIndId));
	}
	
	
	private String createDataLinkEntity(String wfID, int dl, String source, String dest) {
		OntClass dataLinkClass = this.model.getOntClass( SOURCE_URL + "#" + "DataLink" );
		Individual dataLinkInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/dl" + dl, dataLinkClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		dataLinkInd.addProperty(identifierP, source + "_" + dest + "DL", XSDDatatype.XSDstring);
		this.idToInd.put(source + "_" + dest + "DL", dataLinkInd);
		return source + "_" + dest + "DL";
	}
	
	
	private void createDLToInPortObjectProperty(String dataLinkIndId, String inPortIndId) {
		ObjectProperty DLToInPortOP = this.model.createObjectProperty(SOURCE_URL + "#" + "DLToInPort");
		this.model.add(this.idToInd.get(dataLinkIndId), DLToInPortOP, this.idToInd.get(inPortIndId));
	}
	
	
	private void createOutPortToDLObjectProperty(String dataLinkIndId, String outPortIndId) {
		ObjectProperty outPortToDLOP = this.model.createObjectProperty(SOURCE_URL + "#" + "outPortToDL");
		this.model.add(this.idToInd.get(dataLinkIndId), outPortToDLOP, this.idToInd.get(outPortIndId));
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


