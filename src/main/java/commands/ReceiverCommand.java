package commands;

import com.sun.jdi.*;
import result.ResultCommand;

import java.util.List;

public class ReceiverCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) throws IncompatibleThreadStateException {
        StackFrame frame = thread.frame(0);
        ObjectReference receiver = frame.thisObject();
        
        String display = "Receiver: " + (receiver != null ? receiver.toString() : "null");
        
        return new ResultCommand(true, receiver, display);
    }

    @Override
    public boolean shouldResume() {
        return false;
    }
}
