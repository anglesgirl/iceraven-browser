/**
 * Custom Redirect - 用户自定义重定向规则
 * 在设置页自行配置"匹配模式 => 目标URL"，命中即重定向，优先级高于内置映射。
 * 单独文件，合上游时无冲突。
 *
 * 规则文本格式（每行一条）：
 *   *://static2.onlyfans.com/* => https://cdn.example.com/app.js
 *   cdn.jsdelivr.net/npm/foo   => resources/custom/foo/foo.js
 *   # 开头为注释，空行忽略
 */

'use strict';

const customRedirect = {};

customRedirect.rules = [];

/**
 * 将规则文本解析为规则数组
 * @param {string} text - 设置页 textarea 内容
 * @returns {Array<{match: string, target: string, enabled: boolean}>}
 */
customRedirect.parse = function (text) {
    const rules = [];
    if (typeof text !== 'string') {
        return rules;
    }
    text.split(/\r?\n/).forEach(function (line) {
        const trimmed = line.trim();
        if (trimmed === '' || trimmed.startsWith('#')) {
            return;
        }
        const separator = trimmed.indexOf('=>');
        if (separator === -1) {
            return;
        }
        const match = trimmed.slice(0, separator).trim();
        const target = trimmed.slice(separator + 2).trim();
        if (match === '' || target === '') {
            return;
        }
        rules.push({
            'match': match,
            'target': target,
            'enabled': true
        });
    });
    return rules;
};

/**
 * 将规则数组序列化为文本（用于设置页回显）
 */
customRedirect.serialize = function (rules) {
    if (!Array.isArray(rules) || rules.length === 0) {
        return '';
    }
    return rules.map(function (rule) {
        return `${rule.match} => ${rule.target}`;
    }).join('\n');
};

/**
 * 编译规则，缓存正则
 */
customRedirect.load = function (rules) {
    if (!Array.isArray(rules)) {
        rules = [];
    }
    customRedirect.rules = rules.map(function (rule) {
        let expression = rule.match;
        let isWildcard = expression.indexOf('*') !== -1;
        if (isWildcard) {
            // 通配符模式：整体匹配
            expression = '^' + expression.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$';
        }
        return {
            'match': rule.match,
            'target': rule.target,
            'enabled': rule.enabled !== false,
            'isWildcard': isWildcard,
            'regex': isWildcard ? new RegExp(expression) : null
        };
    });
};

/**
 * 匹配 URL，命中返回绝对化的重定向目标，否则返回 null
 */
customRedirect.match = function (url) {
    for (const rule of customRedirect.rules) {
        if (!rule.enabled) {
            continue;
        }
        let matched;
        if (rule.isWildcard) {
            matched = rule.regex.test(url);
        } else {
            matched = url.indexOf(rule.match) !== -1;
        }
        if (matched) {
            return customRedirect.toAbsoluteUrl(rule.target);
        }
    }
    return null;
};

/**
 * 将目标转为绝对 URL：完整 URL 原样返回；其余视为扩展内路径
 */
customRedirect.toAbsoluteUrl = function (target) {
    if (/^(https?|moz-extension|chrome-extension|file|data|blob):/i.test(target)) {
        return target;
    }
    return chrome.runtime.getURL(target.replace(/^\//, ''));
};

// 设置变化时热更新缓存
chrome.storage.onChanged.addListener(function (changes, areaName) {
    if (changes[Setting.CUSTOM_REDIRECT_RULES] !== undefined) {
        customRedirect.load(changes[Setting.CUSTOM_REDIRECT_RULES].newValue);
    }
});
