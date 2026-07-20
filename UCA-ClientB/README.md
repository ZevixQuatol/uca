# UCA-ClientB（子业务系统 B）

`UCA-ClientB` 是 UCA 组合业务架构的子业务系统示例。它演示：

- 启动后向 `UCA-Base` 注册；
- 周期性发送心跳；
- `UCA-Base` 忘记实例后自动重新注册；
- 正常停止时主动注销；
- 通过 `UCA-Base` 发现 `client-a`；
- 获取 A 的一个在线实例后，直接通过 HTTP REST 调用 A；
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
应用编码：client-b
实例标识：client-b-1
监听端口：48102
注册地址：http://127.0.0.1:48102
UCA-Base：http://127.0.0.1:48080
心跳周期：10s
目标应用：client-a
```

环境变量：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `UCA_CLIENT_B_PORT` | B 的监听端口 | `48102` |
| `UCA_CLIENT_B_INSTANCE_ID` | B 的实例标识 | `client-b-1` |
| `UCA_CLIENT_B_ADVERTISED_BASE_URL` | 向 Base 注册的可访问地址 | `http://127.0.0.1:48102` |
| `UCA_BASE_URL` | UCA-Base 地址 | `http://127.0.0.1:48080` |
| `UCA_CLIENT_B_HEARTBEAT_INTERVAL` | 心跳周期 | `10s` |

如果修改监听端口，也要同步修改 `UCA_CLIENT_B_ADVERTISED_BASE_URL`，确保 A 能访问注册的地址。

## 构建和启动

```powershell
mvn clean verify
java -jar .\target\uca-client-b-0.0.1.jar
```

应先启动 `UCA-Base`，再启动 ClientB 和 ClientA。Base 暂时不可用时 ClientB 仍可启动，并会在后续心跳周期重试注册。

## API

### B 自身 Ping

```http
GET /api/v1/client-b/demo/ping
```

### B 发现并调用 A

```http
GET /api/v1/client-b/demo/call-a
```

调用链：

```text
调用方
  -> ClientB /api/v1/client-b/demo/call-a
  -> UCA-Base /api/v1/applications/client-a/instances/next
  <- 返回一个 A 实例的 baseUrl
  -> ClientA {baseUrl}/api/v1/client-a/demo/ping
  <- A 的 Ping 响应
  <- ClientB 返回包含发现信息和 A 响应的结果
```

错误码：

```text
TARGET_UNAVAILABLE
UCA_BASE_UNAVAILABLE
UCA_BASE_CALL_FAILED
TARGET_CALL_FAILED
```

## 手工请求

使用 [requests.http](requests.http) 调用健康检查、Ping 和 B→A 示例。
