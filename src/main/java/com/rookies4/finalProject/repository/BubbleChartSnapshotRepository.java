package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.BubbleChartSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BubbleChartSnapshotRepository extends JpaRepository<BubbleChartSnapshot, Long> {
    Optional<BubbleChartSnapshot> findTopByOrderByCreatedAtDesc();
}
