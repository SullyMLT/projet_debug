package timetravel.main;

import dbg.JDISimpleDebuggee;
import timetravel.debugger.TimeTravelEngine;
import timetravel.ui.DebuggerTimeTravel;
import timetravel.debugger.ScriptableDebuggerTimeTravel;

import javax.swing.SwingUtilities;

public class JDITimeTravelDebuggerUI {
    public static void main(String[] args) throws Exception {
        TimeTravelEngine timeTravelEngine = new TimeTravelEngine();
        ScriptableDebuggerTimeTravel engine = new ScriptableDebuggerTimeTravel(timeTravelEngine);

        SwingUtilities.invokeLater(() -> {
            DebuggerTimeTravel gui = new DebuggerTimeTravel(engine, timeTravelEngine);
            engine.setGui(gui);
            gui.setVisible(true);

            new Thread(() -> {
                try {
                    engine.attachTo(JDISimpleDebuggee.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }
}
