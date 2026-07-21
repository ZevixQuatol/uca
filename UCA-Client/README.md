# UCA-Client

`UCA-Client` 是 UCA 子业务系统共用的轻量客户端核心，封装注册、心跳、本地服务目录、服务间调用和受控透传。

Spring Boot Web 应用引入 `com.twlic.uca:uca-client-core:0.0.1` 后会自动装配全部 Client 能力，不需要额外 `@ComponentScan`、`@Import`、`Clock` 或 `RestClient.Builder` Bean；应用只需提供下面的实例配置。

## 配置

```yaml
uca:
  base:
    host: 127.0.0.1
    port: 20000
  client:
    host: "127.0.0.1:${server.port}"
    code: store
    name: 存储服务
    prefix: api/v1/store
    interval: 5s
    version: 1.0.0
    connect-timeout: 2s
    read-timeout: 5s
    signature-validity: 30s
    max-body-bytes: 1048576
    metadata:
      type: business-system
```

`UCA-Base` 在客户端注册时统一生成实例 ID；客户端只在当前 JVM 内保存，不再需要 `instance-id` 配置。`host` 是当前实例可被其他服务访问的地址，端口可直接引用 `server.port`。`interval` 同时驱动心跳和本地目录刷新，默认 5 秒。

`metadata` 完整保留给业务系统自定义；框架只额外使用保留键 `uca.api-prefix` 保存规范化后的 `prefix`。服务调用 secret 由 `UCA-Base` 在内存中生成，通过注册和心跳控制面响应下发，子服务仅保存在当前 JVM 内存中。

## 本地服务目录

客户端启动后从 Base 获取完整目录，并按 `interval` 刷新 JVM 内存快照。普通业务调用只读取本地目录，不在每次调用时请求 Base。

```java
ucaClient.services();
ucaClient.service("audit");
ucaClient.instances("audit");
ucaClient.discover("audit");
ucaClient.availableServiceNames();
```

`availableServiceNames()` 只返回至少存在一个 `ONLINE` 实例的应用编码，不返回实例 ID、地址或数量。

## Java 服务调用

被调用方用 `@UCAResponse` 明确开放接口：

```java
@UCAResponse
@PostMapping("/api/v1/events")
public EventResponse create(@RequestBody EventRequest request) {
    return eventService.create(request);
}
```

调用方统一使用 `UcaClient`：

```java
EventResponse response = ucaClient
        .post("audit", "/events")
        .body(request)
        .retrieve(EventResponse.class);
```

客户端根据服务名读取本地 `ONLINE` 实例并轮询选择，自动携带调用应用、实例、Request ID 和 HMAC 签名。`GET/HEAD` 连接失败时最多切换一个实例，写请求默认不重试。

## 前端透传入口

UCA-Client 自动开放 `@UCARequest` 统一入口，具体业务接口仍由 `@UCAResponse` 控制是否允许服务调用：

```http
GET /api/v1/uca/request/services
{METHOD} /api/v1/uca/request/{serviceName}/{relativePath}
```

示例：

```http
GET /api/v1/uca/request/client-b/demo/ping
```

如果 `client-b` 注册了 `prefix: api/v1/client-b`，客户端会把 `/demo/ping` 组合为实际目标路径 `/api/v1/client-b/demo/ping`。未配置前缀的服务仍按调用方传入的根相对路径访问。

服务列表只返回服务编码：

```json
{
  "code": 0,
  "error": null,
  "message": "SUCCESS",
  "data": ["client-b"],
  "requestId": "..."
}
```

## UCA 响应协议

UCA 调用使用 HTTP `200` 表示协议响应已返回，通过 `code` 表示实际结果：

```json
{
  "code": 10004,
  "error": "UCA_SERVICE_NOT_FOUND",
  "message": "Service 'client-b' does not exist",
  "data": null,
  "requestId": "..."
}
```

- `0`：成功；
- `10000-10999`：UCA 内置错误；
- `10004`：服务不存在；
- `10005`：服务存在但没有在线实例；
- `10006`：目标连接失败；
- `10007`：目标超时；
- `10008`：目标接口没有 `@UCAResponse`；
- `10009`：缺少服务身份；
- `10010`：服务签名无效；
- `10012`：目标响应不符合 UCA 协议。

当前透传只面向有大小上限的普通 JSON/Text REST 请求，不处理文件流、SSE 或 WebSocket。
