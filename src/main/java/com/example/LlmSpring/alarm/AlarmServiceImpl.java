package com.example.LlmSpring.alarm;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
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
    @Transactional
    public void createAlarm(String userId, String content, String type, String url) {
        AlarmVO alarm = AlarmVO.builder()
                .userId(userId)
                .content(content)
                .type(type)
                .url(url)
                .isRead(false)
                // senderId, projectId, referenceId는 null 또는 0으로 들어감
                // DB 제약조건에 따라 필요하다면 파라미터를 추가해야 함
                .build();

        // 기존 메서드 재사용
        createAlarm(alarm);
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
    @Transactional
    public void sendIssueAssignAlarm(String senderId, String receiverId, int projectId, int issueId, String issueTitle) {
        // 1. 자기 자신에게는 알림 보내지 않음
        if (senderId.equals(receiverId)) {
            return;
        }

        // 2. 정보 조회 (보낸 사람 이름, 프로젝트 이름)
        String senderName = userMapper.getUserName(senderId);
        if (senderName.isEmpty()) {
            return;
        }
        String projectName = projectMapper.getProjectName(projectId);
        if (projectName.isEmpty()) {
            return;
        }

        // 3. 메시지 생성
        // 포맷: [프로젝트명] 홍길동님이 '이슈 제목' 이슈를 배정했습니다.
        String content = String.format("[%s] %s님이 '%s' 이슈를 배정했습니다.",
                projectName, senderName, issueTitle);

        // 4. URL 생성 (대시보드로 이동 + 이슈 모달 트리거용 파라미터)
        String url = String.format("/project/%d/dashboard?tab=ISSUE&issueId=%d", projectId, issueId);

        // 5. DB 저장
        AlarmVO alarm = AlarmVO.builder()
                .userId(receiverId)      // 받는 사람
                .senderId(senderId)      // 보낸 사람
                .projectId(projectId)
                .type("TASK_ASSIGN")     // 알림 타입
                .referenceId(issueId)    // 참조 ID (이슈 ID)
                .content(content)
                .url(url)
                .isRead(false)
                .build();

        alarmMapper.insertAlarm(alarm);
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

    @Override
    @Transactional
    public void sendTaskAlarm(String senderId, List<String> receiverIds, int projectId, Long taskId, String taskTitle, String type) {
        // 1. 보낸 사람 정보 조회
        String senderName = userMapper.getUserName(senderId);
        if (senderName == null) return;

        // 2. 프로젝트 정보 조회
        String projectName = projectMapper.getProjectName(projectId);
        if (projectName == null) return;

        // 3. 알림 내용 및 URL 설정
        String content = "";
        String url = String.format("/projectDetail?projectId=%d&taskId=%d", projectId, taskId);

        switch (type) {
            case "CREATE":
                content = String.format("[%s] %s님이 새 업무 '%s'를 배정했습니다.", projectName, senderName, taskTitle);
                break;
            case "UPDATE":
                content = String.format("[%s] %s님이 업무 '%s'의 내용을 수정했습니다.", projectName, senderName, taskTitle);
                break;
            case "STATUS":
                content = String.format("[%s] %s님이 업무 '%s'의 상태를 변경했습니다.", projectName, senderName, taskTitle);
                break;
            case "DELETE":
                content = String.format("[%s] %s님이 업무 '%s'를 삭제했습니다.", projectName, senderName, taskTitle);
                url = String.format("/projectDetail/%d", projectId);
                break;
            case "CHECKLIST":
                content = String.format("[%s] %s님이 업무 '%s'에 체크리스트를 추가하였습니다.", projectName, senderName, taskTitle);
                break;
            case "CHAT":
                content = String.format("[%s] %s님이 업무 '%s'에 댓글을 남겼습니다.", projectName, senderName, taskTitle);
                break;
        }

        // 4. 수신자 목록 순회하며 알림 전송
        if (receiverIds != null && !receiverIds.isEmpty()) {
            for (String receiverId : receiverIds) {
                if (receiverId.equals(senderId)) continue;

                AlarmVO alarm = AlarmVO.builder()
                        .userId(receiverId)
                        .senderId(senderId)
                        .projectId(projectId)
                        .type("TASK_" + type)
                        .referenceId(taskId.intValue())
                        .content(content)
                        .url(url)
                        .isRead(false)
                        .build();

                createAlarm(alarm);
            }
        }
    }
}
