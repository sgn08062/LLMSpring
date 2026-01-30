package com.example.LlmSpring.alarm;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AlarmMapper {
    // 1. 알림 생성
    void insertAlarm(AlarmVO alarmVO);

    // 2. 내 알림 목록 조회
    List<AlarmVO> selectMyAlarms(String userId);

    // 3. 알림 읽음 처리
    void markAsRead(int alarmId);

    // 4. 알림 전체 읽음 처리
    void markAllAsRead(String userId);

    // 5. 읽지 않은 알림 개수 조회
    int countUnreadAlarms(String userId);

    // 6. 알림 삭제
    void deleteAlarm(int alarmId);

    // 7. 읽은 알림 삭제
    void deleteReadAlarms(String userId);

    // 8. 모든 알림 삭제
    void deleteAllAlarms(String userId);
}
