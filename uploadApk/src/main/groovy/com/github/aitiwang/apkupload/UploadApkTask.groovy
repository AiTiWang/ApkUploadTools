package com.github.aitiwang.apkupload

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import groovy.json.JsonSlurper
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.ApkMeta
import net.dongliu.apk.parser.bean.IconFace
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

public class UploadApkTask extends DefaultTask {
    private Project mProject;
    private BaseVariant mVariant;

    public void init(Project project, BaseVariant variant) {
        this.mVariant = variant;
        this.mProject = project;

        setDescription("UploadApk")
        setGroup("upload")
        dependsOn(variant.getAssembleProvider().get());
    }

    @TaskAction
    public void uploadFile() {
        println("*************** upload start ***************")
        UploadApkConfig uploadApkConfig = UploadApkConfig.getConfig(mProject)
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
               .connectTimeout(Math.max(uploadApkConfig.okHttpConnectTimeout,5_000L))
               .readTimeout(Math.max(uploadApkConfig.okHttpReadTimeout,10_000L))
               .writeTimeout(Math.max(uploadApkConfig.okHttpWriteTimeout,10_000L))
                .build()
        String changeLog = "";
        for (BaseVariantOutput output : mVariant.getOutputs()) {
            File file = output.getOutputFile()
            if (file == null || !file.exists()) {
                println("apk file is not exist")
                mProject.logger.error("apk file is not exist")
                return
            } else {
                if (StringUtils.isNotEmpty(uploadApkConfig.buildUpdateDescription)){
                    changeLog =  uploadApkConfig.buildUpdateDescription
                }
                if (uploadApkConfig.isNeedToUpload()){
                    String pgyApiKey = uploadApkConfig.pgyApiKey
                    String firApiKey = uploadApkConfig.firApiKey
                    System.println(uploadApkConfig.toString())
                    if (StringUtils.isNotEmpty(pgyApiKey) && uploadApkConfig.enablePgyer) {
                        println("************** upload to pgyer start **************")
                        uploadFileToPgyer(okHttpClient,uploadApkConfig,file, pgyApiKey,changeLog)
                        println("************** upload to pgyer finish **************")
                    }
                    if (StringUtils.isNotEmpty(firApiKey)&& uploadApkConfig.enableFir) {
                        println("************** upload to fir start **************")
                        uploadFileToFir(okHttpClient,uploadApkConfig,file, firApiKey, changeLog,output)
                        println("************** upload to fir finish **************")
                    }
                }

            }
        }
        println("*************** upload finish ***************")
    }


    void uploadFileToFir(OkHttpClient okHttpClient, File file,
                         String apikey,String changeLog,
                         BaseVariantOutput output) {

        if (output instanceof ApkVariantOutputImpl) {
            ApkVariantOutputImpl apkVariantOutput = output;

            ApkFile apkFile = new ApkFile(file);
            apkFile.setPreferredLocale(Locale.SIMPLIFIED_CHINESE);
            ApkMeta apkMeta = apkFile.getApkMeta();
            String bundleId = apkMeta.packageName
            int versionCode = apkMeta.versionCode
            String versionName = apkMeta.versionName
            String appName = apkMeta.label
            List<IconFace> icons = apkFile.getAllIcons()
            byte[] iconByte = null;
          //  System.println("apk icons size->"+icons.size())
            for (IconFace icon : icons) {
                byte[] itemByte = icon.data
                if (iconByte == null) {
                    iconByte = itemByte
                } else if (itemByte.length > iconByte.length) {
                    iconByte = itemByte
                }
            }
            File iconFile = null;
            if (iconByte != null) {
                File iconFileTempDir = new File(file.parentFile, "iconTemp")
                if (!iconFileTempDir.exists()) {
                    iconFileTempDir.mkdirs()
                }
                iconFile = new File(iconFileTempDir, "icon.png")
                if (!iconFile.exists()) {
                    iconFile.delete()
                }
                try {
                    Path path = Paths.get(iconFile.path)
                    Files.write(path, iconByte)
                } catch (Exception e) {
                    mProject.logger.error("icon file save fail,reason->"+e.localizedMessage)
                    iconFile = null;
                }
            }
            HashMap<String, String> firPostMap = new HashMap<>();
            firPostMap.put("type", "android")
            firPostMap.put("bundle_id", bundleId)
            firPostMap.put("api_token", apikey)

            Provider<Directory> manifests = apkVariantOutput.packageApplication.manifests
          //  System.println("manifests--->" + manifests.toString())
            String content = "{\"type\":\"android\", \"bundle_id\":\"${bundleId}\", \"api_token\":\"${apikey}\"}"
            RequestBody firRequestBody = RequestBody.create(MediaType.parse("application/json;charset=utf-8")
                    , content);
            String  url = uploadApkConfig.firApiUrl;
            if (!StringUtils.isNotEmpty(url)){
                url = "http://api.bq04.com/apps";
            }
            Request firRequest = new Request.Builder()
                    .url(url)
                    .post(firRequestBody)
                    .build()
            try {
                Response response = okHttpClient.newCall(firRequest).execute();
                String result = response.body().string();
                System.println(result)
                mProject.logger.info("fir api respone->"+result)
                def resp = new JsonSlurper().parseText(result)
                if (resp == null) {
                    mProject.logger.error("fir upload apk fail,reason-> fir api respone null")
                } else {
                    def cert = resp.cert
                    if (cert == null) {
                        mProject.logger.error("fir upload apk fail,reason-> fir api respone cert null")
                    } else {
                        if (iconFile != null) {
                            System.println("************* fir icon upload start *************")
                            def iconKey = cert.icon.key
                            def iconToken = cert.icon.token
                            def iconUploadUrl = cert.icon.upload_url
                            uploadApkFileToQiniu(okHttpClient,iconKey,iconToken,iconUploadUrl,appName,
                            versionName,versionCode,"",iconFile,true)
                            System.println("************* fir icon upload finish *************")
                        }


                        def binaryKey = cert.binary.key
                        def binaryToken = cert.binary.token
                        def binaryUploadUrl = cert.binary.upload_url
                        System.println("************* fir apk upload start *************")
                        uploadApkFileToQiniu(okHttpClient,binaryKey,binaryToken,binaryUploadUrl,appName,
                                versionName,versionCode,changeLog,file,false)
                        System.println("************* fir apk upload finish *************")
                    }
                }
            } catch (Exception e) {
                String msg = "fir upload apk fail , reason->"+e.localizedMessage;
                mProject.logger.error(msg)
                throw new GradleException(msg,e);
            }
        } else {
            throw new GradleException("out put is no apk")
        }

    }

