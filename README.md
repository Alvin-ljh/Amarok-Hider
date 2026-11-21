# Amarok 改造：逐应用隐藏 + 列表内启动（Root + XHide）

这是一个针对 [deltazefiro/Amarok-Hider](https://github.com/deltazefiro/Amarok-Hider) `main` 分支的最小改造，
目标是把「只能一键全部隐藏」改成 Hail 那样**逐应用隐藏/恢复**，并且**在列表里点一下就能启动应用**。

- 只考虑 **Root 模式 + XHide**（其它隐藏模式代码未动，仍然可用但未测试）
- **没有改动文件隐藏**（保持原样，不用可以无视）
- 没有新增布局/菜单资源，交互是「点列表项 → 弹出操作对话框」

---

## 1. 改了什么

| 文件 | 改动 |
|---|---|
| `app/src/main/java/deltazero/amarok/Hider.java` | 新增逐应用 `hideApp()` / `unhideApp()` / `launchApp()`，用 libsu 直接执行 `pm disable/hide`、`pm enable/unhide`；新增「当前已隐藏包名集合」的持久化；全局隐藏/恢复时同步该集合 |
| `app/src/main/java/deltazero/amarok/utils/XHidePrefBridge.java` | `commitNewValues()` 改为 public；推给 XHide 的包名从「隐藏清单」改为「**当前实际隐藏的包名**」，且 `IS_ACTIVE = 集合非空 && XHide 开启` |
| `app/src/main/java/deltazero/amarok/ui/AppListAdapter.java` | 列表项增加单击回调（不影响原有 CheckBox） |
| `app/src/main/java/deltazero/amarok/ui/SetHideAppActivity.java` | 单击列表项弹出操作框：**启动应用 / 立即隐藏 / 取消隐藏**；启动已隐藏的应用会先自动取消隐藏再启动 |

效果：

- 勾选框仍然是「一键隐藏清单」的成员，主界面的隐藏按钮行为不变；
- 在应用列表里对单个应用执行「立即隐藏」后，该应用被 `pm disable + pm hide`，同时被写进 XHide 的过滤集合 —— 因此**单应用隐藏也是彻底的**（系统设置、第三方包列表都看不到）；
- 单击条目可以直接启动任意应用；对已隐藏的应用会先 `pm enable + pm unhide` 再 `startActivity`；
- 「一键全部隐藏/恢复」仍然可用，恢复时会清空逐应用隐藏集合。

---

## 2. 没有 Android Studio 怎么编译

仓库自带 GitHub Actions 工作流（`.github/workflows/build_on_push.yml`），push 后会自动
`assembleFossDebug` 并把 APK 作为 artifact 上传。所以只需要一个 GitHub 账号：

1. **Fork** `deltazefiro/Amarok-Hider` 到你自己的账号。
2. 在你的 fork 里，把本目录下这 4 个文件按相同路径替换掉：
   - `app/src/main/java/deltazero/amarok/Hider.java`
   - `app/src/main/java/deltazero/amarok/utils/XHidePrefBridge.java`
   - `app/src/main/java/deltazero/amarok/ui/AppListAdapter.java`
   - `app/src/main/java/deltazero/amarok/ui/SetHideAppActivity.java`

   做法：在仓库页面按 `.` 打开网页版编辑器（github.dev），进入对应文件全选粘贴后提交；
   或者用 GitHub 的 “Upload files” 直接拖入这 4 个文件（保持目录结构）。
3. 提交后进入 **Actions → Build on push**，等构建完成，在页面底部下载 artifact
   `app-foss-debug`（zip 里是 `app-foss-debug.apk`）。
4. 安装前注意（**很重要**）：
   - debug 包用的是 debug 签名，**不能覆盖安装**官方版/旧版，必须先卸载 Amarok；
   - 卸载前先在旧版里**取消隐藏**所有应用（否则隐藏列表会丢失，应用仍处于 disable/hide 状态，需要按官方 FAQ 手动恢复）；
   - 卸载 → 安装新 APK → 重新配置隐藏清单。
5. 打开 LSPosed，Amarok 模块作用域勾选 **系统框架（android）**，重启手机；再进 Amarok 打开 XHide，
   应用隐藏模式选 **Root**。

以后我改了代码，重复第 2~3 步即可（每次都要重新下载 APK 覆盖安装，签名相同可以直接覆盖升级）。

---

## 3. 用法

主界面 →「设置隐藏应用」→ 列表里：

- **单击某个应用** → 弹窗：`启动应用` / `立即隐藏` / `取消隐藏`
  - 标题后面带「（已隐藏）」表示该包当前处于隐藏状态；
  - 点「启动应用」时如果它是隐藏的，会先自动取消隐藏再启动（Android 不允许直接启动隐藏包）；
- **右侧勾选框** → 决定该应用是否属于「一键隐藏」清单（原逻辑不变）；
- 主界面「隐藏 / 取消隐藏」→ 仍然是整批操作。

> 说明：逐应用隐藏后，主界面开关仍显示「未隐藏」（因为全局状态没有被标记为 HIDDEN）。
> 这是有意为之的最小改动，避免影响磁贴、悬浮球、文件隐藏等依赖全局状态的逻辑。

---

## 4. 已知限制 / 后续可以做的

- 只实现了 Root 路径的逐应用隐藏（用 `su` 执行 `pm`），Shizuku/Dhizuku 下的逐应用隐藏未实现；
- 没有做「编组」：如果你需要 A 组 / B 组分别一键切换，可以下一步在 `PrefMgr` 里加
  `Map<组名, Set<包名>>`，列表顶部加 chip，点击时调用 `Hider.hideApp()` 循环；
- 隐藏某个应用后，桌面图标消失属于系统行为；恢复后图标位置可能乱（Amarok 官方文档已说明）；
- 隐藏系统应用/设备管理员类应用可能失败或被系统拒绝（`pm` 会返回错误，日志里能看到）。

---

## 5. 如果 CI 编译失败

Actions 里会给出具体报错（文件:行号）。把那段报错贴给我，我按提示修正后你再提交一次即可。
常见原因只会是我改动的这 4 个文件的语法/导入问题，修起来很快。
