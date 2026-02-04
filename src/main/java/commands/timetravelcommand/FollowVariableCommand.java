package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.snapshot.VariableModification;

import java.util.List;

public class FollowVariableCommand implements Command {
    
    private TimeTravelEngine timeTravelEngine;
    
    public FollowVariableCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }
    
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) 
            throws IncompatibleThreadStateException, AbsentInformationException {
        
        if (args.isEmpty()) {
            return new ResultCommand(false, null, "Usage: follow-variable <variableName>");
        }
        
        String varName = args.get(0);

        // recupere les modifications de la variable
        List<VariableModification> modifications = timeTravelEngine.findVariableModifications(varName, null);

        if (modifications.isEmpty()) {
            return new ResultCommand(false, modifications, 
                "No snapshots found for variable: " + varName);
        }

        StringBuilder display = new StringBuilder();
        display.append("Variable Tracking: ").append(varName).append("\n");

        String currentSnapshotValue = "";
        for (VariableModification mod : modifications) {
            if (!currentSnapshotValue.equals(mod.getValue())){
                currentSnapshotValue = mod.getValue();
                display.append(String.format("Snapshot #%d | Value: %s | Line: %d\n",
                        mod.getSnapshot().getSnapshotId(),
                        mod.getValue() == null ? "null" : mod.getValue(),
                        mod.getSnapshot().getLineNumber()));
            }
        }
        
        return new ResultCommand(true, modifications, display.toString());
    }
    
    @Override
    public boolean shouldResume() {
        return false;
    }
}
