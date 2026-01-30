package com.example.LlmSpring.controller;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.alarm.AlarmVO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmService alarmService;
    private final JWTService jwtService;

    // 1. 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<List<AlarmVO>> getMyAlarms(@RequestHeader("Authorization") String authHeader){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        List<AlarmVO> alarms = alarmService.getMyAlarms(userId);
        return ResponseEntity.ok(alarms);
    }

    // 2. 안 읽은 알림 개수 조회
    @GetMapping("/unread")
    public ResponseEntity<Integer> getUnreadCount(@RequestHeader("Authorization") String authHeader){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        int count = alarmService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    // 3. 특정 알림 읽음 처리
    @PatchMapping("/{alarmId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable int alarmId){
        alarmService.markAsRead(alarmId);
        return ResponseEntity.ok().build();
    }

    // 4. 모든 알림 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<?> markAsReadAll(@RequestHeader("Authorization") String authHeader){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        alarmService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
