package timetravel.main;

import dbg.JDISimpleDebuggee;
import timetravel.debugger.TimeTravelEngine;
import timetravel.debugger.ScriptableDebuggerTimeTravel;

public class JDITimeTravelDebugger {
    public static void main(String[] args) throws Exception {
        TimeTravelEngine timeTravelEngine = new TimeTravelEngine();
        ScriptableDebuggerTimeTravel engine = new ScriptableDebuggerTimeTravel(timeTravelEngine);
        engine.attachTo(JDISimpleDebuggee.class);
    }
}
