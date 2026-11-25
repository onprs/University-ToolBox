# 超星学习通官方 HTTP 通信协议规范

> 本文档依据对超星学习通官方 HTTP 服务与官方前端静态资源的客观实测探测整理而成，仅描述公开协议端点、加密参数、请求格式、响应 Schema、HTML 结构要素与状态/错误码枚举。文中所有样例字段值均已替换为脱敏占位符，不含任何真实账号、凭据、会话 Cookie、机构或课程业务数据。

---

## 1. 通用协议约定与域名体系

### 1.1 官方核心服务域名

以下域名提取自超星官方入口页面与学习活动动态域名脚本（`GET https://mobilelearn.chaoxing.com/service-domain/serviceDomainScript?DB_STRATEGY=RANDOM`）：

| 服务用途 | 官方域名 | 协议 |
| :--- | :--- | :--- |
| 统一认证与账号中心 | `passport2.chaoxing.com` / `passport2-api.chaoxing.com` | HTTPS |
| 单点登录与会话信息 | `sso.chaoxing.com` | HTTPS |
| 行为验证码服务 | `captcha.chaoxing.com` | HTTPS |
| 个人空间首页 | `i.chaoxing.com` / `i.mooc.chaoxing.com` | HTTPS |
| 课程列表（移动端 JSON API） | `mooc1-api.chaoxing.com` | HTTPS |
| 课程列表（新版 Web 端） | `mooc2-ans.chaoxing.com` / `mooc1.chaoxing.com` | HTTPS |
| 学习活动与签到核心服务 | `mobilelearn.chaoxing.com` | HTTPS |
| 学习活动静态资源 | `mobilelearn-static.chaoxing.com` | HTTPS |
| 微信端签到跳转域 | `mobilewx.chaoxing.com` | HTTPS |
| 云盘存储与图片上传 | `pan-yz.chaoxing.com` / `pan-static.chaoxing.com` | HTTPS |
| 用户头像静态服务 | `photo.chaoxing.com` | HTTPS |
| 课件/签到图片静态域 | `p.ananas.chaoxing.com` / `p.cldisk.com` | HTTPS |

> **多域名后缀兼容**：官方前端 `commonUtil.js` 声明了三组等价域名后缀：`[".chaoxing.com", ".cxwfw.com", ".xuexitong.com"]`。

### 1.2 通用请求头与客户端标识

- **移动端 Web / H5 请求**：建议使用标准移动端 Android Chrome `User-Agent`；AJAX 请求附带 `X-Requested-With: XMLHttpRequest` 及对应来源 `Referer` / `Origin`。
- **官方客户端 `User-Agent` 格式**（提取自 `passport2-static.chaoxing.com/js/common/commonUtil.js` 正则表达式）：
  ```text
  .../ChaoXingStudy_<productId>_<clientVersion>_<clientType>_<arch>_<build>_<apiVersion> (<deviceInfo>)_<deviceNum>...
  ```
  - 示例匹配结构：`productId`（数字，如 `3`）、`clientVersion`（如 `6.x.x`）、`clientType`（如 `android` / `ios` / `windows` / `mac`）、`apiVersion`（整数）、可选 `apiHbVersion_<num>`、`deviceNum`。
- **`Content-Type` 兼容注意**：超星多个核心 JSON 接口（如 `/fanyalogin`、`/mycourse/backclazzdata`、`/apis/login/userLogin4Uname.do`、`/mooc/getAccountMessage`、`/api/token/uservalid`）实际返回的 HTTP `Content-Type` 为 `text/html;charset=utf-8` 或 `text/html;charset=UTF-8`，而非 `application/json`。客户端 HTTP 反序列化层不得仅依赖 `Content-Type` 判断 JSON。

---

## 2. 登录与会话认证协议

### 2.1 登录表单加密算法（`encryptByAES`）

在 `https://passport2.chaoxing.com/login`（桌面端）及 `/mlogin`（移动端）官方登录脚本（`login.js`）中，当表单字段 `t == "true"` 时，账号与密码（及短信验证码登录时的验证码）使用标准 **AES-128-CBC** 加密：

- **算法**：`AES/CBC/PKCS5Padding`（即前端 `CryptoJS` 中的 `AES-CBC + PKCS7` 填充，密钥长度 16 字节对应 AES-128）
- **密钥（Key）**：UTF-8 编码的 16 字节固定传输密钥 `u2oh6Vu^HWe4_AES`
- **初始向量（IV）**：与 Key 相同，UTF-8 编码 `u2oh6Vu^HWe4_AES`（16 字节）
- **明文编码**：UTF-8
- **密文输出**：标准 Base64 编码字符串（`uname` 与 `password` 在 AJAX `application/x-www-form-urlencoded` 提交时由 jQuery 自动做表单 URL 编码；`fanyaloginbycode` 接口中的 `verCode` 字段在 `login.js` 中先做了一次 `encodeURIComponent(...)`）

### 2.2 账号密码登录：`POST https://passport2.chaoxing.com/fanyalogin`

- **前置步骤（可选）**：`GET https://passport2.chaoxing.com/mlogin`（或 `/login`）可获取初始会话 Cookie（`JSESSIONID`、`route`，作用域 `passport2.chaoxing.com`，`Path=/`）及页面隐藏表单字段默认值。
- **请求方法**：`POST`
- **请求头**：
  - `Content-Type: application/x-www-form-urlencoded; charset=UTF-8`
  - `Referer: https://passport2.chaoxing.com/mlogin`
  - `Origin: https://passport2.chaoxing.com`
  - `X-Requested-With: XMLHttpRequest`
- **请求表单字段（Form Data）**：

