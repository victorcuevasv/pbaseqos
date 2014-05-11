
package org.dataone.daks.pbaserdf.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.dataone.daks.pbaserdf.dao.LDBDAO;

/** Example resource class hosted at the URI path "/exampledaorunids"
 */
@Path("/exampledaorunids")
public class ExampleDAORunIdsResource {
    
    /** Method processing HTTP GET requests, producing "text/plain" MIME media
     * type.
     * @return String that will be send back as a response of type "text/plain".
     */
    @GET 
    @Produces("text/plain")
    public String getIt(@QueryParam("dbname") String dbname, @QueryParam("wfid") String wfid) {
    	LDBDAO dao = LDBDAO.getInstance();
    	String retVal = null;
    	try {
    		if( dbname == null )
    			System.out.println("ERROR: dbname parameter is null.");
    		if( wfid == null )
    			System.out.println("ERROR: wfid parameter is null.");
    		if( dbname != null && wfid != null ) {
    			dao.init(dbname);
    			retVal = dao.getRunIDs(wfid);
    		}
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return retVal;
    }
}
