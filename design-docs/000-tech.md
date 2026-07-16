# Core 独立服务项目规范

版本：v1.0
适用范围：所有 `core-*` 基础服务
默认架构：独立服务、独立仓库、独立数据库、单体部署
默认技术栈：Java 21、Spring Boot、Maven、Vue3、SQLite

---

# 1. 总体架构

Core Platform 不是一个包含大量 Maven Module 的巨型工程。

每一个核心能力都是一个独立项目：

```text
core-identity
core-billing
core-notification
core-ai-gateway
core-storage
core-workflow
core-api-gateway
core-marketplace
```

每一个 `core-*` 都必须满足：

```text
独立 Git 仓库
独立 Maven 工程
独立 Spring Boot 应用
独立数据库
独立配置
独立版本
独立发布
独立文档
独立部署
独立开源
```

例如：

```text
github.com/core-platform/core-identity
github.com/core-platform/core-billing
github.com/core-platform/core-notification
```

禁止建立：

```text
core-platform/
├── core-identity/
├── core-billing/
├── core-notification/
└── ...
```

这种将全部服务绑定在一个 Maven 父项目中的结构。

各服务可以共同遵循标准，但不共同生命周期。

---

# 2. 一个 Core 就是一个服务

以 `core-identity` 为例：

```text
core-identity/
├── pom.xml
├── README.md
├── LICENSE
├── CHANGELOG.md
├── SECURITY.md
├── Dockerfile
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
│       └── java/
│
├── web/
├── docs/
└── scripts/
```

它只有一个 Maven 工程：

```text
artifactId: core-identity
```

只有一个 Spring Boot 启动类：

```java
CoreIdentityApplication
```

只有一个后端进程：

```bash
java -jar core-identity.jar
```

只有一个主要发布产物：

```text
core-identity-{version}.jar
```

不再划分：

```text
backend
admin-backend
frontend
admin-frontend
```

用户 API 和管理 API 属于同一个服务。

用户页面和管理页面可以属于同一个 Vue 应用。

---

# 3. 标准项目目录

所有 `core-*` 使用同一种项目骨架。

```text
core-xxx/
├── pom.xml
├── README.md
├── LICENSE
├── CHANGELOG.md
├── SECURITY.md
├── Dockerfile
├── .gitignore
├── .editorconfig
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/coreplatform/xxx/
│   │   │       ├── CoreXxxApplication.java
│   │   │       ├── api/
│   │   │       ├── application/
│   │   │       └── infrastructure/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-sqlite.yml
│   │       ├── application-mysql.yml
│   │       ├── db/
│   │       │   └── migration/
│   │       └── static/
│   │
│   └── test/
│       └── java/
│
├── web/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│
├── docs/
│   ├── architecture.md
│   ├── api.md
│   ├── database.md
│   ├── configuration.md
│   ├── deployment.md
│   └── development.md
│
└── scripts/
    ├── dev.sh
    ├── dev.bat
    ├── build.sh
    ├── build.bat
    ├── backup.sh
    └── backup.bat
```

其中：

```text
src/    Spring Boot 后端源码
web/    Vue3 前端源码
docs/   当前服务自己的技术文档
scripts/ 开发、构建、备份脚本
```

`web/` 不是独立产品，也不是独立发布项目。

Vue3 构建结果最终写入：

```text
src/main/resources/static/
```

或在 Maven 构建阶段复制到：

```text
target/classes/static/
```

最终前后端共同发布为一个 JAR。

---

# 4. 只保留三层

每一个 Core 后端只保留三层：

```text
api
application
infrastructure
```

统一结构：

```text
io.coreplatform.identity
├── api
├── application
└── infrastructure
```

不要再引入更多顶层架构目录。

---

# 5. 第一层：API

API 层是服务的入口。

```text
api/
├── controller/
├── dto/
├── request/
├── response/
├── security/
├── exception/
└── contract/
```

