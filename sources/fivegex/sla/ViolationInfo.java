package fivegex.sla;

public class ViolationInfo {
    public String name;
    public String kpi;
    public long timestamp;
    
    /**
     * Takes a name and a timestamp in millis 
     */
    public ViolationInfo(String name, String kpi, long ts) {
        this.name = name;
        this.kpi = kpi;
        this.timestamp = ts;
    }

    public String toString() {
        return "(" + name + ", " + (timestamp / 1000) + ")";
    }
}
