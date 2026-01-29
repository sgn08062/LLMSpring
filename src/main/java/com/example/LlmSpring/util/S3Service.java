package com.example.LlmSpring.util;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket")
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

    // 기존 이미지 삭제
    public void deleteFile(String fileUrl){
        try{
            String splitStr = ".com/";
            String fileName = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());

            s3Template.deleteObject(bucketName, fileName);
        }catch(Exception e){
            System.out.println("S3 파일 삭제 실패: " + e.getMessage());
        }
    }
}
