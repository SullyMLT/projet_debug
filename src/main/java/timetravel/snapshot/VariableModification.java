package timetravel.snapshot;

import com.sun.jdi.Location;

public class VariableModification {
    private final ExecutionSnapshot snapshot;
    private final String variableName;
    private final String value;
    private final Location location;

    public VariableModification(ExecutionSnapshot snapshot, String variableName, String newValue, Location location) {
        this.snapshot = snapshot;
        this.variableName = variableName;
        this.value = newValue;
        this.location = location;
    }

    public ExecutionSnapshot getSnapshot() { return snapshot; }
    public String getVariableName() { return variableName; }
    public String getValue() { return value; }
    public Location getLocation() { return location; }

    @Override
    public String toString() {
        return String.format("Snapshot #%d - %s = %s line = %d",
                snapshot.getSnapshotId(),
                variableName,
                value,
                location.lineNumber()
        );
    }
}