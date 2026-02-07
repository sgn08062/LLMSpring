package com.example.LlmSpring.scheduler;

import com.example.LlmSpring.alarm.AlarmMapper;
import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.alarm.AlarmVO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import com.example.LlmSpring.report.dailyreport.DailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectScheduler {

    private final ProjectMapper projectMapper;
    private final AlarmMapper alarmMapper;
    private final DailyReportService dailyReportService;
    private final AlarmService alarmService;

    /**
     * 매일 자정(00:00:00)에 실행
     * 1. 마감 임박(D-1) 알림 발송
     * 2. 마감된 프로젝트 자동 완료 처리 및 알림 발송
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void runDailyProjectCheck() {
        checkDueTomorrowProjects(); // 마감 임박 D-1
        checkOverdueProjects(); // 자동 완료 D-Day+1
        checkHardDeleteDueTomorrow(); // 영구 삭제 예고 D-1
        notifyPermanentDelete();         // 영구 삭제 알림 (D-Day+1)
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduleDailyReportGeneration(){
        // 1. 현재 시간
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MINUTES);
        // 시간을 문자열 "HH:mm" (예: "12:00")으로 변환
        String timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        // 2. 해당 시간에 리포트를 생성해야 하는 프로젝트 조회
        List<ProjectVO> targetProjects = projectMapper.selectProjectsByReportTime(timeStr);
        if(targetProjects.isEmpty()){
            return;
        }

        // 3. 각 프로젝트의 멤버별로 리포트 생성 요청
        for(ProjectVO project: targetProjects){
            if (project.getDeletedAt() != null && project.getStatus().equals("DONE")) {
                continue;
            }
            triggerReportForMembers(project);
        }
    }

    // 1. 마감 임박 프로젝트 처리
    private void checkDueTomorrowProjects() {
        List<ProjectVO> dueProjects = projectMapper.getProjectsDueTomorrow();
        if (dueProjects.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();

        for (ProjectVO project : dueProjects) {

            if (project.getDeletedAt() != null) {
                continue;
            }

            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());

//            for (String memberId : memberIds) {
//                alarmList.add(AlarmVO.builder()
//                        .userId(memberId)
//                        .senderId(null) // 시스템 알림
//                        .projectId(project.getProjectId())
//                        .type("PROJECT_DUE_SOON")
//                        .content("📢 프로젝트 '" + project.getName() + "' 마감이 하루 남았습니다!")
//                        .url("/projects/" + project.getProjectId()) // 클릭 시 이동 경로
//                        .build());
//            }
//        }
//
//        if (!alarmList.isEmpty()) {
//            alarmMapper.insertAlarmsBatch(alarmList);
//            log.info(">>> [D-1 Notification] Sent {} alarms.", alarmList.size());
//        }
//    }
            for (String memberId : memberIds) {
                AlarmVO alarm = AlarmVO.builder()
                        .userId(memberId)
                        .senderId(null) // 시스템 알림
                        .projectId(project.getProjectId())
                        .type("PROJECT_DUE_SOON")
                        .content("📢 프로젝트 '" + project.getName() + "' 마감이 하루 남았습니다!")
                        .url("/projects/" + project.getProjectId())
                        .build();

                // [변경] Service를 통해 알림 생성 -> SSE 전송됨
                alarmService.createAlarm(alarm);
            }
        }
        log.info(">>> [D-1 Notification] Processed {} projects.", dueProjects.size());
    }

    // 2. 마감된 프로젝트 처리
    private void checkOverdueProjects() {
        List<ProjectVO> overdueProjects = projectMapper.getOverdueActiveProjects();
        if (overdueProjects.isEmpty()) return;

        // 2-1. 상태 업데이트 (ACTIVE -> DONE)
        List<Integer> projectIds = overdueProjects.stream()
                .filter(p -> p.getDeletedAt() == null)
                .map(ProjectVO::getProjectId)
                .collect(Collectors.toList());

        if (!projectIds.isEmpty()) { // [추가] 빈 리스트 체크 (필터링 후 비어있을 수 있음)
            projectMapper.updateProjectsStatusToDone(projectIds);
            log.info(">>> [Auto-Close] Closed {} projects: {}", projectIds.size(), projectIds);
        }

//        // 2-2. 완료 알림 발송
//        List<AlarmVO> alarmList = new ArrayList<>();
//
//        for (ProjectVO project : overdueProjects) {
//
//            if (project.getDeletedAt() != null) {
//                continue;
//            }
//
//            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());
//
//            for (String memberId : memberIds) {
//                alarmList.add(AlarmVO.builder()
//                        .userId(memberId)
//                        .senderId(null)
//                        .projectId(project.getProjectId())
//                        .type("PROJECT_FINISHED")
//                        .content("📢 프로젝트 '" + project.getName() + "'의 마감 기한이 지나 자동으로 완료 처리되었습니다.")
//                        .url("/projects/" + project.getProjectId())
//                        .build());
//            }
//        }
//
//        if (!alarmList.isEmpty()) {
//            alarmMapper.insertAlarmsBatch(alarmList);
//            log.info(">>> [Finished Notification] Sent {} alarms.", alarmList.size());
//        }
//    }
        // 2-2. 완료 알림 발송
        for (ProjectVO project : overdueProjects) {
            if (project.getDeletedAt() != null) continue;

            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());

            for (String memberId : memberIds) {
                AlarmVO alarm = AlarmVO.builder()
                        .userId(memberId)
                        .senderId(null)
                        .projectId(project.getProjectId())
                        .type("PROJECT_FINISHED")
                        .content("📢 프로젝트 '" + project.getName() + "'의 마감 기한이 지나 자동으로 완료 처리되었습니다.")
                        .url("/projects/" + project.getProjectId())
                        .build();

                // [변경] Service 호출
                alarmService.createAlarm(alarm);
            }
        }
    }


    // 영구 삭제 임박(D-1) 알림 발송
    private void checkHardDeleteDueTomorrow() {
        List<ProjectVO> hardDeleteProjects = projectMapper.getProjectsDueForHardDeleteTomorrow();
        if (hardDeleteProjects.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();
        for (ProjectVO project : hardDeleteProjects) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());
//            for (String memberId : memberIds) {
//                alarmList.add(AlarmVO.builder()
//                        .userId(memberId)
//                        .projectId(project.getProjectId())
//                        .type("PROJECT_HARD_DELETE_SOON")
//                        .content("🚨 프로젝트 '" + project.getName() + "'의 보관 기간이 하루 남았습니다. 내일 영구 삭제 처리됩니다.")
//                        .url("/projects")
//                        .build());
//            }
//        }
//        if (!alarmList.isEmpty()) alarmMapper.insertAlarmsBatch(alarmList);
            for (String memberId : memberIds) {
                AlarmVO alarm = AlarmVO.builder()
                        .userId(memberId)
                        .projectId(project.getProjectId())
                        .type("PROJECT_HARD_DELETE_SOON")
                        .content("🚨 프로젝트 '" + project.getName() + "'의 보관 기간이 하루 남았습니다. 내일 영구 삭제 처리됩니다.")
                        .url("/projects")
                        .build();

                // [변경] Service 호출
                alarmService.createAlarm(alarm);
            }
        }
    }

    // 영구 삭제 처리 알림 (실제 삭제 X, 7일 만료 알림)
    private void notifyPermanentDelete() {
        // 삭제된 지 정확히 7일 된 프로젝트 조회
        List<ProjectVO> deleteTargets = projectMapper.getProjectsDueForHardDeleteToday();
        if (deleteTargets.isEmpty()) return;

        List<AlarmVO> alarmList = new ArrayList<>();
        for (ProjectVO project : deleteTargets) {
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());
//            for (String memberId : memberIds) {
//                alarmList.add(AlarmVO.builder()
//                        .userId(memberId)
//                        .projectId(null) // 삭제 처리되었으므로 링크 없음
//                        .type("PROJECT_PERMANENTLY_DELETED")
//                        // [수정] 7일 만료 메시지
//                        .content("🗑️ 프로젝트 '" + project.getName() + "'의 보관 기간(7일)이 만료되어 영구 삭제 처리되었습니다.")
//                        .url("/projects")
//                        .build());
//            }
//        }
//
//        if (!alarmList.isEmpty()) {
//            alarmMapper.insertAlarmsBatch(alarmList);
//            log.info(">>> [Hard-Delete Notice] Sent {} alarms for 7-day expiration.", alarmList.size());
//        }
//    }
            for (String memberId : memberIds) {
                AlarmVO alarm = AlarmVO.builder()
                        .userId(memberId)
                        .projectId(null) // 삭제됨
                        .type("PROJECT_PERMANENTLY_DELETED")
                        .content("🗑️ 프로젝트 '" + project.getName() + "'의 보관 기간(7일)이 만료되어 영구 삭제 처리되었습니다.")
                        .url("/projects")
                        .build();

                // [변경] Service 호출
                alarmService.createAlarm(alarm);
            }
        }
        log.info(">>> [Hard-Delete Notice] Processed {} projects.", deleteTargets.size());
    }

    // 일일 리포트 생성
    private void triggerReportForMembers(ProjectVO project){
        try{
            List<String> memberIds = projectMapper.getActiveMemberIds(project.getProjectId());

            for(String memberId: memberIds){
                dailyReportService.createSystemReportAsync(project.getProjectId().longValue(), memberId);
            }

            log.info("Triggered async daily reports for project: {}", project.getName());

        }catch (Exception e){
            log.error("Error triggering report for project: " + project.getProjectId(), e);
        }
    }
}