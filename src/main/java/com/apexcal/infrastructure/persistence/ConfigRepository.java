package com.apexcal.infrastructure.persistence;

import com.apexcal.domain.semester.SemesterConfig;
import java.util.Optional;

public interface ConfigRepository {
    Optional<SemesterConfig> loadSemesterConfig();

    void saveSemesterConfig(SemesterConfig config);

    Optional<String> findValue(String key);

    void putValue(String key, String value);
}