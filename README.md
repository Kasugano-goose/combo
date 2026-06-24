# 🎮 Combo - 游戏匹配系统

一个基于 Spring Boot + Vue 3 的多人游戏匹配系统，支持实时匹配、好友系统、聊天功能和实时场景同步。

## ✨ 功能特性

### 玩家系统
- 用户注册、登录（Session 认证）
- 个人资料管理（段位、积分、角色选择）
- BCrypt 密码加密

### 好友系统
- 发送好友申请
- 接受/拒绝申请
- 好友列表查询
- WebSocket 实时通知（对方收到申请/被接受/被拒绝）

### 匹配系统
- Redis Sorted Set 匹配池（支持分布式扩展）
- 段位匹配算法（段位差 ≤ 1 级，分数差 ≤ 300）
- 60 秒匹配超时自动清理
- **双方确认机制**：匹配成功后双方需各自确认才能进入场景
- 30 秒确认超时自动取消
- synchronized 保证并发安全

### 聊天系统
- 基于 WebSocket 的实时聊天
- 好友校验（只有好友才能聊天）
- 消息不入库（纯内存转发）

### 场景系统
- 服务端权威架构（前端发指令，后端算位置，后端广播）
- 游戏循环（每 100ms 更新一次位置）
- 键盘方向键控制角色移动
- Canvas 实时渲染双方位置
- 边界校验
- 一方退出自动通知另一方

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 4.0.6
- **语言**: Java 17
- **数据库**: MySQL 8.0+（玩家、好友关系等持久化数据）
- **缓存**: Redis（匹配池：Sorted Set + Hash）
- **实时通信**: WebSocket（JSR 356 @ServerEndpoint）
- **ORM**: Spring Data JPA (Hibernate)
- **构建工具**: Maven

### 前端
- **框架**: Vue 3 + Composition API
- **构建工具**: Vite
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP 客户端**: Axios
- **实时通信**: 原生 WebSocket API
- **游戏渲染**: HTML5 Canvas

## 📁 项目结构

```
combo/
├── backend/                        # 后端项目
│   ├── src/main/java/com/example/combo/
│   │   ├── player/                 # 玩家模块
│   │   │   ├── controller/         # REST 接口
│   │   │   ├── service/            # 业务逻辑
│   │   │   ├── repository/         # JPA 查询
│   │   │   ├── domain/             # 实体类
│   │   │   └── dto/                # 数据传输对象
│   │   ├── friendship/             # 好友模块
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── domain/
│   │   │   └── dto/
│   │   ├── match/                  # 匹配模块
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   └── dto/
│   │   ├── chat/                   # 聊天模块
│   │   │   ├── endpoint/           # WebSocket 端点
│   │   │   ├── service/
│   │   │   └── dto/
│   │   ├── scene/                  # 场景模块
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── endpoint/
│   │   │   └── domain/
│   │   └── common/                 # 公共模块
│   │       ├── websocket/          # WebSocket 基础设施
│   │       ├── exception/          # 全局异常处理
│   │       ├── redis/              # Redis 配置
│   │       └── AppConfig.java      # 通用配置
│   ├── src/main/resources/
│   │   ├── application.properties  # 应用配置
│   │   ├── schema.sql              # 数据库表结构
│   │   └── data.sql                # 种子数据
│   └── pom.xml
├── frontend/                       # 前端项目
│   ├── src/
│   │   ├── views/                  # 页面组件
│   │   │   ├── Login.vue
│   │   │   ├── Register.vue
│   │   │   ├── Home.vue
│   │   │   ├── Friends.vue
│   │   │   ├── Match.vue
│   │   │   ├── Chat.vue
│   │   │   └── Scene.vue
│   │   ├── api/                    # API 调用
│   │   ├── stores/                 # Pinia 状态管理
│   │   ├── composables/            # 组合式函数（WebSocket 等）
│   │   └── router/                 # 路由配置
│   └── package.json
└── README.md
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+（或 Windows 版 Memurai）
- Maven 3.8+

### 1. 启动 Redis

```bash
# Linux/Mac
redis-server

# Windows（使用 Memurai 或下载 Redis for Windows）
# 下载地址: https://github.com/tporadowski/redis/releases
redis-server redis.windows.conf
```

验证 Redis 运行：
```bash
redis-cli ping
# 返回 PONG 表示正常
```

### 2. 配置数据库

创建 MySQL 数据库：
```sql
CREATE DATABASE combo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

编辑 `backend/src/main/resources/application.properties`：
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/combo?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_password
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端启动后自动执行 `schema.sql`（建表）和 `data.sql`（种子数据）。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 📊 数据库设计

### ER 关系图

```
player_rank 1──N player N──1 game_role
                  │
                  │ (requester_id)
                  ▼
              friendship
                  ▲
                  │ (addressee_id)
                  │
                player
```

### 表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| player_rank | 段位表 | code, level, min_score, max_score |
| game_role | 角色表 | code, name, description |
| player | 玩家表 | username, password(BCrypt), rank_id, rank_score, selected_role_id |
| friendship | 好友关系表 | requester_id, addressee_id, status(PENDING/ACCEPTED/REJECTED) |

### 索引设计

- `player`: idx_rank_score, idx_rank_id, idx_selected_role_id
- `friendship`: idx_requester_status, idx_addressee_status, idx_pair_status

## 🔌 WebSocket 端点

| 端点 | 路径 | 用途 |
|------|------|------|
| FriendWebSocketEndpoint | `/ws/friend/{playerId}` | 好友通知、匹配通知 |
| ChatWebSocketEndpoint | `/ws/chat/{playerId}` | 实时聊天 |
| SceneWebSocketEndpoint | `/ws/scene/{sceneId}/{playerId}` | 场景位置同步 |

## 🎯 匹配流程

```
Player1 加入匹配池 → 等待
Player2 加入匹配池 → 匹配成功
    │
    ▼
双方收到 MATCHED 通知（显示对手信息）
    │
    ▼
双方各自点击"确认进入场景"
    │
    ▼
双方确认 → 创建场景 → 收到 SCENE_READY
    │
    ▼
跳转场景页面 → 方向键控制移动 → 实时位置同步
    │
    ▼
一方退出 → 通知对方 → 场景销毁
```

## ⚠️ 已知问题与解决方案

详见 `backend/bug记录.txt`，包含以下问题的分析和解决方案：

1. WebSocket Session 注册时序问题 → 改为双方确认机制
2. 三人同时匹配的并发竞态 → synchronized 加锁
3. 确认阶段无超时 → 定时清理过期 MatchPair

## 📝 API 接口

### 玩家接口
- `POST /players/register` - 注册
- `POST /players/login` - 登录
- `POST /players/logout` - 登出
- `GET /players/me` - 获取当前玩家信息

### 好友接口
- `POST /friends/requests` - 发送好友申请
- `GET /friends/requests` - 查询待处理申请
- `POST /friends/requests/{id}/accept` - 接受申请
- `POST /friends/requests/{id}/reject` - 拒绝申请
- `GET /friends` - 好友列表

### 匹配接口
- `POST /match/join` - 加入匹配池
- `POST /match/leave` - 退出匹配池
- `POST /match/confirm` - 确认进入场景
- `GET /match/status` - 查询匹配状态
- `GET /match/pool` - 查询匹配池人数

### 场景接口
- `POST /scene/exit` - 退出场景
- `GET /scene/count` - 查询活跃场景数

## 📄 许可证

MIT License

---

**作者**: [Kasugano-goose](https://github.com/Kasugano-goose)
