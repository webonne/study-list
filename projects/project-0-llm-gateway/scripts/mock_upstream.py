#!/usr/bin/env python3
"""模拟 DeepSeek（OpenAI 兼容）的 /chat/completions，用来本地复现和验证问题。

不花钱、不依赖网络。按请求内容决定行为：
  stream=false              -> 返回一段完整 JSON，usage 里带缓存命中字段
  stream=true               -> 每 0.3s 吐一段，共 20 段（约 6s），然后 [DONE]
  stream=true 且消息含 STALL -> 吐 1 段后卡住 300s，不关连接

每个请求的开始、结束、是否被对端提前断开，都写进 /tmp/mock_upstream.log。

用法：
  python3 scripts/mock_upstream.py            # 监听 127.0.0.1:9099
  java -jar target/llm-gateway-*.jar --app.llm.base-url=http://127.0.0.1:9099
"""
import http.server
import json
import threading
import time

LOG_PATH = '/tmp/mock_upstream.log'
LOG = open(LOG_PATH, 'a', buffering=1)


def read_body(handler):
    # RestClient + JDK HttpClient 发的是 chunked，不一定有 Content-Length
    length = handler.headers.get('Content-Length')
    if length is not None:
        return handler.rfile.read(int(length))
    buf = b''
    while True:
        size = int(handler.rfile.readline().strip().split(b';')[0], 16)
        if size == 0:
            handler.rfile.readline()
            return buf
        buf += handler.rfile.read(size)
        handler.rfile.readline()


class Handler(http.server.BaseHTTPRequestHandler):
    # JDK HttpClient 需要 HTTP/1.1；BaseHTTPRequestHandler 默认 HTTP/1.0
    protocol_version = 'HTTP/1.1'

    def log_message(self, *args):
        pass

    def do_POST(self):
        body = json.loads(read_body(self) or b'{}')
        messages = body.get('messages') or []
        last = messages[-1].get('content', '') if messages else ''
        rid = f"{time.strftime('%H:%M:%S')}#{threading.get_ident() % 10000}"
        LOG.write(f"{rid} START stream={body.get('stream')} messages={len(messages)} msg={last[:20]!r}\n")
        if body.get('stream'):
            self._stream(rid, last)
        else:
            self._complete(rid, messages)

    def _complete(self, rid, messages):
        users = len([m for m in messages if m.get('role') == 'user'])
        payload = json.dumps({
            'id': 'chatcmpl-mock',
            'object': 'chat.completion',
            'choices': [{
                'index': 0,
                'message': {'role': 'assistant',
                            'content': f'[mock] 第 {users} 个用户问题，本次共收到 {len(messages)} 条消息'},
                'finish_reason': 'stop',
            }],
            'usage': {
                'prompt_tokens': 128, 'completion_tokens': 42, 'total_tokens': 170,
                'prompt_cache_hit_tokens': 96, 'prompt_cache_miss_tokens': 32,
            },
        }, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
        LOG.write(f"{rid} END non-stream\n")

    def _stream(self, rid, last):
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.end_headers()
        sent = 0
        try:
            if 'STALL' in last:
                self._chunk('卡')
                sent = 1
                LOG.write(f"{rid} STALLING after 1 chunk\n")
                time.sleep(300)
            else:
                for i in range(20):
                    self._chunk(f'字{i} ')
                    sent += 1
                    time.sleep(0.3)
                self.wfile.write(b'data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\n')
                self.wfile.write(b'data: [DONE]\n\n')
                self.wfile.flush()
            LOG.write(f"{rid} END sent={sent}\n")
        except (BrokenPipeError, ConnectionResetError) as e:
            # 上游视角：对端（我们的网关）提前关了连接 —— 这就是"停止生成"
            LOG.write(f"{rid} CLIENT_CLOSED after sent={sent} ({type(e).__name__})\n")

    def _chunk(self, text):
        data = {'choices': [{'delta': {'content': text}, 'finish_reason': None}]}
        self.wfile.write(f"data: {json.dumps(data, ensure_ascii=False)}\n\n".encode())
        self.wfile.flush()


if __name__ == '__main__':
    print(f'mock upstream on http://127.0.0.1:9099, log -> {LOG_PATH}')
    http.server.ThreadingHTTPServer(('127.0.0.1', 9099), Handler).serve_forever()
