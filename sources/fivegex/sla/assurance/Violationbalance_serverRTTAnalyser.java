package fivegex.sla.assurance;

import fivegex.sla.ResourceOrchestratorException;
import fivegex.sla.ResourceOrchestratorInteractor;
import fivegex.sla.ViolationAnalyser;
import fivegex.sla.ViolationAnalyserException;
import java.io.IOException;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.Resty;
import static us.monoid.web.Resty.form;


public class Violationbalance_serverRTTAnalyser implements ViolationAnalyser {
    
    ResourceOrchestratorInteractor ro;
    String vnfID = null;
    Resty r;

    public Violationbalance_serverRTTAnalyser(ResourceOrchestratorInteractor ro) {
        System.err.println("In Violationbalance_serverRTTAnalyser");
        this.ro = ro;
        r = new Resty();
    }

    public void setContext(String vnfID, String vnfType, String kpiType, long timestamp) {
        this.vnfID = vnfID;
        
    }
    
    
    private void sendMonitoringRequest() throws IOException {
        String imosURL = "http://imos:2222/monitoring/?vnfid=" + vnfID;
        System.err.println("Sending monitoring reconfiguration request to: " + imosURL);
        r.json(imosURL, form(""));
    }
    

    @Override
    public boolean execute() throws ViolationAnalyserException {
        try {
            ro.setMigrationStatusOnNF(vnfID);
            JSONObject migrationResult = ro.startMigration(vnfID);
            if (migrationResult.getBoolean("success")) {
                System.err.println("Violationbalance_serverRTTAnalyser: Performed migration of vnf => " + vnfID);
                System.err.println("Migrated VNF ID is => " + migrationResult.getString("id"));
                sendMonitoringRequest();
                return true;
            }
        } catch (ResourceOrchestratorException | JSONException e) {
            throw new ViolationAnalyserException("ViolationAnalyser error: " + e.getMessage());  
        } catch (IOException ie) {
            throw new ViolationAnalyserException("ViolationAnalyser error while sending monitoring reconfiguration request: " + ie.getMessage());  
        }
    return false;    
    }
    
}
