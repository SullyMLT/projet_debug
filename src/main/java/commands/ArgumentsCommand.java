package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgumentsCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame frame = thread.frame(0);
        Map<String, Value> arguments = new HashMap<>();
        
        Method method = frame.location().method();
        List<LocalVariable> variables = frame.visibleVariables();
        
        for (LocalVariable var : variables) {
            if (var.isArgument()) {
                arguments.put(var.name(), frame.getValue(var));
            }
        }
        
        StringBuilder display = new StringBuilder();
        display.append("Arguments:\n");
        for (Map.Entry<String, Value> entry : arguments.entrySet()) {
            display.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }
        
        return new ResultCommand(true, arguments, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
