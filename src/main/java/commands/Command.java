package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;

import java.util.List;

public interface Command {
    boolean execute(ThreadReference thread, VirtualMachine vm, List<String> args);
}