负责：

```text
REST API
请求参数接收
参数校验
身份读取
权限入口
DTO 转换
HTTP 状态码
错误响应
OpenAPI 描述
```

例如：

```text
api/
├── controller/
│   ├── AuthController.java
│   ├── AccountController.java
│   └── AdminUserController.java
│
├── request/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   └── UpdateUserRequest.java
│
├── response/
│   ├── LoginResponse.java
│   └── UserResponse.java
│
└── exception/
    └── GlobalExceptionHandler.java
```

API 层禁止：

```text
直接操作数据库
直接调用 Repository
编写复杂业务规则
直接调用第三方 SDK
管理事务
```

正确调用方向：

```text
Controller
    ↓
Application Service
```

---

# 6. 第二层：Application

Application 层负责业务能力和领域规则。

```text
application/
├── service/
├── domain/
├── command/
├── query/
├── event/
├── port/
└── exception/
```

负责：

```text
业务用例
领域规则
事务边界
权限业务判断
命令处理
查询处理
领域事件
接口抽象
```

例如：

```text
application/
├── service/
│   ├── AuthenticationService.java
│   ├── UserService.java
│   ├── OrganizationService.java
│   └── PermissionService.java
│
├── domain/
│   ├── User.java
│   ├── Organization.java
│   ├── Membership.java
│   ├── Role.java
│   └── Permission.java
│
├── command/
│   ├── CreateUserCommand.java
│   └── DisableUserCommand.java
│
├── query/
│   └── UserQueryService.java
│
└── port/
    ├── UserRepository.java
    ├── PasswordEncoderPort.java
    └── NotificationPort.java
```

Application 层可以定义接口，但不能依赖具体基础设施。

例如：

```java
public interface UserRepository {

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    User save(User user);
}
```

Application 层只知道：

```text
需要保存用户
需要发送通知
需要验证密码
```

它不应该知道：

```text
数据库是 SQLite 还是 MySQL
邮件由 SMTP 还是其他服务发送
密码由哪个具体库完成
HTTP 客户端使用什么实现
```

---

# 7. 第三层：Infrastructure

Infrastructure 层实现所有技术细节。

```text
infrastructure/
├── persistence/
├── security/
├── client/
├── provider/
├── config/
├── job/
└── integration/
```

负责：

```text
数据库访问
Repository 实现
SQL
第三方服务调用
安全框架配置
HTTP Client
定时任务
配置读取
缓存实现
文件系统
外部 Provider
```

例如：

```text
infrastructure/
├── persistence/
│   ├── entity/
│   ├── repository/
│   ├── jdbc/
│   └── converter/
│
├── security/
│   ├── SecurityConfig.java
│   ├── JwtService.java
│   └── PasswordEncoderAdapter.java
│
├── client/
│   ├── NotificationClient.java
│   └── BillingClient.java
│
└── config/
    ├── IdentityProperties.java
    └── DatabaseConfig.java
```

正确依赖方向：

```text
API
 ↓
Application
 ↑
Infrastructure
```

Infrastructure 实现 Application 定义的接口。

例如：

```java
@Repository
public class JdbcUserRepository implements UserRepository {
}
```

禁止 Application 依赖：

```text
JdbcTemplate
JPA Repository
JavaMailSender
RestClient
OpenAI SDK
SQLite Driver
MySQL Driver
```

---

# 8. 前端结构

每个 Core 最多只有一个 Vue3 应用。

例如：

```text
core-identity/web/
```

统一结构：

```text
web/src/
├── app/
├── api/
├── router/
├── layouts/
├── pages/
├── components/
├── stores/
├── permissions/
├── styles/
└── main.ts
```

用户端和管理端不拆成两个 Vue 项目。

通过路由区分：

```text
/login
/register
/account
/account/security
/account/api-keys

/admin/users
/admin/organizations
/admin/roles
/admin/permissions
```

通过 Layout 区分：

