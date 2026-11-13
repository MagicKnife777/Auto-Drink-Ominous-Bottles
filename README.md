# Auto-Drink Ominous Bottles (MC 26.3 Fabric Edition)

A client-side Fabric mod that automatically drinks an Ominous Bottle when a raid ends. It also includes a built-in Auto-Attack feature.

> **Note / Credits:**
> This project is a modified version based on the original *Auto-Drink Ominous Bottles* mod by **shumsic**. The original source code was not written by me.
>
> My contributions (MagicKnife) include:
> 1. Porting and adapting the mod to **Minecraft 26.3 / Fabric** (rewrote the underlying drinking logic).
> 2. Implementing a new **Auto-Attack** feature (via reflection) with an 80gt interval.
> 3. Refactoring package structure and adding multi-language (i18n) support.
>
> *Disclaimer: The code modifications were heavily assisted by AI. The Auto-Attack feature uses reflection to access a method that became `private` in MC 26.3, which may break in future updates.*

## Features

*   **Auto-Drink:** Automatically detects the end of a raid and drinks an Ominous Bottle.
*   **Smart Raid Detection:** Scans for `Raider` entities within 100 blocks instead of relying on the flickering Boss Bar.
*   **Auto-Attack:** Triggers an attack every 80 game ticks (4 seconds). Perfect for dealing with lingering mobs.
*   **Safety Windows:** Waits for a safe period (16-30s after raiders are dead) before drinking to prevent accidental triggers.
*   **Smart Selection:** Prioritizes the highest Omen level bottle available.

## Keybindings

Go to: `Options` → `Controls` → `Key Binds`

*   **R Key**: Toggle Auto-Drink (Enable / Disable)
*   **G Key**: Toggle Auto-Attack (Enable / Disable)

Toggling will show a small action bar message. The text automatically adapts to your game language (English / 简体中文).

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Download the latest `.jar` from the [Releases](https://github.com/MagicKnife777/Auto-Drink-Ominous-Bottles/releases) page.
3. Drop the `.jar` into your `.minecraft/mods` folder.
4. *This is a client-side only mod; it does not need to be installed on the server.*

---
---

# 自动喝不祥之瓶 (MC 26.3 Fabric 版本)

一个客户端 Fabric 模组。当袭击（Raid）结束时，自动喝下不祥之瓶。并内置了自动连点（攻击）功能。

> **致谢与说明：**
> 本项目基于原作者 **shumsic** 的 *Auto-Drink Ominous Bottles* 修改而来。
>
> 我的贡献（MagicKnife）：
> 1. 将模组移植并适配至 **Minecraft 26.3 / Fabric** 版本（重写了失效的底层喝药逻辑）。
> 2. 新增了 **自动连点** 功能（通过反射调用，间隔为 80gt）。
> 3. 重构了包名结构，并添加了多语言（i18n）支持。
>
> *免责声明：代码修改过程由 AI 大量辅助完成。自动连点功能使用了反射机制来调用在 MC 26.3 中变为 `private` 的方法，这在未来的游戏版本更新中可能会失效。*

## 功能特性

*   **自动喝药**：自动检测袭击结束，并喝下不祥之瓶。
*   **智能袭击检测**：不再依赖会闪烁的 Boss 条，而是扫描周围 100 格内的灾厄村民实体。
*   **自动连点**：每 80 个游戏刻（4秒）自动触发一次攻击，非常适合清理残血怪物。
*   **安全窗口**：袭击结束后，会等待 16-30 秒的安全期再喝药，防止误触。
*   **智能选择**：自动优先选择背包中最高等级的不祥之瓶。

## 按键绑定

前往：`选项` → `控制` → `按键绑定`

*   **R 键**：开启/关闭 自动喝药
*   **G 键**：开启/关闭 自动连点

*(切换时会在物品栏上方显示提示，自动适配中英文)*

## 安装方法

1. 为 Minecraft 26.3 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 从 [Releases](https://github.com/MagicKnife777/Auto-Drink-Ominous-Bottles/releases) 页面下载最新的 `.jar` 文件。
3. 将 `.jar` 文件放入 `.minecraft/mods` 文件夹中。
4. *纯客户端模组，无需在服务器端安装。*