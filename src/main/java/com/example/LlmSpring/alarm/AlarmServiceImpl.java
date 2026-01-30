package com.example.LlmSpring.alarm;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {
    private final AlarmMapper alarmMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;

    // 사용자 ID별 Emitter 저장소
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void createAlarm(AlarmVO alarmVO) {
        alarmMapper.insertAlarm(alarmVO);

        // 실시간 전송
        String receiverId = alarmVO.getUserId();
        SseEmitter emitter = emitters.get(receiverId);

        if(emitter != null){
            try{
                emitter.send(SseEmitter.event()
                        .name("alarm")
                        .data(alarmVO));
            }catch (Exception e){
                emitters.remove(receiverId);
            }
        }
    }

    @Override
    public void sendInviteAlarm(String senderId, String receiverId, int projectId) {
        String senderName = userMapper.getUserName(senderId);
        String projectName = projectMapper.getProjectName(projectId);

        String content = senderName+"님이 " + projectName + "에 초대했습니다";

        AlarmVO alarmVO = AlarmVO.builder()
                        .userId(receiverId)
                        .senderId(senderId)
                        .projectId(projectId)
                        .type("INVITE")
                        .referenceId(projectId) // 초대는 프로젝트 자체가 참조 대상
                        .content(content)
                        .url("/project/invite/" + projectId)
                        .build();

        createAlarm(alarmVO);
    }

    @Override
    public List<AlarmVO> getMyAlarms(String userId) {
        return alarmMapper.selectMyAlarms(userId);
    }

    @Override
    public void markAsRead(int alarmId) {
        alarmMapper.markAsRead(alarmId);
    }

    @Override
    public void markAllAsRead(String userId) {
        System.out.println("전부 읽음 서비스 진입");
        alarmMapper.markAllAsRead(userId);
    }

    @Override
    public int getUnreadCount(String userId) {
        return alarmMapper.countUnreadAlarms(userId);
    }


    @Override
    @Transactional
    public void deleteReadAlarms(String userId) {
        alarmMapper.deleteReadAlarms(userId);
    }

    @Override
    @Transactional
    public void deleteAllAlarms(String userId) {
        alarmMapper.deleteAllAlarms(userId);
    }

    @Override
    public SseEmitter subscribe(String userId) {
        // 1. Emitter (타임아웃 1시간)
        SseEmitter emitter = new SseEmitter(60*60*1000L);

        // 2. emitter 저장
        emitters.put(userId, emitter);

        // 3. 연결 종료/타임아웃/에러 시 제거
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // 4. 연결 즉시 더미 데이터 전송
        try{
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        }catch (Exception e){
            emitters.remove(userId);
        }

        return emitter;
    }
}
