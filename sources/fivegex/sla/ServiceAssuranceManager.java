package fivegex.sla;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

import us.monoid.json.JSONObject;
import us.monoid.json.JSONException;

import java.util.concurrent.LinkedBlockingQueue;

import fivegex.sla.console.*;

import cc.clayman.logging.*;

public class ServiceAssuranceManager {
    // The config file name
    String configFileName;

    // The console
    ServiceAssuranceManagerConsole console;

    // The port for the ManagementConsole
    int consolePort = 7080;


    JSONObject config;

    // The ViolationProcessor
    ViolationProcessor violationProcessor;

    // Thread for ViolationProcessor
    Thread vpThread;


    // A ResourceOrchestratorInteractor
    ResourceOrchestratorInteractor resourceOrchestratorInteractor;

    // A queue of ViolationInfo objects
    // The queue to take from
    LinkedBlockingQueue<ViolationInfo> queue;
    


    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        ServiceAssuranceManager sam = new ServiceAssuranceManager();

        boolean initOK = false;

        initOK = sam.init(args);

        // logging setup by now

        if (initOK) {
            boolean startOK = sam.start();

            if (!startOK) {
                System.exit(1);
            }
        }

    }

    /**
     * Construct a ServiceAssuranceManager
     */
    public ServiceAssuranceManager() {
        config = new JSONObject();
    }

    /** 
     *  Initialisation for the ServiceAssuranceManager
     */
    public boolean init(String [] args) {
        // allocate a new logger
        Logger logger = Logger.getLogger("log");

        // tell it to output to stdout and tell it what to pick up
        // it will actually output things where the log has bit
        // MASK.STDOUT set

        // tell it to output to stderr and tell it what to pick up
        // it will actually output things where the log has bit
        // MASK.ERROR set
        logger.addOutput(System.err, new BitMask(MASK.ERROR));
        logger.addOutput(System.out, new BitMask(MASK.STDOUT));

        // add some extra output channels, using mask bit 7, 8, 9, 10
        try {
            //logger.addOutput(new PrintWriter(new FileOutputStream("/tmp/slicec-channel7.out")), new BitMask(1<<9));
            //logger.addOutput(new PrintWriter(new FileOutputStream("/tmp/slicec-channel8.out")), new BitMask(1<<9));
            //logger.addOutput(new PrintWriter(new FileOutputStream("/tmp/slicec-channel9.out")), new BitMask(1<<9));
            //logger.addOutput(new PrintWriter(new FileOutputStream("/tmp/slicec-channel10.out")), new BitMask(1<<10));
            //logger.addOutput(new PrintWriter(new FileOutputStream("/tmp/slicec-channel11.out")), new BitMask(1<<11));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }


        // Process the args

        // Get config file name
        if (args.length == 1) {
            configFileName = args[0];
        } else {
            configFileName = "config/basic.json";
        }


        try {
            String configStr = readConfigFile(configFileName);
            config = new JSONObject(configStr);

            System.err.println("config = " + config);

        } catch (IOException e) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Cannot open config file: " + configFileName);
            return false;
        } catch (JSONException je) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Invalid json in config file: " + configFileName + " at " + je.getMessage());
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Start the SliceController
     */
    public boolean start() {
        boolean resVal = true;
        JSONObject escapeConfig = null;
        JSONObject gvnfmConfig = null;
        
        String escapeHost;
        int escapePort;
        String escapeURL;
        int escapeTimeout;
        
        try {
            escapeConfig = config.getJSONObject("escape");
            
            escapeHost = escapeConfig.getString("host");
            escapePort = escapeConfig.getInt("port");
            escapeURL = escapeConfig.getString("url");
            escapeTimeout = escapeConfig.getInt("timeout");
        } catch (JSONException jex) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Warning: No valid config for 'escape' found, using default configuration");
            
            escapeHost = "escape";
            escapePort = 8888;
            escapeURL = "/ro/v0";
            escapeTimeout = 20;
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "escape Host => " + escapeHost + 
                                                                 "; escape Port => " + escapePort + 
                                                                 "; escape URL => " + escapeURL +
                                                                 "; escape Timeout => " + escapeTimeout);
        }
        
        String gvnfmHost;
        int gvnfmPort;
        String gvnfmURL;
        
        try {
            gvnfmConfig = config.getJSONObject("gvnfm");
            
            gvnfmHost = gvnfmConfig.getString("host");
            gvnfmPort = gvnfmConfig.getInt("port");
            gvnfmURL = gvnfmConfig.getString("url");
        } catch (JSONException jex) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Warning: No valid config for 'gvnfm' found, using ESCAPE configuration");
            
            gvnfmHost = escapeHost;
            gvnfmPort = escapePort;
            gvnfmURL = escapeURL;
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "gvnfm Host => " + gvnfmHost + 
                                                                 "; gvnfm Port => " + gvnfmPort + 
                                                                 "; gvnfm URL => " + gvnfmURL);
            
        }
            
        try {
            resourceOrchestratorInteractor = new ResourceOrchestratorInteractor(escapeHost, 
                                                                                escapePort,
                                                                                escapeURL,
                                                                                gvnfmHost,
                                                                                gvnfmPort,
                                                                                gvnfmURL);

            // get a snapshot of the infrastructure waiting up to timeout seconds
            resourceOrchestratorInteractor.getInfrastructureView(escapeTimeout);
            
            // setup queue
            queue = new LinkedBlockingQueue<ViolationInfo>();

            // setup violationProcessor, and pass in queue
            violationProcessor = new ViolationProcessor(queue, resourceOrchestratorInteractor);

            vpThread = new Thread(violationProcessor);

            vpThread.start();
        
            // start console
            if (resVal) {

                startConsole();

                Logger.getLogger("log").logln(MASK.STDOUT, leadin() + "Started");

                return true;
            } else {

                return false;
            }

        } catch (ResourceOrchestratorException roe) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Error contacting the resource orchestrator: " + roe.getMessage());
            return false;
        }
        

    }

    /**
     * Stop the SliceController.
     */
    public boolean stop() {
        Logger.getLogger("log").logln(MASK.STDOUT, leadin() + "Stopping");

        // Stop resources
        stopConsole();

        Logger.getLogger("log").logln(MASK.STDOUT, leadin() + "Stopped");

        return true;
    }

    /**
     * Shutdown
     */
    public void shutDown() {
        stop();
    }
    /**
     * Start the console.
     */
    protected void startConsole() {
        console = new ServiceAssuranceManagerConsole(this, getPort());
        console.start();
    }

    /**
     * Stop the console.
     */
    protected void stopConsole() {
        console.stop();
    }


    /**
     * Get the port for the ManagementConsole
     */
    public int getPort() {
        return consolePort;
    }

    /**
     * Get the Queue that ViolationInfo events go onto
     */
    public LinkedBlockingQueue<ViolationInfo> getQueue() {
        return queue;
    }
    

    /**
     * Read a config file and return it as a string
     */
    protected String readConfigFile(String filename) throws IOException {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException e) {
            throw e;
        }        

        String line = null;
        StringBuilder stringBuilder = new StringBuilder();

        System.getProperty("line.separator");

        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
            stringBuilder.append(" ");
        }
        reader.close();

        return stringBuilder.toString();

    }

    /* Main functional methods */

    /**
     * Leadin String for msgs
     */
    protected String leadin() {
        return "Service Assurance Manager: ";
    }
}
