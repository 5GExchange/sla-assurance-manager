package fivegex.sla;

/**
 * These are the functions a ViolationAnalyser has to do.
 */
public interface ViolationAnalyser {
    public void setContext(String vnfID, String vnfType, String kpiType, long timestamp);

    public boolean execute() throws ViolationAnalyserException;
}
