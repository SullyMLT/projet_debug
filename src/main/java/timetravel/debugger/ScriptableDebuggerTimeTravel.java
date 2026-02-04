package timetravel.debugger;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import commands.*;
import commands.timetravelcommand.*;
import result.ResultCommand;
import timetravel.snapshot.ExecutionSnapshot;
import timetravel.snapshot.VariableModification;
import timetravel.ui.DebuggerTimeTravel;

import java.util.*;
import java.io.*;
import javax.swing.SwingUtilities;

public class ScriptableDebuggerTimeTravel {
    private Class debugClass;
    private VirtualMachine vm;
    private Map<String, Command> commandMap;
    private DebuggerTimeTravel debuggerUI;
    private String pendingCommand = null;
    private TimeTravelEngine timeTravelEngine;

    private boolean isTimeTraveling = false;
    private int targetSnapshotId = -1;


    private boolean isReplayMode = false;
    // true = execute tout le code source en step in
    private boolean autoExecuteAll = true;

    public ScriptableDebuggerTimeTravel(TimeTravelEngine ttEngine) {
        this.timeTravelEngine = ttEngine;
        
        commandMap = new HashMap<>();
        commandMap.put("step", new StepCommand(timeTravelEngine));
        commandMap.put("step-over", new StepOverCommand(timeTravelEngine));
        commandMap.put("continue", new ContinueCommand(timeTravelEngine));

        commandMap.put("follow-variable", new FollowVariableCommand(timeTravelEngine));
        commandMap.put("time-travel", new TimeTravelCommand(timeTravelEngine));
        commandMap.put("show-snapshots", new ShowSnapshotsCommand(timeTravelEngine));
        commandMap.put("back", new BackCommand(timeTravelEngine));
        commandMap.put("forward", new ForwardCommand(timeTravelEngine));
        commandMap.put("breakpoint", new BreakpointCommand(timeTravelEngine));
        commandMap.put("remove-breakpoint", new RemoveBreakpointCommand(timeTravelEngine));
    }

    public void setGui(DebuggerTimeTravel ui) {
        this.debuggerUI = ui;
    }
    
    public TimeTravelEngine getTimeTravelEngine() {
        return timeTravelEngine;
    }

    public void attachTo(Class debuggeeClass) throws Exception {
        this.debugClass = debuggeeClass;
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("main").setValue(debugClass.getName());
        args.get("options").setValue("-cp " + System.getProperty("java.class.path"));

        vm = connector.launch(args);
        redirectOutput();
        enableClassPrepareRequest();
        startDebuggerLoop();
    }

