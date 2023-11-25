package pro.youquan.gradle.apk.upload

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

public class UploadApkPlugin implements Plugin<Project> {
    public static final String UPLOAD_APK_EXTENSION_NAME = "uploadApkConfig";

    @Override
    void apply(Project project) {
        UploadApkConfig extension = project.getExtensions().create(UPLOAD_APK_EXTENSION_NAME, UploadApkConfig.class);
        project.android.applicationVariants.all { variant ->
            String taskSuffix = variant.name.capitalize()
            if (taskSuffix.contains("Release")) {
                TaskContainer taskContainer = project.getTasks();

                String taskName = "assemble" + taskSuffix +"ToPgyerAndFir"
                UploadApkTask uploadApkTask = taskContainer.create(taskName, UploadApkTask.class)
                uploadApkTask.init(project, variant)
            }
        }
    }

}