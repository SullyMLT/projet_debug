package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.StepRequest;
import result.ResultCommand;

import java.util.List;

public class StepCommand implements Command{
    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        vm.eventRequestManager().deleteEventRequests(vm.eventRequestManager().stepRequests());

        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_INTO);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("com.sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");

        stepRequest.addCountFilter(1);
        stepRequest.enable();
        return new ResultCommand(true, stepRequest, "Step into");
    }

    @Override
    public boolean shouldResume() {
        return true;
    }
}
