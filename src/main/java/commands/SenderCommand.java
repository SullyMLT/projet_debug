package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.List;

public class SenderCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        if (thread.frameCount() < 2) {
            return new ResultCommand(true, null, "Sender: null");
        }
        
        StackFrame callerFrame = thread.frame(1);
        ObjectReference sender = callerFrame.thisObject();
        
        String display = "Sender: " + (sender != null ? sender.toString() : "null");
        
        return new ResultCommand(true, sender, display);
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
