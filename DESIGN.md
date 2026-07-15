# Destination Compass Design System

Destination Compass 是适合户外与室内混合光线的克制型 Material 3 工具。Android 12+ 使用 Material You 动态配色；回退主题以清透蓝为主色、珊瑚红为目标指针强调色。

- 使用 Android 系统字体与 Material 3 字级，数值采用清晰的等宽数字特性。
- 页面背景使用 `surface`，功能区使用 `surfaceContainer` 系列；卡片圆角 16–24dp。
- 罗盘是首页唯一视觉中心，保持大面积留白，不叠加装饰性地图。
- 所有交互控件至少 48dp；正文与背景满足 WCAG AA。
- spring 用于罗盘指针，Shared Axis 用于主页面切换，Container Transform 用于收藏卡片展开。
