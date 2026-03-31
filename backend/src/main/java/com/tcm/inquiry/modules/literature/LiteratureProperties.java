package com.tcm.inquiry.modules.literature;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tcm.literature")
public class LiteratureProperties {

    /** 文献文件落盘目录（相对 backend 工作目录） */
    private String storageDir = "data/literature-files";

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }
}
