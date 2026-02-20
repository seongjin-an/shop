```text
src/
 ├── app/                 # App Router
 │    ├── layout.tsx
 │    ├── page.tsx
 │    └── (auth)/
 │
 ├── features/            # 도메인 단위 구조 (추천)
 │    ├── user/
 │    ├── article/
 │    └── notification/
 │
 ├── shared/
 │    ├── components/
 │    ├── hooks/
 │    ├── lib/
 │    └── utils/
 │
 ├── services/            # API 호출 모음
 ├── store/               # Zustand / Redux
 └── types/
```

## ✅ 5️⃣ API 호출 베스트 프랙티스
- ❌ 절대 이렇게 하지 말 것
  - `fetch("http://localhost:8080/api/...")`
- ✅ 이렇게
- `const API_BASE = process.env.NEXT_PUBLIC_API_URL`
- `.env`
  - `NEXT_PUBLIC_API_URL=https://api.myservice.com`
```text
###### .env 파일
# 가장 우선순위가 낮다. 모든 환경에서 공통으로 사용할 디폴트 키를 관리한다.
NEXT_PUBLIC_ANALYTICS_ID=default_analytics_id
NEXT_PUBLIC_API_KEY=default_api_key
NODE_VALUE=default_value  


###### .env.development 파일
# 개발환경(process.env.NODE_ENV === 'development')에서 사용할 키를 등록한다. 
# 개발환경일 경우, .env에 같은 환경변수가 있다면 덮어쓴다.
NEXT_PUBLIC_API_KEY=dev_api_key
NODE_VALUE=dev_value


###### .env.production 파일
# 배포/빌드환경(process.env.NODE_ENV === 'production')에서 사용할 키를 등록한다. 
# 배포환경일 경우, .env에 같은 환경변수가 있다면 덮어쓴다.
NEXT_PUBLIC_API_KEY=prod_api_key
NODE_VALUE=prod_value


###### .env.local 파일
# 모든 환경에서 최우선순위로 적용할 환경변수를 정의한다.
# 모든 .env.* 파일보다 우선순위가 높다.(같은 환경변수가 있다면 모두 덮어쓴다.)
NEXT_PUBLIC_API_KEY=local_api_key
NODE_VALUE=local_value

접두사 : NEXT_PUBLIC_ : 브라우저 참조용

기본적으로 환경 변수는 서버에만 참조할 수 있습니다.

서버와 브라우저 모두에서 환경 변수를 사용하려면 환경 변수명 앞에 NEXT_PUBLIC_ 을 붙여줘야 합니다.


```