
package org.dataone.daks.pbaserdf.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.dataone.daks.pbaserdf.dao.LDBDAO;

/** Example resource class hosted at the URI path "/wfidsresource"
 */
@Path("/wfidsresource")
public class WfIdsResource {
    
    /** Method processing HTTP GET requests, producing "text/plain" MIME media
     * type.
     * @return String that will be send back as a response of type "text/plain".
     */
    @GET 
    @Produces("text/plain")
    public String getIt(@QueryParam("dbname") String dbname, 
    		@QueryParam("rankproperty") String rankProperty) {
    	LDBDAO dao = LDBDAO.getInstance();
    	dao.init(dbname);
    	String retVal = null;
    	try {
    		if( rankProperty == null || rankProperty.equals("none") )
    			retVal = dao.getWfIDs();
    		else
    			retVal = dao.getWfIDsRanked(rankProperty);
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return retVal;
    }
}

