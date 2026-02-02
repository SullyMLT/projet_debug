package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.List;

public class PrintVarCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException, AbsentInformationException {
        if (args.isEmpty()) {
            return new ResultCommand(false, null, "Error: nom variable required");
        }
        
        String varName = args.get(0);
        StackFrame frame = thread.frame(0);
        
        for (LocalVariable var : frame.visibleVariables()) {
            if (var.name().equals(varName)) {
                Value value = frame.getValue(var);
                return new ResultCommand(true, value, varName + " -> " + value);
            }
        }
        
        ObjectReference receiver = frame.thisObject();
        if (receiver != null) {
            for (Field field : receiver.referenceType().allFields()) {
                if (field.name().equals(varName)) {
                    Value value = receiver.getValue(field);
                    return new ResultCommand(true, value, varName + " -> " + value);
                }
            }
        }
        
        return new ResultCommand(false, null, "Error: variable '" + varName + "' not found");
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
