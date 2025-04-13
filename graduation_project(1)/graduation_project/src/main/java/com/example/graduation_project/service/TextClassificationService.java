package com.example.graduation_project.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.core.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextClassificationService {
    private Classifier classifier; // 存储加载的文本分类模型
    private StringToWordVector filter; // 存储加载的文本特征提取过滤器
    private List<String> categories = Arrays.asList("财经资讯", "娱乐资讯", "科技资讯", "时尚资讯", "健康资讯"); // 预定义的分类列表

    @PostConstruct
    public void loadModel() {
        try {
            // **加载训练好的文本分类模型**
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("src/main/resources/naive_bayes_model.model"))) {
                classifier = (Classifier) ois.readObject(); // 反序列化加载分类模型
            }
            // **加载文本处理的 TF-IDF 过滤器**
            try (ObjectInputStream fis = new ObjectInputStream(new FileInputStream("src/main/resources/string_to_word_vector_model.model"))) {
                filter = (StringToWordVector) fis.readObject(); // 反序列化加载 TF-IDF 过滤器
            }
            System.out.println("模型和过滤器加载成功！");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("模型加载失败", e);
        }
    }

     //对输入文本进行分类
    public String classifyText(String content) throws Exception {
        List<String> sentences = splitIntoSentences(content); // 将文本拆分为句子
        Map<String, Integer> categoryVotes = new HashMap<>(); // 记录每个类别的票数
        categories.forEach(category -> categoryVotes.put(category, 0)); // 初始化票数

        // **遍历文本中的每个句子并进行分类**
        for (String sentence : sentences) {
            String category = classifySentence(sentence); // 逐句分类
            categoryVotes.put(category, categoryVotes.getOrDefault(category, 0) + 1); // 统计每个类别的出现次数
        }
//        System.out.println(categoryVotes);
        return getMostFrequentCategory(categoryVotes); // 返回票数最多的类别
    }

     //使用正则表达式拆分文本为句子
    private List<String> splitIntoSentences(String content) {
        // 拆分文本，基于句号、问号、感叹号等
        String sentenceRegex = "(?<=。|！|！|\\?|\\!)\\s*";
        Pattern pattern = Pattern.compile(sentenceRegex);
        String[] sentences = pattern.split(content.trim());
        return Arrays.asList(sentences);
    }

    //对单个句子进行分类
    private String classifySentence(String sentence) throws Exception {
        // 定义 Weka 数据集的属性
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("text", (ArrayList<String>) null)); // 文字内容属性（文本特征）

        ArrayList<String> classValues = new ArrayList<>(categories); // 分类标签的取值范围
        // 将类别属性作为最后一列（目标属性）
        Attribute classAttribute = new Attribute("category", classValues); // 目标分类属性
        attributes.add(classAttribute); // 添加到属性列表

        // 创建 Weka 数据集格式
        Instances data = new Instances("TextClassification", attributes, 1);
        data.setClassIndex(attributes.size() - 1); // 设置最后一列为类别列

        // 创建并填充单个文本实例
        double[] values = new double[data.numAttributes()];
        values[0] = data.attribute(0).addStringValue(sentence); // 将句子文本添加到数据集
        Instance instance = new DenseInstance(1.0, values);
        data.add(instance); // 将实例添加到数据集

        // 设置实例所属的数据集（filter 需要此信息）
        instance.setDataset(data);

        // 使用训练时已配置好的 filter 进行转换
        Instances filteredData = Filter.useFilter(data, filter);  // 使用 filter 转换数据
        Instance filteredInstance = filteredData.instance(0);  // 获取转换后的实例

        // 用分类器进行预测
        double prediction = classifier.classifyInstance(filteredInstance);
        return data.classAttribute().value((int) prediction); // 返回分类结果
    }


     //统计类别投票数，选出最高的类别
    private String getMostFrequentCategory(Map<String, Integer> categoryVotes) {
        return categoryVotes.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue)) // 按照票数降序排列
                .map(Map.Entry::getKey) // 获取票数最多的类别
                .orElse("待分类");
    }
}