```text
PublicLayout
AccountLayout
AdminLayout
```

通过权限控制：

```text
identity.user.read
identity.user.create
identity.organization.manage
identity.role.manage
```

禁止创建：

```text
core-identity-web
core-identity-admin
```

两个独立前端。

这样可以避免：

```text
重复登录逻辑
重复 API SDK
重复权限系统
重复组件
重复样式
重复构建流程
```

---

# 9. 并非所有 Core 都必须有前端

统一的是工程规则，不是空目录。

以下服务通常需要前端：

```text
core-identity
core-billing
core-ai-gateway
core-storage
core-workflow
core-marketplace
```

以下服务可能只需要简单管理页面：

```text
core-notification
core-api-gateway
```

如果某个服务暂时没有界面，可以不创建：

```text
web/
```

禁止为了目录对称保留空 Vue 项目。

当需要页面时，再按统一结构创建。

---

# 10. 服务独立数据库

每个 `core-*` 拥有自己的数据库。

默认：

```text
core-identity/data/core-identity.db
core-billing/data/core-billing.db
core-notification/data/core-notification.db
```

禁止多个服务共享同一个 SQLite 文件：

```text
core-platform.db
```

数据库归属示例：

```text
core-identity
  identity_user
  identity_organization
  identity_role
  identity_permission

core-billing
  billing_plan
  billing_subscription
  billing_invoice
  billing_usage_entry

core-notification
  notification_template
  notification_message
  notification_delivery
```

一个服务禁止直接查询另一个服务的数据库。

例如 `core-billing` 禁止：

```sql
SELECT *
FROM identity_user
```

必须通过：

```text
core-identity API
```

获取用户和组织信息。

---

# 11. SQLite 与 MySQL

每个 Core 默认使用 SQLite：

```yaml
core:
  datasource:
    type: sqlite
```

默认文件：

```text
./data/core-{service}.db
```

例如：

```text
./data/core-identity.db
```

生产环境可以切换：

```yaml
core:
  datasource:
    type: mysql
```

配置文件：

```text
application.yml
application-sqlite.yml
application-mysql.yml
```

启动示例：

```bash
java -jar core-identity.jar \
  --spring.profiles.active=sqlite
```

或者：

```bash
java -jar core-identity.jar \
  --spring.profiles.active=mysql
```

数据库迁移统一使用 Flyway。

每个服务维护自己的迁移文件：

```text
src/main/resources/db/migration/
```

禁止：

```text
Hibernate ddl-auto=update
手工创建生产表
启动时动态修改数据库结构
```

---

# 12. 独立服务之间如何通信

第一阶段不引入：

```text
服务注册中心
Redis
MQ
Kafka
RabbitMQ
配置中心
分布式事务
```

服务地址通过配置明确指定。

例如 `core-billing`：

```yaml
core:
  services:
    identity:
      base-url: http://localhost:8101
```

服务之间统一使用：

```text
HTTP REST
JSON
短超时
明确重试
幂等键
```

禁止：

```text
共享数据库
直接引用对方 Repository
直接依赖对方 Java 实现类
复制对方业务逻辑
```

---

# 13. 服务发现策略

第一阶段不建立服务注册中心。

服务地址来源：

```text
application.yml
环境变量
启动参数
```

例如：

```yaml
core:
  services:
    identity:
      base-url: ${CORE_IDENTITY_URL:http://localhost:8101}

    billing:
      base-url: ${CORE_BILLING_URL:http://localhost:8102}

    notification:
      base-url: ${CORE_NOTIFICATION_URL:http://localhost:8103}
```

这已经足够支持：

```text
本地开发
单机部署
Docker Compose
小规模服务器部署
```

只有进入动态多实例部署时，再考虑服务发现。

---

# 14. 认证方式

`core-identity` 是唯一身份提供者。

它负责：

```text
用户登录
Token 签发
Token 刷新
组织上下文
角色
权限
服务账号
API Client
```

