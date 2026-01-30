# NoSpawn
为 Minecraft Paper 服务器设计的区域保护插件。在世界出生点或任意坐标周围设定一个球形保护区域，阻止区域内怪物的生成，提供细粒度的控制、日志、热管理命令

## 功能
- **自定义区域保护**:以设定点为中心设置半径及Y轴的圆柱形保护/以设定点为中心设置X、Y、Z三轴延伸形成立方体保护
- **自定义过滤**：可选择阻止所有怪物，或仅阻止自定义列表中的特定实体，支持过滤生成来源（刷怪笼/刷怪蛋/繁殖），支持过滤指定世界区域（主世界/地狱/末地）
- **日志记录**：支持将拦截记录写入按日期分割的日志文件，并可开关控制台实时输出，记录生成位置/怪物类型/生成原因和关联玩家信息
- **交互反馈**：当玩家进出保护区域时可给予反馈（消息/PUSH效果/音效）
- **边缘可视化**：通过盔甲架即时生成投影边缘，便于调试保护范围
- **命令热管理**：提供命令实时重载配置、热开关插件和日志功能，无需重启服务器


### 开发环境
- **JDK**: 21
- **工具**: Gradle
- **API**: Paper-API 1.20.4
- **开发工具**: IntelliJ IDEA

### 运行环境
- **服务器核心**: **Paper** 1.20.4 或兼容的 Spigot 核心
- **Java版本**: 服务器必须运行在 **Java 21** 或更高版本上
- **依赖**: 无需额外依赖库

## 使用
1. 将插件放入服务器的 `plugins/` 文件夹
2. 启动服务器，插件会自动生成配置文件 (`plugins/NoSpawnPlugin/config.yml`)
3. 按需编辑配置文件，并在游戏内使用 `/ns reload` 重载

## 配置详细说明

**区域设置** (`region`):
- `mode`: 区域模式，`circle`（圆形/圆柱体）或 `square`（方形/立方体）
- `radius`: 圆形模式半径（格数）
- `circle-extends-y`: 圆形模式Y轴范围（上下各多少格）
- `extends.x/y/z`: 方形模式三轴延伸范围

**虚拟墙壁** (`virtual-wall`):
- `feedback-type`: 反馈类型，`NONE`（无）、`MESSAGE`（消息）、`PUSH_BACK`（击退）、`BOTH`（两者）
- `push-back-strength`: 击退强度（0.0-2.0）
- `play-sound`: 是否播放音效

**边界可视化** (`boundary-visualization`):
- `marker-spacing`: 盔甲架间距，值越小边界点越密集（圆形建议6，方形建议3）
- `duration-seconds`: 投影持续时间（秒）

## 命令与权限
主命令：`/nospawn` 或 `/ns`

**基础命令**:
- `/ns help` - 显示帮助
- `/ns toggle` - 开关插件
- `/ns reload` - 重载配置
- `/ns mode <circle|square>` - 切换区域模式（圆形/方形）
- `/ns visualize <on|off>` - 显示/隐藏边界投影
- `/ns log <on|off>` - 开关文件日志
- `/ns status` - 查看状态

**虚拟墙壁个性化命令** (`/ns vm`):
- `/ns vm toggle` - 开关个人虚拟墙壁
- `/ns vm feedback <MESSAGE|PUSH|BOTH>` - 设置反馈类型
- `/ns vm sound <on|off>` - 开关音效
- `/ns vm status` - 查看当前设置
- `/ns vm reset` - 重置为服务器默认值

**权限说明**:
- `nospawn.virtualwall` - 使用虚拟墙壁功能（默认所有玩家）
- `nospawn.admin` - 包含所有管理权限（默认OP）

## 构建与安装

**从源代码构建**:
1. 克隆仓库: `git clone https://github.com/await591/NoSpawnPlugin.git`
2. 进入目录: `cd NoSpawnPlugin`
3. 构建插件: `./gradlew build`（需要Java 21）
4. 生成的JAR文件位于 `build/libs/NoSpawnPlugin-版本号.jar`

**服务器安装**:
1. 将生成的JAR文件放入服务器的 `plugins/` 文件夹
2. 启动服务器，插件会自动生成配置文件
3. 编辑 `plugins/NoSpawnPlugin/config.yml` 调整设置
4. 在游戏中使用 `/ns reload` 重载配置

## 开发信息

**项目结构**:
```
src/main/java/art/await591/nospawn/
├── NoSpawnPlugin.java      # 主插件类，事件监听和配置管理
├── NoSpawnCommand.java     # 命令处理器，包含Tab补全逻辑
├── RegionVisualizer.java   # 虚拟墙壁和边界可视化功能
└── LoggerManager.java      # 日志记录系统
```

**构建系统**:
- 使用Gradle构建工具
- 依赖PaperMC开发包（1.20.4-R0.1-SNAPSHOT）

## 更新日志
- **V1.2.0**: 新增虚拟墙壁个性化设置（/ns vm命令），支持玩家独立配置和持久化存储
- **V1.1.1**: 修复出入保护区颜色错误
- **V1.1.0**：支持保护区域自定义（添加圆形或方形可选项），支持Y轴范围自定义。添加交互功能，进出保护区域时给予消息等反馈。添加边缘可视化，生成投影查看保护范围
- **V1.0.0**：支持圆形范围保护，自定义过滤实体，命令热管理
 
## 待办事项
- **日志过滤器**: 添加过滤条件，允许根据实体类型、生成原因等条件筛选日志记录
- **边缘击退反馈冷却**: 为击退效果添加冷却时间，防止玩家在边界附近反复触发击退
- **过滤项修复**：应根据拦截实体列表决定豁免生成因素，而非拦截与豁免分开处理
