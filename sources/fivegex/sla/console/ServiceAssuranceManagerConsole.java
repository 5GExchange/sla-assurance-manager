package fivegex.sla.console;

import fivegex.sla.ServiceAssuranceManager;
import cc.clayman.console.AbstractRestConsole;

/**
 * A Rest console for the ServiceAssuranceManager
 */
public class ServiceAssuranceManagerConsole extends AbstractRestConsole {
    public ServiceAssuranceManagerConsole(ServiceAssuranceManager controller, int port) {
        setAssociated(controller);
        initialise(port);
    }

    public void registerCommands() {

        // setup /command/ handler
        defineRequestHandler("/command/.*", new CommandHandler());

        // setup /violation/ handler
        defineRequestHandler("/violation/.*", new ViolationHandler());


    }


    public ServiceAssuranceManager getController() {
        return (ServiceAssuranceManager)getAssociated();
    }
}
