package timetravel.snapshot;

import com.sun.jdi.*;

import java.util.*;

public class VariableSnapshot {
    private final String name;
    private final String typeName;
    private final String valueString;
    private final Object debuggerObject;
    private final List<VariableSnapshot> children;

    public VariableSnapshot(String name, String typeName, Value value, Object debuggerObject) {
        this.name = name;
        this.typeName = typeName;
        this.debuggerObject = debuggerObject;
        this.children = new ArrayList<>();

        if (value == null) {
            this.valueString = "null";
        } else if (value instanceof ArrayReference) {
            ArrayReference arrayRef = (ArrayReference) value;
            this.valueString = "Array[" + arrayRef.length() + "]";
            captureArrayChildren(arrayRef);
        } else if (value instanceof StringReference) {
            this.valueString = "\"" + ((StringReference) value).value() + "\"";
        } else if (value instanceof ObjectReference) {
            ObjectReference objRef = (ObjectReference) value;
            this.valueString = objRef.referenceType().name();
            captureObjectChildren(objRef);
        } else {
            this.valueString = value.toString();
        }
    }

    private void captureArrayChildren(ArrayReference arrayRef) {
        try {
            List<Value> values = arrayRef.getValues();
            int limit = Math.min(values.size(), 20);
            for (int i = 0; i < limit; i++) {
                Value val = values.get(i);
                String childType = val != null ? val.type().name() : "null";
                children.add(new VariableSnapshot("[" + i + "]", childType, val, null));
            }
            if (values.size() > 20) {
                children.add(new VariableSnapshot("...", "(" + (values.size() - 20) + " more)", null, null));
            }
        } catch (Exception e) {
            // Ignorer les erreurs
        }
    }

    private void captureObjectChildren(ObjectReference objRef) {
        try {
            ReferenceType refType = objRef.referenceType();
            for (Field field : refType.allFields()) {
                if (!field.isStatic()) {
                    Value fieldValue = objRef.getValue(field);
                    children.add(new VariableSnapshot(field.name(), field.typeName(), fieldValue, field));
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs
        }
    }

    public String getName() { return name; }
    public String getTypeName() { return typeName; }
    public String getValueString() { return valueString; }
    public Object getDebuggerObject() { return debuggerObject; }
    public List<VariableSnapshot> getChildren() { return children; }
    public boolean hasChildren() { return !children.isEmpty(); }

    @Override
    public String toString() {
        return name + " (" + typeName + ") = " + valueString;
    }
}
