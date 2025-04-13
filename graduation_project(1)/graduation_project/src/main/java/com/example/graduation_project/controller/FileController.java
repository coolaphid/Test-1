package com.example.graduation_project.controller;

import com.example.graduation_project.model.Article;
import com.example.graduation_project.repository.ArticleRepository;
import com.example.graduation_project.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@CrossOrigin(origins = "http://localhost:5173")  // 允许特定前端访问
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;
    private final ArticleRepository articleRepository;

    @Autowired
    public FileController(FileService fileService, ArticleRepository articleRepository) {
        this.fileService = fileService;
        this.articleRepository = articleRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            Article article = fileService.processFile(file);
            articleRepository.save(article);
            return ResponseEntity.ok(article);
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常堆栈信息
            return ResponseEntity.status(500).body("文件处理失败：" + e.getMessage());
        }
    }
}
