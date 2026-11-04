package com.csust.backend.controller;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.entity.FileRecord;
import com.csust.backend.repository.FileRecordRepository;
import com.csust.backend.repository.PostImageRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.FileResizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件管理", description = "图片上传")
public class FileController {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_USER_QUOTA = 100L * 1024 * 1024; // 100MB per user
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    private final AuthUtil authUtil;
    private final FileRecordRepository fileRecordRepository;
    private final PostImageRepository postImageRepository;
    private final PostRepository postRepository;
    private final FileResizeService fileResizeService;

    public FileController(AuthUtil authUtil,
                          FileRecordRepository fileRecordRepository,
                          PostImageRepository postImageRepository,
                          PostRepository postRepository,
                          FileResizeService fileResizeService) {
        this.authUtil = authUtil;
        this.fileRecordRepository = fileRecordRepository;
        this.postImageRepository = postImageRepository;
        this.postRepository = postRepository;
        this.fileResizeService = fileResizeService;
    }

    @Operation(summary = "上传图片")
    @PostMapping("/upload")
    @RateLimit(capacity = 20, refillCount = 20, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail(400, "文件大小不能超过5MB");
        }

        long used = getUsedSpace(userId);
        if (used + file.getSize() > MAX_USER_QUOTA) {
            return Result.fail(400, "存储空间已满，上限100MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return Result.fail(400, "只支持图片格式(jpg, jpeg, png, gif, webp, bmp)");
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID() + ext;
        Path filePath = uploadPath.resolve(fileName);

        // 校验为有效图片：流式读取图片头，避免 ImageIO.read 把整张图解码成 BufferedImage 占用 50MB+ 内存
        // 仅验证可解析出 ImageReader 即可，不真正解码像素
        if (!isValidImage(file.getInputStream(), ext.substring(1))) {
            return Result.fail(400, "文件不是有效图片");
        }

        // 落盘原始字节（缩放交给异步线程池）
        Files.copy(file.getInputStream(), filePath);

        String url = "/uploads/" + fileName;

        FileRecord record = new FileRecord();
        record.setUserId(userId);
        record.setFileName(fileName);
        record.setFileSize(file.getSize());
        fileRecordRepository.save(record);

        // 异步缩放：不阻塞上传响应，避免 20 并发解码大图 OOM
        fileResizeService.resizeAsync(fileName, ext.substring(1));

        return Result.success(url);
    }

    /**
     * 流式校验图片有效性：仅判断能否找到对应格式的 ImageReader，不真正解码像素。
     * 相比 ImageIO.read 节省 50MB+ BufferedImage 内存。
     */
    private boolean isValidImage(InputStream input, String formatName) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return false;
            }
            // 至少能读到图片头宽度，确保不是伪装成图片的恶意文件
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Operation(summary = "删除图片")
    @DeleteMapping("/delete")
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> delete(@RequestParam("url") String url) {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        if (url == null || url.isBlank()) {
            return Result.fail(400, "图片URL不能为空");
        }

        if (!url.startsWith("/uploads/")) {
            return Result.fail(400, "无效的图片URL");
        }

        String fileName = url.substring("/uploads/".length());
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Result.fail(400, "无效的文件名");
        }

        FileRecord record = fileRecordRepository.findByFileName(fileName)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (!record.getUserId().equals(userId)) {
            return Result.fail(403, "无权删除该文件");
        }

        if (postImageRepository.existsByImageUrl(url) || postRepository.existsByCoverImage(url)) {
            return Result.fail(400, "文件已被帖子引用，不可删除");
        }

        Path filePath = Paths.get(uploadDir).resolve(fileName);
        try {
            Files.deleteIfExists(filePath);
            fileRecordRepository.delete(record);
        } catch (IOException e) {
            return Result.fail(500, "删除文件失败");
        }

        return Result.success();
    }

    private long getUsedSpace(Long userId) {
        return fileRecordRepository.sumFileSizeByUserId(userId);
    }
}
