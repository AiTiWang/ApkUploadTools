package com.github.aitiwang.apkupload;

public class StringUtils {
    public static boolean isNotEmpty(String str) {
        return (str != null && !str.trim().equalsIgnoreCase(""))
    }

}
