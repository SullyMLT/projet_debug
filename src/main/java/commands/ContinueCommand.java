package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import result.ResultCommand;

import java.util.List;

public class ContinueCommand implements Command {
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        return new ResultCommand(true, null, "Continue execution");
    }

    @Override
    public boolean shouldResume() {
        return true;
    }
}
