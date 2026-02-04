package timetravel.snapshot;

import com.sun.jdi.*;

import java.util.*;

public class ExecutionSnapshot {
    private final int snapshotId;
    private final Location location;
    // stack frame capturee lors de l execution
    private final List<StackFrameSnapshot> stackFrames;
    // variable etats
    private final Map<String, VariableSnapshot> localVariables;
    private boolean breakpoint;

    public ExecutionSnapshot(int id, ThreadReference thread) throws IncompatibleThreadStateException, AbsentInformationException {
        this.snapshotId = id;
        StackFrame currentFrame = thread.frame(0);
        this.location = currentFrame.location();
        this.stackFrames = captureStackFrames(thread);
        this.localVariables = captureLocalVariables(currentFrame);
    }
    
    private List<StackFrameSnapshot> captureStackFrames(ThreadReference thread) throws IncompatibleThreadStateException {
        List<StackFrameSnapshot> frames = new ArrayList<>();
        for (StackFrame frame : thread.frames()) {
            frames.add(new StackFrameSnapshot(frame));
        }
        return frames;
    }
    
    private Map<String, VariableSnapshot> captureLocalVariables(StackFrame frame) throws AbsentInformationException {
        Map<String, VariableSnapshot> vars = new HashMap<>();
        for (LocalVariable var : frame.visibleVariables()) {
            Value value = frame.getValue(var);
            vars.put(var.name(), new VariableSnapshot(var.name(), var.typeName(), value, var));
        }
        return vars;
    }

    public String printLocalVariables() {
        StringBuilder result = new StringBuilder();
        if (localVariables.isEmpty()) {
            result.append("\n");
        } else {
            for (Map.Entry<String, VariableSnapshot> entry : localVariables.entrySet()) {
                result.append(entry.getValue().printToConsole());
            }
        }
        return result.toString();
    }

    public int getSnapshotId() { return snapshotId; }
    public Location getLocation() { return location; }
    public List<StackFrameSnapshot> getStackFrames() { return stackFrames; }
    public Map<String, VariableSnapshot> getLocalVariables() { return localVariables; }
    public int getLineNumber() { return location.lineNumber(); }
    public boolean isBreakpoint() { return breakpoint; }
    public void setBreakpoint(boolean breakpoint) { this.breakpoint = breakpoint; }
    
    @Override
    public String toString() {
        return String.format("%s, line : %d",
            location.declaringType().name(),
            location.lineNumber()
        );
    }
}
