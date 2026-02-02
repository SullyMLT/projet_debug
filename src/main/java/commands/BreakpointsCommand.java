package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.BreakpointRequest;
import result.ResultCommand;

import java.util.List;

public class BreakpointsCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        List<BreakpointRequest> breakpoints = vm.eventRequestManager().breakpointRequests();
        
        StringBuilder display = new StringBuilder();
        display.append("Active breakpoints:\n");
        
        if (breakpoints.isEmpty()) {
            display.append("  (none)");
        } else {
            for (int i = 0; i < breakpoints.size(); i++) {
                BreakpointRequest bp = breakpoints.get(i);
                display.append("  [").append(i).append("] ");
                display.append(bp.location().declaringType().name()).append(":");
                display.append(bp.location().lineNumber());
                display.append(" (").append(bp.isEnabled() ? "enabled" : "disabled").append(")\n");
            }
        }
        
        return new ResultCommand(true, breakpoints, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
