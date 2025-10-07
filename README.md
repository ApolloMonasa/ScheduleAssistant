

# 🏢 社团值班表助手 (Club Duty Scheduler)

一款专为社团办公室设计的安卓排班应用，告别繁琐的人力排班，一键生成智能值班表。

![语言](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![平台](https://img.shields.io/badge/Platform-Android-brightgreen.svg)
![状态](https://img.shields.io/badge/Status-Active-yellow.svg)
![许可证](https://img.shields.io/badge/License-MIT-orange.svg)

## 💡 项目背景

社团办公室的排班工作，长久以来都依赖于社团成员在群聊中的口头协调和人工记录。这种方式不仅效率低下，容易出错，而且在学期末交接时，新成员很难快速上手。

为了解决这个痛点，并方便社团的技术成果能够轻松地传承下去，我决定开发这款安卓App。相比于需要服务器和域名维护的Web应用，安卓App一次开发，即可通过安装包轻松传递，非常适合社团这种使用频率不高但又必不可少的场景。

## ✨ 功能亮点

- 📂 **人员信息管理**：
  - 支持从Excel文件一键批量导入社员名单，告别手动录入。
  - 支持在App内对人员信息进行独立的**增、删、改、查**操作，管理灵活。

- ⚙️ **智能排班配置**：
  - **年级配额**：可根据社团实际情况，自由设置不同年级的排班名额。
  - **权限限制**：可设置“高年级陪同”规则，确保每个班次都有经验丰富的成员在场；同时可以限制特定成员（如低年级）参与晚班。

- 📅 **班次灵活定义**：
  - 轻松创建、修改或删除值班时段（例如：周一上午、周三晚上）。
  - 自由定义每个时间段需要的值班人数，满足不同时段的需求。

- 🤖 **一键生成班表**：
  - 根据预设的人员信息和排班规则，App将自动进行排班运算，生成初步的值班表，大大节省组织者的时间和精力。

## 📱 应用截图


<p align="center">
  <img src="https://free.picui.cn/free/2025/10/07/68e40efe4c100.png" alt="主界面" style="margin-right: 10px;"/>
  <img src="https://free.picui.cn/free/2025/10/07/68e410a319c7b.png" alt="人员管理" style="margin-right: 10px;"/>
  <img src="https://free.picui.cn/free/2025/10/07/68e40f45b2971.png" alt="排班配置" style="margin-right: 10px;"/>
  <img src="https://free.picui.cn/free/2025/10/07/68e40f9082cd5.png" alt="设置页面" style="margin-right: 10px;"/>
  <img src="https://free.picui.cn/free/2025/10/07/68e40ed6dc7eb.png" alt="排班结果"/>
</p>

## 🚀 未来展望 & 已知问题

我们深知这个项目还有很大的提升空间，目前主要存在以下几点：

1.  **算法优化**
    - `[待提升]` 当前的排班算法能满足基本需求，但并非最优解。未来计划引入更完善的公平性算法和权重分配，让排班结果更加人性化。算法在无解区会出现奇奇怪怪的错误。

2.  **界面美化 (UI/UX)**
    - `[待提升]` 界面设计以功能实现为优先，美观度有待提高。欢迎有设计想法的同学贡献力量，一起让它变得更漂亮！

## 🛠️ 技术栈

- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose
- **数据库**: Room
- **Excel处理**: Apache POI 

## 许可协议

本项目采用 [MIT License](LICENSE) 开源协议。
