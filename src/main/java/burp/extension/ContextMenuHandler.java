package burp.extension;

import burp.IContextMenuInvocation;
import burp.IHttpRequestResponse;
import burp.util.RegexUtils;
import burp.core.BurpSSEPlugin;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ContextMenuHandler {
    private final BurpSSEPlugin plugin;

    public ContextMenuHandler(BurpSSEPlugin plugin) {
        this.plugin = plugin;
    }

    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        if (invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
                invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST ||
                invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE ||
                invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE) {
            List<JMenuItem> menu = new ArrayList<>();

            // 添加 "Apply Encrypt Script" 菜单
            JMenu scriptMenu = new JMenu("Apply Encrypt Script");
            for (String scriptName : plugin.getEncryptScripts().keySet()) {
                JMenuItem item = new JMenuItem(scriptName);
                item.addActionListener(e -> applyScript(invocation, scriptName));
                scriptMenu.add(item);
            }
            menu.add(scriptMenu);

            // 添加 "Add Rule" 菜单项
            JMenuItem addRuleItem = new JMenuItem("Add Rule");
            addRuleItem.addActionListener(e -> showAddRuleDialog(invocation));
            menu.add(addRuleItem);

            return menu;
        }
        return null;
    }

    private void applyScript(IContextMenuInvocation invocation, String scriptName) {
        int[] bounds = invocation.getSelectionBounds();
        IHttpRequestResponse message = invocation.getSelectedMessages()[0];
        byte[] request = message.getRequest();
        String selectedText = new String(Arrays.copyOfRange(request, bounds[0], bounds[1]));
        String taggedText = String.format("[[%s:%s]]", scriptName, selectedText);
        byte[] newRequest = plugin.getHelpers().stringToBytes(
                new String(request, 0, bounds[0]) + taggedText +
                        new String(request, bounds[1], request.length - bounds[1])
        );
        message.setRequest(newRequest);
    }

    private void showAddRuleDialog(IContextMenuInvocation invocation) {
        IHttpRequestResponse message = invocation.getSelectedMessages()[0];
        int[] bounds = invocation.getSelectionBounds();
        boolean isReq = (invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
                invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST);
        byte[] content =  isReq ?
                message.getRequest() : message.getResponse();
        String selectedText = "";
        if (bounds != null && bounds[0] >= 0 && bounds[1] <= content.length && bounds[0] < bounds[1]) {
            selectedText = new String(Arrays.copyOfRange(content, bounds[0], bounds[1]));
        }

        // 创建对话框
        JDialog dialog = new JDialog((Frame) null, "Add Rule", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(null);

        // 消息编辑框
        JTextArea messageArea = new JTextArea(10, 40);
        messageArea.setEditable(false);
        String contentString = new String(content);

        messageArea.setText(contentString);
        messageArea.setLineWrap(true);
        JScrollPane messageScrollPane = new JScrollPane(messageArea);

        // 初始化正则表达式
        String generateRegex="";
        try {
            generateRegex = RegexUtils.generateRegex(contentString, selectedText, bounds[0]);
        } catch (Exception e) {
            plugin.getCallbacks().printError("Err in generate regex: " + e.getMessage());
            generateRegex = "";
        }

        // 正则表达式编辑框
        JTextField regexField = new JTextField(40);
        regexField.setText(generateRegex);

        // 加密或解密下拉列表
        JComboBox<String> scriptModeCombo = new JComboBox<>();
        scriptModeCombo.addItem("encrypt");
        scriptModeCombo.addItem("decrypt");
        scriptModeCombo.setSelectedItem("decrypt");

        // 脚本下拉列表
        JComboBox<String> scriptCombo = new JComboBox<>();
        for (String scriptName : plugin.getDecryptScripts().keySet()) {
            scriptCombo.addItem(scriptName);
        }
        if (scriptCombo.getItemCount() == 0) {
            scriptCombo.addItem("No decrypt scripts available");
            scriptCombo.setEnabled(false);
        }

        // URL Path
        String urlPath = plugin.getHelpers().analyzeRequest(message).getUrl().getPath();
        JTextField urlPathField = new JTextField(urlPath, 40);
        urlPathField.setEditable(true);

        // 按钮面板
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // 布局组件
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        inputPanel.add(new JLabel("URL Path:"));
        inputPanel.add(urlPathField);
        inputPanel.add(new JLabel("Regex Pattern:"));
        inputPanel.add(regexField);
        inputPanel.add(new JLabel("Mode:"));
        inputPanel.add(scriptModeCombo);
        inputPanel.add(new JLabel("Script:"));
        inputPanel.add(scriptCombo);

        dialog.add(messageScrollPane, BorderLayout.CENTER);
        dialog.add(inputPanel, BorderLayout.NORTH);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        scriptModeCombo.addActionListener(e->{
            String scriptMode = (String)scriptModeCombo.getSelectedItem();
            Map<String, String> scripts;
            if (scriptMode.equals("encrypt")) {
                scripts = plugin.getEncryptScripts();
            } else {
                scripts = plugin.getDecryptScripts();
            }
            scriptCombo.removeAllItems();
            for (String scriptName : scripts.keySet()) {
                scriptCombo.addItem(scriptName);
            }
            if (scriptCombo.getItemCount() == 0) {
                scriptCombo.addItem("No decrypt scripts available");
                scriptCombo.setEnabled(false);
            }
        });

        // 监听消息编辑框的选择变化
        messageArea.addCaretListener(e -> {
            int start = messageArea.getSelectionStart();
            int end = messageArea.getSelectionEnd();
            if (start >= 0 && end <= content.length && start < end) {
                String newSelectedText = contentString.substring(start, end);
                try {
                    String newRegex = RegexUtils.generateRegex(contentString, newSelectedText, start);
                    regexField.setText(newRegex);
                } catch (Exception ex) {
                    plugin.getCallbacks().printError("Err in generate regex: " + ex.getMessage());
                    regexField.setText("");
                }
            }
        });

        // 为 regexField 添加事件监听器
        regexField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }

            private void updateOkButtonState() {
                ContextMenuHandler.this.updateOkButtonState(regexField, okButton, contentString, messageArea);
            }
        });

        // OK 按钮事件：保存规则
        okButton.addActionListener(e -> {
            String selectedScript = (String) scriptCombo.getSelectedItem();
            if (selectedScript != null && !selectedScript.equals("No decrypt scripts available")) {
                String regex = regexField.getText().trim();
                Boolean isEnc = scriptModeCombo.getSelectedItem().equals("encrypt");
                if (!regex.isEmpty()) {
                    plugin.getGuiManager().addRule(urlPath, regex, isReq, isEnc, selectedScript);
                    dialog.dispose();
                }
            }
        });

        // Cancel 按钮事件：关闭对话框
        cancelButton.addActionListener(e -> dialog.dispose());

        // 窗口初始化执行正则匹配
        ContextMenuHandler.this.updateOkButtonState(regexField, okButton, contentString, messageArea);

        dialog.setVisible(true);
    }

    private void updateOkButtonState(JTextField regexField, JButton okButton, String contentString, JTextArea messageArea ) {
        String regex = regexField.getText().trim();
        if (regex.isEmpty()) {
            okButton.setEnabled(false);
            return;
        }

        try {
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(contentString);
            int matchCount = 0;
            Highlighter highlighter = messageArea.getHighlighter();
            highlighter.removeAllHighlights();
            while (matcher.find()) {

                if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                    highlighter.addHighlight(matcher.start(1), matcher.end(1), new DefaultHighlighter.DefaultHighlightPainter(Color.yellow));
                }

                matchCount++;
                if (matchCount > 1) {
                    break;
                }
            }
            okButton.setEnabled(matchCount == 1); // 仅当有唯一匹配时启用
        } catch (PatternSyntaxException e) {
            plugin.getCallbacks().printError("Invalid regex: " + e.getMessage());
            okButton.setEnabled(false);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }
}