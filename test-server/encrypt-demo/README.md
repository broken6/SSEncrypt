对于复杂请求包（签名）的构建，推荐使用插件[hackvertor](https://github.com/hackvertor/hackvertor)
配合自定义tag标签，可实现对请求包的灵活构造。

定义自定义tag标签，构造json参数 POST发送请求到SSEncrypt插件监听的/input接口
一切正常的将返回一个json对象，output即为加密/解密结果。

tag脚本如下:
- 脚本语言选择python （引擎默认jython 2.7.3b1）
- 参数1 字符串 url 127.0.0.1:8081
- 参数2 字符串 func enc/dec-函数名称

```python
import json
import httplib
funcType = func.split("-")[0]
funcName = func.split("-")[1]
data = {
	"input": input,
	"funcType": funcType,
	"funcName": funcName
}
hdr = {"content-type": "application/json"}

conn = httplib.HTTPConnection(url)
conn.request('POST','/input', json.dumps(data), hdr)
response = conn.getresponse()
result = json.loads(response.read())
output = result["output"]
```