package com.csust.backend.ai.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    public static final int MAX_CHUNK_LENGTH = 500;
    public static final int MIN_MERGE_LENGTH = 100;

    /**
     * 对知识库条目内容进行切片。
     * MANUAL_TEXT 类型不切片（整篇返回一个 chunk）；
     * POST_REF 类型按段落切片，每片 ≤ 500 字。
     *
     * @param title   条目标题
     * @param content 条目正文
     * @param sourceType 来源类型（MANUAL_TEXT / POST_REF）
     * @return chunk 列表，每个 chunk 的 text 已拼接标题前缀
     */
    public List<Chunk> chunk(String title, String content, String sourceType) {
        if ("MANUAL_TEXT".equals(sourceType)) {
            // 手写文本不切片，整篇一个 chunk
            return List.of(new Chunk(title + ": " + content, 0));
        }

        // POST_REF：按段落切片
        String[] paragraphs = content.split("\n\\s*\n");
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.strip();
            if (para.isEmpty()) continue;

            if (current.isEmpty()) {
                current.append(para);
            } else if (current.length() + para.length() < MAX_CHUNK_LENGTH) {
                // 合并短段
                current.append("\n").append(para);
            } else if (current.length() < MIN_MERGE_LENGTH) {
                // 当前片段还太短，继续合并
                current.append("\n").append(para);
            } else {
                merged.add(current.toString());
                current = new StringBuilder(para);
            }
        }
        if (!current.isEmpty()) {
            merged.add(current.toString());
        }

        // 如果某个段太长，按 \n 再细分
        List<String> finalChunks = new ArrayList<>();
        for (String chunk : merged) {
            if (chunk.length() > MAX_CHUNK_LENGTH) {
                String[] subLines = chunk.split("\n");
                StringBuilder sub = new StringBuilder();
                for (String line : subLines) {
                    line = line.strip();
                    if (line.isEmpty()) continue;
                    if (sub.isEmpty()) {
                        sub.append(line);
                    } else if (sub.length() + line.length() < MAX_CHUNK_LENGTH) {
                        sub.append("\n").append(line);
                    } else {
                        finalChunks.add(sub.toString());
                        sub = new StringBuilder(line);
                    }
                }
                if (!sub.isEmpty()) {
                    finalChunks.add(sub.toString());
                }
            } else {
                finalChunks.add(chunk);
            }
        }

        // 拼接标题前缀
        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < finalChunks.size(); i++) {
            String text = title + "#第" + (i + 1) + "段: " + finalChunks.get(i);
            result.add(new Chunk(text, i));
        }

        return result;
    }

    /**
     * 按段落切片（简版，不区分 sourceType，整篇按段落切）
     */
    public List<Chunk> chunkByParagraphs(String title, String content) {
        return chunk(title, content, "POST_REF");
    }

    public record Chunk(String text, int index) {}
}
