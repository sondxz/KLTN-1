package com.web.service;

import com.web.entity.Diseases;
import com.web.exception.MessageException;
import com.web.repository.DiseasesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

@Service
public class DiseasesService {

    @Autowired
    private DiseasesRepository diseasesRepository;

    // ✅ Lấy danh sách có tìm kiếm + phân trang
    public Page<Diseases> findAllByParam(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return diseasesRepository.findAll(pageable);
        }
        return diseasesRepository.findAllByParam(search.trim(), pageable);
    }

    // ✅ Lấy theo ID
    public Diseases findById(Long id) {
        Optional<Diseases> optional = diseasesRepository.findById(id);
        if (optional.isEmpty()) {
            throw new MessageException("Không tìm thấy bệnh có ID = " + id);
        }
        return optional.get();
    }

    // ✅ Kiểm tra trùng tên
    public boolean existsByName(String name) {
        return diseasesRepository.existsByName(name);
    }

    // ✅ Kiểm tra trùng slug
    public boolean existsBySlug(String slug) {
        return diseasesRepository.existsBySlug(slug);
    }

    // ✅ Thêm hoặc cập nhật
    public Diseases save(Diseases diseases) {
        try {
            if (diseases.getSlug() == null || diseases.getSlug().isEmpty()) {
                diseases.setSlug(null); // gọi SlugGenerator bên trong entity
            }
            return diseasesRepository.save(diseases);
        } catch (Exception e) {
            throw new MessageException("Lỗi dữ liệu khi lưu bệnh: " + e.getMessage());
        }
    }

    // ✅ Xóa
    public void delete(Long id) {
        try {
            diseasesRepository.deleteById(id);
        } catch (Exception e) {
            throw new MessageException("Đã có lỗi khi xóa bệnh!");
        }
    }

    public List<Diseases> findAllList(){
        return diseasesRepository.findAll();
    }

    /**
     * Ghi danh sách bệnh ra CSV.
     */
    public void writeDiseasesToCsv(Writer writer, String q) {
        try {
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
            List<Diseases> list = diseasesRepository.findAllForExport(search);
            writer.write("ID,TEN_BENH,MO_TA,SLUG\n");
            for (Diseases d : list) {
                String line = String.format(
                        "%d,%s,%s,%s\n",
                        d.getId(),
                        escapeCsv(d.getName()),
                        escapeCsv(d.getDescription()),
                        escapeCsv(d.getSlug())
                );
                writer.write(line);
            }
            writer.flush();
        } catch (IOException e) {
            throw new MessageException("Lỗi khi xuất dữ liệu bệnh: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }
}