    private void redirectOutput() {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(vm.process().getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    if (debuggerUI != null) {
                        SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole("[OUT] " + finalLine));
                    } else {
                        System.out.println("[OUT] " + finalLine);
                    }
                }
            } catch (IOException e) {
                if (debuggerUI != null) {
                    SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole("--- VM Disconnected ---"));
                } else {
                    System.out.println("--- VM Disconnected ---");
                }
            }
        }).start();
    }

    private void startDebuggerLoop() throws Exception {
        EventSet eventSet;
        int stepCount = 0;
        
        while ((eventSet = vm.eventQueue().remove()) != null) {
            boolean shouldResume = true;
            
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent evt = (ClassPrepareEvent) event;
                    Method mainMethod = evt.referenceType().methodsByName("main").get(0);
                    int firstLine = mainMethod.location().lineNumber();
                    setBreakPoint(debugClass.getName(), firstLine);

                } else if (event instanceof BreakpointEvent) {
                    BreakpointEvent bpEvent = (BreakpointEvent) event;
                    ThreadReference thread = bpEvent.thread();
                    
                    // creer le snapshot (capture)
                    ExecutionSnapshot snapshot = timeTravelEngine.captureSnapshot(thread);
                    if (snapshot != null && debuggerUI != null) {
                        SwingUtilities.invokeLater(() ->
                            debuggerUI.appendToConsole("Snapshot #" + snapshot.getSnapshotId() + " captured"));
                    } else if (snapshot != null) {
                        System.out.println("Snapshot #" + snapshot.getSnapshotId() + " captured");
                    }

                    // auto exec mode step in
                    if (!executeStepIn(thread)){
                        shouldResume = askUserCommand(thread);
                    }
                    
                } else if (event instanceof StepEvent) {
                    StepEvent stepEvent = (StepEvent) event;
                    ThreadReference thread = stepEvent.thread();
                    vm.eventRequestManager().deleteEventRequest(event.request());
                    
                    stepCount++;

                    // creer le snapshot (capture)
                    ExecutionSnapshot snapshot = timeTravelEngine.captureSnapshot(thread);
                    if (snapshot != null && debuggerUI != null) {
                        SwingUtilities.invokeLater(() ->
                            debuggerUI.appendToConsole("Snapshot #" + snapshot.getSnapshotId() + " captured"));
                    } else if (snapshot != null) {
                        System.out.println("Snapshot #" + snapshot.getSnapshotId() + " captured");
                    }
                    
                    // auto exec mode step in
                    if (!executeStepIn(thread)){
                        shouldResume = askUserCommand(thread);
                    }
                    
                } else if (event instanceof VMDisconnectEvent) {
                    if (debuggerUI != null) {
                        SwingUtilities.invokeLater(() -> {
                            debuggerUI.appendToConsole("\n=== Execution Complete ===");
                            debuggerUI.appendToConsole("Total snapshots captured: " + timeTravelEngine.getSnapshotCount());
                            debuggerUI.appendToConsole("\n=== Entering Replay Mode ===");
                            debuggerUI.appendToConsole("Available commands: step, step-over, continue, breakpoint <line>, remove-breakpoint <line> show-snapshots\n" +
                                    "back, forward, follow-variable <varName>, stop, time-travel <snapshotId>");
                        });
                    } else {
                        System.out.println("\n=== Execution Complete ===");
                        System.out.println("Total snapshots captured: " + timeTravelEngine.getSnapshotCount());
                        System.out.println("\n=== Entering Replay Mode ===");
                        System.out.println("Available commands: step, step-over, continue, breakpoint <line>, remove-breakpoint <line> show-snapshots\n" +
                        "back, forward, follow-variable <varName>, stop, time-travel <snapshotId>");
                    }

                    isReplayMode = true;
                    autoExecuteAll = false;

                    enterReplayMode();
                    return;
                }
            }
            
            if (shouldResume) {
                vm.resume();
            }
        }
    }

    // auto execute tout les step in lorsqu'on lance la vm et
    public boolean executeStepIn(ThreadReference thread) {
        if (autoExecuteAll) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(
                    thread,
                    com.sun.jdi.request.StepRequest.STEP_LINE,
                    com.sun.jdi.request.StepRequest.STEP_INTO
            );
            stepRequest.addClassExclusionFilter("java.*");
            stepRequest.addClassExclusionFilter("javax.*");
            stepRequest.addClassExclusionFilter("sun.*");
            stepRequest.addClassExclusionFilter("com.sun.*");
            stepRequest.addClassExclusionFilter("jdk.*");
            stepRequest.addCountFilter(1);
            stepRequest.enable();

            return true;
        }
        return false;
    }

    public synchronized boolean askUserCommand(ThreadReference thread) {
        if (debuggerUI != null) {
            SwingUtilities.invokeLater(() -> debuggerUI.updateAll(thread));
        }

        // Boucle pour redemander une commande tant que shouldResume() retourne false
        while (true) {
            pendingCommand = null;
            String[] parts;

            if (debuggerUI != null) {
                while (pendingCommand == null) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // récupère la commande textuelle de la console
                parts = pendingCommand.split("\\s+");
            } else {
                // console mode
                Scanner scanner = new Scanner(System.in);
                System.out.print("--> ");
                String input = scanner.nextLine().trim();
                parts = input.split("\\s+");
            }

            String cmdName = parts[0];
            List<String> args = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                args.add(parts[i]);
            }

            Command cmd = commandMap.get(cmdName);
            if (cmd != null) {
                try {
                    ResultCommand result = cmd.execute(thread, vm, args);
                    if (result != null) {
                        if (debuggerUI != null) {
                            SwingUtilities.invokeLater(() ->
                                debuggerUI.appendToConsole("[CMD] " + result.toString()));
                        } else {
                            System.out.println("[CMD] " + result.toString());
                        }

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                String errorMsg = "[ERROR] Unknown command: " + cmdName;
                if (debuggerUI != null) {
                    SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole(errorMsg));
                } else {
                    System.out.println(errorMsg);
                }
            }
            // redemander une commande
        }
    }

    public synchronized void handleGuiInput(String commandName) {
        this.pendingCommand = commandName;
        this.notifyAll();
    }

    public void setBreakPoint(String className, int line) throws Exception {
        for (ReferenceType ref : vm.allClasses()) {
            if (ref.name().equals(className)) {
                List<Location> locs = ref.locationsOfLine(line);
                if (!locs.isEmpty()) {
                    vm.eventRequestManager().createBreakpointRequest(locs.get(0)).enable();
                }
            }
        }
    }

    public void removeBreakPoint(String className, int line) {
        List<com.sun.jdi.request.BreakpointRequest> requests = vm.eventRequestManager().breakpointRequests();
        for (com.sun.jdi.request.BreakpointRequest req : requests) {
            if (req.location().declaringType().name().equals(className)
                    && req.location().lineNumber() == line) {
                req.disable();
                vm.eventRequestManager().deleteEventRequest(req);
            }
        }
    }

    public String getSourceCode(String className) {
        String relativePath = "src/main/java/" + className.replace('.', '/') + ".java";
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get(relativePath));
        } catch (Exception e) { 
            return "Source not found: " + relativePath; 
        }
    }

    private void enableClassPrepareRequest() {
        ClassPrepareRequest cpr = vm.eventRequestManager().createClassPrepareRequest();
        cpr.addClassFilter(debugClass.getName());
        cpr.enable();
    }

    private void enterReplayMode() {
        if (timeTravelEngine.getSnapshotCount() > 0) {
            timeTravelEngine.travelToSnapshot(0);
            ExecutionSnapshot firstSnapshot = timeTravelEngine.getCurrentSnapshot();

            if (debuggerUI != null) {
                SwingUtilities.invokeLater(() -> {
                    debuggerUI.appendToConsole("\nCurrent snapshot:");
                    debuggerUI.appendToConsole(firstSnapshot.toString());

                    // charge le premier snapshot dans l'ui
                    debuggerUI.updateUIFromSnapshot(firstSnapshot);
                });
            } else {
                System.out.println("\nCurrent snapshot:");
                System.out.println(firstSnapshot.toString());
            }
        }

        while (isReplayMode) {
            pendingCommand = null;
            String[] parts;

            if (debuggerUI != null) {
                synchronized (this) {
                    while (pendingCommand == null) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // close l'UI
                if (pendingCommand.equalsIgnoreCase("stop")) {
                    System.exit(0);
                    return;
                }
                parts = pendingCommand.split("\\s+");
            } else {
                // console mode
                Scanner scanner = new Scanner(System.in);
                System.out.print("--> ");
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("stop")) {
                    break;
                }
                parts = input.split("\\s+");
            }

            String cmdName = parts[0];
            List<String> args = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                args.add(parts[i]);
            }

            Command cmd = commandMap.get(cmdName);
            if (cmd != null) {
                try {
                    // thread et vm a null car vm close (mode replay)
                    ResultCommand result = cmd.execute(null, null, args);
                    if (result != null) {
                        if (debuggerUI != null) {
                            SwingUtilities.invokeLater(() ->
                                debuggerUI.appendToConsole("[OUT] " + result.toString()));
                        } else {
                            System.out.println("[OUT] " + result.toString());
                        }

                        // gère le follow-variable
                        if (cmdName.equals("follow-variable") && result.isSucces() && debuggerUI != null) {
                            @SuppressWarnings("unchecked")
                            List<VariableModification> modifications = (List<VariableModification>) result.getDonnee();
                            debuggerUI.updateVariableTracking(modifications);
                        }

                        // gère breakpoint/remove-breakpoint, met a jour l'UI pour les breakpoints
                        if (cmdName.equals("breakpoint") && result.isSucces() && debuggerUI != null) {
                            int line = (int) result.getDonnee();
                            debuggerUI.addBreakpointVisual(line);
                        }
                        if (cmdName.equals("remove-breakpoint") && result.isSucces() && debuggerUI != null) {
                            int line = (int) result.getDonnee();
                            debuggerUI.removeBreakpointVisual(line);
                        }

                        // gère step/step-over/continue/back/forward/time-travel : met à jour l'UI avec le snapshot courant
                        if ((cmdName.equals("step") || cmdName.equals("step-over") || cmdName.equals("continue")
                            || cmdName.equals("back") || cmdName.equals("forward") || cmdName.equals("time-travel"))
                            && result.isSucces() && debuggerUI != null) {
                            ExecutionSnapshot snapshot = timeTravelEngine.getCurrentSnapshot();
                            if (snapshot != null) {
                                debuggerUI.updateUIFromSnapshot(snapshot);
                            }
                        }
                    }
                } catch (Exception e) {
                    String errorMsg = "[ERROR] " + e.getMessage();
                    if (debuggerUI != null) {
                        SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole(errorMsg));
                    } else {
                        System.out.println(errorMsg);
                    }
                }
            } else {
                String errorMsg = "[ERROR] Unknown command: " + cmdName;
                if (debuggerUI != null) {
                    SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole(errorMsg));
                } else {
                    System.out.println(errorMsg);
                }
            }
        }
    }
}
