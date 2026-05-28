package com.ndcong.chat.service.impl;

import com.ndcong.chat.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    // Thư mục gốc lưu trữ file
    private final Path fileStorageLocation;

    public FileStorageServiceImpl() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            // Tự động tạo thư mục nếu chưa tồn tại
            Files.createDirectories(this.fileStorageLocation.resolve("images"));
            Files.createDirectories(this.fileStorageLocation.resolve("videos"));
            Files.createDirectories(this.fileStorageLocation.resolve("files"));
        } catch (Exception ex) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ file.", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file) throws IOException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        
        // Tạo tên file mới với UUID để đảm bảo tính duy nhất
        String fileExtension = "";
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String newFileName = UUID.randomUUID().toString() + fileExtension;

        // Phân loại thư mục lưu trữ dựa trên Content Type
        String contentType = file.getContentType();
        String subFolder = "files";
        if (contentType != null) {
            if (contentType.startsWith("image/")) subFolder = "images";
            else if (contentType.startsWith("video/")) subFolder = "videos";
        }

        // Đường dẫn vật lý lưu file
        Path targetLocation = this.fileStorageLocation.resolve(subFolder).resolve(newFileName);
        
        // Copy file vào ổ cứng (Ghi đè nếu trùng tên - dù xác suất UUID trùng là số 0)
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Trả về đường dẫn tương đối để lưu vào Database và Client truy cập
        return "/uploads/" + subFolder + "/" + newFileName;
    }
}