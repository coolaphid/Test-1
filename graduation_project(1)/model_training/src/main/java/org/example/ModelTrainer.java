package org.example;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.evaluation.Evaluation;
import weka.core.*;
import weka.core.converters.ArffLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//使用朴素贝叶斯模型
public class ModelTrainer {
    public static void main(String[] args) throws Exception {
        // 加载数据
        ArffLoader loader = new ArffLoader();
        loader.setFile(new File("small_news_dataset.arff"));
        Instances data = loader.getDataSet();
        data.setClassIndex(data.numAttributes() - 1);

        // 文本分词
        for (int i = 0; i < data.numInstances(); i++) {
            String text = data.instance(i).stringValue(0);
            List<String> words = tokenizeChineseText(text);
            data.instance(i).setValue(0, String.join(" ", words));
        }

        // 构建并应用 TF-IDF 向量转换器
        StringToWordVector filter = new StringToWordVector();
        filter.setIDFTransform(true);
        filter.setTFTransform(true);
        filter.setInputFormat(data);
        Instances vectorizedData = Filter.useFilter(data, filter);

        // 训练分类器
        Classifier classifier = new NaiveBayes();
        classifier.buildClassifier(vectorizedData);

        // 模型评估
        Evaluation evaluation = new Evaluation(vectorizedData);
        evaluation.crossValidateModel(classifier, vectorizedData, 10, new Random(1));
        System.out.println("模型评估：\n" + evaluation.toSummaryString());

        // 保存模型与过滤器
        SerializationHelper.write("naive_bayes_model.model", classifier);
        SerializationHelper.write("string_to_word_vector_model.model", filter);
    }

    private static List<String> tokenizeChineseText(String text) {
        List<Term> termList = HanLP.segment(text);
        List<String> wordList = new ArrayList<>();
        for (Term term : termList) {
            wordList.add(term.word);
        }
        return wordList;
    }
}

//import com.hankcs.hanlp.HanLP;
//import com.hankcs.hanlp.seg.common.Term;
//import weka.classifiers.Classifier;
//import weka.classifiers.Evaluation;
//import weka.classifiers.bayes.NaiveBayes;
//import weka.core.Instances;
//import weka.core.Instance;
//import weka.core.DenseInstance;
//import weka.core.converters.ArffLoader;
//import weka.filters.Filter;
//import weka.filters.unsupervised.attribute.StringToWordVector;
//import weka.core.SerializationHelper;
//
//import java.io.*;
//import java.util.*;
//
//public class ModelTrainer {
//
//    public static void main(String[] args) throws Exception {
//        // 加载训练数据
//        ArffLoader loader = new ArffLoader();
//        loader.setFile(new File("small_news_dataset.arff"));
//        Instances data = loader.getDataSet();
//        data.setClassIndex(data.numAttributes() - 1);  // 设置类别列为最后一列
//
//        // 预处理数据
//        Instances processedData = preprocessData(data);
//
//        // 训练 NaiveBayes 模型
//        Classifier classifier = new NaiveBayes();
//        classifier.buildClassifier(processedData);
//        // Step 4: 模型评估
//        evaluateModel(classifier, processedData);
//
//        // 保存模型
//        saveModel(classifier, "naive_bayes_model.model");
//        saveStringToWordVectorModel("string_to_word_vector_model.model");
//
//        // 进行预测
//        String newSentence = "维生素C诱导体及抗氧化剂等可以防止由于紫外线照射等引起的氧化。";
//        String defaultCategory = "财经资讯";  // 默认类别
//        String predictedCategory = predictCategory(newSentence, defaultCategory, classifier, processedData);
//
//        // 输出预测分类
//        System.out.println("预测分类: " + predictedCategory);
//    }
//
//    // 数据预处理：中文分词、去停用词、使用 TF-IDF
//    private static Instances preprocessData(Instances data) throws Exception {
//        // 使用 HanLP 进行中文分词
//        for (int i = 0; i < data.numInstances(); i++) {
//            String sentence = data.instance(i).stringValue(data.instance(i).attribute(0)); // 训练文本在第一列
//            List<String> words = tokenizeChineseText(sentence);
//            String processedText = String.join(" ", words);
//            data.instance(i).setValue(0, processedText); // 设置处理后的文本
//        }
//        System.out.println(data);
//        // 使用 StringToWordVector 转换为 TF-IDF 特征向量
//        StringToWordVector filter = new StringToWordVector();
//        filter.setInputFormat(data);
//        filter.setIDFTransform(true);  // 开启 IDF 变换
//        filter.setTFTransform(true);   // 开启 TF 变换
//        data = Filter.useFilter(data, filter);
//
//        return data;
//    }
//
//    // 使用 HanLP 进行中文分词
//    private static List<String> tokenizeChineseText(String text) {
//        // 使用 HanLP 进行分词
//        List<Term> termList = HanLP.segment(text);
//        List<String> wordList = new ArrayList<>();
//        for (Term term : termList) {
//            wordList.add(term.word);  // 提取每个分词的词语部分
//        }
//        return wordList;
//    }
//
//    // 模型评估
//    private static void evaluateModel(Classifier classifier, Instances data) throws Exception {
//        Evaluation evaluation = new Evaluation(data);
//        evaluation.crossValidateModel(classifier, data, 10, new Random(1));
//        System.out.println("评估结果：\n" + evaluation.toSummaryString());
//    }
//
//    // 保存训练好的 NaiveBayes 模型
//    private static void saveModel(Classifier classifier, String modelFile) throws Exception {
//        SerializationHelper.write(modelFile, classifier);
//        System.out.println("模型已保存至: " + modelFile);
//    }
//
//    // 保存 StringToWordVector 过滤器模型
//    private static void saveStringToWordVectorModel(String modelFile) throws Exception {
//        StringToWordVector filter = new StringToWordVector();
//        SerializationHelper.write(modelFile, filter);
//        System.out.println("StringToWordVector 过滤器模型已保存至: " + modelFile);
//    }
//
//    // 创建实例（将句子转换为特征向量）
//    private static Instance createInstance(String sentence, Instances processedData) throws Exception {
//        // 使用 StringToWordVector 过滤器将句子转换为特征向量
//        StringToWordVector filter = new StringToWordVector();
//        filter.setInputFormat(processedData);
//
//        // 创建原始实例
//        Instance newInstance = createRawInstance(sentence, processedData);
//        Instances tempData = new Instances(processedData, 0);
//        tempData.add(newInstance);
//
//        // 使用过滤器将文本转换为特征向量
//        Instances filteredInstance = Filter.useFilter(tempData, filter);
//
//        // 返回转换后的实例
//        return filteredInstance.firstInstance();
//    }
//
//    // 创建原始实例（将文本转换为单词）
//    private static Instance createRawInstance(String sentence, Instances processedData) {
//        double[] values = new double[processedData.numAttributes()];
//        Instance instance = new DenseInstance(1.0, values);
//        instance.setDataset(processedData);
//        return instance;
//    }
//
//    // 使用模型进行预测，忽略默认输入类别
//    private static String predictCategory(String sentence, String defaultCategory, Classifier classifier, Instances processedData) throws Exception {
//        // 创建新的实例
//        Instance newInstance = createInstance(sentence, processedData);
//
//        // 使用模型进行预测
//        double predictedClass = classifier.classifyInstance(newInstance);
//
//        // 输出预测的类别
//        return processedData.classAttribute().value((int) predictedClass);
//    }
//}

