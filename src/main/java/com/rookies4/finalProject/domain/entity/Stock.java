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
    @Column(name = "stock_code", nullable = false, unique = true, length = 20)
    private String stockCode; // 종목코드 (예: 005930)

    @Column(name = "name", nullable = false, length = 100)
    private String name; // 종목명 (예: 삼성전자)

    @Column(name = "market_type", length = 20)
    private String marketType; // KOSPI, KOSDAQ, KONEX

    @Column(name = "sector", length = 50)
    private String sector; // 업종명

    @Column(name = "industry_code", length = 20)
    private String industryCode; // 업종코드

    @Column(name = "corp_code", length = 8, unique = true)
    private String corpCode; // 법인고유번호 (DART용 8자리)

    // 보유 내역
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Holding> holdings = new ArrayList<>();

    // 거래 내역
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    // AI 추천 내역
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AIRecommendation> recommendations = new ArrayList<>();

    // 피처 데이터
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockFeatures> features = new ArrayList<>();

    // 이 종목이 영향을 주는 관계들 (from_stock)
    @OneToMany(mappedBy = "fromStock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockRelation> outgoingRelations = new ArrayList<>();

    // 이 종목이 영향을 받는 관계들 (to_stock)
    @OneToMany(mappedBy = "toStock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockRelation> incomingRelations = new ArrayList<>();

    // 이 종목을 관심 종목으로 등록한 사용자 목록
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InterestStock> interestedUsers = new ArrayList<>();

    // 연관관계 편의 메서드
    public void addOutgoingRelation(StockRelation relation) {
        outgoingRelations.add(relation);
        relation.setFromStock(this);
    }

    public void addIncomingRelation(StockRelation relation) {
        incomingRelations.add(relation);
        relation.setToStock(this);
    }

    // 종목이 몇 명의 관심 종목으로 등록되었는지 계산
    public int getInterestCount() {
        return interestedUsers != null ? interestedUsers.size() : 0;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "stockCode='" + stockCode + '\'' +
                ", name='" + name + '\'' +
                ", marketType='" + marketType + '\'' +
                ", sector='" + sector + '\'' +
                '}';
    }
}