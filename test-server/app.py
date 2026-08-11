from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
import json

class RequestHandler(BaseHTTPRequestHandler):
    def _handle_request(self):
        # 解析 URL 和 query string 参数
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)

        # 获取 POST body 数据（如果有）
        body_params = {}
        content_length = int(self.headers.get('Content-Length', 0))
        if content_length > 0:
            post_data = self.rfile.read(content_length).decode('utf-8')
            try:
                body_params = json.loads(post_data)  # 尝试解析 JSON
            except json.JSONDecodeError:
                body_params = parse_qs(post_data)  # 解析表单格式

        # 准备输出内容
        result = f"Path: {parsed_path.path}\n\n"

        # 打印和收集 URL 参数（query string）
        print(f"Path: {parsed_path.path}")
        print("URL Parameters (Query String):")
        result += "URL Parameters (Query String):\n"
        if query_params:
            for key, values in query_params.items():
                print(f"  {key}: {', '.join(values)}")
                result += f"  {key}: {', '.join(values)}\n"
        else:
            print("  None")
            result += "  None\n"

        # 打印和收集 body 参数
        print("Body Parameters:")
        result += "\nBody Parameters:\n"
        if body_params:
            for key, values in body_params.items():
                if isinstance(values, list):
                    print(f"  {key}: {', '.join(values)}")
                    result += f"  {key}: {', '.join(values)}\n"
                else:
                    print(f"  {key}: {values}")
                    result += f"  {key}: {values}\n"
        else:
            print("  None")
            result += "  None\n"

        # 打印和收集请求头
        print("Request Headers:")
        result += "\nRequest Headers:\n"
        for header, value in self.headers.items():
            print(f"  {header}: {value}")
            result += f"  {header}: {value}\n"

        # 发送响应
        self.send_response(200)
        self.send_header("Content-type", "text/plain")
        self.end_headers()
        response = f"Request received (Method: {self.command})\n\n{result}"
        self.wfile.write(bytes(response, 'utf-8'))

    def do_GET(self):
        self._handle_request()

    def do_POST(self):
        self._handle_request()

if __name__ == "__main__":
    server_address = ('', 5000)
    httpd = HTTPServer(server_address, RequestHandler)
    print("Serving on port 5000...")
    httpd.serve_forever()
