# 🥚 나의 계란바구니(My EggBasket) - Backend
> SKRookies 1팀(DEVBUG)의 소중한 데이터를 담는 백엔드 저장소입니다.  
> 사용자의 주식을 '계란'으로 비유하여 바구니(포트폴리오)에서 관리하는 서비스입니다.

---

## 🚀 프로젝트 개요
- **프로젝트 명:** 나의 계란바구니MyEggBasket
- **목적:** AI 리밸런싱을 이용한 주식 포트폴리오 관리 서비스
- **개발 기간:** 2025.11.20 ~ 2026.01.09 (7주)

---

## 🛠 Tech Stack
### Framework & Language
- ![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white) **Java 17**
- ![SpringBoot](https://img.shields.io/badge/springboot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white) **Spring Boot 3.x**
- ![Maven](https://img.shields.io/badge/apache_maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white) **Maven**

### Persistence & Database
- ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge&logo=Spring&logoColor=white)
- ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white) **PostgreSQL**

### Infrastructure & Ops
- ![AWS](https://img.shields.io/badge/AWS-%23FF9900.svg?style=for-the-badge&logo=amazon-aws&logoColor=white)
- ![Kubernetes](https://img.shields.io/badge/kubernetes-%23326ce5.svg?style=for-the-badge&logo=kubernetes&logoColor=white)
- ![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
- ![GitHub Actions](https://img.shields.io/badge/github%20actions-%232088FF.svg?style=for-the-badge&logo=githubactions&logoColor=white)

---

## 🏗 System Architecture

### ☁️ AWS Architecture
> ![AWS Architecture](여기에_AWS_이미지_경로를_넣으세요)
- 클라우드 기반의 안정적인 인프라 환경 구축

### ☸️ Kubernetes Architecture
> ![K8s Architecture](여기에_K8s_이미지_경로를_넣으세요)
- 컨테이너 오케스트레이션을 통한 효율적인 리소스 관리 및 배포 자동화

---

## ✨ 주요 기능
1. **계란바구니 관리:** 원하는 투자 성향을 선택하여 바구니(포트폴리오) 생성 및 관리
2. **목표가 달성 알림:** 사용자 목표가 설정 후 목표 체결가 도달 시 알림 배너 표시
3. **통계 데이터 제공:** 포트폴리오 별 종목에 대한 보유 종목 통계 데이터 표시

---

## ⚙️ 시작하기 (Getting Started)

### Requirements
- JDK 17
- PostgreSQL 18 (PostgreSQL 설치 및 설정 방법 : https://yeoleum123.tistory.com/15)

### Installation & Run
```bash
# 레포지토리 클론
git clone [https://github.com/skRookies1team/MyEggBasket-BE.git](https://github.com/skRookies1team/MyEggBasket-BE.git)

# 빌드 (Maven)
mvn clean install

# 실행
java -jar target/MyEggBasket-0.0.1-SNAPSHOT.jar