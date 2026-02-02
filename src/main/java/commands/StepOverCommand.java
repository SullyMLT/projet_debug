package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.StepRequest;

import java.util.List;

public class StepOverCommand implements Command {

    @Override
    public boolean execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        vm.eventRequestManager().deleteEventRequests(vm.eventRequestManager().stepRequests());

        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_OVER);
        stepRequest.addCountFilter(1);
        stepRequest.enable();
        return true;
    }
}
