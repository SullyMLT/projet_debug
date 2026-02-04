package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.snapshot.ExecutionSnapshot;

import java.util.List;

public class ContinueCommand implements Command {

    private TimeTravelEngine timeTravelEngine;

    public ContinueCommand() {
        this.timeTravelEngine = null;
    }

    public ContinueCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }

    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        // Mode replay : vm == null && thread == null -> aller au dernier snapshot ou prochain breakpoint
        if (vm == null && thread == null) {
            if (timeTravelEngine == null) {
                return new ResultCommand(false, null, "TimeTravelEngine not available");
            }
            // Aller au dernier snapshot ou au prochain breakpoint
            while (timeTravelEngine.forwardtrack()) {
                ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();
                if (snapshot.isBreakpoint()){
                    StringBuilder display = new StringBuilder();
                    display.append("Continue stop by breakpoint at line: "+snapshot.getLocation().lineNumber()+"\n");
                    display.append(snapshot.toString());
                    return new ResultCommand(true, snapshot, display.toString());
                }
            }
            ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();
            if (snapshot != null) {
                StringBuilder display = new StringBuilder();
                display.append("Continue to last line of execution\n");
                display.append(snapshot.toString());
                return new ResultCommand(true, snapshot, display.toString());
            }
            return new ResultCommand(false, null, "No snapshots available");
        }

        // Mode VM active
        return new ResultCommand(true, null, "Continue execution");
    }

    @Override
    public boolean shouldResume() {
        return true;
    }
}
