#!/usr/bin/env python3
"""模拟 DeepSeek（OpenAI 兼容）的 /chat/completions，用来本地复现和验证问题。

不花钱、不依赖网络。按请求内容决定行为：
  stream=false              -> 返回一段完整 JSON，usage 里带缓存命中字段
  stream=true               -> 每 0.3s 吐一段，共 20 段（约 6s），然后 [DONE]
  stream=true 且消息含 STALL -> 吐 1 段后卡住 300s，不关连接

#04 结构化输出（请求里是工单抽取的提示词时）：
  带 response_format=json_object -> 返回纯 JSON 工单
  不带 response_format           -> 返回"好的，以下是结果：```json ...```"（模拟不开 JSON 模式时的常见输出）
  用户消息里的故障注入标记：
    MOCK_EMPTY_ONCE  第一次返回空内容，之后正常
    MOCK_TRUNC       返回截断的 JSON，finish_reason=length
    MOCK_BADENUM     severity 给 "紧急"；收到修复请求后改对
    MOCK_ALWAYS_BAD  永远返回非 JSON 文本

每个请求的开始、结束、是否被对端提前断开，都写进 /tmp/mock_upstream.log。

用法：
  python3 scripts/mock_upstream.py            # 监听 127.0.0.1:9099
  java -jar target/llm-gateway-*.jar --app.llm.base-url=http://127.0.0.1:9099
"""
import http.server
import json
import threading
import time
from collections import Counter

SEEN = Counter()
TICKET = {
    'title': '下单接口超时',
    'severity': 'P1',
    'component': 'order-service',
    'summary': '晚高峰三成用户下不了单',
    'steps': ['查看 order-service 错误日志', '检查数据库连接池'],
}

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
        elif messages and '工单信息抽取器' in (messages[0].get('content') or ''):
            self._extract(rid, body, messages)
        else:
            self._complete(rid, messages)

    def _extract(self, rid, body, messages):
        json_mode = (body.get('response_format') or {}).get('type') == 'json_object'
        user_text = ' '.join(m.get('content', '') for m in messages if m.get('role') == 'user')
        is_repair = '上一次的输出' in (messages[-1].get('content') or '')
        finish = 'stop'
        if 'MOCK_EMPTY_ONCE' in user_text:
            SEEN['empty'] += 1
            content = '' if SEEN['empty'] == 1 else json.dumps(TICKET, ensure_ascii=False)
        elif 'MOCK_TRUNC' in user_text:
            content, finish = json.dumps(TICKET, ensure_ascii=False)[:40], 'length'
        elif 'MOCK_BADENUM' in user_text and not is_repair:
            content = json.dumps(dict(TICKET, severity='紧急'), ensure_ascii=False)
        elif 'MOCK_ALWAYS_BAD' in user_text:
            content = '这个问题比较严重，建议先重启服务再观察。'
        elif json_mode:
            content = json.dumps(TICKET, ensure_ascii=False)
        else:
            content = '好的，以下是抽取结果：\n```json\n' + json.dumps(TICKET, ensure_ascii=False) + '\n```'
        LOG.write(f"{rid} EXTRACT json_mode={json_mode} repair={is_repair} finish={finish} len={len(content)}\n")
        self._send_json({
            'id': 'chatcmpl-mock', 'object': 'chat.completion',
            'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': content},
                         'finish_reason': finish}],
            'usage': {'prompt_tokens': 300, 'completion_tokens': 80, 'total_tokens': 380,
                      'prompt_cache_hit_tokens': 256, 'prompt_cache_miss_tokens': 44},
        })

    def _send_json(self, obj):
        payload = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

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
