package burp.extension;

import burp.*;
import burp.core.BurpSSEPlugin;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageEditorTab implements IMessageEditorTab {
    private final BurpSSEPlugin plugin;
    private final IMessageEditorController controller;
    private final JTabbedPane tabbedPane;
    private final ITextEditor rawEditor;
    private final RSyntaxTextArea prettyArea;
    private final HexViewerPanel hexViewer;
    private final ITextEditor unicodeEditor;
    private static final int TAB_PRETTY = 1;
    private static final int TAB_HEX = 2;
    private static final int TAB_UNICODE = 3;
    private byte[] currentContent;
    private boolean isRequest;
    private final String tittle = "Decrypted";
    private final Object contentLock = new Object();
    private String lastDisplayedContent = null;
    private final AtomicLong taskEpoch = new AtomicLong(0);

    public MessageEditorTab(BurpSSEPlugin plugin, IMessageEditorController controller, boolean editable) {
        this.plugin = plugin;
        this.controller = controller;
        this.rawEditor = plugin.getCallbacks().createTextEditor();
        this.rawEditor.setEditable(editable);

        this.prettyArea = new RSyntaxTextArea();
        this.prettyArea.setEditable(false);
        this.prettyArea.setCodeFoldingEnabled(true);
        this.prettyArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        try {
            Theme theme = Theme.load(MessageEditorTab.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/idea.xml"));
            theme.apply(prettyArea);
        } catch (Exception ignored) { }

        this.hexViewer = new HexViewerPanel();
        this.unicodeEditor = plugin.getCallbacks().createTextEditor();
        this.unicodeEditor.setEditable(false);

        this.tabbedPane = new JTabbedPane();
        this.tabbedPane.addTab("Raw", rawEditor.getComponent());
        this.tabbedPane.addTab("Pretty", new JScrollPane(prettyArea));
        this.tabbedPane.addTab("Hex", hexViewer);
        this.tabbedPane.addTab("Unicode", unicodeEditor.getComponent());
        this.tabbedPane.addChangeListener(e -> refreshDerivedViews());
    }

    @Override
    public String getTabCaption() {
        return tittle;
    }

    @Override
    public Component getUiComponent() {
        return tabbedPane;
    }

    @Override
    public boolean isEnabled(byte[] content, boolean isRequest) {
        return true;
    }

    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        this.isRequest = isRequest;
        String incoming = new String(content, StandardCharsets.UTF_8).replace("\r\n", "\n");

        synchronized (contentLock) {
            if (incoming.equals(lastDisplayedContent)) {
                return;
            }
            this.currentContent = content;
        }

        boolean match = checkMatchingRule(content, isRequest);
        if (match) {
            loadDecryptedData();
        } else {
            setTextSafely("No Match Rule");
        }
    }

    @Override
    public byte[] getMessage() {
        return rawEditor != null ? rawEditor.getText() : new byte[0];
    }

    @Override
    public boolean isModified() {
        return rawEditor != null && rawEditor.isTextModified();
    }

    @Override
    public byte[] getSelectedData() {
        return rawEditor != null ? rawEditor.getSelectedText() : null;
    }

    private boolean checkMatchingRule(byte[] content, boolean isRequest) {
        IHttpService httpService = controller.getHttpService();
        byte[] request = controller.getRequest();
        if (request == null) {
            return false;
        }

        IRequestInfo requestInfo = plugin.getHelpers().analyzeRequest(httpService, request);
        JTable ruleTable = plugin.getGuiManager().getRuleTable();
        String messageContent = new String(content, StandardCharsets.UTF_8);

        for (int i = 0; i < ruleTable.getRowCount(); i++) {
            Boolean enabled = (Boolean) ruleTable.getValueAt(i, 5);
            if (enabled != null && !enabled) continue;
            String isEnc = (String) ruleTable.getValueAt(i, 3);
            if (!"decrypt".equals(isEnc)) continue;

            String urlPath = (String) ruleTable.getValueAt(i, 0);
            String regex = (String) ruleTable.getValueAt(i, 1);
            String type = (String) ruleTable.getValueAt(i, 2);
            if (requestInfo.getUrl().getPath().equals(urlPath) || requestInfo.getUrl().getPath().startsWith(urlPath)) {
                if ((isRequest && "request".equals(type)) || (!isRequest && "response".equals(type))) {
                    try {
                        Pattern pattern = plugin.getPatternCache().computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
                        Matcher matcher = pattern.matcher(messageContent);
                        if (matcher.find()) {
                            return true;
                        }
                    } catch (Exception e) {
                        plugin.getCallbacks().printError("Invalid regex pattern at row " + i + ": " + e.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private void loadDecryptedData() {
        if (currentContent == null) {
            setTextSafely("No content available.");
            return;
        }

        String originalMessage = new String(currentContent, StandardCharsets.UTF_8);
        setTextSafely(originalMessage);

        final long epoch = taskEpoch.incrementAndGet();
        plugin.getDecryptExecutor().submit(() -> {
            try {
                if (taskEpoch.get() != epoch) return;
                IHttpService httpService = controller.getHttpService();
                byte[] requestBytes = controller.getRequest();
                if (requestBytes == null) {
                    setTextSafely("No request data available to determine URL.");
                    return;
                }

                IRequestInfo requestInfo = plugin.getHelpers().analyzeRequest(httpService, requestBytes);
                JTable ruleTable = plugin.getGuiManager().getRuleTable();
                String currentResult = originalMessage;
                boolean matchedRule;
                ArrayList<Integer> usedRules = new ArrayList<>();

                do {
                    matchedRule = false;
                    for (int i = 0; i < ruleTable.getRowCount(); i++) {
                        Boolean enabled = (Boolean) ruleTable.getValueAt(i, 5);
                        String encDec = (String) ruleTable.getValueAt(i, 3);
                        if (!"decrypt".equals(encDec)) continue;
                        if (enabled != null && !enabled) continue;
                        String urlPath = (String) ruleTable.getValueAt(i, 0);
                        String regex = (String) ruleTable.getValueAt(i, 1);
                        String type = (String) ruleTable.getValueAt(i, 2);
                        String scriptName = (String) ruleTable.getValueAt(i, 4);
                        if (usedRules.contains(i)) continue;

                        if (requestInfo.getUrl().getPath().equals(urlPath) || requestInfo.getUrl().getPath().startsWith(urlPath)) {
                            if ((isRequest && "request".equals(type)) || (!isRequest && "response".equals(type))) {
                                Pattern pattern = plugin.getPatternCache().computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
                                Matcher matcher = pattern.matcher(currentResult);
                                if (matcher.find()) {
                                    usedRules.add(i);
                                    currentResult = decryptData(currentResult, matcher, regex, scriptName);
                                    matchedRule = true;
                                    break;
                                }
                            }
                        }
                    }
                } while (matchedRule);

                if (taskEpoch.get() == epoch) {
                    setTextSafely(currentResult);
                }
            } catch (Exception e) {
                if (taskEpoch.get() == epoch) {
                    setTextSafely("Error decrypting data: " + e.getMessage());
                }
            }
        });
    }

    private String decryptData(String originalMessage, Matcher matcher, String regex, String scriptName) throws Exception {
        String script = plugin.getDecryptScripts().get(scriptName);
        if (script == null) {
            throw new Exception("Decrypt script '" + scriptName + "' not found.");
        }

        if (matcher.groupCount() < 1) {
            throw new Exception("Regex pattern '" + regex + "' does not contain a capture group (group 1).");
        }

        StringBuffer result = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String matchedData = matcher.group(1);
            String decryptedData = plugin.processWithSSE(matchedData, script);
            String replacement = fullMatch.substring(0, matcher.start(1) - matcher.start()) +
                    decryptedData +
                    fullMatch.substring(matcher.end(1) - matcher.start());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private void setTextSafely(String text) {
        if (rawEditor != null) {
            SwingUtilities.invokeLater(() -> {
                synchronized (contentLock) {
                    lastDisplayedContent = text.replace("\r\n", "\n");
                }
                byte[] data = text.getBytes(StandardCharsets.UTF_8);
                rawEditor.setText(data);
                renderPretty(text);
                hexViewer.setData(data);
                unicodeEditor.setText(decodeUnicode(text).getBytes(StandardCharsets.UTF_8));
            });
        } else {
            plugin.getCallbacks().printOutput("Text editor is null, cannot set text: " + text);
        }
    }

    private void refreshDerivedViews() {
        if (tabbedPane == null || rawEditor == null) return;
        byte[] data = rawEditor.getText();
        if (data == null) data = new byte[0];
        int idx = tabbedPane.getSelectedIndex();
        if (idx == TAB_PRETTY) {
            renderPretty(new String(data, StandardCharsets.UTF_8));
        } else if (idx == TAB_HEX) {
            hexViewer.setData(data);
        } else if (idx == TAB_UNICODE) {
            unicodeEditor.setText(decodeUnicode(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void renderPretty(String text) {
        if (prettyArea == null) return;
        String body = text;
        int sep = text.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (sep < 0) {
            sep = text.indexOf("\n\n");
            sepLen = 2;
        }
        if (sep >= 0) {
            body = text.substring(sep + sepLen);
        }
        prettyArea.setSyntaxEditingStyle(detectSyntax(body));
        prettyArea.setText(text);
    }

    private String detectSyntax(String body) {
        String b = body == null ? "" : body.trim();
        if (b.isEmpty()) return SyntaxConstants.SYNTAX_STYLE_NONE;
        char c0 = b.charAt(0);
        if (c0 == '{' || c0 == '[') return SyntaxConstants.SYNTAX_STYLE_JSON;
        if (c0 == '<') return SyntaxConstants.SYNTAX_STYLE_XML;
        String lower = b.toLowerCase();
        if (lower.startsWith("select ") || lower.startsWith("insert ") || lower.startsWith("update ")
                || lower.startsWith("delete ") || lower.startsWith("create ")) {
            return SyntaxConstants.SYNTAX_STYLE_SQL;
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    private String decodeUnicode(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 5 < text.length() && text.charAt(i + 1) == 'u') {
                String hex = text.substring(i + 2, i + 6);
                if (isHex(hex)) {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
