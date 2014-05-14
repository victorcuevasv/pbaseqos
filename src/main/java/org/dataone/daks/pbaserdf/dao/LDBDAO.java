package org.dataone.daks.pbaserdf.dao;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.tdb.TDBFactory;

import org.json.*;
import org.dataone.daks.pbase.treecover.*;


public class LDBDAO {
	
	
	private Dataset ds;
	
	private String directory;
	
	private Model model;
	
	private static final LDBDAO instance = new LDBDAO();
	
	
	public LDBDAO() {

	}
	
	
	public static LDBDAO getInstance() {
    	return instance;
    }
	
	
	public synchronized void init(String directory) {
		if( this.ds == null || ( this.directory != null && ! this.directory.equals(directory) ) ) {
			if( this.ds != null )
				this.ds.close();
			this.directory = directory;
			Dataset ds = TDBFactory.createDataset(this.directory);
			this.ds = ds;
			this.directory = directory;
			this.model = this.ds.getDefaultModel();
		}
	}
	
	
	public synchronized void shutdown() {
		this.model.close();
		this.ds.close();
		this.ds = null;
		this.model = null;
	}
	
	
	public void addModel(Model m) {
		this.model.add(m);
	}
	
	
	/**
	 * Returns a String representation of a JSON array containing the wfIDs of the workflows in the database,
	 * ranked by a QoS metric.
	 */
	public String getWfIDsRanked(String rankProperty) {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX wfms: <http://www.vistrails.org/registry.xsd#> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" + 
				"SELECT ?id ?" + rankProperty + " WHERE { " + 
        		"?wf dc:identifier ?id . " +
        		"OPTIONAL { ?wf wfms:" + rankProperty + " ?aggavgtime } . " +
        		"?wf rdf:type provone:Workflow . " +
        		"} " + 
        		"ORDER BY ?" + rankProperty;
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        List<String> wfIDsList = new ArrayList<String>();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            String id = soln.getLiteral("id").getString();
            wfIDsList.add(id);
        }
        qexec.close();
        JSONArray array = new JSONArray();
        for (int i = 0; i < wfIDsList.size(); i++) {
        	array.put(wfIDsList.get(i));
        }
		return array.toString();
	}
	
	
	/**
	 * Returns a String representation of a JSON array containing the wfIDs of the workflows in the database.
	 */
	public String getWfIDs() {
		String sparqlQueryString = "SELECT ?v WHERE {  ?s " +
        		"<http://purl.org/dc/terms/identifier> ?v . " +
        		"?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " +
        		"<http://purl.org/provone/ontology#Workflow> .}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        List<String> wfIDsList = new ArrayList<String>();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            String id = soln.getLiteral("v").getString();
            wfIDsList.add(id);
        }
        qexec.close();
        Collections.sort(wfIDsList);
        JSONArray array = new JSONArray();
        for (int i = 0; i < wfIDsList.size(); i++) {
        	array.put(wfIDsList.get(i));
        }
		return array.toString();
	}
	
	
    public JSONArray getProcesses(String wfID) {
    	String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX wfms: <http://www.vistrails.org/registry.xsd#> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"SELECT ?i ?t ?service ?avgtime ?avgcost ?avgreliability ?wfavgtime ?wfavgcost ?wfavgreliability " + 
        		"WHERE {  ?process dc:identifier ?i . " +
        		"?process dc:title ?t . " +
        		"OPTIONAL { ?process wfms:service ?service } . " +
        		"OPTIONAL { ?process wfms:avgtime ?avgtime } . " +
        		"OPTIONAL { ?process wfms:avgcost ?avgcost } . " +
        		"OPTIONAL { ?process wfms:avgreliability ?avgreliability } . " +
        		"OPTIONAL { ?process wfms:wfavgtime ?wfavgtime } . " +
        		"OPTIONAL { ?process wfms:wfavgcost ?wfavgcost } . " +
        		"OPTIONAL { ?process wfms:wfavgreliability ?wfavgreliability } . " +
        		"?process rdf:type provone:Process . " + 
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wf provone:hasSubProcess ?process . }";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        JSONArray nodesArray = new JSONArray();
        try {
        	for ( ; results.hasNext() ; ) {
        		JSONObject nodeObj = new JSONObject();
        		QuerySolution soln = results.nextSolution();
        		String id = soln.getLiteral("i").getString();
				nodeObj.put("nodeId", id);
				String title = soln.getLiteral("t").getString();
	            nodeObj.put("title", title);
	            Literal serviceLit = soln.getLiteral("service");
	            if( serviceLit != null )
	            	nodeObj.put("service", serviceLit.getString());
	            Literal avgtimeLit = soln.getLiteral("avgtime");
	            if( avgtimeLit != null ) {
	            	double avgtime = avgtimeLit.getDouble();
	            	String avgtimeStr = String.format("%.3f", avgtime);
	            	nodeObj.put("avgtime", avgtimeStr);
	            }
	            Literal avgcostLit = soln.getLiteral("avgcost");
	            if( avgcostLit != null ) {
	            	double avgcost = avgcostLit.getDouble();
	            	String avgcostStr = String.format("%.3f", avgcost);
	            	nodeObj.put("avgcost", avgcostStr);
	            }
	            Literal avgreliabilityLit = soln.getLiteral("avgreliability");
	            if( avgreliabilityLit != null ) {
	            	double avgreliability = avgreliabilityLit.getDouble();
	            	String avgreliabilityStr = String.format("%.3f", avgreliability);
	            	nodeObj.put("avgrebty", avgreliabilityStr);
	            }
	            Literal wfavgtimeLit = soln.getLiteral("wfavgtime");
	            if( wfavgtimeLit != null ) {
	            	double wfavgtime = wfavgtimeLit.getDouble();
	            	String wfavgtimeStr = String.format("%.3f", wfavgtime);
	            	nodeObj.put("wfavgtime", wfavgtimeStr);
	            }
	            Literal wfavgcostLit = soln.getLiteral("wfavgcost");
	            if( wfavgcostLit != null ) {
	            	double wfavgcost = wfavgcostLit.getDouble();
	            	String wfavgcostStr = String.format("%.3f", wfavgcost);
	            	nodeObj.put("wfavgcost", wfavgcostStr);
	            }
	            Literal wfavgreliabilityLit = soln.getLiteral("wfavgreliability");
	            if( wfavgreliabilityLit != null ) {
	            	double wfavgreliability = wfavgreliabilityLit.getDouble();
	            	String wfavgreliabilityStr = String.format("%.3f", wfavgreliability);
	            	nodeObj.put("wfavgrebty", wfavgreliabilityStr);
	            }
	            nodesArray.put(nodeObj);
			}
        }
        catch (JSONException e) {
			e.printStackTrace();
		}
        qexec.close();
        return nodesArray;
    }
    
    
    public JSONArray getDataLinks(String wfID) {
    	String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"SELECT ?op_process_id ?ip_process_id WHERE {  ?dl rdf:type provone:DataLink . " +
        		"?dl provone:DLToOutPort ?op . " +
        		"?dl provone:DLToInPort ?ip . " +
        		"?op_process provone:hasOutputPort ?op . " + 
        		"?ip_process provone:hasInputPort ?ip . " + 
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wf provone:hasSubProcess ?op_process . " + 
        		"?wf provone:hasSubProcess ?ip_process . " +
        		"?op_process dc:identifier ?op_process_id . " +
        		"?ip_process dc:identifier ?ip_process_id . } ";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        JSONArray edgesArray = new JSONArray();
        try {
        	for ( ; results.hasNext() ; ) {
        		JSONObject edgeObj = new JSONObject();
        		QuerySolution soln = results.nextSolution();
        		String start = soln.getLiteral("op_process_id").getString();
				edgeObj.put("startNodeId", start);
				String end = soln.getLiteral("ip_process_id").getString();
	            edgeObj.put("endNodeId", end);
	            edgeObj.put("edgeLabel", "");
	            edgesArray.put(edgeObj);
			}
        }
        catch (JSONException e) {
			e.printStackTrace();
		}
        qexec.close();
        return edgesArray;
    }
    
    
    public String getWorkflow(String wfID) {
    	JSONArray processesArray = this.getProcesses(wfID);
    	JSONArray edgesArray = this.getDataLinks(wfID);
    	JSONObject gqosMetricsObj = this.getAggQoSMetrics(wfID);
    	JSONObject wfqosMetricsObj = this.getWfAggQoSMetrics(wfID);
    	JSONObject jsonObj = new JSONObject();
    	try {
			jsonObj.put("nodes", processesArray);
			jsonObj.put("edges", edgesArray);
			jsonObj.put("gqosmetrics", gqosMetricsObj);
			jsonObj.put("wfqosmetrics", wfqosMetricsObj);
		}
    	catch (JSONException e) {
			e.printStackTrace();
		}
    	return jsonObj.toString();
    }
    
    
    public String getWorkflowReachEncoding(String wfID) {
    	JSONArray nodesArray = this.getProcesses(wfID);
    	JSONArray edgesArray = this.getDataLinks(wfID);
    	JSONObject gqosMetricsObj = this.getAggQoSMetrics(wfID);
    	JSONObject wfqosMetricsObj = this.getWfAggQoSMetrics(wfID);
    	JSONObject jsonObj = new JSONObject();
    	try {
    		Digraph coverDigraph = new Digraph();
        	for( int i = 0; i < edgesArray.length(); i++ ) {
        		JSONObject edgeObj = edgesArray.getJSONObject(i);
        		coverDigraph.addEdge(edgeObj.getString("startNodeId"), edgeObj.getString("endNodeId"));	
        	}
        	TreeCover cover = new TreeCover();
    		cover.createCover(coverDigraph);
    		for(int i = 0; i < nodesArray.length(); i++) {
    			JSONObject nodeObj = nodesArray.getJSONObject(i);
    			String nodeIdStr = nodeObj.getString("nodeId");
    			nodeObj.put("intervals", "[" + cover.getCode(nodeIdStr).toString() + "]");
    			nodeObj.put("postorder", cover.getPostorder(nodeIdStr));
    		}
			jsonObj.put("nodes", nodesArray);
			jsonObj.put("edges", edgesArray);
			jsonObj.put("gqosmetrics", gqosMetricsObj);
			jsonObj.put("wfqosmetrics", wfqosMetricsObj);
		}
    	catch (JSONException e) {
			e.printStackTrace();
		}
    	return jsonObj.toString();
    }
	
    
	/**
	 * Returns a String representation of a JSON array containing the runIDs of the workflows in the database.
	 */
	public String getRunIDs(String wfID) {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				"SELECT ?v WHERE {  " + 
        		"?pexec dc:identifier ?v . " +
        		"?pexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        List<String> runIDsList = new ArrayList<String>();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            String id = soln.getLiteral("v").getString();
            runIDsList.add(id);
        }
        qexec.close();
        Collections.sort(runIDsList);
        JSONArray array = new JSONArray();
        for (int i = 0; i < runIDsList.size(); i++) {
        	array.put(runIDsList.get(i));
        }
		return array.toString();
	}
	
	
	public void getProcessExecNodes(String wfID, String runID, Hashtable<String, JSONObject> nodesHT) 
			throws JSONException {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
        		"PREFIX wfms: <http://www.vistrails.org/registry.xsd#> \n" +
				"SELECT ?id ?service ?time ?cost ?reliability WHERE {  " + 
        		"?wfpexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wfpexec dc:identifier " + "\"" + runID + "\"^^xsd:string . " +
        		"?pexec provone:isPartOf ?wfpexec . " +
        		"?pexec dc:identifier ?id . " +
        		"OPTIONAL { ?pexec wfms:service ?service } . " +
        		"OPTIONAL { ?pexec wfms:time ?time } . " +
        		"OPTIONAL { ?pexec wfms:cost ?cost } . " +
        		"OPTIONAL { ?pexec wfms:reliability ?reliability } . " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            JSONObject jsonObj = new JSONObject();
            String id = soln.getLiteral("id").getString();
            jsonObj.put("nodeId", id);
            Literal serviceLit = soln.getLiteral("service");
            if( serviceLit != null ) {
            	String serviceStr = serviceLit.getString();
            	jsonObj.put("service", serviceStr);
            }
            Literal timeLit = soln.getLiteral("time");
            if( timeLit != null ) {
            	double time = timeLit.getDouble();
            	String timeStr = String.format("%.3f", time);
            	jsonObj.put("time", timeStr);
            }
            Literal costLit = soln.getLiteral("cost");
            if( costLit != null ) {
            	double cost = costLit.getDouble();
            	String costStr = String.format("%.3f", cost);
            	jsonObj.put("cost", costStr);
            }
            Literal reliabilityLit = soln.getLiteral("reliability");
            if( reliabilityLit != null ) {
            	double reliability = reliabilityLit.getDouble();
            	String reliabilityStr = String.format("%.3f", reliability);
            	jsonObj.put("rebty", reliabilityStr);
            }
            jsonObj.put("type", "activity");
            nodesHT.put(id, jsonObj);
        }
        qexec.close();
	}
	
	
	public void getUsedDataNodes(String wfID, String runID, Hashtable<String, JSONObject> nodesHT) 
			throws JSONException {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				"SELECT ?id ?t WHERE {  " + 
        		"?wfpexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wfpexec dc:identifier " + "\"" + runID + "\"^^xsd:string . " +
        		"?pexec provone:isPartOf ?wfpexec . " +
        		"?data rdf:type provone:Data . " +
        		"?pexec prov:used ?data . " +
        		"?data dc:identifier ?id . " +
        		"OPTIONAL { ?data dc:title ?t } " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            JSONObject jsonObj = new JSONObject();
            String id = soln.getLiteral("id").getString();
            jsonObj.put("nodeId", id);
            jsonObj.put("type", "data");
            Literal titleLit = soln.getLiteral("t");
            if( titleLit != null )
            	jsonObj.put("title", titleLit.getString());
            nodesHT.put(id, jsonObj);
        }
        qexec.close();
	}
	
	
	public void getWasGenByDataNodes(String wfID, String runID, Hashtable<String, JSONObject> nodesHT)
			throws JSONException {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				"SELECT ?id ?t WHERE {  " + 
        		"?wfpexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wfpexec dc:identifier " + "\"" + runID + "\"^^xsd:string . " +
        		"?pexec provone:isPartOf ?wfpexec . " +
        		"?data rdf:type provone:Data . " +
        		"?data prov:wasGeneratedBy ?pexec . " +
        		"?data dc:identifier ?id . " +
        		"OPTIONAL { ?data dc:title ?t } " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            JSONObject jsonObj = new JSONObject();
            String id = soln.getLiteral("id").getString();
            jsonObj.put("nodeId", id);
            jsonObj.put("type", "data");
            Literal titleLit = soln.getLiteral("t");
            if( titleLit != null )
            	jsonObj.put("title", titleLit.getString());
            nodesHT.put(id, jsonObj);
        }
        qexec.close();
	}
	
	
	public List<JSONObject> getWasGenByEdges(String wfID, String runID) throws JSONException {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				"SELECT ?data_id ?pexec_id WHERE {  " + 
        		"?wfpexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wfpexec dc:identifier " + "\"" + runID + "\"^^xsd:string . " +
        		"?pexec provone:isPartOf ?wfpexec . " +
        		"?data rdf:type provone:Data . " +
        		"?data prov:wasGeneratedBy ?pexec . " +
        		"?data dc:identifier ?data_id . " +
        		"?pexec dc:identifier ?pexec_id . " +
        		"}";
		List<JSONObject> edgesList = new ArrayList<JSONObject>();
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            JSONObject jsonObj = new JSONObject();
            String dataId = soln.getLiteral("data_id").getString();
            String pexecId = soln.getLiteral("pexec_id").getString();
            String label = "wasGenBy";
            jsonObj.put("startNodeId", dataId);
            jsonObj.put("endNodeId", pexecId);
            jsonObj.put("edgeLabel", label);
            edgesList.add(jsonObj);
        }
        qexec.close();
		return edgesList;
	}
	
	
	public List<JSONObject> getUsedEdges(String wfID, String runID) throws JSONException {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				"SELECT ?data_id ?pexec_id WHERE {  " + 
        		"?wfpexec prov:wasAssociatedWith ?wf . " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"?wfpexec dc:identifier " + "\"" + runID + "\"^^xsd:string . " +
        		"?pexec provone:isPartOf ?wfpexec . " +
        		"?data rdf:type provone:Data . " +
        		"?pexec prov:used ?data . " +
        		"?data dc:identifier ?data_id . " +
        		"?pexec dc:identifier ?pexec_id . " +
        		"}";
		List<JSONObject> edgesList = new ArrayList<JSONObject>();
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            JSONObject jsonObj = new JSONObject();
            String dataId = soln.getLiteral("data_id").getString();
            String pexecId = soln.getLiteral("pexec_id").getString();
            String label = "used";
            jsonObj.put("startNodeId", pexecId);
            jsonObj.put("endNodeId", dataId);
            jsonObj.put("edgeLabel", label);
            edgesList.add(jsonObj);
        }
        qexec.close();
		return edgesList;
	}
	
	
	public String getTrace(String wfID, String runID) throws JSONException {
		JSONObject resultObj = new JSONObject();
		//Get the data nodes
		Hashtable<String, JSONObject> nodesHT = new Hashtable<String, JSONObject>();
		this.getUsedDataNodes(wfID, runID, nodesHT);
		this.getWasGenByDataNodes(wfID, runID, nodesHT);
		//Get the activity nodes
		this.getProcessExecNodes(wfID, runID, nodesHT);
		//Get the edges
		List<JSONObject> usedEdges = getUsedEdges(wfID, runID);
		List<JSONObject> wasGenByEdges = getWasGenByEdges(wfID, runID);
		JSONArray edgesArray = new JSONArray();
		Digraph digraph = new Digraph();
		Digraph coverDigraph = new Digraph();
		for(JSONObject usedEdge: usedEdges) {
			edgesArray.put(usedEdge);
			digraph.addEdge(usedEdge.getString("startNodeId"), usedEdge.getString("endNodeId"));
			coverDigraph.addEdge(usedEdge.getString("startNodeId"), usedEdge.getString("endNodeId"));
		}
		for(JSONObject wasGenByEdge: wasGenByEdges) {
			edgesArray.put(wasGenByEdge);
			digraph.addEdge(wasGenByEdge.getString("startNodeId"), wasGenByEdge.getString("endNodeId"));
			coverDigraph.addEdge(wasGenByEdge.getString("startNodeId"), wasGenByEdge.getString("endNodeId"));
		}
		resultObj.put("edges", edgesArray);
		JSONArray nodesArray = new JSONArray();
		TreeCover cover = new TreeCover();
		cover.createCover(coverDigraph);
		List<String> revTopSortList = digraph.reverseTopSort();
		for(String nodeStr : revTopSortList) {
			JSONObject nodeObj = nodesHT.get(nodeStr);
			nodeObj.put("intervals", "[" + cover.getCode(nodeStr).toString() + "]");
			nodeObj.put("postorder", cover.getPostorder(nodeStr));
			nodesArray.put(nodeObj);
		}
		resultObj.put("nodes", nodesArray);
        return resultObj.toString();
	}

	
	/**
	 * Execute a SPARQL query provided as a String.
	 * 
	 * @param query
	 * @return
	 */
	public String executeQuery(String sparqlQueryString) {
		Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        Model model = results.getResourceModel();
        String retVal = null;
        try {
        	List<String> columns = results.getResultVars();
        	JSONObject jsonResult = new JSONObject();
			JSONArray columnsArray = new JSONArray();
			for(String s: columns) {
				JSONObject colVal = new JSONObject();
				colVal.put(s, "string");
				columnsArray.put(colVal);
			}
			JSONArray dataArray = new JSONArray();
			boolean first = true;
			int counter = 0;
        	for ( ; results.hasNext() ; ) {
        		QuerySolution soln = results.nextSolution();
        		JSONArray row = new JSONArray();
				for(String key: columns) {
					RDFNode rdfNode = soln.get(key);
					if ( rdfNode.isResource() ) {
						row.put(this.generateNodeJSON(soln.getResource(key), model));
						if( first )
							columnsArray.getJSONObject(counter).put(columns.get(counter), "node");
					}
					else
						row.put(soln.getLiteral(key).getString());
					counter++;
				}
				dataArray.put(row);
				first = false;
        	}
			jsonResult.put("columns", columnsArray);
			jsonResult.put("data", dataArray);
			retVal = jsonResult.toString(); 
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
		finally {
			qexec.close();
		}
		return retVal;
	}	
	
	
	public JSONObject getAggQoSMetrics(String wfID) {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX wfms: <http://www.vistrails.org/registry.xsd#> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"SELECT ?aggavgtime ?aggavgcost ?aggavgreliability " + 
        		"WHERE { " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"OPTIONAL { ?wf wfms:aggavgtime ?aggavgtime } . " +
        		"OPTIONAL { ?wf wfms:aggavgcost ?aggavgcost } . " +
        		"OPTIONAL { ?wf wfms:aggavgreliability ?aggavgreliability } . " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        JSONObject qosMetricsObj = new JSONObject();
        try {
        	for ( ; results.hasNext() ; ) {
        		QuerySolution soln = results.nextSolution();
	            Literal aggavgtimeLit = soln.getLiteral("aggavgtime");
	            if( aggavgtimeLit != null ) {
	            	double aggavgtime = aggavgtimeLit.getDouble();
	            	String aggavgtimeStr = String.format("%.3f", aggavgtime);
	            	qosMetricsObj.put("aggavgtime", aggavgtimeStr);
	            }
	            Literal aggavgcostLit = soln.getLiteral("aggavgcost");
	            if( aggavgcostLit != null ) {
	            	double aggavgcost = aggavgcostLit.getDouble();
	            	String aggavgcostStr = String.format("%.3f", aggavgcost);
	            	qosMetricsObj.put("aggavgcost", aggavgcostStr);
	            }
	            Literal aggavgreliabilityLit = soln.getLiteral("aggavgreliability");
	            if( aggavgreliabilityLit != null ) {
	            	double aggavgreliability = aggavgreliabilityLit.getDouble();
	            	String aggavgreliabilityStr = String.format("%.3f", aggavgreliability);
	            	qosMetricsObj.put("aggavgrebty", aggavgreliabilityStr);
	            }
	            //Multiple results should not be produced
	            break;
			}
        }
        catch (JSONException e) {
			e.printStackTrace();
		}
        qexec.close();
        return qosMetricsObj;
	}
	
	
	public JSONObject getWfAggQoSMetrics(String wfID) {
		String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX wfms: <http://www.vistrails.org/registry.xsd#> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"SELECT ?wfaggavgtime ?wfaggavgcost ?wfaggavgreliability " + 
        		"WHERE { " +
        		"?wf rdf:type provone:Workflow . " +
        		"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
        		"OPTIONAL { ?wf wfms:wfaggavgtime ?wfaggavgtime } . " +
        		"OPTIONAL { ?wf wfms:wfaggavgcost ?wfaggavgcost } . " +
        		"OPTIONAL { ?wf wfms:wfaggavgreliability ?wfaggavgreliability } . " +
        		"}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, this.ds);
        ResultSet results = qexec.execSelect();
        JSONObject qosMetricsObj = new JSONObject();
        try {
        	for ( ; results.hasNext() ; ) {
        		QuerySolution soln = results.nextSolution();
	            Literal wfaggavgtimeLit = soln.getLiteral("wfaggavgtime");
	            if( wfaggavgtimeLit != null ) {
	            	double wfaggavgtime = wfaggavgtimeLit.getDouble();
	            	String wfaggavgtimeStr = String.format("%.3f", wfaggavgtime);
	            	qosMetricsObj.put("wfaggavgtime", wfaggavgtimeStr);
	            }
	            Literal wfaggavgcostLit = soln.getLiteral("wfaggavgcost");
	            if( wfaggavgcostLit != null ) {
	            	double wfaggavgcost = wfaggavgcostLit.getDouble();
	            	String wfaggavgcostStr = String.format("%.3f", wfaggavgcost);
	            	qosMetricsObj.put("wfaggavgcost", wfaggavgcostStr);
	            }
	            Literal wfaggavgreliabilityLit = soln.getLiteral("wfaggavgreliability");
	            if( wfaggavgreliabilityLit != null ) {
	            	double wfaggavgreliability = wfaggavgreliabilityLit.getDouble();
	            	String wfaggavgreliabilityStr = String.format("%.3f", wfaggavgreliability);
	            	qosMetricsObj.put("wfaggavgrebty", wfaggavgreliabilityStr);
	            }
	            //Multiple results should not be produced
	            break;
			}
        }
        catch (JSONException e) {
			e.printStackTrace();
		}
        qexec.close();
        return qosMetricsObj;
	}
	
	
	public JSONObject generateNodeJSON(Resource resource, Model model) {
		String DCTERMS_NS = "http://purl.org/dc/terms/";
		JSONObject nodeObj = new JSONObject();
		try {
			Property property = model.createProperty(DCTERMS_NS + "identifier");
			if( resource.hasProperty(property) )
				nodeObj.put("nodeId", resource.getProperty(property).getLiteral().getString());
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
		return nodeObj;
	}
	
	
}




