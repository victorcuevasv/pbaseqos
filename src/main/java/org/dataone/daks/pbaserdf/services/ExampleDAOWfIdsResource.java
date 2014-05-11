
package org.dataone.daks.pbaserdf.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.dataone.daks.pbaserdf.dao.LDBDAO;

/** Example resource class hosted at the URI path "/exampledaowfids"
 */
@Path("/exampledaowfids")
public class ExampleDAOWfIdsResource {
    
    /** Method processing HTTP GET requests, producing "text/plain" MIME media
     * type.
     * @return String that will be send back as a response of type "text/plain".
     */
    @GET 
    @Produces("text/plain")
    public String getIt(@QueryParam("dbname") String dbname) {
    	LDBDAO dao = LDBDAO.getInstance();
    	String retVal = null;
    	try {
    		if( dbname == null )
    			System.out.println("ERROR: dbname parameter is null.");
    		else {
    			dao.init(dbname);
    			retVal = dao.getWfIDs();
    		}
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return retVal;
    }
}
