package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReceiverVariablesCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        StackFrame frame = thread.frame(0);
        ObjectReference receiver = frame.thisObject();
        
        Map<String, Value> variables = new HashMap<>();
        
        if (receiver == null) {
            return new ResultCommand(true, variables, "Receiver variables: non");
        }
        
        ReferenceType refType = receiver.referenceType();
        for (Field field : refType.allFields()) {
            variables.put(field.name(), receiver.getValue(field));
        }
        
        StringBuilder display = new StringBuilder();
        display.append("Receiver variables:\n");
        for (Map.Entry<String, Value> entry : variables.entrySet()) {
            display.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }
        
        return new ResultCommand(true, variables, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
