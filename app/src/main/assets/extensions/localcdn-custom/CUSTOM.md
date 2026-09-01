# LocalCDN-custom

Fork of [nobody/LocalCDN](https://codeberg.org/nobody/LocalCDN) - 保留全部 CDN 加速，只加私货。

## 私货规则

- `static2.onlyfans.com/*` => `resources/custom/onlyfans/app.jsm` (本地嵌入)
- 文件：`core/custom-resources.js` + `core/custom-mappings.js` (单独文件，合上游无冲突)
- 内置：`pages/background/background.html` 已注入

## 维护

```bash
# 同步上游
git fetch upstream
git merge upstream/main
# 加新规则：只改 core/custom-* + resources/custom/
```

## 构建

Firefox: `web-ext build` 或 `make` 生成 xpi，Iceraven 内置到 `assets/extensions/`
