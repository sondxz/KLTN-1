package com.web.service;

import com.web.entity.Families;
import com.web.exception.MessageException;
import com.web.repository.FamiliesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
public class FamiliesService {

    @Autowired
    private FamiliesRepository familiesRepository;

    /**
     * Lấy danh sách tất cả families
     */
    public Page<Families> findAll(Pageable pageable, String search) {
        return familiesRepository.findAllByParam(search, pageable);
    }

    /**
     * Lấy families theo ID
     */
    public Families findById(Long id) {
        return familiesRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy family có ID = " + id));
    }

    /**
     * Thêm mới hoặc cập nhật families
     */
    public Families save(Families families) {
        if (families.getId() != null) {
            // --- Cập nhật ---
            Families existing = findById(families.getId());

            // Kiểm tra trùng slug hoặc name với bản ghi khác
            if (familiesRepository.existsBySlug(families.getSlug())
                    && !families.getSlug().equals(existing.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }
            if (familiesRepository.existsByName(families.getName())
                    && !families.getName().equals(existing.getName())) {
                throw new MessageException("Tên đã tồn tại");
            }

            existing.setName(families.getName());
            existing.setSlug(families.getSlug());
            existing.setDescription(families.getDescription());

            return familiesRepository.save(existing);
        } else {
            // --- Thêm mới ---
            if (families.getSlug() == null || families.getSlug().isBlank()) {
                throw new MessageException("Slug không được để trống");
            }
            if (families.getName() == null || families.getName().isBlank()) {
                throw new MessageException("Tên không được để trống");
            }

            if (familiesRepository.existsBySlug(families.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }
            if (familiesRepository.existsByName(families.getName())) {
                throw new MessageException("Tên đã tồn tại");
            }

            return familiesRepository.save(families);
        }
    }

    /**
     * Xóa family theo ID
     */
    public void delete(Long id) {
        if (!familiesRepository.existsById(id)) {
            throw new MessageException("Không tìm thấy family để xóa");
        }
        try {
            familiesRepository.deleteById(id);
        } catch (Exception e) {
            throw new MessageException("Đã có lỗi khi xóa family: " + e.getMessage());
        }
    }

    public List<Families> findAllList(){
        return familiesRepository.findAllByAscName();
    }

    /**
     * Ghi danh sách họ thực vật ra CSV.
     */
    public void writeFamiliesToCsv(Writer writer, String q) {
        try {
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
            List<Families> list = familiesRepository.findAllForExport(search);
            writer.write("ID,HO_THUC_VAT,MO_TA,SLUG\n");
            for (Families f : list) {
                String line = String.format(
                        "%d,%s,%s,%s\n",
                        f.getId(),
                        escapeCsv(f.getName()),
                        escapeCsv(f.getDescription()),
                        escapeCsv(f.getSlug())
                );
                writer.write(line);
            }
            writer.flush();
        } catch (IOException e) {
            throw new MessageException("Lỗi khi xuất dữ liệu họ thực vật: " + e.getMessage());
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
