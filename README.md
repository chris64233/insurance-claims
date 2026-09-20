# 保险理赔报案登记

基于 Spring Boot 4 + Spring Data JPA（H2 内存库）与 Vue 3 + Vite 的保险理赔报案登记示例，支持报案创建、重复报案拦截、报案列表查询。

## 功能说明

- 报案字段：保单号、被保险人姓名、联系方式、事故类型、事故日期、申请金额、事故说明。
- 服务端创建成功后自动生成：唯一理赔编号（`CLM + 8 位日期 + 8 位随机串`）、创建时间、初始状态 `待受理`，客户端传入的这些字段一律忽略。
- 参数校验：全部必填项不能为空；事故日期不能晚于当前日期；申请金额必须大于 0；校验失败返回字段级错误信息。
- 重复报案判定：保单号、事故日期、事故类型三者完全相同，返回 `409` 与明确提示；数据库层另有唯一约束兜底并发场景。
- 前端覆盖提交中、列表加载中、空数据、请求失败（可重试）等状态，提交成功后展示理赔编号并自动刷新列表。

## 目录结构

```
src/main/java/com/example/insuranceclaims/
  claim/            # 报案领域：实体、仓库、服务、异常、DTO、控制器
  common/           # 统一错误响应与全局异常处理
frontend/src/
  api/              # 接口封装与错误处理
  components/       # ClaimForm、ClaimList 组件
  utils/            # 前端校验与格式化
```

## 后端运行

要求 JDK 21。

```bash
./mvnw spring-boot:run        # 默认端口 8080
./mvnw test                   # 运行后端测试
```

## 前端运行

要求 Node.js 18+。

```bash
cd frontend
npm install
npm run dev                   # 默认 http://localhost:5173，/api 代理到 http://localhost:8080
npm test                      # 运行前端单元/组件测试
npm run build                 # 生产构建
```

> 如 8080 被占用，可通过 `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` 更换后端端口，并同步修改 `frontend/vite.config.js` 中的代理地址。

## HTTP 接口

### 创建报案 `POST /api/claims`

请求体：

```json
{
  "policyNumber": "P20260001",
  "insuredName": "张三",
  "contactInfo": "13800138000",
  "accidentType": "交通事故",
  "accidentDate": "2026-09-15",
  "claimAmount": 5000.00,
  "description": "雨天追尾"
}
```

- 成功返回 `201`，响应包含服务端生成的 `claimNo`、`status`、`createdAt`。
- 参数错误返回 `400`：

```json
{
  "status": 400,
  "message": "申请金额必须大于 0",
  "fieldErrors": { "claimAmount": "申请金额必须大于 0" },
  "timestamp": "2026-09-20T10:00:00"
}
```

- 重复报案返回 `409`：`{"status":409,"message":"该保单项下当日已存在相同事故类型的报案，请勿重复报案",...}`

### 查询报案列表 `GET /api/claims`

返回按创建时间倒序排列的报案数组，空数据时返回 `[]`。

## 测试覆盖

- 后端（9 个用例）：创建成功且忽略客户端伪造字段、必填校验、未来日期、非正金额、重复报案 409、不同事故类型不算重复、列表倒序、空列表、应用上下文加载。
- 前端（22 个用例）：前端校验规则、API 封装（成功/网络错误/400 字段错误/409 重复）、表单组件（校验提示、提交中文案、服务端字段错误、重置）、列表组件（加载中/空数据/失败重试/数据渲染）。
