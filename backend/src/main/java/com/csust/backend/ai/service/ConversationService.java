package com.csust.backend.ai.service;

import com.csust.backend.ai.entity.AiConversation;
import com.csust.backend.ai.entity.AiMessage;
import com.csust.backend.ai.repository.AiConversationRepository;
import com.csust.backend.ai.repository.AiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final TransactionTemplate transactionTemplate;

    /** 超过此消息数时触发摘要（约 6 轮对话） */
    private static final int SUMMARIZE_THRESHOLD = 12;
    /** 摘要时保留最近 N 条消息不压缩 */
    private static final int SUMMARIZE_KEEP = 8;
    /** 摘要最大 token 数 */
    private static final int MAX_SUMMARY_TOKENS = 25000;
    /** 最多压缩轮数（防止死循环） */
    private static final int MAX_COMPRESS_ROUNDS = 3;

    // ==================== 对话管理 ====================

    /**
     * 获取或创建用户的唯一对话。
     */
    @Transactional(timeout = 5)
    public AiConversation getOrCreateConversation(Long userId) {
        return conversationRepository.findByUserId(userId)
                .orElseGet(() -> {
                    AiConversation conv = new AiConversation();
                    conv.setUserId(userId);
                    return conversationRepository.save(conv);
                });
    }

    /**
     * 获取用户的对话（可能为 null）。
     */
    public AiConversation getByUserId(Long userId) {
        return conversationRepository.findByUserId(userId).orElse(null);
    }

    // ==================== 消息管理 ====================

    /**
     * 分页获取消息（按时间倒序，最新在前）。
     */
    public Page<AiMessage> getMessages(Long conversationId, int page, int size) {
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    /**
     * 获取对话的所有消息（按时间正序，用于上下文组装）。
     */
    public List<AiMessage> getAllMessagesAsc(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional(timeout = 5)
    public AiMessage saveMessage(Long conversationId, String role, String content,
                                  String sourcesJson, Boolean fromKnowledge, String status) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSources(sourcesJson);
        msg.setFromKnowledge(fromKnowledge);
        msg.setStatus(status);
        return messageRepository.save(msg);
    }

    @Transactional(timeout = 5)
    public void updateMessageStatus(Long messageId, String content, String status, String sourcesJson) {
        AiMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        msg.setContent(content);
        msg.setStatus(status);
        if (sourcesJson != null) {
            msg.setSources(sourcesJson);
        }
        messageRepository.save(msg);
    }

    @Transactional(timeout = 5)
    public void deleteMessage(Long id, Long userId) {
        AiMessage msg = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        // 验证该消息属于当前用户
        AiConversation conv = conversationRepository.findById(msg.getConversationId())
                .orElseThrow(() -> new RuntimeException("对话不存在"));
        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此消息");
        }
        messageRepository.delete(msg);
    }

    // ==================== 上下文窗口（token 估算） ====================

    /**
     * 估算字符串的 token 数：中文 ≈ 1.5 字/token。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 1.5);
    }

    /**
     * 构建上下文窗口，返回组装好的 prompt 各部分。
     *
     * @return [系统提示文本, 对话历史消息列表, 总 token 数]
     */
    @SuppressWarnings("unchecked")
    public Object[] buildContextWindow(Long conversationId, int windowSize, int hardLimit) {
        AiConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("对话不存在"));

        // 系统提示
        String systemPrompt = """
                你是「生活帖」平台的 AI 问答助手，一个生活分享社区。
                你拥有与用户的完整对话历史（包括早期摘要和近期消息列表），请务必利用这些历史记录回答用户。
                当用户要求回顾之前聊过的内容时，你应该直接从提供的对话历史中查找并回答，不要说"无法查看"或"无法回顾"。
                请用友好、专业的中文回答用户的问题。
                回答应简洁明了，条理清晰。
                """.stripIndent();

        // 如果有摘要，追加到系统提示
        String summaryContext = "";
        if (conv.getSummary() != null && !conv.getSummary().isEmpty()) {
            summaryContext = "\n\n【历史对话摘要（早期对话的压缩记录）】\n" + conv.getSummary()
                    + "\n\n注意：上述摘要是早期对话的压缩记录，下方的消息列表是你与用户更近期的完整对话记录。请将摘要和消息列表结合，视为完整的对话历史。";
        }

        // 获取最近的消息（滑窗）
        List<AiMessage> allMessages = getAllMessagesAsc(conversationId);
        int totalTokens = estimateTokens(systemPrompt + summaryContext);

        // 从后往前取 windowSize 条消息
        int startIdx = Math.max(0, allMessages.size() - windowSize);
        List<AiMessage> windowMessages = allMessages.subList(startIdx, allMessages.size());

        // 计算总 token，如果超过硬上限则丢弃最早的消息
        for (AiMessage msg : windowMessages) {
            totalTokens += estimateTokens(msg.getRole() + ": " + msg.getContent());
        }

        while (totalTokens > hardLimit && !windowMessages.isEmpty()) {
            AiMessage removed = windowMessages.remove(0);
            totalTokens -= estimateTokens(removed.getRole() + ": " + removed.getContent());
        }

        return new Object[]{systemPrompt + summaryContext, windowMessages, totalTokens};
    }

    // ==================== 自动摘要压缩 ====================

    /**
     * 异步触发摘要更新。由 RagService.chat 在用户消息落库后调用。
     */
    @Async("aiSummaryExecutor")
    public void maybeUpdateSummaryAsync(Long conversationId) {
        try {
            transactionTemplate.executeWithoutResult(status -> doUpdateSummary(conversationId));
        } catch (Exception e) {
            log.error("生成对话摘要失败，conversationId={}: {}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 消息数超过阈值时，调用 AI 对旧消息生成问答轮摘要并存入 AiConversation.summary。
     */
    private void doUpdateSummary(Long conversationId) {
        try {
            List<AiMessage> allMessages = getAllMessagesAsc(conversationId);
            if (allMessages.size() <= SUMMARIZE_THRESHOLD) {
                return;
            }

            AiConversation conv = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("对话不存在"));

            // 需要压缩的消息 = 旧消息（去掉最近 SUMMARIZE_KEEP 条）
            int toCompress = allMessages.size() - SUMMARIZE_KEEP;
            List<AiMessage> oldMessages = allMessages.subList(0, toCompress);

            // 构建问答轮格式
            StringBuilder history = new StringBuilder();
            for (AiMessage msg : oldMessages) {
                String content = msg.getContent();
                if (content == null || content.isBlank()) continue;
                if ("USER".equals(msg.getRole())) {
                    history.append("用户：").append(content).append("\n");
                } else {
                    history.append("助手：").append(content).append("\n");
                }
            }

            if (history.isEmpty()) {
                return;
            }

            String summaryPrompt;
            String existingSummary = conv.getSummary();
            if (existingSummary != null && !existingSummary.isEmpty()) {
                summaryPrompt = "以下是之前的对话摘要：\n" + existingSummary
                        + "\n\n以下是更早的对话记录：\n" + history
                        + "\n请将对话记录压缩为问答轮摘要。每轮格式为："
                        + "\nN. 问：{用户问题核心} | 答：{助手回答核心}"
                        + "\n规则："
                        + "\n- 每轮对话压缩为一行，问和答各用一句话提炼核心"
                        + "\n- 保留用户的关键需求、问题主题、重要结论"
                        + "\n- 将新对话追加到已有摘要末尾（新旧不重复）"
                        + "\n- 直接输出，不要加任何前缀";
            } else {
                summaryPrompt = "以下是AI助手与用户的对话记录：\n" + history
                        + "\n请将对话记录压缩为问答轮摘要。每轮格式为："
                        + "\nN. 问：{用户问题核心} | 答：{助手回答核心}"
                        + "\n规则："
                        + "\n- 每轮对话压缩为一行，问和答各用一句话提炼核心"
                        + "\n- 保留用户的关键需求、问题主题、重要结论"
                        + "\n- 直接输出，不要加任何前缀";
            }

            ChatClient chatClient = chatClientBuilder.build();
            String newSummary = chatClient.prompt()
                    .system("你是一个对话摘要助手，将对话历史压缩为问答轮摘要。只输出摘要。")
                    .user(summaryPrompt)
                    .call()
                    .content();

            if (newSummary == null || newSummary.isBlank()) {
                return;
            }

            // 智能淘汰：超过上限时递归压缩（最多3次）
            int round = 0;
            while (estimateTokens(newSummary.strip()) > MAX_SUMMARY_TOKENS && round < MAX_COMPRESS_ROUNDS) {
                round++;
                log.info("摘要超长，第{}次智能压缩，conversationId={}", round, conversationId);

                String compressPrompt = "以下是对话摘要（已超过25000 token），请智能淘汰不重要的问答轮并精简："
                        + "\n\n淘汰规则（按优先级）："
                        + "\n1. 优先删除：闲聊/寒暄类问答（如'你好''谢谢''好的'）"
                        + "\n2. 其次删除：单次提及且不重要的话题（如闲聊天气、问时间）"
                        + "\n3. 再删除：超过20轮未被再次提及的话题"
                        + "\n4. 最后删除：信息密度低的问答"
                        + "\n\n保留规则（无论如何不删除）："
                        + "\n1. 最近10轮的问答（活跃对话）"
                        + "\n2. 用户明确表达需求/偏好的问答"
                        + "\n3. 涉及重要结论/决策的问答"
                        + "\n\n要求："
                        + "\n- 保持问答轮格式：N. 问：xxx | 答：xxx"
                        + "\n- 精简到25000 token以内"
                        + "\n- 直接输出，不要加前缀\n\n"
                        + newSummary;

                newSummary = chatClient.prompt()
                        .system("你是一个对话摘要压缩助手，负责智能淘汰不重要的问答轮，将摘要精简到25000 token以内，保留核心信息。只输出摘要。")
                        .user(compressPrompt)
                        .call()
                        .content();

                if (newSummary == null || newSummary.isBlank()) break;
            }

            // 如果3次后还超，强制截断
            if (newSummary != null && estimateTokens(newSummary.strip()) > MAX_SUMMARY_TOKENS) {
                newSummary = forceTruncateSummary(newSummary.strip());
            }

            if (newSummary != null && !newSummary.isBlank()) {
                conv.setSummary(newSummary.strip());
                conversationRepository.save(conv);
                log.info("对话摘要已更新，conversationId={}, 旧消息数={}, 压缩轮数={}", conversationId, toCompress, round);
            }
        } catch (Exception e) {
            // 摘要失败不影响正常对话
            log.error("生成对话摘要失败，conversationId={}: {}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 强制截断摘要（兜底方案）：按行截断，保留前N行
     */
    private String forceTruncateSummary(String summary) {
        String[] lines = summary.split("\n");
        StringBuilder sb = new StringBuilder();
        int usedTokens = 0;
        for (String line : lines) {
            int lineTokens = estimateTokens(line);
            if (usedTokens + lineTokens > MAX_SUMMARY_TOKENS * 9 / 10) { // 留10%余量
                break;
            }
            sb.append(line).append("\n");
            usedTokens += lineTokens;
        }
        return sb.toString();
    }

    // ==================== 问答索引（方案A：注入用户消息前） ====================

    /**
     * 构建全部问答索引：摘要中的问题 + 滑窗中的用户问题，连续编号。
     * 用于拼接到用户消息前面，让模型能完整列出所有问题。
     */
    public String buildQuestionIndex(AiConversation conv, List<AiMessage> windowMessages) {
        // 1. 从摘要中提取问题
        List<String> summaryQuestions = new ArrayList<>();
        if (conv.getSummary() != null && !conv.getSummary().isEmpty()) {
            for (String line : conv.getSummary().split("\n")) {
                String trimmed = line.strip();
                if (trimmed.matches("\\d+\\.\\s*问：.*")) {
                    int askIdx = trimmed.indexOf("问：");
                    if (askIdx >= 0) {
                        String q = trimmed.substring(askIdx + 2);
                        int pipeIdx = q.indexOf("|");
                        if (pipeIdx > 0) q = q.substring(0, pipeIdx).strip();
                        summaryQuestions.add(q);
                    }
                }
            }
        }

        // 2. 从滑窗中提取用户问题
        List<String> windowQuestions = new ArrayList<>();
        for (AiMessage msg : windowMessages) {
            if ("USER".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isBlank()) {
                windowQuestions.add(msg.getContent().strip());
            }
        }

        if (summaryQuestions.isEmpty() && windowQuestions.isEmpty()) {
            return "";
        }

        // 3. 拼接全部问答索引
        StringBuilder idx = new StringBuilder();
        int total = summaryQuestions.size() + windowQuestions.size();
        idx.append("【全部问答索引】（共").append(total)
           .append("轮对话，用户询问'全部问题'时请完整列出以下所有条目）\n");

        int num = 1;
        if (!summaryQuestions.isEmpty()) {
            idx.append("\n历史摘要中的问题：\n");
            for (String q : summaryQuestions) {
                idx.append(num++).append(". ").append(q).append("\n");
            }
        }
        if (!windowQuestions.isEmpty()) {
            idx.append("\n近期对话中的问题：\n");
            for (String q : windowQuestions) {
                idx.append(num++).append(". ").append(q).append("\n");
            }
        }

        return idx.toString();
    }
}
