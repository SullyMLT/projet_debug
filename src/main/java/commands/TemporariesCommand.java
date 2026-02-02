package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemporariesCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame frame = thread.frame(0);
        Map<String, Value> temporaries = new HashMap<>();
        
        for (LocalVariable var : frame.visibleVariables()) {
            temporaries.put(var.name(), frame.getValue(var));
        }
        
        StringBuilder display = new StringBuilder();
        display.append("Temporaries:\n");
        for (Map.Entry<String, Value> entry : temporaries.entrySet()) {
            display.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }
        
        return new ResultCommand(true, temporaries, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
