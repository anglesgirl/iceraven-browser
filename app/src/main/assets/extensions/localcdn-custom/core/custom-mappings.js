'use strict';

/**
 * Custom Mappings - 私货映射
 * 在 LocalCDN 官方映射基础上追加，单独文件避免合 upstream 冲突
 * 规则：static2.onlyfans.com/* => 本地 resources/custom/onlyfans/
 * 以后加新规则只改这里 + resources/custom/ 下加文件
 */

// 等 mappings 加载后再追加
if (typeof mappings !== 'undefined' && mappings.cdn) {
    mappings.cdn['static2.onlyfans.com'] = {
        '/': {
            '': resources.onlyfansStatic2
        }
    };
    // 如需更细粒度，可按路径区分：
    // mappings.cdn['static2.onlyfans.com'] = {
    //   '/assets/': { 'app.': resources.onlyfansStatic2 },
    //   '/css/': { '': resources.onlyfansStatic2 }
    // };
}
