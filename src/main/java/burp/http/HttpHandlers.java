package burp.http;

import burp.util.HttpUtils;
import burp.core.BurpSSEPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class HttpHandlers {

    public static String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    static class SSEHandler implements HttpHandler {
        private final BurpSSEPlugin plugin;

        public SSEHandler(BurpSSEPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpUtils.setCORSHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            // Each SSE client gets its own queue so messages are broadcast
            BlockingQueue<String> clientQueue = new LinkedBlockingQueue<>(100);
            plugin.addSSEClient(clientQueue);

            try (OutputStream os = exchange.getResponseBody()) {
                while (plugin.isRunning()) {
                    try {
                        String msg = clientQueue.poll(1, TimeUnit.SECONDS);
                        if (msg != null) {
                            os.write(("data: " + msg + "\n\n").getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        }
                    } catch (IOException e) {
                        plugin.getCallbacks().printOutput("Client disconnect: " + e.getMessage());
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        plugin.getCallbacks().printOutput("SSE interrupted: " + e.getMessage());
                        break;
                    }
                }
            } finally {
                plugin.removeSSEClient(clientQueue);
                exchange.close();
                plugin.getCallbacks().printOutput("SSE Connection Close");
            }
        }
    }
    static class ResultHandler implements HttpHandler {
        private final BurpSSEPlugin plugin;

        public ResultHandler(BurpSSEPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpUtils.setCORSHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                HttpUtils.sendResponse(exchange, 405, "{\"error\": true, \"message\": \"Method not allowed\"}");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                JSONObject json = new JSONObject(requestBody);
                String taskId = json.getString("id");
                String output = json.getString("output");
                plugin.getResults().put(taskId, output);
                CountDownLatch latch = plugin.getResultEvents().get(taskId);
                if (latch != null) latch.countDown();
                HttpUtils.sendResponse(exchange, 200, "{\"error\": false}");
            } catch (Exception e) {
                plugin.getCallbacks().printError("Error in /result: " + e.getMessage());
                HttpUtils.sendResponse(exchange, 500, "{\"error\": true}");
            }
        }
    }

    static class InputHandler implements HttpHandler {
        private final BurpSSEPlugin plugin;

        public InputHandler(BurpSSEPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpUtils.setCORSHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                HttpUtils.sendResponse(exchange, 405, "{\"error\": true, \"message\": \"Method not allowed\"}");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String taskId = null;
            try {
                JSONObject json = new JSONObject(requestBody);
                taskId = UUID.randomUUID().toString();
                json.put("id", taskId);

                if (!json.has("script") && json.has("funcType") && json.has("funcName")) {
                    String script = "this.result(msg, msg.input);";
                    if ("enc".equals(json.get("funcType")) && plugin.getEncryptScripts().containsKey(json.getString("funcName"))) {
                        script = plugin.getEncryptScripts().get(json.getString("funcName"));
                    } else if ("dec".equals(json.get("funcType")) && plugin.getDecryptScripts().containsKey(json.getString("funcName"))) {
                        script = plugin.getDecryptScripts().get(json.getString("funcName"));
                    }
                    json.put("script", script);
                }

                int timeout = plugin.getConfigManager().getSSETimeout();
                if (json.has("timeout")) {
                    timeout = json.getInt("timeout");
                }
                json.put("timeout", timeout);

                CountDownLatch latch = new CountDownLatch(1);
                plugin.getResultEvents().put(taskId, latch);
                plugin.broadcastToSSE(json.toString());

                boolean completed = latch.await(timeout, TimeUnit.SECONDS);
                String output = completed ? plugin.getResults().remove(taskId) : "Timeout";
                json.put("output", output != null ? output : "Timeout");
                json.put("error", !completed);

                HttpUtils.sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                plugin.getCallbacks().printError("Error in /input: " + e.getMessage());
                plugin.getCallbacks().printError(getStackTraceAsString(e));
                HttpUtils.sendResponse(exchange, 500, "{\"error\": true, \"output\": \"Error\"}");
            } finally {
                if (taskId != null) {
                    plugin.getResultEvents().remove(taskId);
                    plugin.getResults().remove(taskId);
                }
            }
        }
    }
}