//import weka.classifiers.Evaluation;
//import weka.core.*;
//import weka.core.converters.CSVLoader;
//import weka.classifiers.Classifier;
//import weka.classifiers.bayes.NaiveBayes;
//import weka.classifiers.functions.SMO;  // SVM 分类器
//import weka.classifiers.functions.SMO;
//import weka.classifiers.trees.J48;
//import weka.filters.unsupervised.attribute.StringToWordVector;
//import weka.filters.Filter;
//
//import java.io.File;
//
//public class ModelTrainer {
//    public static void main(String[] args) throws Exception {
//        // 1. 加载 CSV 数据
//        CSVLoader loader = new CSVLoader();
//        loader.setSource(new File("src/main/resources/small_news_dataset.csv")); // 替换为你的 CSV 路径
//        Instances data = loader.getDataSet();
//
//        // 2. 设置类别列 (最后一列为分类标签)
//        data.setClassIndex(data.numAttributes() - 1);
//
//        // 3. 文本向量化 (TF-IDF)
//        StringToWordVector filter = new StringToWordVector();
//        filter.setTFTransform(true);
//        filter.setIDFTransform(true);
//        filter.setInputFormat(data);
//        Instances filteredData = Filter.useFilter(data, filter);
//
//        // 4. 训练 SVM 模型 (SMO)
//        Classifier classifier = new SMO();  // 使用 SVM
//        classifier.buildClassifier(filteredData);
//
//        // 5. 评估模型
//        Evaluation eval = new Evaluation(filteredData);
//        eval.crossValidateModel(classifier, filteredData, 10, new java.util.Random(1));
//
//
//        // 6. 保存训练好的模型
//        SerializationHelper.write("textClassifier_9.model", classifier);
//        SerializationHelper.write("tfidf_filter.model", filter); // 保存 TF-IDF 过滤器
//
//        System.out.println("模型已保存为 textClassifier_9.model 和 tfidf_filter.model");
//
//        // 输出分类结果
//        System.out.println("=== 模型评估 ===");
//        System.out.println(eval.toSummaryString());
//        System.out.println("=== 详细分类精度 ===");
//        System.out.println(eval.toClassDetailsString());
//        System.out.println("=== 混淆矩阵 ===");
//        System.out.println(eval.toMatrixString());
//    }
//}