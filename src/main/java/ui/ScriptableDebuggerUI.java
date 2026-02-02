package ui;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import commands.*;
import result.ResultCommand;
import java.util.*;
import java.io.*;
import javax.swing.SwingUtilities;

public class ScriptableDebuggerUI {
    private Class debugClass;
    private VirtualMachine vm;
    private Map<String, Command> commandMap;
    private DebuggerUI debuggerUI;
    private String pendingCommand = null;

    public ScriptableDebuggerUI() {
        commandMap = new HashMap<>();
        commandMap.put("step", new StepCommand());
        commandMap.put("step-over", new StepOverCommand());
        commandMap.put("continue", new ContinueCommand());
        commandMap.put("stack", new StackCommand());
        commandMap.put("temporaries", new TemporariesCommand());
    }

    public void setGui(DebuggerUI ui) { this.debuggerUI = ui; }

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
                    SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole("[OUT] " + finalLine));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole("--- VM Disconnected ---"));
            }
        }).start();
    }

    private void startDebuggerLoop() throws Exception {
        EventSet eventSet;
        while ((eventSet = vm.eventQueue().remove()) != null) {
            boolean shouldResume = true;
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent evt = (ClassPrepareEvent) event;
                    Method mainMethod = evt.referenceType().methodsByName("main").get(0);
                    int firstLine = mainMethod.location().lineNumber();
                    setBreakPoint(debugClass.getName(), firstLine);

                } else if (event instanceof BreakpointEvent || event instanceof StepEvent) {
                    ThreadReference thread = (event instanceof BreakpointEvent) ?
                            ((BreakpointEvent)event).thread() : ((StepEvent)event).thread();
                    shouldResume = askUserCommand(thread);
                } else if (event instanceof VMDisconnectEvent) {
                    return;
                }
            }
            if (shouldResume) {
                vm.resume();
            }
        }
    }

    public synchronized boolean askUserCommand(ThreadReference thread) {
        SwingUtilities.invokeLater(() -> debuggerUI.updateAll(thread));
        pendingCommand = null;
        while (pendingCommand == null) {
            try { wait(); } catch (InterruptedException e) { return true; }
        }
        Command cmd = commandMap.get(pendingCommand);
        if (cmd != null) {
            try {
                ResultCommand result = cmd.execute(thread, vm, new ArrayList<>());
                if (result != null) SwingUtilities.invokeLater(() -> debuggerUI.appendToConsole("[CMD] " + result.toString()));
                return cmd.shouldResume();
            } catch (Exception e) { e.printStackTrace(); }
        }
        return true;
    }

    public synchronized void handleGuiInput(String commandName) {
        this.pendingCommand = commandName;
        this.notifyAll();
    }

    // ajoute un Breakpoint
    public void setBreakPoint(String className, int line) throws Exception {
        for (ReferenceType ref : vm.allClasses()) {
            if (ref.name().equals(className)) {
                List<Location> locs = ref.locationsOfLine(line);
                if (!locs.isEmpty()) vm.eventRequestManager().createBreakpointRequest(locs.get(0)).enable();
            }
        }
    }

    // supprime le Breakpoint
    public void removeBreakPoint(String className, int line) {
        List<BreakpointRequest> requests = vm.eventRequestManager().breakpointRequests();
        for (BreakpointRequest req : requests) {
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
        } catch (Exception e) { return "Source non trouvée : " + relativePath; }
    }

    private void enableClassPrepareRequest() {
        ClassPrepareRequest cpr = vm.eventRequestManager().createClassPrepareRequest();
        cpr.addClassFilter(debugClass.getName());
        cpr.enable();
    }
}