    boolean uploadApkFileToQiniu(OkHttpClient okHttpClient,String key, String token, String uploadUrl, String appName,
                                 String versionName, int versionCode, String changeLog, File file,
                                 boolean isIcon) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", key)
                .addFormDataPart("token", token)
        if (!isIcon) {
            bodyBuilder.addFormDataPart("x:name", appName)
            bodyBuilder.addFormDataPart("x:version", versionName)
            bodyBuilder.addFormDataPart("x:build", String.valueOf(versionCode))
            bodyBuilder.addFormDataPart("x:changelog", changeLog)
        }
        bodyBuilder.addFormDataPart("file", file.name, RequestBody.create(MediaType.parse("*/*"), file))
        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(bodyBuilder.build())
                .build()
        try {
            Response response = okHttpClient.newCall(request).execute();
            String result = response.body().string();
            System.println(result)
            if (isIcon) {
                mProject.logger.info("upload icon file to qiniu,result->" + result)
            } else {
                mProject.logger.info("upload apk file to qiniu,result->" + result)
            }
            def resp = new JsonSlurper().parseText(result)
            if (resp!=null){
                def isCompleted = resp.is_completed
                if (isCompleted){
                    if (isIcon) {
                        mProject.logger.info("upload icon file to qiniu success")
                    } else {
                        mProject.logger.info("upload apk file to qiniu success" )
                    }
                    return true
                }else {
                    return false
                }

            }else {
                return false
            }

        } catch (IOException e) {
            mProject.logger.error(e.localizedMessage)
            return false
        }
    }

    void uploadFileToPgyer(OkHttpClient okHttpClient,
                            UploadApkConfig uploadApkConfig,
                           File file, String apikey,String changeLog) {
        System.println("************* Pgyer apk upload start *************")
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("_api_key", apikey)
                .addFormDataPart("buildUpdateDescription", changeLog)
                .addFormDataPart("file", file.name, RequestBody.create(MediaType.parse("*/*"), file))
        String  url = uploadApkConfig.pgyApiUrl;
        if (!StringUtils.isNotEmpty(url)){
            url = "https://www.pgyer.com/apiv2/app/upload";
        }
        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build()

        try {
            Response response = okHttpClient.newCall(request).execute();
            String result = response.body().string();
            println("result->"+result)
            mProject.logger.info(result)
        } catch (IOException e) {
            String msg = "Pgyer apk upload  error , reason->"+e.localizedMessage;
            mProject.logger.error(msg)
            throw new GradleException(msg,e);
        }

        System.println("************* Pgyer apk upload finish *************")
    }
}