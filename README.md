# 🎮 Combo - 游戏匹配系统

一个基于 Spring Boot + Vue 3 的多人游戏匹配系统，支持实时匹配、好友系统、聊天功能和场景管理。

## ✨ 功能特性

- **用户系统** - 注册、登录、个人资料管理、角色选择
- **好友系统** - 发送好友请求、接受/拒绝、好友列表
- **实时匹配** - 智能匹配算法、实时匹配状态更新
- **聊天系统** - 基于 WebSocket 的实时聊天
- **场景管理** - 多场景支持、玩家位置同步

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 4.0.6
- **语言**: Java 17
- **数据库**: MySQL
- **缓存**: Redis
- **实时通信**: WebSocket
- **构建工具**: Maven

### 前端
- **框架**: Vue 3
- **构建工具**: Vite
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP 客户端**: Axios

## 📁 项目结构

```
combo/
├── backend/                # 后端项目
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── com/example/combo/
│   │               ├── player/      # 玩家模块
│   │               ├── friendship/  # 好友模块
│   │               ├── match/       # 匹配模块
│   │               ├── chat/        # 聊天模块
│   │               ├── scene/       # 场景模块
│   │               └── common/      # 公共模块
│   └── pom.xml
└── frontend/               # 前端项目
    ├── src/
    ├── public/
    └── package.json
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- Maven 3.8+

### 后端启动

```bash
cd backend

# 配置数据库连接
# 编辑 src/main/resources/application.yml

# 运行项目
./mvnw spring-boot:run
```

### 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

## ⚙️ 配置说明

在 `backend/src/main/resources/application.yml` 中配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/combo
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

## 📝 API 文档

启动后端后，访问 API 接口：

- 玩家接口: `/api/players`
- 好友接口: `/api/friends`
- 匹配接口: `/api/match`
- 场景接口: `/api/scene`

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

---

**作者**: [Kasugano-goose](https://github.com/Kasugano-goose)
