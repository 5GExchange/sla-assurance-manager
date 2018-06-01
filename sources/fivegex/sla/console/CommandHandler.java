package fivegex.sla.console;


import org.simpleframework.http.Response;
import org.simpleframework.http.Request;
import org.simpleframework.http.Path;
import org.simpleframework.http.Query;
import us.monoid.json.*;
import java.io.IOException;

import cc.clayman.console.*;

/**
 * A class to handle /command/ requests
 */
public class CommandHandler extends BasicRequestHandler implements RequestHandler {
    ServiceAssuranceManagerConsole console;


    public CommandHandler() {
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
                notFound(response, "POST bad request");
                
            } else if (method.equals("DELETE")) {
                // can't delete data via REST
                notFound(response, "DELETE bad request");
                
            } else if (method.equals("GET")) {
                if (name == null) {      // no arg, so get energy data
                    notFound(response, "GET bad request");

                } else if (segments.length == 2) {   // get specific energy info
                    processCommand(request, response);
                    
                } else {
                    notFound(response, "GET bad request");
                }
                
            } else if (method.equals("PUT")) {
                notFound(response, "PUT bad request");
                
            } else {
                badRequest(response, "Unknown method" + method);
            }



            // check if the response is closed
            response.close();

            return true;

        } catch (IOException ioe) {
            System.err.println("IOException " + ioe.getMessage());
        } catch (JSONException jse) {
            System.err.println("JSONException " + jse.getMessage());
        }

        return false;

    }

    /**
     * GET: Process a command from a request and send a response.
     */
    public void processCommand(Request request, Response response) throws IOException, JSONException  {

        // get the path
        Path path =  request.getPath();
        String name = path.getName();

        if (name.equals("SHUT_DOWN")) {
            console.getController().shutDown();
        } else {
                badRequest(response, "Unknown command: " + name);
            
        }

        

    }

}
