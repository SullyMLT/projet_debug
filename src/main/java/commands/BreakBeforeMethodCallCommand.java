package commands;

import com.sun.jdi.*;
import com.sun.jdi.request.MethodEntryRequest;
import result.ResultCommand;

import java.util.List;

public class BreakBeforeMethodCallCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        if (args.isEmpty()) {
            return new ResultCommand(false, null, "Error: method name required");
        }
        
        String methodName = args.get(0);
        MethodEntryRequest methodEntryReq = vm.eventRequestManager().createMethodEntryRequest();
        
        for (ReferenceType refType : vm.allClasses()) {
            for (Method method : refType.methods()) {
                if (method.name().equals(methodName)) {
                    methodEntryReq.addClassFilter(refType);
                }
            }
        }
        
        methodEntryReq.enable();
        
        return new ResultCommand(true, methodEntryReq, "Method entry breakpoint set for method: " + methodName);
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
