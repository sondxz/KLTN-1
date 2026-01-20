package com.web.utils;

import com.web.dto.search.SearchDto;
import org.springframework.data.domain.*;
import org.springframework.util.Assert;

import javax.persistence.Query;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PageUtils {
    private PageUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Lấy nội dung phân trang từ Query.
     *
     * @param dto Đối tượng chứa thông tin phân trang.
     * @param q   Query để lấy dữ liệu.
     * @param <T> Kiểu dữ liệu của entity.
     * @return Danh sách các entity.
     */
    public static <T> List<T> getContent(SearchDto dto, Query q) {
        Assert.notNull(dto, "SearchDto must not be null");
        Assert.notNull(q, "Query must not be null");

        int pageIndex = getPageIndex(dto.getPageIndex());
        int pageSize = getPageSize(dto.getPageSize());

        q.setFirstResult(pageIndex * pageSize);
        q.setMaxResults(pageSize);

        @SuppressWarnings("unchecked")
        List<T> result = q.getResultList();
        return result;
    }

    /**
     * Tạo đối tượng Page từ danh sách entities và Query đếm tổng số bản ghi.
     *
     * @param entities Danh sách entities.
     * @param dto      Đối tượng chứa thông tin phân trang.
     * @param qCount   Query để đếm tổng số bản ghi.
     * @param <T>      Kiểu dữ liệu của entity.
     * @return Đối tượng Page.
     */
    public static <T> Page<T> getPage(List<T> entities, SearchDto dto, Query qCount) {
        Assert.notNull(entities, "Entities must not be null");
        Assert.notNull(dto, "SearchDto must not be null");
        Assert.notNull(qCount, "Count Query must not be null");

        Number countResult = (Number) qCount.getSingleResult();
        long count = countResult.longValue();

        // Tạo Pageable với thông tin sort nếu có
        Pageable pageable;
        if (dto.getSortField() != null && !dto.getSortField().isEmpty()) {
            Sort sort = createSort(dto.getSortField(), dto.getSortDirection());
            pageable = getPageable(dto.getPageIndex(), dto.getPageSize(), sort);
        } else {
            pageable = getPageable(dto.getPageIndex(), dto.getPageSize());
        }

        return new PageImpl<>(entities, pageable, count);
    }

    /**
     * Tạo đối tượng Page từ Query và Query đếm tổng số bản ghi.
     *
     * @param dto    Đối tượng chứa thông tin phân trang.
     * @param q      Query để lấy dữ liệu.
     * @param qCount Query để đếm tổng số bản ghi.
     * @param <T>    Kiểu dữ liệu của entity.
     * @return Đối tượng Page.
     */
    public static <T> Page<T> getPage(SearchDto dto, Query q, Query qCount) {
        List<T> entities = getContent(dto, q);
        return getPage(entities, dto, qCount);
    }

    /**
     * Tạo đối tượng Page với chuyển đổi sang DTO.
     *
     * @param dto       Đối tượng chứa thông tin phân trang.
     * @param q         Query để lấy dữ liệu.
     * @param qCount    Query để đếm tổng số bản ghi.
     * @param converter Hàm chuyển đổi từ entity sang DTO.
     * @param <T>       Kiểu dữ liệu của entity.
     * @param <D>       Kiểu dữ liệu của DTO.
     * @return Đối tượng Page chứa DTO.
     */
    public static <T, D> Page<D> getPage(SearchDto dto, Query q, Query qCount, Function<T, D> converter) {
        Assert.notNull(converter, "Converter function must not be null");

        List<T> entities = getContent(dto, q);
        List<D> dtos = entities.stream().map(converter).collect(Collectors.toList());

        Number countResult = (Number) qCount.getSingleResult();
        long count = countResult.longValue();

        // Tạo Pageable với thông tin sort nếu có
        Pageable pageable;
        if (dto.getSortField() != null && !dto.getSortField().isEmpty()) {
            Sort sort = createSort(dto.getSortField(), dto.getSortDirection());
            pageable = getPageable(dto.getPageIndex(), dto.getPageSize(), sort);
        } else {
            pageable = getPageable(dto.getPageIndex(), dto.getPageSize());
        }

        return new PageImpl<>(dtos, pageable, count);
    }

    /**
     * Tạo đối tượng Sort từ tên trường và hướng sắp xếp.
     *
     * @param field     Tên trường sắp xếp.
     * @param direction Hướng sắp xếp (asc/desc).
     * @return Đối tượng Sort.
     */
    public static Sort createSort(String field, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(sortDirection, field);
    }

    /**
     * Tạo đối tượng Pageable với sắp xếp.
     *
     * @param pageIndex Số trang (bắt đầu từ 1).
     * @param pageSize  Kích thước trang.
     * @param sort      Đối tượng Sort.
     * @return Đối tượng Pageable.
     */
    public static Pageable getPageable(Integer pageIndex, Integer pageSize, Sort sort) {
        Assert.notNull(sort, "Sort must not be null");

        int validPageIndex = getPageIndex(pageIndex);
        int validPageSize = getPageSize(pageSize);
        return PageRequest.of(validPageIndex, validPageSize, sort);
    }

    /**
     * Tạo đối tượng Pageable không có sắp xếp.
     *
     * @param pageIndex Số trang (bắt đầu từ 1).
     * @param pageSize  Kích thước trang.
     * @return Đối tượng Pageable.
     */
    public static Pageable getPageable(Integer pageIndex, Integer pageSize) {
        int validPageIndex = getPageIndex(pageIndex);
        int validPageSize = getPageSize(pageSize);
        return PageRequest.of(validPageIndex, validPageSize);
    }

    /**
     * Lấy chỉ số trang hợp lệ (bắt đầu từ 0).
     *
     * @param pageIndex Số trang (bắt đầu từ 1).
     * @return Chỉ số trang hợp lệ.
     */
    public static int getPageIndex(Integer pageIndex) {
        return pageIndex != null && pageIndex > 0 ? pageIndex - 1 : 0;
    }

    /**
     * Lấy kích thước trang hợp lệ.
     *
     * @param pageSize Kích thước trang.
     * @return Kích thước trang hợp lệ.
     */
    public static int getPageSize(Integer pageSize) {
        int defaultPageSize = 10;
        int maxPageSize = 100; // Giới hạn kích thước tối đa

        if (pageSize == null || pageSize <= 0) {
            return defaultPageSize;
        }

        return Math.min(pageSize, maxPageSize);
    }
}
