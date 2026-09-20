# LocalCDN-custom

Fork of [nobody/LocalCDN](https://codeberg.org/nobody/LocalCDN) - 保留全部 CDN 加速，只加私货。

## 私货规则

- `static2.onlyfans.com/*` => `resources/custom/onlyfans/app.jsm` (本地嵌入)
- 文件：`core/custom-resources.js` + `core/custom-mappings.js` (单独文件，合上游无冲突)
- 内置：`pages/background/background.html` 已注入

## 自定义重定向（v2.6.86+）

设置页 **Advanced → Custom redirect rules** 可自行配置重定向规则，优先级高于内置映射：

- 格式：每行一条 `匹配模式 => 目标`，`*` 为通配符，`#` 开头为注释
- 目标可为完整 URL（`https://...`）或扩展内路径（`resources/custom/...`，自动 resolve）
- 运行时：`core/custom-redirect.js`（storage.onChanged 热更新，无需重启）
- 文件：`core/custom-redirect.js` + 设置页 `pages/options/*` + `core/constants.js` + `core/main.js` + `core/interceptor.js`

示例：
```
*://static2.onlyfans.com/* => https://cdn.example.com/app.js
cdn.jsdelivr.net/npm/foo   => resources/custom/foo/foo.js
```

## 维护

```bash
# 同步上游
git fetch upstream
git merge upstream/main
# 加新规则：只改 core/custom-* + resources/custom/
```

## 构建

Firefox: `web-ext build` 或 `make` 生成 xpi，Iceraven 内置到 `assets/extensions/`