其他 Core 不保存：

```text
用户密码
登录会话
完整用户表
角色表
权限表
```

其他服务通过访问令牌识别用户。

推荐流程：

```text
用户
 ↓
core-identity 登录
 ↓
获得短期 Access Token
 ↓
访问 core-billing / core-storage / core-ai-gateway
```

其他服务通过 `core-identity` 暴露的公钥验证 Token。

这样每一次请求不需要同步调用 Identity。

`core-identity` 提供：

```text
/.well-known/jwks.json
```

其他服务本地验证：

```text
签名
过期时间
issuer
audience
scope
organization_id
```

---

# 15. 服务间认证

服务调用服务时，不使用个人用户密码。

使用：

```text
Service Account
Client ID
Client Secret
短期 Service Token
```

例如：

```text
core-billing
    ↓
core-notification
```

调用时携带：

```text
Authorization: Bearer {service-token}
```

Token 权限示例：

```text
notification.message.send
identity.user.read
billing.usage.write
```

任何服务不得因为处于内网就跳过身份验证。

---

# 16. API 路径规范

每个服务有自己的统一前缀。

`core-identity`：

```text
/api/v1/identity
```

示例：

```text
POST /api/v1/identity/auth/login
POST /api/v1/identity/auth/logout
GET  /api/v1/identity/account
GET  /api/v1/identity/organizations
```

管理接口：

```text
/api/v1/identity/admin
```

示例：

```text
GET  /api/v1/identity/admin/users
POST /api/v1/identity/admin/users
GET  /api/v1/identity/admin/roles
```

`core-billing`：

```text
/api/v1/billing
/api/v1/billing/admin
```

`core-notification`：

```text
/api/v1/notification
/api/v1/notification/admin
```

统一形式：

```text
/api/v1/{service}
/api/v1/{service}/admin
```

---

# 17. API 返回规范

成功请求直接返回资源。

```json
{
  "id": "user-id",
  "email": "user@example.com",
  "status": "ACTIVE"
}
```

