package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.HistoryReport;
import com.rookies4.finalProject.domain.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoryReportRepository extends JpaRepository<HistoryReport,Long> {
    List<HistoryReport> findByPortfolio(Portfolio portfolio);
}
