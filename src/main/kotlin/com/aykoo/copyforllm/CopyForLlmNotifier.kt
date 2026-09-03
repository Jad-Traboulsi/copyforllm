package com.aykoo.copyforllm

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Displays balloon notifications in the IDE for the CopyForLlm+ actions. */
object CopyForLlmNotifier {

    private const val NOTIFICATION_GROUP_ID = "CopyForLLMNotifications"

    fun notify(project: Project, type: NotificationType, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(content, type)
            .notify(project)
    }
}
