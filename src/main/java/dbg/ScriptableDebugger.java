package dbg;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import commands.*;
import result.ResultCommand;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

public class ScriptableDebugger {

    private Class debugClass;
    private VirtualMachine vm;
    private Map<String, Command> commandMap;
    private Scanner scanner;

    public VirtualMachine connectAndLaunchVM() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-cp " + System.getProperty("java.class.path"));
        VirtualMachine vm = launchingConnector.launch(arguments);

        scanner = new Scanner(System.in);

        commandMap = new HashMap<>();
        commandMap.put("step", new StepCommand());
        commandMap.put("step-over", new StepOverCommand());
        commandMap.put("continue", new ContinueCommand());
        commandMap.put("frame", new FrameCommand());
        commandMap.put("temporaries", new TemporariesCommand());
        commandMap.put("stack", new StackCommand());
        commandMap.put("receiver", new ReceiverCommand());
        commandMap.put("sender", new SenderCommand());
        commandMap.put("receiver-variables", new ReceiverVariablesCommand());
        commandMap.put("method", new MethodCommand());
        commandMap.put("arguments", new ArgumentsCommand());
        commandMap.put("print-var", new PrintVarCommand());
        commandMap.put("break", new BreakCommand());
        commandMap.put("breakpoints", new BreakpointsCommand());
        commandMap.put("break-once", new BreakOnceCommand());
        commandMap.put("break-on-count", new BreakOnCountCommand());
        commandMap.put("break-before-method-call", new BreakBeforeMethodCallCommand());

        return vm;
    }

    public void attachTo(Class debuggeeClass) {
        this.debugClass = debuggeeClass;
        try {
            vm = connectAndLaunchVM();
            enableClassPrepareRequest(vm);
            startDebugger();

            System.out.println("\n====End of program====");
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            try {
                reader.transferTo(writer);
                writer.flush();
            } catch (IOException e) {
                System.out.println("Target VM inputstream reading error.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace();
        } catch (VMStartException e) {
            e.printStackTrace();
            System.out.println(e.toString());
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected: " + e.toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    public void startDebugger() throws VMDisconnectedException, InterruptedException, AbsentInformationException {
        EventSet eventSet = null;
        boolean shouldResume = true;

        while ((eventSet = vm.eventQueue().remove()) != null) {
            shouldResume = true;

            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
                    System.out.println("Class prepared: " + classPrepEvent.referenceType().name());
                    setBreakPoint(debugClass.getName(), 6);
                    setBreakPoint(debugClass.getName(), 10);
                }

                if (event instanceof VMDisconnectEvent) {
                    System.out.println("====VM Disconnected====");
                    return;
                }

                if (event instanceof BreakpointEvent) {
                    BreakpointEvent bpEvent = (BreakpointEvent) event;
                    System.out.println("\nBreakpoint hit at " + bpEvent.location());
                    shouldResume = askUserCommand(bpEvent.thread());
                }

                if (event instanceof StepEvent) {
                    StepEvent stepEvent = (StepEvent) event;
                    vm.eventRequestManager().deleteEventRequest(event.request());
                    System.out.println("\nStep at " + stepEvent.location());
                    shouldResume = askUserCommand(stepEvent.thread());
                }

                if (event instanceof MethodEntryEvent) {
                    MethodEntryEvent methodEvent = (MethodEntryEvent) event;
                    System.out.println("\nMethod entry: " + methodEvent.method().name() +
                            " at " + methodEvent.location());
                    shouldResume = askUserCommand(methodEvent.thread());
                }
            }

            if (shouldResume) {
                vm.resume();
            }
        }
    }

    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    public void setBreakPoint(String className, int lineNumber) throws AbsentInformationException {
        for (ReferenceType targetClass : vm.allClasses()) {
            if (targetClass.name().equals(className)) {
                List<Location> locations = targetClass.locationsOfLine(lineNumber);
                if (!locations.isEmpty()) {
                    Location location = locations.get(0);
                    com.sun.jdi.request.BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                    bpReq.enable();
                    System.out.println("Initial breakpoint set at " + className + ":" + lineNumber);
                }
            }
        }
    }

    public boolean askUserCommand(ThreadReference thread) {
        boolean resume = false;

        while (!resume) {
            try {
                System.out.print("--> ");

                if (!scanner.hasNextLine()) {
                    return true;
                }

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                String[] tokens = input.split("\\s+");
                String commandName = tokens[0];
                Command command = commandMap.get(commandName);

                if (command != null) {
                    List<String> args = new ArrayList<>();
                    for (int i = 1; i < tokens.length; i++) {
                        args.add(tokens[i]);
                    }

                    try {
                        ResultCommand result = command.execute(thread, vm, args);
                        if (result != null) {
                            result.display();
                            if (!result.isSucces()) {
                                System.err.println("echec command");
                            }
                        }
                        resume = command.shouldResume();
                    } catch (Exception e) {
                        System.err.println("Error executing : " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("Error: command '" + commandName + "'");
                    System.err.println("Commands disponible : step, step-over, continue, frame, temporaries, stack, receiver, sender, receiver-variables, method, arguments, print-var, break, breakpoints, break-once, break-on-count, break-before-method-call");
                }
            } catch (Exception e) {
                System.err.println("Error reading : " + e.getMessage());
                return true;
            }
        }

        return true;
    }
}