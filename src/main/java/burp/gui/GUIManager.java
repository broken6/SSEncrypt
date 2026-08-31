package burp.gui;

import burp.http.HttpServerManager;
import burp.ITab;
import burp.ITextEditor;
import burp.script.ScriptHandler;
import burp.core.BurpSSEPlugin;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GUIManager {
    private final BurpSSEPlugin plugin;
    private JPanel panel;
    private JTextField portField;
    private JButton startButton, stopButton, downloadScriptButton;
    private JList<String> encryptScriptList;
    private JList<String> decryptScriptList;
    private DefaultListModel<String> encryptScriptListModel;
    private DefaultListModel<String> decryptScriptListModel;
    private JTextField scriptNameField;
    //private ITextEditor scriptContentArea; // Replaced RSyntaxTextArea with ITextEditor
    private RSyntaxTextArea scriptContentArea;
    private final HttpServerManager serverManager;
    private JTable ruleTable;

    JButton addScript,deleteScript;

    private static final int PADDING = 4;
    private static final int GAP = 8;

    public GUIManager(BurpSSEPlugin plugin) {
        this.plugin = plugin;
        this.serverManager = plugin.getServerManager();
    }

    public void setupGUI() {
        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // 整体 padding

        setupServerPanel();
        setupScriptPanel();

        SwingUtilities.invokeLater(() -> {
            plugin.getCallbacks().customizeUiComponent(panel);
        });
        plugin.getCallbacks().addSuiteTab(new BurpTab());
    }

    private void setupServerPanel() {
// 创建服务器配置面板，带标题边框和 padding
        JPanel serverPanel = new JPanel(new BorderLayout());
        serverPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Server Configuration"),
                BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)
        ));

        // 使用 GridBagLayout 实现 form 布局（标签右对齐，字段左对齐）
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(GAP, GAP, GAP, GAP);  // 统一间距
        gbc.anchor = GridBagConstraints.WEST;        // 左对齐
        gbc.fill = GridBagConstraints.HORIZONTAL;    // 字段横向填充

        // Port 行
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;  // 标签不拉伸
        JLabel portLabel = new JLabel("Port:");
        portLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        formPanel.add(portLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;  // 输入框拉伸填充剩余空间
        portField = new JTextField(String.valueOf(plugin.getConfigManager().getBindPort()), 10);  // 稍宽一点，更好用
        formPanel.add(portField, gbc);

        // 按钮行（Start 和 Stop 放在同一行，右对齐）
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;           // 跨两列
        gbc.anchor = GridBagConstraints.EAST;  // 右对齐
        gbc.fill = GridBagConstraints.NONE;    // 按钮不填充

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, GAP, 0));

        downloadScriptButton = new JButton("TamperMonkey Script");
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(downloadScriptButton);

        formPanel.add(buttonPanel, gbc);

        serverPanel.add(formPanel, BorderLayout.CENTER);

        startButton.addActionListener(e -> {
            serverManager.startServer(portField.getText());
            updateButtonStates();
            if (plugin.isRunning()) {
                try {
                    plugin.getConfigManager().setBindPort(Integer.parseInt(portField.getText().trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        stopButton.addActionListener(e -> {
            serverManager.stopServer();
            updateButtonStates();
        });

        panel.add(serverPanel, BorderLayout.NORTH);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(GAP, 0, GAP, 0));
        panel.add(Box.createHorizontalStrut(PADDING));

        panel.add(new JLabel("Script Name: "));
        panel.add(scriptNameField);
        panel.add(Box.createHorizontalStrut(GAP));

        panel.add(addScript);
        panel.add(Box.createHorizontalStrut(GAP));

        panel.add(deleteScript);
        panel.add(Box.createHorizontalStrut(GAP));

        panel.add(Box.createHorizontalGlue());  // 把按钮推到右边，如果需要
        return panel;
    }

    private JPanel createScriptListsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Scripts"));

        // 加密部分
        JPanel encryptSection = new JPanel(new BorderLayout());
        encryptSection.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP/2, GAP));
        encryptSection.add(new JLabel("Encryption Scripts:"), BorderLayout.NORTH);
        encryptSection.add(new JScrollPane(encryptScriptList), BorderLayout.CENTER);

        // 解密部分
        JPanel decryptSection = new JPanel(new BorderLayout());
        decryptSection.setBorder(BorderFactory.createEmptyBorder(GAP/2, GAP, GAP, GAP));
        decryptSection.add(new JLabel("Decryption Scripts:"), BorderLayout.NORTH);
        decryptSection.add(new JScrollPane(decryptScriptList), BorderLayout.CENTER);

        panel.add(encryptSection);
        panel.add(Box.createVerticalStrut(GAP));
        panel.add(decryptSection);

        return panel;
    }

    private void setupScriptPanel() {
        JPanel scriptPanel = new JPanel(new BorderLayout());
        scriptPanel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));


        // 加密脚本列表部分
        encryptScriptListModel = new DefaultListModel<>();
        encryptScriptList = new JList<>(encryptScriptListModel);
        JLabel encryptLabel = new JLabel("Encryption Scripts:");

        // 解密脚本列表部分
        decryptScriptListModel = new DefaultListModel<>();
        decryptScriptList = new JList<>(decryptScriptListModel);
        JLabel decryptLabel = new JLabel("Decryption Scripts:");

        scriptNameField = new JTextField(20);


        // 初始化 Burp 的 TextEditor
        scriptContentArea = new RSyntaxTextArea();
        scriptContentArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        scriptContentArea.setCodeFoldingEnabled(true);
        scriptContentArea.setEditable(true);
        try {
            Theme theme = Theme.load(
                    GUIManager.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/idea.xml")
            );
            theme.apply(scriptContentArea);
        } catch (Exception e){ }

        scriptContentArea.setTabSize(4);

        // 创建表格 - 5列
        String[] columnNames = {"URL Path", "Regex Pattern", "Req/Res", "Enc/Dec", "Script", "Enabled"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 5) return Boolean.class; // Enabled column as Boolean for checkbox
                return String.class;
            }
        };
        ruleTable = new JTable(tableModel);

        JComboBox<String> reqResCombo = new JComboBox<>(new String[]{"request", "response"});
        ruleTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(reqResCombo));

        // 自定义 Script 列的 TableCellEditor

        ScriptCellEditor scriptCellEditor = new ScriptCellEditor();
        ruleTable.getColumnModel().getColumn(4).setCellEditor(scriptCellEditor);

        // 配置第3列 (Enc/Dec) 下拉框
        JComboBox<String> encDecCombo = new JComboBox<>(new String[]{"encrypt", "decrypt"});
        ruleTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(encDecCombo));
        encDecCombo.addActionListener(e -> {
            String encDec = (String) encDecCombo.getSelectedItem();
            int editingRow = ruleTable.getEditingRow();
            if (editingRow != -1) {
                JComboBox<String> scriptCombo = new JComboBox<>();
                Map<String, String> scripts;
                if ("encrypt".equals(encDec)) {
                    scripts = plugin.getEncryptScripts();
                } else {
                    scripts = plugin.getDecryptScripts();
                }
                if (scripts != null) {
                    scripts.keySet().forEach(scriptCombo::addItem);
                    String script = (String)ruleTable.getValueAt(editingRow, 4);
                    if (!scripts.containsKey(script)) {
                        ruleTable.setValueAt("", editingRow, 4);
                    }
                }

                scriptCellEditor.setRowEditor(editingRow, scriptCombo);
                if (ruleTable.getCellEditor() != null) {
                    ruleTable.getCellEditor().stopCellEditing();
                }
            }
        });

        // 配置第5列 (Enabled) 复选框 - 默认启用
        ruleTable.getColumnModel().getColumn(5).setCellRenderer(new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected(value != null && (Boolean) value);
                checkBox.setHorizontalAlignment(JCheckBox.CENTER);
                return checkBox;
            }
        });

        // 控制面板
        JPanel scriptControlPanel = new JPanel();
        addScript = new JButton("Add");
        deleteScript = new JButton("Delete");

        scriptControlPanel.add(new JLabel("Script Name:"));
        scriptControlPanel.add(scriptNameField);
        scriptControlPanel.add(addScript);
        scriptControlPanel.add(deleteScript);

        ScriptHandler scriptHandler = new ScriptHandler(plugin,
                encryptScriptListModel, encryptScriptList,
                decryptScriptListModel, decryptScriptList,
                scriptNameField, scriptContentArea, addScript);
        scriptNameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (scriptHandler.contains(scriptNameField.getText())) {
                    addScript.setText("Update " + (scriptHandler.isEncryptListSelected() ? "Encrypt" : "Decrypt"));
                } else {
                    addScript.setText("Add " + (scriptHandler.isEncryptListSelected() ? "Encrypt" : "Decrypt"));
                }
            }
        });

        encryptScriptList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                scriptHandler.setEncryptListSelected(true);
            }
        });
        decryptScriptList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                scriptHandler.setEncryptListSelected(false);
            }
        });
        addScript.addActionListener(e -> scriptHandler.addScript());
        deleteScript.addActionListener(e -> scriptHandler.deleteScript());
        encryptScriptList.addListSelectionListener(e -> scriptHandler.handleEncryptScriptSelection());
        decryptScriptList.addListSelectionListener(e -> scriptHandler.handleDecryptScriptSelection());
        downloadScriptButton.addActionListener(e -> downloadScript());

        // 布局设置

        // ------------------- 顶部控制栏 -------------------
        JPanel controlPanel = createControlPanel();
        scriptPanel.add(controlPanel, BorderLayout.NORTH);

        // ------------------- 主内容：左右分栏 -------------------
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.25);  // 左侧占 25%，右侧 75%
        mainSplit.setDividerLocation(280);  // 初始宽度
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);  // 去掉默认 border，让整体更干净

        // 左侧：加密/解密脚本列表（垂直 BoxLayout）
        JPanel leftPanel = createScriptListsPanel();
        mainSplit.setLeftComponent(leftPanel);

        // 右侧：编辑器 + 规则表（垂直分割，可拖拽调整）
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setResizeWeight(0.55);  // 编辑器占 55%，表格占 45%
        rightSplit.setDividerLocation(380);
        rightSplit.setContinuousLayout(true);
        rightSplit.setBorder(null);

        // 编辑器区域
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createTitledBorder("Script Editor"));

        RTextScrollPane scrollPane = new RTextScrollPane(scriptContentArea);
        editorPanel.add(scrollPane, BorderLayout.CENTER);

        rightSplit.setTopComponent(editorPanel);

        // 规则表区域
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Rule Configuration"));

        JScrollPane tableScroll = new JScrollPane(ruleTable);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        rightSplit.setBottomComponent(tablePanel);

        mainSplit.setRightComponent(rightSplit);

        scriptPanel.add(mainSplit, BorderLayout.CENTER);

        panel.add(scriptPanel, BorderLayout.CENTER);
    }

    private String readScriptContent(String filename) {
        String scriptContent;
        try {
            InputStream inputStream = getClass().getResourceAsStream("/"+filename);
            if (inputStream == null) {
                throw new FileNotFoundException("Cannot find tampermonkey.js in resources");
            }
            scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel,
                    "Error reading tampermonkey.js: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            scriptContent = "";
        }
        return scriptContent;
    }

    private void downloadScript() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("script.js"));
        int option = fileChooser.showSaveDialog(panel);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                String tamperMonkeyScript = readScriptContent("tampermonkey.js").replace("{bind_port}",
                        String.valueOf(plugin.getConfigManager().getBindPort()));
                writer.write(tamperMonkeyScript);
                JOptionPane.showMessageDialog(panel, "Script saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(panel, "Error saving script: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateButtonStates() {
        startButton.setEnabled(!plugin.isRunning());
        stopButton.setEnabled(plugin.isRunning());
    }

    public void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    public void addRule(String urlPath, String regex, Boolean reqOrRes, Boolean isEnc, String selectedScript) {
        DefaultTableModel model = (DefaultTableModel) ruleTable.getModel();
        String encDec = isEnc ? "encrypt" : "decrypt";
        int newRowIndex = model.getRowCount(); // 获取新行的索引
        model.addRow(new Object[]{urlPath, regex, reqOrRes ? "request" : "response", encDec, selectedScript, true}); // Default enabled

        // 获取 Script 列的自定义编辑器
        TableCellEditor editor = ruleTable.getColumnModel().getColumn(4).getCellEditor();
        if (editor instanceof ScriptCellEditor) {
            ScriptCellEditor scriptCellEditor = (ScriptCellEditor) editor;
            JComboBox<String> scriptCombo = new JComboBox<>();
            Map<String, String> scripts = isEnc ? plugin.getEncryptScripts() : plugin.getDecryptScripts();
            if (scripts != null) {
                scripts.keySet().forEach(scriptCombo::addItem);
            }
            scriptCellEditor.setRowEditor(newRowIndex, scriptCombo);
        }
    }

    public JTable getRuleTable() {
        return ruleTable;
    }

    class BurpTab implements ITab {
        @Override
        public String getTabCaption() {
            return "SSEncrypt";
        }

        @Override
        public Component getUiComponent() {
            return panel;
        }
    }

    public HttpServerManager getServerManager() {
        return serverManager;
    }


    class ScriptCellEditor extends AbstractCellEditor implements TableCellEditor {
        private JComboBox<String> currentCombo;
        private final Map<Integer, JComboBox<String>> rowEditors = new HashMap<>();

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentCombo = rowEditors.getOrDefault(row, new JComboBox<>());
            currentCombo.setSelectedItem(value);
            return currentCombo;
        }

        @Override
        public Object getCellEditorValue() {
            return currentCombo.getSelectedItem();
        }

        public void setRowEditor(int row, JComboBox<String> comboBox) {
            rowEditors.put(row, comboBox);
        }
    }
}
