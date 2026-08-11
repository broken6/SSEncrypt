// AES 加密配置
const SECRET_KEY = 'xAI_Grok3_2025_16bytes_key123456';
const IV = '1234567890abcdef';
const SIGNATURE_KEY = 'xAI_Signature_Key_2025';

// AES 加密函数
function encryptData(data) {
    const key = CryptoJS.enc.Utf8.parse(SECRET_KEY);
    const iv = CryptoJS.enc.Utf8.parse(IV);
    const encrypted = CryptoJS.AES.encrypt(data, key, {
        iv: iv,
        mode: CryptoJS.mode.CBC,
        padding: CryptoJS.pad.Pkcs7
    });
    return encrypted.toString();
}

// AES 解密函数
function decryptData(encrypted) {
    const key = CryptoJS.enc.Utf8.parse(SECRET_KEY);
    const iv = CryptoJS.enc.Utf8.parse(IV);
    const decrypted = CryptoJS.AES.decrypt(encrypted, key, {
        iv: iv,
        mode: CryptoJS.mode.CBC,
        padding: CryptoJS.pad.Pkcs7
    });
    return decrypted.toString(CryptoJS.enc.Utf8);
}

// 生成 HMAC-SHA256 签名
function generateSignature(data) {
    return CryptoJS.HmacSHA256(data, SIGNATURE_KEY).toString();
}