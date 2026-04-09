package com.springboot.documentsearcher.model;
import lombok.Data;

@Data
public class DocInfo {
    private int docId;
    private String title;
    private String URL;
    private String content;

    public DocInfo(String title, String URL, String content) {
        this.title = title;
        this.URL = URL;
        this.content = content;
    }

    public DocInfo() {
    }
}
