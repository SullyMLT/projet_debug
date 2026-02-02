package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.StepRequest;

import java.util.List;

public class StepCommand implements Command{
    @Override
    public boolean execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        vm.eventRequestManager().deleteEventRequests(vm.eventRequestManager().stepRequests());

        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(thread,
                StepRequest.STEP_MIN,
                StepRequest.STEP_INTO);
        stepRequest.addCountFilter(1);
        stepRequest.enable();
        return true;
    }
}
