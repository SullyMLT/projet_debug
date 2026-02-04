package timetravel.ui;

import com.sun.jdi.*;
import commands.timetravelcommand.FollowVariableCommand;
import result.ResultCommand;
import timetravel.debugger.TimeTravelEngine;
import timetravel.debugger.ScriptableDebuggerTimeTravel;
import timetravel.snapshot.ExecutionSnapshot;
import timetravel.snapshot.StackFrameSnapshot;
import timetravel.snapshot.VariableModification;
import timetravel.snapshot.VariableSnapshot;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

public class DebuggerTimeTravel extends JFrame {
    private ScriptableDebuggerTimeTravel debugger;
    private TimeTravelEngine timeTravelEngine;

    private JTextPane sourcePane;
    private JList<String> stackList;
    private DefaultListModel<String> stackModel = new DefaultListModel<>();
    private JTree inspectorTree;
    private JTextArea consoleArea;
    private LineNumberGutter gutter;

    private String currentClassName;
    private Set<Integer> breakpoints = new HashSet<>();
    private ThreadReference currentThread;
    
    // Snapshot courant pour le mode replay
    private ExecutionSnapshot currentSnapshot;

    // Label pour afficher le snapshot actuel
    private JLabel snapshotLabel;

    // Panneaux Time-Travel
    private JTable variableTrackingTable;
    private DefaultTableModel variableTrackingModel;
    private JTable methodCallsTable;
    private DefaultTableModel methodCallsModel;
    private JTextField followVariableField;
    private JTextField findMethodField;
    private LocalVariable currentTrackedVariable;