| 字段名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `fid` | String | 是 | 机构/学校 ID；手机号通用登录默认传 `"-1"` |
| `uname` | String | 是 | 手机号/超星号/邮箱；`t="true"` 时为 AES-CBC Base64 密文 |
| `password` | String | 是 | 登录密码；`t="true"` 时为 AES-CBC Base64 密文 |
| `refer` | String | 否 | 登录成功回跳地址，默认 `"http%3A%2F%2Fi.mooc.chaoxing.com"` |
| `t` | String | 是 | `"true"` 表示启用 `AES-128-CBC` 传输加密 |
| `forbidotherlogin` | String | 否 | 默认 `"0"` |
| `doubleFactorLogin` | String | 否 | 双因子登录开关标识，默认 `"0"` |
| `independentId` | String | 否 | 独立机构标识，默认 `"0"` |
| `independentNameId` | String | 否 | 独立机构名称标识，默认 `"0"` |
| `validate` | String | 条件 | 当服务端触发行为验证码（`needVcode="1"`）时，传入验证码校验凭据，否则为空字符串 |

- **成功响应（HTTP `200`，`Content-Type: text/html;charset=utf-8`）**：
  ```json
  {
    "url": "http%3A%2F%2Fi.mooc.chaoxing.com",
    "status": true
  }
  ```
  - 若触发双因子认证（按官方 `login.js` 分支）：响应额外包含 `containTwoFactorLogin: true`、`twoFactorLoginUrl`（移动端）或 `twoFactorLoginPCUrl`（桌面端），需跳转并提交 `/v11/checkPassword` 或 `/v11/checkmessage`。
- **失败响应样例（HTTP `200`）**：
  - 参数为空：`{"msg2":"参数为空","status":false}`
  - 账号或密码错误 / 密文格式错误：`{"msg2":"用户名或密码错误","status":false}`
  - 弱密码或密码过期强制修改（按官方 `login.js` 分支）：`{"status":false,"weakpwd":true,"uid":"<REDACTED_UID>","passwordTimeout":1}`，前端需跳转 `/v11/updateweakpwd`。

### 2.3 登录成功下发的核心认证 Cookie

实测 `POST /fanyalogin` 成功响应通过 `Set-Cookie` 在 `Domain=.chaoxing.com; Path=/` 下发以下 Cookie（均用于后续跨域业务请求）：

| Cookie 名称 | 作用域 | 属性特征 | 用途说明 |
| :--- | :--- | :--- | :--- |
| `UID` | `.chaoxing.com` | `Path=/` | 用户数字 ID（核心身份标识） |
| `_uid` | `.chaoxing.com` | `Path=/` | 用户数字 ID（与 `UID` 一致） |
| `fid` | `.chaoxing.com` | `Path=/` | 所属机构/学校 ID |
| `_d` | `.chaoxing.com` | `Path=/` | 会话时间戳/状态凭证 |
| `vc3` | `.chaoxing.com` | `Path=/; HttpOnly` | 核心认证令牌 |
| `uf` | `.chaoxing.com` | `Path=/; HttpOnly` | 用户签名凭证 |
| `cx_p_token` | `.chaoxing.com` | `Path=/` | 通行证 Token |
| `p_auth_token` | `.chaoxing.com` | `Path=/` | 接口鉴权 Token（云盘等接口同时支持 `p-auth-token` 请求头） |
| `xxtenc` | `.chaoxing.com` | `Path=/` | 扩展校验串 |
| `DSSTASH_LOG` | `.chaoxing.com` | `Path=/` | 会话跟踪标识 |

> **关键保存集合**：至少必须完整持久化并携带 `UID`、`_uid`、`fid`、`_d`、`vc3`、`uf`（推荐保留 `.chaoxing.com` 域下全部登录响应 Cookie，以满足 `mooc1-api`、`mooc2-ans`、`mobilelearn`、`sso`、`pan-yz` 等全子域鉴权）。

### 2.4 其他官方登录方式与行为验证码协议

1. **手机号 + 短信验证码登录**：`POST https://passport2.chaoxing.com/fanyaloginbycode`
   - 表单字段：`fid`, `uname`（明文手机号）, `verCode`（`encodeURIComponent(encryptByAES(vercode, "u2oh6Vu^HWe4_AES"))`）, `refer`, `doubleFactorLogin`, `independentNameId`, `countrycode`（如 `"86"`）。
   - 失败未注册分支：`status=false, type=1` 时返回 `url` / `quickUrl` 跳转密码设置流程；其他错误读取 `msg2`。
2. **机构学号/工号登录**：`POST https://passport2.chaoxing.com/unitlogin`
   - 表单字段：`pid`, `fid`, `uname`（AES 加密）, `password`（AES 加密）, `numcode`（移动端图形验证码）, `validate`（行为验证码）, `refer`, `t`, `hidecompletephone`, `doubleFactorLogin`, `forbidotherlogin`, `independentId`, `independentNameId`。
   - 响应使用 `data.status`、`data.type`（`0` 成功、`1` 失败刷新 `/num/code`、`2` 需完善信息）及 `data.mes`。
3. **桌面端扫码登录**：
   - 初始化页面 `GET https://passport2.chaoxing.com/login` 隐藏域提供 `uuid` 与 `enc`；
   - 获取二维码图片：`GET https://passport2.chaoxing.com/createqr?uuid=<UUID>&fid=-1`（返回 `200 image/jpeg`）；
   - 刷新二维码：`POST https://passport2.chaoxing.com/refreshQRCode`（返回新 `uuid` 与 `enc`）；
   - 轮询扫码状态（前端间隔 `3000ms`）：`POST https://passport2.chaoxing.com/getauthstatus/v2`
     - 表单字段：`enc`, `uuid`, `doubleFactorLogin`, `forbidotherlogin`。
     - 实测未扫码响应：`{"mes":"未登录","type":"3","status":false}`。
     - 前端状态机（`login.js` 内联脚本）：`status=true` 登录成功（并下发认证 Cookie）；`type="4"` 已扫码待确认（返回 `nickname`, `uid`）；`type="6"` 客户端取消；`type="7"` 错误信息 `mes`；`type="2"` 二维码或会话失效。
