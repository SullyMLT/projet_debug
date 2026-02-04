package commands;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.StepRequest;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.snapshot.ExecutionSnapshot;

import java.util.List;

public class StepOverCommand implements Command {

    private TimeTravelEngine timeTravelEngine;

    public StepOverCommand() {
        this.timeTravelEngine = null;
    }

    public StepOverCommand(TimeTravelEngine engine) {
        this.timeTravelEngine = engine;
    }

    @Override
    public ResultCommand execute(ThreadReference thread, VirtualMachine vm, List<String> args) {
        // vm disable
        if (vm == null && thread == null) {
            if (timeTravelEngine == null) {
                return new ResultCommand(false, null, "step over not available");
            }
            if (timeTravelEngine.stepOver()) {
                ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();
                StringBuilder display = new StringBuilder();
                display.append("Step-over\n");
                display.append(snapshot.printLocalVariables());
                return new ResultCommand(true, snapshot, display.toString());
            } else {
                return new ResultCommand(false, null, "program finished execution");
            }
        }

        // vm enable
        vm.eventRequestManager().deleteEventRequests(vm.eventRequestManager().stepRequests());

        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_OVER);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("javax.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.addClassExclusionFilter("com.sun.*");
        stepRequest.addClassExclusionFilter("jdk.*");

        stepRequest.addCountFilter(1);
        stepRequest.enable();
        return new ResultCommand(true, stepRequest, "Step over");
    }

    @Override
    public boolean shouldResume() {
        return true;
    }
}
