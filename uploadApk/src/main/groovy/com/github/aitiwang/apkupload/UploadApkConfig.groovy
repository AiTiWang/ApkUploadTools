package com.github.aitiwang.apkupload


import org.gradle.api.Project

class UploadApkConfig {
    public String pgyApiKey;
    public String pgyApiUrl;
    public String firApiKey;
    public String firApiUrl;
    public String buildUpdateDescription;
    public boolean enableGitCommitLog = false;
    public boolean enablePgyer = false;
    public boolean enableFir = false;

    public long okHttpConnectTimeout = 20_000L;
    public long okHttpReadTimeout = 120_000L;
    public long okHttpWriteTimeout = 120_000L;
    public UploadApkConfig() {
    }

    public UploadApkConfig(String pgyApiKey, String firApiKey) {
        this.pgyApiKey = pgyApiKey;
        this.firApiKey = firApiKey;

    }

    public isNeedToUpload(){
        boolean isNeedToUpload = false;
        if (enableFir){
            if (StringUtils.isNotEmpty(firApiKey)){
                isNeedToUpload = true
            }
        }
        if (enablePgyer){
            if (StringUtils.isNotEmpty(pgyApiKey)){
                isNeedToUpload = true
            }
        }
        return isNeedToUpload;
    }
    public static UploadApkConfig getConfig(Project project) {
        UploadApkConfig extension = project.getExtensions().findByType(UploadApkConfig.class);
        if (extension == null) {
            extension = new UploadApkConfig();
        }
        return extension;
    }


    @Override
    public String toString() {
        return "UploadApkConfig{" +
                "pgyApiKey='" + pgyApiKey + '\'' +
                ", firApiKey='" + firApiKey + '\'' +
                '}';
    }
}