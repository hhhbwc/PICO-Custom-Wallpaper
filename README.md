# PICO Custom Wallpaper

[中文](#中文) | [English](#english) | [Русский](#русский)

<a id="中文"></a>
## 中文

为 PICO 4 资源库和 PICO 设置提供独立静态壁纸和裁切预览。

### 功能

- 为资源库和 PICO 设置分别设置壁纸，也可一次应用到两者。
- 壁纸覆盖整个窗口：左侧导航栏与右侧内容区共用一张图，无缝拼接；PICO 设置页面切换后自动重新应用。
- 壁纸面板保留 PICO 原生的圆角轮廓（导航栏左圆角、内容区右圆角），不会把面板切成直角。
- 快捷设置面板按钮渲染为毛玻璃效果（实时采样壁纸），悬停高亮与焦点描边保留。
- 设置行保留指针悬停/按压反馈（白 8% / 12% 叠加），不会因为清空背景而"找不到指向哪一行"。
- 文字亮度自适应：壁纸偏亮时文字自动压暗并加浅色光晕；且保留 PICO 原有的选中/禁用状态区分，游戏封面上的标题不受影响。
- 图片编辑器使用完整的 `1125 x 750` 左右窗口比例，支持拖动调整位置与双指缩放。
- 裁切框内保持明亮，框外显示同一图片的暗化预览。
- 每个目标独立保存图片、缩放和位置。
- **未设置壁纸时模块完全无感**：不修改任何视图，PICO 原生背景、分割线、芯片样式原样保留。
- 设置页使用固定逻辑 `1280 x 720` 工作台，并等比适配 PICO 窗口。
- 设置页默认简体中文，并提供中文、English、Русский 切换。

### 更新日志

**1.1.0**
- 重构：代码拆分为 `core`（纯逻辑，含单元测试）/ `draw`（渲染）/ `data`（存储）/ `hook`（注入）。
- 修复：未设置壁纸时设置页背景被错误清空（背景发淡、分割线消失）的问题。
- 修复：壁纸面板四角由圆角变直角的问题。
- 修复：清空设置行背景导致悬停/按压反馈丢失的问题。
- 修复：文字重染压平颜色选择器，导致导航选中态/禁用态无法区分的问题。
- 修复：游戏封面上的应用名在浅色壁纸下被错误压暗的问题。
- 修复：快捷面板玻璃效果漏掉部分按钮类的问题。
- 加固：损坏/超大图片输入（读取路径 2048 上限、解码失败退避重试、NaN 变换钳制、root 命令超时）。

### 支持目标

- 资源库：`com.pvr.appmanager`，`AllAppActivity`
- PICO 设置：`com.picovr.settings`，`UnityActivity` + `QuickSettingsActivity`

### 限制

当前版本仅支持静态图片。不会修改 Android 系统壁纸、应用安装策略、包管理、签名、权限、系统语言或 Dock。

### 使用

1. 安装 APK，在模块管理器中启用，并把作用域只设为资源库和 PICO 设置。
2. 打开 **PICO 自定义壁纸**。
3. 选择图片，为资源库或 PICO 设置调整预览位置和缩放。
4. 选择“应用”，再选择资源库、PICO 设置或两者。
5. 选择“恢复默认”可移除对应目标的自定义壁纸。

### 构建

```sh
./gradlew test lint assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

<a id="english"></a>
## English

Provides independent static wallpapers and crop previews for the PICO 4 AppManager library and PICO Settings.

### Features

- Set wallpapers separately for the Library and PICO Settings, or apply one image to both.
- The wallpaper covers the whole window: the left navigation pane and right content pane share one image with a seamless seam. PICO Settings reapplies its wallpaper after page navigation.
- Wallpapaped panes keep PICO's native rounded silhouette (left-rounded rail, right-rounded content) instead of turning square.
- Quick-settings buttons render as frosted glass (live wallpaper sampling) while keeping hover highlighting and focus borders.
- Settings rows keep pointer hover/pressed feedback (8% / 12% white overlay), so the controller pointer target is never lost.
- Adaptive text contrast: on light wallpapers text darkens with a soft halo, PICO's selected/disabled state distinctions are preserved, and titles sitting on game artwork are left untouched.
- The image editor uses the full `1125 x 750` left-and-right window ratio and supports drag-to-position and pinch-to-zoom.
- The crop frame stays bright while the same image remains visible as a dimmed preview outside it.
- Each target saves its own image, scale, and position.
- **The module is fully invisible when no wallpaper is set**: no view is modified, and PICO's original backgrounds, dividers and chip styles stay intact.
- The settings page uses a fixed logical `1280 x 720` workspace that scales uniformly for the PICO window.
- The settings page defaults to Simplified Chinese and provides Chinese, English, and Russian language switching.

### Changelog

**1.1.0**
- Refactor: code split into `core` (pure logic, unit-tested) / `draw` (rendering) / `data` (storage) / `hook` (injection).
- Fix: settings background wrongly cleared when no wallpaper was configured (washed-out look, missing dividers).
- Fix: wallpapered panels losing PICO's rounded corners.
- Fix: settings rows losing hover/pressed feedback after background clearing.
- Fix: text restyling flattening color state lists, erasing selected/disabled distinctions.
- Fix: app titles on game artwork wrongly darkened on light wallpapers.
- Fix: quick-panel glass effect missing several button classes.
- Hardening: corrupt/oversized image inputs (2048 read-path cap, decode backoff, NaN clamping, root command timeout).

### Supported targets

- Library: `com.pvr.appmanager`, `AllAppActivity`
- PICO Settings: `com.picovr.settings`, `UnityActivity` + `QuickSettingsActivity`

### Limitations

This release supports static images only. It does not modify the Android system wallpaper, app-installation policy, package management, signatures, permissions, system language, or Dock.

### Use

1. Install the APK, enable the module, and scope it only to AppManager and PICO Settings.
2. Open **PICO Custom Wallpaper**.
3. Choose an image and adjust its position and scale for the Library or PICO Settings preview.
4. Select Apply, then choose the Library, PICO Settings, or both.
5. Select Restore default to remove custom wallpaper from the chosen target.

### Build

```sh
./gradlew test lint assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

<a id="русский"></a>
## Русский

Независимые статические обои и предпросмотр кадрирования для библиотеки AppManager и настроек PICO на PICO 4.

### Возможности

- Устанавливайте обои отдельно для библиотеки и настроек PICO либо применяйте одно изображение к обеим целям.
- Обои занимают всё окно: левая панель навигации и правая область содержимого используют одно изображение без шва. В настройках PICO обои повторно применяются после перехода между страницами.
- Панели с обоями сохраняют фирменные скругления PICO (левые углы навигации, правые углы содержимого) и не становятся прямоугольными.
- Кнопки быстрой настроек отображаются как матовое стекло (живая выборка обоев) с сохранением подсветки наведения и рамки фокуса.
- Строки настроек сохраняют отклик на наведение и нажатие (белая заливка 8% / 12%), поэтому указатель контроллера не «теряется».
- Адаптивная контрастность текста: на светлых обоях текст затемняется с мягким свечением, различия выбранных и отключённых состояний PICO сохраняются, а заголовки поверх значков игр не затрагиваются.
- Редактор изображения использует полное соотношение левой и правой частей окна `1125 x 750`, поддерживает перетаскивание и масштабирование жестом двумя пальцами.
- Внутри рамки кадрирования изображение остается ярким, а за ее пределами то же изображение видно в затемненном виде.
- Для каждой цели отдельно сохраняются изображение, масштаб и положение.
- **Без установленных обоев модуль полностью незаметен**: ни одно представление не изменяется, исходные фоны, разделители и стили PICO остаются нетронутыми.
- Страница настроек использует фиксированное логическое рабочее пространство `1280 x 720` с равномерным масштабированием для окна PICO.
- По умолчанию используется упрощенный китайский язык; также доступны китайский, английский и русский языки.

### Журнал изменений

**1.1.0**
- Рефакторинг: код разделён на `core` (чистая логика с тестами) / `draw` (отрисовка) / `data` (хранение) / `hook` (внедрение).
- Исправление: очистка фона настроек при отсутствии обоев (бледный вид, пропавшие разделители).
- Исправление: потеря скруглённых углов панелей с обоями.
- Исправление: потеря отклика наведения и нажатия у строк настроек.
- Исправление: выравнивание списков цветов текстом, из-за чего пропадали различия выбранного и отключённого состояний.
- Исправление: неверное затемнение заголовков на значках игр при светлых обоях.
- Исправление: эффект стекла в быстрой настройке пропускал часть кнопок.
- Устойчивость: повреждённые и огромные изображения (предел 2048 на пути чтения, задержка после сбоя декодирования, ограничение NaN, тайм-аут команд root).

### Поддерживаемые цели

- Библиотека: `com.pvr.appmanager`, `AllAppActivity`
- Настройки PICO: `com.picovr.settings`, `UnityActivity` + `QuickSettingsActivity`

### Ограничения

Текущая версия поддерживает только статические изображения. Модуль не изменяет системные обои Android, политику установки приложений, управление пакетами, подписи, разрешения, системный язык или Dock.

### Использование

1. Установите APK, включите модуль и задайте область действия только для AppManager и настроек PICO.
2. Откройте **PICO Custom Wallpaper**.
3. Выберите изображение и настройте его положение и масштаб для библиотеки или настроек PICO.
4. Нажмите «Применить», затем выберите библиотеку, настройки PICO или оба варианта.
5. Нажмите «Восстановить по умолчанию», чтобы убрать пользовательские обои с выбранной цели.

### Сборка

```sh
./gradlew test lint assembleDebug
```

Отладочный APK создается по пути:

```text
app/build/outputs/apk/debug/app-debug.apk
```
