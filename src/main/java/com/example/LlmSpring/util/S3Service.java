package com.example.LlmSpring.util;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // 프로필 이미지 업로드
    public String uploadFile(MultipartFile file) throws IOException {
        // 1. 파일 이름 중복 방지를 위한 UUID 생성
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String s3FileName = "profileImage/" + UUID.randomUUID().toString().substring(0, 10) + extension;

        // 2. s3에 파일 업로드
        s3Template.upload(bucketName, s3FileName, file.getInputStream());

        // 3. 업로드 된 이미지의 URL 반환
        return "https://" + bucketName + ".s3.ap-northeast-2.amazonaws.com/" + s3FileName;
    }

    //  텍스트 내용을 S3에 파일로 저장하고 URL 반환
    public String uploadTextContent(String path, String content) {

        // String을 InputStream으로 변환
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(contentBytes);

        // S3에 업로드 (이미 경로가 지정되어 들어옴)
        s3Template.upload(bucketName, path, inputStream);

        // 업로드 된 파일의 URL 반환
        return "https://" + bucketName + ".s3.ap-northeast-2.amazonaws.com/" + path;
    }

    // 3. S3 파일 삭제 (프로필 이미지, 리포트 공용)
    public void deleteFile(String fileUrl){
        if(fileUrl == null || fileUrl.isEmpty()) return;

        try{
            String splitStr = ".com/";
            int index = fileUrl.lastIndexOf(splitStr);

            if(index != -1){
                String key = fileUrl.substring(index + splitStr.length());

                // 삭제 수행
                s3Template.deleteObject(bucketName, key);
                log.info("S3 파일 삭제 완료: {}", key);
            }
        }catch(Exception e){
            log.error("S3 파일 삭제 실패 (URL: {}): {}", fileUrl, e.getMessage());
        }
    }
}