4. **行为验证码协议（`captcha.chaoxing.com`）**：
   - 登录页在 `needVcode == "1"` 时加载 `https://captcha.chaoxing.com/load.min.js`，并使用页面配置 `captchaId`（桌面端登录页公开固定值 `GcXX5vewqE7DezKGlyvleKCnkTglvGpL`）。
   - 获取配置（JSONP）：`GET https://captcha.chaoxing.com/captcha/get/conf?captchaId=<CAPTCHA_ID>&callback=<CALLBACK>`
     - 实测返回：`cx_cb({"t":<TIMESTAMP_MS>,"captchaId":"GcXX5vewqE7DezKGlyvleKCnkTglvGpL"})`（若缺 `callback` 参数返回 `{"10013":"CALLBACK ERROR"}`）。
   - 获取验证图片（JSONP）：`GET https://captcha.chaoxing.com/captcha/get/verification/image?...`（受签名校验参数 `r` 保护；缺省或校验失败返回 `{"10030":"r verify error"}`）。
   - 校验结果：`GET https://captcha.chaoxing.com/captcha/check/verification/result?...`，成功后生成 `validate` 字段回填登录/业务请求。

---

## 3. 用户信息、头像与学校检索接口

### 3.1 单点登录用户信息（推荐主接口）

- **Endpoint**：`GET https://sso.chaoxing.com/apis/login/userLogin4Uname.do`
- **鉴权**：携带 `.chaoxing.com` 登录态 Cookie。
- **响应格式**：HTTP `200`，`Content-Type: text/html;charset=UTF-8`。
- **响应 JSON Schema（已脱敏）**：
  ```json
  {
    "result": 1,
    "msg": {
      "uid": 0,
      "puid": 0,
      "fid": 0,
      "dxfid": "<REDACTED_DXFID>",
      "ppfid": "<REDACTED_PPFID>",
      "name": "<REDACTED_REAL_NAME>",
      "nick": "<REDACTED_NICKNAME>",
      "fullpinyin": "<REDACTED_PINYIN>",
      "simplepinyin": "<REDACTED_INITIALS>",
      "sex": 0,
      "phone": "<REDACTED_PHONE>",
      "uname": "<REDACTED_UNAME>",
      "studentcode": "<REDACTED_STUDENT_CODE>",
      "schoolname": "<REDACTED_SCHOOL_NAME>",
      "pic": "https://photo.chaoxing.com/p/<REDACTED_UID>_80",
      "isCertify": 0,
      "roleid": "3",
      "status": 1,
      "unitConfigInfos": [
        {
          "fid": 0,
          "dxfid": "<REDACTED_DXFID>",
          "uname": "<REDACTED_UNAME>",
          "schoolname": "<REDACTED_SCHOOL_NAME>",
          "status": "1",
          "xb": 0,
          "idcardFlag": false,
          "hpConfig": {
            "showBaseHp": 1,
            "showMicroServiceHp": 1
          }
        }
      ],
      "accountInfo": {
        "cx_fanya": {
          "uid": 0,
          "cxid": 0,
          "schoolid": 0,
          "roleid": "3",
          "countrycode": "86",
          "isCertify": 0,
          "status": 1
        }
      }
    }
  }
  ```
- **未登录 / 会话失效响应（实测）**：
  - 无 Cookie：`{"result":0,"errorMsg":"非法请求,请重新登录(>_<)"}`
  - Cookie 失效或伪造：`{"result":0,"errorMsg":"非法请求2,请重新登录(>_<)"}`

### 3.2 账号管理详情接口

- **Endpoint**：`GET https://passport2.chaoxing.com/mooc/getAccountMessage`
- **鉴权**：携带 `.chaoxing.com` 登录态 Cookie。
- **响应格式**：HTTP `200`，`Content-Type: text/html;charset=utf-8`。
- **响应 JSON Schema（已脱敏）**：
  ```json
  {
    "status": true,
    "message": {
      "uid": 0,
      "fid": 0,
      "realname": "<REDACTED_REAL_NAME>",
      "phone": "<REDACTED_PHONE>",
      "email": "<REDACTED_EMAIL>",
      "sex": 0,
      "studentCode": "<REDACTED_STUDENT_CODE>",
      "isCertify": 0,
      "trustuname": "<REDACTED_UNAME>",
      "trustunitname": "<REDACTED_SCHOOL_NAME>",
      "fidListSize": 1,
      "fidList": [
        {
          "id": 0,
          "fid": 0,
          "uname": "<REDACTED_UNAME>",
          "originalUname": "<REDACTED_UNAME>",
          "unitname": "<REDACTED_SCHOOL_NAME>"
        }
      ],
      "deletedloginInfoList": []
    }
  }
  ```
- **未登录响应（实测）**：`{"status":false}`。

### 3.3 用户头像 URL 规范

- 实测来源于 `i.chaoxing.com`、`userLogin4Uname.do`、`accountManage.js` 及 `newsign/preSign`：
  - `https://photo.chaoxing.com/p/<UID>_30`（30×30 小图）
  - `https://photo.chaoxing.com/p/<UID>_80`（80×80 中图）
  - `https://photo.chaoxing.com/p/<UID>_160`（160×160 大图）

### 3.4 学校 / 机构检索接口

- **按名称搜索学校**：`GET https://passport2.chaoxing.com/org/searchUnis?filter=<KEYWORD>&product=44&type=`
  - 响应（`200 text/html;charset=utf-8`）：
    ```json
    {
      "result": true,
      "fromNums": 1,
      "froms": [
        {
          "schoolid": 0,
          "_id": "<EXAMPLE_FID>",
          "name": "<EXAMPLE_SCHOOL_NAME>",
          "pid": 0,
          "dxfid": 0,
          "ishidden": 0,
          "allowJoin": 0,
          "productId": [44, 48]
        }
      ]
    }
    ```
