package com.example.LlmSpring.alarm;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AlarmService {
    void createAlarm(AlarmVO alarmVO);
    void sendInviteAlarm(String senderId, String receiverId, int projectId);

    // 이슈 할당 알림
    void sendIssueAssignAlarm(String senderId, String receiverId, int projectId, int issueId, String issueTitle);

    List<AlarmVO> getMyAlarms(String userId);
    void markAsRead(int alarmId);
    void markAllAsRead(String userId);
    int getUnreadCount(String userId);

    void deleteReadAlarms(String userId);
    void deleteAllAlarms(String userId);

    SseEmitter subscribe(String userId);
}
