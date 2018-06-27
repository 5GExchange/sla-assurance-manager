package fivegex.sla.assurance;

import fivegex.sla.ResourceOrchestratorException;
import fivegex.sla.ResourceOrchestratorInteractor;
import fivegex.sla.ViolationAnalyser;
import fivegex.sla.ViolationAnalyserException;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;


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
            JSONObject migrationResult = ro.startMigration(vnfID);
            if (migrationResult.getBoolean("success")) {
                System.err.println("Violationbalance_serverRTTAnalyser: Performed migration of vnf => " + vnfID);
                System.err.println("Migrated VNF ID is => " + migrationResult.getString("id"));
            }
        } catch (ResourceOrchestratorException | JSONException e) {
            throw new ViolationAnalyserException("ViolationAnalyser error: " + e.getMessage());  
        }
    return true;    
    }
    
}
