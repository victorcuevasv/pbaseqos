
package org.dataone.daks.pbaserdf.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.dataone.daks.pbaserdf.dao.LDBDAO;

/** Example resource class hosted at the URI path "/exampledaotrace"
 */
@Path("/exampledaotrace")
public class ExampleDAOTraceResource {
    
    /** Method processing HTTP GET requests, producing "text/plain" MIME media
     * type.
     * @return String that will be send back as a response of type "text/plain".
     */
    @GET 
    @Produces("text/plain")
    public String getIt(@QueryParam("dbname") String dbname, @QueryParam("wfid") String wfid, 
    		@QueryParam("traceid") String traceid) {
    	LDBDAO dao = LDBDAO.getInstance();
    	String retVal = null;
    	try {
    		if( dbname == null )
    			System.out.println("ERROR: dbname parameter is null.");
    		if( wfid == null )
    			System.out.println("ERROR: wfid parameter is null.");
    		if( traceid == null )
    			System.out.println("ERROR: traceid parameter is null.");
    		if( dbname != null && wfid != null && traceid != null ) {
    			dao.init(dbname);
    			retVal = dao.getTrace(wfid, traceid);
    		}
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return retVal;
    }
}


