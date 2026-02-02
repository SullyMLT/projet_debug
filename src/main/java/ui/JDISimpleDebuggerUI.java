package ui;

import dbg.JDISimpleDebuggee;
import javax.swing.SwingUtilities;

public class JDISimpleDebuggerUI {
    public static void main(String[] args) {
        ScriptableDebuggerUI engine = new ScriptableDebuggerUI();
        SwingUtilities.invokeLater(() -> {
            DebuggerUI gui = new DebuggerUI(engine);
            engine.setGui(gui);
            gui.setVisible(true);

            new Thread(() -> {
                try {
                    engine.attachTo(JDISimpleDebuggee.class);
                }
                catch (Exception e) { e.printStackTrace(); }
            }).start();
        });
    }
}