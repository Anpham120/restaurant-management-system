package com.restaurant.core.common.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

/** Thao tác trên id của Entity: gom id để truy vấn IN, lập bảng tra theo id, tham chiếu không cần truy vấn. */
public final class EntityIdUtils {

    private EntityIdUtils() {
    }

    /** Id khác null, không trùng. Danh sách rỗng thì trả [-1] để câu truy vấn {@code IN (:ids)} vẫn hợp lệ và không khớp dòng nào. */
    public static <T> List<Long> collectIds(List<T> lines, Function<T, Long> id) {
        List<Long> ids = lines.stream().map(id).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    public static <T> Map<Long, T> mapById(List<T> entities, Function<T, Long> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    /** Tham chiếu tới dòng có id cho trước (không truy vấn); id null (cột khóa ngoại không bắt buộc) thì trả null. */
    public static <T> T referenceOrNull(JpaRepository<T, Long> repository, Long id) {
        return id == null ? null : repository.getReferenceById(id);
    }
}
