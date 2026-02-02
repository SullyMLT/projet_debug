package commands;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import result.ResultCommand;

import java.util.List;

public class StackCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        List<StackFrame> stack = thread.frames();
        
        StringBuilder display = new StringBuilder();
        display.append("Stack trace:\n");
        for (int i = 0; i < stack.size(); i++) {
            StackFrame frame = stack.get(i);
            display.append("  [").append(i).append("] ");
            display.append(frame.location().declaringType().name()).append(".");
            display.append(frame.location().method().name()).append("()");
            display.append(" line ").append(frame.location().lineNumber()).append("\n");
        }
        
        return new ResultCommand(true, stack, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
