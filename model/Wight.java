package com.springboot.documentsearcher.model;

import lombok.Data;

//文档id与词的相关性进行包裹
@Data
public class Wight {
    private int docId;
    //文档与词的相关性,用词出现的次数表示
    private int wight;

    public int getWight() {
        return wight;
    }

    public void setWight(int wight) {
        this.wight = wight;
    }

    public int getDocId() {
        return docId;
    }

    public void setDocId(int docId) {
        this.docId = docId;
    }
}
