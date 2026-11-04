package com.csust.backend.service;

import com.csust.backend.entity.FileRecord;
import com.csust.backend.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 图片缩放服务：独立线程池执行，避免阻塞上传请求线程、避免大图解码占用主路径内存。
 *
 * 上传流程：
 * 1. Controller 同步校验图片有效性（轻量 ImageIO.read 仅读图头）+ 落盘原始字节
 * 2. 调用 {@link #resizeAsync} 异步缩放，覆写原图文件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileResizeService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final Set<String> SUPPORTED_RESIZE_FORMATS = Set.of("jpg", "jpeg", "png", "bmp", "gif");
    private static final int THUMBNAIL_MAX_WIDTH = 800;

    private final FileRecordRepository fileRecordRepository;

    /**
     * 异步缩放：从磁盘读取已落盘的原图，缩放后覆写。
     * 若图片不需要缩放或缩放失败，保留原图，不阻塞上传响应。
     */
    @Async("fileResizeExecutor")
    public void resizeAsync(String fileName, String extNoDot) {
        try {
            if (!SUPPORTED_RESIZE_FORMATS.contains(extNoDot)) {
                return;
            }
            Path filePath = Paths.get(uploadDir).resolve(fileName);
            if (!Files.exists(filePath)) {
                log.warn("异步缩放：文件不存在 {}", fileName);
                return;
            }

            BufferedImage original = ImageIO.read(filePath.toFile());
            if (original == null) {
                log.warn("异步缩放：文件不是有效图片 {}", fileName);
                return;
            }
            if (original.getWidth() <= THUMBNAIL_MAX_WIDTH) {
                return; // 小图无需缩放
            }

            int width = THUMBNAIL_MAX_WIDTH;
            int height = (int) (original.getHeight() * ((double) width / original.getWidth()));
            int imageType = original.getType() == 0 ? BufferedImage.TYPE_INT_RGB : original.getType();
            BufferedImage resized = new BufferedImage(width, height, imageType);
            Graphics2D g2d = resized.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(original, 0, 0, width, height, null);
            } finally {
                g2d.dispose();
                original.flush();
            }
            ImageIO.write(resized, extNoDot, filePath.toFile());
            resized.flush();

            // 缩放后更新文件大小记录
            long newSize = Files.size(filePath);
            fileRecordRepository.findByFileName(fileName).ifPresent(record -> {
                record.setFileSize(newSize);
                fileRecordRepository.save(record);
            });
            log.info("异步缩放完成: {} -> {}x{}", fileName, width, height);
        } catch (Exception e) {
            log.error("异步缩放失败: {}, 保留原图", fileName, e);
        }
    }
}