package com.example.graduation_project.service;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.example.graduation_project.model.Article;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
@Service
@RequiredArgsConstructor
public class FileService {
    private static final int MAX_KEYWORDS = 5;  // 最多提取5个关键词
//    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>();
    @Autowired
    private TextClassificationService textClassificationService;

    public Article processFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new Exception("文件为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new Exception("文件名无效");
        }

        String content;
        if (fileName.endsWith(".txt")) {
            content = readTxtFile(file);
        } else if (fileName.endsWith(".docx")) {
            content = readWordFile(file);
        } else {
            throw new Exception("不支持的文件格式");
        }

        // 提取标题和正文
        String[] lines = content.split("\n", 2);
        String title = lines[0].trim();
        String body = lines.length > 1 ? lines[1].trim() : "";

        // 使用 HanLP 提取关键词
        List<String> keywords = extractKeywords(body);

        // 使用模型进行分类
        String category = textClassificationService.classifyText(body);

        // 调用 TextRank 生成摘要（选择5个句子）
        String summary = generateSummary(body);

        // 返回文章对象
        Article article = new Article();
        article.setTitle(title);
        article.setSummary(body.substring(0, Math.min(body.length(), 200)) + "...");
        //第二种摘要生成方法
//        article.setSummary(summary);
        article.setKeywords(String.join(", ", keywords)); // 存储关键词
        article.setCategory(category);
        return article;
    }

    private String readTxtFile(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private String readWordFile(MultipartFile file) throws Exception {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            return document.getParagraphs().stream()
                    .map(p -> p.getText().trim())
                    .collect(Collectors.joining("\n"));
        }
    }

     //使用 HanLP 提取关键词
    private List<String> extractKeywords(String text) {
        // 使用 HanLP 进行分词
        List<Term> termList = HanLP.segment(text);
        // 过滤掉停用词、标点符号和无意义的词（如助词）
        List<String> filteredWords = termList.stream()
                .map(term -> term.word)  // 获取词语本身
                .map(String::trim) // 去掉前后空格
                .filter(word -> word.length() > 1)  // 过滤掉单字符词
                .filter(word -> !isPunctuation(word))  // 过滤掉标点符号
                .collect(Collectors.toList());
        // 统计词频
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : filteredWords) {
            wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
        }
        // 按照词频排序并返回前 MAX_KEYWORDS 个关键词
        return wordFreq.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // 按词频排序
                .limit(MAX_KEYWORDS) // 只取前 MAX_KEYWORDS 个
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

     //判断词语是否是标点符号
    private boolean isPunctuation(String word) {
        // 通过正则表达式判断是否为标点符号
        return word.matches("[\\p{P}\\p{S}]");
    }

    // 使用基于句子的 TF-IDF 方法生成摘要
    private String generateSummary(String text) {
        // 分割文章为句子
        List<String> sentences = HanLP.extractSummary(text, 5);  // 提取前5个最重要的句子
        return String.join(" ", sentences);
    }
    
}
