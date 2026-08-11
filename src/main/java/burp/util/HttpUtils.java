package burp.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpUtils {
    public static void setCORSHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Access-Control-Max-Age", "86400");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            try {
                exchange.sendResponseHeaders(204, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8); // ✅ 固定编码

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);       // ✅ 用字节长度

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes); // ✅ 写同一份 bytes
        }
    }
}