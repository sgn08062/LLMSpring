package com.example.LlmSpring.scheduler;

import com.example.LlmSpring.alarm.AlarmMapper;
import com.example.LlmSpring.alarm.AlarmVO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectScheduler {

    private final ProjectMapper projectMapper;
    private final AlarmMapper alarmMapper;

    /**
     * 매일 자정(00:00:00)에 실행
     * 1. 마감 임박(D-1) 알림 발송
     * 2. 마감된 프로젝트 자동 완료 처리 및 알림 발송
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void runDailyProjectCheck() {
        log.info(">>> [Scheduler] Daily Project Check Started...");

        checkDueTomorrowProjects(); // 마감 임박 D-1
        checkOverdueProjects(); // 자동 완료 D-Day+1
        checkHardDeleteDueTomorrow(); // 영구 삭제 예고 D-1
        notifyPermanentDelete();         // 영구 삭제 알림 (D-Day+1)

        log.info(">>> [Scheduler] Daily Project Check Finished.");
    }

    // 1. 마감 임박 프로젝트 처리
    private void checkDueTomorrowProjects() {
        List<ProjectVO> dueProjects = projectMapper.getProjectsDueTomorrow();
        if (dueProjects.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();

        for (ProjectVO project : dueProjects) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());

            for (String memberId : memberIds) {
                alarmList.add(AlarmVO.builder()
                        .userId(memberId)
                        .senderId(null) // 시스템 알림
                        .projectId(project.getProjectId())
                        .type("PROJECT_DUE_SOON")
                        .content("📢 프로젝트 '" + project.getName() + "' 마감이 하루 남았습니다!")
                        .url("/projects/" + project.getProjectId()) // 클릭 시 이동 경로
                        .build());
            }
        }

        if (!alarmList.isEmpty()) {
            alarmMapper.insertAlarmsBatch(alarmList);
            log.info(">>> [D-1 Notification] Sent {} alarms.", alarmList.size());
        }
    }

    // 2. 마감된 프로젝트 처리
    private void checkOverdueProjects() {
        List<ProjectVO> overdueProjects = projectMapper.getOverdueActiveProjects();
        if (overdueProjects.isEmpty()) return;

        // 2-1. 상태 업데이트 (ACTIVE -> DONE)
        List<Integer> projectIds = overdueProjects.stream()
                .map(ProjectVO::getProjectId)
                .collect(Collectors.toList());

        projectMapper.updateProjectsStatusToDone(projectIds);
        log.info(">>> [Auto-Close] Closed {} projects: {}", projectIds.size(), projectIds);

        // 2-2. 완료 알림 발송
        List<AlarmVO> alarmList = new ArrayList<>();

        for (ProjectVO project : overdueProjects) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());

            for (String memberId : memberIds) {
                alarmList.add(AlarmVO.builder()
                        .userId(memberId)
                        .senderId(null)
                        .projectId(project.getProjectId())
                        .type("PROJECT_FINISHED")
                        .content("📢 프로젝트 '" + project.getName() + "'의 마감 기한이 지나 자동으로 완료 처리되었습니다.")
                        .url("/projects/" + project.getProjectId())
                        .build());
            }
        }

        if (!alarmList.isEmpty()) {
            alarmMapper.insertAlarmsBatch(alarmList);
            log.info(">>> [Finished Notification] Sent {} alarms.", alarmList.size());
        }
    }


    // 영구 삭제 임박(D-1) 알림 발송
    private void checkHardDeleteDueTomorrow() {
        List<ProjectVO> hardDeleteProjects = projectMapper.getProjectsDueForHardDeleteTomorrow();
        if (hardDeleteProjects.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();
        for (ProjectVO project : hardDeleteProjects) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());
            for (String memberId : memberIds) {
                alarmList.add(AlarmVO.builder()
                        .userId(memberId)
                        .projectId(project.getProjectId())
                        .type("PROJECT_HARD_DELETE_SOON")
                        .content("🚨 프로젝트 '" + project.getName() + "'의 보관 기간이 하루 남았습니다. 내일 영구 삭제 처리됩니다.")
                        .url("/projects")
                        .build());
            }
        }
        if (!alarmList.isEmpty()) alarmMapper.insertAlarmsBatch(alarmList);
    }

    // 영구 삭제 처리 알림 (실제 삭제 X, 7일 만료 알림)
    private void notifyPermanentDelete() {
        // 삭제된 지 정확히 7일 된 프로젝트 조회
        List<ProjectVO> deleteTargets = projectMapper.getProjectsDueForHardDeleteToday();
        if (deleteTargets.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();
        for (ProjectVO project : deleteTargets) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());
            for (String memberId : memberIds) {
                alarmList.add(AlarmVO.builder()
                        .userId(memberId)
                        .projectId(null) // 삭제 처리되었으므로 링크 없음
                        .type("PROJECT_PERMANENTLY_DELETED")
                        // [수정] 7일 만료 메시지
                        .content("🗑️ 프로젝트 '" + project.getName() + "'의 보관 기간(7일)이 만료되어 영구 삭제 처리되었습니다.")
                        .url("/projects")
                        .build());
            }
        }

        if (!alarmList.isEmpty()) {
            alarmMapper.insertAlarmsBatch(alarmList);
            log.info(">>> [Hard-Delete Notice] Sent {} alarms for 7-day expiration.", alarmList.size());
        }
    }
}