package com.apexcal.infrastructure.persistence;

import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskType;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    List<TaskItem> findAllActive();

    Optional<TaskItem> findById(String uuid);

    TaskItem save(TaskItem task);

    void softDelete(String uuid);

    long countByType(TaskType type);

    long countByTypeAndSource(TaskType type, TaskSource source);

    void deleteByTypeAndSource(TaskType type, TaskSource source);
}