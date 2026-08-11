# SSEncrypt Plugin

[中文](README-CN.md)

> **Original Author / 原作者**: [freeAhao](https://github.com/freeAhao/SSEncrypt) · Original repository / 原始仓库: <https://github.com/freeAhao/SSEncrypt>
> This project is a maintained fork of the original [SSEncrypt](https://github.com/freeAhao/SSEncrypt). / 本项目基于原 [SSEncrypt](https://github.com/freeAhao/SSEncrypt) 的修改版。

**SSEncrypt Plugin** is a Burp Suite extension designed to enhance HTTP request and response manipulation by integrating Server-Sent Events (SSE) and custom encryption/decryption scripts. This plugin provides a flexible framework for real-time HTTP message processing, enabling security researchers and penetration testers to automate encryption/decryption workflows, manage scripts, and visualize decrypted data within Burp Suite.

The plugin includes an embedded HTTP server to handle SSE communication, supports dynamic script management, and offers a user-friendly GUI for configuration and rule management.

## Features

- **Embedded HTTP Server**: Runs an SSE server within Burp Suite to dynamically process HTTP messages.
- **TamperMonkey Script Export**: Allows downloading a pre-configured TamperMonkey script to connect to the SSE server from a browser.
- **Custom Encryption/Decryption Scripts**: Enables users to define and manage JavaScript-based encryption and decryption scripts, stored in a JSON configuration file (`SSEncrypt.json`).
- **Context Menu Integration**: Apply encryption scripts to selected HTTP request text and add decryption rules directly from the context menu.
- **Message Editor Tab**: Displays decrypted HTTP request/response content in a dedicated tab based on predefined rules.
- **Dynamic Rule-Based Decryption**: Automatically decrypts HTTP messages matching user-defined URL paths and regex patterns.
- **GUI Management**: Manage scripts, server settings, and decryption rules via the "SSEncrypt" tab in Burp Suite.
- **Real-Time Processing**: Processes nested encryption tags (`[[scriptName:content]]`) in HTTP requests within Repeater and Intruder tools.

## Requirements

- **Burp Suite Professional or Community Edition** (tested with version 2023.x and later).
- **Java 8 or higher** (compatible with Burp Suite's Jython/embedded JVM).
- Dependencies:
  - `org.json` (for JSON handling, included in the code).
  - Burp Suite Extender API (provided by Burp Suite).

## Installation

1. **Compile the Plugin**:
   - Clone this repository:
     ```bash
     git clone https://github.com/freeAhao/SSEncrypt.git
     ```
   - Compile the project using Maven:
     ```bash
     mvn compile package
     ```
   - If compile successful, you should find the compiled JAR file: `target/SSEncrypt-1.0-SNAPSHOT.jar`

2. **Load into Burp Suite**:
   - Open Burp Suite.
   - Navigate to **Extender** tab > **Extensions** > **Add**.
   - Set the extension type to **Java** and select the compiled `burp-sse-plugin-1.0-SNAPSHOT.jar`.
   - Confirm the plugin loads successfully (check for the "SSEncrypt" tab in Burp).

3. **Configuration File** (optional):
   - The plugin creates `SSEncrypt.json` in the same directory as the JAR file on first run.
   - Pre-populate this file with encryption/decryption scripts if desired (see [Configuration](#configuration)).

## Usage

### Starting the SSEncrypt
1. Go to the **SSEncrypt** tab in Burp Suite.
2. Set the desired port (default: `8081`) in the "Port" field.
3. Click **Start Server** to launch the embedded HTTP server.
   - The server handles `/sse`, `/input`, and `/result` endpoints for real-time communication.

### Exporting TamperMonkey Script
- Click **TamperMonkey Script** in the GUI to download `script.js`.
- Import the script into the TamperMonkey extension in your browser.
- Visit the target page and connect to the SSE server via the TamperMonkey menu.

### Managing Scripts
1. In the **SSEncrypt** tab, use the "Encryption Scripts" and "Decryption Scripts" lists to view existing scripts.
2. Add a new script:
   - Enter a **Script Name**.
   - Write the script content in the text editor (JavaScript format).
   - Click **Add** (or **Update** if modifying an existing script).
3. Delete a script by selecting it and clicking **Delete**.
4. Scripts are saved to `SSEncrypt.json` automatically.

**Script Examples**:
```javascript
// Encryption script
function encrypt(input) {
    return btoa(input); // Base64 encode
}
this.result(msg, encrypt(msg.input));
```

```javascript
// Decryption script
function decrypt(input) {
    return atob(input); // Base64 decode
}
this.result(msg, decrypt(msg.input));
```

### Applying Encryption
1. In the **Repeater** or **Proxy** tab, right-click on a request.
2. Select **Apply Encrypt Script** > [Script Name] from the context menu.
3. The selected text will be wrapped in `[[scriptName:selectedText]]` and processed by the SSE server when sent.

### Adding Decryption Rules
1. Right-click on a request/response in any editor/viewer.
2. Choose **Add Decrypt Rule**.
3. In the dialog:
   - View the message content (read-only).
   - Adjust the regex pattern (auto-generated based on selection).
   - Select a decryption script from the dropdown.
   - Click **OK** to save the rule.
4. Rules appear in the decryption table in the **SSEncrypt** tab.

### Viewing Decrypted Data
- When a request/response matches a decryption rule, the **Decrypted** tab displays the decryption result.
- If no rule matches, the **Decrypted** tab shows "No Match Rule".

## Configuration

The plugin stores scripts in `SSEncrypt.json`. Example structure:
```json
{
  "encrypt_scripts": [
    {"name": "base64_enc", "content": "function encrypt(input) { return btoa(input); } this.result(msg, encrypt(msg.input));"}
  ],
  "decrypt_scripts": [
    {"name": "base64_dec", "content": "function decrypt(input) { return atob(input); } this.result(msg, decrypt(msg.input));"}
  ]
}
```

## Endpoints

- **/sse**: Streams SSE messages to establish a connection with the browser TamperMonkey script.
- **/result**: Receives processing results sent by the TamperMonkey script via POST requests.
- **/input**: Accepts POST requests with JSON containing `input` and `script` for processing.

## Troubleshooting

- **Server Won’t Start**: Check port availability (`netstat -an | grep 8081`) and firewall settings.
- **Scripts Not Loading**: Verify `SSEncrypt.json` is in the same directory as the JAR and is valid JSON.
- **Decryption Fails**: Ensure regex patterns are correct and scripts return valid output. Check Burp’s **Output** tab for errors.

## Contributing

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/new-feature`).
3. Commit changes (`git commit -m "Add new feature"`).
4. Push to the branch (`git push origin feature/new-feature`).
5. Open a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with the Burp Suite Extender API.
- Inspired by the need for flexible, real-time HTTP message processing in security testing.

