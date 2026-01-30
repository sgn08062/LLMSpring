package com.example.LlmSpring.alarm;

import java.util.List;

public interface AlarmService {
    void createAlarm(AlarmVO alarmVO);
    void sendInviteAlarm(String senderId, String receiverId, int projectId);

    List<AlarmVO> getMyAlarms(String userId);
    void markAsRead(int alarmId);
    void markAllAsRead(String userId);
    int getUnreadCount(String userId);

    void deleteReadAlarms(String userId);
    void deleteAllAlarms(String userId);
}