分页：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 100,
  "hasNext": true
}
```

错误：

```json
{
  "type": "https://core-platform.dev/problems/user-not-found",
  "title": "User not found",
  "status": 404,
  "detail": "The requested user does not exist.",
  "errorCode": "IDENTITY_USER_NOT_FOUND",
  "traceId": "01J..."
}
```

HTTP 状态码必须表达真实结果：

```text
200 查询成功
201 创建成功
204 删除成功
400 请求错误
401 未登录
403 无权限
404 资源不存在
409 状态冲突
422 业务校验失败
429 请求过多
500 服务异常
503 外部依赖暂时不可用
```

禁止所有错误都返回：

```text
HTTP 200
```

---

# 18. 每个服务的标准能力

每一个 `core-*` 默认提供：

```text
健康检查
版本信息
OpenAPI
统一异常
请求 ID
结构化日志
数据库迁移
配置校验
优雅停机
审计日志
备份脚本
升级文档
```

推荐端点：

```text
GET /actuator/health
GET /actuator/info
GET /v3/api-docs
GET /swagger-ui
```

版本信息至少包含：

```json
{
  "name": "core-identity",
  "version": "1.0.0",
  "buildTime": "2026-07-14T00:00:00Z",
  "gitCommit": "abcdef"
}
```

---

# 19. 每个服务的表命名

即使数据库独立，也保留服务前缀。

例如 `core-identity`：

```text
identity_user
identity_organization
identity_membership
identity_role
identity_permission
identity_audit_log
```

例如 `core-billing`：

```text
billing_plan
billing_subscription
billing_usage_entry
billing_invoice
billing_payment
```

这样未来合库、导出、数据仓库分析时不会冲突。

---

# 20. 基础字段

普通业务表建议：

```text
id
created_at
updated_at
version
```

需要记录操作人时：

```text
created_by
updated_by
```

确实需要软删除时才增加：

```text
deleted_at
deleted_by
```

禁止所有表强制增加：

```text
deleted
```

支付流水、审计日志、用量账本等数据不得软删除。

---

# 21. 服务事件

第一阶段没有 MQ，可以采用两种方式。

## 方式一：同步 HTTP

适用于必须立即获得结果的操作：

```text
查询用户
检查权限
扣减额度
创建支付
获取文件信息
```

## 方式二：Outbox + HTTP 投递

适用于异步事件：

```text
用户创建
订阅变更
支付完成
文件上传完成
额度即将耗尽
Workflow 执行完成
```

每个服务维护：

```text
core_outbox_event
```

后台任务负责：

```text
扫描未发送事件
调用目标 Webhook
失败重试
记录投递结果
超过次数进入死信
支持人工重放
```

事件必须包含：

```text
event_id
event_type
event_version
producer
created_at
payload
status
attempt
next_retry_at
```

消费者必须根据：

```text
event_id
```

实现幂等。

这样暂时不需要 MQ，也不会把跨服务事件完全绑定为同步调用。

---

# 22. 前端发布方式

Vue3 构建后，由 Spring Boot 托管静态资源。

开发阶段：

```text
Vue3: http://localhost:5173
Spring Boot: http://localhost:8101
```

生产阶段：

```text
http://localhost:8101/
```

直接提供：

```text
登录页面
账户页面
管理页面
REST API
OpenAPI
```

最终只需启动一个 JAR：

```bash
java -jar core-identity.jar
```

不需要额外启动 Nginx 和 Node.js。

---

# 23. 服务端口约定

默认开发端口：

```text
core-api-gateway    8080
core-identity       8101
core-billing        8102
core-notification   8103
core-ai-gateway     8104
core-storage        8105
core-workflow       8106
core-marketplace    8107
```

端口只是默认值，必须允许通过环境变量覆盖。

例如：

```text
SERVER_PORT=9101
```

---

# 24. 服务职责边界

## core-identity

```text
用户
组织
成员
认证
会话
角色
权限
邀请
服务账号
Token
```

## core-billing

```text
产品
套餐
价格
订阅
权益
额度
用量
账单
支付
退款
对账
```

## core-notification

```text
邮件
短信
站内信
IM
Webhook
模板
发送记录
重试
通知偏好
```

## core-ai-gateway

```text
模型供应商
模型目录
密钥
统一调用
模型路由
Prompt
Agent
Tool
Token 用量
费用
预算
```

## core-storage

```text
文件元数据
上传
下载
本地存储
对象存储
文件权限
文件版本
配额
```

## core-workflow

```text
事件
任务
定时任务
延迟任务
重试
步骤
规则
自动化
运行记录
```

## core-api-gateway

```text
统一入口
API Client
API Key
请求签名
限流
路由
OpenAPI 汇总
Webhook 管理
```

## core-marketplace

```text
插件
应用
模板
扩展点
安装
升级
启用
禁用
依赖
权限声明
```

---

# 25. 共享规范如何维护

虽然所有 Core 是独立仓库，但可以维护两个非常薄的公共项目。

## core-parent

Maven Parent 和 BOM：

```text
Java 版本
Spring Boot 版本
Maven 插件版本
测试插件
代码格式
依赖版本
编译参数
```

每个项目：

```xml
<parent>
    <groupId>io.coreplatform</groupId>
    <artifactId>core-parent</artifactId>
    <version>1.0.0</version>