- **按 `schoolid` 反查学校名**：`POST https://passport2.chaoxing.com/org/getName?schoolid=<FID>`（返回 `status`, `schoolid`, `name`）。

---

## 4. 课程列表接口

### 4.1 移动端 JSON 课程列表接口（推荐）

- **Endpoint**：`GET https://mooc1-api.chaoxing.com/mycourse/backclazzdata`
- **Query 参数**：
  - `view=json`
  - `rss=1`
- **鉴权**：携带 `.chaoxing.com` 登录态 Cookie。
- **响应格式**：HTTP `200`，`Content-Type: text/html;charset=UTF-8`。
- **响应 JSON Schema（已脱敏）**：
  ```json
  {
    "result": 1,
    "msg": "获取成功",
    "mcode": "<REDACTED_MCODE>",
    "createcourse": 0,
    "teacherEndCourse": 0,
    "stuEndCourse": 0,
    "showEndCourse": 0,
    "hasMore": false,
    "channelList": [
      {
        "id": 0,
        "cfid": 0,
        "norder": 0,
        "cataid": "<REDACTED_CATA_ID>",
        "cataName": "课程",
        "key": 100000001,
        "cpi": 100000002,
        "topsign": 0,
        "content": {
          "id": 100000001,
          "cpi": 100000002,
          "name": "<EXAMPLE_CLASS_NAME>",
          "roletype": 3,
          "state": 0,
          "isFiled": 0,
          "isretire": 0,
          "isstart": true,
          "isthirdaq": 0,
          "isSquare": 0,
          "studentcount": 60,
          "beginDate": "2026-09-01 00:00",
          "endDate": "2027-01-31 23:59",
          "chatid": "<REDACTED_CHAT_ID>",
          "bbsid": "<REDACTED_BBS_ID>",
          "course": {
            "data": [
              {
                "id": 200000001,
                "name": "<EXAMPLE_COURSE_NAME>",
                "teacherfactor": "<REDACTED_TEACHER_NAME>",
                "imageurl": "https://p.ananas.chaoxing.com/star4/<REDACTED_HASH>/240_130c.jpg",
                "belongSchoolId": "<EXAMPLE_FID>",
                "schools": "",
                "coursestate": 0,
                "isCourseSquare": 0,
                "courseSquareUrl": "<EXAMPLE_SQUARE_URL>",
                "createtime": 1700000000000,
                "defaultShowCatalog": 1,
                "sectionId": 0,
                "smartCourseState": 0,
                "appData": 0,
                "appInfo": ""
              }
            ]
          }
        }
      }
    ]
  }
  ```
- **核心字段提取规则**：
  - `cataName`: 仅处理 `"课程"`（过滤非课程通道及无 `content.course.data` 的记录）。
  - `courseId`: `channel.content.course.data[0].id`
  - `clazzId`（班级 ID，部分活动接口称 `classId`）: `channel.content.id`（通常与 `channel.key` 一致）
  - `cpi`（课程个人标识 `curPersonId`）: `channel.cpi`（或 `channel.content.cpi`）
  - `roletype`: 角色标识（实测学生选修课程为 `3`）
  - `state` / `isFiled`: 课程状态（`0` 正常，`1` 结课/归档等；`isFiled=1` 表示已归档）
- **未登录 / 会话失效响应（实测）**：
  ```json
  {"result":0,"msg":"请重新登录","status":false,"errorMsg":"请重新登录"}
  ```

### 4.2 新版 Web 端 HTML 课程列表接口

- **入口页面**：`GET https://mooc2-ans.chaoxing.com/visit/interaction`
- **课程卡片数据接口**：`GET https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata`
  - Query 参数：`courseType=1`、`courseFolderId=0`、`query=`、`superstarClass=0`
  - 响应类型：HTTP `200`，`Content-Type: text/html;charset=UTF-8`（HTML 片段）
- **关键 HTML 结构与解析选择器（实测）**：
  - 课程卡片容器：`div.course.clearfix`（`#stuTopCourseListDiv` 为置顶课程，`#stuNormalCourseListDiv` 为普通课程），属性 `info="<CLAZZ_ID>_<CPI>"`、`id="c_<COURSE_ID>"`。
  - 隐藏表单域（位于 `.course-cover` 内）：
    - `input.clazzId[name="clazzId"]` -> `clazzId`
    - `input.courseId[name="courseId"]` -> `courseId`
    - `input.curPersonId` -> `cpi`
    - `input.role[name="role"]` -> 角色（学生课为 `"0"`）
  - 课程跳转链接：`a[href*="stucoursemiddle"]`（包含 `courseid=<COURSE_ID>&clazzid=<CLAZZ_ID>&cpi=<CPI>&ismooc2=1&v=2`）
  - 封面图：`.course-cover img[src]`
  - 课程名称：`span.course-name`
  - 教师/单位信息：`p.line2.color3`（以及 `p.margint10.line2.color2`）

---

## 5. 活动与签到列表接口

### 5.1 移动端活动列表 API（推荐）

- **Endpoint**：`GET https://mobilelearn.chaoxing.com/v2/apis/active/student/activelist`
- **Query 参数**：

| 参数名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `fid` | String/Int | 推荐 | 机构/学校 ID（可来自 `fid` Cookie 或 `"0"`） |
| `courseId` | Long/String | 是 | 课程 ID |
| `classId` | Long/String | 是 | 班级 ID（即课程列表 `clazzId`） |
| `cpi` | Long/String | 是 | 课程个人标识 `cpi` |
| `showNotStartedActive` | Int | 否 | 是否返回未开始活动（实测 `0`） |

