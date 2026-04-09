package com.springboot.documentsearcher.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.documentsearcher.model.DocInfo;
import com.springboot.documentsearcher.model.Wight;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
//索引模块--描述索引
public class Index {
    private Object lock = new Object();
    private Object locker = new Object();
    private static final String INDEX_PATH = "E:\\Java\\docs\\";
    private ObjectMapper objectMapper = new ObjectMapper();
    //用数组下标表示docId
    private ArrayList<DocInfo> forwardIndex = new ArrayList<>();
    //用哈希表来表示倒排索引,key为词,value为和这个词相关的文章
    private HashMap<String,List<Wight>> invertedIndex = new HashMap<>();

    //正排索引,根据id查找文档的内容
    public DocInfo getDocInfo(int docId) {
        return forwardIndex.get(docId);
    }

    //倒排索引,根据关键词查找文档列表,返回id列表
    public List<Wight> getInverted(String term){
        return invertedIndex.get(term);
    }

    //往索引中添加文档
    public void addDoc(String title, String URL, String content) {
        //构建正排索引
        DocInfo docInfo = buildForward(title, URL, content);
        //构造倒排索引
        buildInverted(docInfo);
    }

    private void buildInverted(DocInfo docInfo) {
        class WordCount{
            public int titleCount;
            public int contentCount;
        }
        Map<String, WordCount> wordCountMap = new HashMap<>();
        //针对文档标题进行分词
        List<Term> terms = ToAnalysis.parse(docInfo.getTitle()).getTerms();
        //遍历分词结果,统计词出现的次数
        for(Term term : terms){
            String word = term.getName();

            WordCount wordCount = wordCountMap.get(word);
            if(wordCount == null){
                wordCount = new WordCount();
                wordCount.titleCount = 1;
                wordCount.contentCount = 0;
                wordCountMap.put(word, wordCount);
            }else{
                wordCount.titleCount++;
            }
        }

        //针对文档正文进行分词
        terms = ToAnalysis.parse(docInfo.getContent()).getTerms();
        //遍历分词结果,统计词出现的次数
        for(Term term : terms){
            String word = term.getName();
            WordCount wordCount = wordCountMap.get(word);
            if(wordCount == null){
                wordCount = new WordCount();
                wordCount.titleCount = 0;
                wordCount.contentCount = 1;
                wordCountMap.put(word, wordCount);
            }else{
                wordCount.contentCount++;
            }
        }

        //对结果进行汇总
        for(Map.Entry<String, WordCount> entry : wordCountMap.entrySet()){
            String word = entry.getKey();
            WordCount wordCount = entry.getValue();
            synchronized (locker){
                if (invertedIndex.containsKey(word)) {
                    //单词已经存储过
                    //构造文档的权重
                    Wight wight = new Wight();
                    wight.setDocId(docInfo.getDocId());
                    wight.setWight(wordCount.titleCount * 10 + wordCount.contentCount);
                    //往单词对应的列表中添加wight
                    invertedIndex.get(word).add(wight);
                } else {
                    //单词没有存储过
                    //新建这个单词对应的列表
                    List<Wight> wights = new ArrayList<>();
                    //构造文档的权重
                    Wight wight = new Wight();
                    wight.setDocId(docInfo.getDocId());
                    wight.setWight(wordCount.titleCount * 10 + wordCount.contentCount);
                    wights.add(wight);
                    //插入单词和对应的列表
                    invertedIndex.put(word, wights);
                }
            }
        }
    }

    private DocInfo buildForward(String title, String URL, String content) {
        DocInfo docInfo = new DocInfo(title,URL,content);
        synchronized (lock){
            forwardIndex.add(docInfo);
            //文档放到了末尾,文档id为列表的长度-1
            docInfo.setDocId(forwardIndex.size() - 1);
        }
        return docInfo;
    }

    //把索引内存结构保存到硬盘
    public void save() {
        //使用JSON格式进行序列化
        long start = System.currentTimeMillis();
        System.out.println("保存索引开始");
        File indexPathFile = new File(INDEX_PATH);
        if(!indexPathFile.exists()){
            indexPathFile.mkdirs();
        }
        //指定索引保存的位置
        File forwardIndexFile = new File(INDEX_PATH+"forward.txt");
        File invertedIndexFile = new File(INDEX_PATH+"inverted.txt");
        //序列号索引
        try{
            objectMapper.writeValue(forwardIndexFile, forwardIndex);
            objectMapper.writeValue(invertedIndexFile, invertedIndex);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        System.out.println("保存索引完成! 消耗时间:" + (end-start) + "ms");
    }

    //把硬盘的索引结构加载到内存
    public void load(){
        //使用JSON格式进行反序列化
        long start = System.currentTimeMillis();
        Logger logger = LoggerFactory.getLogger(Index.class);
        logger.info("加载索引");
        File forwardIndexFile = new File(INDEX_PATH+"forward.txt");
        File invertedIndexFile = new File(INDEX_PATH+"inverted.txt");
        try{
            forwardIndex = objectMapper.readValue(forwardIndexFile,new TypeReference<ArrayList<DocInfo>>(){});
            invertedIndex = objectMapper.readValue(invertedIndexFile,new TypeReference<HashMap<String,List<Wight>>>(){});
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        logger.info("索引加载完成! 消耗时间:" + (end-start) + "ms");
    }

}
