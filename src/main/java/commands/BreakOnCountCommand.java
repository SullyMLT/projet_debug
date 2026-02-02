package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import result.ResultCommand;

import java.util.List;

public class BreakOnCountCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws AbsentInformationException {
        if (args.size() < 3) {
            return new ResultCommand(false, null, "Error: filename, line number and count required");
        }
        
        String className = args.get(0).replace(".java", "");
        int lineNumber = Integer.parseInt(args.get(1));
        int count = Integer.parseInt(args.get(2));
        
        for (ReferenceType refType : vm.allClasses()) {
            if (refType.name().contains(className)) {
                List<Location> locations = refType.locationsOfLine(lineNumber);
                if (!locations.isEmpty()) {
                    Location location = locations.get(0);
                    BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                    bpReq.addCountFilter(count);
                    bpReq.enable();
                    
                    return new ResultCommand(true, bpReq, "Conditional breakpoint set at " + className + ":" + lineNumber + " (count=" + count + ")");
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
