package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.snapshot.ExecutionSnapshot;
import timetravel.debugger.TimeTravelEngine;

import java.util.List;

/**
 * Commande pour afficher tous les snapshots collectés
 */
public class ShowSnapshotsCommand implements Command {
    
    private TimeTravelEngine timeTravelEngine;
    
    public ShowSnapshotsCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }
    
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) 
            throws IncompatibleThreadStateException, AbsentInformationException {
        
        List<ExecutionSnapshot> snapshots = timeTravelEngine.getAllSnapshots();
        
        if (snapshots.isEmpty()) {
            return new ResultCommand(false, snapshots, "No modification yet");
        }

        StringBuilder display = new StringBuilder();
        display.append("Execution modification\n");
        display.append("Total: ").append(snapshots.size()).append("\n");
        
        for (ExecutionSnapshot snapshot : snapshots) {
            display.append(snapshot.toString());
            display.append("\n");
        }
        
        return new ResultCommand(true, snapshots, display.toString());
    }
    
    @Override
    public boolean shouldResume() {
        return false;
    }
}
