package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_relations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRelations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id")
    private Long relationId;

    // 원인 종목 (영향을 주는 종목, 예: 엔비디아)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stock_id")
    private Stock fromStock;

    // 결과 종목 (영향을 받는 종목, 예: SK하이닉스)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_stock_id")
    private Stock toStock;

    @Column(name = "relation_type", length = 50)
    private String relationType; // SUPPLIER, COMPETITOR, THEME_PARTNER 등

    @Column(name = "weight")
    private Float weight; // 관계 강도 (0.0 ~ 1.0, GNN 학습 가중치)

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 관계 설명 (예: "HBM 독점 공급")

    // toString에서 무한 루프 방지
    @Override
    public String toString() {
        return "StockRelation{" +
                "relationId=" + relationId +
                ", fromStockId=" + (fromStock != null ? fromStock.getStockId() : null) +
                ", toStockId=" + (toStock != null ? toStock.getStockId() : null) +
                ", relationType='" + relationType + '\'' +
                ", weight=" + weight +
                '}';
    }
}