- **响应格式**：HTTP `200`，`Content-Type: application/json;charset=UTF-8`。
- **响应 JSON Schema（已脱敏）**：
  ```json
  {
    "result": 1,
    "msg": null,
    "errorMsg": null,
    "data": {
      "ext": {},
      "readingDuration": 0,
      "activeList": [
        {
          "id": 7000000000001,
          "activeType": 2,
          "type": 2,
          "otherId": "4",
          "status": 2,
          "userStatus": 1,
          "nameOne": "<EXAMPLE_SIGN_TITLE>",
          "nameTwo": "",
          "nameFour": "11-24 10:12",
          "startTime": 1700000000000,
          "endTime": 1700000600000,
          "groupId": 0,
          "source": 15,
          "isLook": 1,
          "releaseNum": 60,
          "attendNum": 58,
          "logo": "https://mobilelearn-static.chaoxing.com/...",
          "flagLogo": false
        }
      ]
    }
  }
  ```
- **关键字段说明与实测枚举**：
  - `activeType`（与 `type` 一致）活动类型枚举（结合实测响应与官方 `util2.js`）：
    - `2`：签到
    - `74`：普通签到-签退（签退活动）
    - 其他课程活动：`4` 抢答、`5` 讨论、`11` 选人、`14` 问卷、`17` 直播、`23` 评分、`27` 在线课堂、`35` 分组任务、`42` 随堂练习、`43` 投票、`45` 通知、`46` 课堂笔记/通知、`51` 同步课堂、`57` PPT、`58` 教案、`64` 直播、`77` 主题讨论/思政活动
  - `otherId`（当 `activeType == 2` 或 `74` 时的签到子类型，注意在 `activelist` 中为**字符串**如 `"0"`、`"2"`、`"3"`、`"4"`、`"5"`，而在 `getPPTActiveInfo` 中为**整数**）：
    - `"0"`：普通签到 / 拍照签到（需结合 `getPPTActiveInfo` 中 `ifphoto` 判断是否要求拍照）
    - `"2"`：二维码签到
    - `"3"`：手势签到
    - `"4"`：位置签到
    - `"5"`：签到码签到
  - `status`（活动状态）：
    - `1`：进行中
    - `2`：已结束
    - `0`：未开始（配合 `showNotStartedActive`）
  - `userStatus`（学生个人参与/签到状态，来源：`pcpick/stu/index` 模板及 `util2.js`）：
    - `0`：未签
    - `1`：已签
    - `2`：代签（教师代签）
    - `4`：请假
    - `5`：缺勤（部分学校展示为旷课）
    - `7`：病假
    - `8`：事假
    - `9`：迟到
    - `10`：早退
    - `11`：签到已过期
    - `12`：公假（见 `pc/util.js` `dealSignStatus` 与 `newsign/preSign` 颜色分支）
  - `startTime` / `endTime`：13 位毫秒级 Unix 时间戳；`nameOne` 为活动标题；`nameFour` 为展示时间串（如 `"11-24 10:12"`）。
- **未登录 / 会话失效响应（实测）**：
  - HTTP `302` 重定向至 `https://passport2.chaoxing.com/mlogin?newversion=true&refer=...`（HTTP 客户端若自动跟随重定向会得到 HTML 登录页而非 JSON，建议禁用自动重定向或识别重定向目标为 `passport2.chaoxing.com`）。

### 5.2 PC Web 端活动列表接口（备选）

- **页面入口**：`GET https://mobilelearn.chaoxing.com/widget/pcpick/stu/index?courseId=<COURSE_ID>&jclassId=<CLAZZ_ID>`
- **数据接口**：`POST https://mobilelearn.chaoxing.com/widget/pcpick/stu/activeList`
  - Form 参数：`courseId`、`puid`（即 `UID`）、`classId`
  - 响应（`200 application/json;charset=UTF-8`）：`{"result":1,"msg":"获取成功","data":{"startCount":0,"startList":[...],"endCount":5,"endList":[...]}}`

---

## 6. 签到详情、预加载与提交协议

### 6.1 获取签到详情：`GET https://mobilelearn.chaoxing.com/v2/apis/active/getPPTActiveInfo`

> **实测注意**：`v2/apis/active/getActiveInfo` 与 `v2/apis/sign/getActiveInfo` 与 `v2/apis/sign/getSignInfo` 在服务端均返回 HTTP `500`；学生端获取签到配置应使用 **`/v2/apis/active/getPPTActiveInfo`**。

- **Query 参数**：`activeId=<ACTIVE_ID>`（缺少参数或 `activeId` 无效返回 HTTP `500`）。
- **响应格式**：HTTP `200`，`Content-Type: application/json;charset=UTF-8`。
- **响应 JSON 关键字段 Schema（已脱敏）**：
  ```json
  {
    "result": 1,
    "msg": "success",
    "errorMsg": null,
    "data": {
      "id": 7000000000001,
      "clazzid": 100000001,
      "name": "<EXAMPLE_SIGN_TITLE>",
      "activeType": 2,
      "otherId": 4,
      "status": 2,
      "source": 15,
      "ifphoto": 0,
      "ifopenAddress": 1,
      "ifNeedVCode": 0,
      "showVCode": 0,
      "ifrefreshewm": 0,
      "ewmRefreshTime": 0,
      "openPreventCheatFlag": 0,
      "openCheckFaceFlag": 0,
      "openCheckWeChatFlag": 0,
      "openSignOutFlag": 0,
      "signInId": null,
      "signOutDuration": null,
      "signOutAbsoluteTime": null,
      "timer": null,
      "signCode": "",
      "locationText": "<REDACTED_LOCATION_TEXT>",
      "locationRange": "100",
      "latitude": 0.0,
      "longitude": 0.0,
      "locationLatitude": "",
      "locationLongitude": "",
      "locationLatitude_gd": "",
      "locationLongitude_gd": "",
      "starttime": 1700000000000,
      "endtime": 1700000600000,
      "nowTime": 1700000100000,
      "starttimeStr": "2026-11-24 10:00:00",
      "endtimeStr": "2026-11-24 10:10:00",
      "createtimeStr": "2026-11-24 10:00:00",
      "timeLong": 600000,
      "lateMinute": 0,
      "lateEndTime": 0,
      "createuid": "<REDACTED_TEACHER_UID>",
      "createxxuid": "<REDACTED_TEACHER_UID>",
      "puid": 0,
      "releaseNum": 60,
      "attendNum": 58,
      "configJson": "{\"sendToOtherClassList\":\"\",\"onlyAttendForCurrentClass\":0,\"ifSendToOtherClass\":0}"
    }
  }
  ```
