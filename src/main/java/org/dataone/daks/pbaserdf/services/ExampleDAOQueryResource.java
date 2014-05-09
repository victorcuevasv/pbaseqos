
package org.dataone.daks.pbaserdf.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.dataone.daks.pbaserdf.dao.LDBDAO;

/** Example resource class hosted at the URI path "/exampledaoquery"
 */
@Path("/exampledaoquery")
public class ExampleDAOQueryResource {
    
    /** Method processing HTTP GET requests, producing "text/plain" MIME media
     * type.
     * @return String that will be send back as a response of type "text/plain".
     */
    @GET 
    @Produces("text/plain")
    public String getIt() {
    	LDBDAO dao = LDBDAO.getInstance();
    	dao.init("provone");
    	String wfID = "spatialtemporal_summary";
    	String processID = "e3";
    	String sparqlQueryString = "PREFIX provone: <http://purl.org/provone/ontology#> \n" +
        		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		"PREFIX dc: <http://purl.org/dc/terms/> \n" +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" +
        		"PREFIX prov: <http://www.w3.org/ns/prov#> \n" +
				//"SELECT DISTINCT ?derdata_id WHERE {  " + 
				"SELECT DISTINCT ?derdata WHERE {  " + 
				"?wf rdf:type provone:Workflow . " +
				"?wf dc:identifier " + "\"" + wfID + "\"^^xsd:string . " +
				"?wf provone:hasSubProcess ?p . " +
				"?p dc:identifier " + "\"" + processID + "\"^^xsd:string . " +
				"?pexec prov:wasAssociatedWith ?p . " +
				"?data prov:wasGeneratedBy ?pexec . " +
				"?data dc:identifier ?data_id . " +
				"?derdata (prov:used | prov:wasGeneratedBy)* ?data . " +
				"?derdata rdf:type provone:Data . " +
				"?derdata dc:identifier ?derdata_id . " +
        		"} ";
    	String retVal = null;
    	try {
    		retVal = dao.executeQuery(sparqlQueryString);
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return retVal;
    }
    
}


