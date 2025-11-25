package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker; // 종목코드 (예: 005930)

    @Column(name = "name", nullable = false, length = 100)
    private String name; // 종목명 (예: 삼성전자)

    @Column(name = "market_type", length = 20)
    private String marketType; // KOSPI, KOSDAQ

    @Column(name = "sector", length = 50)
    private String sector; // 섹터 (반도체, 2차전지 등)

    @Column(name = "industry_code", length = 20)
    private String industryCode; // 산업분류코드

    // 이 종목이 영향을 주는 관계들 (from_stock)
    @OneToMany(mappedBy = "fromStock", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockRelations> outgoingRelations = new ArrayList<>();

    // 이 종목이 영향을 받는 관계들 (to_stock)
    @OneToMany(mappedBy = "toStock", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockRelations> incomingRelations = new ArrayList<>();

    // 연관관계 편의 메서드
    public void addOutgoingRelation(StockRelations relation) {
        outgoingRelations.add(relation);
        relation.setFromStock(this);
    }

    public void addIncomingRelation(StockRelations relation) {
        incomingRelations.add(relation);
        relation.setToStock(this);
    }

    @Override
    public String toString() {
        return "Stock{" +
                "stockId=" + stockId +
                ", ticker='" + ticker + '\'' +
                ", name='" + name + '\'' +
                ", sector='" + sector + '\'' +
                '}';
    }
}