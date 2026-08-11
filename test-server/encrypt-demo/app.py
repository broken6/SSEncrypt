from flask import Flask, request, render_template, jsonify
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
import base64
import json
import os
import hmac
import hashlib

app = Flask(__name__)

# AES 加密密钥（必须是 16、24 或 32 字节）
SECRET_KEY = b'xAI_Grok3_2025_16bytes_key123456'  # 32 字节密钥
IV = b'1234567890abcdef'  # 初始化向量，16 字节
SIGNATURE_KEY = b'xAI_Signature_Key_2025'  # 用于签名的密钥

# 硬编码账户口令
VALID_USERNAME = "admin"
VALID_PASSWORD = "xai2025"

# 模拟数据
MOCK_DATA = {
    "id": 1,
    "name": "Test User",
    "email": "test@example.com",
    "status": "active"
}

# AES 加密函数
def encrypt_data(data, key=SECRET_KEY, iv=IV):
    cipher = AES.new(key, AES.MODE_CBC, iv)
    padded_data = pad(data.encode(), AES.block_size)
    encrypted_data = cipher.encrypt(padded_data)
    return base64.b64encode(encrypted_data).decode('utf-8')

# AES 解密函数
def decrypt_data(encrypted_data, key=SECRET_KEY, iv=IV):
    cipher = AES.new(key, AES.MODE_CBC, iv)
    encrypted_bytes = base64.b64decode(encrypted_data)
    decrypted_padded = cipher.decrypt(encrypted_bytes)
    return unpad(decrypted_padded, AES.block_size).decode('utf-8')

# 生成签名
def generate_signature(data, key=SIGNATURE_KEY):
    return hmac.new(key, data.encode('utf-8'), hashlib.sha256).hexdigest()

# 验证签名
def verify_signature(data, signature, key=SIGNATURE_KEY):
    expected_signature = generate_signature(data, key)
    return hmac.compare_digest(expected_signature, signature)

# 前端页面路由
@app.route('/')
def index():
    return render_template('index.html')

# 登录页面路由
@app.route('/login')
def login_page():
    return render_template('login.html')

# 登录接口
@app.route('/login', methods=['POST'])
def login():
    try:
        # 获取签名
        signature = request.headers.get('Sign')
        if not signature:
            return jsonify({"error": "Missing signature"}), 401

        # 获取加密的请求体
        encrypted_body = request.data.decode('utf-8')

        # 验证签名
        if not verify_signature(encrypted_body, signature):
            return jsonify({"error": "Invalid signature"}), 401

        decrypted_body = decrypt_data(encrypted_body)
        request_json = json.loads(decrypted_body)
        encrypted_data = request_json.get('data')
        decrypted_data = decrypt_data(encrypted_data)
        login_data = json.loads(decrypted_data)

        username = login_data.get('username')
        password = login_data.get('password')

        if username == VALID_USERNAME and password == VALID_PASSWORD:
            response = {"status": "success", "message": "登录成功"}
        else:
            response = {"status": "error", "message": "用户名或密码错误"}

        response_json = json.dumps(response)
        encrypted_response = encrypt_data(response_json)

        # 对整个响应体再次加密
        response_body = json.dumps({"data": encrypted_response})
        encrypted_body = encrypt_data(response_body)

        return encrypted_body
    except Exception as e:
        return jsonify({"error": str(e)}), 400

# 原有查询接口保持不变
@app.route('/query', methods=['POST'])
def query_data():
    try:
        encrypted_body = request.data.decode('utf-8')
        decrypted_body = decrypt_data(encrypted_body)
        request_json = json.loads(decrypted_body)
        
        encrypted_param = request_json.get('data')
        decrypted_param = decrypt_data(encrypted_param)
        param_json = json.loads(decrypted_param)
        
        response_data = MOCK_DATA
        response_data["name"] = param_json["query"]
        
        response_json = json.dumps(response_data)
        encrypted_response = encrypt_data(response_json)
        
        response_body = json.dumps({"data": encrypted_response})
        encrypted_body = encrypt_data(response_body)
        
        return encrypted_body
    except Exception as e:
        return jsonify({"error": str(e)}), 400

# 新增带签名验证的查询接口
@app.route('/query_with_sign', methods=['POST'])
def query_data_with_sign():
    try:
        # 获取签名
        signature = request.headers.get('Sign')
        if not signature:
            return jsonify({"error": "Missing signature"}), 401
        
        # 获取加密的请求体
        encrypted_body = request.data.decode('utf-8')
        
        # 验证签名
        if not verify_signature(encrypted_body, signature):
            return jsonify({"error": "Invalid signature"}), 401
        
        # 解密请求体
        decrypted_body = decrypt_data(encrypted_body)
        request_json = json.loads(decrypted_body)
        # 获取加密的参数并解密
        encrypted_param = request_json.get('data')
        decrypted_param = decrypt_data(encrypted_param)
        param_json = json.loads(decrypted_param)
        # 模拟查询逻辑
        response_data = MOCK_DATA
        response_data["name"] = param_json["query"]
        # 将响应数据转为 JSON 并加密
        response_json = json.dumps(response_data)
        encrypted_response = encrypt_data(response_json)
        
        # 对整个响应体再次加密
        response_body = json.dumps({"data": encrypted_response})
        encrypted_body = encrypt_data(response_body)
        
        return encrypted_body
    except Exception as e:
        return jsonify({"error": str(e)}), 400

if __name__ == '__main__':
    app.run(debug=True)