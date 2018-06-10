package fivegex.sla.assurance;

import fivegex.sla.ResourceOrchestratorException;
import fivegex.sla.ResourceOrchestratorInteractor;
import fivegex.sla.ViolationAnalyser;
import fivegex.sla.ViolationAnalyserException;


public class Violationbalance_serverRTTAnalyser implements ViolationAnalyser {
    
    ResourceOrchestratorInteractor ro;
    String vnfID = null;

    public Violationbalance_serverRTTAnalyser(ResourceOrchestratorInteractor ro) {
        System.err.println("In Violationbalance_serverRTTAnalyser");
        this.ro = ro;
    }

    public void setContext(String vnfID, String vnfType, String kpiType, long timestamp) {
        this.vnfID = vnfID;
        
    }

    @Override
    public boolean execute() throws ViolationAnalyserException {
        try {
            ro.setMigrationStatusOnNF(vnfID);
            ro.startMigration();
            System.err.println("Violationbalance_serverRTTAnalyser: Performed migration of vnf => " + vnfID);
        } catch (ResourceOrchestratorException roe) {
            throw new ViolationAnalyserException("ViolationAnalyser error: " + roe.getMessage());  
        }

        return true;
    }
    
}
