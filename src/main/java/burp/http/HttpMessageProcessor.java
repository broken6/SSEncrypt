package burp.http;

import burp.IBurpExtenderCallbacks;
import burp.IHttpRequestResponse;
import burp.IHttpService;
import burp.IRequestInfo;
import burp.core.BurpSSEPlugin;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static burp.http.HttpHandlers.getStackTraceAsString;

// 支持多层标签嵌套，从最里面的嵌套开始处理
public class HttpMessageProcessor {
    private final BurpSSEPlugin plugin;

    public HttpMessageProcessor(BurpSSEPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean checkMatchingRule(boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        IRequestInfo requestInfo = this.plugin.getHelpers().analyzeRequest(messageInfo);

        JTable ruleTable = plugin.getGuiManager().getRuleTable();
        String messageContent;
        if (messageIsRequest) {
            byte[] req = messageInfo.getRequest();
            if (req == null) return false;
            messageContent = new String(req, StandardCharsets.UTF_8);
        } else {
            byte[] resp = messageInfo.getResponse();
            if (resp == null) return false;
            messageContent = new String(resp, StandardCharsets.UTF_8);
        }

        for (int i = 0; i < ruleTable.getRowCount(); i++) {
            Boolean enabled = (Boolean) ruleTable.getValueAt(i, 5);
            if (enabled != null && !enabled) continue;
            String encDec = (String) ruleTable.getValueAt(i, 3);
            if (!"encrypt".equals(encDec)) continue;

            String urlPath = (String) ruleTable.getValueAt(i, 0);
            String regex = (String) ruleTable.getValueAt(i, 1);
            String type = (String) ruleTable.getValueAt(i, 2);
            if (requestInfo.getUrl().getPath().equals(urlPath) || requestInfo.getUrl().getPath().startsWith(urlPath)) {
                if ((messageIsRequest && "request".equals(type)) || (!messageIsRequest && "response".equals(type))) {
                    try {
                        Pattern pattern = getCachedPattern(regex);
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


    private String encryptData(String originalMessage, Matcher matcher, String regex, String scriptName) throws Exception {
        String script = plugin.getEncryptScripts().get(scriptName);
        if (script == null) {
            throw new Exception("Encrypt script '" + scriptName + "' not found.");
        }

        if (matcher.groupCount() < 1) {
            throw new Exception("Regex pattern '" + regex + "' does not contain a capture group (group 1).");
        }

        StringBuffer result = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String matchedData = matcher.group(1);
            String encryptedData = plugin.processWithSSE(matchedData, script);
            String replacement = fullMatch.substring(0, matcher.start(1) - matcher.start()) +
                    encryptedData +
                    fullMatch.substring(matcher.end(1) - matcher.start());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String encryptRequest(IRequestInfo requestInfo, String currentRequest) throws Exception {
        JTable ruleTable = plugin.getGuiManager().getRuleTable();
        boolean matchedRule;
        ArrayList<Integer> usedRules = new ArrayList<>();

        // Recursive encryption loop
        do {
            matchedRule = false;
            for (int i = 0; i < ruleTable.getRowCount(); i++) {
                Boolean enabled = (Boolean) ruleTable.getValueAt(i, 5);
                String encDec = (String) ruleTable.getValueAt(i, 3);
                if (!"encrypt".equals(encDec)) continue;
                if (enabled != null && !enabled) continue;
                String urlPath = (String) ruleTable.getValueAt(i, 0);
                String regex = (String) ruleTable.getValueAt(i, 1);
                String type = (String) ruleTable.getValueAt(i, 2);
                String scriptName = (String) ruleTable.getValueAt(i, 4);
                if (usedRules.contains(i)) continue;

                if (requestInfo.getUrl().getPath().equals(urlPath) || requestInfo.getUrl().getPath().startsWith(urlPath)) {
                    if ("request".equals(type)) {
                        Matcher matcher = getCachedPattern(regex).matcher(currentRequest);
                        if (matcher.find()) {
                            usedRules.add(i);
                            currentRequest = encryptData(currentRequest, matcher, regex, scriptName);
                            matchedRule = true;
                            break;
                        }
                    }
                }
            }
        } while (matchedRule);

        return currentRequest;
    }

    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (!this.plugin.isRunning() || !messageIsRequest) {
            return;
        }
        byte[] request = messageInfo.getRequest();
        if (request == null) return;
        IHttpService httpService = messageInfo.getHttpService();

        if (checkMatchingRule(messageIsRequest, messageInfo) && (toolFlag == IBurpExtenderCallbacks.TOOL_PROXY ||
                toolFlag == IBurpExtenderCallbacks.TOOL_EXTENDER || toolFlag == IBurpExtenderCallbacks.TOOL_SCANNER)) {

            try {
                IRequestInfo requestInfo = plugin.getHelpers().analyzeRequest(httpService, request);
                String currentRequest = new String(request, StandardCharsets.UTF_8);

                String modifiedRequest = encryptRequest(requestInfo, currentRequest);

                if (!modifiedRequest.equals(currentRequest)) {
                    int bodyOffset = plugin.getHelpers().analyzeRequest(modifiedRequest.getBytes()).getBodyOffset();
                    String modifiedBody = modifiedRequest.substring(bodyOffset);
                    List<String> headers = plugin.getHelpers().analyzeRequest(modifiedRequest.getBytes()).getHeaders();
                    updateRequest(messageInfo, headers, modifiedBody);
                    messageInfo.setHighlight("green");
                    messageInfo.setComment("SSEncrypt");
                }
            } catch (Exception e) {
                plugin.getCallbacks().printError("Error processing http message: " + e.getMessage());
                plugin.getCallbacks().printError(getStackTraceAsString(e));
                messageInfo.setHighlight("red");
                messageInfo.setComment("SSEncrypt");
            }
        }

        if (toolFlag == IBurpExtenderCallbacks.TOOL_REPEATER || toolFlag == IBurpExtenderCallbacks.TOOL_INTRUDER) {
            try {
                IRequestInfo requestInfo = plugin.getHelpers().analyzeRequest(httpService, request);
                String requestStr = new String(request, StandardCharsets.UTF_8);

                int bodyOffset = requestInfo.getBodyOffset();
                String bodyStr = requestStr.substring(bodyOffset);

                String modifiedBody = resolveTags(bodyStr);

                if (!modifiedBody.equals(bodyStr)) {
                    updateRequest(messageInfo, requestInfo.getHeaders(), modifiedBody);
                    messageInfo.setHighlight("green");
                    messageInfo.setComment("SSEncrypt");
                }
            } catch (Exception e) {
                plugin.getCallbacks().printError("Unexpected error in processHttpMessage: " + e.getMessage());
                plugin.getCallbacks().printError(getStackTraceAsString(e));
                messageInfo.setHighlight("red");
                messageInfo.setComment("SSEncrypt");
            }
        }
    }
    private Pattern getCachedPattern(String regex) {
        return plugin.getPatternCache().computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
    }

    private String resolveTags(String input) throws Exception {
        String previous;
        String current = input;
        do {
            previous = current;
            current = resolveSingleLevel(current);
        } while (!current.equals(previous));
        return current;
    }

    private String resolveSingleLevel(String input) throws Exception {
        String regex = "\\[\\[([^:]+):(.*?)\\]\\](?!\\])";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);

        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return input;
        matcher.reset();

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String scriptName = matcher.group(1);
            String content = matcher.group(2);

            String script = plugin.getEncryptScripts().get(scriptName);
            String output;

            if (script != null) {
                output = plugin.processWithSSE(content, script);

                if (output == null) {
                    output = "[[NULL_OUTPUT:" + scriptName + "]]";
                }
            } else {
                output = fullMatch;
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(output));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private void updateRequest(IHttpRequestResponse messageInfo, List<String> headers, String modifiedBodyStr) {
        byte[] modifiedBodyBytes = plugin.getHelpers().stringToBytes(modifiedBodyStr);
        headers.removeIf(header -> header.toLowerCase().startsWith("content-length:"));
        headers.add("Content-Length: " + modifiedBodyBytes.length);

        byte[] newRequest = plugin.getHelpers().buildHttpMessage(headers, modifiedBodyBytes);
        if (newRequest != null) {
            messageInfo.setRequest(newRequest);
        }
    }
}
