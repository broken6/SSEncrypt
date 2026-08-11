package burp.config;

import burp.IBurpExtenderCallbacks;
import burp.core.BurpSSEPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class ConfigManager {

    private final BurpSSEPlugin plugin;
    private final IBurpExtenderCallbacks callbacks;
    private final PluginConfig pluginConfig;
    private File configFile;
    private final String config;

    public ConfigManager(BurpSSEPlugin plugin, String config) {
        this.config = config;
        this.plugin = plugin;
        this.callbacks = plugin.getCallbacks();
        this.pluginConfig = new PluginConfig();
    }

    private void initConfigFile() {
        configFile = new File(callbacks.getExtensionFilename()).getParentFile();
        configFile = new File(configFile, config);
    }

    public void saveConfig() {
        if (configFile == null) initConfigFile();
        File tempFile = new File(configFile.getAbsolutePath() + ".tmp");
        try {
            JSONObject configJson = new JSONObject();

            JSONArray encryptScriptsArray = new JSONArray();
            for (Map.Entry<String, String> entry : pluginConfig.encryptScripts.entrySet()) {
                JSONObject scriptObj = new JSONObject();
                scriptObj.put("name", entry.getKey());
                scriptObj.put("content", entry.getValue());
                encryptScriptsArray.put(scriptObj);
            }

            JSONArray decryptScriptsArray = new JSONArray();
            for (Map.Entry<String, String> entry : pluginConfig.decryptScripts.entrySet()) {
                JSONObject scriptObj = new JSONObject();
                scriptObj.put("name", entry.getKey());
                scriptObj.put("content", entry.getValue());
                decryptScriptsArray.put(scriptObj);
            }

            configJson.put("encrypt_scripts", encryptScriptsArray);
            configJson.put("decrypt_scripts", decryptScriptsArray);
            configJson.put("bind_port", this.pluginConfig.bindPort);
            configJson.put("sse_timeout", this.pluginConfig.sseTimeout);

            try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
                writer.write(configJson.toString(2));
            }
            Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            callbacks.printOutput("Scripts saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            callbacks.printError("Error saving scripts to config: " + e.getMessage());
            tempFile.delete();
        }
    }

    private String readScriptContent(String filename) {
        String scriptContent = "";
        try {
            InputStream inputStream = getClass().getResourceAsStream("/" + filename);
            if (inputStream == null) {
                throw new FileNotFoundException("Cannot find " + filename + " in resources");
            }
            scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();
        } catch (IOException e) {
            this.callbacks.printError("Error reading script: " + e.getMessage());
        }
        return scriptContent;
    }

    public void loadConfig() {
        if (configFile == null) initConfigFile();
        try {
            String content;
            if (!configFile.exists()) {
                content = readScriptContent(config);
            } else {
                content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            }
            JSONObject configJson = new JSONObject(content);

            this.pluginConfig.encryptScripts.clear();
            this.pluginConfig.decryptScripts.clear();
            try {
                this.pluginConfig.bindPort = configJson.getInt("bind_port");
            } catch (Exception e) {
                this.pluginConfig.bindPort = 9999;
            }
            try {
                this.pluginConfig.sseTimeout = configJson.getInt("sse_timeout");
            } catch (Exception e) {
                this.pluginConfig.sseTimeout = 10;
            }

            JSONArray encryptScriptsArray = configJson.optJSONArray("encrypt_scripts");
            if (encryptScriptsArray != null) {
                for (int i = 0; i < encryptScriptsArray.length(); i++) {
                    JSONObject scriptObj = encryptScriptsArray.getJSONObject(i);
                    String name = scriptObj.getString("name");
                    String scriptContent = scriptObj.getString("content");
                    pluginConfig.encryptScripts.put(name, scriptContent);
                }
            }

            JSONArray decryptScriptsArray = configJson.optJSONArray("decrypt_scripts");
            if (decryptScriptsArray != null) {
                for (int i = 0; i < decryptScriptsArray.length(); i++) {
                    JSONObject scriptObj = decryptScriptsArray.getJSONObject(i);
                    String name = scriptObj.getString("name");
                    String scriptContent = scriptObj.getString("content");
                    pluginConfig.decryptScripts.put(name, scriptContent);
                }
            }

            callbacks.printOutput("Loaded " + pluginConfig.encryptScripts.size() + " encrypt scripts and " +
                    pluginConfig.decryptScripts.size() + " decrypt scripts from config");
        } catch (IOException e) {
            callbacks.printError("Error reading config file: " + e.getMessage());
        } catch (Exception e) {
            callbacks.printError("Error parsing config file: " + e.getMessage());
        }
    }

    public void setBindPort(int port) {
        this.pluginConfig.bindPort = port;
    }

    public int getBindPort() {
        return this.pluginConfig.bindPort;
    }

    public int getSSETimeout() {
        return this.pluginConfig.sseTimeout;
    }

    public void setSSETimeout(int seconds) {
        this.pluginConfig.sseTimeout = seconds;
    }

    public Map<String, String> getDecryptScripts() {
        return this.pluginConfig.decryptScripts;
    }

    public Map<String, String> getEncryptScripts() {
        return this.pluginConfig.encryptScripts;
    }

    protected PluginConfig getPluginConfig() {
        return pluginConfig;
    }
}
