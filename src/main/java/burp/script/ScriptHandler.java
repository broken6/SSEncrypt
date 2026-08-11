package burp.script;

import burp.core.BurpSSEPlugin;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ScriptHandler {
    private final BurpSSEPlugin plugin;
    private final DefaultListModel<String> encryptScriptListModel;
    private final JList<String> encryptScriptList;
    private final DefaultListModel<String> decryptScriptListModel;
    private final JList<String> decryptScriptList;
    private final JTextField scriptNameField;
    private final RSyntaxTextArea scriptContentArea;
    private final JButton addScript;
    private boolean isEncryptListSelected = true; // 跟踪当前选中的是哪个列表

    public ScriptHandler(BurpSSEPlugin plugin,
                         DefaultListModel<String> encryptScriptListModel,
                         JList<String> encryptScriptList,
                         DefaultListModel<String> decryptScriptListModel,
                         JList<String> decryptScriptList,
                         JTextField scriptNameField,
                         JTextComponent scriptContentArea,
                         JButton addScript) {
        this.plugin = plugin;
        this.encryptScriptListModel = encryptScriptListModel;
        this.encryptScriptList = encryptScriptList;
        this.decryptScriptListModel = decryptScriptListModel;
        this.decryptScriptList = decryptScriptList;
        this.scriptNameField = scriptNameField;
        this.scriptContentArea = (RSyntaxTextArea) scriptContentArea;
        this.addScript = addScript;

        loadScriptsToList();
    }

    public void addScript() {
        String name = scriptNameField.getText().trim();
        String content = new String(scriptContentArea.getText());
        if (!name.isEmpty() && !content.isEmpty()) {
            if (isEncryptListSelected) {
                plugin.getEncryptScripts().put(name, content);
                if (!encryptScriptListModel.contains(name)) {
                    encryptScriptListModel.addElement(name);
                }
            } else {
                plugin.getDecryptScripts().put(name, content);
                if (!decryptScriptListModel.contains(name)) {
                    decryptScriptListModel.addElement(name);
                }
            }
            plugin.getCallbacks().printOutput("Script added: " + name + " to " +
                    (isEncryptListSelected ? "encrypt" : "decrypt") + " list");
            plugin.getConfigManager().saveConfig();
        }
    }

    public void deleteScript() {
        if (isEncryptListSelected) {
            String selected = encryptScriptList.getSelectedValue();
            if (selected != null) {
                plugin.getEncryptScripts().remove(selected);
                encryptScriptListModel.removeElement(selected);
                plugin.getCallbacks().printOutput("Encrypt script deleted: " + selected);
            }
        } else {
            String selected = decryptScriptList.getSelectedValue();
            if (selected != null) {
                plugin.getDecryptScripts().remove(selected);
                decryptScriptListModel.removeElement(selected);
                plugin.getCallbacks().printOutput("Decrypt script deleted: " + selected);
            }
        }
        scriptNameField.setText("");
        scriptContentArea.setText("");
        plugin.getConfigManager().saveConfig();
    }

    public void handleEncryptScriptSelection() {
        String selected = encryptScriptList.getSelectedValue();
        if (selected != null) {
            isEncryptListSelected = true;
            scriptNameField.setText(selected);
            scriptContentArea.setText(plugin.getEncryptScripts().get(selected));
            addScript.setText(encryptScriptListModel.contains(selected) ? "Update Encrypt" : "Add Encrypt");
        }
    }

    public void handleDecryptScriptSelection() {
        String selected = decryptScriptList.getSelectedValue();
        if (selected != null) {
            isEncryptListSelected = false;
            scriptNameField.setText(selected);
            scriptContentArea.setText(plugin.getDecryptScripts().get(selected));
            addScript.setText(decryptScriptListModel.contains(selected) ? "Update Decrypt" : "Add Decrypt");
        }
    }

    private void loadScriptsToList() {
        encryptScriptListModel.clear();
        for (String scriptName : plugin.getEncryptScripts().keySet()) {
            encryptScriptListModel.addElement(scriptName);
        }

        decryptScriptListModel.clear();
        for (String scriptName : plugin.getDecryptScripts().keySet()) {
            decryptScriptListModel.addElement(scriptName);
        }
    }

    public boolean contains(String scriptName) {
        return isEncryptListSelected ?
                encryptScriptListModel.contains(scriptName) :
                decryptScriptListModel.contains(scriptName);
    }

    public void setEncryptListSelected(boolean b) {
        if (isEncryptListSelected != b){
            isEncryptListSelected = b;
            this.scriptNameField.setText("");
            this.scriptContentArea.setText("");
            this.addScript.setText("Add");
        }

        if (b){
            this.decryptScriptList.clearSelection();
        }else{
            this.encryptScriptList.clearSelection();
        }

    }

    public boolean isEncryptListSelected() {
        return isEncryptListSelected;
    }
}
