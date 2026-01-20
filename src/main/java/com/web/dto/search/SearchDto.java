package com.web.dto.search;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
public class SearchDto implements Serializable {
    private Integer pageIndex = 1;
    private Integer pageSize = 12;
    private String keyword;
    private String sortField;
    private String sortDirection;
    private Map<String, Object> filters = new HashMap<>();

    public SearchDto(Integer pageIndex, Integer pageSize) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
    }

    public SearchDto(Integer pageIndex, Integer pageSize, String keyword) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.keyword = keyword;
    }

    public SearchDto addFilter(String key, Object value) {
        if (key != null && value != null) {
            this.filters.put(key, value);
        }
        return this;
    }

    public SearchDto removeFilter(String key) {
        if (key != null) {
            this.filters.remove(key);
        }
        return this;
    }

    public SearchDto setSort(String field, String direction) {
        this.sortField = field;
        this.sortDirection = direction;
        return this;
    }

    public void setPageIndex(Integer pageIndex) {
        this.pageIndex = pageIndex;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters != null ? filters : new HashMap<>();
    }

    @Override
    public String toString() {
        return "SearchDto{" +
                "pageIndex=" + pageIndex +
                ", pageSize=" + pageSize +
                ", keyword='" + keyword + '\'' +
                ", sortField='" + sortField + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                ", filters=" + filters +
                '}';
    }
}
