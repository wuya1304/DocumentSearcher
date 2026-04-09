package com.springboot.documentsearcher.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
//索引模块--解析索引
public class Parser {
    //指定加载文档的路径
    private static final String INPUT_PATH = "E:/Java/docs/api/";
    @Autowired
    private Index index;

    private void run() {
        //parser的入口
        long start = System.currentTimeMillis();
        //1.根据指定的路径,枚举出路径中所有的文件(html)
        //保存枚举的文件结果
        ArrayList<File> fileList = new ArrayList<>();
        enumFile(INPUT_PATH, fileList);
        //2.针对罗列出的文件路径,打开文件,读取文件内容,进行解析并构建索引
        for (File file : fileList) {
            //解析文件
            System.out.println("开始解析:" + file.getAbsolutePath());
            parseHTML(file);
        }
        //3.把内存中构造好的索引解构保存到文件中
        index.save();
        long end = System.currentTimeMillis();
        System.out.println("解析并保存索引消耗时间:" + (end - start) + "ms");
    }

    //多线程制作索引
    private void runByThread() {
        //parser的入口
        long start = System.currentTimeMillis();
        //1.根据指定的路径,枚举出路径中所有的文件(html)
        //保存枚举的文件结果
        ArrayList<File> fileList = new ArrayList<>();
        enumFile(INPUT_PATH, fileList);
        ExecutorService service = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(fileList.size());
        //2.针对罗列出的文件路径,打开文件,读取文件内容,进行解析并构建索引
        for (File file : fileList) {
            //解析文件
            service.submit(() -> {
                    System.out.println("开始解析:" + file.getAbsolutePath());
                    parseHTML(file);
                    latch.countDown();
            });
        }
        //3.把内存中构造好的索引解构保存到文件中
        try {
            //等待线程池任务执行完毕
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //关闭线程池
            service.shutdown();
        }
        index.save();
        long end = System.currentTimeMillis();
        System.out.println("解析并保存索引消耗时间:" + (end - start) + "ms");
    }

    //解析HTML文件
    private void parseHTML(File file) {
        //解析标题
        String title = parseTitle(file);
        //解析URL
        String URL = parseURL(file);
        //解析描述,描述是正文的一段摘要
        String content = parseContentByRegex(file);
        //把解析出的信息,添加到索引中.
        index.addDoc(title, URL, content);
    }

    //解析描述 基于正则表达式去除标签和script
    public String parseContentByRegex(File file) {
        String content = readFile(file);
        //将script标签及其内容和html标签去除
        content = content.replaceAll("<script.*?>(.*?)</script>"," ");
        content = content.replaceAll("<.*?>"," ");
        //去除重复的空格
        content = content.replaceAll("\\s+"," ");
        return content;
    }

    private String readFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file), 1024*1024)) {
            while(true){
                int n = reader.read();
                if (n == -1) {
                    break;
                }
                char c = (char)n;
                if(c == '\n' || c == '\r'){
                    c = ' ';
                }
                sb.append(c);
            }
        }  catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    //解析描述  读取文件是耗时较高的操作
    public String parseContent(File file) {
        //去除html中的html标签
        //遇到除 < 外的字符,则将字符放到结果中.
        //遇到 < 后,不把字符放到结果中,直到遇到 > 为止.
        //"a</div>x" -> "ax"
        //判断是否遇到了<,遇到<设置为false,遇到>设置为true.
        boolean flag = true;
        //保存结果
        StringBuilder content = new StringBuilder();
        //BufferedReader会将文件预加载到缓冲区中,减少文件的读取次数.
        //BufferedReader的默认缓冲区仅有8KB,这里设置为1MB
        try (BufferedReader reader = new BufferedReader(new FileReader(file), 1024*1024)) {
            while (true) {
                int n = reader.read();
                if (n == -1) {
                    //文件已经读完了
                    break;
                }
                char c = (char) n;
                if (flag) {
                    //还没有遇到<
                    if (c == '<') {
                        //c就是<,标志设置为false
                        flag = false;
                    } else {
                        //c是普通字符并且不是换行"\n" "\r",则添加到结果集,是换行,替换成空格
                        if (c == '\n' || c == '\r') {
                            c = ' ';
                        }
                        content.append(c);
                    }
                } else {
                    //已经遇到了<
                    if (c == '>') {
                        //遇到>,标志位设置为true
                        flag = true;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return content.toString();
    }

    //解析URL
    private String parseURL(File file) {
        String oracle = "https://docs.oracle.com/en/java/javase/17/docs/api/";
        String fileURL = file.getAbsolutePath().substring(INPUT_PATH.length()).replace("\\", "/");
        return oracle + fileURL;
    }

    //解析标题
    private String parseTitle(File file) {
        //Java标准库的命名非常规范,可以直接从文件名中获取标题
        //根据.分割来丢弃后缀名
        //split参数为正则表达式,正则表达式中符号.有特殊含义,"\\."由Java解析后为"\.",再由正则表达式解析"\.",表示字面意义的.符号
        String[] split = file.getName().split("\\.");
        return split[0];
    }

    //遍历目录,并将结果装入列表中
    private void enumFile(String inputPath, ArrayList<File> fileList) {
        File rootPath = new File(inputPath);
        //listFiles获取当前目录下的文件或路径
        File[] files = rootPath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                //如果是目录,获取目录中内容
                enumFile(file.getAbsolutePath(), fileList);
            } else {
                //不是目录,是html文件则添加到文件列表中
                //endsWith,String类的方法,判断字符串是否以传入参数结尾
                if (file.getAbsolutePath().endsWith(".html")) {
                    fileList.add(file);
                }
            }
        }
    }

    public static void main(String[] args) {
        //实现整个制作索引的过程
        Parser parser = new Parser();
        parser.runByThread();
    }
}
