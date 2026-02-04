package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.snapshot.ExecutionSnapshot;

import java.util.List;

public class ForwardCommand implements Command {

    private TimeTravelEngine timeTravelEngine;

    public ForwardCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }

    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args)
            throws IncompatibleThreadStateException, AbsentInformationException {

        if (timeTravelEngine.forwardtrack()) {
            ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();

            StringBuilder display = new StringBuilder();
            display.append("Forward to line: "+snapshot.getLocation().lineNumber()+"\n");
            display.append(snapshot.toString());

            return new ResultCommand(true, snapshot, display.toString());
        } else {
            return new ResultCommand(false, null, "program finished execution");
        }
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
