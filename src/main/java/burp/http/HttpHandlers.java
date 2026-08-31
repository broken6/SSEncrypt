package burp.http;

import burp.util.HttpUtils;
import burp.core.BurpSSEPlugin;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
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

    public static void handleConnection(Socket socket, BurpSSEPlugin plugin) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        ByteArrayOutputStream headerBuf = readRequestHeaders(in);
        if (headerBuf == null) return;

        String headerText = new String(headerBuf.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0 || lines[0].trim().isEmpty()) return;

        String[] requestLine = lines[0].trim().split("\\s+");
        if (requestLine.length < 2) return;
        String method = requestLine[0];
        String path = requestLine[1];

        if ("OPTIONS".equalsIgnoreCase(method)) {
            HttpUtils.writeOptions(out);
            return;
        }

        int contentLength = 0;
        for (int i = 1; i < lines.length; i++) {
            int idx = lines[i].indexOf(':');
            if (idx > 0 && "content-length".equalsIgnoreCase(lines[i].substring(0, idx).trim())) {
                try {
                    contentLength = Integer.parseInt(lines[i].substring(idx + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        byte[] body = new byte[0];
        if (contentLength > 0) {
            body = in.readNBytes(contentLength);
        }
        String bodyText = new String(body, StandardCharsets.UTF_8);

        if ("/sse".equals(path)) {
            handleSSE(plugin, out);
        } else if ("/result".equals(path)) {
            handleResult(plugin, out, bodyText);
        } else if ("/input".equals(path)) {
            handleInput(plugin, out, bodyText);
        } else {
            HttpUtils.writeJson(out, 404, "{\"error\": true, \"message\": \"Not found\"}");
        }
    }

    private static ByteArrayOutputStream readRequestHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            // track \r\n\r\n sequence
            if (b == '\r') {
                state = (state == 2) ? 3 : 1;
            } else if (b == '\n') {
                state = (state == 1 || state == 3) ? state + 1 : 0;
            } else {
                state = 0;
            }
            if (state == 4) break;
        }
        if (buf.size() == 0) return null;
        return buf;
    }

    private static void handleSSE(BurpSSEPlugin plugin, OutputStream out) throws IOException {
        HttpUtils.writeEventStreamHead(out);

        BlockingQueue<String> clientQueue = new LinkedBlockingQueue<>(100);
        plugin.addSSEClient(clientQueue);

        try {
            while (plugin.isRunning()) {
                String msg = clientQueue.poll(1, TimeUnit.SECONDS);
                if (msg != null) {
                    out.write(("data: " + msg + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (IOException e) {
            plugin.getCallbacks().printOutput("Client disconnect: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getCallbacks().printOutput("SSE interrupted: " + e.getMessage());
        } finally {
            plugin.removeSSEClient(clientQueue);
            plugin.getCallbacks().printOutput("SSE Connection Close");
        }
    }

    private static void handleResult(BurpSSEPlugin plugin, OutputStream out, String requestBody) throws IOException {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            HttpUtils.writeJson(out, 400, "{\"error\": true, \"message\": \"Empty request body\"}");
            return;
        }
        try {
            JSONObject json = new JSONObject(requestBody);
            String taskId = json.getString("id");
            String output = json.getString("output");
            plugin.getResults().put(taskId, output);
            CountDownLatch latch = plugin.getResultEvents().get(taskId);
            if (latch != null) latch.countDown();
            HttpUtils.writeJson(out, 200, "{\"error\": false}");
        } catch (Exception e) {
            plugin.getCallbacks().printError("Error in /result: " + e.getMessage());
            if (out != null) HttpUtils.writeJson(out, 500, "{\"error\": true}");
        }
    }

    private static void handleInput(BurpSSEPlugin plugin, OutputStream out, String requestBody) throws IOException {
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

            HttpUtils.writeJson(out, 200, json.toString());
        } catch (Exception e) {
            plugin.getCallbacks().printError("Error in /input: " + e.getMessage());
            plugin.getCallbacks().printError(getStackTraceAsString(e));
            try {
                HttpUtils.writeJson(out, 500, "{\"error\": true, \"output\": \"Error\"}");
            } catch (IOException ignored) {
            }
        } finally {
            if (taskId != null) {
                plugin.getResultEvents().remove(taskId);
                plugin.getResults().remove(taskId);
            }
        }
    }
}