    public DebuggerTimeTravel(ScriptableDebuggerTimeTravel debugger, TimeTravelEngine ttEngine) {
        this.debugger = debugger;
        this.timeTravelEngine = ttEngine;
        setTitle("Java Time-Travel Debugger - UI");
        setSize(1400, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // Toolbar
        JPanel toolbar = new JPanel();
        String[] actions = {"step", "step-over", "continue"};
        for (String act : actions) {
            JButton btn = new JButton(act.toUpperCase());
            btn.addActionListener(e -> debugger.handleGuiInput(act));
            toolbar.add(btn);
        }

        toolbar.add(new JToolBar.Separator());

        JButton backBtn = new JButton("<- BACK");
        backBtn.setBackground(new Color(100, 150, 255));
        backBtn.setForeground(Color.WHITE);
        backBtn.setToolTipText("Go back to previous snapshot");
        backBtn.addActionListener(e -> debugger.handleGuiInput("back"));
        toolbar.add(backBtn);

        toolbar.add(new JToolBar.Separator());

        JButton stopBtn = new JButton("STOP");
        stopBtn.setBackground(new Color(255, 80, 80));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.addActionListener(e -> System.exit(0));
        toolbar.add(stopBtn);
        add(toolbar, BorderLayout.NORTH);

        // Source pane avec gutter
        sourcePane = new JTextPane();
        sourcePane.setEditable(false);
        sourcePane.setFont(new Font("Monospaced", Font.PLAIN, 13));
        sourcePane.setMargin(new Insets(0, 5, 0, 0));

        JScrollPane sourceScroll = new JScrollPane(sourcePane);
        gutter = new LineNumberGutter(sourcePane);
        sourceScroll.setRowHeaderView(gutter);
        sourceScroll.getViewport().addChangeListener(e -> gutter.repaint());

        add(sourceScroll, BorderLayout.CENTER);

        // Panneau gauche
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Call stack
        stackList = new JList<>(stackModel);
        stackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateContextToSelectedFrame();
            }
        });
        JScrollPane stackScroll = new JScrollPane(stackList);
        stackScroll.setBorder(BorderFactory.createTitledBorder("CALL STACK"));

        // Variable Tracking
        JPanel trackingPanel = createVariableTrackingPanel();
        trackingPanel.setBorder(BorderFactory.createTitledBorder("VARIABLE TRACKING"));

        // Diviser verticalement
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stackScroll, trackingPanel);
        leftSplitPane.setResizeWeight(0.5);
        leftSplitPane.setDividerLocation(300);
        leftPanel.add(leftSplitPane, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);

        // Inspector tree varable tracker avec double-clic pour tracker une variable
        inspectorTree = new JTree(new DefaultMutableTreeNode("Variables"));
        inspectorTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = inspectorTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        String nodeText = node.toString();
                        String varName = extractVariableName(nodeText);
                        if (varName != null && !varName.isEmpty()) {
                            // appel de  la commande follow-variable via handleGuiInput
                            debugger.handleGuiInput("follow-variable " + varName);
                        }
                    }
                }
            }
        });
        JScrollPane treeScroll = new JScrollPane(inspectorTree);
        treeScroll.setPreferredSize(new Dimension(300, 0));
        treeScroll.setBorder(BorderFactory.createTitledBorder("INSPECTOR (double-click to track)"));
        add(treeScroll, BorderLayout.EAST);

        // Console en bas
        JPanel consolePanel = new JPanel(new BorderLayout());
        consoleArea = new JTextArea(3, 0);
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(20, 20, 20));
        consoleArea.setForeground(Color.GREEN);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setPreferredSize(new Dimension(0, 120));
        consolePanel.add(consoleScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JLabel promptLabel = new JLabel("> ");
        promptLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        JTextField commandInput = new JTextField();
        commandInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        commandInput.addActionListener(e -> {
            String cmd = commandInput.getText().trim();
            if (!cmd.isEmpty()) {
                appendToConsole("> " + cmd);
                debugger.handleGuiInput(cmd);
                commandInput.setText("");
            }
        });
        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(commandInput, BorderLayout.CENTER);
        consolePanel.add(inputPanel, BorderLayout.SOUTH);
        consolePanel.setBorder(BorderFactory.createTitledBorder("CONSOLE"));

        add(consolePanel, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private String extractVariableName(String nodeText) {
        if (nodeText == null || nodeText.isEmpty()) return null;
        int spaceIdx = nodeText.indexOf(' ');
        int parenIdx = nodeText.indexOf('(');
        int eqIdx = nodeText.indexOf('=');

        int endIdx = nodeText.length();
        if (spaceIdx > 0) endIdx = Math.min(endIdx, spaceIdx);
        if (parenIdx > 0) endIdx = Math.min(endIdx, parenIdx);
        if (eqIdx > 0) endIdx = Math.min(endIdx, eqIdx);

        String varName = nodeText.substring(0, endIdx).trim();
        if (varName.equals("Variables") || varName.equals("Local") || varName.equals("Receiver")
            || varName.startsWith("[") || varName.equals("this") || varName.equals("No")) {
            return null;
        }
        return varName;
    }

    private JPanel createVariableTrackingPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        String[] columnNames = {"Modification", "Value", "Line"};
        variableTrackingModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        variableTrackingTable = new JTable(variableTrackingModel);

        variableTrackingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = variableTrackingTable.getSelectedRow();
                    if (row != -1) {
                        List<ExecutionSnapshot> execSnapshots = timeTravelEngine.getAllSnapshots();
                        for (ExecutionSnapshot snap : execSnapshots) {
                            if (snap.getLineNumber() == (int) variableTrackingModel.getValueAt(row, 2)) {
                                // trouve le snapshot correspondant
                                debugger.handleGuiInput("time-travel " + snap.getSnapshotId());
                                return;
                            }
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(variableTrackingTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel hint = new JLabel("Double-click to time-travel");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 10f));
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    public void updateUIFromSnapshot(ExecutionSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            try {
                // recupere le snapshot courant
                this.currentSnapshot = snapshot;
                this.currentThread = null;

                // deplace le surlignage et met a jour le source pane
                String className = snapshot.getLocation().declaringType().name();
                int lineNumber = snapshot.getLineNumber();

                if (!className.equals(currentClassName)) {
                    currentClassName = className;
                    sourcePane.setText(debugger.getSourceCode(className));
                }

                highlightLine(lineNumber);
                gutter.repaint();

                sourcePane.revalidate();
                sourcePane.repaint();

                stackModel.clear();
                // ajoute dans la liste de call stack
                for (StackFrameSnapshot frame : snapshot.getStackFrames()) {
                    stackModel.addElement(frame.toString());
                }

                if (!stackModel.isEmpty()) {
                    stackList.setSelectedIndex(0);
                }

                stackList.revalidate();
                stackList.repaint();

                // met a jour l'inspector avec les variable du snapshot
                updateInspectorFromSnapshot(snapshot);

            } catch (Exception e) {
                appendToConsole("[ERROR] Failed to update UI from snapshot: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // update inspector tree from snapshot
    private void updateInspectorFromSnapshot(ExecutionSnapshot snapshot) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");

        try {
            Map<String, VariableSnapshot> localVars = snapshot.getLocalVariables();
            for (Map.Entry<String, VariableSnapshot> entry : localVars.entrySet()) {
                VariableSnapshot varSnap = entry.getValue();
                DefaultMutableTreeNode varNode = createVariableNode(varSnap);
                root.add(varNode);
            }

            if (localVars.isEmpty()) {
                root.add(new DefaultMutableTreeNode("No variables in this snapshot"));
            }

        } catch (Exception e) {
            root.add(new DefaultMutableTreeNode("Error reading snapshot: " + e.getMessage()));
        }

        inspectorTree.setModel(new DefaultTreeModel(root));
        expandAllNodes(inspectorTree);
        inspectorTree.revalidate();
        inspectorTree.repaint();
    }

    private DefaultMutableTreeNode createVariableNode(VariableSnapshot varSnap) {
        String display = String.format("%s (%s) = %s",
            varSnap.getName(),
            varSnap.getTypeName(),
            varSnap.getValueString());
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(display);

        for (VariableSnapshot child : varSnap.getChildren()) {
            node.add(createVariableNode(child));
        }

        return node;
    }

    private void expandAllNodes(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    public void updateVariableTracking(List<VariableModification> modifications) {
        SwingUtilities.invokeLater(() -> {
            variableTrackingModel.setRowCount(0);
            String currentSnapshotValue = "";
            int modificationCount = 1;
            for (VariableModification mod : modifications) {
                if (!currentSnapshotValue.equals(mod.getValue())){
                    currentSnapshotValue = mod.getValue();
                    variableTrackingModel.addRow(new Object[]{
                            modificationCount,
                            mod.getValue() == null ? "null" : mod.getValue(),
                            mod.getSnapshot().getLineNumber()
                    });
                    modificationCount++;
                }
            }
            variableTrackingTable.revalidate();
            variableTrackingTable.repaint();
        });
    }

    public void appendToConsole(String text) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(text + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    public void addBreakpointVisual(int line) {
        SwingUtilities.invokeLater(() -> {
            breakpoints.add(line);
            gutter.repaint();
        });
    }

    public void removeBreakpointVisual(int line) {
        SwingUtilities.invokeLater(() -> {
            breakpoints.remove(line);
            gutter.repaint();
        });
    }

    public void updateAll(ThreadReference thread) {
        SwingUtilities.invokeLater(() -> {
            try {
                this.currentThread = thread;
                this.currentSnapshot = null;
                stackModel.clear();

                for (StackFrame f : thread.frames()) {
                    Location l = f.location();
                    stackModel.addElement(l.declaringType().name() + "." + l.method().name() + "():" + l.lineNumber());
                }

                if (!stackModel.isEmpty()) {
                    stackList.setSelectedIndex(0);
                }

                stackList.revalidate();
                stackList.repaint();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateContextToSelectedFrame() {
        int index = stackList.getSelectedIndex();
        if (index == -1) return;

        if (currentSnapshot != null) {
            List<StackFrameSnapshot> frames = currentSnapshot.getStackFrames();
            if (index >= 0 && index < frames.size()) {
                StackFrameSnapshot frame = frames.get(index);

                if (!frame.getClassName().equals(currentClassName)) {
                    currentClassName = frame.getClassName();
                    sourcePane.setText(debugger.getSourceCode(currentClassName));
                }

                highlightLine(frame.getLineNumber());
                gutter.repaint();
                sourcePane.revalidate();
                sourcePane.repaint();

                updateInspectorFromStackFrame(frame);
            }
            return;
        }

        if (currentThread != null) {
            try {
                StackFrame frame = currentThread.frame(index);
                Location loc = frame.location();

                this.currentClassName = loc.declaringType().name();
                sourcePane.setText(debugger.getSourceCode(currentClassName));
                highlightLine(loc.lineNumber());
                gutter.repaint();
                sourcePane.revalidate();
                sourcePane.repaint();

                updateTree(frame);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateInspectorFromStackFrame(StackFrameSnapshot frame) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");

        try {
            Map<String, VariableSnapshot> localVars = frame.getLocalVariables();
            for (Map.Entry<String, VariableSnapshot> entry : localVars.entrySet()) {
                VariableSnapshot varSnap = entry.getValue();
                DefaultMutableTreeNode varNode = createVariableNode(varSnap);
                root.add(varNode);
            }

            if (localVars.isEmpty()) {
                root.add(new DefaultMutableTreeNode("No variables in this frame"));
            }

        } catch (Exception e) {
            root.add(new DefaultMutableTreeNode("Error reading frame: " + e.getMessage()));
        }

        inspectorTree.setModel(new DefaultTreeModel(root));
        expandAllNodes(inspectorTree);
        inspectorTree.revalidate();
        inspectorTree.repaint();
    }

    private void highlightLine(int line) {
        StyledDocument doc = sourcePane.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), new SimpleAttributeSet(), true);
        Element root = doc.getDefaultRootElement();
        if (line > 0 && line <= root.getElementCount()) {
            Element lineElem = root.getElement(line - 1);
            SimpleAttributeSet sas = new SimpleAttributeSet();
            StyleConstants.setBackground(sas, Color.YELLOW);
            doc.setCharacterAttributes(lineElem.getStartOffset(), lineElem.getEndOffset() - lineElem.getStartOffset(), sas, false);
            sourcePane.setCaretPosition(lineElem.getStartOffset());
        }
    }

    private void updateTree(StackFrame frame) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");
        try {
            Set<ObjectReference> visited = new HashSet<>();

            for (LocalVariable v : frame.visibleVariables()) {
                Value value = frame.getValue(v);
                addNode(root, v.name(), value, visited);
            }

            ObjectReference thisObject = frame.thisObject();
            if (thisObject != null) {
                addNode(root, "this", thisObject, visited);
            }
        } catch (Exception e) {
            root.add(new DefaultMutableTreeNode("Erreur / Pas de variables"));
        }
        inspectorTree.setModel(new DefaultTreeModel(root));
        expandAllNodes(inspectorTree);
        inspectorTree.revalidate();
        inspectorTree.repaint();
    }

    private void addNode(DefaultMutableTreeNode parent, String name, Value value, Set<ObjectReference> visited) {
        if (value == null) {
            parent.add(new DefaultMutableTreeNode(name + " = null"));
            return;
        }

        if (value instanceof ArrayReference) {
            ArrayReference arrayRef = (ArrayReference) value;
            DefaultMutableTreeNode arrayNode = new DefaultMutableTreeNode(name + " (Array[" + arrayRef.length() + "])");
            parent.add(arrayNode);

            if (visited.contains(arrayRef)) {
                arrayNode.add(new DefaultMutableTreeNode("[Already displayed]"));
                return;
            }
            visited.add(arrayRef);

            try {
                List<Value> values = arrayRef.getValues();
                for (int i = 0; i < Math.min(values.size(), 10); i++) {
                    addNode(arrayNode, "[" + i + "]", values.get(i), new HashSet<>(visited));
                }
                if (values.size() > 10) {
                    arrayNode.add(new DefaultMutableTreeNode("... (" + (values.size() - 10) + " more)"));
                }
            } catch (Exception e) {
                arrayNode.add(new DefaultMutableTreeNode("Error reading array"));
            }
        } else if (value instanceof StringReference) {
            parent.add(new DefaultMutableTreeNode(name + " = \"" + ((StringReference) value).value() + "\""));
        } else if (value instanceof ObjectReference) {
            ObjectReference objRef = (ObjectReference) value;
            ReferenceType refType = objRef.referenceType();

            DefaultMutableTreeNode objectNode = new DefaultMutableTreeNode(name + " (" + refType.name() + ")");
            parent.add(objectNode);

            if (visited.contains(objRef)) {
                objectNode.add(new DefaultMutableTreeNode("[Cycle detected]"));
                return;
            }
            visited.add(objRef);

            try {
                for (Field field : refType.allFields()) {
                    Value fieldValue = objRef.getValue(field);
                    addNode(objectNode, field.name(), fieldValue, new HashSet<>(visited));
                }
            } catch (Exception e) {
                objectNode.add(new DefaultMutableTreeNode("Error reading fields"));
            }
        } else {
            parent.add(new DefaultMutableTreeNode(name + " = " + value));
        }
    }

    private class LineNumberGutter extends JPanel {
        private final JTextPane textPane;

        public LineNumberGutter(JTextPane pane) {
            this.textPane = pane;
            setBackground(new Color(230, 230, 230));
            setPreferredSize(new Dimension(50, 0));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleBreakpoint(e.getPoint());
                }
            });
        }

        private void toggleBreakpoint(Point p) {
            Rectangle visibleRect = textPane.getVisibleRect();
            Point textPanePoint = new Point(10, p.y + visibleRect.y);
            int offset = textPane.viewToModel2D(textPanePoint);
            if (offset < 0) return;
            int line = textPane.getDocument().getDefaultRootElement().getElementIndex(offset) + 1;

            if (breakpoints.contains(line)) {
                breakpoints.remove(line);
                debugger.handleGuiInput("remove-breakpoint " + line);
            } else {
                breakpoints.add(line);
                debugger.handleGuiInput("breakpoint " + line);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setFont(textPane.getFont());
            FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());
            Element root = textPane.getDocument().getDefaultRootElement();
            int lineCount = root.getElementCount();

            Rectangle visibleRect = textPane.getVisibleRect();

            for (int i = 0; i < lineCount; i++) {
                try {
                    int startOffset = root.getElement(i).getStartOffset();
                    Rectangle2D rect = textPane.modelToView2D(startOffset);

                    if (rect != null) {
                        // calcule y relatif
                        int y = (int) rect.getY() - visibleRect.y;
                        int height = (int) rect.getHeight();
                        int lineNum = i + 1;

                        // Ne dessiner que si la ligne est visible
                        if (y + height >= 0 && y < getHeight()) {
                            // dessiner le breakpoint
                            if (breakpoints.contains(lineNum)) {
                                g2.setColor(Color.RED);
                                g2.fillOval(5, y + (height - 10) / 2, 10, 10);
                            }

                            // num ligne
                            g2.setColor(Color.GRAY);
                            String txt = String.valueOf(lineNum);
                            int x = getWidth() - metrics.stringWidth(txt) - 5;
                            g2.drawString(txt, x, y + metrics.getAscent());
                        }
                    }
                } catch (BadLocationException e) {
                    // Ignorer les erreurs de position
                }
            }
        }
    }
}
