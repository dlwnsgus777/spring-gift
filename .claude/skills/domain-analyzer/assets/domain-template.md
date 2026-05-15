# {{DOMAIN_TITLE}} 도메인 분석

> 작성일: {{DATE}}  
> 분석 대상 브랜치: {{BRANCH}}

---

## 1. 파일 구성

```
{{FILE_TREE}}
```

{{TEST_FILES}}

---

## 2. 비즈니스 로직

{{DOMAIN_ROLE_SUMMARY}}

{{OPERATIONS}}

---

## 3. 데이터 모델

### DB 스키마 ({{SCHEMA_FILE}})

```sql
{{DDL}}
```

{{DEFAULT_DATA_SECTION}}

---

## 4. 도메인 객체

```java
{{ENTITY_CODE}}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
{{PUBLIC_API_ROWS}}

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
{{API_ROWS}}

{{AUTH_DESCRIPTION}}

---

## 6. DTO 설계

{{DTO_SECTIONS}}

---

## 7. 다른 도메인과의 관계

```
{{RELATION_DIAGRAM}}
```

{{RELATION_DESCRIPTION}}
