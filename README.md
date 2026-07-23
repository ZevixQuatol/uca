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
5. `uca.client.mode=partial` 时，只有标记 `@UCAResponse` 的接口允许其他已认证 UCA 服务访问；`full` 时，已有业务接口无需修改代码即可接受 UCA 服务调用。
6. Base 只处理注册、发现和治理控制流量，不代理普通业务流量。

前端需要动态调用其他服务时，可以使用 Client 自动提供的统一透传入口：

```http
GET /api/v1/uca/request/services
{METHOD} /api/v1/uca/request/{serviceName}/{relativePath}
```

服务列表只返回当前存在在线实例的服务名，不暴露实例 ID 和地址。UCA 协议通过响应体 `code` 表示业务结果，`10000-10999` 为框架内置错误码，例如 `10004 UCA_SERVICE_NOT_FOUND`；HTTP 状态码仍表示 HTTP 协议本身的处理结果。

## 快速开始

### 1. 拉取并安装 UCA

```powershell
git clone https://github.com/ZevixQuatol/uca.git
cd uca
mvn -B clean install
```

当前版本尚未发布到远程 Maven 仓库，`mvn clean install` 会把父 POM、`uca-base` 和 `uca-client-core` 安装到本机 Maven 仓库。

### 2. 创建 Spring Boot 项目

使用 [Spring Initializr](https://start.spring.io/) 创建项目：

- Project：Maven
- Language：Java
- Spring Boot：4.1.0
- Java：21
- Dependency：Spring Web / Spring Web MVC

项目包名和启动类位置不需要与 `com.twlic.uca` 相同。UCA 通过 Spring Boot AutoConfiguration 加载，不需要额外添加 `@ComponentScan` 或 `@Import`。

### 3. 选择接入方式

#### 方式一：只引入 UCA-Base

适用于独立注册中心，或者需要在现有 Spring Boot 应用中承载 UCA 控制面的场景。

```xml
<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-base</artifactId>
    <version>0.0.1</version>
</dependency>
```

`application.yml`：

```yaml
spring:
  application:
    name: uca-base

server:
  port: 20000

uca:
  base:
    heartbeat-timeout: 30s
    offline-retention: 2m
    scan-interval: 5s
    secret-interval: 10m
```

启动后自动获得实例注册、心跳、服务目录、secret 轮换和 Dashboard，不需要编写 Base 启动代码。

#### 方式二：只引入 UCA-Client

适用于普通业务服务。该方式要求已经存在一个可访问的 UCA-Base。

```xml
<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-client-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

`application.yml`：

```yaml
spring:
  application:
    name: order-service

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
    mode: partial
    prefix: api/v1/order
    interval: 5s
    version: 0.0.1
    metadata:
      type: business-system
```

`mode` 控制服务接口开放范围：

- `partial`：默认值，只有 `@UCAResponse` 标记的接口接受 UCA 服务调用；
- `full`：所有现有 Spring MVC 接口都接受经过 UCA 签名认证的服务调用，适合已有项目快速接入。

`full` 不会匿名开放接口，也不会改变普通前端请求的原状态码和响应体。实例 ID 和 secret 均不需要配置：实例 ID 由 Base 生成，secret 由 Base 在内存中轮换并通过注册、心跳控制面下发。

#### 方式三：同时引入 UCA-Base 和 UCA-Client

适用于同一个 Spring Boot 应用既提供 UCA 控制面，又作为普通业务服务注册和被调用的场景。

```xml
<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-base</artifactId>
    <version>0.0.1</version>
</dependency>

<dependency>
    <groupId>com.twlic.uca</groupId>
    <artifactId>uca-client-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

`application.yml`：

```yaml
spring:
  application:
    name: uca-admin

server:
  port: 20000

uca:
  base:
    # 当前 Client 注册到同一应用内的 Base
    host: 127.0.0.1
    port: ${server.port}
    heartbeat-timeout: 30s
    offline-retention: 2m
    scan-interval: 5s
    secret-interval: 10m
  client:
    # 分布式部署时改为其他服务可以访问的 IP 或域名
    host: "127.0.0.1:${server.port}"
    code: uca-admin
    name: UCA 管理服务
    mode: partial
    prefix: api/v1/admin
    interval: 5s
    version: 0.0.1
    metadata:
      type: control-and-business
```

应用启动完成后，Client 会通过本机 HTTP 注册到同一 JVM 中的 Base。组合部署会让业务服务和 Base 共享生命周期；需要独立发布或更高可用性时，应使用前两种方式拆分部署。

### 4. 开放和调用业务接口

`partial` 模式下，在被调用接口使用 `@UCAResponse`：

```java
import com.twlic.uca.client.core.UCAResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    @UCAResponse
    @GetMapping("/demo/ping")
    public Map<String, String> ping() {
        return Map.of("message", "pong");
    }
}
```

`full` 模式无需修改现有 Controller。

其他业务服务通过 `UcaClient` 调用：

```java
Map<?, ?> response = ucaClient
        .get("order-service", "/demo/ping")
        .retrieve(Map.class);
```

Client 会从 JVM 本地服务目录选择 `order-service` 在线实例，并将注册前缀和业务相对路径组合为：

```text
/api/v1/order/demo/ping
```

前端也可以通过当前服务的统一入口透传：

```http
GET /api/v1/uca/request/order-service/demo/ping
```

### 5. 启动项目

开发环境直接启动：

```powershell
mvn spring-boot:run
```

或者构建后运行：

```powershell
mvn -B clean package
java -jar .\target\your-application-0.0.1-SNAPSHOT.jar
```

Base-only 或 Base+Client 项目可以访问：

```text
http://127.0.0.1:20000/dashboard
http://127.0.0.1:20000/actuator/health
```

Client-only 或 Base+Client 项目可以检查：

```http
GET /api/v1/uca/client
GET /api/v1/uca/request/services
```

项目地址：[github.com/ZevixQuatol/uca](https://github.com/ZevixQuatol/uca)

许可证：[Apache License 2.0](LICENSE)