</parent>
```

`core-parent` 只管理工程规范，不包含业务代码。

## core-starter

提供稳定的公共技术能力：

```text
统一错误模型
请求 ID
基础审计
日志字段
安全工具
OpenAPI 基础配置
配置校验
数据库基础配置
```

禁止放入：

```text
用户实体
组织实体
支付逻辑
文件逻辑
AI 逻辑
通知逻辑
```

`core-starter` 必须保持极薄，防止演变为通用垃圾桶。

前端可以维护：

```text
core-ui
```

提供：

```text
设计 Token
基础组件
Layout
表单组件
权限组件
错误页面
```

各 Core 通过 npm 包依赖，而不是复制代码。

---

# 26. 服务版本独立

每一个 Core 独立版本：

```text
core-identity 1.2.0
core-billing 1.0.3
core-notification 1.4.1
```

不要求所有服务同时发布。

每个服务必须声明兼容范围：

```yaml
core:
  compatibility:
    identity: ">=1.2.0 <2.0.0"
    notification: ">=1.0.0 <2.0.0"
```

只有发生公开 API 不兼容变化时，才升级主版本。

---

# 27. 服务不可用时的处理

调用其他 Core 时必须设置：

```text
连接超时
读取超时
有限重试
失败降级
错误日志
Trace ID
```

禁止无限重试。

例如：

```text
Identity 不可用
```

已经签发且未过期的 Token 仍然可以本地验证。

例如：

```text
Notification 不可用
```

业务服务可以记录待发送任务，稍后重试，不应直接让核心业务事务永久失败。

例如：

```text
Billing 不可用
```

涉及额度和付费权限时默认拒绝，避免免费越权使用。

不同依赖必须明确：

```text
失败开放
失败关闭
延迟处理
```

策略。

---

# 28. 本地开发方式

每个 Core 可以单独运行。

例如开发 Identity：

```bash
git clone core-identity
cd core-identity
mvn spring-boot:run
```

默认使用：

```text
SQLite
本地文件
默认管理员
本地日志
```

开发整个 Core Platform 时，使用独立编排仓库：

```text
core-dev-environment
```

只存放：

```text
docker-compose.yml
启动脚本
环境变量模板
服务地址配置
开发文档
```

它不包含任何 Core 的业务源码。

例如：

```text
core-dev-environment/
├── docker-compose.yml
├── .env.example
├── start-all.bat
├── stop-all.bat
└── README.md
```

这样既能统一启动全部服务，又不会把源码重新混进一个大仓库。

---

# 29. 每个项目 README 必须包含

```text
项目定位
职责范围
明确不负责什么
运行要求
快速启动
配置说明
API 文档
数据库说明
依赖服务
被哪些服务依赖
权限列表
事件列表
备份恢复
升级方式
开发规范
版本兼容
安全说明
```

README 开头统一：

```markdown
# core-identity

Independent identity and access management service for Core Platform.

## Responsibilities

- User
- Organization
- Authentication
- Authorization

## Non-responsibilities

- Billing
- File storage
- Notification delivery
- Business user profiles
```

---

# 30. 一个 Core 的完成标准

一个项目只有满足以下要求，才算可发布：

```text
可以独立克隆
可以独立编译
可以独立启动
默认 SQLite 可运行
MySQL 配置可运行
数据库迁移完整
API 文档完整
权限模型完整
关键操作有审计
服务调用有超时
写接口支持必要幂等
没有访问其他服务数据库
没有复制其他服务业务模型
前端与后端可构建为一个 JAR
README 可指导新开发者运行
```

---

# 31. 最高约束

每个 `core-*` 必须遵守：

> 一个仓库、一个服务、一个 Maven 工程、一个数据库、一个版本、一个主要发布产物。

统一的是：

```text
三层结构
API 规范
配置规范
数据库规范
安全规范
日志规范
事件规范
项目骨架
```

独立的是：

```text
源码
数据库
发布
部署
版本
生命周期
业务职责
```

不要为了统一把所有服务放回同一个工程。

也不要为了独立，把一个服务再次拆成用户后端、管理后端、用户前端、管理前端四个项目。

最终形态应当是：

```text
core-identity
  一个独立服务

core-billing
  一个独立服务

core-notification
  一个独立服务
```

每个服务内部足够完整，每个服务之间足够松耦合。
