package burp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import burp.core.BurpSSEPlugin;

public class HttpServerManager {
    private final BurpSSEPlugin plugin;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService workerPool;

    public HttpServerManager(BurpSSEPlugin plugin) {
        this.plugin = plugin;
    }

    public void startServer(String portText) {
        if (plugin.isRunning()) return;

        int port;
        try {
            port = Integer.parseInt(portText.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            plugin.getCallbacks().printError("Invalid port number: " + portText + " (" + e.getMessage() + ")");
            return;
        }

        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(port), 10);
            workerPool = Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors()));

            acceptThread = new Thread(this::acceptLoop, "SSEncrypt-Acceptor");
            acceptThread.setDaemon(true);
            acceptThread.start();

            plugin.setRunning(true);
            plugin.getCallbacks().printOutput("Server started on port " + port);
        } catch (IOException e) {
            plugin.getCallbacks().printError("Error starting server: " + e.getMessage());
        } catch (Throwable t) {
            plugin.getCallbacks().printError("Unexpected error starting server: " + t);
            plugin.getCallbacks().printError(HttpHandlers.getStackTraceAsString(t));
        }
    }

    private void acceptLoop() {
        while (plugin.isRunning() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(0);
                workerPool.execute(() -> handleSocket(socket));
            } catch (IOException e) {
                if (plugin.isRunning() && serverSocket != null && !serverSocket.isClosed()) {
                    plugin.getCallbacks().printError("Accept error: " + e.getMessage());
                }
                break;
            } catch (Throwable t) {
                plugin.getCallbacks().printError("Unexpected accept error: " + t);
                break;
            }
        }
    }

    private void handleSocket(Socket socket) {
        try {
            HttpHandlers.handleConnection(socket, plugin);
        } catch (Exception e) {
            plugin.getCallbacks().printError("Error handling connection: " + e.getMessage());
        } catch (Throwable t) {
            plugin.getCallbacks().printError("Unexpected connection error: " + t);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void stopServer() {
        if (!plugin.isRunning() || serverSocket == null) return;
        plugin.setRunning(false);
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        if (acceptThread != null) acceptThread.interrupt();
        if (workerPool != null) workerPool.shutdownNow();
        plugin.getCallbacks().printOutput("Server stopped");
    }
}
