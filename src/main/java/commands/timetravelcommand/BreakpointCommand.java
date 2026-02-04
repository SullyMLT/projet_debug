package commands.timetravelcommand;

import com.sun.jdi.*;
import commands.Command;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;

import java.util.List;

public class BreakpointCommand implements Command {

    private TimeTravelEngine timeTravelEngine;

    public BreakpointCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }

    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args)
            throws IncompatibleThreadStateException, AbsentInformationException {

        if (args.isEmpty()) {
            return new ResultCommand(false, null, "Usage: breakpoint <lineNumber>");
        }

        int lineNumber;
        try {
            lineNumber = Integer.parseInt(args.get(0));
        } catch (NumberFormatException e) {
            return new ResultCommand(false, null, "Invalid line number: " + args.get(0));
        }

        boolean result = timeTravelEngine.setBreakpointAtLine(lineNumber);

        if (!result) {
            return new ResultCommand(false, null,
                    "Error: could not set breakpoint at line " + lineNumber);
        }

        return new ResultCommand(true, lineNumber,
            "Breakpoint set at line " + lineNumber);
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
