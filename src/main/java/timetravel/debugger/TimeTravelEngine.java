package timetravel.debugger;

import com.sun.jdi.*;
import timetravel.snapshot.ExecutionSnapshot;
import timetravel.snapshot.VariableModification;
import timetravel.snapshot.VariableSnapshot;

import java.util.*;

public class TimeTravelEngine {
    private final List<ExecutionSnapshot> executionHistory;
    private int currentSnapshotIndex;
    private int nextSnapshotId;
    
    public TimeTravelEngine() {
        this.executionHistory = new ArrayList<>();
        this.currentSnapshotIndex = -1;
        this.nextSnapshotId = 0;
    }
    
    // créer une snapshot
    public ExecutionSnapshot captureSnapshot(ThreadReference thread) {
        try {
            ExecutionSnapshot snapshot = new ExecutionSnapshot(nextSnapshotId++, thread);

            checkSnapshotUnchangeMultiple(snapshot);

            executionHistory.add(snapshot);
            currentSnapshotIndex = executionHistory.size() - 1;
            return snapshot;
        } catch (Exception e) {
            System.err.println("Erreur lors de la capture du snapshot: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean checkSnapshotUnchangeMultiple(ExecutionSnapshot snapshot) {
        for (ExecutionSnapshot execSnapshot : executionHistory) {
            if (execSnapshot.getLocation().equals(snapshot.getLocation())
                    && execSnapshot.getLocalVariables().equals(snapshot.getLocalVariables())) {
                // doublon, ne pas ajouter
                nextSnapshotId--;
                return false;
            }else{
                Map<String, VariableSnapshot> local = execSnapshot.getLocalVariables();
                Map<String, VariableSnapshot> currentLocal = snapshot.getLocalVariables();

                if (local.keySet().equals(currentLocal.keySet())) {
                    boolean allMatch = true;
                    for (String varName : local.keySet()) {
                        if (!local.get(varName).getValueString().equals(currentLocal.get(varName).getValueString())) {
                            allMatch = false;
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
    
    // modif variable specifique
    public List<VariableModification> findVariableModifications(String variableName, LocalVariable targetVariable) {
        List<VariableModification> modifications = new ArrayList<>();
        
        for (ExecutionSnapshot snapshot : executionHistory) {
            // cherche les variable local et récupère celle que l'on cherche
            VariableSnapshot varSnap = snapshot.getLocalVariables().get(variableName);
            
            if (varSnap != null) {
                //
                if (targetVariable != null && varSnap.getDebuggerObject() != null) {
                    if (!varSnap.getDebuggerObject().equals(targetVariable)) {
                        continue; // !equals
                    }
                }
                
                String currentValue = varSnap.getValueString();
                
                // modification
                modifications.add(new VariableModification(
                    snapshot,
                    variableName,
                    currentValue,
                    snapshot.getLocation()
                ));
            }
        }
        
        return modifications;
    }
    
    // recupere le snapshot par id
    public ExecutionSnapshot getSnapshotById(int snapshotId) {
        for (ExecutionSnapshot snapshot : executionHistory) {
            if (snapshot.getSnapshotId() == snapshotId) {
                return snapshot;
            }
        }
        return null;
    }
    
    // travel vers un snapshot
    public ExecutionSnapshot travelToSnapshot(int snapshotId) {
        ExecutionSnapshot snapshot = getSnapshotById(snapshotId);
        if (snapshot != null) {
            currentSnapshotIndex = executionHistory.indexOf(snapshot);
        }
        return snapshot;
    }

    public ExecutionSnapshot getCurrentSnapshot() {
        if (currentSnapshotIndex >= 0 && currentSnapshotIndex < executionHistory.size()) {
            return executionHistory.get(currentSnapshotIndex);
        }
        return null;
    }

    public List<ExecutionSnapshot> getAllSnapshots() {
        return new ArrayList<>(executionHistory);
    }


    public boolean backtrack() {
        if (currentSnapshotIndex > 0) {
            currentSnapshotIndex--;
            return true;
        }
        return false;
    }

    public boolean forwardtrack() {
        if (currentSnapshotIndex < executionHistory.size() - 1) {
            currentSnapshotIndex++;
            return true;
        }
        return false;
    }

    public boolean stepOver() {
        // verifie si pas dernier snapshot
        if (currentSnapshotIndex < 0 || currentSnapshotIndex >= executionHistory.size() - 1) {
            return false;
        }

        // recup info snapshot courant
        ExecutionSnapshot currentSnapshot = executionHistory.get(currentSnapshotIndex);
        // taille stackFrame
        int currentStackDepth = currentSnapshot.getStackFrames().size();
        // get className et MethodName du premier stackFrame
        String currentMethod = currentSnapshot.getStackFrames().get(0).getMethodName();
        String currentClass = currentSnapshot.getStackFrames().get(0).getClassName();

        int currentLine = currentSnapshot.getLineNumber();

        // Parcourir les snapshots suivants pour trouver le prochain arrêt
        for (int i = currentSnapshotIndex + 1; i < executionHistory.size(); i++) {
            ExecutionSnapshot nextSnapshot = executionHistory.get(i);
            int nextStackDepth = nextSnapshot.getStackFrames().size();

            // si profondeur diminue alors sortie
            if (nextStackDepth < currentStackDepth) {
                currentSnapshotIndex = i;
                return true;
            }

            // meme profondeur
            if (nextStackDepth == currentStackDepth) {
                String nextMethod = nextSnapshot.getStackFrames().get(0).getMethodName();
                String nextClass = nextSnapshot.getStackFrames().get(0).getClassName();
                int nextLine = nextSnapshot.getLineNumber();

                if (nextClass.equals(currentClass) && nextMethod.equals(currentMethod) && nextLine != currentLine) {
                    currentSnapshotIndex = i;
                    return true;
                }

                if (!nextClass.equals(currentClass) || !nextMethod.equals(currentMethod)) {
                    currentSnapshotIndex = i;
                    return true;
                }
            }
            // si nextStackDepth > currentStackDepth, on continue
        }

        // retourner dernier snapshot si aucun autre trouvé
        currentSnapshotIndex = executionHistory.size() - 1;
        return true;
    }

    public int getSnapshotCount() {
        return executionHistory.size();
    }

    public boolean setBreakpointAtLine(int lineNumber) {
        int count = 0;
        for (ExecutionSnapshot snapshot : executionHistory) {
            if (snapshot.getLineNumber() == lineNumber) {
                snapshot.setBreakpoint(true);
                count++;
            }
        }
        if (count > 0) {
            return true;
        }
        return false;
    }

    public boolean removeBreakpointAtLine(int lineNumber) {
        for (ExecutionSnapshot snapshot : executionHistory) {
            if (snapshot.getLineNumber() == lineNumber) {
                if (!snapshot.isBreakpoint()) {
                    return false;
                }
                snapshot.setBreakpoint(false);
                return true;
            }
        }
        return false;
    }
}
