package com.modscantrans.core.cfpa;

import java.util.Optional;

/**
 * CFPA 社区人工汉化词条拉取客户端(加载器无关 SPI)。
 *
 * <p>core 层只定义接口,默认实现 {@link HttpCfpaClient} 使用 JDK {@code HttpClient};
 * 适配层可注入自定义实现(例如走缓存代理或本地镜像)。
 *
 * <p><b>硬性规则</b>:词条全局优先级最高(CFPA &gt; AI 缓存 &gt; 实时 AI),
 * 命中后该 key 禁止被 AI 翻译覆盖。<b>网络异常时返回 {@link Optional#empty()},
 * 不抛异常、不阻塞流程</b>。
 */
public interface CfpaClient {
    /**
     * 刷新 CFPA 资源索引(拉取全树路径并构建 modid→版本→路径映射)。
     *
     * <p>网络异常时返回 {@link CfpaIndex#empty()},不抛异常。
     *
     * @return 刷新后的索引(可能为空)
     */
    CfpaIndex refreshIndex();

    /**
     * 按 modID + MC 版本拉取 CFPA 人工汉化词条。
     *
     * <p>实现应先查索引得路径,再拉取对应 {@code zh_cn.json} 并解析。
     * 版本匹配含归并与(可选)跨版本回退。任何网络 / 解析异常均返回 empty。
     *
     * @param modId           模组 modid(namespace)
     * @param mcVersion       用户 MC 版本(如 {@code 1.21.1})
     * @param enableFallback  当前版本族未命中时是否回退到更老版本
     * @return 命中的 CFPA 资源;未命中 / 网络异常返回 empty
     */
    Optional<CfpaResource> fetch(String modId, String mcVersion, boolean enableFallback);
}
