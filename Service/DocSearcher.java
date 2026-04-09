package com.springboot.documentsearcher.Service;
import com.springboot.documentsearcher.model.DocInfo;
import com.springboot.documentsearcher.model.Result;
import com.springboot.documentsearcher.model.Wight;
import jakarta.annotation.PostConstruct;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service
//搜索模块
public class DocSearcher {
    private static final String STOP_WORD_PATH="E:\\Java\\DocumentSearcher\\stop_word.txt";
    @Autowired
    private Index index;
    private Set<String> stopWords = new HashSet<>();

    public DocSearcher() {
    }

    //在注入完成后自动调用
    @PostConstruct
    public void init(){
        //创建实例时加载索引
        index.load();
        loadStopWords();
    }

    public List<Result> searcher(String query) {
        List<Result> ret = new ArrayList<Result>();
        //1.对查询语句进行分词
        List<Term> terms = ToAnalysis.parse(query).getTerms();
        //2.针对分词结果进行倒排查询
        List<List<Wight>> termResult = new ArrayList<>();
        for (Term term : terms) {
            String word = term.getName();
            if(stopWords.contains(word)) {
                //这个词在暂停词表中,跳过
                continue;
            }
            //返回这个词相关的文档列表
            List<Wight> invertedList = index.getInverted(word);
            if (invertedList == null) {
                //这个词没有出现过,返回列表为空,直接跳过
                continue;
            }
            //对多个不同的词进行搜索时,可能会搜索到同一个文件
            //使用二维列表存储多个分词结果的列表
            termResult.add(invertedList);
        }
        //对多个分词结果触发的相同文档进行合并
        List<Wight> allTermResult = mergeResult(termResult);
        //3.针对查询结果按权重进行倒叙排序
        allTermResult.sort(new Comparator<Wight>() {
            //进行倒叙排序
            @Override
            public int compare(Wight o1, Wight o2) {
                return o2.getWight() - o1.getWight();
            }
        });
        //4.对结果进行包装
        for (Wight wight : allTermResult) {
            DocInfo docInfo = index.getDocInfo(wight.getDocId());
            //从正文中解析摘要
            String desc = genDesc(docInfo.getContent(), terms);
            Result result = new Result(docInfo.getTitle(), docInfo.getURL(), desc);
            ret.add(result);
        }
        return ret;
    }

    static class Pos{
        public int row;
        public int col;

        public Pos(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private List<Wight> mergeResult(List<List<Wight>> termResult) {
        //1.对行进行按id排序
        for (List<Wight> curRow : termResult) {
            curRow.sort(new Comparator<Wight>() {
                @Override
                public int compare(Wight o1, Wight o2) {
                    return o1.getDocId() - o2.getDocId();
                }
            });
        }
        //2.使用优先级队列进行合并
        List<Wight> target = new ArrayList<>();
        //按照wight的id取小的更优先
        PriorityQueue<Pos> queue = new PriorityQueue<>(new Comparator<Pos>() {
            @Override
            public int compare(Pos o1, Pos o2) {
                //现根据pos值找到对应的wight对象,再根据wight的id来排序
                Wight w1 = termResult.get(o1.row).get(o1.col);
                Wight w2 = termResult.get(o2.row).get(o2.col);
                return w1.getDocId() - w2.getDocId();
            }
        });
        //初始化队列,把每一行的第一个元素放入队列中
        for (int row = 0; row < termResult.size(); row++) {
            queue.add(new Pos(row,0));
        }
        while (!queue.isEmpty()) {
            Pos cur = queue.poll();
            Wight curWight = termResult.get(cur.row).get(cur.col);
            if(!target.isEmpty()){
                Wight lastWight = target.get(target.size() - 1);
                if (lastWight.getDocId() == curWight.getDocId()) {
                    //遇到了相同的文档 合并权重
                    lastWight.setWight(lastWight.getWight() + curWight.getWight());
                }else{
                    target.add(curWight);
                }
            }else{
                target.add(curWight);
            }
            Pos pos = new Pos(cur.row,cur.col+1);
            if (pos.col >= termResult.get(pos.row).size()){
                //这一行已经处理完毕
                continue;
            }
            queue.offer(pos);
        }
        return target;
    }

    //简单的实现分析摘要
    private String genDesc(String content, List<Term> terms){
        int post = -1;
        for (Term term : terms) {
            String word = term.getName();
            //使用正则表达式匹配单个单词
            content = content.toLowerCase().replaceAll("\\b"+"word"+"\\b"," " + word + " ");
            post = content.indexOf(" " + word + " ");
            if (post >= 0) {
                //找到了这个词
                break;
            }
        }
        if (post == -1) {
            return content.substring(0,Math.min(content.length(), 100));
        }
        //根据分词位置生成描述
        int begin = post > 50 ? post-50 : 0;
        int end = Math.min(post + 50, content.length());
        String desc = content.substring(begin, end) + "...";
        for (Term term : terms) {
            String word = term.getName();
            desc = desc.replaceAll("(?i) " + word + " ", "<i> " + word + " </i>");
        }
        return desc;
    }

    public void loadStopWords(){
        try(BufferedReader reader = new BufferedReader(new FileReader(STOP_WORD_PATH))){
            while(true){
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                stopWords.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
