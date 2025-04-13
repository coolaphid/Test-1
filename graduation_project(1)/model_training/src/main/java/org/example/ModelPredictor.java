package org.example;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import weka.classifiers.Classifier;
import weka.core.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.util.ArrayList;
import java.util.List;

public class ModelPredictor {

    public static void main(String[] args) throws Exception {
        // 加载模型和向量化过滤器
        Classifier classifier = (Classifier) SerializationHelper.read("naive_bayes_model.model");
        StringToWordVector filter = (StringToWordVector) SerializationHelper.read("string_to_word_vector_model.model");

        // 构建测试实例
//        String newSentence = "维生素C诱导体及抗氧化剂等可以防止由于紫外线照射等引起的氧化。";
//        String newSentence = "安卓开源项目（Android Open Source Project），简称AOSP，是谷歌在Apache 2.0许可下发布的一个操作系统。Apache 2.0允许任何人使用、分发或修改和分发基于AOSP的操作系统，而无需支付任何许可费用或发布源代码。";
        String newSentence = "第三，饮食调理，避免刺激性食物，咖啡、浓茶、酒要尽量避免。可以吃滋阴的食物，银耳、百合、枸杞、黑芝麻等。此外健脾食物也有帮助，如山药、莲子等。";
//        String newSentence = "能快速祛斑的美白成分开发十分盛行，但是最近的开发方向则是开发可以维持已知的安全的美白成分的成 分。为了快速排出黑色素，再生能力的提高与促进血液循环是非常有效的。";
        String predicted = predict(newSentence, classifier, filter);
        System.out.println("预测结果：" + predicted);
    }

    public static String predict(String sentence, Classifier classifier, StringToWordVector filter) throws Exception {
        // 分词
        List<String> tokens = tokenizeChineseText(sentence);
        String processedText = String.join(" ", tokens);

        // 构造临时数据集
        FastVector attributes = new FastVector(2);
        attributes.addElement(new Attribute("text", (FastVector) null));
        FastVector classVals = new FastVector(5);
        classVals.addElement("财经资讯");
        classVals.addElement("娱乐资讯");
        classVals.addElement("科技资讯");
        classVals.addElement("时尚资讯");
        classVals.addElement("健康资讯");
        attributes.addElement(new Attribute("class", classVals));
        Instances testData = new Instances("TestRelation", attributes, 1);
        testData.setClassIndex(1);

        DenseInstance instance = new DenseInstance(2);
        instance.setValue((Attribute) attributes.elementAt(0), processedText);
        instance.setDataset(testData);
        testData.add(instance);

        // 向量化
        Instances vectorized = Filter.useFilter(testData, filter);

        // 分类预测
        double result = classifier.classifyInstance(vectorized.firstInstance());
        return testData.classAttribute().value((int) result);
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
