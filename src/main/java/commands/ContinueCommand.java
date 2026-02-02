package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.List;

public class ContinueCommand implements Command {
    @Override
    public boolean execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        System.out.println("Continue execution");
        return true;
    }
}
