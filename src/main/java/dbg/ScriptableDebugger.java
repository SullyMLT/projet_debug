package dbg;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import commands.Command;
import commands.ContinueCommand;
import commands.StepCommand;
import commands.StepOverCommand;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

public class ScriptableDebugger {

    private Class debugClass;
    private VirtualMachine vm;
    private Map<String, Command> commandMap;

    public VirtualMachine connectAndLaunchVM() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-cp " + System.getProperty("java.class.path"));
        VirtualMachine vm = launchingConnector.launch(arguments);
        commandMap = new HashMap<>();
        commandMap.put("step", new StepCommand());
        commandMap.put("step-over", new StepOverCommand());
        commandMap.put("continue", new ContinueCommand());

        return vm;
    }
    public void attachTo(Class debuggeeClass) {

        this.debugClass = debuggeeClass;
        try {
            vm = connectAndLaunchVM();
            enableClassPrepareRequest(vm);
            startDebugger();

            System.out.println("End of program");
            InputStreamReader reader =
                    new InputStreamReader(vm.process().getInputStream());
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startDebugger() throws VMDisconnectedException, InterruptedException, AbsentInformationException {
        EventSet eventSet = null;
        while ((eventSet = vm.eventQueue().remove()) != null) {
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    setBreakPoint(debugClass.getName(), 6);
                    setBreakPoint(debugClass.getName(), 10);
                }
                if(event instanceof VMDisconnectEvent) {
                    System.out.println("====End of program.");
                    return;
                }
                if (event instanceof BreakpointEvent) {
                    askUserCommand(((BreakpointEvent) event).thread());
                }

                if (event instanceof StepEvent) {
                    eventSet.virtualMachine().eventRequestManager().deleteEventRequest(event.request());
                    askUserCommand(((StepEvent) event).thread());
                }
                System.out.println(event.toString());
                vm.resume();
            }
        }
    }
    public void enableClassPrepareRequest (VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    public void setBreakPoint(String className, int lineNumber) throws AbsentInformationException {
        for (ReferenceType targetClass : vm.allClasses()) {
            if (targetClass.name().equals(className)) {
                Location location = targetClass.locationsOfLine(lineNumber).get(0);
                BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                bpReq.enable();
            }
        }
    }

    public void askUserCommand(ThreadReference thread) {
        Scanner scanner = new Scanner(System.in);
        boolean resume = false;

        while (!resume) {
            System.out.print("--> ");
            String input = scanner.nextLine();
            String[] tokens = input.split("\\s+");

            if (tokens.length == 0 || tokens[0].isEmpty()) continue;

            String commandName = tokens[0];
            Command command = commandMap.get(commandName);

            if (command != null) {
                List<String> args = new ArrayList<>();
                for (int i = 1; i < tokens.length; i++) {
                    args.add(tokens[i]);
                }

                try {
                    resume = command.execute(thread, vm, args);
                } catch (Exception e) {
                    System.err.println("Error executing command: " + e.getMessage());
                }
            } else {
                System.err.println("Error : Commande inconnue");
            }
        }
        scanner.close();
    }
}
