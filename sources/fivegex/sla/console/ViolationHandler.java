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

import java.util.Date;
import java.text.SimpleDateFormat;

import cc.clayman.console.*;
import cc.clayman.logging.*;

/**
 * A class to handle /violation/ requests
[
  {
     "uuid":"4d99d9f8-f421-4490-98a4-b7bc20a14f00",
     "contractUuid":"violtest_ag1",
     "kpiName":"memory",
     "datetime":"Jan 19, 2018 9:54:40 PM",
     "expectedValue":"memory LT 0.2",
     "actualValue":"0.9781141115546773",
     "breaches":[
        {
           "id":776,
           "contractUUID":"violtest_ag1",
           "datetime":"Jan 19, 2018 9:52:40 PM",
           "metricName":"memory",
           "value":"0.2611020262764644"
        },
        {
           "id":777,
           "contractUUID":"violtest_ag1",
           "datetime":"Jan 19, 2018 9:53:20 PM",
           "metricName":"memory",
           "value":"0.270392657720141"
        },
        {
           "contractUUID":"violtest_ag1",
           "datetime":"Jan 19, 2018 9:54:40 PM",
           "metricName":"memory",
           "value":"0.9781141115546773"
        }
     ],
     "policy":{
        "id":3,
        "count":3,
        "timeInterval":"Jan 1, 1970 1:02:30 AM"
     }
  }
]
 */
public class ViolationHandler extends BasicRequestHandler implements RequestHandler {
    ServiceAssuranceManagerConsole console;


    public ViolationHandler() {
    }


    /**
     * Handle a request and send a response.
     */
    public boolean  handle(Request request, Response response) {
        // get RestListener
        console = (ServiceAssuranceManagerConsole)getManagementConsole();

        try {
            /*
            System.out.println("method: " + request.getMethod());
            System.out.println("target: " + request.getTarget());
            System.out.println("path: " + request.getPath());
            System.out.println("directory: " + request.getPath().getDirectory());
            System.out.println("name: " + request.getPath().getName());
            System.out.println("segments: " + java.util.Arrays.asList(request.getPath().getSegments()));
            System.out.println("query: " + request.getQuery());
            System.out.println("keys: " + request.getQuery().keySet());
            */

            //System.out.println("\n/slice/ REQUEST: " + request.getMethod() + " " +  request.getTarget());

            long time = System.currentTimeMillis();

            response.set("Content-Type", "application/json");
            response.set("Server", "Slice Controller/1.0 (SimpleFramework 4.0)");
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
            if (method.equals("POST")) {
                processViolation(request, response);

                
            } else if (method.equals("DELETE")) {
                // can't delete data via REST
                notFound(response, "DELETE bad request");
                
            } else if (method.equals("GET")) {
                notFound(response, "GET bad request");
                
                
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
    public void processViolation(Request request, Response response) throws IOException, JSONException {
        try {
            
            Logger.getLogger("log").logln(MASK.STDOUT, "ServiceAssuranceManager REST Violation Handler: " +  "processViolation");

            Query query = request.getQuery();

            JSONObject recvd;
        

            if (query.size() == 0) {
                String content = request.getContent();

                //System.err.println("content = " + content);

                if (content != null && content.length() > 0) {

                    JSONString value = null;
            
                    try {
                        value = new JSONData(content);
            
                        //Path path =  request.getPath();
                        //System.err.println("\nGot: " + path + " '" + value + "'");


                        recvd = new JSONObject(value.toString());
                    } catch (JSONException je) {
                        // patch it up by adding "violation" key
                        content = "{ \"violation\" : " + content + "}";
                        
                        value = new JSONData(content);

                        // and try again
                        recvd = new JSONObject(value.toString());

                        System.err.println("Add \"violation\": key");
                    }

                    System.err.println("recvd = " + recvd + " ");

                    // now try and find some relevant info
                    ViolationInfo vInfo = violationInfo(recvd);

                    // and put it onto the queue.
                    LinkedBlockingQueue<ViolationInfo> queue = getServiceAssuranceManager().getQueue();

                    try {
                        queue.put(vInfo);

                        System.err.println("ViolationHandler: added ViolationInfo event to queue " + vInfo);
                    } catch (InterruptedException ie) {
                        complain(response, "Cannot pass ViolationInfo into a queue");
                        return;
                    }
                        
                } else {
                    complain(response, "No request value passed in");
                    return;
                }

            } else {
                complain(response, "No passed POSTed in");
                return;
            }


        
        } catch (IOException ioe) {
            System.err.println("IOException " + ioe.getMessage());
            complain(response, "IOException " + ioe.getMessage());
        } catch (JSONException jse) {
            System.err.println("JSONException " + jse.getMessage());
            complain(response, "JSONException " + jse.getMessage());
            jse.printStackTrace();
        }


    }


    /**
     * Get the relevant info from the violation
     */
    public ViolationInfo violationInfo(JSONObject violation) throws JSONException, IOException {
        try {
            /* Find the contractUuid */
            JSONPath contractUuidPath = new JSONPath("violation[0].contractUuid");
            String contractUuid = contractUuidPath.match(violation).toString();
            System.err.println("contractUuid = " + contractUuid);

            /* Find the kpiName */
            JSONPath kpiNamePath = new JSONPath("violation[0].kpiName");
            String kpiName = kpiNamePath.match(violation).toString();
            System.err.println("kpiName = " + kpiName);

            /* Find the violation timeInterval */
            JSONPath timeIntervalPath = new JSONPath("violation[0].policy.timeInterval");
            String timeInterval = timeIntervalPath.match(violation).toString();
            //System.err.println("timeInterval = " + timeInterval);

            // Convert "Jan 1, 1970 1:02:30 AM" to 2 mins 30 secs
            SimpleDateFormat parser = new SimpleDateFormat("MMM d, yyyy HH:mm:ss a");
            Date date = parser.parse(timeInterval);

            int hours = date.getHours();
            int minutes = date.getMinutes();
            int seconds = date.getSeconds();

            System.err.println("Elapsed = " + minutes + ":" + seconds);


            // Now we have the data we construct a ViolationInfo object
            ViolationInfo vInfo = new ViolationInfo(contractUuid, kpiName, date.getTime());

            return vInfo;

        } catch (us.monoid.web.jp.javacc.ParseException pe) {
            pe.printStackTrace();
            return null;
        } catch (java.text.ParseException de) {
            de.printStackTrace();
            return null;
        }
    }


    /**
     * Get the ServiceAssuranceManager
     */
    public ServiceAssuranceManager getServiceAssuranceManager() {
        return console.getController();
    }

}
