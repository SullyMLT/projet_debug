package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.snapshot.ExecutionSnapshot;
import timetravel.debugger.TimeTravelEngine;

import java.util.List;

public class TimeTravelCommand implements Command {
    
    private TimeTravelEngine timeTravelEngine;
    
    public TimeTravelCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }
    
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) 
            throws IncompatibleThreadStateException, AbsentInformationException {
        
        if (args.isEmpty()) {
            return new ResultCommand(false, null, 
                "Error: snapshot ID required\nUsage: time-travel <snapshot-id>");
        }
        
        int snapshotId;
        try {
            snapshotId = Integer.parseInt(args.get(0));
        } catch (NumberFormatException e) {
            return new ResultCommand(false, null, "Error: invalid snapshot ID");
        }

        ExecutionSnapshot snapshot = timeTravelEngine.travelToSnapshot(snapshotId);
        
        if (snapshot == null) {
            return new ResultCommand(false, null, 
                "Error: snapshot #" + snapshotId + " not found");
        }
        
        StringBuilder display = new StringBuilder();
        display.append("Time Travel to Snapshot #").append(snapshotId).append("\n");
        display.append("Location: ").append(snapshot.getLocation().lineNumber());
        
        return new ResultCommand(true, snapshot, display.toString());
    }
    
    @Override
    public boolean shouldResume() {
        return false;
    }
}
