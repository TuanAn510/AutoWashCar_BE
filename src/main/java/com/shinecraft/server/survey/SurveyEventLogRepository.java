package com.shinecraft.server.survey;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyEventLogRepository extends JpaRepository<SurveyEventLog, Long> {
    List<SurveyEventLog> findTop100ByOrderByCreatedAtDesc();
}
