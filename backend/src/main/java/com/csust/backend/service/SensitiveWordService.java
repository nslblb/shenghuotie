package com.csust.backend.service;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.entity.SensitiveWord;
import com.csust.backend.repository.SensitiveWordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordRepository sensitiveWordRepository;
    private volatile Set<String> sensitiveWords = new HashSet<>();
    private volatile Pattern sensitivePattern;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public synchronized void refreshCache() {
        List<SensitiveWord> words = sensitiveWordRepository.findByEnabledTrue();
        sensitiveWords = new HashSet<>();
        for (SensitiveWord word : words) {
            sensitiveWords.add(word.getWord().toLowerCase());
        }
        if (!sensitiveWords.isEmpty()) {
            String patternStr = "(" + String.join("|", sensitiveWords.stream()
                    .map(Pattern::quote)
                    .toArray(String[]::new)) + ")";
            sensitivePattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        } else {
            sensitivePattern = null;
        }
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty() || sensitivePattern == null) {
            return false;
        }
        return sensitivePattern.matcher(text).find();
    }

    public String highlightSensitiveWords(String text) {
        if (text == null || text.isEmpty() || sensitivePattern == null) {
            return text;
        }
        return sensitivePattern.matcher(text).replaceAll("<span class=\"sensitive-word\">$1</span>");
    }

    public Set<String> findSensitiveWords(String text) {
        Set<String> found = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        String lowerText = text.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerText.contains(word)) {
                found.add(word);
            }
        }
        return found;
    }

    public Page<SensitiveWord> getSensitiveWords(String keyword, String category, Boolean enabled, Pageable pageable) {
        return sensitiveWordRepository.findByConditions(keyword, category, enabled, pageable);
    }

    @Transactional
    public SensitiveWord createSensitiveWord(String word, String category) {
        if (sensitiveWordRepository.existsByWord(word)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该敏感词已存在");
        }
        SensitiveWord sensitiveWord = new SensitiveWord();
        sensitiveWord.setWord(word);
        sensitiveWord.setCategory(category);
        sensitiveWord.setEnabled(true);
        sensitiveWord = sensitiveWordRepository.save(sensitiveWord);
        refreshCache();
        return sensitiveWord;
    }

    @Transactional
    public SensitiveWord updateSensitiveWord(Long id, String word, String category, Boolean enabled) {
        SensitiveWord sensitiveWord = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "敏感词不存在"));
        if (word != null) {
            sensitiveWord.setWord(word);
        }
        if (category != null) {
            sensitiveWord.setCategory(category);
        }
        if (enabled != null) {
            sensitiveWord.setEnabled(enabled);
        }
        sensitiveWord = sensitiveWordRepository.save(sensitiveWord);
        refreshCache();
        return sensitiveWord;
    }

    @Transactional
    public void deleteSensitiveWord(Long id) {
        sensitiveWordRepository.deleteById(id);
        refreshCache();
    }

    @Transactional
    public void batchDeleteSensitiveWords(List<Long> ids) {
        sensitiveWordRepository.deleteAllById(ids);
        refreshCache();
    }
}
