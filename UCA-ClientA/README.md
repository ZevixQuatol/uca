# UCA-ClientA（子业务系统 A）

`UCA-ClientA` 是 UCA 组合业务架构的子业务系统示例。它演示：

- 启动后向 `UCA-Base` 注册；
- 周期性发送心跳；
- `UCA-Base` 忘记实例后自动重新注册；
- 正常停止时主动注销；
- 通过 `UCA-Base` 发现 `client-b`；
- 获取 B 的一个在线实例后，直接通过 HTTP REST 调用 B；
- `UCA-Base` 不参与业务请求转发。

## 技术栈

```text
Spring Boot 4.1.0
JDK 21
Maven
Spring MVC RestClient
Spring Boot Actuator
```

项目不包含数据库、Redis、Spring Security、Spring Cloud 或前端页面。

## 默认配置

```text
应用编码：client-a
实例标识：client-a-1
监听端口：48101
注册地址：http://127.0.0.1:48101
UCA-Base：http://127.0.0.1:48080
心跳周期：10s
目标应用：client-b
```

环境变量：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `UCA_CLIENT_A_PORT` | A 的监听端口 | `48101` |
| `UCA_CLIENT_A_INSTANCE_ID` | A 的实例标识 | `client-a-1` |
| `UCA_CLIENT_A_ADVERTISED_BASE_URL` | 向 Base 注册的可访问地址 | `http://127.0.0.1:48101` |
| `UCA_BASE_URL` | UCA-Base 地址 | `http://127.0.0.1:48080` |
| `UCA_CLIENT_A_HEARTBEAT_INTERVAL` | 心跳周期 | `10s` |

如果修改监听端口，也要同步修改 `UCA_CLIENT_A_ADVERTISED_BASE_URL`，确保 B 能访问注册的地址。

## 构建和启动

```powershell
mvn clean verify
java -jar .\target\uca-client-a-0.0.1.jar
```

应先启动 `UCA-Base`，再启动 ClientA 和 ClientB。Base 暂时不可用时 ClientA 仍可启动，并会在后续心跳周期重试注册。

## API

### A 自身 Ping

```http
GET /api/v1/client-a/demo/ping
```

### A 发现并调用 B

```http
GET /api/v1/client-a/demo/call-b
```

调用链：

```text
调用方
  -> ClientA /api/v1/client-a/demo/call-b
  -> UCA-Base /api/v1/applications/client-b/instances/next
  <- 返回一个 B 实例的 baseUrl
  -> ClientB {baseUrl}/api/v1/client-b/demo/ping
  <- B 的 Ping 响应
  <- ClientA 返回包含发现信息和 B 响应的结果
```

错误码：

```text
TARGET_UNAVAILABLE
UCA_BASE_UNAVAILABLE
UCA_BASE_CALL_FAILED
TARGET_CALL_FAILED
```

## 手工请求

使用 [requests.http](requests.http) 调用健康检查、Ping 和 A→B 示例。
