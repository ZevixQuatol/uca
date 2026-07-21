# UCA: Unified Composeable Architecture

中文名称：统一组合框架

## 项目介绍

UCA 是一套面向 Spring Boot 子业务系统的轻量级统一组合框架，负责服务注册与发现、客户端本地服务目录、实例负载均衡、服务间认证以及 HTTP REST 通讯。

UCA 不绑定组织、角色、人员、权限、菜单等具体业务域。业务系统只需要引入公共依赖，即可作为独立服务加入 UCA；具体业务能力仍由各业务系统自行实现和组合。

当前仓库包含两个可复用模块：

| 模块 | Maven 坐标 | 作用 |
| --- | --- | --- |
| `UCA-Base` | `com.twlic.uca:uca-base:0.0.1` | 内存实例注册表、心跳与下线检测、服务目录、共享 secret 轮换和 Dashboard |
| `UCA-Client` | `com.twlic.uca:uca-client-core:0.0.1` | 自动注册、心跳、目录同步、本地轮询、服务签名、直接调用和统一透传 |

技术基线：

- JDK 21
- Spring Boot 4.1.0
- Maven
- Apache License 2.0

## 架构逻辑

```text
                        注册、心跳、目录同步
┌─────────────────┐  ──────────────────────>  ┌─────────────────┐
│  Spring Boot A  │                            │    UCA-Base     │
│  + UCA-Client   │  <──────────────────────  │ 实例目录/secret │
└────────┬────────┘        控制面响应           └─────────────────┘
         │
         │ 本地解析服务名
         │ 本地轮询 ONLINE 实例
         │ HMAC 签名后 HTTP REST 直连
         ▼
┌─────────────────┐
│  Spring Boot B  │
│  + UCA-Client   │
│  @UCAResponse   │
└─────────────────┘
```

运行逻辑：

1. 子服务启动后向 Base 注册，Base 统一生成实例 ID，并通过注册控制面下发当前共享 secret。
2. Client 默认每 5 秒发送心跳并同步服务目录，在线实例保存在当前 JVM 内存中。
3. 调用方按服务名读取本地目录并轮询选择一个 `ONLINE` 实例，普通业务调用不会在调用时再次请求 Base。
4. Client 根据目标实例地址、可选 URL 前缀和业务相对路径拼接目标 URL，自动携带调用方、实例、Request ID 和 HMAC 签名。
5. 被调用方只有标记 `@UCAResponse` 的接口允许其他已认证 UCA 服务访问；没有有效服务身份的直接请求会被拒绝。
6. Base 只处理注册、发现和治理控制流量，不代理普通业务流量。

前端需要动态调用其他服务时，可以使用 Client 自动提供的统一透传入口：

```http
GET /api/v1/uca/request/services
{METHOD} /api/v1/uca/request/{serviceName}/{relativePath}
```

服务列表只返回当前存在在线实例的服务名，不暴露实例 ID 和地址。UCA 协议通过响应体 `code` 表示业务结果，`10000-10999` 为框架内置错误码，例如 `10004 UCA_SERVICE_NOT_FOUND`；HTTP 状态码仍表示 HTTP 协议本身的处理结果。

## 快速开始

### 1. 构建并安装到本地 Maven 仓库

```powershell
mvn -B clean install
```

当前仓库尚未发布到远程 Maven 仓库，因此其他本机项目需要先执行上述命令。

### 2. 启动 UCA-Base

构建后直接运行独立可执行 JAR：

```powershell
java -jar .\UCA-Base\target\uca-base-0.0.1-exec.jar
```

默认地址：

- Base：`http://127.0.0.1:20000`
- Dashboard：`http://127.0.0.1:20000/dashboard`
- Health：`http://127.0.0.1:20000/actuator/health`

也可以在另一个 Spring Boot Web 应用中直接引入 Base：

```xml
<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-base</artifactId>
    <version>0.0.1</version>
</dependency>
```

Base 能力会通过 Spring Boot AutoConfiguration 自动启用，不需要额外配置 `@ComponentScan` 或 `@Import`。

### 3. 在业务服务中引入 UCA-Client

```xml
<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-client-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

添加最小配置：

```yaml
server:
  port: 20101

uca:
  base:
    host: 127.0.0.1
    port: 20000
  client:
    host: "127.0.0.1:${server.port}"
    code: order-service
    name: 订单服务
    prefix: api/v1/order
    interval: 5s
    version: 0.0.1
    metadata:
      type: business-system
```

`instance-id` 和 `secret` 不需要配置：实例 ID 由 Base 注册时生成，secret 由 Base 在内存中生成、轮换并通过控制面下发。

### 4. 开放服务接口

```java
import com.twlic.uca.client.core.UCAResponse;

@UCAResponse
@GetMapping("/demo/ping")
public Map<String, String> ping() {
    return Map.of("message", "pong");
}
```

如果 Controller 的公共路径为 `/api/v1/order`，其他服务只需关注业务相对路径。

### 5. 调用其他服务

```java
import com.twlic.uca.client.core.UcaClient;

Map<?, ?> response = ucaClient
        .get("order-service", "/demo/ping")
        .retrieve(Map.class);
```

Client 会在本地选择 `order-service` 的在线实例，并将注册前缀和相对路径组合为：

```text
/api/v1/order/demo/ping
```

前端通过当前服务透传时使用：

```http
GET /api/v1/uca/request/order-service/demo/ping
```

项目地址：[github.com/ZevixQuatol/uca](https://github.com/ZevixQuatol/uca)

许可证：[Apache License 2.0](LICENSE)
