package burp.config;

import java.util.Map;
import java.util.TreeMap;

public class PluginConfig {
    protected Map<String, String> encryptScripts = new TreeMap<>();
    protected Map<String, String> decryptScripts = new TreeMap<>();
    protected int bindPort = 9999;
    protected int sseTimeout = 10;
}
