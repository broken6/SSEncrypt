# SSEncrypt 插件

> **原作者 / Original Author**: [freeAhao](https://github.com/freeAhao/SSEncrypt) · 原始仓库: <https://github.com/freeAhao/SSEncrypt>
> 本项目是基于原作者 [SSEncrypt](https://github.com/freeAhao/SSEncrypt) 的修改版。

**SSEncrypt 插件** 是一个 Burp Suite 扩展，旨在通过集成 Server-Sent Events (SSE) 和自定义加密/解密脚本增强 HTTP 请求和响应的处理能力。该插件为实时处理 HTTP 消息提供了灵活的框架，使安全研究人员和渗透测试人员能够自动化加密/解密流程、管理脚本并在 Burp Suite 中可视化解密后的数据。

该插件内置了一个 HTTP 服务器来处理 SSE 通信，支持动态脚本管理，并提供了一个用户友好的图形界面用于配置和管理规则。

```mermaid
sequenceDiagram
    participant User as 用户
    participant Burp as Burp插件(HTTP服务)
    participant Repeater as Burp Repeater/Proxy/Intruder
    participant BrowserExt as 浏览器插件
    participant WebPage as 目标网页JS环境

    User->>Repeater: 输入请求体中[[encrypt_password:123456]]
    Repeater->>Burp: 发送请求(包含加密调用标记)
    Burp->>Burp: 解析到加密脚本调用
    Burp->>BrowserExt: 通过SSE发送加密任务(encrypt_password, 123456)
    BrowserExt->>WebPage: 在页面环境执行对应加密JS逻辑
    WebPage-->>BrowserExt: 返回加密结果(例如 abcd1234)
    BrowserExt-->>Burp: 通过SSE返回结果
    Burp->>Repeater: 将[[encrypt_password:123456]]替换为abcd1234
    Repeater->>目标服务器: 发送最终请求
    Note over Burp, BrowserExt: 解密流程与加密类似，只是调用不同函数
```

## 功能

- **内置 HTTP 服务器**：在 Burp Suite 中运行一个 SSE 服务器，以动态处理 HTTP 消息。
- **TamperMonkey 脚本导出**：支持下载预配置的 TamperMonkey 脚本用于连接SSE服务器。
- **自定义加密/解密脚本**：允许用户定义和管理基于 JavaScript 的加密和解密脚本，存储在 JSON 配置文件（`SSEncrypt.json`）中。
- **上下文菜单集成**：通过右键菜单将加密脚本应用于选中的 HTTP 请求文本，并添加解密规则。
- **消息编辑器选项卡**：在专用选项卡中显示根据预定义规则解密的 HTTP 请求/响应内容。
- **动态规则解密**：自动解密匹配用户定义的 URL 路径和正则表达式的 HTTP 消息。
- **图形界面管理**：通过 Burp Suite 的 "SSEncrypt" 选项卡管理脚本、服务器设置和解密规则。
- **实时处理**：在 Repeater 和 Intruder 工具中处理 HTTP 请求中的嵌套加密标签（`[[scriptName:content]]`）。

## 要求

- **Burp Suite Professional 或 Community Edition**（已测试版本为 2023.x 及以上）。
- **Java 8 或更高版本**（与 Burp Suite 的 Jython/嵌入式 JVM 兼容）。
- 依赖项：
  - `org.json`（用于 JSON 处理，已包含在代码中）。
  - Burp Suite Extender API（由 Burp Suite 提供）。

## 安装

1. **编译插件**：
   - 克隆此仓库：
     ```bash
     git clone https://github.com/freeAhao/SSEncrypt.git
     ```
   - 编译
     ```bash
     mvn compile package
     ```
   - 编译成功将看到`target/SSEncrypt-1.0-SNAPSHOT.jar`

2. **加载到 Burp Suite**：
   - 打开 Burp Suite。
   - 导航到 **Extender** 选项卡 > **Extensions** > **Add**。
   - 将扩展类型设置为 **Java**，然后选择编译好的 `SSEncrypt-1.0-SNAPSHOT.jar`。
   - 确认插件加载成功（检查 Burp 中是否出现 "SSEncrypt" 选项卡）。

3. **配置文件**（可选）：
   - 插件首次运行时会在 JAR 文件所在目录创建 `SSEncrypt.json`。
   - 可预先在该文件中填充加密/解密脚本（参见 [配置](#配置)）。

## 使用方法

### 启动 SSE 服务器
1. 转到 Burp Suite 中的 **SSEncrypt** 选项卡。
2. 在 "Port" 字段中设置所需端口（默认：`8081`）。
3. 点击 **Start Server** 启动内置 HTTP 服务器。
   - 服务器将处理 `/sse`、`/input` 和 `/result` 端点以实现实时通信。

### 导出 TamperMonkey 脚本
- 在图形界面中点击 **TamperMonkey Script** 下载 `script.js`。
- 在浏览器的TamperMonkey插件中导入脚本。
- 前往目标页面，并通过TamperMonkey菜单连接SSE服务器。

### 管理脚本
1. 在 **SSEncrypt** 选项卡中，使用 "Encryption Scripts" 和 "Decryption Scripts" 列表查看现有脚本。
2. 添加新脚本：
   - 输入 **脚本名称**。
   - 在文本编辑器中编写脚本内容（JavaScript 格式）。
   - 点击 **Add**（若修改现有脚本则为 **Update**）。
3. 删除脚本：选中脚本后点击 **Delete**。
4. 脚本会自动保存到 `SSEncrypt.json`。

**脚本示例**：
```javascript
// 加密脚本
function encrypt(input) {
    return btoa(input); // Base64 编码
}
this.result(msg, encrypt(msg.input));
```

```javascript
// 解密脚本
function decrypt(input) {
    return atob(input); // Base64 解码
}
this.result(msg, decrypt(msg.input));
```

### 应用加密
1. 在 **Repeater** 或 **Proxy** 选项卡中，右键点击请求。
2. 从上下文菜单中选择 **Apply Encrypt Script** > [脚本名称]。
3. 选中的文本将被包裹为 `[[scriptName:selectedText]]`，并在发送时由 SSE 服务器处理。

### 添加解密规则
1. 在任何编辑器/查看器中右键点击请求/响应。
2. 选择 **Add Decrypt Rule**。
3. 在对话框中：
   - 查看消息内容（只读）。
   - 调整正则表达式（基于选中文本自动生成）。
   - 从下拉列表中选择解密脚本。
   - 点击 **OK** 保存规则。
4. 规则将显示在 **SSEncrypt** 选项卡的解密表中。

### 查看解密数据
- 当请求/响应匹配解密规则时， **Decrypted** 选项卡将显示解密结果。
- 当请求/响应不匹配解密规则时， **Decrypted** 显示No Match Rule。

## 配置

插件将脚本存储在 `SSEncrypt.json` 中。示例结构：
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

## 端点

- **/sse**：流式传输 SSE 消息以实现实时更新，与浏览器油猴脚本建立连接。
- **/result**：浏览油猴脚本通过 POST 请求发送，接收处理结果。
- **/input**：接受带有 `input` 和 `script` 的 JSON POST 请求进行处理。

## 故障排除

- **服务器无法启动**：检查端口是否被占用（`netstat -an | grep 8081`）及防火墙设置。
- **脚本未加载**：确保 `SSEncrypt.json` 与 JAR 文件在同一目录且为有效的 JSON 格式。
- **解密失败**：确认正则表达式正确且脚本返回有效输出。查看 Burp 的 **Output** 选项卡以获取错误信息。

## 贡献

1. Fork 此仓库。
2. 创建功能分支（`git checkout -b feature/new-feature`）。
3. 提交更改（`git commit -m "添加新功能"`）。
4. 推送分支（`git push origin feature/new-feature`）。
5. 提交 Pull Request。

## 许可证

本项目采用 MIT 许可证 - 详情见 [LICENSE](LICENSE) 文件。

## 致谢

- 基于 Burp Suite Extender API 构建。
- 灵感来源于安全测试中对灵活、实时 HTTP 消息处理的需求。

