
package org.dataone.daks.seriespar;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.json.*;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.antlr.runtime.tree.Tree;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.dataone.daks.pbaserdf.dao.LDBDAO;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;

import org.dataone.daks.pbase.treecover.Digraph;


public class DigraphJSONtoRDFQoS {
	
	
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
	private MinMaxRandomServiceCatalog originalCatalog;
	private RandomServiceCatalog globalMetricsCatalog;
	private HashMap<String, Integer> globalMetricsCountHT;
	private HashMap<String, Activity> activitiesHT;
	private HashMap<String, String> wfActivitiesBindingsHT;
	
	
	public DigraphJSONtoRDFQoS() {
		this.model = createOntModel();
		this.idToInd = new HashMap<String, Individual>();
		this.originalCatalog = new MinMaxRandomServiceCatalog();
		this.globalMetricsCatalog = new RandomServiceCatalog();
		this.globalMetricsCountHT = new HashMap<String, Integer>();
		this.activitiesHT = new HashMap<String, Activity>();
		this.wfActivitiesBindingsHT = new HashMap<String, String>();
	}
	
	
	public static void main(String args[]) {
		if( args.length != 4 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.DigraphJSONtoRDFQoS <json files folder> <wf ids file> <n traces file> <services catalog file>");      
			System.exit(0);
		}
		DigraphJSONtoRDFQoS converter = new DigraphJSONtoRDFQoS();
		converter.initializeCatalogsAndCounts(args[3]);
		List<String> wfNamesList = converter.createWFNamesList(args[1]);
		HashMap<String, Integer> numTracesHT = converter.createNumTracesHT(args[2]);
		String folder = args[0];
		for(String wfName: wfNamesList) {
			String wfJSONStr = converter.readFile(folder + "/" + wfName + ".json");
			String wfText = converter.readFile(folder + "/" + wfName + ".txt");
			converter.createRDFWFFromJSONString(wfJSONStr, wfName);
			int nTraces = numTracesHT.get(wfName);
			HashMap<String, QoSMetrics> wfMetricsHT = new HashMap<String, QoSMetrics>();
			HashMap<String, Integer> wfCountHT = new HashMap<String, Integer>();
			for( int i = 1; i <= nTraces; i++ ) {
				String traceJSONStr = converter.readFile(folder + "/" + wfName + "trace" + i + ".json");
				converter.createRDFTraceFromJSONString(traceJSONStr, wfName, i, wfMetricsHT, wfCountHT);
			}
			//addWFMetricsToModel modifies wfMetricsHT
			converter.addWFMetricsToModel(wfMetricsHT, wfCountHT);
			RandomServiceCatalog wfAggCatalog = converter.createWfAggCatalog(wfMetricsHT, wfName, wfText);
			QoSMetrics wfAggMetrics = converter.generateAggMetrics(args[0], wfName, wfAggCatalog);
			converter.addWfAggMetricsToModel(wfName, wfAggMetrics);
		}
		converter.generateGlobalMetrics(args[3]);
		converter.addGlobalMetricsToModel();
		for(String wfName: wfNamesList) {
			QoSMetrics aggGlobalMetrics = converter.generateAggMetrics(args[0], wfName, converter.globalMetricsCatalog);   
			converter.addAggGlobalMetricsToModel(wfName, aggGlobalMetrics);
		}
		converter.saveModelAsXMLRDF();
		converter.createTDBDatabase(DBNAME);
	}
	
	
	//The HashMap ht contains keys of the form wfID_service 
	private RandomServiceCatalog createWfAggCatalog(HashMap<String, QoSMetrics> ht, String wfID, String wfText) {
		RandomServiceCatalog catalog = new RandomServiceCatalog();
		List<String> activities = catalog.generateServiceList(wfText);
		List<String> services = new ArrayList<String>();
		for( String activity : activities ) {
			String service = this.wfActivitiesBindingsHT.get(wfID + "_" + activity);
			services.add(service);
		}
		catalog.initializeSetZero(services);
		for( String service : services ) {
			QoSMetrics metrics = ht.get(wfID + "_" + service);
			if( metrics != null )
				catalog.addQoSMetrics(service, metrics);
		}
		return catalog;
	}
	
	
	private void initializeCatalogsAndCounts(String filename) {
		this.originalCatalog.initializeFromJSONFile(filename);
		List<String> servicesList = this.originalCatalog.getServicesList();
		this.globalMetricsCatalog.initializeSetZero(servicesList);
		for(String service: servicesList)
			this.globalMetricsCountHT.put(service, 0);
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
				if( nodeObj.has("service") ) {
					String service = nodeObj.getString("service");
					this.createServiceObjectProperty(processIndId, service);
					Activity activity = new Activity(wfID, nodeId, service);
					this.activitiesHT.put(processIndId, activity);
					this.wfActivitiesBindingsHT.put(wfID + "_" + nodeId, service);
				}
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
	
	
	public void createRDFTraceFromJSONString(String jsonStr, String wfID, int traceNumber, 
			HashMap<String, QoSMetrics> wfMetricsHT, HashMap<String, Integer> wfCountHT) {
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
					//Generate QoS metrics properties
					double time = nodeObj.getDouble("time");
					double cost = nodeObj.getDouble("cost");
					double reliability = nodeObj.getDouble("reliability");
					this.createQoSObjectProperties(pExecIndId, time, cost, reliability);
					//Generate service object property
					String service = nodeObj.getString("service");
					this.createServiceObjectProperty(pExecIndId, service);
					//Update the global service metrics
					this.updateGlobalMetrics(service, time, cost, reliability);
					//Update the service metrics for the traces of the workflow
					this.updateWFMetrics(wfID, service, time, cost, reliability, wfMetricsHT, wfCountHT);
					//Increment the process exec index
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
	
	
	private void updateGlobalMetrics(String service, double time, double cost, double reliability) {
		double globalTime = this.globalMetricsCatalog.getQoSMetrics(service).getTime();
		this.globalMetricsCatalog.getQoSMetrics(service).setTime(globalTime + time);
		double globalCost = this.globalMetricsCatalog.getQoSMetrics(service).getCost();
		this.globalMetricsCatalog.getQoSMetrics(service).setCost(globalCost + cost);
		double globalReliability = this.globalMetricsCatalog.getQoSMetrics(service).getReliability();
		this.globalMetricsCatalog.getQoSMetrics(service).setReliability(globalReliability + reliability);
		int globalCount = this.globalMetricsCountHT.get(service);
		this.globalMetricsCountHT.put(service, globalCount + 1);
	}
	
	
	private void updateWFMetrics(String wfID, String service, double time, double cost, double reliability,
			HashMap<String, QoSMetrics> wfMetricsHT, HashMap<String, Integer> wfCountHT) {
		QoSMetrics wfMetrics = wfMetricsHT.get(wfID + "_" + service);
		if( wfMetrics == null ) {
			wfMetrics = new QoSMetrics(0.0, 0.0, 0.0);
			wfMetricsHT.put(wfID + "_" + service, wfMetrics);
		}
		double wfTime = wfMetrics.getTime();
		wfMetrics.setTime(wfTime + time);
		double wfCost = wfMetrics.getCost();
		wfMetrics.setCost(wfCost + cost);
		double wfReliability = wfMetrics.getReliability();
		wfMetrics.setReliability(wfReliability + reliability);
		Integer wfCount = wfCountHT.get(wfID + "_" + service);
		if( wfCount == null )
			wfCount = 0;
		wfCountHT.put(wfID + "_" + service, wfCount + 1);
	}
	
	
	private void generateGlobalMetrics(String origCatalogFile) {
		//Iterate over all of the services in the global catalog
		List<String> servicesList = this.originalCatalog.getServicesList();
	    for( String service: servicesList ) {
	    	//Generate the average values with the additions and the counts
	    	QoSMetrics globalServiceMetrics = this.globalMetricsCatalog.getQoSMetrics(service);
	    	double addedTime = globalServiceMetrics.getTime();
	    	double addedCost = globalServiceMetrics.getCost();
	    	double addedReliability = globalServiceMetrics.getReliability();
	    	int count = this.globalMetricsCountHT.get(service);
	    	if( count > 0 ) {
	    		globalServiceMetrics.setTime(addedTime / count);
	    		globalServiceMetrics.setCost(addedCost / count);
	    		globalServiceMetrics.setReliability(addedReliability / count);
	    	}
	    }
	    //Save the global catalog
	    //Get the filename without the extension and add global to it
	  	String globalCatalogFile = origCatalogFile.substring(0, origCatalogFile.length()-5) + "global.json";
		this.globalMetricsCatalog.saveAsJSONFile(globalCatalogFile);
	}
	
	
	private QoSMetrics generateAggMetrics(String folder, String wfID, RandomServiceCatalog catalog) {
		String wfText = this.readFile(folder + "/" + wfID + ".txt");
		QoSMetrics qosMetrics = null;
    	ANTLRStringStream input = new ANTLRStringStream(wfText);
    	ASMSimpleLexer lexer = new ASMSimpleLexer(input);
    	CommonTokenStream tokens = new CommonTokenStream(lexer);
    	ASMSimpleParser parser = new ASMSimpleParser(tokens);
    	ASMSimpleParser.asm_return result = null;
		try {
			result = parser.asm();
		}
		catch (RecognitionException e) {
			e.printStackTrace();
		}
    	Tree t = (Tree)result.getTree();    	
        CommonTreeNodeStream nodes = new CommonTreeNodeStream(t);
        nodes.setTokenStream(tokens);
        ASMSimpleGraphQoS walker = new ASMSimpleGraphQoS(nodes);
        //The generateServiceList method is generic and only depends on the supplied string
        //in this case the list will contain activity names, not the bound services names
        List<String> activityNodes = catalog.generateServiceList(wfText);
        RandomServiceCatalog tempCatalog = new RandomServiceCatalog();
        for( String activity: activityNodes ) {
        	String service = this.wfActivitiesBindingsHT.get(wfID + "_" + activity);
        	QoSMetrics m = catalog.getQoSMetrics(service);
        	tempCatalog.addQoSMetrics(activity, m);
        }
        walker.catalog = tempCatalog;
        try {
			walker.asm();
			WFComponentQoS topComponent = walker.topComponent;
			qosMetrics = topComponent.getQoSMetrics();
		}
        catch (RecognitionException e) {
			e.printStackTrace();
		}
		return qosMetrics;
	}
	
	
	private void addGlobalMetricsToModel() {
		Set<String> keyList = this.activitiesHT.keySet();
		for( String activityKey: keyList ) {
			Individual processInd = this.idToInd.get(activityKey);
			Activity activity = this.activitiesHT.get(activityKey);
			QoSMetrics gMetrics = this.globalMetricsCatalog.getQoSMetrics(activity.service);
			Property gTimeOP = this.model.createProperty(WFMS_NS + "avgtime");
			processInd.addProperty(gTimeOP, gMetrics.getTime() + "", XSDDatatype.XSDdouble);
			Property gCostOP = this.model.createProperty(WFMS_NS + "avgcost");
			processInd.addProperty(gCostOP, gMetrics.getCost() + "", XSDDatatype.XSDdouble);
			Property gReliabilityOP = this.model.createProperty(WFMS_NS + "avgreliability");
			processInd.addProperty(gReliabilityOP, gMetrics.getReliability() + "", XSDDatatype.XSDdouble);
		}
	}
	
	
	private void addAggGlobalMetricsToModel(String wfID, QoSMetrics qosMetrics) {
		Individual wfInd = this.idToInd.get(wfID);
		Property aggGlobalTimeOP = this.model.createProperty(WFMS_NS + "aggavgtime");
		wfInd.addProperty(aggGlobalTimeOP, qosMetrics.getTime() + "", XSDDatatype.XSDdouble);
		Property aggGlobalCostOP = this.model.createProperty(WFMS_NS + "aggavgcost");
		wfInd.addProperty(aggGlobalCostOP, qosMetrics.getCost() + "", XSDDatatype.XSDdouble);
		Property aggGlobalReliabilityOP = this.model.createProperty(WFMS_NS + "aggavgreliability");
		wfInd.addProperty(aggGlobalReliabilityOP, qosMetrics.getReliability() + "", XSDDatatype.XSDdouble);
	}
	
	
	private void addWFMetricsToModel(HashMap<String, QoSMetrics> wfMetricsHT, HashMap<String, Integer> wfCountHT) {
		Set<String> keyList = this.activitiesHT.keySet();
		//Necessary to check for multiple activities bound to the same service
		HashMap<String, Boolean> seenKeys = new HashMap<String, Boolean>();
		for( String activityKey: keyList ) {
			Individual processInd = this.idToInd.get(activityKey);
			Activity activity = this.activitiesHT.get(activityKey);
			QoSMetrics wfMetrics = wfMetricsHT.get(activity.wfID + "_" + activity.service);
			Integer count = wfCountHT.get(activity.wfID + "_" + activity.service);
			if( count == null )
				count = 0;
			double time = 0.0;
			double cost = 0.0;
			double reliability = 0.0;
			if( count > 0 && seenKeys.get(activity.wfID + "_" + activity.service) == null ) {
				time = wfMetrics.getTime() / count;
				cost = wfMetrics.getCost() / count;
				reliability = wfMetrics.getReliability() / count;
				wfMetrics.setTime(time);
				wfMetrics.setCost(cost);
				wfMetrics.setReliability(reliability);
			}
			if( count > 0 ) {
				time = wfMetrics.getTime();
				cost = wfMetrics.getCost();
				reliability = wfMetrics.getReliability();
				Property wfTimeOP = this.model.createProperty(WFMS_NS + "wfavgtime");
				processInd.addProperty(wfTimeOP, time + "", XSDDatatype.XSDdouble);
				wfMetrics.setTime(time);
				Property wfCostOP = this.model.createProperty(WFMS_NS + "wfavgcost");
				processInd.addProperty(wfCostOP, cost + "", XSDDatatype.XSDdouble);
				wfMetrics.setCost(cost);
				Property wfReliabilityOP = this.model.createProperty(WFMS_NS + "wfavgreliability");
				processInd.addProperty(wfReliabilityOP, reliability + "", XSDDatatype.XSDdouble);
				wfMetrics.setReliability(reliability);
			}
			seenKeys.put(activity.wfID + "_" + activity.service, true);
		}
	}
	
	
	private void addWfAggMetricsToModel(String wfID, QoSMetrics qosMetrics) {
		Individual wfInd = this.idToInd.get(wfID);
		Property wfAggTimeOP = this.model.createProperty(WFMS_NS + "wfaggavgtime");
		wfInd.addProperty(wfAggTimeOP, qosMetrics.getTime() + "", XSDDatatype.XSDdouble);
		Property wfAggCostOP = this.model.createProperty(WFMS_NS + "wfaggavgcost");
		wfInd.addProperty(wfAggCostOP, qosMetrics.getCost() + "", XSDDatatype.XSDdouble);
		Property wfAggReliabilityOP = this.model.createProperty(WFMS_NS + "wfaggavgreliability");
		wfInd.addProperty(wfAggReliabilityOP, qosMetrics.getReliability() + "", XSDDatatype.XSDdouble);
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


