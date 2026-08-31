package burp.util;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpUtils {

    private static final String[] CORS_LINES = {
            "Access-Control-Allow-Origin: *",
            "Access-Control-Allow-Methods: GET, POST, OPTIONS",
            "Access-Control-Allow-Headers: Content-Type, Authorization",
            "Access-Control-Max-Age: 86400"
    };

    public static Map<String, String> corsHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.put("Access-Control-Max-Age", "86400");
        return headers;
    }

    public static void writeHead(OutputStream os, int status, String statusText,
                                 Map<String, String> headers, boolean keepAlive) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(' ').append(statusText).append("\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        sb.append("\r\n");
        os.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static void writeJson(OutputStream os, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = corsHeaders();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("Content-Length", String.valueOf(bytes.length));
        writeHead(os, status, statusText(status), headers, false);
        os.write(bytes);
        os.flush();
    }

    public static void writeOptions(OutputStream os) throws java.io.IOException {
        writeHead(os, 204, "No Content", corsHeaders(), false);
        os.flush();
    }

    public static void writeEventStreamHead(OutputStream os) throws java.io.IOException {
        Map<String, String> headers = corsHeaders();
        headers.put("Content-Type", "text/event-stream");
        headers.put("Cache-Control", "no-cache");
        writeHead(os, 200, "OK", headers, true);
        os.flush();
    }

    private static String statusText(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default:  return "OK";
        }
    }
}
