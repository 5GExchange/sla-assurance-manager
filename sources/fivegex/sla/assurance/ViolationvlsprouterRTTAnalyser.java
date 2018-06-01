package fivegex.sla.assurance;

import fivegex.sla.ResourceOrchestratorInteractor;
import fivegex.sla.ViolationAnalyser;


public class ViolationvlsprouterRTTAnalyser implements ViolationAnalyser {

    public ViolationvlsprouterRTTAnalyser(ResourceOrchestratorInteractor ro) {
        System.err.println("In ViolationvlsprouterRTTAnalyser");
    }

    public void setContext(String vnfType, String kpiType, long timestamp) {
    }

    public boolean execute() {
        System.err.println("Migrate");

        return true;
    }
    
}
