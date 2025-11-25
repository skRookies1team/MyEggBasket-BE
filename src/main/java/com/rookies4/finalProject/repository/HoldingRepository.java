package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.dto.HoldingDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
}