- **核心判定字段解析**：
  - **是否为拍照签到**：`otherId == 0 && ifphoto == 1`（或签退 `activeType == 74 && ifphoto == 1`）。
  - **是否为指定位置签到**：`otherId == 4` 或 `ifopenAddress == 1`。服务端同时定义百度坐标系字段（`latitude`/`longitude`/`locationLatitude`/`locationLongitude`，页面加载百度地图 `api.map.baidu.com`）与高德坐标系后缀字段（`locationLatitude_gd`/`locationLongitude_gd`）；`locationRange` 为允许半径（米，如 `"100"`），`locationText` 为指定地点文本。
  - **是否开启二维码动态刷新**：`otherId == 2` 时，`ifrefreshewm == 1` 表示动态刷新二维码，`ewmRefreshTime` 为刷新周期。
  - **是否需要行为验证码**：`ifNeedVCode == 1`（或 `showVCode == 1`）。
  - **防作弊与扩展校验标记**：`openPreventCheatFlag`（防作弊开关）、`openCheckFaceFlag`（人脸识别校验开关）、`openCheckWeChatFlag`（微信校验开关）。
  - **签退关联字段**（`activeType == 74` 实测）：`openSignOutFlag == 1`、`signInId`（关联签到活动 ID）、`signOutDuration`（毫秒，如 `600000`）、`signOutAbsoluteTime`（时间戳）及可选 `timer` 对象（含 `timerSignId`, `signOutDuration`, `signOutAbsoluteTime` 等）。
- **未登录响应（实测）**：`{"result":0,"msg":null,"data":null,"errorMsg":"请登录后再试"}`。

### 6.2 签到预加载页面（H5 与 Web）

在实际提交签到前，官方客户端通常先访问预加载页面以完成页面状态检查或服务端上下文准备：

1. **移动端 H5 预加载**：`GET https://mobilelearn.chaoxing.com/newsign/preSign`
   - Query 参数：`courseId`、`classId`、`activePrimaryId`（即 `activeId`）、`general=1`、`sys=1`、`ls=1`、`appType=15`、`tid=`、`uid`、`ut=s`
   - 响应：HTTP `200 text/html`，已签或已结束时直接渲染签到结果与状态图标/时间戳。
2. **PC Web 端预加载**：`GET https://mobilelearn.chaoxing.com/widget/sign/pcStuSignController/preSign`
   - Query 参数：`activeId`、`classId`、`fid`、`courseId`
   - 响应：HTTP `200 text/html`，包含隐藏字段 `#activeId`、`#activeStatus`、`#courseId`、`#classId`、`#puid`、`#createUid`、`#fid`。

### 6.3 签到与签退提交主端点：`GET https://mobilelearn.chaoxing.com/pptSign/stuSignajax`

> **合规探测边界说明**：为避免对真实课堂造成数据污染，本规范中针对 `stuSignajax` 的参数验证仅在已结束（`status=2`）且当前账号已留存签到状态（`userStatus=1`）的历史活动上探测只读校验分支；**未对进行中课堂提交任何真实写入请求**。

- **请求方法**：`GET`
- **鉴权**：携带 `.chaoxing.com` 登录态 Cookie。
- **响应格式**：HTTP `200`，`Content-Type: text/html;charset=UTF-8`，响应体为**纯文本状态串**（非 JSON）。
- **公共及各场景 Query 参数**：

| 参数名 | 适用类型 | 说明 | 实测观测证据 |
| :--- | :--- | :--- | :--- |
| `activeId` | 全部 | 签到/签退活动 ID（`activeType=2` 或 `74` 均使用本接口） | 缺省或不存在返回 HTTP `500`；`74_0_2` 实测走此端点触发拍照校验 |
| `uid` | 全部 | 当前用户 `UID` | 身份标识参数 |
| `fid` | 全部 | 当前用户学校 `fid` | 机构标识参数 |
| `appType` | 全部 | 客户端类型，H5/移动端固定传 `"15"` | 来自 `newsign/preSign` 与官方探测 |
| `name` | 全部 | 学生姓名（可传空字符串 `""` 或用户姓名） | 公共可选参数 |
| `clientip` | 全部 | 客户端 IP（可传空字符串 `""`） | 公共可选参数 |
| `latitude` | 位置/通用 | 纬度字符串；非位置签到可传 `"-1"`；位置签到传真实纬度 | 在已结束位置签到中传入 `"-1"` 与 `"39.9"` 实测改变了服务端计算距离 |
| `longitude` | 位置/通用 | 经度字符串；非位置签到可传 `"-1"`；位置签到传真实经度 | 在已结束位置签到中传入 `"-1"` 与 `"116.3"` 实测改变了服务端计算距离 |
| `address` | 位置 | 位置文本描述 | 位置签到附加地址参数 |
| `ifTiJiao` | 位置/通用 | 位置签到提交标识 `"1"` | 位置签到参数 |
| `enc` | 二维码 | 二维码中的动态校验串 `enc` | 已结束二维码签到缺 `enc` 返回 `"签到失败，请重新扫描"`；错 `enc` 返回 `"签到失败，请重新扫描。"` |
| `signCode` | 手势/签到码 | 手势序列或数字签到码 | 已结束手势/签到码缺 `signCode` 返回 `"签到失败[90002]"` |
| `objectId` | 拍照 | 上传至 `pan-yz.chaoxing.com` 获得的图片 `objectId` | `ifphoto=1` 时缺省或无效返回 `"photoAuthError_PCR007"` |
| `validate` | 验证码触发 | 行为验证码通过后回调得到的 `validate` 凭据 | 当 `ifNeedVCode=1` 或触发风控时携带 |

