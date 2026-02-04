package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.snapshot.ExecutionSnapshot;

import java.util.List;

public class BackCommand implements Command {

    private TimeTravelEngine timeTravelEngine;

    public BackCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }

    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args)
            throws IncompatibleThreadStateException, AbsentInformationException {

        if (timeTravelEngine.backtrack()) {
            ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();

            StringBuilder display = new StringBuilder();
            display.append("Back to line: "+snapshot.getLocation().lineNumber()+"\n");
            display.append(snapshot.toString());

            return new ResultCommand(true, snapshot, display.toString());
        } else {
            return new ResultCommand(false, null, "can't go back more in execution");
        }
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
