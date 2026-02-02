package ui;

import com.sun.jdi.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class DebuggerUI extends JFrame {
    private ScriptableDebuggerUI debugger;

    private JTextPane sourcePane;
    private JList<String> stackList;
    private DefaultListModel<String> stackModel = new DefaultListModel<>();
    private JTree inspectorTree;
    private JTextArea consoleArea;
    private LineNumberGutter gutter;

    private String currentClassName;
    private Set<Integer> breakpoints = new HashSet<>();
    private ThreadReference currentThread;

    public DebuggerUI(ScriptableDebuggerUI debugger) {
        this.debugger = debugger;
        setTitle("Java Debugger - IHM");
        setSize(1200, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        JPanel toolbar = new JPanel();
        String[] actions = {"step", "step-over", "continue"};
        for (String act : actions) {
            JButton btn = new JButton(act.toUpperCase());
            btn.addActionListener(e -> debugger.handleGuiInput(act));
            toolbar.add(btn);
        }
        JButton stopBtn = new JButton("STOP");
        stopBtn.setBackground(new Color(255, 80, 80));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.addActionListener(e -> System.exit(0));
        toolbar.add(stopBtn);
        add(toolbar, BorderLayout.NORTH);

        sourcePane = new JTextPane();
        sourcePane.setEditable(false);
        sourcePane.setFont(new Font("Monospaced", Font.PLAIN, 13));
        sourcePane.setMargin(new Insets(0, 5, 0, 0));

        JScrollPane sourceScroll = new JScrollPane(sourcePane);
        gutter = new LineNumberGutter(sourcePane);
        sourceScroll.setRowHeaderView(gutter);
        add(sourceScroll, BorderLayout.CENTER);

        stackList = new JList<>(stackModel);
        stackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateContextToSelectedFrame();
            }
        });

        JScrollPane stackScroll = new JScrollPane(stackList);
        stackScroll.setPreferredSize(new Dimension(300, 0));
        stackScroll.setBorder(BorderFactory.createTitledBorder("CALL STACK"));
        add(stackScroll, BorderLayout.WEST);

        inspectorTree = new JTree(new DefaultMutableTreeNode("Variables"));
        JScrollPane treeScroll = new JScrollPane(inspectorTree);
        treeScroll.setPreferredSize(new Dimension(300, 0));
        treeScroll.setBorder(BorderFactory.createTitledBorder("INSPECTOR"));
        add(treeScroll, BorderLayout.EAST);

        consoleArea = new JTextArea(12, 0);
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(20, 20, 20));
        consoleArea.setForeground(Color.GREEN);
        add(new JScrollPane(consoleArea), BorderLayout.SOUTH);
    }

    public void appendToConsole(String text) {
        SwingUtilities.invokeLater(() -> consoleArea.append(text + "\n"));
    }

    public void updateAll(ThreadReference thread) {
        SwingUtilities.invokeLater(() -> {
            try {
                this.currentThread = thread;
                stackModel.clear();

                for (StackFrame f : thread.frames()) {
                    Location l = f.location();
                    stackModel.addElement(l.declaringType().name() + "." + l.method().name() + "():" + l.lineNumber());
                }

                if (!stackModel.isEmpty()) {
                    stackList.setSelectedIndex(0);
                }

            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void updateContextToSelectedFrame() {
        int index = stackList.getSelectedIndex();
        if (index != -1 && currentThread != null) {
            try {
                StackFrame frame = currentThread.frame(index);
                Location loc = frame.location();

                this.currentClassName = loc.declaringType().name();
                sourcePane.setText(debugger.getSourceCode(currentClassName));
                highlightLine(loc.lineNumber());
                gutter.repaint();

                updateTree(frame);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables Locales");
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
                arrayNode.add(new DefaultMutableTreeNode("[Déjà affiché]"));
                return;
            }
            visited.add(arrayRef);

            try {
                List<Value> values = arrayRef.getValues();
                for (int i = 0; i < values.size(); i++) {
                    addNode(arrayNode, "[" + i + "]", values.get(i), new HashSet<>(visited));
                }
            } catch (Exception e) {
                arrayNode.add(new DefaultMutableTreeNode("Erreur lecture tableau"));
            }
        }
        else if (value instanceof StringReference) {
            parent.add(new DefaultMutableTreeNode(name + " = \"" + ((StringReference) value).value() + "\""));
        }
        else if (value instanceof ObjectReference) {
            ObjectReference objRef = (ObjectReference) value;
            ReferenceType refType = objRef.referenceType();

            DefaultMutableTreeNode objectNode = new DefaultMutableTreeNode(name + " (" + refType.name() + ")");
            parent.add(objectNode);

            if (visited.contains(objRef)) {
                objectNode.add(new DefaultMutableTreeNode("[Cycle détecté]"));
                return;
            }
            visited.add(objRef);

            try {
                for (Field field : refType.allFields()) {
                    Value fieldValue = objRef.getValue(field);
                    addNode(objectNode, field.name(), fieldValue, new HashSet<>(visited));
                }
            } catch (Exception e) {
                objectNode.add(new DefaultMutableTreeNode("Erreur lecture champs"));
            }
        }
        else {
            parent.add(new DefaultMutableTreeNode(name + " = " + value.toString()));
        }
    }

    private class LineNumberGutter extends JPanel {
        private JTextPane textPane;

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
            int offset = textPane.viewToModel2D(p);
            if (offset < 0) return;
            int line = textPane.getDocument().getDefaultRootElement().getElementIndex(offset) + 1;

            if (currentClassName != null) {
                if (breakpoints.contains(line)) {
                    breakpoints.remove(line);
                    debugger.removeBreakPoint(currentClassName, line);
                    appendToConsole("[GUI] Breakpoint retiré ligne " + line);
                } else {
                    breakpoints.add(line);
                    try {
                        debugger.setBreakPoint(currentClassName, line);
                        appendToConsole("[GUI] Breakpoint ajouté ligne " + line);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
                repaint();
            }
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

            for (int i = 0; i < lineCount; i++) {
                try {
                    int startOffset = root.getElement(i).getStartOffset();
                    Rectangle2D rect = textPane.modelToView2D(startOffset);

                    if (rect != null) {
                        int y = (int) rect.getY();
                        int height = (int) rect.getHeight();
                        int lineNum = i + 1;

                        if (breakpoints.contains(lineNum)) {
                            g2.setColor(Color.RED);
                            g2.fillOval(5, y + (height - 10) / 2, 10, 10);
                        }

                        g2.setColor(Color.GRAY);
                        String txt = String.valueOf(lineNum);
                        int x = getWidth() - metrics.stringWidth(txt) - 5;
                        g2.drawString(txt, x, y + metrics.getAscent());
                    }
                } catch (BadLocationException e) {}
            }
        }
    }
}