- **服务端实测返回文本枚举**：
  - `"您已签到过了"`：重复签到（在已结束且已签的普通签到活动上实测返回）。
  - `"签到失败，请重新扫描"` / `"签到失败，请重新扫描。"`：二维码 `enc` 缺失、无效或已过期。
  - `"签到失败[90002]"`：手势/签到码错误、参数不足或活动已不可签（实测见于 `2_3_2`、`2_5_2`）。
  - `"距教师指定签到地点<DISTANCE>米，不在可签到范围内。"`：位置签到坐标超出 `locationRange` 范围。
  - `"photoAuthError_PCR007"`：拍照签到（`ifphoto=1`，含普通拍照签到及拍照签退 `activeType=74`）缺少有效 `objectId` 或图片鉴权失败。

### 6.4 辅助签到查询与校验端点

1. **手势/签到码前置校验**：`GET https://mobilelearn.chaoxing.com/widget/sign/pcStuSignController/checkSignCode`
   - Query 参数：`activeId`、`signCode`
   - 响应（`200 application/json;charset=UTF-8`）：
     - 校验失败实测：`{"result":0,"msg":null,"data":null,"errorMsg":"手势不正确"}`（实测对 `otherId=3` 手势与 `otherId=5` 签到码均返回该格式）。
2. **学生签到记录查询 / v2 签到接口**：`GET https://mobilelearn.chaoxing.com/v2/apis/sign/signIn?activeId=<ACTIVE_ID>`
   - 响应（`200 application/json;charset=UTF-8`）：
     - 已存在签到记录且命中返回分支时返回 `result=1, msg="success"` 及 `data` 记录对象（含 `id`, `uid`, `activeId`, `status`, `createtime`, `updatetime`, `name`, `sasort`, `longitude`, `latitude`, `distance`, `distanceStr`, `clientip`, `useragent`, `type`, `submittime`, `schoolname`, `fid`, `deviceCode`, `photoErrorFlag`, `extraJson` 等字段）。
     - 已结束不可签活动实测返回：`{"result":0,"msg":null,"data":{"extraJson":{}},"errorMsg":"签到已结束，签到失败"}`。
3. **二维码内容格式与跳转端点**：`GET https://mobilelearn.chaoxing.com/widget/sign/e`
   - 标准扫码 URL 形态：`https://mobilelearn.chaoxing.com/widget/sign/e?id=<ACTIVE_ID>&c=<CODE>&enc=<ENC>&DB_STRATEGY=PRIMARY_KEY&STRATEGY_PARA=id`
   - 实测携带合法参数请求该 URL 返回 HTTP `302` 重定向至：
     `https://mobilewx.chaoxing.com/widget/weixin/sign/stuSignController/preSign?activeId=<ACTIVE_ID>&rcode=SIGNIN%3Aaid%3D<ACTIVE_ID>%26source%3D15%26Code%3D<CODE>%26enc%3D<ENC>`
   - 由此确认超星二维码签到内部 `rcode` 字符串结构为：`SIGNIN:aid=<ACTIVE_ID>&source=15&Code=<CODE>&enc=<ENC>`（客户端扫码后可同时兼容解析上述 URL Query 参数 `id`/`enc` 及 `SIGNIN:...` 键值对提取 `aid` 与 `enc`）。
4. **教师端刷新二维码端点（学生端权限拒绝样本）**：`GET https://mobilelearn.chaoxing.com/v2/apis/sign/refreshQRCode?activeId=<ACTIVE_ID>`
   - 非二维码活动返回：`{"result":0,"msg":null,"data":null,"errorMsg":"非二维码签到"}`
   - 学生身份调用二维码活动返回：`{"result":0,"msg":null,"data":null,"errorMsg":"无权限获取签到二维码"}`
5. **PC 端普通签入页**：`GET https://mobilelearn.chaoxing.com/widget/sign/pcStuSignController/signIn?activeId=<ID>&classId=<CLAZZ_ID>&courseId=<COURSE_ID>&fid=<FID>`（返回 HTML，活动结束时呈现 `"签到已结束"`）。

### 6.5 拍照签到图片上传协议（`pan-yz.chaoxing.com`）

拍照签到（`ifphoto == 1`）需先将照片上传至超星云盘取得 `objectId`，再将该 `objectId` 传入 `/pptSign/stuSignajax`。

1. **获取上传临时令牌 `_token`**：
   - **Endpoint**：`GET https://pan-yz.chaoxing.com/api/token/uservalid`
   - **鉴权**：携带 `.chaoxing.com` 登录态 Cookie
   - **成功响应（`200 text/html;charset=UTF-8`）**：
     ```json
     {
       "result": true,
       "_token": "<REDACTED_32_CHAR_HEX_TOKEN>"
     }
     ```
   - **会话失效响应（实测）**：
     ```json
     {
       "code": -10,
       "msg": "生成token失败",
       "newCode": 200013,
       "result": false
     }
     ```
