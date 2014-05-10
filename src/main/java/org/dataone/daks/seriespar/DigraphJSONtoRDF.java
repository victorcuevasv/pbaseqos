
package org.dataone.daks.seriespar;


import java.io.*;
import java.util.HashMap;

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
		if( args.length != 3 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.DigraphJSONtoRDF <JSON file name> <graph ID> <workflow|trace>");   
			System.exit(0);
		}
		DigraphJSONtoRDF converter = new DigraphJSONtoRDF();
		String jsonStr = converter.readFile(args[0]);
		if( args[2].equals("workflow") )
			converter.createRDFWFFromJSONString(jsonStr, args[1]);
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
			this.createWorkflowEntity(wfID);
			int ip = 1;
			int op = 1;
			for( int i = 0; i < nodesArray.length(); i++ ) {
				JSONObject nodeObj = nodesArray.getJSONObject(i);
				String nodeId = nodeObj.getString("nodeId");
				this.createProcessEntity(wfID, nodeId, nodeId, i);
				// Generate InputPort and OutputPort entities
				//IMPORTANT the number of inputs of the first workflow tasks is determined randomly
				createInputPortEntity(wfID, i, ip, nodeId);
				
			}		
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	private void createWorkflowEntity(String wfID) {
		OntClass workflowClass = this.model.getOntClass( SOURCE_URL + "#" + "Workflow" );
		Individual workflowInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/wf", workflowClass );
		Property wfIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		workflowInd.addProperty(wfIdentifierP, wfID, XSDDatatype.XSDstring);
		this.idToInd.put(wfID, workflowInd);
	}
	
	
	private void createProcessEntity(String wfID, String id, String title, int processIndex) {
		OntClass processClass = this.model.getOntClass( SOURCE_URL + "#" + "Process" );
		Individual processInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/process_" + processIndex, processClass );
		Property identifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		processInd.addProperty(identifierP, id, XSDDatatype.XSDstring);
		this.idToInd.put(id, processInd);
		Property titleP = this.model.createProperty(DCTERMS_NS + "title");
		processInd.addProperty(titleP, title, XSDDatatype.XSDstring);
	}
	
	
	private void createInputPortEntity(String wfID, int processIndex, int ipIndex, String processId) {
		OntClass inputPortClass = this.model.getOntClass( SOURCE_URL + "#" + "InputPort" );
		OntClass outputPortClass = this.model.getOntClass( SOURCE_URL + "#" + "OutputPort" );
		Individual inputPortInd = this.model.createIndividual( EXAMPLE_NS + wfID + "/p" + processIndex + "_ip" + 
				ipIndex, inputPortClass );
		Property ipIdentifierP = this.model.createProperty(DCTERMS_NS + "identifier");
		inputPortInd.addProperty(ipIdentifierP, processId + "_" + "_ip" + ipIndex, XSDDatatype.XSDstring);
		this.idToInd.put(processId + "_" + "_ip" + ipIndex, inputPortInd);
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


