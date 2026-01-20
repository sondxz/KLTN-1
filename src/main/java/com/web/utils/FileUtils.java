package com.web.utils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    private FileUtils() {}

    /**
     * Lấy phần mở rộng của tên tập tin
     * @param filename Tên tập tin
     * @return Phần mở rộng của tập tin hoặc empty Optional nếu không có
     */
    public static Optional<String> getFileExtension(String filename) {
        if (StringUtils.isBlank(filename)) {
            return Optional.empty();
        }

        int pos = filename.lastIndexOf('.');
        return pos > 0 ? Optional.of(filename.substring(pos + 1)) : Optional.empty();
    }

    /**
     * Lưu dữ liệu dạng byte vào tập tin
     * @param filePath Đường dẫn tập tin
     * @param data Dữ liệu cần lưu
     * @return true nếu lưu thành công, false nếu có lỗi
     */
    public static boolean saveFile(String filePath, byte[] data) {
        try {
            Files.write(Path.of(filePath), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Error saving file to path: {}", filePath, e);
            return false;
        }
    }

    /**
     * Lưu MultipartFile vào đường dẫn chỉ định
     * @param filePath Đường dẫn tập tin
     * @param file MultipartFile cần lưu
     * @return true nếu lưu thành công, false nếu có lỗi
     */
    public static boolean saveFile(String filePath, MultipartFile file) {
        try {
            Files.write(Path.of(filePath), file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Error saving MultipartFile to path: {}", filePath, e);
            return false;
        }
    }

    /**
     * Xóa tập tin tại đường dẫn chỉ định
     * @param filePath Đường dẫn tập tin cần xóa
     * @return true nếu xóa thành công, false nếu có lỗi
     */
    public static boolean deleteFile(String filePath) {
        try {
            return Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            log.error("Error deleting file at path: {}", filePath, e);
            return false;
        }
    }

    /**
     * Đọc nội dung của tập tin thành mảng byte
     * @param filePath Đường dẫn tập tin
     * @return Optional chứa dữ liệu đọc được hoặc empty nếu có lỗi
     */
    public static Optional<byte[]> readFile(String filePath) {
        try {
            return Optional.of(Files.readAllBytes(Path.of(filePath)));
        } catch (IOException e) {
            log.error("Error reading file from path: {}", filePath, e);
            return Optional.empty();
        }
    }

    /**
     * Kiểm tra xem tập tin có tồn tại không
     * @param filePath Đường dẫn tập tin
     * @return true nếu tập tin tồn tại, false nếu không
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Path.of(filePath));
    }

    /**
     * Tạo thư mục nếu chưa tồn tại
     * @param directoryPath Đường dẫn thư mục
     * @return true nếu thư mục đã tồn tại hoặc được tạo thành công, false nếu có lỗi
     */
    public static boolean createDirectoryIfNotExists(String directoryPath) {
        try {
            Files.createDirectories(Path.of(directoryPath));
            return true;
        } catch (IOException e) {
            log.error("Error creating directory: {}", directoryPath, e);
            return false;
        }
    }
}
