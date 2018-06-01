package fivegex.sla;

import java.lang.reflect.Constructor;
import cc.clayman.logging.BitMask;
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

            // try and find thw vnf type
            String name = vInfo.name;
            String kpi = vInfo.kpi;
            long timestamp = vInfo.timestamp;

            // lookup VNF type from ResourceOrchestratorInteractor
            String vnfType = null;

            try {
                vnfType = resourceOrchestratorInteractor.getNFtype(name);

                System.err.println("ResourceOrchestratorInteractor says contractUuid " + name + " => " + vnfType);

                System.err.println("KPI name = " + kpi);
                
            } catch (IOException ioe) {
                vnfType = null;
                System.err.println("ResourceOrchestratorInteractor " + ioe.getMessage());
            }


            // now try and construct the relevant ViolationAnalyser
            if (vnfType != null) {
                ViolationAnalyser violationAnalyser = trigger(vnfType, kpi, timestamp);

                violationAnalyser.setContext(vnfType, kpi, timestamp);

                violationAnalyser.execute();
            }

        }
    }


    /** 
     * We should have a logic that according to the violation and the 
     * type of vnf can trigger some actions
     */
    public ViolationAnalyser trigger(String vnfType, String violationType, long timestamp) {
        String vnfMod = vnfType.replaceAll(":", "");
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


    private String leadin() {
        return "ViolationProcessor: ";
    }
}
