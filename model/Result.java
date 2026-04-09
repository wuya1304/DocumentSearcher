package com.springboot.documentsearcher.model;

import lombok.Data;

//结果类
@Data
public class Result {
    private String title;
    private String URL;
    //描述 正文的一段摘要
    private String desc;

    public Result() {
    }

    public Result(String title, String URL, String desc) {
        this.title = title;
        this.URL = URL;
        this.desc = desc;
    }
}
