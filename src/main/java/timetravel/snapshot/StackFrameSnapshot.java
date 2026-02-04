package timetravel.snapshot;

import com.sun.jdi.*;

import java.util.HashMap;
import java.util.Map;

public class StackFrameSnapshot {
    private final String className;
    private final String methodName;
    private final int lineNumber;
    private final Map<String, VariableSnapshot> localVariables;

    public StackFrameSnapshot(StackFrame frame) {
        Location loc = frame.location();
        this.className = loc.declaringType().name();
        this.methodName = loc.method().name();
        this.lineNumber = loc.lineNumber();
        this.localVariables = captureLocalVariables(frame);
    }

    private Map<String, VariableSnapshot> captureLocalVariables(StackFrame frame) {
        Map<String, VariableSnapshot> vars = new HashMap<>();
        try {
            for (LocalVariable var : frame.visibleVariables()) {
                Value value = frame.getValue(var);
                vars.put(var.name(), new VariableSnapshot(var.name(), var.typeName(), value, var));
            }
        } catch (AbsentInformationException e) {
            // Pas de variables disponibles
        }
        return vars;
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public int getLineNumber() { return lineNumber; }
    public Map<String, VariableSnapshot> getLocalVariables() { return localVariables; }

    @Override
    public String toString() {
        return className + "." + methodName + "():" + lineNumber;
    }
}