package burp.sse;

import burp.core.BurpSSEPlugin;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SSEProcessor {
    private final BurpSSEPlugin plugin;

    public SSEProcessor(BurpSSEPlugin plugin) {
        this.plugin = plugin;
    }

    public String processWithSSE(String input, String script) throws Exception {
        String taskId = UUID.randomUUID().toString();
        JSONObject json = new JSONObject();
        json.put("id", taskId);
        json.put("input", input);
        json.put("script", script);

        int timeout = plugin.getConfigManager().getSSETimeout();

        CountDownLatch latch = new CountDownLatch(1);
        plugin.getResultEvents().put(taskId, latch);
        plugin.broadcastToSSE(json.toString());

        boolean completed = latch.await(timeout, TimeUnit.SECONDS);
        String output = completed ? plugin.getResults().remove(taskId) : "Timeout";
        plugin.getResultEvents().remove(taskId);

        if (!completed) throw new Exception("Processing timeout after " + timeout + "s");
        if (output == null) throw new Exception("No output received");

        return output;
    }
}