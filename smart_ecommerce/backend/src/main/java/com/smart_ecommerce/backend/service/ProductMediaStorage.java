package com.smart_ecommerce.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ProductMediaStorage {
    private final Path dir;
    public ProductMediaStorage(@Value("${app.upload.dir:./uploads}") String path) {
        dir = Paths.get(path).toAbsolutePath().normalize().resolve("products");
    }
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh phải từ 1 byte đến 5 MB");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ảnh quá lớn");
            String ext;
            if (bytes.length >= 3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) ext="jpg";
            else if (bytes.length >= 8 && (bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71
                    && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10) ext="png";
            else if (bytes.length >= 12 && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) ext="webp";
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ nhận ảnh JPEG, PNG hoặc WebP");
            Files.createDirectories(dir);
            String name = UUID.randomUUID()+"."+ext;
            Files.write(dir.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            return "/api/media/products/"+name;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Không thể lưu ảnh");
        }
    }
    public void removeStored(String url) {
        if (url == null || !url.startsWith("/api/media/products/")) return;
        String name=url.substring("/api/media/products/".length());
        if (!name.matches("[a-fA-F0-9-]{36}\\.(jpg|png|webp)")) return;
        try { Files.deleteIfExists(dir.resolve(name)); } catch (IOException ignored) { }
    }
    public ResponseEntity<Resource> read(String name) {
        if (!name.matches("[a-fA-F0-9-]{36}\\.(jpg|png|webp)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path file=dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType type=name.endsWith("png")? MediaType.IMAGE_PNG :
            name.endsWith("webp")? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "public, max-age=86400")
            .body(new FileSystemResource(file));
    }
}
