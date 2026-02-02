package commands;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import result.ResultCommand;

import java.util.List;

public class FrameCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        StackFrame frame = thread.frame(0);
        StringBuilder display = new StringBuilder();
        display.append("Frame: ").append(frame.location()).append("\n");
        display.append("  Class: ").append(frame.location().declaringType().name()).append("\n");
        display.append("  Method: ").append(frame.location().method().name()).append("\n");
        display.append("  Line: ").append(frame.location().lineNumber());
        
        return new ResultCommand(true, frame, display.toString());
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
