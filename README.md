# PICO Custom Wallpaper

[中文](#中文) | [English](#english) | [Русский](#русский)

<a id="中文"></a>
## 中文

为 PICO 4 资源库和 PICO 设置提供独立静态壁纸和裁切预览。

### 功能

- 分别为资源库和 PICO 设置设置壁纸，也可一次应用到两者。
- 壁纸会覆盖左侧导航栏和右侧内容区；PICO 设置页面切换后会自动重新应用。
- 图片编辑器使用完整的 `1125 x 750` 左右窗口比例，支持拖动调整位置与双指缩放。
- 裁切框内保持明亮，框外显示同一图片的暗化预览。
- 每个目标独立保存图片、缩放和位置。
- 首次使用及恢复默认后保持 PICO 原始背景；可单独恢复资源库、PICO 设置或两者。
- 设置页使用固定逻辑 `1280 x 720` 工作台，并等比适配 PICO 窗口。
- 设置页默认简体中文，并提供中文、English、Русский 切换。

### 支持目标

- 资源库：`com.pvr.appmanager`，`AllAppActivity`
- PICO 设置：`com.picovr.settings`，`UnityActivity`

### 限制

当前版本仅支持静态图片。不会修改 Android 系统壁纸、应用安装策略、包管理、签名、权限、系统语言或 Dock。

### 使用

1. 安装 APK，并在模块管理器中启用后只作用于资源库和 PICO 设置。
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
- Wallpapers cover the left navigation pane and right content pane. PICO Settings reapplies its wallpaper after page navigation.
- The image editor uses the full `1125 x 750` left-and-right window ratio and supports drag-to-position and pinch-to-zoom.
- The crop frame stays bright while the same image remains visible as a dimmed preview outside it.
- Each target saves its own image, scale, and position.
- Original PICO backgrounds remain active until an image is applied or after restoring defaults. Restore the Library, PICO Settings, or both independently.
- The settings page uses a fixed logical `1280 x 720` workspace that scales uniformly for the PICO window.
- The settings page defaults to Simplified Chinese and provides Chinese, English, and Russian language switching.

### Supported targets

- Library: `com.pvr.appmanager`, `AllAppActivity`
- PICO Settings: `com.picovr.settings`, `UnityActivity`

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

- Устанавливайте обои отдельно для библиотеки и настроек PICO либо применяйте одно изображение к обоим целям.
- Обои применяются к левой панели навигации и правой области содержимого. В настройках PICO обои повторно применяются после перехода между страницами.
- Редактор изображения использует полное соотношение левой и правой частей окна `1125 x 750`, поддерживает перетаскивание и масштабирование жестом двумя пальцами.
- Внутри рамки кадрирования изображение остается ярким, а за ее пределами то же изображение видно в затемненном виде.
- Для каждой цели отдельно сохраняются изображение, масштаб и положение.
- До применения изображения и после восстановления используются исходные фоны PICO. Можно отдельно восстановить библиотеку, настройки PICO или оба фона.
- Страница настроек использует фиксированное логическое рабочее пространство `1280 x 720` с равномерным масштабированием для окна PICO.
- По умолчанию используется упрощенный китайский язык; также доступны китайский, английский и русский языки.

### Поддерживаемые цели

- Библиотека: `com.pvr.appmanager`, `AllAppActivity`
- Настройки PICO: `com.picovr.settings`, `UnityActivity`

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
