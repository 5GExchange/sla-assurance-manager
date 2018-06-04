package fivegex.sla.console;

import fivegex.sla.ServiceAssuranceManager;
import fivegex.sla.ViolationInfo;
import java.util.concurrent.LinkedBlockingQueue;

import org.simpleframework.http.Response;
import org.simpleframework.http.Request;
import org.simpleframework.http.Path;
import org.simpleframework.http.Query;
import us.monoid.json.*;
import java.io.IOException;


import cc.clayman.console.*;
import cc.clayman.logging.*;
import java.io.PrintStream;



public class slaAssuranceTest extends BasicRequestHandler implements RequestHandler {
    ServiceAssuranceManagerConsole console;


    public slaAssuranceTest() {
    }


    /**
     * Handle a request and send a response.
     */
    public boolean  handle(Request request, Response response) {
        // get RestListener
        console = (ServiceAssuranceManagerConsole)getManagementConsole();

        try {
            long time = System.currentTimeMillis();

            response.set("Content-Type", "application/json");
            response.set("Server", "SLA Assurance Manager/0.1 (SimpleFramework 4.0)");
            response.setDate("Date", time);
            response.setDate("Last-Modified", time);

            // get the path
            Path path =  request.getPath();
            String directory = path.getDirectory();
            String name = path.getName();
            String[] segments = path.getSegments();

            // Get the method
            String method = request.getMethod();

            // Get the Query
            Query query = request.getQuery();


            // and evaluate the input
            if (method.equals("GET")) {
                if (name == null && segments.length == 1)
                    testResult(request, response);

                
            } else if (method.equals("DELETE")) {
                // can't delete data via REST
                notFound(response, "DELETE bad request");
                
            } else if (method.equals("POST")) {
                notFound(response, "POST bad request");
                
            } else if (method.equals("PUT")) {
                notFound(response, "PUT bad request");
                
            } else {
                badRequest(response, "Unknown method" + method);
            }

            // check if the response is closed
            response.close();

            return true;

        } catch (IOException ioe) {
            System.err.println("handle IOException " + ioe.getMessage());
        } catch (JSONException jse) {
            System.err.println("handle JSONException " + jse.getMessage());
        }

        return false;

    }

    /**
     * POST: Process a violation from a request and send a response.
       Where:
       contractUuid: instance ID
       kpiName: KPI key
       expectedValue: Formula to evaluate the KPI
       actualValue: Value received when the last breach happened
       breaches: List of breaches that lead to the violation
       Policy: Policy to apply to evaluate breaches (in this example: 3 breaches in 2:30 minutes)
     */
    
    public void testResult(Request request, Response response) throws IOException, JSONException {
        try {
            
            Logger.getLogger("log").logln(MASK.STDOUT, "ServiceAssuranceManager REST Violation Handler: " +  "testResult");
            JSONObject jsobj = new JSONObject();
            
            boolean success = true;
            jsobj.put("result", success);
        
            if (success) {
                PrintStream out = response.getPrintStream();       
                out.println(jsobj.toString());
            }

            else {
                response.setCode(302);
                PrintStream out = response.getPrintStream();       
                out.println(jsobj.toString());
            }
        
        } catch (IOException ioe) {
            System.err.println("IOException " + ioe.getMessage());
            complain(response, "IOException " + ioe.getMessage());
        }
    }


    /**
     * Get the ServiceAssuranceManager
     */
    public ServiceAssuranceManager getServiceAssuranceManager() {
        return console.getController();
    }

}
