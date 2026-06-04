package com.yupi.yuaiagent.profile;

import com.yupi.yuaiagent.profile.model.UserProfile;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 用户画像服务。
 * <p>
 * 负责画像的抽取编排、合并、查询、清空与提示词注入，是用户画像系统对外的统一入口。
 * 协作组件：
 * <ul>
 *     <li>{@link UserProfileExtractor}：基于对话内容 LLM 抽取画像维度（永不返回 null）</li>
 *     <li>{@link UserProfileRepository}：合并去重并持久化画像（以 userId 唯一存储）</li>
 *     <li>{@link ProfilePromptBuilder}：将画像转换为可注入 system prompt 的中文片段</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Service
public class UserProfileService {

    @Resource
    private UserProfileRepository repository;

    @Resource
    private UserProfileExtractor extractor;

    @Resource
    private ProfilePromptBuilder promptBuilder;

    /**
     * 对话结束后异步触发画像更新：抽取 → 合并 → 持久化（merge 内部已持久化）。
     * <p>
     * 全流程在 {@link CompletableFuture#runAsync(Runnable)} 中执行，不阻塞调用方
     * （不阻塞向用户返回本次对话响应，Req 11.6）。当抽取或合并过程发生异常时，
     * 仅记录错误日志并保留该 userId 已有画像不变，异常不向外传播（Req 11.5）。
     *
     * @param userId       用户唯一标识
     * @param conversation 本次对话的消息列表
     */
    public void updateAsync(String userId, List<Message> conversation) {
        CompletableFuture.runAsync(() -> {
            try {
                // LLM 抽取（失败时 extractor 返回空画像，不会抛出异常）
                UserProfile extracted = extractor.extract(conversation);
                // 合并去重 + 刷新 updatedAt，并持久化（Req 11.2/11.3/11.4）
                repository.merge(userId, extracted);
            } catch (Exception e) {
                // 抽取/合并失败：记录错误日志并保留原画像不变（Req 11.5）
                log.error("用户 {} 画像更新失败，保留原画像不变", userId, e);
            }
        });
    }

    /**
     * 查询指定 userId 的画像。
     *
     * @param userId 用户唯一标识
     * @return 对应画像，不存在时返回 {@link Optional#empty()}
     */
    public Optional<UserProfile> get(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * 清空指定 userId 的画像并持久化删除结果（Req 13.3）。
     *
     * @param userId 用户唯一标识
     */
    public void clear(String userId) {
        repository.deleteByUserId(userId);
    }

    /**
     * 生成用于注入 system prompt 的画像片段（含字符上限，Req 12 / 19）。
     *
     * @param userId 用户唯一标识
     * @return 画像提示片段；当该 userId 尚无画像时返回空字符串 ""，
     * 调用方据此走不含画像信息的默认 prompt（Req 12.4）
     */
    public String buildPromptInjection(String userId) {
        return repository.findByUserId(userId)
                .map(promptBuilder::build)
                .orElse("");
    }
}
