package pro.youquan.gradle.apk.upload;

public class StringUtils {
    public static boolean isNotEmpty(String str) {
        return (str != null && !str.trim().equalsIgnoreCase(""))
    }

}
