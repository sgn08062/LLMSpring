package com.example.LlmSpring.alarm;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {
    private final AlarmMapper alarmMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;

    @Override
    @Transactional
    public void createAlarm(AlarmVO alarmVO) {
        alarmMapper.insertAlarm(alarmVO);
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
}
