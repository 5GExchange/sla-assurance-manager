package fivegex.sla;

import java.lang.reflect.Constructor;
import cc.clayman.logging.Logger;
import cc.clayman.logging.MASK;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.IOException;

/**
 * This class processes violations that have been POSTed into the /violation/ REST end-pount.
 * It listens to a queue of violation events, and deals with them 1 by 1.
 */
public class ViolationProcessor implements Runnable {
    // The queue take thread
    Thread takeThread;

    // The queue to take from
    LinkedBlockingQueue<ViolationInfo> queue;

    // The ResourceOrchestratorInteractor
    ResourceOrchestratorInteractor resourceOrchestratorInteractor;

    // It the thread running
    boolean isRunning = true;


    public ViolationProcessor(LinkedBlockingQueue<ViolationInfo> queue, ResourceOrchestratorInteractor resourceOrchestratorInteractor) {
        this.queue = queue;
        this.resourceOrchestratorInteractor = resourceOrchestratorInteractor;
    }


    public void run() {
        ViolationInfo vInfo = null;
        
        while (isRunning) {
            // take the enxt element off the queue
            try {
                vInfo = queue.take();

                System.err.println("ViolationProcessor: queue take " + vInfo);
            } catch (InterruptedException ie) {
                System.err.println("ViolationProcessor: queue take() interrupted");
                isRunning = false;
            }

            // try and find the vnf type
            
            // vnfID is e.g., the content of  "agreementId" : "vnfvCDN_SLA_TEST_0_80a33f57-a39b-11e7-b368-0242ac120008"
            // without "vnf"            
            String vnfID = trimVNFid(vInfo.name);
            String kpi = vInfo.kpi;
            long timestamp = vInfo.timestamp;

            // lookup VNF type from ResourceOrchestratorInteractor
            String vnfType = null;

            try {
                // update to the latest infrastructure view
                resourceOrchestratorInteractor.getInfrastructureView();
                vnfType = resourceOrchestratorInteractor.getNFtype(vnfID);

                System.err.println("ResourceOrchestratorInteractor says contractUuid " + vnfID + " => " + vnfType);

                System.err.println("KPI name = " + kpi);
                
            } catch (ResourceOrchestratorException roe) {
                vnfType = null;
                System.err.println("ResourceOrchestratorInteractor failed: " + roe.getMessage());
            }


            // now try and construct the relevant ViolationAnalyser
            if (vnfType != null) {
                ViolationAnalyser violationAnalyser = trigger(vnfType, kpi, timestamp);

                try {
                    if (violationAnalyser != null) {
                        violationAnalyser.setContext(vnfID, vnfType, kpi, timestamp);
                        if (violationAnalyser.execute())
                            System.err.println("Lyfecycle management event correcly performed for VNF => " + vnfID);
                    }
                    else
                        System.err.println("There is no life cycle event defined for VNF type => " + vnfType + " and KPI => " + kpi);
                    
                    
                } catch (ViolationAnalyserException vae) {
                  System.err.println("Error while performing lifecycle event: " + vae.getMessage());
                  }
            }

        }
    }


    /** 
     * We should have a logic that according to the violation and the 
     * type of vnf can trigger some actions
     */
    public ViolationAnalyser trigger(String vnfType, String violationType, long timestamp) {
        String vnfMod = vnfType.replaceAll(":[a-zA-Z_0-9]*", ""); //should fix ":version" suffix in domain capabilities
        String className = "fivegex.sla.assurance.Violation" + vnfMod + violationType + "Analyser";
            
        ViolationAnalyser analyser = setupViolationAnalyser(className);

        return analyser;
    }

    /**
     * Set up a ViolationAnalyser
     */
    private ViolationAnalyser setupViolationAnalyser(String violationAnalyserClassName) {
        try {
            ViolationAnalyser newViolationAnalyser = null;
            
            Class<?> c = Class.forName(violationAnalyserClassName);
            Class<? extends ViolationAnalyser> cc = c.asSubclass(ViolationAnalyser.class);

            // find Constructor for when arg is ResourceOrchestratorInteractor
            Constructor<? extends ViolationAnalyser> cons = cc.getDeclaredConstructor(ResourceOrchestratorInteractor.class);

            newViolationAnalyser = cons.newInstance(resourceOrchestratorInteractor);

            Logger.getLogger("log").logln(MASK.STDOUT, leadin() + "Setup ViolationAnalyser: " + newViolationAnalyser);

            // if we get here we instantiated the ViolationAnalyser OK
            // so set it for further use
            return newViolationAnalyser;

        } catch (ClassNotFoundException cnfe) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Class not found " + violationAnalyserClassName);
            return null;
        } catch (Exception e) {
            Logger.getLogger("log").logln(MASK.ERROR, leadin() + "Cannot instantiate class " + violationAnalyserClassName);
            e.printStackTrace();
            return null;
        }
    }


    private String trimVNFid(String rawId) {
        return rawId.replaceFirst("^vnf", "");
    }
    
    private String leadin() {
        return "ViolationProcessor: ";
    }
}