2. **上传图片文件**：
   - **Endpoint**：
     - `POST https://pan-yz.chaoxing.com/upload`（或官方网盘移动端页面 `POST https://pan-yz.chaoxing.com/upload/uploadfile?uploadtype=normal&prdid=-1&_token=<TOKEN>&puid=<PUID>&fldid=<FLDID>`）
   - **请求格式**：`multipart/form-data`
     - 表单字段：`puid`（用户 `UID`）、`_token`（上一步取得的 `_token`）、`file`（二进制图片文件）；移动端 `fileList` 亦传 `fldid`（目录 ID）。
     - 若有需求可附带 `p-auth-token` 请求头（取自登录 Cookie `p_auth_token`）。
   - **实测参数校验错误响应（`200 text/html;charset=UTF-8`）**：
     - 缺少/无效 `_token` 时：`{"result":false,"msg":"您的ip：<CLIENT_IP> 不被允许访问"}`
     - 缺少 `puid` 时：`{"code":-5,"msg":"参数错误, puid不能为空, null","newCode":200008,"result":false}`
     - 缺少 `file` 时：`{"code":-5,"msg":"参数错误, file不能为空","newCode":200008,"result":false}`
   - **关于成功响应说明**：为避免向测试账号云盘写入垃圾文件，本次未实际上传图片文件；依据官方网盘 `mobile/fileList` 内联脚本逻辑，成功响应 JSON 包含 `result: true` 及 `data.audit`（且 `common.js` 中文件记录使用 `objectId` 字段，对应 `stuSignajax` 接收的 `objectId` 参数）。`objectId` 在上传接口返回 JSON 中位于顶层或 `data` 子对象需以首次真实文件上传响应为准，客户端可兼容读取 `objectId` 与 `data.objectId`。

---

## 7. 常见风控、会话过期与错误码汇总

| 分类 | 触发场景 / 端点 | HTTP 状态 | 实际观测响应 / 表现 | 客户端处理建议 |
| :--- | :--- | :--- | :--- | :--- |
| 登录校验 | `fanyalogin` 参数为空 | `200` | `{"msg2":"参数为空","status":false}` | 拦截空输入并提示 |
| 登录校验 | `fanyalogin` 账号/密码错误或密文损坏 | `200` | `{"msg2":"用户名或密码错误","status":false}` | 提示凭据错误，勿高频重试以免触发滑块 |
| 行为风控 | 登录页 `needVcode="1"` 或签到 `ifNeedVCode=1` | `200` | 需通过 `captcha.chaoxing.com` 获取 `validate` 票据 | 引导用户完成点选/滑块验证并回填 `validate` |
| 安全策略 | 弱密码/密码超时（`login.js`） | `200` | `status=false, weakpwd=true, uid=...` | 引导跳转官方 `/v11/updateweakpwd` 修改密码 |
| 安全策略 | 异地/新设备双因子登录（`login.js`） | `200` | `status=true, containTwoFactorLogin=true, twoFactorLoginUrl=...` | 引导完成 `/v11/checkPassword` 或短信 `/v11/checkmessage` |
| 会话过期 | `mooc1-api` 课程列表 Cookie 失效 | `200` | `{"result":0,"msg":"请重新登录","status":false,"errorMsg":"请重新登录"}` | 清除本地会话并触发重新登录 |
| 会话过期 | `mobilelearn` 活动列表 Cookie 失效 | `302` | `Location: https://passport2.chaoxing.com/mlogin?...` | 识别重定向到 `passport2` 登录页，判定会话失效 |
| 会话过期 | `getPPTActiveInfo` Cookie 失效 | `200` | `{"result":0,"msg":null,"data":null,"errorMsg":"请登录后再试"}` | 触发会话重登 |
| 会话过期 | `sso` `userLogin4Uname.do` 鉴权失败 | `200` | 无 Cookie：`{"result":0,"errorMsg":"非法请求,请重新登录(>_<)"}`；无效 Cookie：`{"result":0,"errorMsg":"非法请求2,请重新登录(>_<)"}` | 判定登录态失效 |
| 会话过期 | `passport2` `getAccountMessage` 失效 | `200` | `{"status":false}` | 判定登录态失效 |
| 会话过期 | `pan-yz` `/api/token/uservalid` 失效 | `200` | `{"code":-10,"msg":"生成token失败","newCode":200013,"result":false}` | 重新登录或刷新 Cookie 后重试 |
| 签到业务 | `stuSignajax` 重复签到 | `200` | `"您已签到过了"` | 视为已签到态，不再重复请求 |
| 签到业务 | `stuSignajax` 缺少/错误手势或签到码 | `200` | `"签到失败[90002]"`（`checkSignCode` 返回 `errorMsg: "手势不正确"`） | 提示用户核对输入或确认活动状态 |
| 签到业务 | `stuSignajax` 二维码过期或无效 | `200` | `"签到失败，请重新扫描"` / `"签到失败，请重新扫描。"` | 提示重新扫描最新二维码 |
| 签到业务 | `stuSignajax` 超出位置范围 | `200` | `"距教师指定签到地点<X>米，不在可签到范围内。"` | 检查坐标系与定位半径提示 |
| 签到业务 | `stuSignajax` 拍照鉴权异常 | `200` | `"photoAuthError_PCR007"` | 重新获取 `_token` 并上传有效照片 `objectId` |
| 签到业务 | `v2/apis/sign/signIn` 活动已结束 | `200` | `{"result":0,"msg":null,"data":{"extraJson":{}},"errorMsg":"签到已结束，签到失败"}` | 更新本地活动状态为已结束 |
| 参数异常 | 缺少必填 ID（`activeId` 等）或未传参访问接口 | `405` / `500` | Tengine 默认 `500 Internal Server Error` HTML（`pan-yz /upload` GET 返回 `405`） | 保证必填参数非空与 HTTP Method 正确 |
