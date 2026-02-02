package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.List;

public class MethodCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        StackFrame frame = thread.frame(0);
        Method method = frame.location().method();
        
        StringBuilder display = new StringBuilder();
        display.append("Method: ").append(method.declaringType().name()).append(".");
        display.append(method.name()).append("(");
        
        boolean first = true;
        for (String argType : method.argumentTypeNames()) {
            if (!first) display.append(", ");
            display.append(argType);
            first = false;
        }
        display.append(") -> ").append(method.returnTypeName());
        
        return new ResultCommand(true, method, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
