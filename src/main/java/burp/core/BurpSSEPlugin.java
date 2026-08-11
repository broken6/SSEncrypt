package burp.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.swing.*;

import burp.*;
import burp.config.ConfigManager;
import burp.extension.ContextMenuHandler;
import burp.extension.MessageEditorTab;
import burp.gui.GUIManager;
import burp.http.HttpMessageProcessor;
import burp.http.HttpServerManager;
import burp.sse.SSEProcessor;

public class BurpSSEPlugin implements IBurpExtender, IExtensionStateListener, IContextMenuFactory, IHttpListener, IMessageEditorTabFactory {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private HttpServerManager serverManager;
    private GUIManager guiManager;
    private ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, CountDownLatch> resultEvents = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private final CopyOnWriteArrayList<BlockingQueue<String>> sseClients = new CopyOnWriteArrayList<>();
    private final ExecutorService decryptExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();
    private HttpMessageProcessor httpMessageProcessor;

    private ConfigManager configManager;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        callbacks.setExtensionName("SSEncrypt v1.2.3");
        callbacks.registerExtensionStateListener(this);
        callbacks.registerContextMenuFactory(this);
        callbacks.registerHttpListener(this);
        callbacks.registerMessageEditorTabFactory(this);

        this.configManager = new ConfigManager(this, "SSEncrypt.json");

        serverManager = new HttpServerManager(this);
        guiManager = new GUIManager(this);
        httpMessageProcessor = new HttpMessageProcessor(this);

        configManager.loadConfig();
        guiManager.setupGUI();
    }

    public HttpServerManager getServerManager() {
        return serverManager;
    }

    public IBurpExtenderCallbacks getCallbacks() { return callbacks; }
    public IExtensionHelpers getHelpers() { return helpers; }
    public ConcurrentHashMap<String, String> getResults() { return results; }
    public ConcurrentHashMap<String, CountDownLatch> getResultEvents() { return resultEvents; }
    public Map<String, String> getEncryptScripts() { return configManager.getEncryptScripts(); }
    public Map<String, String> getDecryptScripts() { return configManager.getDecryptScripts(); }
    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) {
        this.isRunning = running;
        this.guiManager.setRunning(running);
    }
    public ExecutorService getDecryptExecutor() { return decryptExecutor; }
    public ConcurrentHashMap<String, Pattern> getPatternCache() { return patternCache; }

    public void addSSEClient(BlockingQueue<String> clientQueue) {
        sseClients.add(clientQueue);
    }

    public void removeSSEClient(BlockingQueue<String> clientQueue) {
        sseClients.remove(clientQueue);
    }

    public void broadcastToSSE(String msg) {
        for (BlockingQueue<String> queue : sseClients) {
            queue.offer(msg);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        return new ContextMenuHandler(this).createMenuItems(invocation);
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        httpMessageProcessor.processHttpMessage(toolFlag, messageIsRequest, messageInfo);
    }

    public String processWithSSE(String input, String script) throws Exception {
        return new SSEProcessor(this).processWithSSE(input, script);
    }

    @Override
    public void extensionUnloaded() {
        this.serverManager.stopServer();
        decryptExecutor.shutdown();
        try {
            if (!decryptExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                decryptExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            decryptExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    @Override
    public IMessageEditorTab createNewInstance(IMessageEditorController controller, boolean editable) {
        return new MessageEditorTab(this, controller, editable);
    }
}
