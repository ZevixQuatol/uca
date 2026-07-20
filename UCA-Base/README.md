# UCA-Base（基础模块）

`UCA-Base` 是统一控制面下可组合业务应用架构的基础模块。第一阶段负责子业务系统的注册、发现、接入和状态管理。

当前项目是一个 Spring Boot 单体服务，提供 REST API 和基于 Freemarker 的系统状态页面，不连接数据库或 Redis。

## 技术栈

- JDK 21
- Spring Boot 4.1.0
- Maven
- Spring MVC
- Freemarker
- HTML、CSS、JavaScript
- Jakarta Bean Validation
- Spring Boot Actuator

Maven 坐标：

```text
com.twlic.uca:uca-base:0.0.1
```

Java 根包名：

```text
com.twlic.uca.base
```

## 当前职责

- 支持子业务系统主动注册；
- 支持同一业务系统同时注册多个实例；
- 根据子业务系统主动心跳维护在线状态；
- 心跳超时后先标记实例离线；
- 离线实例超过保留时间后从内存注册表移除；
- 支持实例主动注销；
- 按应用独立轮询，从在线实例中直接选择并返回一个实例；
- 查询当前应用和实例状态。

`UCA-Base` 只返回发现结果，不代理或转发业务请求。

## 当前不包含

- 用户、组织、角色和权限；
- OAuth2、OIDC、JWT 或 SSO；
- 注册、心跳和发现接口的身份验证；
- 数据库、Redis 和文件持久化；
- Spring Cloud、Nacos 或 Eureka；
- 主动调用子系统健康检查地址；
- 独立前端应用和 Node.js 构建链；
- 多个 `UCA-Base` 节点之间的状态同步。

后续认证能力将作为独立的 `Auth` 子业务系统建设，并注册到 `UCA-Base`。

## 构建和启动

确认当前 Java 版本为 21：

```powershell
java -version
mvn -version
```

运行测试：

```powershell
mvn test
```

打包：

```powershell
mvn clean package
```

启动：

```powershell
java -jar .\target\uca-base-0.0.1.jar
```

默认监听：

```text
http://localhost:48080
```

可以通过环境变量覆盖端口：

```powershell
$env:UCA_BASE_PORT='48081'
java -jar .\target\uca-base-0.0.1.jar
```

## 系统状态页面

启动后访问：

```text
http://localhost:48080/
```

也可以使用别名路径：

```text
http://localhost:48080/dashboard
```

页面采用 Freemarker 服务端模板和独立的 HTML、CSS、JavaScript 实现，无需 Node.js 或前端打包。主要展示：

- UCA-Base 名称、版本、Java 版本、启动时间和运行时长；
- UCA-Base 自身健康状态；
- 已注册子业务系统数量、实例总数、在线和离线实例数；
- 子业务系统名称、应用编码、实例标识、状态、地址、版本、最近心跳和元数据；
- 每 5 秒自动刷新，也支持手工立即刷新。

页面通过现有 `/actuator/health` 和 `/api/v1/applications` 接口读取实时状态，不增加单独的持久化或前端状态源。

## 注册生命周期

```text
子业务系统启动
  -> PUT 注册实例
  -> 周期性 PUT 心跳
  -> UCA-Base 保持 ONLINE

心跳达到超时阈值
  -> 标记 OFFLINE

OFFLINE 达到保留期限
  -> 从内存注册表移除

子业务系统正常停止
  -> DELETE 主动注销
```

暂时离线的已知实例恢复心跳后会重新变为 `ONLINE`。`UCA-Base` 重启会清空内存注册表；此时心跳会返回 `INSTANCE_NOT_FOUND`，子业务系统应立即重新注册。

## 配置

默认配置：

```yaml
uca:
  registry:
    heartbeat-timeout: 30s
    offline-retention: 2m
    scan-interval: 5s
```

对应环境变量：

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| 服务端口 | `UCA_BASE_PORT` | `48080` |
| 心跳超时 | `UCA_REGISTRY_HEARTBEAT_TIMEOUT` | `30s` |
| 离线保留 | `UCA_REGISTRY_OFFLINE_RETENTION` | `2m` |
| 状态扫描周期 | `UCA_REGISTRY_SCAN_INTERVAL` | `5s` |

三个时间配置必须大于零。

## REST API

API 根路径：

```text
/api/v1
```

### 注册或更新实例

```http
PUT /api/v1/applications/{applicationCode}/instances/{instanceId}
Content-Type: application/json
```

```json
{
  "applicationName": "认证模块",
  "baseUrl": "http://auth-1:8080",
  "version": "1.0.0",
  "metadata": {
    "zone": "default"
  }
}
```

首次注册返回 `201 Created`；相同 `applicationCode + instanceId` 再次注册会更新地址、版本和元数据，并返回 `200 OK`。

### 发送心跳

```http
PUT /api/v1/applications/{applicationCode}/instances/{instanceId}/heartbeat
```

未知实例返回 `404 INSTANCE_NOT_FOUND`。

### 主动注销

```http
DELETE /api/v1/applications/{applicationCode}/instances/{instanceId}
```

注销具有幂等语义，实例不存在时仍返回 `204 No Content`。

### 发现一个在线实例

```http
GET /api/v1/applications/{applicationCode}/instances/next
```

同一应用的发现请求在当前在线实例之间轮询。应用不存在时返回 `404 APPLICATION_NOT_FOUND`；应用存在但没有在线实例时返回 `503 NO_ONLINE_INSTANCE`。

### 查询状态

```http
GET /api/v1/applications
GET /api/v1/applications/{applicationCode}
```

返回当前进程内存中的只读状态快照。

### UCA-Base 自身健康状态

```http
GET /actuator/health
```

这个接口只表示 `UCA-Base` 进程自身可用，不代表所有子业务系统都在线。

## 错误响应

错误响应使用 `ProblemDetail`，并带有稳定的 `code` 字段：

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Application 'missing' is not registered",
  "code": "APPLICATION_NOT_FOUND"
}
```

第一阶段错误码：

```text
VALIDATION_FAILED
APPLICATION_NOT_FOUND
INSTANCE_NOT_FOUND
NO_ONLINE_INSTANCE
```

## 手工请求

仓库根目录的 [`requests.http`](requests.http) 提供了完整的注册、心跳、轮询发现、状态查询和注销示例，可由 IntelliJ IDEA HTTP Client 直接执行。

## 重要限制

当前所有注册和发现 API 完全开放，只适合受信网络中的第一阶段联调。注册信息只保存在当前 Java 进程内存中，服务重启后不会保留。
