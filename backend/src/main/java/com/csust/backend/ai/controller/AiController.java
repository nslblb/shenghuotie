package com.csust.backend.ai.controller;

import com.csust.backend.ai.dto.AiMessageVO;
import com.csust.backend.ai.dto.ChatRequest;
import com.csust.backend.ai.dto.ConversationVO;
import com.csust.backend.ai.dto.MessageSource;
import com.csust.backend.ai.entity.AiConversation;
import com.csust.backend.ai.entity.AiMessage;
import com.csust.backend.ai.service.ConversationService;
import com.csust.backend.ai.service.RagService;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.Result;
import com.csust.backend.common.BusinessException;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.SensitiveWordService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final RagService ragService;
    private final ConversationService conversationService;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectMapper objectMapper;
    private final AuthUtil authUtil;

    @Operation(summary = "流式问答（SSE）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        Long userId = authUtil.getCurrentUserId();

        // 敏感词检查
        if (sensitiveWordService.containsSensitiveWord(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "提问包含敏感词，请修改后重试");
        }

        return ragService.chat(userId, request.getMessage());
    }

    @Operation(summary = "获取当前用户的对话信息")
    @GetMapping("/conversation")
    public Result<ConversationVO> getConversation() {
        Long userId = authUtil.getCurrentUserId();
        AiConversation conv = conversationService.getOrCreateConversation(userId);
        ConversationVO vo = new ConversationVO();
        vo.setId(conv.getId());
        vo.setTitle(conv.getTitle());
        vo.setCreatedAt(conv.getCreatedAt());
        vo.setUpdatedAt(conv.getUpdatedAt());
        return Result.success(vo);
    }

    @Operation(summary = "获取对话消息列表（分页，按时间倒序）")
    @GetMapping("/messages")
    public Result<Page<AiMessageVO>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = authUtil.getCurrentUserId();
        AiConversation conv = conversationService.getByUserId(userId);
        if (conv == null) {
            return Result.success(Page.empty());
        }
        Page<AiMessage> messagePage = conversationService.getMessages(conv.getId(), page, size);
        Page<AiMessageVO> voPage = messagePage.map(this::toMessageVO);
        return Result.success(voPage);
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/messages/{id}")
    public Result<Void> deleteMessage(@PathVariable Long id) {
        Long userId = authUtil.getCurrentUserId();
        conversationService.deleteMessage(id, userId);
        return Result.success();
    }

    // ==================== 内部方法 ====================

    private AiMessageVO toMessageVO(AiMessage entity) {
        AiMessageVO vo = new AiMessageVO();
        vo.setId(entity.getId());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setFromKnowledge(entity.getFromKnowledge());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());

        // 解析 sources JSON
        if (entity.getSources() != null && !entity.getSources().isEmpty()) {
            try {
                List<MessageSource> sources = objectMapper.readValue(
                        entity.getSources(), new TypeReference<List<MessageSource>>() {});
                vo.setSources(sources);
            } catch (Exception e) {
                vo.setSources(List.of());
            }
        } else {
            vo.setSources(List.of());
        }

        return vo;
    }
}
