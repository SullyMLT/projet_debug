package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import result.ResultCommand;

import java.util.List;

public class BreakCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws AbsentInformationException {
        if (args.size() < 2) {
            return new ResultCommand(false, null, "Error: filename and line number required");
        }
        
        String className = args.get(0).replace(".java", "");
        int lineNumber = Integer.parseInt(args.get(1));
        
        for (ReferenceType refType : vm.allClasses()) {
            if (refType.name().contains(className)) {
                List<Location> locations = refType.locationsOfLine(lineNumber);
                if (!locations.isEmpty()) {
                    Location location = locations.get(0);
                    BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                    bpReq.enable();
                    
                    return new ResultCommand(true, bpReq, "Breakpoint set at " + className + ":" + lineNumber);
                }
            }
        }
        
        return new ResultCommand(false, null, "Error: could not set breakpoint at " + className + ":" + lineNumber);
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
