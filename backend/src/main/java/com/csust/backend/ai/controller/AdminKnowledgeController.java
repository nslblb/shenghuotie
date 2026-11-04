package com.csust.backend.ai.controller;

import com.csust.backend.ai.dto.KnowledgeVO;
import com.csust.backend.ai.service.KnowledgeService;
import com.csust.backend.annotation.Log;
import com.csust.backend.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;

    // ==================== 知识库条目 CRUD ====================

    @Operation(summary = "分页获取知识库列表")
    @GetMapping("/knowledge")
    public Result<Page<KnowledgeVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(knowledgeService.list(pageable));
    }

    @Operation(summary = "获取知识库条目详情")
    @GetMapping("/knowledge/{id}")
    public Result<KnowledgeVO> getById(@PathVariable Long id) {
        return Result.success(knowledgeService.getById(id));
    }

    @Operation(summary = "创建知识库条目")
    @Log("创建知识库条目")
    @PostMapping("/knowledge")
    public Result<KnowledgeVO> create(
            @RequestParam String sourceType,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) Long postId) {
        return Result.success(knowledgeService.create(sourceType, title, content, postId));
    }

    @Operation(summary = "更新知识库条目")
    @Log("更新知识库条目")
    @PutMapping("/knowledge/{id}")
    public Result<KnowledgeVO> update(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Boolean enabled) {
        return Result.success(knowledgeService.update(id, title, content, enabled));
    }

    @Operation(summary = "删除知识库条目")
    @Log("删除知识库条目")
    @DeleteMapping("/knowledge/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.success();
    }

    @Operation(summary = "切换知识库条目启用/禁用状态")
    @Log("切换知识库条目状态")
    @PutMapping("/knowledge/{id}/toggle")
    public Result<KnowledgeVO> toggleEnabled(@PathVariable Long id) {
        return Result.success(knowledgeService.toggleEnabled(id));
    }

    // ==================== 从笔记导入 ====================

    @Operation(summary = "获取可导入的已发布笔记列表")
    @GetMapping("/knowledge/posts")
    public Result<List<Map<String, Object>>> listAvailablePosts() {
        return Result.success(knowledgeService.listAvailablePosts());
    }

    @Operation(summary = "从笔记导入知识库")
    @Log("从笔记导入知识库")
    @PostMapping("/knowledge/import-post")
    public Result<KnowledgeVO> importFromPost(@RequestParam Long postId) {
        return Result.success(knowledgeService.importFromPost(postId));
    }

    // ==================== 全量重建索引 ====================

    @Operation(summary = "全量重建 AI 知识库向量索引")
    @Log("全量重建 AI 知识库向量索引")
    @PostMapping("/ai/reindex")
    public Result<Map<String, Integer>> reindex() {
        return Result.success(knowledgeService.reindexAll());
    }
}
